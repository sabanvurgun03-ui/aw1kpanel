package com.saban.aw1kpanel.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saban.aw1kpanel.data.KediNode
import com.saban.aw1kpanel.data.KediPing
import com.saban.aw1kpanel.data.ModemState
import com.saban.aw1kpanel.data.SystemState

// ---- Ortak parçalar ------------------------------------------------------

/** Yanıp sönen durum noktası (çalışıyorsa yeşil nabız, değilse sabit kırmızı). */
@Composable
private fun PulseDot(on: Boolean, size: Dp = 10.dp) {
    val t = rememberInfiniteTransition(label = "nabiz")
    val a by t.animateFloat(1f, 0.35f, infiniteRepeatable(tween(950), RepeatMode.Reverse), label = "nabizA")
    val c = if (on) Color(0xFF5BE584) else Color(0xFFFF7A7A)
    Box(Modifier.size(size).clip(CircleShape).background(c.copy(alpha = if (on) a else 1f)))
}

/** Dairesel gösterge: yay [fraction] kadar dolar, ortasına [content] yazılır. */
@Composable
private fun RingGauge(
    fraction: Float,
    color: Color,
    track: Color,
    size: Dp,
    stroke: Dp,
    content: @Composable () -> Unit,
) {
    val f by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(700), label = "halka")
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val s = stroke.toPx()
            val st = Stroke(width = s, cap = StrokeCap.Round)
            val inset = s / 2
            val arcSize = androidx.compose.ui.geometry.Size(this.size.width - s, this.size.height - s)
            val tl = androidx.compose.ui.geometry.Offset(inset, inset)
            drawArc(track, 135f, 270f, false, tl, arcSize, style = st)
            if (f > 0f) drawArc(color, 135f, 270f * f, false, tl, arcSize, style = st)
        }
        content()
    }
}

/** Degrade kartın üstünde yarı saydam "cam" kutu. */
@Composable
private fun RowScope.GlassTile(label: String, value: String, arrow: String) {
    Column(
        Modifier
            .weight(1f)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.14f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text("$arrow $label", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.78f))
        Text(
            value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun GlassChip(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = Color.White,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.16f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

private fun homeTech(m: ModemState): String =
    if (m.carriers > 1 && m.networkMode.uppercase().startsWith("LTE") && !m.networkMode.contains("-A")) {
        "LTE-A (${m.carriers}CA)"
    } else {
        m.networkMode
    }

private fun homeRate(b: Long): String = when {
    b >= 1_048_576 -> "%.1f MB/s".format(b / 1_048_576.0)
    b >= 1024 -> "%.1f KB/s".format(b / 1024.0)
    else -> "$b B/s"
}

private fun homeDuration(s: Long): String = durFull(s)

// ---- 1) Modem kartı --------------------------------------------------------

@Composable
internal fun HomeModemCard(m: ModemState, error: String?) {
    val (statusText, statusColor) = when {
        error != null -> "Bağlantı sorunu" to Color(0xFFFCA5A5)
        m.connected -> "Normal" to Color(0xFF86EFAC)
        else -> "Bağlı değil" to Color(0xFFFCA5A5)
    }
    SectionCard(brush = HeaderBrush) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    m.operatorName,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.9f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PulseDot(m.connected && error == null, 12.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        statusText,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    GlassChip(homeTech(m))
                    if (m.cells.isNotEmpty()) GlassChip(m.cells.joinToString(" + ") { it.band })
                }
            }
            // Sinyal kalitesi halkası
            RingGauge(
                fraction = (m.signalPercent ?: 0) / 100f,
                color = Color.White,
                track = Color.White.copy(alpha = 0.18f),
                size = 84.dp,
                stroke = 8.dp,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        m.signalPercent?.let { "%$it" } ?: "—",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                    Text("sinyal", fontSize = 11.sp, color = Color.White.copy(alpha = 0.75f))
                }
            }
        }
        if (error != null) {
            Text(error, style = MaterialTheme.typography.bodySmall, color = Color(0xFFFCA5A5), modifier = Modifier.padding(top = 6.dp))
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassTile("İndirme", m.rxRate, "↓")
            GlassTile("Yükleme", m.txRate, "↑")
        }
        val extra = listOfNotNull(
            m.temperature.takeIf { it.isNotBlank() && it != "—" }?.let { "🌡 $it" },
            m.connSeconds?.takeIf { it > 0 }?.let { "Bağlantı ${homeDuration(it)}" },
        )
        if (extra.isNotEmpty()) {
            Text(
                extra.joinToString("  ·  "),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.75f),
                modifier = Modifier.padding(top = 10.dp, start = 2.dp),
            )
        }
    }
}

// ---- 2) Sistem kartı ------------------------------------------------------

