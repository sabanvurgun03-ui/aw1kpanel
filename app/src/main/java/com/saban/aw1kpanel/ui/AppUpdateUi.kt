package com.saban.aw1kpanel.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.saban.aw1kpanel.R

private fun mb(b: Long): String = "%.1f MB".format(b / 1048576.0)

/** Güncelleme sayfasının üstündeki "Uygulama güncellemesi" kartı. */
@Composable
internal fun AppUpdateCard(u: AppUpdateUi, vm: PanelViewModel) {
    SectionCard {
        CardTitle(R.drawable.ic_nav_update, "Uygulama güncellemesi")
        Spacer(Modifier.height(6.dp))
        Text(
            "Kurulu sürüm: v$APP_VERSION" + (u.release?.let { " · GitHub: v${it.version.removePrefix("v")}" } ?: ""),
            style = MaterialTheme.typography.bodyMedium,
        )
        if (u.message.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                u.message,
                style = MaterialTheme.typography.bodyMedium,
                color = if (u.newer) Good else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val notes = u.release?.notes?.trim().orEmpty()
        if (u.newer && notes.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                notes.take(600),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (u.downloading) {
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { u.progress },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
            )
            Text(
                "%" + (u.progress * 100).toInt(),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(
                onClick = { vm.checkAppUpdate(true) },
                enabled = !u.checking && !u.downloading,
            ) { Text(if (u.checking) "Denetleniyor…" else "Güncellemeyi denetle") }
            if (u.newer && !u.downloading) {
                Button(onClick = vm::downloadAppUpdate) {
                    Text("İndir ve kur" + (u.release?.size?.takeIf { it > 0 }?.let { " (${mb(it)})" } ?: ""))
                }
            }
        }
        if (u.ready != null && !u.downloading) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = vm::installAppUpdate) { Text("Kurulumu aç") }
        }
        if (u.release?.sha256 == null && u.newer) {
            Text(
                "Bu yayında SHA-256 bilgisi yok, dosya doğrulanamaz.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

/** Açılışta yeni sürüm bulunursa bir kez sorar. */
@Composable
internal fun AppUpdateDialog(u: AppUpdateUi, vm: PanelViewModel) {
    var dismissed by rememberSaveable { mutableStateOf(false) }
    if (!u.newer || dismissed || u.ready != null) return
    val rel = u.release ?: return
    AlertDialog(
        onDismissRequest = { if (!u.downloading) dismissed = true },
        title = { Text("Yeni sürüm: v${rel.version.removePrefix("v")}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Kurulu sürüm: v$APP_VERSION", style = MaterialTheme.typography.bodyMedium)
                val notes = rel.notes.trim()
                if (notes.isNotEmpty()) {
                    Text(notes.take(500), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (u.downloading) {
                    LinearProgressIndicator(
                        progress = { u.progress },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                    )
                }
                if (u.message.startsWith("İndirilemedi")) {
                    Text(u.message, color = Bad, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = vm::downloadAppUpdate, enabled = !u.downloading) {
                Text(if (u.downloading) "İndiriliyor…" else "İndir ve kur")
            }
        },
        dismissButton = {
            TextButton(onClick = { dismissed = true }, enabled = !u.downloading) { Text("Sonra") }
        },
    )
}


/** Kurulumdan sonra bir kez: "Güncelleme yüklendi" + sürüm + varsa değişiklikler. */
@Composable
internal fun WhatsNewDialog(w: com.saban.aw1kpanel.data.WhatsNew?, vm: PanelViewModel) {
    if (w == null) return
    AlertDialog(
        onDismissRequest = vm::ackWhatsNew,
        title = { Text("Güncelleme yüklendi") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (w.from == w.to) "KediRay ${w.to}" else "KediRay ${w.from} → ${w.to}",
                    style = MaterialTheme.typography.titleSmall,
                )
                if (w.changes.isNotEmpty()) {
                    Text("Değişiklikler:", style = MaterialTheme.typography.bodyMedium)
                    w.changes.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                } else {
                    Text("Değişiklik notu alınamadı.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = { TextButton(onClick = vm::ackWhatsNew) { Text("Tamam") } },
    )
}
