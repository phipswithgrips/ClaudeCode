package de.rezeptkiste

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import de.rezeptkiste.ui.App

class MainActivity : ComponentActivity() {
    private val picker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        (application as RezeptApplication).platform.onPicked(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Helle Symbole auf dunklen Systemleisten
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        val platform = (application as RezeptApplication).platform
        platform.launchPicker = { mime -> picker.launch(mime) }
        setContent { App(platform) }
    }
}