@Composable
internal fun HomeSystemCard(s: SystemState?, d: com.saban.aw1kpanel.data.SysDetails? = null) {
    val scheme = MaterialTheme.colorScheme
    var details by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Sistem", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (s != null) {
                Text(
                    "Yük %.2f".format(s.load1),
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(scheme.surfaceVariant)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        if (s == null) {
            Text("Okunuyor…", color = scheme.onSurfaceVariant)
            return@SectionCard
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Top) {
            val cpu = s.cpuPercent ?: 0
            GaugeBlock("CPU", s.cpuPercent?.let { "%$it" } ?: "—", cpu / 100f, loadColor(cpu), null)
            GaugeBlock(
                "Bellek", "%${s.memPercent}", s.memPercent / 100f, loadColor(s.memPercent),
                "${s.memUsed / 1_048_576} / ${s.memTotal / 1_048_576} MB",
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(96.dp)) {
                Box(
                    Modifier.size(78.dp).clip(CircleShape).background(scheme.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("⏱", fontSize = 26.sp)
                }
                Spacer(Modifier.height(6.dp))
                Text(durFull(s.uptimeSeconds, 2), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text("Çalışma süresi", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
            }
        }
        if (d != null) {
            TextButton(onClick = { details = !details }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text(if (details) "Detayları gizle ▴" else "Detaylar ▾")
            }
            androidx.compose.animation.AnimatedVisibility(visible = details) {
                SysDetailsBlock(d)
            }
        }
    }
}

private fun mb(b: Long): String = when {
    b >= 1L shl 30 -> "%.1f GB".format(b / 1073741824.0)
    else -> "%.1f MB".format(b / 1048576.0)
}

@Composable
private fun SysDetailsBlock(d: com.saban.aw1kpanel.data.SysDetails) {
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KTile("RAM kullanılan", mb(d.memUsed), Modifier.weight(1f), sub = "toplam ${mb(d.memTotal)}",
                bar = if (d.memTotal > 0) d.memUsed.toFloat() / d.memTotal else null, barColor = loadColor(if (d.memTotal > 0) (d.memUsed * 100 / d.memTotal).toInt() else 0),
                pct = if (d.memTotal > 0) (d.memUsed * 100 / d.memTotal).toInt() else null)
            KTile("RAM önbellek", mb(d.cached), Modifier.weight(1f), sub = "gerektiğinde boşalır")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val cf = if (d.conn != null && d.connMax != null && d.connMax > 0) d.conn.toFloat() / d.connMax else null
            KTile(
                "Bağlantılar", d.conn?.toString() ?: "—", Modifier.weight(1f),
                sub = d.connMax?.let { "en çok $it" },
                bar = cf, barColor = loadColor(((cf ?: 0f) * 100).toInt()),
            )
            val rf = if (d.rootFree != null && d.rootTotal != null && d.rootTotal > 0) 1f - d.rootFree.toFloat() / d.rootTotal else null
            KTile(
                "Depolama (boş)", d.rootFree?.let { mb(it) } ?: "—", Modifier.weight(1f),
                sub = d.rootTotal?.let { "toplam ${mb(it)}" },
                bar = rf, barColor = loadColor(((rf ?: 0f) * 100).toInt()),
                pct = rf?.let { (it * 100).toInt() },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val tf = if (d.tmpFree != null && d.tmpTotal != null && d.tmpTotal > 0) 1f - d.tmpFree.toFloat() / d.tmpTotal else null
            KTile(
                "Geçici bellek (boş)", d.tmpFree?.let { mb(it) } ?: "—", Modifier.weight(1f),
                sub = d.tmpTotal?.let { "toplam ${mb(it)}" },
                bar = tf, barColor = loadColor(((tf ?: 0f) * 100).toInt()),
                pct = tf?.let { (it * 100).toInt() },
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(scheme.surfaceVariant.copy(alpha = 0.45f))
                .padding(horizontal = 14.dp, vertical = 6.dp),
        ) {
            listOf(
                "Yazılım" to d.release,
                "Çekirdek" to d.kernel,
                "Model" to d.model,
                "İşlemci" to d.cpu,
            ).filter { it.second != null }.forEachIndexed { i, (k, v) ->
                if (i > 0) KDivider()
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(k, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, modifier = Modifier.width(88.dp))
                    Text(
                        v!!,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.End,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

private fun loadColor(p: Int): Color = when {
    p < 60 -> Good
    p < 85 -> Warn
    else -> Bad
}

@Composable
private fun GaugeBlock(label: String, value: String, fraction: Float, color: Color, sub: String?) {
    val scheme = MaterialTheme.colorScheme
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(96.dp)) {
        RingGauge(fraction, color, scheme.surfaceVariant, 78.dp, 8.dp) {
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(sub ?: " ", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant, maxLines = 1)
    }
}

// ---- 3) VPN kartı --------------------------------------------------------

/**
 * Ana sayfa VPN kartı: durum + canlı hız, sunucu seçimi ve ping. Düzenleme / silme / ekleme VPN sekmesinde kalır.
 * KediRay kuruluysa onun sunucuları, değilse xDivine-Ray profilleri listelenir.
 */
@Composable
internal fun HomeVpnCard(state: UiState, vm: PanelViewModel, onOpenVpn: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val k = state.kediray
    val v = state.vpn
    val connected = v?.connected == true
    SectionCard {
        // Başlık
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(scheme.primary.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) { Text("🛡", fontSize = 20.sp) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "VPN" + (v?.provider?.let { " · $it" } ?: ""),
                    style = MaterialTheme.typography.labelLarge,
                    color = scheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PulseDot(connected)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when {
                            v == null -> "Durum okunamadı"
                            connected -> "Bağlı"
                            else -> "Bağlı değil"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (connected) Good else if (v == null) scheme.onSurfaceVariant else Bad,
                    )
                }
            }
            if (connected) {
                Text(
                    durFull(v!!.uptimeSeconds, 2),
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(scheme.surfaceVariant)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }

        if (connected) {
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SpeedTile("↓ İndirme", homeRate(v!!.downlinkRate), Modifier.weight(1f))
                SpeedTile("↑ Yükleme", homeRate(v.uplinkRate), Modifier.weight(1f))
            }
        }

        // Sunucu seçimi + ping
        if (k != null) {
            val nodes = k.nodes.filter { it.enabled }
            if (nodes.isNotEmpty()) {
                val allBusy = "all" in state.kediPinging
                SectionHeader("Sunucu", if (allBusy) "Ölçülüyor…" else "⚡ Tümünü test et", !allBusy) { vm.pingKedi(null) }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    nodes.forEach { n ->
                        ServerRow(
                            name = n.name,
                            tags = listOf(n.protocol, n.network).filter { it.isNotEmpty() }.joinToString(" · "),
                            active = n.section == k.activeSection,
                            ping = state.kediPings[n.section],
                            busy = allBusy || n.section in state.kediPinging,
                            onPick = { vm.useKediNode(n.section, n.name) },
                            onPing = { vm.pingKedi(n.section) },
                        )
                    }
                }
            }
        } else if (state.profiles.isNotEmpty()) {
            SectionHeader("Profil", null, false) {}
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.profiles.forEach { p ->
                    val ping = state.pings[p.id]
                    ServerRow(
                        name = p.name,
                        tags = listOf(p.proto, p.network).filter { it.isNotEmpty() }.joinToString(" · "),
                        active = p.id == state.activeProfileId,
                        ping = ping?.let { KediPing(null, it.ms) } ?: null,
                        busy = p.id in state.pinging,
                        onPick = { vm.connectProfile(p.id) },
                        onPing = { vm.pingProfile(p.id) },
                    )
                }
            }
        }

        Spacer(Modifier.height(4.dp))
        TextButton(onClick = onOpenVpn, modifier = Modifier.align(Alignment.End)) {
            Text("Düzenle ve ayarlar ›")
        }
    }
}

@Composable
private fun SectionHeader(title: String, action: String?, enabled: Boolean, onAction: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        if (action != null) TextButton(onClick = onAction, enabled = enabled) { Text(action) }
    }
}

@Composable
private fun SpeedTile(label: String, value: String, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(scheme.surfaceVariant.copy(alpha = 0.7f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

/** Dokununca o sunucuya geçer; sağdaki rozete dokununca ping ölçer. */
@Composable
private fun ServerRow(
    name: String,
    tags: String,
    active: Boolean,
    ping: KediPing?,
    busy: Boolean,
    onPick: () -> Unit,
    onPing: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val bg by animateColorAsState(
        if (active) scheme.primaryContainer.copy(alpha = 0.55f) else scheme.surfaceVariant.copy(alpha = 0.45f),
        tween(250), label = "satirBg",
    )
    Surface(
        onClick = onPick,
        enabled = !active,
        shape = RoundedCornerShape(18.dp),
        color = bg,
        border = if (active) BorderStroke(1.5.dp, scheme.primary) else null,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Seçim göstergesi
            Box(
                Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(if (active) scheme.primary else Color.Transparent)
                    .border(2.dp, if (active) scheme.primary else scheme.outline, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (active) Text("✓", fontSize = 12.sp, color = scheme.onPrimary, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (tags.isNotEmpty()) {
                    Text(tags, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant, maxLines = 1)
                }
            }
            PingPill(ping, busy, onPing)
        }
    }
}

@Composable
private fun PingPill(p: KediPing?, busy: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val ms = p?.rttMs ?: p?.tunnelMs
    val (text, color) = when {
        busy -> "…" to scheme.onSurfaceVariant
        p == null -> "Ping" to scheme.primary
        ms == null -> "Hata" to Bad
        ms < 60 || (p.rttMs == null && ms < 150) -> "$ms ms" to Good
        ms < 150 || (p.rttMs == null && ms < 400) -> "$ms ms" to Warn
        else -> "$ms ms" to Bad
    }
    Surface(
        onClick = onClick,
        enabled = !busy,
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.14f),
        modifier = Modifier.padding(start = 8.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}
