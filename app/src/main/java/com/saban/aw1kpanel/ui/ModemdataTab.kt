package com.saban.aw1kpanel.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.material3.Switch
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.saban.aw1kpanel.data.ModemdataItem
import kotlinx.coroutines.delay

private val SENSITIVE = Regex("imsi|iccid|imei|msisdn|phone|telefon|numara", RegexOption.IGNORE_CASE)
private val LightGreen = Color(0xFF7CB342)
private val Amber: Color get() = Warn

private fun mask(v: String): String =
    if (v.length <= 4) "••••" else "••••" + v.takeLast(4)

private fun quality(item: ModemdataItem): Pair<String, Color>? {
    val name = (item.label + " " + item.key).uppercase()
    val v = item.value.toFloatOrNull() ?: return null
    fun grade(a: Float, b: Float, c: Float): Pair<String, Color> = when {
        v >= a -> "Mükemmel" to Good
        v >= b -> "İyi" to LightGreen
        v >= c -> "Orta" to Amber
        else -> "Zayıf" to Bad
    }
    return when {
        "RSRP" in name -> grade(-80f, -90f, -100f)
        "RSRQ" in name -> grade(-9f, -12f, -15f)
        "SINR" in name || "SNR" in name -> grade(13f, 10f, 0f)
        "RSSI" in name -> grade(-65f, -75f, -85f)
        else -> null
    }
}

@Composable
internal fun ModemdataTab(state: UiState, vm: PanelViewModel) {
    var hide by rememberSaveable { mutableStateOf(true) }
    val owner = LocalLifecycleOwner.current

    LaunchedEffect(owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                vm.refreshModemdata()
                delay(8000)
            }
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { hide = !hide }
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Verileri gizle (IMEI, IMSI, ICCID, numara)",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = hide, onCheckedChange = { hide = it })
            }
        }
        state.modemdataError?.let {
            item { Text(it, color = Bad, style = MaterialTheme.typography.bodySmall) }
        }
        if (state.modemdata.isEmpty() && state.modemdataError == null) {
            item { Text("Yükleniyor…", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        items(state.modemdata, key = { it.title }) { g ->
            SectionCard {
                Text(g.title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                g.items.forEach { item ->
                    val sensitive = SENSITIVE.containsMatchIn(item.label + " " + item.key)
                    val shown = if (hide && sensitive) mask(item.value) else item.value
                    val q = quality(item)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            item.label + if (item.extra.isNotEmpty()) " · ${item.extra}" else "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "$shown ${item.unit}".trim(),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                            )
                            if (q != null) {
                                Box(
                                    Modifier
                                        .padding(start = 8.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(q.second.copy(alpha = 0.16f))
                                        .padding(horizontal = 8.dp, vertical = 2.dp),
                                ) {
                                    Text(
                                        q.first,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = q.second,
                                        fontWeight = FontWeight.Medium,
                                    )
                                }
                            }
                        }
                    }
                    item.fraction?.let {
                        LinearProgressIndicator(
                            progress = { it.coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp, bottom = 6.dp)
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = q?.second ?: MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}
