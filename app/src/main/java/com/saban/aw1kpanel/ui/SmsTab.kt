package com.saban.aw1kpanel.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.saban.aw1kpanel.R
import com.saban.aw1kpanel.data.SmsMessage
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

private val SMS_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
private val NUMBER_RE = Regex("^\\+?[0-9]{3,15}$")

private fun smsTime(ts: Long): String =
    if (ts <= 0) "" else SMS_TIME.format(Instant.ofEpochSecond(ts).atZone(ZoneId.systemDefault()))

private fun copyText(ctx: Context, text: String) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("SMS", text))
    Toast.makeText(ctx, "Kopyalandı", Toast.LENGTH_SHORT).show()
}

/** SMS: SIM'deki mesajları okur, yeni SMS gönderir, siler (modemdeki qmodem eklentisi üzerinden). */
@Composable
internal fun SmsTab(state: UiState, vm: PanelViewModel) {
    val ctx = LocalContext.current
    var compose by remember { mutableStateOf<String?>(null) } // null = kapalı; metin = alıcı numarası (boş olabilir)
    var confirm by remember { mutableStateOf<Pair<String, List<Int>>?>(null) }

    // Sayfa açıkken 15 sn'de bir yenile.
    LaunchedEffect(Unit) {
        vm.loadSms()
        while (true) {
            delay(15000)
            vm.loadSms()
        }
    }

    val list = state.sms
    LazyColumn(
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            SectionCard {
                CardTitle(R.drawable.ic_nav_sms, "SMS")
                Spacer(Modifier.height(4.dp))
                Text(
                    when {
                        list == null -> "Yükleniyor…"
                        list.isEmpty() -> "SIM kartta mesaj yok."
                        else -> "SIM kartta ${list.size} mesaj var."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "SIM belleği dolarsa yeni SMS gelmez; eski mesajları silerek yer aç.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                state.smsError?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { compose = "" }, enabled = !state.smsBusy) { Text("Yeni SMS") }
                    OutlinedButton(onClick = { vm.refreshSms() }) { Text("Yenile") }
                    if (!list.isNullOrEmpty()) {
                        OutlinedButton(
                            onClick = { confirm = "Tüm mesajlar (${list.size}) silinsin mi?" to list.flatMap { it.indexes } },
                            enabled = !state.smsBusy,
                            colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = Bad),
                        ) { Text("Tümünü sil") }
                    }
                }
            }
        }
        items(list ?: emptyList(), key = { it.indexes.first() }) { m ->
            SmsCard(
                m,
                busy = state.smsBusy,
                onCopy = { copyText(ctx, m.text) },
                onReply = { compose = m.sender },
                onDelete = { confirm = "Bu mesaj silinsin mi?" to m.indexes },
            )
        }
    }

    compose?.let { initial ->
        ComposeDialog(
            initialNumber = initial,
            busy = state.smsBusy,
            onSend = { number, text -> vm.sendSms(number, text) { compose = null } },
            onClose = { compose = null },
        )
    }

    confirm?.let { (question, idx) ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(question) },
            text = { Text("Silinen mesaj geri getirilemez.") },
            confirmButton = {
                TextButton(onClick = { vm.deleteSms(idx); confirm = null }) { Text("Sil", color = Bad) }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Vazgeç") } },
        )
    }
}

@Composable
private fun SmsCard(m: SmsMessage, busy: Boolean, onCopy: () -> Unit, onReply: () -> Unit, onDelete: () -> Unit) {
    SectionCard {
        Row(Modifier.fillMaxWidth()) {
            Text(
                m.sender.ifEmpty { "Bilinmeyen" },
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                smsTime(m.timestamp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(m.text, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = onCopy) { Text("Kopyala") }
            if (NUMBER_RE.matches(m.sender)) TextButton(onClick = onReply, enabled = !busy) { Text("Yanıtla") }
            TextButton(onClick = onDelete, enabled = !busy) { Text("Sil", color = Bad) }
        }
    }
}

@Composable
private fun ComposeDialog(initialNumber: String, busy: Boolean, onSend: (String, String) -> Unit, onClose: () -> Unit) {
    var number by remember { mutableStateOf(initialNumber) }
    var text by remember { mutableStateOf("") }
    val cleaned = number.replace(" ", "")
    val valid = NUMBER_RE.matches(cleaned) && text.isNotBlank()
    AlertDialog(
        onDismissRequest = { if (!busy) onClose() },
        title = { Text("Yeni SMS") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = number,
                    onValueChange = { number = it },
                    label = { Text("Numara (+90…)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Mesaj") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "${text.length} karakter",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSend(cleaned, text) }, enabled = valid && !busy) {
                Text(if (busy) "Gönderiliyor…" else "Gönder")
            }
        },
        dismissButton = { TextButton(onClick = onClose, enabled = !busy) { Text("Vazgeç") } },
    )
}
