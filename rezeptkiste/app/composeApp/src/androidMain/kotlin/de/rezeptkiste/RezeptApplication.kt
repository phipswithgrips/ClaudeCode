package de.rezeptkiste

import android.app.Application

/** Hält die Plattformdienste (Datenbank, Token-Ablage) über Activity-Neustarts hinweg. */
class RezeptApplication : Application() {
    val platform: AndroidPlatform by lazy { AndroidPlatform(this) }
}
