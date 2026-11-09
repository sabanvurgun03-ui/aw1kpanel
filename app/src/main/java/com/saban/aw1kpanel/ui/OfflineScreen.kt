package com.saban.aw1kpanel.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Telefon modemin ağında değilken açılan ekran: uygulama açılır, durum yazar, tekrar denenebilir. */
@Composable
fun OfflineScreen(
    state: UiState,
    onRetry: () -> Unit,
    onLoginForm: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            KediLogo(88.dp)
            Spacer(Modifier.height(16.dp))
            Text("AW1K Panel", style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(24.dp))
            Text(
                "Router'a bağlı değil",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Telefon modemin Wi-Fi ağına bağlı değil ya da modem yanıt vermiyor. " +
                    "Modemin ağına bağlanıp tekrar dene.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            state.loginError?.let {
                Spacer(Modifier.height(12.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(24.dp))
            Button(onClick = onRetry, enabled = !state.retrying, modifier = Modifier.fillMaxWidth()) {
                if (state.retrying) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.size(10.dp))
                        Text("Bağlanıyor…")
                    }
                } else {
                    Text("Bağlanmayı dene")
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onLoginForm, modifier = Modifier.fillMaxWidth()) {
                Text("Modem seç / giriş yap")
            }
        }
    }
}
