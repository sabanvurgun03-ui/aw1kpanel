package com.saban.aw1kpanel.ui

import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saban.aw1kpanel.data.TrafficDay
import com.saban.aw1kpanel.data.UsageData
import com.saban.aw1kpanel.data.UsageDay
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

private fun formatBytes(b: Long): String = when {
    b >= (1L shl 30) -> "%.2f GB".format(b / 1073741824.0)
    b >= (1L shl 20) -> "%.1f MB".format(b / 1048576.0)
    b >= 1024L -> "%.0f KB".format(b / 1024.0)
    else -> "$b B"
}

@Composable
internal fun UsageTab(usage: UsageData?, trafficDays: List<TrafficDay> = emptyList()) {
    if (usage == null) {
        LazyColumn(contentPadding = PaddingValues(vertical = 12.dp)) {
            item {
                SectionCard {
                    Text("Kayıt servisi kurulu değil", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Günlük, haftalık ve aylık veri için modemde küçük bir kayıt betiği çalışmalı. " +
                            "Kurulum için projedeki router/aw1k-usage-setup.sh dosyasını modemde çalıştır, " +
                            "birkaç dakika sonra bu sekme dolmaya başlar.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        return
    }

    val today = runCatching { LocalDate.parse(usage.today) }.getOrDefault(LocalDate.now())
    val byDate: Map<LocalDate, UsageDay> = usage.days.mapNotNull { d ->
        runCatching { LocalDate.parse(d.date) }.getOrNull()?.let { it to d }
    }.toMap()

    fun sum(pred: (LocalDate) -> Boolean): Pair<Long, Long> {
        var rx = 0L
        var tx = 0L
        byDate.forEach { (date, d) ->
            if (pred(date)) {
                rx += d.rx
                tx += d.tx
            }
        }
        return rx to tx
    }

    val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val day = sum { it == today }
    val week = sum { !it.isBefore(weekStart) && !it.isAfter(today) }
    val month = sum { it.year == today.year && it.month == today.month }

    val bars = (13 downTo 0).map { i ->
        val date = today.minusDays(i.toLong())
        val d = byDate[date]
        date to ((d?.rx ?: 0L) + (d?.tx ?: 0L))
    }
    var selected by remember { mutableStateOf<LocalDate?>(null) }

    val months = byDate.entries
        .groupBy { YearMonth.from(it.key) }
        .toSortedMap(reverseOrder())
        .entries
        .take(12)

    LazyColumn(
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            SectionCard(brush = HeaderBrush) {
                Text("Mobil veri · bugün", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.75f))
                Text(
                    formatBytes(day.first + day.second),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Text(
                    "↓ ${formatBytes(day.first)} · ↑ ${formatBytes(day.second)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f),
                )
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HeroStat("Bu hafta", week, Modifier.weight(1f))
                    HeroStat("Bu ay", month, Modifier.weight(1f))
                }
            }
        }
        item {
            SectionCard {
                Text("Son 14 gün", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Bars(bars, selected) { d -> selected = if (selected == d) null else d }
                val sel = selected
                if (sel == null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Ayrıntı için bir güne dokun.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    DayDetail(sel, trafficDays.firstOrNull { it.day == sel.toString() }, byDate[sel])
                }
            }
        }
        item {
            SectionCard {
                Text("Aylar", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                if (months.isEmpty()) {
                    Text("Henüz kayıt yok", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val monthTotals = months.map { (ym, entries) ->
                    Triple(ym, entries.sumOf { it.value.rx }, entries.sumOf { it.value.tx })
                }
                val monthMax = (monthTotals.maxOfOrNull { it.second + it.third } ?: 0L).coerceAtLeast(1L)
                monthTotals.take(3).forEach { (ym, rx, tx) ->
                    SummaryRow("%02d/%d".format(ym.monthValue, ym.year), rx to tx)
                    LinearProgressIndicator(
                        progress = { ((rx + tx).toFloat() / monthMax).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    )
                    Spacer(Modifier.height(6.dp))
                }
                // 3 aydan eskiler: katlanır, tek satırlık kısa liste.
                val older = monthTotals.drop(3)
                if (older.isNotEmpty()) {
                    var open by remember { mutableStateOf(false) }
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { open = !open }.padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Önceki aylar (${older.size})", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                        Text(if (open) "▴" else "▾", color = MaterialTheme.colorScheme.primary)
                    }
                    if (open) {
                        older.forEach { (ym, rx, tx) ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text("%02d/%d".format(ym.monthValue, ym.year), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(formatBytes(rx + tx), fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
        item {
            Text(
                "Kayıt wwan0_1 arayüzünden alınır; VPN trafiği dahil, modemin gerçek mobil kullanımıdır. Betik kurulduğu andan itibaren sayar.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HeroStat(label: String, value: Pair<Long, Long>, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.16f))
            .padding(12.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.75f))
        Text(
            formatBytes(value.first + value.second),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
    }
}

@Composable
private fun SummaryRow(label: String, value: Pair<Long, Long>) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label)
        Column(horizontalAlignment = Alignment.End) {
            Text(
                formatBytes(value.first + value.second),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "↓ ${formatBytes(value.first)} · ↑ ${formatBytes(value.second)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Seçilen günün dökümü: VPN / Direkt / Toplam, indirme ve yükleme (KediRay sayacından). */
@Composable
private fun DayDetail(date: LocalDate, k: TrafficDay?, fallback: UsageDay?) {
    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Text(
            "%02d.%02d.%d".format(date.dayOfMonth, date.monthValue, date.year),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        if (k != null) {
            SummaryRow("VPN", k.vpnRx to k.vpnTx)
            SummaryRow("Direkt", k.dirRx to k.dirTx)
            SummaryRow("Toplam", (k.vpnRx + k.dirRx) to (k.vpnTx + k.dirTx))
        } else {
            if (fallback != null) SummaryRow("Toplam", fallback.rx to fallback.tx)
            Text(
                "Bu gün için VPN / Direkt dökümü yok (KediRay sayacı son 30 günü tutar).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Bars(values: List<Pair<LocalDate, Long>>, selected: LocalDate?, onSelect: (LocalDate) -> Unit) {
    val max = (values.maxOfOrNull { it.second } ?: 0L).coerceAtLeast(1L)
    Row(
        Modifier
            .fillMaxWidth()
            .height(120.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        values.forEach { (date, v) ->
            Column(
                Modifier.weight(1f).clickable { onSelect(date) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height((90f * v / max).dp.coerceAtLeast(2.dp))
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = if (selected != null && selected != date) 0.4f else 1f),
                            RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 2.dp, bottomEnd = 2.dp),
                        ),
                )
                Text(
                    date.dayOfMonth.toString(),
                    fontSize = 9.sp,
                    fontWeight = if (date == selected) FontWeight.Bold else FontWeight.Normal,
                    color = if (date == selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
