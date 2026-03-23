package com.rezept.app

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class WebFormActivity : AppCompatActivity() {

    private val formUrl = "https://www.bergmann-dachau.de/index.php/kontakt/ihreanfrage"

    // Persönliche Daten
    private val userName = "Philipp Reindl"
    private val userEmail = "reindlmail@gmail.com"
    private val userPhone = "01733923115"
    private val userBirthdate = "12.02.1985"

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_web_form)

        val medications = intent.getStringArrayListExtra("medications") ?: run {
            finish()
            return
        }

        val webView = findViewById<WebView>(R.id.webView)
        val progressBar = findViewById<ProgressBar>(R.id.progressBar)
        val tvTitle = findViewById<TextView>(R.id.tvWebTitle)
        val tvSubtitle = findViewById<TextView>(R.id.tvWebSubtitle)
        val bannerInfo = findViewById<LinearLayout>(R.id.bannerInfo)

        val message = buildMessage(medications)

        // WebView konfigurieren
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = true
            displayZoomControls = false
            // Browser-User-Agent um Bot-Sperren zu umgehen
            userAgentString = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        }

        // Ladefortschritt anzeigen
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (newProgress < 100) {
                    tvSubtitle.text = "Laden... $newProgress%"
                }
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)

                // Kurz warten bis DOM vollständig gerendert ist
                view?.postDelayed({
                    injectFormData(webView, message)
                    progressBar.visibility = View.GONE
                    tvTitle.text = "Formular prüfen & absenden"
                    tvSubtitle.text = "Daten eingetragen — bitte prüfen und absenden"
                    bannerInfo.visibility = View.VISIBLE
                }, 800)
            }
        }

        webView.loadUrl(formUrl)
    }

    private fun buildMessage(medications: List<String>): String {
        val medList = medications.joinToString("\n") { "- $it" }
        return """Sehr geehrte Damen und Herren,

ich bitte um die Ausstellung eines E-Rezepts für folgende Medikamente:

$medList

Patientendaten:
Name: $userName
Geburtsdatum: $userBirthdate
Telefon: $userPhone
E-Mail: $userEmail

Bitte schicken Sie das E-Rezept an meine E-Mail-Adresse oder informieren Sie mich telefonisch.

Mit freundlichen Grüßen
$userName"""
    }

    private fun injectFormData(webView: WebView, message: String) {
        val escapedMsg = message
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\n", "\\n")
            .replace("\r", "")
        val escapedName  = userName.replace("'", "\\'")
        val escapedEmail = userEmail.replace("'", "\\'")
        val escapedPhone = userPhone.replace("'", "\\'")

        // JavaScript füllt alle typischen Joomla-Kontaktformular-Felder aus
        val js = """
(function() {
    function fill(selectors, value) {
        selectors.forEach(function(sel) {
            document.querySelectorAll(sel).forEach(function(el) {
                el.value = value;
                el.dispatchEvent(new Event('input',  {bubbles:true}));
                el.dispatchEvent(new Event('change', {bubbles:true}));
            });
        });
    }

    // Name
    fill([
        'input[name="jform[contact_name]"]',
        'input[name*="name"]',
        'input[id*="name"]',
        'input[placeholder*="Name"]',
        'input[placeholder*="name"]'
    ], '$escapedName');

    // E-Mail
    fill([
        'input[name="jform[contact_email]"]',
        'input[type="email"]',
        'input[name*="email"]',
        'input[id*="email"]',
        'input[placeholder*="mail"]',
        'input[placeholder*="Mail"]'
    ], '$escapedEmail');

    // Telefon
    fill([
        'input[name="jform[contact_telephone]"]',
        'input[name*="phone"]',
        'input[name*="tel"]',
        'input[id*="phone"]',
        'input[id*="tel"]',
        'input[placeholder*="Telefon"]',
        'input[placeholder*="telefon"]',
        'input[type="tel"]'
    ], '$escapedPhone');

    // Betreff
    fill([
        'input[name="jform[contact_subject]"]',
        'input[name*="subject"]',
        'input[id*="subject"]',
        'input[placeholder*="Betreff"]',
        'input[placeholder*="betreff"]'
    ], 'E-Rezept Anforderung');

    // Nachricht
    var msgSelectors = [
        'textarea[name="jform[contact_message]"]',
        'textarea[name*="message"]',
        'textarea[id*="message"]',
        'textarea[name*="text"]',
        'textarea[id*="text"]',
        'textarea[placeholder*="Nachricht"]',
        'textarea[placeholder*="nachricht"]',
        'textarea[placeholder*="Anfrage"]',
        'textarea'
    ];
    msgSelectors.forEach(function(sel) {
        document.querySelectorAll(sel).forEach(function(el) {
            el.value = '$escapedMsg';
            el.dispatchEvent(new Event('input',  {bubbles:true}));
            el.dispatchEvent(new Event('change', {bubbles:true}));
        });
    });

    // Seite nach oben scrollen damit Benutzer alles sieht
    window.scrollTo(0, 0);
})();
        """.trimIndent()

        webView.evaluateJavascript(js, null)
    }
}
