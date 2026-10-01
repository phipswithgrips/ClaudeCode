package de.rezeptkiste

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import de.rezeptkiste.ui.App

fun main() {
    val platform = DesktopPlatform()
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Rezeptkiste",
            state = rememberWindowState(size = DpSize(1320.dp, 860.dp)),
        ) {
            window.minimumSize = java.awt.Dimension(900, 600)
            platform.window = window
            App(platform)
        }
    }
}
