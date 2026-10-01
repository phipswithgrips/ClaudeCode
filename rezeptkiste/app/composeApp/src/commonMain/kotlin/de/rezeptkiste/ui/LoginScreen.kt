package de.rezeptkiste.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import de.rezeptkiste.AppController

@Composable
fun LoginScreen(controller: AppController) {
    val state by controller.login.collectAsState()
    var url by remember { mutableStateOf(controller.serverUrl ?: "") }
    var user by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var device by remember { mutableStateOf(controller.defaultDeviceName) }
    val canSubmit = url.isNotBlank() && user.isNotBlank() && password.isNotEmpty() && device.isNotBlank() && !state.busy

    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.Center) {
        Surface(
            modifier = Modifier.padding(16.dp).widthIn(max = 440.dp).fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Rezeptkiste", style = MaterialTheme.typography.headlineMedium, color = accent)
                Text(
                    "Mit deinem Server verbinden. Die Rezepte werden danach auf diesem Gerät gespeichert und funktionieren auch offline.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = url, onValueChange = { url = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    label = { Text("Server-Adresse") }, placeholder = { Text("https://rezepte.example.de") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                )
                OutlinedTextField(
                    value = user, onValueChange = { user = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    label = { Text("Benutzername") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                )
                OutlinedTextField(
                    value = password, onValueChange = { password = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    label = { Text("Passwort") }, visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                )
                OutlinedTextField(
                    value = device, onValueChange = { device = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    label = { Text("Gerätename") },
                    supportingText = { Text("Erscheint in der Geräteliste; darüber lässt sich das Gerät später abmelden.") },
                )
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = { controller.login(url, user, password, device) },
                    enabled = canSubmit,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) {
                    if (state.busy) {
                        CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text("Anmelden")
                    }
                }
            }
        }
    }
}
