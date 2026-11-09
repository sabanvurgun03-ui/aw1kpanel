package com.saban.aw1kpanel.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.saban.aw1kpanel.data.WifiDetail

private val ENCRYPTIONS = listOf(
    "psk2" to "WPA2",
    "psk-mixed" to "WPA/WPA2",
    "sae" to "WPA3",
    "sae-mixed" to "WPA2/WPA3",
    "none" to "Açık",
)

@Composable
internal fun WifiEditor(initial: WifiDetail, onSave: (WifiDetail) -> Unit, onClose: () -> Unit) {
    var ssid by remember { mutableStateOf(initial.ssid) }
    var key by remember { mutableStateOf(initial.key) }
    var showKey by remember { mutableStateOf(false) }
    var enc by remember { mutableStateOf(initial.encryption) }
    var hidden by remember { mutableStateOf(initial.hidden) }
    var isolate by remember { mutableStateOf(initial.isolate) }
    var channel by remember { mutableStateOf(initial.channel) }
    var htmode by remember { mutableStateOf(initial.htmode) }
    var txpower by remember { mutableStateOf(initial.txpower) }
    var country by remember { mutableStateOf(initial.country) }
    var confirm by remember { mutableStateOf(false) }

    val needsKey = enc != "none"
    val keyOk = !needsKey || key.length >= 8
    val valid = ssid.isNotBlank() && keyOk

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Wi-Fi ayarları") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(ssid, { ssid = it }, label = { Text("Ağ adı (SSID)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("Güvenlik", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ENCRYPTIONS.take(3).forEach { (v, l) ->
                        FilterChip(selected = enc == v, onClick = { enc = v }, label = { Text(l) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ENCRYPTIONS.drop(3).forEach { (v, l) ->
                        FilterChip(selected = enc == v, onClick = { enc = v }, label = { Text(l) })
                    }
                    if (ENCRYPTIONS.none { it.first == enc }) {
                        FilterChip(selected = true, onClick = {}, label = { Text(enc) })
                    }
                }
                if (needsKey) {
                    OutlinedTextField(
                        key, { key = it },
                        label = { Text("Şifre (en az 8 karakter)") },
                        singleLine = true,
                        isError = !keyOk,
                        visualTransformation = if (showKey) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = { TextButton(onClick = { showKey = !showKey }) { Text(if (showKey) "Gizle" else "Göster") } },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Gizli ağ (SSID yayınlama)")
                    Switch(hidden, { hidden = it })
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("İstemci yalıtımı")
                    Switch(isolate, { isolate = it })
                }
                Text(
                    "Radyo: ${initial.radio}${if (initial.band.isNotEmpty()) " · ${initial.band}" else ""} (bu ayarlar aynı radyodaki tüm ağları etkiler)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                OutlinedTextField(channel, { channel = it }, label = { Text("Kanal (auto ya da numara)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(htmode, { htmode = it }, label = { Text("Kanal genişliği (HT20, HT40, VHT80…)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(txpower, { txpower = it }, label = { Text("Verici gücü (dBm, boş = varsayılan)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(country, { country = it }, label = { Text("Ülke kodu (TR)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { confirm = true }) { Text("Uygula") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Vazgeç") } },
    )

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Ayarlar uygulansın mı?") },
            text = { Text("Wi-Fi yeniden başlar; telefonun dahil bu ağa bağlı tüm cihazların bağlantısı kesilir. Ad ya da şifre değiştiyse yeni bilgilerle yeniden bağlanman gerekir.") },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    onSave(
                        initial.copy(
                            ssid = ssid.trim(), key = key, encryption = enc, hidden = hidden, isolate = isolate,
                            channel = channel.trim(), htmode = htmode.trim(), txpower = txpower.trim(), country = country.trim(),
                        ),
                    )
                }) { Text("Uygula") }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Vazgeç") } },
        )
    }
}
