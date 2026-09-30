package de.rezeptkiste.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import de.rezeptkiste.AppController
import de.rezeptkiste.PlatformServices

@Composable
fun App(platform: PlatformServices) {
    val scope = rememberCoroutineScope()
    val controller = remember { AppController(platform, scope) }
    val loggedIn by controller.loggedIn.collectAsState()

    RezeptTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                if (loggedIn) {
                    MainScreen(controller, platform)
                } else {
                    LoginScreen(controller)
                }
            }
        }
    }
}
