package com.saban.aw1kpanel.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saban.aw1kpanel.data.SpeedResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ln

/** Göstergede 0-1000 Mbps; küçük hızlar daha geniş yer kaplasın diye logaritmik. */
private fun gaugeFrac(v: Float): Float = (ln(1f + v.coerceAtLeast(0f)) / ln(1001f)).coerceIn(0f, 1f)

private fun fmtSpeed(v: Float?): String = when {
    v == null -> "—"
    v >= 100f -> "%.0f".format(Locale.US, v)
    v >= 10f -> "%.1f".format(Locale.US, v)
    else -> "%.2f".format(Locale.US, v)
}

private fun fmtMs(v: Float?): String = if (v == null) "—" else "%.0f ms".format(Locale.US, v)

private val dateFmt get() = SimpleDateFormat("d MMM yyyy · HH:mm", Locale("tr"))

@Composable
private fun SpeedGauge(value: Float, label: String, accent: Color) {
    val scheme = MaterialTheme.colorScheme
    val anim by animateFloatAsState(value, tween(600), label = "gauge")
    val frac = gaugeFrac(anim)
    val track = scheme.onSurface.copy(alpha = 0.10f)
    Box(
        Modifier.widthIn(max = 340.dp).fillMaxWidth().aspectRatio(1f),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = size.minDimension * 0.075f
            val d = size.minDimension - stroke * 2
            val tl = Offset((size.width - d) / 2f, (size.height - d) / 2f)
            val sz = Size(d, d)
            drawArc(track, 135f, 270f, false, tl, sz, style = Stroke(stroke, cap = StrokeCap.Round))
            if (frac > 0.003f) {
                drawArc(accent.copy(alpha = 0.22f), 135f, 270f * frac, false, tl, sz, style = Stroke(stroke * 1.9f, cap = StrokeCap.Round))
                drawArc(accent, 135f, 270f * frac, false, tl, sz, style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
            Text(fmtSpeed(anim), fontSize = 54.sp, fontWeight = FontWeight.Bold, color = scheme.onSurface, maxLines = 1)
            Text("Mbps", style = MaterialTheme.typography.titleSmall, color = accent, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun PhaseSteps(phase: String, down: Color, up: Color) {
    val order = mapOf("starting" to 0, "ping" to 0, "down" to 1, "up" to 2, "done" to 3)
    val cur = order[phase] ?: -1
    val names = listOf("Ping", "İndirme", "Yükleme")
    val cols = listOf(MaterialTheme.colorScheme.primary, down, up)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        names.forEachIndexed { i, n ->
            val on = cur == i
            val done = cur > i
            val c = cols[i]
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(50))
                    .background(if (on) c.copy(alpha = 0.22f) else if (done) c.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    (if (done) "✓ " else "") + n,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                    color = if (on || done) c else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun SpeedChart(list: List<SpeedResult>, down: Color, up: Color) {
    val items = list.take(14).reversed()
    val mx = (items.maxOfOrNull { maxOf(it.down, it.up) } ?: 1f).coerceAtLeast(1f)
    Canvas(Modifier.fillMaxWidth().height(96.dp)) {
        val n = items.size
        val slot = size.width / n
        val bw = (slot * 0.32f).coerceAtMost(14.dp.toPx())
        val r = CornerRadius(bw / 2f, bw / 2f)
        items.forEachIndexed { i, it ->
            val cx = slot * (i + 0.5f)
            val hd = size.height * (it.down / mx).coerceIn(0.02f, 1f)
            val hu = size.height * (it.up / mx).coerceIn(0.02f, 1f)
            drawRoundRect(down, Offset(cx - bw - 1.dp.toPx(), size.height - hd), Size(bw, hd), r)
            drawRoundRect(up, Offset(cx + 1.dp.toPx(), size.height - hu), Size(bw, hu), r)
        }
    }
}

@Composable
internal fun SpeedTab(state: UiState, vm: PanelViewModel) {
    LaunchedEffect(Unit) { vm.loadSpeedHistory() }
    val scheme = MaterialTheme.colorScheme
    val downC = scheme.primary
    val upC = scheme.tertiary
    val st = state.speed
    val last = state.speedHistory.firstOrNull()
    var confirmClear by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<SpeedResult?>(null) }

    val shownDown = if (st.phase == "done" || st.phase == "error" || st.phase == "idle") (st.down ?: last?.down) else st.down
    val shownUp = if (st.phase == "done" || st.phase == "error" || st.phase == "idle") (st.up ?: last?.up) else st.up
    val shownPing = if (st.running || st.phase == "done") st.ping else (st.ping ?: last?.ping)
    val shownJit = if (st.running || st.phase == "done") st.jitter else (st.jitter ?: last?.jitter)

    val (gv, gl, gc) = when (st.phase) {
        "down" -> Triple(st.live, "İndirme", downC)
        "up" -> Triple(st.live, "Yükleme", upC)
        "starting", "ping" -> Triple(0f, "Gecikme ölçülüyor", scheme.primary)
        else -> Triple(shownDown ?: 0f, "İndirme", downC)
    }

    LazyColumn(
        contentPadding = PaddingValues(top = 8.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                SpeedGauge(gv, gl, gc)
                val where = listOf(st.server.ifBlank { last?.server.orEmpty() }, st.isp.ifBlank { last?.isp.orEmpty() })
                    .filter { it.isNotBlank() }.joinToString(" · ")
                Text(
                    if (where.isNotBlank()) "Modemden · $where" else "Modemin kendi internet hızı",
                    style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
                    textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        item { PhaseSteps(st.phase, downC, upC) }
        item {
            if (st.running) {
                androidx.compose.material3.OutlinedButton(
                    onClick = vm::stopSpeedTest,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(28.dp),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, Bad),
                ) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Bad)
                    Spacer(Modifier.padding(start = 10.dp))
                    Text("DURDUR", color = Bad, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
            } else {
                Button(
                    onClick = vm::startSpeedTest,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(28.dp),
                ) {
                    Text(if (st.phase == "done" || last != null) "TEKRAR TEST ET" else "TESTİ BAŞLAT", fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
            }
        }
        if (st.phase == "error") {
            item {
                Text(
                    st.error.ifBlank { "Test tamamlanamadı" },
                    color = Bad, style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Bad.copy(alpha = 0.12f)).padding(12.dp),
                )
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    KTile("↓ İNDİRME", fmtSpeed(shownDown) + if (shownDown != null) " Mbps" else "", Modifier.weight(1f), valueColor = downC)
                    KTile("↑ YÜKLEME", fmtSpeed(shownUp) + if (shownUp != null) " Mbps" else "", Modifier.weight(1f), valueColor = upC)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    KTile("PİNG", fmtMs(shownPing), Modifier.weight(1f))
                    KTile("TİTREŞİM", fmtMs(shownJit), Modifier.weight(1f))
                }
            }
        }
        if (state.speedHistory.size >= 2) {
            item {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(scheme.surfaceVariant.copy(alpha = 0.45f)).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Son testler", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text("● İndirme", color = downC, style = MaterialTheme.typography.labelSmall)
                        Spacer(Modifier.padding(start = 10.dp))
                        Text("● Yükleme", color = upC, style = MaterialTheme.typography.labelSmall)
                    }
                    SpeedChart(state.speedHistory, downC, upC)
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Geçmiş", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (state.speedHistory.isNotEmpty()) {
                    TextButton(onClick = { confirmClear = true }) { Text("Tümünü sil", color = Bad) }
                }
            }
        }
        if (state.speedHistory.isEmpty()) {
            item {
                Text(
                    "Henüz kayıtlı test yok. Sonuçlar bu telefonda saklanır.",
                    style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant,
                )
            }
        }
        items(state.speedHistory, key = { it.ts }) { r ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(scheme.surfaceVariant.copy(alpha = 0.55f))
                    .clickable { detail = r }
                    .padding(start = 14.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("↓ " + fmtSpeed(r.down) + " Mbps", color = downC, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text("↑ " + fmtSpeed(r.up) + " Mbps", color = upC, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                    Text(
                        fmtMs(r.ping) + " · " + dateFmt.format(Date(r.ts)) + listOf(r.server, r.isp).filter { it.isNotBlank() }.joinToString(" · ").let { if (it.isBlank()) "" else " · $it" },
                        style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                TextButton(onClick = { vm.deleteSpeedResult(r.ts) }) { Text("✕", color = scheme.onSurfaceVariant) }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Geçmiş silinsin mi?") },
            text = { Text("Kayıtlı ${state.speedHistory.size} hız testi sonucu silinecek.") },
            confirmButton = {
                TextButton(onClick = { vm.clearSpeedHistory(); confirmClear = false }) { Text("Sil", color = Bad) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Vazgeç") } },
        )
    }

    detail?.let { r ->
        val rows = listOf(
            "↓ İndirme" to fmtSpeed(r.down) + " Mbps",
            "↑ Yükleme" to fmtSpeed(r.up) + " Mbps",
            "Ping" to fmtMs(r.ping),
            "Titreşim" to fmtMs(r.jitter),
            "Sunucu" to r.server,
            "Servis sağlayıcı" to r.isp,
            "IP" to r.ip,
        ).filter { it.second.isNotBlank() && it.second != "—" }
        AlertDialog(
            onDismissRequest = { detail = null },
            title = { Text(dateFmt.format(Date(r.ts))) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    rows.forEach { (k, v) ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                            Text(k, color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.45f))
                            Text(v, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End, modifier = Modifier.weight(0.55f))
                        }
                    }
                    Text("Modemden ölçüldü", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                }
            },
            confirmButton = { TextButton(onClick = { detail = null }) { Text("Kapat") } },
            dismissButton = { TextButton(onClick = { vm.deleteSpeedResult(r.ts); detail = null }) { Text("Sil", color = Bad) } },
        )
    }
}
