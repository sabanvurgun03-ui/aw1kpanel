package com.saban.aw1kpanel.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
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

private fun scanClassShort(c: String) = when (c) {
    "NR_NSA" -> "5G NSA"
    "NR" -> "5G SA"
    "LTE" -> "4G LTE"
    else -> c
}

@Composable
private fun SmartLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
}

@Composable
private fun Stepper(value: Int, enabled: Boolean, onMinus: () -> Unit, onPlus: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(scheme.surfaceVariant),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onMinus, enabled = enabled) { Text("−", style = MaterialTheme.typography.titleMedium) }
        Text("$value sn", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(52.dp), textAlign = TextAlign.Center)
        TextButton(onClick = onPlus, enabled = enabled) { Text("+", style = MaterialTheme.typography.titleMedium) }
    }
}

/** Her bandı tek tek kilitleyip hız/sinyal ölçen, en iyisini seçen kart. */
@OptIn(ExperimentalLayoutApi::class)
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
        KHeader(
            title = "Akıllı band seçimi",
            subtitle = "Bantları tek tek dener, en hızlısını seçer",
            emoji = "⚡",
            trailing = { KPill(if (useSpeed) "SPEEDTEST" else "SİNYAL") },
        )
        Text(
            "Telefon modemin Wi-Fi ağında olmalı. Her denemede bağlantı kısa süre kesilir.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )

        SmartLabel("Şebeke tipi")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            classes.forEach { c ->
                KToggle(scanClassShort(c), c == selCls, onClick = { cls = c }, enabled = !busy, modifier = Modifier.weight(1f))
            }
        }

        Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Denenecek bantlar · ${chosen.size}/${allIds.size}",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { picked = allIds.toSet() }, enabled = !busy) { Text("Tümü") }
            TextButton(onClick = { picked = emptySet() }, enabled = !busy) { Text("Hiçbiri") }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            group.available.forEach { (id, name) ->
                KToggle(shortBand(name), id in picked, onClick = { picked = if (id in picked) picked - id else picked + id }, enabled = !busy)
            }
        }

        SmartLabel("Ölçüm")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KToggle("Speedtest (önerilen)", useSpeed, onClick = { useSpeed = true }, enabled = !busy, modifier = Modifier.weight(1f))
            KToggle("Sadece sinyal", !useSpeed, onClick = { useSpeed = false }, enabled = !busy, modifier = Modifier.weight(1f))
        }

        Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Bekleme / aday", style = MaterialTheme.typography.labelLarge)
                Text("Her bantta ölçümden önce", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Stepper(waitSec, enabled = !busy, onMinus = { waitSec = (waitSec - 5).coerceAtLeast(10) }, onPlus = { waitSec = (waitSec + 5).coerceAtMost(60) })
        }
        KSwitchRow("En iyisini otomatik uygula", null, autoApply, { autoApply = it }, enabled = !busy)

        Spacer(Modifier.height(6.dp))
        if (busy) {
            Button(
                onClick = { vm.cancelBandScan() },
                colors = ButtonDefaults.buttonColors(containerColor = Bad),
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(25.dp),
            ) { Text("İptal") }
        } else {
            Button(
                onClick = { confirm = true },
                enabled = chosen.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(25.dp),
            ) { Text("⚡ Taramayı başlat") }
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
            Spacer(Modifier.height(14.dp))
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
    val scheme = MaterialTheme.colorScheme
    if (scan.running) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                scan.phase.ifEmpty { "Çalışıyor…" },
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.primary,
                modifier = Modifier.weight(1f),
            )
            Text("${scan.done}/${scan.total}", style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { if (scan.total == 0) 0f else scan.done.toFloat() / scan.total },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)),
        )
    }
    scan.note?.let {
        Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))
    }
    if (scan.rows.isEmpty()) return

    Spacer(Modifier.height(10.dp))
    val sorted = scan.rows.sortedWith(compareByDescending<com.saban.aw1kpanel.data.ScanRow> { it.ok }.thenByDescending { it.score })
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        sorted.forEach { r ->
            val best = r.id == scan.bestId && r.ok
            androidx.compose.material3.Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (best) Good.copy(alpha = 0.12f) else scheme.surfaceVariant.copy(alpha = 0.55f),
                border = if (best) androidx.compose.foundation.BorderStroke(1.5.dp, Good) else null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(shortBand(r.name), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            if (best) KPill("EN İYİ", Good)
                        }
                        if (r.note.isNotEmpty() && !r.ok) {
                            Text(r.note, style = MaterialTheme.typography.labelSmall, color = Bad)
                        } else {
                            Text(
                                "SNR ${f1(r.sinr)} · RSRP ${f1(r.rsrp)}" + if (r.used.isNotEmpty() && r.used != r.name) " · ${r.used}" else "",
                                style = MaterialTheme.typography.labelSmall,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (r.ok || r.mbps != null) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                if (r.mbps == null) "—" else f1(r.mbps),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (best) Good else scheme.onSurface,
                            )
                            Text(if (r.mbps == null) "skor %.0f".format(r.score) else "Mbps", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
