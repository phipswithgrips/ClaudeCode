package de.rezeptkiste.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.rezeptkiste.AppController
import de.rezeptkiste.data.RecipeText
import kotlinx.coroutines.delay

/** Schrift für antippbare Zeitangaben ("10 Minuten") im Rezept. */
@Composable
fun timerLinkStyles() = TextLinkStyles(
    SpanStyle(color = RkColors.Link, textDecoration = TextDecoration.Underline),
    hoveredStyle = SpanStyle(color = RkColors.Link.copy(alpha = 0.8f), textDecoration = TextDecoration.Underline),
)

/** Laufende eingebaute Timer unten rechts, über allen Bildschirmen. */
@Composable
fun TimerPanel(controller: AppController) {
    val timers by controller.timers.collectAsState()
    if (timers.isEmpty()) return
    var now by remember { mutableLongStateOf(controller.platform.nowMillis()) }
    LaunchedEffect(timers.isNotEmpty()) {
        while (true) {
            now = controller.platform.nowMillis()
            delay(250)
        }
    }
    Box(Modifier.fillMaxSize().padding(14.dp), contentAlignment = Alignment.BottomEnd) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.End) {
            timers.forEach { t ->
                val done = t.done
                Surface(
                    color = if (done) accentFill else RkColors.SurfaceHigh,
                    shape = RoundedCornerShape(6.dp),
                    shadowElevation = 6.dp,
                    modifier = Modifier.widthIn(min = 240.dp, max = 340.dp),
                ) {
                    Row(Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (done) "Fertig!" else RecipeText.clock(t.leftSec(now)),
                                fontSize = 26.sp, fontWeight = FontWeight.Light, color = Color.White,
                            )
                            Text(t.label, style = MaterialTheme.typography.bodyMedium, color = if (done) Color.White else RkColors.TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        if (!done) {
                            TimerButton(if (t.pausedLeft != null) "Weiter" else "Pause") {
                                if (t.pausedLeft != null) controller.resumeTimer(t.id) else controller.pauseTimer(t.id)
                            }
                        }
                        TimerIcon(Icons.Filled.Add, "+1 Min.") { controller.addMinute(t.id) }
                        TimerIcon(Icons.Filled.Close, if (done) "OK" else "Abbrechen") { controller.removeTimer(t.id) }
                    }
                }
            }
        }
    }
}

@Composable
private fun TimerButton(text: String, onClick: () -> Unit) {
    Text(
        text, color = Color.White, style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.clip(RoundedCornerShape(4.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 6.dp),
    )
}

@Composable
private fun TimerIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Box(Modifier.size(34.dp).clip(RoundedCornerShape(4.dp)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, label, tint = Color.White, modifier = Modifier.size(18.dp))
    }
}

/** "Neuer Timer": Name und Dauer eingeben (Windows; auf Android öffnet sich die Uhr-App). */
@Composable
fun NewTimerDialog(controller: AppController, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var minutes by remember { mutableStateOf<Long?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = RkColors.SurfaceHigh,
        title = { Text("Neuer Timer", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(Modifier.width(280.dp)) {
                FieldLabel("Name")
                RkField(name, { name = it }, Modifier.fillMaxWidth(), placeholder = "z. B. Nudeln")
                FieldLabel("Dauer")
                TimeField(minutes, { minutes = it }, Modifier.fillMaxWidth())
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(5L, 10L, 15L, 30L, 60L).forEach { m ->
                        Text(
                            formatMinutes(m) ?: "", style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(RkColors.Field).clickable { minutes = m }.padding(horizontal = 8.dp, vertical = 5.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val m = minutes ?: return@TextButton
                controller.startInAppTimer(m * 60, name.ifBlank { "Timer" })
                onDismiss()
            }, enabled = (minutes ?: 0) > 0) { Text("Starten", color = accent) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen", color = RkColors.Text) } },
    )
}
