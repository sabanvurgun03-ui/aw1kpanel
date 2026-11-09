package com.saban.aw1kpanel.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.saban.aw1kpanel.R

private const val TOTAL_STEPS = 7

/** KediRay güncelle: GitHub'dan (modem indirir) ya da telefondan kediray-…-install.sh seçerek. */
@Composable
internal fun UpdateTab(state: UiState, vm: PanelViewModel) {
    val ctx = LocalContext.current
    val u = state.update
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val name = runCatching {
                ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
                    val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (c.moveToFirst() && i >= 0) c.getString(i) else null
                }
            }.getOrNull() ?: "kurulum.sh"
            vm.startKediUpdate(uri, name)
        }
    }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { AppUpdateCard(state.appUpdate, vm) }
        item {
            SectionCard {
                CardTitle(R.drawable.ic_nav_vpn, "GitHub'dan güncelle")
                Spacer(Modifier.height(6.dp))
                Text(
                    "En son sürümü modem GitHub'dan indirir, SHA256 ile doğrular ve kurar. Modemde KediRay 1.19.36 veya yenisi olmalı.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (u.checkMsg.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        u.checkMsg,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (u.newer) Good else MaterialTheme.colorScheme.onSurface,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = vm::checkKediUpdate, enabled = !u.busy && !u.checking) {
                        Text(if (u.checking) "Denetleniyor…" else "Güncellemeyi denetle")
                    }
                    if (u.newer) {
                        Button(onClick = vm::startKediGithubUpdate, enabled = !u.busy) { Text("Şimdi güncelle") }
                    }
                }
            }
        }
        item {
            SectionCard {
                CardTitle(R.drawable.ic_nav_vpn, "Dosyayla güncelle")
                Spacer(Modifier.height(6.dp))
                Text(
                    "kediray-…-install.sh dosyasını seç. Dosya modeme yüklenir, doğrulanır ve LuCI paneli otomatik güncellenir. " +
                        "Bu sırada panel birkaç saniye yanıt vermeyebilir, bu normaldir.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { picker.launch(arrayOf("*/*")) },
                    enabled = !u.busy,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                ) {
                    Text(if (u.busy) "Güncelleniyor…" else ".sh dosyası seç ve güncelle")
                }
            }
        }
        if (u.busy || u.finished) {
            item {
                SectionCard {
                    if (u.fileName.isNotEmpty()) Text(u.fileName, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    if (u.busy) {
                        val p = (u.steps.toFloat() / TOTAL_STEPS).coerceIn(0.03f, 0.95f)
                        LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)))
                        Spacer(Modifier.height(6.dp))
                    }
                    if (u.steps > 0) Text("Adım ${u.steps}/$TOTAL_STEPS", style = MaterialTheme.typography.labelMedium)
                    Text(
                        u.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (u.finished) (if (u.ok) Good else Bad) else MaterialTheme.colorScheme.onSurface,
                    )
                    if (u.log.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            u.log.lines().takeLast(25).joinToString("\n"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (u.finished) {
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(onClick = vm::clearUpdate) { Text("Tamam") }
                    }
                }
            }
        }
    }
}
