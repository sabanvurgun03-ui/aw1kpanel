package com.saban.aw1kpanel.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.saban.aw1kpanel.R
import com.saban.aw1kpanel.data.AppSettings

/** Ayarlar: Band kilidi sayfası için şifre oluştur / değiştir / kaldır. */
@Composable
internal fun BandPasswordCard(settings: AppSettings, vm: PanelViewModel) {
    var dialog by remember { mutableStateOf<String?>(null) } // "set" | "remove"
    SectionCard {
        CardTitle(R.drawable.ic_nav_band, "Band kilidi şifresi")
        Text(
            if (settings.bandLockProtected) "Band kilidi sayfası şifreli." else "Şifre yok; sayfa herkese açık.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { dialog = "set" }) { Text(if (settings.bandLockProtected) "Değiştir" else "Şifre oluştur") }
            if (settings.bandLockProtected) {
                OutlinedButton(onClick = { dialog = "remove" }) { Text("Kaldır", color = Bad) }
            }
        }
    }

    dialog?.let { mode ->
        var current by remember { mutableStateOf("") }
        var new by remember { mutableStateOf("") }
        var err by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(if (mode == "set") (if (settings.bandLockProtected) "Şifreyi değiştir" else "Şifre oluştur") else "Şifreyi kaldır") },
            text = {
                androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (settings.bandLockProtected) {
                        OutlinedTextField(
                            value = current, onValueChange = { current = it; err = null },
                            label = { Text("Mevcut şifre") }, singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                        )
                    }
                    if (mode == "set") {
                        OutlinedTextField(
                            value = new, onValueChange = { new = it; err = null },
                            label = { Text("Yeni şifre (en az 4 karakter)") }, singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                        )
                    }
                    err?.let { Text(it, color = Bad, style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val r = if (mode == "set") vm.setBandPassword(current, new) else vm.removeBandPassword(current)
                    if (r == null) dialog = null else err = r
                }) { Text("Kaydet") }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Vazgeç") } },
        )
    }
}
