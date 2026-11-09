package com.saban.aw1kpanel.ui

import androidx.compose.foundation.layout.Box
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.delay
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.saban.aw1kpanel.data.AppSettings
import com.saban.aw1kpanel.data.Device

private fun fmtDuration(s: Long): String = when {
    s >= 86400 -> "${s / 86400} g ${s % 86400 / 3600} sa"
    s >= 3600 -> "${s / 3600} sa ${s % 3600 / 60} dk"
    s >= 60 -> "${s / 60} dk"
    else -> "$s sn"
}

private fun devRate(b: Float): String = when {
    b >= 1_048_576f -> "%.1f MB/s".format(b / 1_048_576f)
    b >= 1024f -> "%.0f KB/s".format(b / 1024f)
    else -> "%.0f B/s".format(b)
}

private fun devBytes(b: Long): String = when {
    b >= 1L shl 30 -> "%.2f GB".format(b / 1073741824.0)
    b >= 1L shl 20 -> "%.1f MB".format(b / 1048576.0)
    else -> "${b / 1024} KB"
}

@Composable
private fun DevPill(text: String, color: Color) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.Medium)
    }
}

/** Cihaz kartının altındaki anlık veri kutusu: gerçek Wi-Fi sayaçları ve bugünkü KediRay kullanımı. */
@Composable
private fun LiveBox(client: com.saban.aw1kpanel.data.WifiClient?, rate: Pair<Float, Float>?, todayBytes: Long?) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(scheme.surfaceVariant.copy(alpha = 0.6f))
            .padding(12.dp),
    ) {
        Text("Anlık veri", style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        if (client != null) {
            val sig = when {
                client.signal >= -65 -> Good
                client.signal >= -75 -> Warn
                else -> Bad
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DevPill("↓ " + (rate?.let { devRate(it.first) } ?: "…"), scheme.primary)
                DevPill("↑ " + (rate?.let { devRate(it.second) } ?: "…"), scheme.primary)
                DevPill("${client.signal} dBm", sig)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "${client.band} · bağlantı hızı ↓${client.rxMbps}/↑${client.txMbps} Mb/sn · bu oturumda ↓${devBytes(client.txBytes)} ↑${devBytes(client.rxBytes)}",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
        } else if (rate != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DevPill("↓ " + devRate(rate.first), scheme.primary)
                DevPill("↑ " + devRate(rate.second), scheme.primary)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Kablolu ya da Wi-Fi dışı bağlantı: sinyal bilgisi yok.",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
        } else {
            Text(
                "Anlık hız ölçülüyor ya da bu cihaz için KediRay sayacı yok.",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
        }
        if (todayBytes != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                "Bugün KediRay üzerinden: ${devBytes(todayBytes)}",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DeviceStat(label: String, count: Int, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.16f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.75f))
        Text("$count", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DevicesTab(state: UiState, settings: AppSettings, vm: PanelViewModel) {
    var revealed by remember { mutableStateOf(setOf<String>()) }
    var blockTarget by remember { mutableStateOf<Device?>(null) }
    val owner = LocalLifecycleOwner.current

    // Sayfa görünürken Wi-Fi istemci sayaçlarını 3 sn'de bir okuyup anlık hızı hesapla.
    LaunchedEffect(owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                vm.refreshWifiClients()
                delay(3000)
            }
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            val vpnN = state.devices.count { it.mode == "vpn" }
            val directN = state.devices.count { it.mode == "direct" }
            val blockN = state.devices.count { it.mode == "block" || it.mac.uppercase() in state.blocked }
            SectionCard(brush = HeaderBrush) {
                Text("Bağlı cihazlar", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.75f))
                Text(
                    "${state.devices.size}",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DeviceStat("VPN", vpnN, Modifier.weight(1f))
                    DeviceStat("Direkt", directN, Modifier.weight(1f))
                    DeviceStat("Engelli", blockN, Modifier.weight(1f))
                }
            }
        }
        item {
            SectionCard {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        CardTitle(R.drawable.ic_nav_devices, "Bağlı cihazlar (${state.devices.size})")
                        Text(
                            "IP ve MAC adreslerini gizle; bir cihaza dokununca o cihazın bilgisi açılır.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(checked = settings.hideDeviceData, onCheckedChange = vm::setHideDeviceData)
                }
            }
        }
        state.shares?.let { s -> item { DeviceDonutCard(s) } }
        items(state.devices, key = { it.mac + it.ip }) { d ->
            val blocked = d.mac.uppercase() in state.blocked
            val show = !settings.hideDeviceData || d.mac in revealed
            SectionCard {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            revealed = if (d.mac in revealed) revealed - d.mac else revealed + d.mac
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconBadge(
                        icon = if (blocked) R.drawable.ic_block else R.drawable.ic_nav_devices,
                        tint = if (blocked) Bad else MaterialTheme.colorScheme.primary,
                        background = (if (blocked) Bad else MaterialTheme.colorScheme.primary).copy(alpha = 0.14f),
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            d.hostname.ifEmpty { "(adsız cihaz)" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            (if (show) d.ip else maskIp(d.ip)) + " · " + (if (show) d.mac else maskMac(d.mac)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (blocked) Chip("Engelli", Bad)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Bağlı süre: " + ((state.wifiClients.firstOrNull { c -> c.mac.equals(d.mac, true) }?.secs ?: d.connectedSecs)?.let { fmtDuration(it) } ?: "—"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                LiveBox(
                    client = state.wifiClients.firstOrNull { c -> c.mac.equals(d.mac, true) },
                    rate = state.ipRates[d.ip] ?: state.deviceRates[d.mac.uppercase()],
                    todayBytes = state.shares?.devices?.firstOrNull { it.ip == d.ip }?.bytes,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Yön:", style = MaterialTheme.typography.bodySmall)
                    listOf("vpn" to "VPN", "direct" to "Direkt", "block" to "Engelle").forEach { (m, label) ->
                        FilterChip(
                            selected = d.mode == m,
                            onClick = { if (d.mode != m) vm.setDeviceMode(d, m) },
                            label = { Text(label) },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (blocked) {
                    OutlinedButton(onClick = { vm.blockDevice(d, false) }) { Text("Engeli kaldır") }
                } else {
                    OutlinedButton(onClick = { blockTarget = d }, colors = ButtonDefaults.outlinedButtonColors(contentColor = Bad)) { Text("Engelle") }
                }
            }
        }
    }

    blockTarget?.let { d ->
        AlertDialog(
            onDismissRequest = { blockTarget = null },
            title = { Text("Cihaz engellensin mi?") },
            text = {
                Text(
                    "\"${d.hostname.ifEmpty { "Cihaz" }}\" Wi-Fi ağlarına bağlanamaz hale gelir. " +
                        "Wi-Fi birkaç saniye yeniden başlar ve bağlı herkesin bağlantısı kısa süre kesilir. " +
                        "Kablolu cihazlar bu yöntemle engellenemez.",
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.blockDevice(d, true); blockTarget = null }) { Text("Engelle", color = Bad) }
            },
            dismissButton = { TextButton(onClick = { blockTarget = null }) { Text("Vazgeç") } },
        )
    }
}
