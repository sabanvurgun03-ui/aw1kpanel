package com.saban.aw1kpanel.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saban.aw1kpanel.R
import com.saban.aw1kpanel.data.DeviceShares
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.hypot

private class DonutSeg(val name: String, val bytes: Long, val color: Color)

private fun donutBytes(b: Long): String = when {
    b >= (1L shl 30) -> "%.2f GB".format(b / 1073741824.0)
    b >= (1L shl 20) -> "%.1f MB".format(b / 1048576.0)
    b >= 1024L -> "%.0f KB".format(b / 1024.0)
    else -> "$b B"
}

/** IP'nin son sekizlisi: renk yuvası IP sırasına göre verilir, böylece cihazın rengi sıralama değişince oynamaz. */
private fun ipKey(ip: String): Long =
    ip.split('.').fold(0L) { acc, p -> acc * 256 + (p.toLongOrNull() ?: 0L) }

private val DonutLight = listOf(0xFF2A78D6, 0xFFEB6834, 0xFF1BAF7A, 0xFFEDA100, 0xFFE87BA4).map { Color(it) }
private val DonutDark = listOf(0xFF3987E5, 0xFFD95926, 0xFF199E70, 0xFFC98500, 0xFFD55181).map { Color(it) }
private val DonutOther = Color(0xFF898781)

/**
 * Bugünkü veri kullanımının cihazlara dağılımı (halka grafiği). En çok kullanan 5 cihaz ayrı dilim, geri kalanlar ve
 * ağdan ayrılmış cihazlar tek "Diğer" dilimidir. Dilime ya da listedeki satıra dokununca ortada o cihaz görünür.
 */
@Composable
internal fun DeviceDonutCard(shares: DeviceShares) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val palette = if (dark) DonutDark else DonutLight

    val top = shares.devices.filter { it.bytes > 0 }.sortedByDescending { it.bytes }.take(5)
    val rest = shares.devices.filter { it.bytes > 0 }.sortedByDescending { it.bytes }.drop(5).sumOf { it.bytes } + shares.othersBytes
    val slotOf = top.sortedBy { ipKey(it.ip) }.mapIndexed { i, d -> d.ip to i }.toMap()
    val segs = top.map { DonutSeg(it.name, it.bytes, palette[slotOf[it.ip] ?: 0]) } +
        (if (rest > 0) listOf(DonutSeg("Diğer", rest, DonutOther)) else emptyList())
    val total = segs.sumOf { it.bytes }

    SectionCard {
        CardTitle(R.drawable.ic_nav_data, "Bugünkü veri payı")
        Spacer(Modifier.height(10.dp))
        if (total <= 0L) {
            Text(
                "Bugün henüz trafik yok.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@SectionCard
        }
        var sel by remember { mutableStateOf<Int?>(null) }
        val cur = sel?.takeIf { it in segs.indices }
        val ink = MaterialTheme.colorScheme.onSurface
        val dim = MaterialTheme.colorScheme.onSurfaceVariant

        Box(Modifier.align(Alignment.CenterHorizontally).size(170.dp), contentAlignment = Alignment.Center) {
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(170.dp)
                    .pointerInput(segs) {
                        detectTapGestures { p ->
                            val cx = size.width / 2f
                            val cy = size.height / 2f
                            val d = hypot(p.x - cx, p.y - cy)
                            val ringW = 22.dp.toPx()
                            val r = cx - 6.dp.toPx() - ringW / 2f
                            if (d < r - ringW || d > r + ringW) {
                                sel = null
                                return@detectTapGestures
                            }
                            // 12 yönünden saat yönünde derece
                            val ang = ((atan2(p.y - cy, p.x - cx) * 180.0 / PI) + 90.0 + 360.0) % 360.0
                            var acc = 0.0
                            var hit: Int? = null
                            segs.forEachIndexed { i, s ->
                                val sweep = s.bytes.toDouble() / total * 360.0
                                if (hit == null && ang >= acc && ang < acc + sweep) hit = i
                                acc += sweep
                            }
                            sel = if (hit == sel) null else hit
                        }
                    },
            ) {
                val ringW = 22.dp.toPx()
                val pad = 6.dp.toPx()
                val diameter = size.width - pad * 2 - ringW
                val topLeft = Offset(pad + ringW / 2f, pad + ringW / 2f)
                val gapDeg = if (segs.size > 1) (2.dp.toPx() / (diameter / 2f)) * 180f / PI.toFloat() else 0f
                var start = -90f
                segs.forEachIndexed { i, s ->
                    val sweep = (s.bytes.toFloat() / total.toFloat()) * 360f
                    val w = if (i == cur) ringW + 4.dp.toPx() else ringW
                    val a = if (cur != null && i != cur) 0.45f else 1f
                    drawArc(
                        color = s.color.copy(alpha = a),
                        startAngle = start + gapDeg / 2f,
                        sweepAngle = (sweep - gapDeg).coerceAtLeast(0.5f),
                        useCenter = false,
                        topLeft = topLeft,
                        size = Size(diameter, diameter),
                        style = Stroke(width = w),
                    )
                    start += sweep
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(96.dp)) {
                Text(
                    donutBytes(if (cur != null) segs[cur].bytes else total),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ink,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
                Text(
                    if (cur != null) segs[cur].name else "bugün",
                    fontSize = 11.sp,
                    color = dim,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            segs.forEachIndexed { i, s ->
                val pct = Math.round(s.bytes.toDouble() / total * 100).toInt()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            if (i == cur) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f) else Color.Transparent,
                            RoundedCornerShape(8.dp),
                        )
                        .clickable { sel = if (sel == i) null else i }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(11.dp).background(s.color, RoundedCornerShape(3.dp)))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        s.name,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(donutBytes(s.bytes), style = MaterialTheme.typography.bodySmall, color = dim)
                    Text(
                        "%$pct",
                        modifier = Modifier.width(44.dp),
                        textAlign = TextAlign.End,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Bugünkü indirme + yükleme toplamı. Dilime ya da satıra dokun.",
            style = MaterialTheme.typography.bodySmall,
            color = dim,
        )
    }
}
