package com.saban.aw1kpanel.ui

import androidx.compose.material3.Surface
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saban.aw1kpanel.R
import com.saban.aw1kpanel.data.LogEntry
import kotlinx.coroutines.delay

private val LEVEL_FILTERS = listOf("Hepsi", "Uyarı+", "Hata")
private val COMP_FILTERS = listOf("Tümü", "Xray", "KediRay", "DNS", "Sunucu bağlantısı", "UDP tüneli")

private fun levelColor(level: String, scheme: androidx.compose.material3.ColorScheme): Color = when (level) {
    "error" -> scheme.error
    "warn" -> Warn
    "debug" -> scheme.onSurfaceVariant.copy(alpha = 0.6f)
    else -> scheme.onSurfaceVariant
}

/** KediRay / Xray günlüğü: LuCI'deki "Günlük" sayfasının telefon karşılığı. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LogTab(state: UiState, vm: PanelViewModel) {
    val ctx = LocalContext.current
    var auto by remember { mutableStateOf(true) }
    var level by remember { mutableStateOf(0) }
    var comp by remember { mutableStateOf(0) }
    var showErrors by remember { mutableStateOf(true) }
    var expanded by remember { mutableStateOf<Long?>(null) }

    // Sayfa açıkken (ve "Otomatik" açıkken) 3 sn'de bir yenile.
    LaunchedEffect(auto) {
        vm.loadLogs()
        while (auto) {
            delay(3000)
            vm.loadLogs()
        }
    }

    val data = state.logs
    val shown = (data?.entries ?: emptyList()).filter { e ->
        val okLevel = when (level) {
            1 -> e.level == "error" || e.level == "warn"
            2 -> e.level == "error"
            else -> true
        }
        val okComp = comp == 0 || e.comp == COMP_FILTERS[comp]
        okLevel && okComp
    }.asReversed() // en yeni üstte

    val listState = rememberLazyListState()
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            SectionCard {
                CardTitle(R.drawable.ic_nav_log, "Günlük")
                Spacer(Modifier.height(4.dp))
                Text(
                    "KediRay, Xray çekirdeği ve UDP tüneli (hev) satırları. Yalnızca modemin belleğinde tutulur. " +
                        "Bağlantı ayrıntıları için LuCI → Ayarlar → Gelişmiş → Günlük seviyesi: info.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Otomatik yenile", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = auto, onCheckedChange = { auto = it })
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { copyLogs(ctx, shown) }) { Text("Kopyala") }
                    OutlinedButton(onClick = { vm.clearLogs(true) }) { Text("Temizle") }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    LEVEL_FILTERS.forEachIndexed { i, t ->
                        FilterChip(selected = level == i, onClick = { level = i }, label = { Text(t) })
                    }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    COMP_FILTERS.forEachIndexed { i, t ->
                        FilterChip(selected = comp == i, onClick = { comp = i }, label = { Text(t) })
                    }
                }
                state.logsError?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        val errors = data?.errors ?: emptyList()
        if (errors.isNotEmpty()) {
            item {
                SectionCard {
                    Row(
                        Modifier.fillMaxWidth().clickable { showErrors = !showErrors },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Hatalar (${errors.size})", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        Text(if (showErrors) "Gizle" else "Göster", style = MaterialTheme.typography.labelMedium)
                    }
                    if (showErrors) {
                        errors.forEach { er ->
                            Spacer(Modifier.height(10.dp))
                            Text(
                                (er.title ?: er.text) + if (er.count > 1) "  ×${er.count}" else "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                            Text(
                                "${er.comp} · son: ${er.last}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (er.title != null) {
                                Text(
                                    er.text,
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            er.fix?.let {
                                Text("Ne yapmalı: $it", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }

        if (data == null && state.logsError == null) {
            item { Text("Yükleniyor…", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else if (shown.isEmpty() && data != null) {
            item { Text("Bu filtreye uyan satır yok.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }

        items(shown.size) { i ->
            val e = shown[i]
            val rowId = e.id * 1000 + i
            LogRow(e, expanded == rowId) { expanded = if (expanded == rowId) null else rowId }
        }
    }
}

@Composable
private fun LogRow(e: LogEntry, open: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val color = levelColor(e.level, scheme)
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = scheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(4.dp).fillMaxHeight().background(color))
            Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(e.time, style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace), color = scheme.onSurfaceVariant)
                    Text(e.comp, style = MaterialTheme.typography.labelSmall, color = color)
                }
                Text(
                    e.title ?: e.text,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                    color = color,
                    maxLines = if (open) Int.MAX_VALUE else 3,
                )
                if (open) {
                    if (e.title != null && e.text.isNotBlank()) {
                        Text(e.text, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp), color = scheme.onSurfaceVariant)
                    }
                    e.fix?.let { Text("Ne yapmalı: $it", style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
}

private fun copyLogs(ctx: Context, list: List<LogEntry>) {
    val text = list.asReversed().joinToString("\n") { "${it.time} [${it.level}] ${it.comp}: ${it.raw.ifBlank { it.text }}" }
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("KediRay günlüğü", text))
    Toast.makeText(ctx, "${list.size} satır kopyalandı", Toast.LENGTH_SHORT).show()
}
