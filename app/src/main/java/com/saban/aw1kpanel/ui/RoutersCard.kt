package com.saban.aw1kpanel.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.saban.aw1kpanel.data.Router

@Composable
internal fun RoutersCard(state: UiState, vm: PanelViewModel) {
    var editing by remember { mutableStateOf<Router?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Router?>(null) }

    SectionCard {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Modemler", style = MaterialTheme.typography.titleMedium)
            Button(onClick = { adding = true }) { Text("Modem ekle") }
        }
        Spacer(Modifier.height(8.dp))
        state.routers.forEach { r ->
            val active = r.id == state.activeRouterId
            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(r.name, style = MaterialTheme.typography.titleSmall)
                        Text(
                            "${r.host} · ${r.user}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (active) Text("Aktif", color = Good, fontWeight = FontWeight.Medium)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!active) {
                        OutlinedButton(onClick = { vm.connectRouter(r.id) }) { Text("Bağlan") }
                    }
                    OutlinedButton(onClick = { editing = r }) { Text("Düzenle") }
                    OutlinedButton(
                        onClick = { deleting = r },
                        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = Bad),
                    ) { Text("Sil") }
                }
            }
        }
    }

    if (adding) {
        RouterDialog(
            initial = null,
            onDismiss = { adding = false },
            onSave = { n, h, u, p ->
                vm.saveRouter(null, n, h, u, p)
                adding = false
            },
        )
    }
    editing?.let { r ->
        RouterDialog(
            initial = r,
            onDismiss = { editing = null },
            onSave = { n, h, u, p ->
                vm.saveRouter(r.id, n, h, u, p)
                editing = null
            },
        )
    }
    deleting?.let { r ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Modem silinsin mi?") },
            text = { Text("\"${r.name}\" listeden ve kayıtlı şifresiyle birlikte silinir.") },
            confirmButton = {
                TextButton(onClick = { vm.deleteRouter(r.id); deleting = null }) { Text("Sil") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Vazgeç") } },
        )
    }
}

@Composable
private fun RouterDialog(
    initial: Router?,
    onDismiss: () -> Unit,
    onSave: (name: String, host: String, user: String, pass: String) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var host by remember { mutableStateOf(initial?.host ?: "192.168.1.1") }
    var user by remember { mutableStateOf(initial?.user ?: "root") }
    var pass by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Modem ekle" else "Modemi düzenle") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Ad") }, singleLine = true)
                OutlinedTextField(host, { host = it }, label = { Text("Adres") }, singleLine = true)
                OutlinedTextField(user, { user = it }, label = { Text("Kullanıcı") }, singleLine = true)
                OutlinedTextField(
                    pass,
                    { pass = it },
                    label = { Text(if (initial == null) "Şifre" else "Şifre (boşsa değişmez)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = host.isNotBlank() && (initial != null || pass.isNotEmpty()),
                onClick = { onSave(name, host, user, pass) },
            ) { Text("Kaydet") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } },
    )
}
