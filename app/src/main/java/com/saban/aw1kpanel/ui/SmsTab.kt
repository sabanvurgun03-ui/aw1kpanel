package com.saban.aw1kpanel.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.saban.aw1kpanel.R
import com.saban.aw1kpanel.data.SmsMatcher
import com.saban.aw1kpanel.data.SmsMessage
import com.saban.aw1kpanel.data.SmsRules
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

/** Onay penceresi içeriği. */
private class Ask(val title: String, val body: String, val ok: String, val action: () -> Unit)

/** SMS: SIM'deki mesajları okur, yeni SMS gönderir, siler; istenmeyenleri kelimeyle filtreler (modemdeki qmodem + aw1k-smsfilter). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SmsTab(state: UiState, vm: PanelViewModel) {
    val ctx = LocalContext.current
    var compose by remember { mutableStateOf<String?>(null) } // null = kapalı; metin = alıcı numarası (boş olabilir)
    var ask by remember { mutableStateOf<Ask?>(null) }

    // Sayfa açıkken 15 sn'de bir yenile.
    LaunchedEffect(Unit) {
        while (true) {
            vm.loadSms()
            vm.loadSmsFilter()
            delay(15000)
        }
    }

    val list = state.sms
    val rules = state.smsFilter?.rules ?: SmsRules()
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
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Button(onClick = { compose = "" }, enabled = !state.smsBusy) { Text("Yeni SMS") }
                    OutlinedButton(onClick = { vm.refreshSms() }) { Text("Yenile") }
                    if (!list.isNullOrEmpty()) {
                        OutlinedButton(
                            onClick = {
                                val idx = list.flatMap { it.indexes }
                                ask = Ask("Tüm mesajlar (${list.size}) silinsin mi?", "Silinen mesaj geri getirilemez.", "Sil") { vm.deleteSms(idx) }
                            },
                            enabled = !state.smsBusy,
                            colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = Bad),
                        ) { Text("Tümünü sil") }
                    }
                }
            }
        }
        item { SmsFilterCard(state, vm) { ask = it } }
        items(list ?: emptyList(), key = { it.indexes.first() }) { m ->
            SmsCard(
                m,
                busy = state.smsBusy,
                filterReason = SmsMatcher.reason(rules, m.sender, m.text),
                onCopy = { copyText(ctx, m.text) },
                onReply = { compose = m.sender },
                onBlock = {
                    ask = Ask(
                        "Gönderen engellensin mi?",
                        "\"${m.sender}\" engelli listeye eklenir, bu mesaj silinir ve bu göndericiden gelecek mesajlar modem tarafından silinir. Filtre açık olmalı.",
                        "Engelle ve sil",
                    ) { vm.blockSmsSender(m.sender, m.indexes) }
                },
                onDelete = {
                    ask = Ask("Bu mesaj silinsin mi?", "Silinen mesaj geri getirilemez.", "Sil") { vm.deleteSms(m.indexes) }
                },
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

    ask?.let { a ->
        AlertDialog(
            onDismissRequest = { ask = null },
            title = { Text(a.title) },
            text = { Text(a.body) },
            confirmButton = {
                TextButton(onClick = { a.action(); ask = null }) { Text(a.ok, color = Bad) }
            },
            dismissButton = { TextButton(onClick = { ask = null }) { Text("Vazgeç") } },
        )
    }
}

/** İstenmeyen SMS filtresi: kurallar modemde saklanır, modem 2 dakikada bir uygular. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SmsFilterCard(state: UiState, vm: PanelViewModel, onAsk: (Ask) -> Unit) {
    val f = state.smsFilter
    var open by remember { mutableStateOf(false) }
    SectionCard {
        Row(Modifier.fillMaxWidth().clickable { open = !open }, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Spam filtresi", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    when {
                        f == null -> "Okunamadı. Modemde KediRay'i 1.19.94 ya da yenisine güncelle."
                        f.rules.enabled -> "Açık · ${f.rules.keywords.size} kelime, ${f.rules.blocked.size} engelli gönderen"
                        else -> "Kapalı"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(if (open) "▴" else "▾", style = MaterialTheme.typography.titleMedium)
        }
        if (!open || f == null) return@SectionCard
        val r = f.rules
        Spacer(Modifier.height(8.dp))
        if (!f.ucode) {
            Text(
                "Bu modemde ucode yok; filtre çalışamaz.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        KSwitchRow(
            "Filtre açık",
            "Modem 2 dakikada bir SIM'i tarar; uyan mesajı siler. Uygulama kapalıyken de çalışır.",
            r.enabled,
            { vm.saveSmsRules(r.copy(enabled = it)) },
        )
        KDivider()
        KSwitchRow(
            "Göndericiyi de engelle",
            "Kelimeyle yakalanan mesajın göndericisi engelli listeye girer; sonraki mesajları içeriğine bakılmadan silinir.",
            r.autoBlock,
            { vm.saveSmsRules(r.copy(autoBlock = it)) },
        )
        Spacer(Modifier.height(10.dp))
        ChipEditor(
            "Silinecek kelimeler",
            "Mesajda geçerse silinir. Büyük/küçük harf ve Türkçe harf farkı yok (\"Doğrulama\" = \"dogrulama\"). En az 3 harf.",
            r.keywords,
            "Kelime ekle",
            onAdd = { w ->
                when {
                    SmsMatcher.fold(w).length < 3 -> vm.showMessage("Kelime en az 3 harf olmalı")
                    r.keywords.any { SmsMatcher.fold(it) == SmsMatcher.fold(w) } -> vm.showMessage("Bu kelime zaten var")
                    else -> vm.saveSmsRules(r.copy(keywords = r.keywords + w))
                }
            },
            onRemove = { w -> vm.saveSmsRules(r.copy(keywords = r.keywords - w)) },
        )
        Spacer(Modifier.height(10.dp))
        ChipEditor(
            "Engelli göndericiler",
            "Bunlardan gelen her mesaj silinir. Kaldırmak için dokun.",
            r.blocked,
            "Gönderen ekle",
            onAdd = { w -> if (r.blocked.none { SmsMatcher.fold(it) == SmsMatcher.fold(w) }) vm.saveSmsRules(r.copy(blocked = r.blocked + w)) },
            onRemove = { w -> vm.saveSmsRules(r.copy(blocked = r.blocked - w)) },
        )
        Spacer(Modifier.height(10.dp))
        ChipEditor(
            "Silinmeyecek göndericiler",
            "Bunlara filtre hiç dokunmaz (ör. E-DEVLET, bankan). Mesajdaki gönderen adı neyse onu yaz.",
            r.allow,
            "Gönderen ekle",
            onAdd = { w -> if (r.allow.none { SmsMatcher.fold(it) == SmsMatcher.fold(w) }) vm.saveSmsRules(r.copy(allow = r.allow + w)) },
            onRemove = { w -> vm.saveSmsRules(r.copy(allow = r.allow - w)) },
        )
        Spacer(Modifier.height(12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedButton(onClick = { vm.runSmsFilter(false) }, enabled = !state.smsBusy) { Text("Deneme (silmez)") }
            Button(
                onClick = {
                    onAsk(Ask("Filtre şimdi uygulansın mı?", "Kurallara uyan mesajlar SIM'den silinir, geri getirilemez. Önce \"Deneme\" ile neyin silineceğine bak.", "Uygula") {
                        vm.runSmsFilter(true)
                    })
                },
                enabled = !state.smsBusy && r.enabled,
            ) { Text("Şimdi uygula") }
        }
        state.smsRun?.let { run ->
            Spacer(Modifier.height(10.dp))
            Text(
                if (run.matched.isEmpty()) "Deneme: silinecek mesaj yok (${run.scanned} mesaj tarandı)."
                else "Deneme: şu ${run.matched.size} mesaj silinecekti (hiçbir şey silinmedi):",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
            run.matched.forEach { h ->
                Spacer(Modifier.height(6.dp))
                Text("${h.sender} · ${h.reason}", style = MaterialTheme.typography.bodyMedium)
                Text(
                    h.text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TextButton(onClick = { vm.dismissSmsRun() }) { Text("Kapat") }
        }
        if (f.log.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text("Son silinenler", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            f.log.take(10).forEach { e ->
                Spacer(Modifier.height(4.dp))
                Text("${smsTime(e.time)} · ${e.sender} · ${e.reason}", style = MaterialTheme.typography.bodySmall)
                Text(
                    e.text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                "Kayıt modemin belleğinde tutulur, yeniden başlatınca silinir.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ChipEditor(
    title: String,
    hint: String,
    items: List<String>,
    placeholder: String,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
    Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (items.isNotEmpty()) {
        Spacer(Modifier.height(4.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items.forEach { w ->
                FilterChip(selected = true, onClick = { onRemove(w) }, label = { Text("$w  ✕") })
            }
        }
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text(placeholder) },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        Button(onClick = {
            val t = text.trim()
            if (t.isNotEmpty()) {
                onAdd(t)
                text = ""
            }
        }) { Text("Ekle") }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SmsCard(
    m: SmsMessage,
    busy: Boolean,
    filterReason: String?,
    onCopy: () -> Unit,
    onReply: () -> Unit,
    onBlock: () -> Unit,
    onDelete: () -> Unit,
) {
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
        if (filterReason != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                "Filtreye uyuyor ($filterReason): modem yakında silecek.",
                style = MaterialTheme.typography.bodySmall,
                color = Bad,
            )
        }
        Spacer(Modifier.height(4.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = onCopy) { Text("Kopyala") }
            if (NUMBER_RE.matches(m.sender)) TextButton(onClick = onReply, enabled = !busy) { Text("Yanıtla") }
            TextButton(onClick = onBlock, enabled = !busy) { Text("Engelle", color = Bad) }
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
