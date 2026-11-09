package com.saban.aw1kpanel.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.saban.aw1kpanel.R
import com.saban.aw1kpanel.data.BandGroup

private fun className(c: String) = when (c) {
    "UMTS" -> "UMTS (3G)"
    "LTE" -> "LTE (4G)"
    "NR" -> "5G SA"
    "NR_NSA" -> "5G NSA"
    else -> c
}

@Composable
internal fun BandLockTab(state: UiState, settings: com.saban.aw1kpanel.data.AppSettings, vm: PanelViewModel) {
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { vm.relockBand() } }
    if (settings.bandLockProtected && !state.bandUnlocked) {
        PasswordGate(vm)
        return
    }
    LaunchedEffect(Unit) { vm.refreshLockband() }
    val lb = state.lockband

    LazyColumn(
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionCard {
                CardTitle(R.drawable.ic_nav_band, "Band kilidi")
                Text(
                    "Modemin hangi bantlara bağlanabileceğini seç. Yalnızca seçili bantlar kullanılır; " +
                        "yanlış seçim bağlantıyı kesebilir. Hepsini seçmek kilidi kaldırır.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (lb == null) {
            item {
                Text(
                    state.lockbandError ?: "Yükleniyor…",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            item(key = "smart") { SmartBandCard(state, vm) }
            lb.forEach { (cls, group) ->
                item(key = cls) { BandClassCard(cls, group, vm) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BandClassCard(cls: String, group: BandGroup, vm: PanelViewModel) {
    val ids = group.available.map { it.first }
    var sel by remember(group) { mutableStateOf(group.locked.filter { it in ids }.toSet()) }
    var confirm by remember { mutableStateOf(false) }
    val changed = sel != group.locked.filter { it in ids }.toSet()

    SectionCard {
        Text(className(cls), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        group.available.chunked(2).forEach { pair ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                pair.forEach { (id, name) ->
                    FilterChip(
                        selected = id in sel,
                        onClick = { sel = if (id in sel) sel - id else sel + id },
                        label = { Text(name, maxLines = 1) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { sel = ids.toSet() }) { Text("Hepsini seç") }
            Button(onClick = { confirm = true }, enabled = sel.isNotEmpty() && changed) { Text("Gönder") }
        }
        if (sel.isEmpty()) {
            Text(
                "En az bir bant seçmelisin.",
                style = MaterialTheme.typography.bodySmall,
                color = Bad,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }

    if (confirm) {
        val chosen = ids.filter { it in sel }
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("${className(cls)} bantları gönderilsin mi?") },
            text = {
                Column {
                    Text(
                        group.available.filter { it.first in sel }.joinToString(", ") { it.second },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.width(1.dp))
                    Text(
                        "Modem seçili bantlara kilitlenir ve bağlantı kısa süre kesilir. " +
                            "Bulunduğun yerde çekmeyen bantları seçersen internet gidebilir.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.setLockBand(cls, chosen); confirm = false }) { Text("Gönder") }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Vazgeç") } },
        )
    }
}

@Composable
private fun PasswordGate(vm: PanelViewModel) {
    var pw by remember { mutableStateOf("") }
    var err by remember { mutableStateOf(false) }
    var forgot by remember { mutableStateOf(false) }
    if (forgot) {
        AlertDialog(
            onDismissRequest = { forgot = false },
            title = { Text("Şifre modeme yazılsın mı?") },
            text = {
                Text(
                    "Şifre modemde /root/band-kilidi-sifre.txt dosyasına yazılır. SSH ile bağlanıp " +
                        "cat /root/band-kilidi-sifre.txt komutuyla okuyabilirsin. Dosyayı yalnızca modemin root kullanıcısı okuyabilir.",
                )
            },
            confirmButton = { TextButton(onClick = { vm.forgotBandPassword(); forgot = false }) { Text("Yaz") } },
            dismissButton = { TextButton(onClick = { forgot = false }) { Text("Vazgeç") } },
        )
    }
    LazyColumn(contentPadding = PaddingValues(vertical = 12.dp)) {
        item {
            SectionCard {
                CardTitle(R.drawable.ic_nav_band, "Band kilidi şifreli")
                Text(
                    "Bu sayfayı açmak için şifreni gir.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                androidx.compose.material3.OutlinedTextField(
                    value = pw,
                    onValueChange = { pw = it; err = false },
                    label = { Text("Şifre") },
                    singleLine = true,
                    isError = err,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (err) Text("Şifre yanlış", color = Bad, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = { if (!vm.unlockBand(pw)) err = true }, enabled = pw.isNotEmpty()) { Text("Aç") }
                    TextButton(onClick = { forgot = true }) { Text("Şifremi unuttum") }
                }
            }
        }
    }
}
