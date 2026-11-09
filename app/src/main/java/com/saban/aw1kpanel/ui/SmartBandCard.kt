package com.saban.aw1kpanel.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.saban.aw1kpanel.R
import com.saban.aw1kpanel.data.BandScanState

private fun scanClassLabel(c: String) = when (c) {
    "NR_NSA" -> "5G NSA (4G çapa + 5G)"
    "NR" -> "5G SA"
    "LTE" -> "LTE (4G)"
    else -> c
}

private fun f1(v: Float?) = if (v == null) "—" else "%.1f".format(v)

/** Her bandı tek tek kilitleyip hız/sinyal ölçen, en iyisini seçen kart. */
@Composable
internal fun SmartBandCard(state: UiState, vm: PanelViewModel) {
    val lb = state.lockband ?: return
    val scan = state.bandScan
    val classes = listOf("NR_NSA", "NR", "LTE").filter { lb.containsKey(it) }
    if (classes.isEmpty()) return

    var cls by remember { mutableStateOf(classes.first()) }
    val selCls = if (cls in classes) cls else classes.first()
    val group = lb.getValue(selCls)
    val allIds = group.available.map { it.first }
    var picked by remember(selCls, allIds) { mutableStateOf(allIds.toSet()) }
    var waitSec by remember { mutableStateOf(15) }
    var useSpeed by remember { mutableStateOf(true) }
    var autoApply by remember { mutableStateOf(true) }
    var confirm by remember { mutableStateOf(false) }
    val busy = scan.running
    val chosen = allIds.filter { it in picked }

    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CardTitle(R.drawable.ic_nav_band, "Akıllı band seçimi")
            Chip("SPEEDTEST")
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Seçtiğin bantları tek tek kilitler, her birinde hız ve sinyali ölçer, en hızlısını seçer. " +
                "Telefon modemin Wi-Fi ağında olmalı. Her denemede bağlantı kısa süre kesilir.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(10.dp))
        Text("5G tipi", style = MaterialTheme.typography.labelLarge)
        classes.forEach { c ->
            Row(
                Modifier.fillMaxWidth().clickable(enabled = !busy) { cls = c },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = c == selCls, onClick = { cls = c }, enabled = !busy)
                Text(scanClassLabel(c), style = MaterialTheme.typography.bodyMedium)
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Denenecek bantlar", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = { picked = allIds.toSet() }, enabled = !busy) { Text("Tümü") }
            TextButton(onClick = { picked = emptySet() }, enabled = !busy) { Text("Kaldır") }
        }
        group.available.chunked(3).forEach { triple ->
            Row(Modifier.fillMaxWidth()) {
                triple.forEach { (id, name) ->
                    Row(
                        Modifier.weight(1f).clickable(enabled = !busy) {
                            picked = if (id in picked) picked - id else picked + id
                        },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = id in picked,
                            onCheckedChange = { picked = if (it) picked + id else picked - id },
                            enabled = !busy,
                        )
                        Text(name, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                repeat(3 - triple.size) { Spacer(Modifier.weight(1f)) }
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Bekleme / aday (sn)", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = { waitSec = (waitSec - 5).coerceAtLeast(10) }, enabled = !busy) { Text("−") }
            Text(
                "$waitSec",
                modifier = Modifier.width(44.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleMedium,
            )
            OutlinedButton(onClick = { waitSec = (waitSec + 5).coerceAtMost(60) }, enabled = !busy) { Text("+") }
        }

        Spacer(Modifier.height(8.dp))
        Text("Ölçüm", style = MaterialTheme.typography.labelLarge)
        Row(
            Modifier.fillMaxWidth().clickable(enabled = !busy) { useSpeed = true },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = useSpeed, onClick = { useSpeed = true }, enabled = !busy)
            Text("Speedtest (önerilen)", style = MaterialTheme.typography.bodyMedium)
        }
        Row(
            Modifier.fillMaxWidth().clickable(enabled = !busy) { useSpeed = false },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = !useSpeed, onClick = { useSpeed = false }, enabled = !busy)
            Text("Sadece sinyal (hızlı, veri harcamaz)", style = MaterialTheme.typography.bodyMedium)
        }
        Row(
            Modifier.fillMaxWidth().clickable(enabled = !busy) { autoApply = !autoApply },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = autoApply, onCheckedChange = { autoApply = it }, enabled = !busy)
            Text("En iyisini otomatik uygula", style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(Modifier.height(8.dp))
        if (busy) {
            Button(
                onClick = { vm.cancelBandScan() },
                colors = ButtonDefaults.buttonColors(containerColor = Bad),
            ) { Text("İptal") }
        } else {
            Button(onClick = { confirm = true }, enabled = chosen.isNotEmpty()) { Text("Taramayı başlat") }
            if (chosen.isEmpty()) {
                Text(
                    "En az bir bant seç.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Bad,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        if (scan.running || scan.finished) {
            Spacer(Modifier.height(12.dp))
            ScanProgress(scan)
        }
    }

    if (confirm) {
        val minutes = (chosen.size * (waitSec + 6 + (if (useSpeed) 10 else 2)) / 60f).let { kotlin.math.ceil(it).toInt() }
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Tarama başlatılsın mı?") },
            text = {
                Text(
                    "${chosen.size} bant sırayla denenecek (yaklaşık $minutes dk). Her denemede internet kısa süre " +
                        "kesilir. Bitince " + (if (autoApply) "en iyi bant uygulanır" else "eski band kilidi geri yüklenir") +
                        "; iptal edersen de eski kilit geri yüklenir. Tarama sürerken uygulamayı kapatma.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    vm.startBandScan(selCls, chosen, waitSec, useSpeed, autoApply)
                }) { Text("Başlat") }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Vazgeç") } },
        )
    }
}

@Composable
private fun ScanProgress(scan: BandScanState) {
    if (scan.running) {
        Text(
            scan.phase.ifEmpty { "Çalışıyor…" },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { if (scan.total == 0) 0f else scan.done.toFloat() / scan.total },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "${scan.done}/${scan.total}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
    scan.note?.let {
        Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
    }
    if (scan.rows.isEmpty()) return

    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth()) {
        listOf("Bant" to 1.5f, "Hız" to 1.3f, "SNR" to 0.8f, "RSRP" to 0.9f, "Skor" to 1f).forEach { (t, w) ->
            Text(
                t,
                modifier = Modifier.weight(w),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    scan.rows.forEach { r ->
        val best = r.id == scan.bestId && r.ok
        Row(
            Modifier.fillMaxWidth().padding(vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column1(1.5f) {
                Text(
                    r.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (best) FontWeight.Bold else FontWeight.Normal,
                    color = if (best) Good else MaterialTheme.colorScheme.onSurface,
                )
                if (best) Chip("EN İYİ", Good)
                else if (r.used.isNotEmpty() && r.used != r.name) {
                    Text(r.used, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (r.note.isNotEmpty() && !r.ok) {
                Text(
                    r.note,
                    modifier = Modifier.weight(4f),
                    style = MaterialTheme.typography.bodySmall,
                    color = Bad,
                )
            } else {
                Text(
                    if (r.mbps == null) "—" else f1(r.mbps) + " Mbps",
                    modifier = Modifier.weight(1.3f),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(f1(r.sinr), modifier = Modifier.weight(0.8f), style = MaterialTheme.typography.bodyMedium)
                Text(f1(r.rsrp), modifier = Modifier.weight(0.9f), style = MaterialTheme.typography.bodyMedium)
                Text(
                    "%.0f".format(r.score),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Column1(
    weight: Float,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    androidx.compose.foundation.layout.Column(Modifier.weight(weight), content = content)
}
