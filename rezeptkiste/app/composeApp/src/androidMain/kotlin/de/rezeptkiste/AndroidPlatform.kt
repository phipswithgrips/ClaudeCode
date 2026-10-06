package de.rezeptkiste

import android.content.ActivityNotFoundException
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
import android.provider.OpenableColumns
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalView
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import de.rezeptkiste.db.RezeptDatabase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class AndroidPlatform(private val context: Context) : PlatformServices {

    override val sqlDriver: SqlDriver = AndroidSqliteDriver(RezeptDatabase.Schema, context, "rezeptkiste.db")

    override val secureStore: SecureStore = KeystoreSecureStore(context)

    override val defaultDeviceName: String = listOf(Build.MANUFACTURER, Build.MODEL)
        .filter { !it.isNullOrBlank() }
        .joinToString(" ")
        .ifBlank { "Android" }

    override val isDesktop: Boolean = false

    override fun nowMillis(): Long = System.currentTimeMillis()

    override fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private val cacheRoot = File(context.cacheDir, "images")
    // Neue, noch nicht hochgeladene Fotos dürfen nicht im löschbaren Cache liegen
    private val originals = File(context.filesDir, "originals")

    private fun fileFor(name: String) = if (name.startsWith("original/")) File(originals, name.removePrefix("original/")) else File(cacheRoot, name)

    override fun readCache(name: String): ByteArray? = fileFor(name).takeIf { it.isFile }?.readBytes()

    override fun writeCache(name: String, bytes: ByteArray) {
        val f = fileFor(name)
        f.parentFile?.mkdirs()
        val tmp = File(f.parentFile, f.name + ".tmp")
        tmp.writeBytes(bytes)
        tmp.renameTo(f)
    }

    override fun clearCache() {
        cacheRoot.deleteRecursively()
        originals.deleteRecursively()
    }

    override fun decodeImage(bytes: ByteArray): ImageBitmap? =
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()

    // --- Dateiauswahl über die Activity ---------------------------------------------------

    /** Von MainActivity gesetzt: startet den System-Dateiauswahldialog mit MIME-Typ. */
    var launchPicker: ((String) -> Unit)? = null
    private var pending: CompletableDeferred<Uri?>? = null

    fun onPicked(uri: Uri?) {
        pending?.complete(uri)
        pending = null
    }

    override suspend fun pickFile(kind: FileKind): PickedFile? {
        val launch = launchPicker ?: return null
        val deferred = CompletableDeferred<Uri?>()
        pending = deferred
        launch(
            when (kind) {
                FileKind.IMAGE -> "image/*"
                FileKind.ZIP -> "application/zip"
                FileKind.TEXT -> "text/*"
            },
        )
        val uri = deferred.await() ?: return null
        return withContext(Dispatchers.IO) {
            val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            } ?: "datei"
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@withContext null
            PickedFile(name, bytes)
        }
    }

    override fun shareText(title: String, text: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(send, "Rezept teilen").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Timer der Uhr-App mit der Zeit aus dem Rezept starten. */
    override fun startSystemTimer(seconds: Long, label: String): Boolean = try {
        val i = Intent(AlarmClock.ACTION_SET_TIMER)
            .putExtra(AlarmClock.EXTRA_LENGTH, seconds.coerceIn(1, 86_400).toInt())
            .putExtra(AlarmClock.EXTRA_MESSAGE, label.take(60))
            .putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(i)
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }

    override fun openSystemTimers(): Boolean = try {
        context.startActivity(Intent(AlarmClock.ACTION_SHOW_TIMERS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        false
    }

    override fun clipboardText(): String? {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        return cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
    }

    @Composable
    override fun BackHandler(enabled: Boolean, onBack: () -> Unit) {
        androidx.activity.compose.BackHandler(enabled = enabled, onBack = onBack)
    }

    @Composable
    override fun KeepScreenOn(enabled: Boolean) {
        val view = LocalView.current
        DisposableEffect(enabled) {
            view.keepScreenOn = enabled
            onDispose { view.keepScreenOn = false }
        }
    }
}

/** Token mit einem AES-Schlüssel aus dem Android Keystore verschlüsselt. */
private class KeystoreSecureStore(context: Context) : SecureStore {
    private val prefs = context.getSharedPreferences("rezeptkiste_secure", Context.MODE_PRIVATE)
    private val alias = "rezeptkiste_token_key"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(alias, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    override fun load(): String? {
        val iv = prefs.getString("iv", null) ?: return null
        val data = prefs.getString("data", null) ?: return null
        return runCatching {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
            String(cipher.doFinal(Base64.decode(data, Base64.NO_WRAP)), Charsets.UTF_8)
        }.getOrNull()
    }

    override fun save(token: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        prefs.edit()
            .putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString("data", Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .apply()
    }

    override fun clear() {
        prefs.edit().clear().apply()
    }
}
