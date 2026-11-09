package com.saban.aw1kpanel.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saban.aw1kpanel.R
import com.saban.aw1kpanel.data.AppSettings
import com.saban.aw1kpanel.data.WifiClient
import com.saban.aw1kpanel.data.WifiNetwork

private fun encryptionLabel(e: String): String = when (e) {
    "none" -> "Açık ağ"
    "psk2", "psk2+ccmp" -> "WPA2"
    "psk-mixed", "psk-mixed+ccmp" -> "WPA/WPA2"
    "sae" -> "WPA3"
    "sae-mixed" -> "WPA2/WPA3"
    else -> e
}

/** Ağın frekansı: bağlı istemcilerin bandından, yoksa ağ adından tahmin edilir. */
private fun bandOf(w: WifiNetwork, clients: List<WifiClient>): String? {
    clients.firstOrNull { it.band.isNotBlank() }?.band?.let { return it }
    val s = w.ssid.lowercase()
    return when {
        "5g" in s || "5ghz" in s || "_5" in s -> "5 GHz"
        "2.4" in s || "2g" in s || "24g" in s -> "2.4 GHz"
        else -> null
    }
}

@Composable
internal fun WifiTab(state: UiState, settings: AppSettings, vm: PanelViewModel) {
    // Misafir ağı kapalıyken ayrı Wi-Fi kartı gösterilmez (Misafir Wi-Fi kartından yönetilir)
    val wifi = state.wifi.filter { it.section != "guest_wifi" || it.enabled }
    var pendingOff by remember { mutableStateOf<WifiNetwork?>(null) }
    var blockTarget by remember { mutableStateOf<WifiClient?>(null) }
    var revealed by remember { mutableStateOf(setOf<String>()) }
    var expanded by remember { mutableStateOf(setOf<String>()) }

    LazyColumn(
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { WifiHero(state, wifi, settings, vm) }
        if (wifi.isEmpty()) {
            item { Text("Wi-Fi ağı bulunamadı.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        items(wifi, key = { it.section }) { w ->
            val cl = state.wifiClients.filter { it.section == w.section }
            NetworkCard(
                w = w,
                clients = cl,
                open = w.section in expanded,
                onOpen = { expanded = if (w.section in expanded) expanded - w.section else expanded + w.section },
                onToggle = { on -> if (on) vm.setWifi(w, true) else if (!vm.blockWifiOff(w)) pendingOff = w },
                onEdit = { vm.openWifiEditor(w.section) },
            ) {
                cl.forEach { c ->
                    val hide = settings.hideDeviceData && c.mac !in revealed
                    ClientRow(
                        c = c,
                        dev = state.devices.firstOrNull { it.mac.equals(c.mac, true) },
                        blocked = c.mac in state.blocked,
                        hide = hide,
                        onReveal = { revealed = if (c.mac in revealed) revealed - c.mac else revealed + c.mac },
                        onUnblock = { vm.blockMac(c.mac, false) },
                        onBlock = { blockTarget = c },
                    )
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
            confirmButton = { TextButton(onClick = { vm.setWifi(w, false); pendingOff = null }) { Text("Kapat") } },
            dismissButton = { TextButton(onClick = { pendingOff = null }) { Text("Vazgeç") } },
        )
    }
}

@Composable
private fun WifiHero(state: UiState, wifi: List<WifiNetwork>, settings: AppSettings, vm: PanelViewModel) {
    val on = wifi.filter { it.enabled }
    val total = on.sumOf { w -> state.wifiClients.count { it.section == w.section } }
    val g = state.guest
    SectionCard(brush = HeaderBrush) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Wi-Fi", style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.78f))
                Text(
                    if (on.isNotEmpty()) "${on.size} ağ yayında" else "Yayın kapalı",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }
            Box(
                Modifier.size(78.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$total", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text("cihaz", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f))
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            wifi.take(2).forEach { w ->
                val n = state.wifiClients.count { it.section == w.section }
                KGlassTile(
                    w.ssid.ifEmpty { "(adsız)" },
                    if (w.enabled) "$n cihaz" else "Kapalı",
                    Modifier.weight(1f),
                )
            }
            KGlassTile(
                "Misafir",
                when {
                    g == null || !g.exists -> "Yok"
                    g.enabled -> "Açık"
                    else -> "Kapalı"
                },
                Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(10.dp))
        // IP / MAC gizleme: degrade kart içinde küçük anahtar
        Surface(
            onClick = { vm.setHideDeviceData(!settings.hideDeviceData) },
            shape = RoundedCornerShape(50),
            color = Color.White.copy(alpha = if (settings.hideDeviceData) 0.24f else 0.12f),
        ) {
            Text(
                (if (settings.hideDeviceData) "🙈 IP / MAC gizli" else "👁 IP / MAC görünür") + "  ·  değiştir",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun NetworkCard(
    w: WifiNetwork,
    clients: List<WifiClient>,
    open: Boolean,
    onOpen: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    clientRows: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val band = bandOf(w, clients)
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(
                icon = R.drawable.ic_nav_wifi,
                tint = if (w.enabled) scheme.primary else scheme.onSurfaceVariant,
                background = if (w.enabled) scheme.primary.copy(alpha = 0.16f) else scheme.surfaceVariant,
                size = 48,
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    w.ssid.ifEmpty { "(adsız)" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (w.enabled) KPill("Yayında", Good, dot = true) else KPill("Kapalı", scheme.onSurfaceVariant, dot = true)
            }
            Switch(checked = w.enabled, onCheckedChange = onToggle)
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KTile("Frekans", band ?: "—", Modifier.weight(1f))
            KTile("Güvenlik", "🔒 " + encryptionLabel(w.encryption), Modifier.weight(1f))
            KTile("Cihaz", if (w.enabled) "${clients.size}" else "—", Modifier.weight(0.7f))
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = onEdit, modifier = Modifier.weight(1f)) { Text("⚙  Ayarlar") }
            if (w.enabled) {
                FilledTonalButton(onClick = onOpen, modifier = Modifier.weight(1f)) {
                    Text("Cihazlar (${clients.size}) " + if (open) "▴" else "▾")
                }
            }
        }
        AnimatedVisibility(visible = w.enabled && open) {
            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (clients.isEmpty()) {
                    Text("Bağlı cihaz yok.", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                }
                clientRows()
            }
        }
        Text(
            w.radio,
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.padding(top = 8.dp, start = 2.dp),
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
    dbm == 0 -> 0
    dbm >= -55 -> 4
    dbm >= -65 -> 3
    dbm >= -75 -> 2
    else -> 1
}

@Composable
private fun signalColor(dbm: Int): Color = when (signalLevel(dbm)) {
    0 -> MaterialTheme.colorScheme.onSurfaceVariant
    4, 3 -> Good
    2 -> Warn
    else -> Bad
}

@Composable
private fun SignalBars(dbm: Int) {
    val level = signalLevel(dbm)
    val on = signalColor(dbm)
    val off = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom, modifier = Modifier.height(18.dp)) {
        for (i in 1..4) {
            Box(
                Modifier
                    .width(4.dp)
                    .height((4 + i * 3.5f).dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (i <= level) on else off),
            )
        }
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
    val name = dev?.hostname?.takeIf { it.isNotBlank() } ?: "Bilinmeyen cihaz"
    var menu by remember { mutableStateOf(false) }
    Surface(
        onClick = onReveal,
        shape = RoundedCornerShape(18.dp),
        color = scheme.surfaceVariant.copy(alpha = 0.55f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Baş harf rozeti
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(if (blocked) Bad.copy(alpha = 0.18f) else scheme.primary.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        name.first().uppercase(),
                        fontWeight = FontWeight.Bold,
                        color = if (blocked) Bad else scheme.primary,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        (dev?.ip?.let { if (hide) maskIp(it) else it } ?: "—") + " · " + (if (hide) maskMac(c.mac) else c.mac),
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    SignalBars(c.signal)
                    Text(if (c.signal == 0) "— dBm" else "${c.signal} dBm", fontSize = 10.sp, color = signalColor(c.signal))
                }
                Box {
                    TextButton(onClick = { menu = true }) { Text("⋮", style = MaterialTheme.typography.titleMedium) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        if (blocked) {
                            DropdownMenuItem(text = { Text("Engeli kaldır") }, onClick = { menu = false; onUnblock() })
                        } else {
                            DropdownMenuItem(text = { Text("Wi-Fi'dan engelle", color = Bad) }, onClick = { menu = false; onBlock() })
                        }
                        DropdownMenuItem(
                            text = { Text(if (hide) "IP / MAC göster" else "IP / MAC gizle") },
                            onClick = { menu = false; onReveal() },
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (blocked) KPill("Engelli", Bad)
                KPill("↓ ${c.rxMbps} Mb/sn")
                KPill("↑ ${c.txMbps} Mb/sn")
                if (c.band.isNotBlank()) KPill(c.band, scheme.onSurfaceVariant)
            }
            Text(
                "Bugün ↓${fmtBytes(c.txBytes)} ↑${fmtBytes(c.rxBytes)} · Bağlı ${c.secs?.let { fmtSecs(it) } ?: "—"}" +
                    (dev?.let { " · " + when (it.mode) { "direct" -> "Direkt"; "block" -> "Engelli"; else -> "VPN" } } ?: ""),
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun GuestCard(state: UiState, vm: PanelViewModel) {
    val g = state.guest
    var ssid by remember(g) { mutableStateOf(g?.ssid?.ifEmpty { null } ?: "AW1K-Misafir") }
    var key by remember(g) { mutableStateOf(g?.key.orEmpty()) }
    var show by remember { mutableStateOf(false) }
    var edit by remember(g?.exists) { mutableStateOf(g != null && !g.exists) }
    val scheme = MaterialTheme.colorScheme
    SectionCard {
        KHeader(
            title = "Misafir Wi-Fi",
            subtitle = when {
                g == null -> "Durum okunamadı (modemde eklenti güncel mi?)"
                !g.exists -> "Henüz oluşturulmadı"
                g.enabled -> "Yayında · ana ağdan ayrı"
                else -> "Kapalı"
            },
            icon = R.drawable.ic_nav_wifi,
            tint = if (g?.enabled == true) Good else scheme.primary,
            trailing = if (g != null && g.exists) {
                { Switch(checked = g.enabled, onCheckedChange = { vm.saveGuest(it, g.ssid, g.key) }) }
            } else null,
        )
        if (g != null) {
            if (g.exists) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KTile("Ağ adı", g.ssid.ifEmpty { "—" }, Modifier.weight(1f))
                    KTile("Şifre", if (g.key.isEmpty()) "—" else "••••••••", Modifier.weight(1f))
                }
                TextButton(onClick = { edit = !edit }) { Text(if (edit) "Düzenlemeyi kapat ▴" else "Ad ve şifreyi değiştir ▾") }
            }
            AnimatedVisibility(visible = edit) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                    OutlinedTextField(
                        value = ssid, onValueChange = { ssid = it },
                        label = { Text("Ağ adı") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                    )
                    OutlinedTextField(
                        value = key, onValueChange = { key = it },
                        label = { Text("Şifre (en az 8 karakter)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = { TextButton(onClick = { show = !show }) { Text(if (show) "Gizle" else "Göster") } },
                    )
                    Button(
                        onClick = { vm.saveGuest(if (g.exists) g.enabled else true, ssid, key); edit = false },
                        enabled = ssid.isNotBlank() && key.length >= 8,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(24.dp),
                    ) { Text(if (g.exists) "Kaydet" else "Oluştur ve aç") }
                }
            }
            Text(
                "Misafirler yalnızca internete çıkar, yerel ağa erişemez ve birbirini görmez. İlk oluşturmada ağ ayarları yeniden yüklenir; birkaç saniye bağlantı kesilebilir. Misafir ağ KediRay VPN'inden geçmeyebilir.",
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
