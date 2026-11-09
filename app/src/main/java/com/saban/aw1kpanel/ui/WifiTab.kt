package com.saban.aw1kpanel.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Surface
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedButton
import com.saban.aw1kpanel.data.AppSettings
import com.saban.aw1kpanel.data.WifiClient
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.saban.aw1kpanel.R
import com.saban.aw1kpanel.data.WifiNetwork

private fun encryptionLabel(e: String): String = when (e) {
    "none" -> "Açık ağ"
    "psk2", "psk2+ccmp" -> "WPA2"
    "psk-mixed", "psk-mixed+ccmp" -> "WPA/WPA2"
    "sae" -> "WPA3"
    "sae-mixed" -> "WPA2/WPA3"
    else -> e
}

@Composable
internal fun WifiTab(state: UiState, settings: AppSettings, vm: PanelViewModel) {
    // Misafir ağı kapalıyken ayrı Wi-Fi kartı gösterilmez (Misafir Wi-Fi kartından yönetilir)
    val wifi = state.wifi.filter { it.section != "guest_wifi" || it.enabled }
    val onToggle: (WifiNetwork, Boolean) -> Unit = vm::setWifi
    val onEdit: (String) -> Unit = vm::openWifiEditor
    var pendingOff by remember { mutableStateOf<WifiNetwork?>(null) }
    var blockTarget by remember { mutableStateOf<WifiClient?>(null) }
    var revealed by remember { mutableStateOf(setOf<String>()) }
    var expanded by remember { mutableStateOf(setOf<String>()) }

    LazyColumn(
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            val activeNets = wifi.count { it.enabled }
            val totalClients = wifi.filter { it.enabled }.sumOf { w -> state.wifiClients.count { it.section == w.section } }
            SectionCard(brush = HeaderBrush) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("Wi-Fi", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.75f))
                        Text(
                            if (activeNets > 0) "$activeNets ağ yayında" else "Yayın kapalı",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "$totalClients",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                        Text("bağlı istemci", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.75f))
                    }
                }
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().clickable { vm.setHideDeviceData(!settings.hideDeviceData) }.padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("IP / MAC gizle", style = MaterialTheme.typography.bodyLarge)
                    Text("Bir istemciye dokununca o cihazın bilgisi açılır.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.width(12.dp))
                Switch(checked = settings.hideDeviceData, onCheckedChange = vm::setHideDeviceData)
            }
        }
        if (wifi.isEmpty()) {
            item { Text("Wi-Fi ağı bulunamadı.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        items(wifi, key = { it.section }) { w ->
            val tintOn = MaterialTheme.colorScheme.primary
            val tintOff = MaterialTheme.colorScheme.onSurfaceVariant
            val cl = state.wifiClients.filter { it.section == w.section }
            SectionCard {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconBadge(
                        icon = R.drawable.ic_nav_wifi,
                        tint = if (w.enabled) tintOn else tintOff,
                        background = if (w.enabled) {
                            tintOn.copy(alpha = 0.16f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        size = 52,
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(w.ssid.ifEmpty { "(adsız)" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (w.enabled) "Yayında · ${cl.size} istemci" else "Kapalı",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (w.enabled) Good else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    Switch(
                        checked = w.enabled,
                        onCheckedChange = { on -> if (on) onToggle(w, true) else if (!vm.blockWifiOff(w)) pendingOff = w },
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Chip(w.radio)
                        Chip(encryptionLabel(w.encryption))
                    }
                    FilledTonalButton(onClick = { onEdit(w.section) }) { Text("Ayarlar") }
                }
                if (w.enabled) {
                    val open = w.section in expanded
                    Spacer(Modifier.height(6.dp))
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable {
                            expanded = if (open) expanded - w.section else expanded + w.section
                        }.padding(vertical = 10.dp, horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Bağlı istemciler (${cl.size})", fontWeight = FontWeight.Medium)
                        Text(if (open) "▾" else "▸", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    AnimatedVisibility(visible = open) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (cl.isEmpty()) Text("Bağlı istemci yok.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            cl.forEach { c ->
                                val dev = state.devices.firstOrNull { it.mac.equals(c.mac, true) }
                                val blocked = c.mac in state.blocked
                                val hide = settings.hideDeviceData && c.mac !in revealed
                                ClientRow(
                                    c = c,
                                    dev = dev,
                                    blocked = blocked,
                                    hide = hide,
                                    onReveal = { revealed = if (c.mac in revealed) revealed - c.mac else revealed + c.mac },
                                    onUnblock = { vm.blockMac(c.mac, false) },
                                    onBlock = { blockTarget = c },
                                )
                            }
                        }
                    }
                }
            }
        }
        item { GuestCard(state, vm) }
    }

    blockTarget?.let { c ->
        AlertDialog(
            onDismissRequest = { blockTarget = null },
            title = { Text("Cihaz engellensin mi?") },
            text = { Text("Bu cihaz Wi-Fi ağlarına bağlanamaz hale gelir. Wi-Fi birkaç saniye yeniden başlar ve herkesin bağlantısı kısa süre kesilir.") },
            confirmButton = { TextButton(onClick = { vm.blockMac(c.mac, true); blockTarget = null }) { Text("Engelle", color = Bad) } },
            dismissButton = { TextButton(onClick = { blockTarget = null }) { Text("Vazgeç") } },
        )
    }

    pendingOff?.let { w ->
        AlertDialog(
            onDismissRequest = { pendingOff = null },
            title = { Text("Wi-Fi kapatılsın mı?") },
            text = { Text("\"${w.ssid}\" kapatılırsa bu ağa bağlı cihazların (telefonun dahil) bağlantısı kesilir.") },
            confirmButton = {
                TextButton(onClick = { onToggle(w, false); pendingOff = null }) { Text("Kapat") }
            },
            dismissButton = {
                TextButton(onClick = { pendingOff = null }) { Text("Vazgeç") }
            },
        )
    }
}

private fun fmtSecs(s: Long): String = when {
    s >= 86400 -> "${s / 86400} g ${s % 86400 / 3600} sa"
    s >= 3600 -> "${s / 3600} sa ${s % 3600 / 60} dk"
    s >= 60 -> "${s / 60} dk"
    else -> "$s sn"
}

private fun fmtBytes(b: Long): String = when {
    b >= 1L shl 30 -> "%.1f GB".format(b / 1073741824.0)
    b >= 1L shl 20 -> "%.1f MB".format(b / 1048576.0)
    else -> "${b / 1024} KB"
}

private fun signalLevel(dbm: Int): Int = when {
    dbm >= -55 -> 4
    dbm >= -65 -> 3
    dbm >= -75 -> 2
    else -> 1
}

@Composable
private fun signalColor(dbm: Int): Color = when (signalLevel(dbm)) {
    4, 3 -> Good
    2 -> Warn
    else -> Bad
}

@Composable
private fun SignalBars(dbm: Int) {
    val level = signalLevel(dbm)
    val on = signalColor(dbm)
    val off = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom, modifier = Modifier.height(20.dp)) {
        for (i in 1..4) {
            Box(
                Modifier
                    .width(4.dp)
                    .height((5 + i * 4).dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (i <= level) on else off),
            )
        }
    }
}

@Composable
private fun StatPill(text: String, color: Color) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ClientRow(
    c: WifiClient,
    dev: com.saban.aw1kpanel.data.Device?,
    blocked: Boolean,
    hide: Boolean,
    onReveal: () -> Unit,
    onUnblock: () -> Unit,
    onBlock: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onReveal,
        shape = RoundedCornerShape(18.dp),
        color = scheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SignalBars(c.signal)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        dev?.hostname ?: "(bilinmeyen cihaz)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        ((dev?.ip?.let { if (hide) maskIp(it) else it } ?: "—") + " · " + (if (hide) maskMac(c.mac) else c.mac)),
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(8.dp))
                if (blocked) Chip("Engelli", Bad) else Chip(c.band)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StatPill("↓ ${c.rxMbps} Mb/sn", scheme.primary)
                StatPill("↑ ${c.txMbps} Mb/sn", scheme.primary)
                StatPill("${c.signal} dBm", signalColor(c.signal))
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Veri: ↓${fmtBytes(c.txBytes)} ↑${fmtBytes(c.rxBytes)} · Bağlı: ${c.secs?.let { fmtSecs(it) } ?: "—"}" +
                    (dev?.let { " · Yön: " + when (it.mode) { "direct" -> "Direkt"; "block" -> "Engelli"; else -> "VPN" } } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            if (blocked) {
                OutlinedButton(onClick = onUnblock) { Text("Engeli kaldır") }
            } else {
                OutlinedButton(onClick = onBlock, colors = ButtonDefaults.outlinedButtonColors(contentColor = Bad)) { Text("Wi-Fi'dan engelle") }
            }
        }
    }
}

@Composable
private fun GuestCard(state: UiState, vm: PanelViewModel) {
    val g = state.guest
    var ssid by remember(g) { mutableStateOf(g?.ssid?.ifEmpty { null } ?: "AW1K-Misafir") }
    var key by remember(g) { mutableStateOf(g?.key.orEmpty()) }
    var show by remember { mutableStateOf(false) }
    SectionCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                CardTitle(R.drawable.ic_nav_wifi, "Misafir Wi-Fi")
                Text(
                    when {
                        g == null -> "Durum okunamadı (modemde eklenti güncel mi?)"
                        !g.exists -> "Henüz oluşturulmadı"
                        g.enabled -> "Yayında · ana ağdan ayrı, cihazlar birbirini görmez"
                        else -> "Kapalı"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (g != null && g.exists) {
                Switch(checked = g.enabled, onCheckedChange = { vm.saveGuest(it, g.ssid, g.key) })
            }
        }
        if (g != null) {
            Spacer(Modifier.height(10.dp))
            androidx.compose.material3.OutlinedTextField(
                value = ssid, onValueChange = { ssid = it },
                label = { Text("Ağ adı") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            androidx.compose.material3.OutlinedTextField(
                value = key, onValueChange = { key = it },
                label = { Text("Şifre (en az 8 karakter)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (show) androidx.compose.ui.text.input.VisualTransformation.None
                else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                trailingIcon = { TextButton(onClick = { show = !show }) { Text(if (show) "Gizle" else "Göster") } },
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { vm.saveGuest(if (g.exists) g.enabled else true, ssid, key) },
                enabled = ssid.isNotBlank() && key.length >= 8,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(24.dp),
            ) { Text(if (g.exists) "Kaydet" else "Oluştur ve aç") }
            Text(
                "Misafirler yalnızca internete çıkar, yerel ağa erişemez. İlk oluşturmada ağ ayarları yeniden yüklenir; birkaç saniye bağlantı kesilebilir. Misafir ağ KediRay VPN'inden geçmeyebilir.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}
