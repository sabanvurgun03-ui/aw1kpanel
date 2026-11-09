package com.saban.aw1kpanel.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.saban.aw1kpanel.data.KChoice
import com.saban.aw1kpanel.data.KOpt
import com.saban.aw1kpanel.data.KOptType
import com.saban.aw1kpanel.data.KediNode
import com.saban.aw1kpanel.data.KediSettingsData
import com.saban.aw1kpanel.data.KediSettingsSpec
import kotlinx.coroutines.delay

/** Ayarın modemdeki (ya da varsayılan) değeri: tek değer String, liste List<String>. */
private fun storedValue(o: KOpt, data: KediSettingsData): Any {
    val v = data.values[o.key]
    return when (o.type) {
        KOptType.DYNLIST -> when (v) {
            is List<*> -> v.map { it.toString() }
            is String -> v.split(' ').filter { it.isNotBlank() }
            else -> if (o.default.isNotEmpty()) listOf(o.default) else emptyList()
        }
        else -> (v as? String) ?: o.default
    }
}

@Composable
internal fun KediSettingsScreen(
    data: KediSettingsData?,
    error: String?,
    saving: Boolean,
    nodes: List<KediNode>,
    vm: PanelViewModel,
) {
    var tab by remember { mutableStateOf(0) }
    var edits by remember(data) { mutableStateOf(mapOf<String, Any>()) }
    var confirmClose by remember { mutableStateOf(false) }

    val current: (KOpt) -> Any = { o -> edits[o.key] ?: (data?.let { storedValue(o, it) } ?: o.default) }
    val changes: Map<String, Any> = if (data == null) emptyMap() else edits.filter { (k, v) ->
        val o = KediSettingsSpec.all.first { it.key == k }
        v != storedValue(o, data)
    }
    val errors: Map<String, String> = changes.mapNotNull { (k, v) ->
        val o = KediSettingsSpec.all.first { it.key == k }
        (v as? String)?.let { KediSettingsSpec.validate(o, it) }?.let { k to it }
    }.toMap()
    val visible: (KOpt) -> Boolean = { o ->
        o.dependsOn == null || KediSettingsSpec.all.firstOrNull { it.key == o.dependsOn }?.let { current(it) == o.dependsValue } ?: true
    }
    val tryClose = { if (changes.isNotEmpty() && !saving) confirmClose = true else vm.closeKediSettings() }

    Dialog(
        onDismissRequest = tryClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .imePadding(),
            ) {
                // Üst bar
                Row(
                    Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(48.dp).clip(CircleShape).clickable(onClick = tryClose),
                        contentAlignment = Alignment.Center,
                    ) { Text("←", style = MaterialTheme.typography.titleLarge) }
                    Column(Modifier.weight(1f)) {
                        Text("KediRay ayarları", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Modemdeki Ayarlar sayfasıyla aynı",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // Sekmeler: kaydırılabilir hap düğmeler
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    KediSettingsSpec.tabs.forEachIndexed { i, t ->
                        val n = t.opts.count { it.key in changes }
                        TabPill(t.title, selected = i == tab, badge = n) { tab = i }
                    }
                }

                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when {
                        error != null -> CenterNote(error, Bad) { vm.openKediSettings() }
                        data == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                        else -> {
                            val t = KediSettingsSpec.tabs[tab]
                            LazyColumn(
                                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 120.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                data.ownEngine?.let { eng ->
                                    item {
                                        NoteCard(
                                            "Çekirdek ayarı modu açık ($eng)",
                                            "VPN kendi yapılandırma dosyasıyla çalışıyor; bu sayfadaki ayarların çoğu şu an kullanılmıyor.",
                                            Warn,
                                        )
                                    }
                                }
                                if (t.id == "band") {
                                    item { BandCard(vm) }
                                } else {
                                    item {
                                        SettingsGroup {
                                            val shown = t.opts.filter(visible)
                                            shown.forEachIndexed { i, o ->
                                                SettingRow(
                                                    o = o,
                                                    value = current(o),
                                                    changed = o.key in changes,
                                                    error = errors[o.key],
                                                    nodes = nodes,
                                                    onChange = { nv -> edits = edits + (o.key to nv) },
                                                )
                                                if (i < shown.lastIndex) {
                                                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
                                                }
                                            }
                                        }
                                    }
                                    if (t.id == "dns") item { DnsTestCard(vm) }
                                }
                            }
                        }
                    }

                    // Alt kaydet çubuğu
                    SaveArea(
                        visible = changes.isNotEmpty(),
                        count = changes.size,
                        hasErrors = errors.isNotEmpty(),
                        saving = saving,
                        onReset = { edits = emptyMap() },
                        onSave = { vm.saveKediSettings(changes) },
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }
        }
    }

    if (confirmClose) {
        AlertDialog(
            onDismissRequest = { confirmClose = false },
            title = { Text("Değişiklikler kaydedilmedi") },
            text = { Text("${changes.size} ayar değişti. Kaydetmeden çıkarsan değişiklikler kaybolur.") },
            confirmButton = {
                TextButton(onClick = { confirmClose = false; vm.saveKediSettings(changes) }, enabled = errors.isEmpty()) {
                    Text("Kaydet ve uygula")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClose = false; vm.closeKediSettings() }) { Text("Kaydetmeden çık", color = Bad) }
            },
        )
    }
}

@Composable
private fun TabPill(title: String, selected: Boolean, badge: Int, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val bg by animateColorAsState(if (selected) scheme.primary else scheme.surfaceVariant, tween(220), label = "pillBg")
    val fg by animateColorAsState(if (selected) scheme.onPrimary else scheme.onSurfaceVariant, tween(220), label = "pillFg")
    Surface(onClick = onClick, shape = RoundedCornerShape(50), color = bg) {
        Row(
            Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, color = fg, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium)
            if (badge > 0) {
                Spacer(Modifier.width(6.dp))
                Box(
                    Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(if (selected) scheme.onPrimary else scheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "$badge",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (selected) scheme.primary else scheme.onPrimary,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsGroup(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column { content() }
    }
}

@Composable
private fun NoteCard(title: String, text: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = color.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, color = color)
            Text(text, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun CenterNote(text: String, color: Color, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text, color = color)
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onRetry) { Text("Tekrar dene") }
    }
}

/** Uzun açıklamalar iki satıra kısalır; dokununca açılır. */
@Composable
private fun Description(text: String) {
    if (text.isEmpty()) return
    var open by remember { mutableStateOf(false) }
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = if (open) Int.MAX_VALUE else 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.clickable { open = !open },
    )
}

@Composable
private fun SettingRow(
    o: KOpt,
    value: Any,
    changed: Boolean,
    error: String?,
    nodes: List<KediNode>,
    onChange: (Any) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val bar by animateColorAsState(if (changed) scheme.primary else Color.Transparent, tween(220), label = "changedBar")
    Row(Modifier.drawBehind { drawRect(bar, size = Size(4.dp.toPx(), size.height)) }) {
        Column(
            Modifier.weight(1f).padding(start = 18.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            when (o.type) {
                KOptType.FLAG -> {
                    val on = value == "1"
                    Row(
                        Modifier.fillMaxWidth().clickable { onChange(if (on) "0" else "1") },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(o.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        Switch(checked = on, onCheckedChange = { onChange(if (it) "1" else "0") })
                    }
                    Description(o.desc)
                }
                KOptType.LIST -> ListRow(o, value as String, nodes, onChange)
                KOptType.VALUE -> {
                    Text(o.title, style = MaterialTheme.typography.titleSmall)
                    Description(o.desc)
                    val numeric = o.min != null || o.check == "port"
                    OutlinedTextField(
                        value = value as String,
                        onValueChange = { onChange(it) },
                        singleLine = true,
                        isError = error != null,
                        placeholder = if (o.placeholder.isNotEmpty()) {
                            { Text(o.placeholder) }
                        } else null,
                        supportingText = if (error != null) {
                            { Text(error) }
                        } else null,
                        keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Uri),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (o.choices.isNotEmpty()) {
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            o.choices.forEach { ch -> SuggestChip(ch.label, value == ch.value) { onChange(ch.value) } }
                        }
                    }
                }
                KOptType.DYNLIST -> {
                    @Suppress("UNCHECKED_CAST")
                    DynListRow(o, value as List<String>, onChange)
                }
            }
            o.warn?.let { w ->
                Text(
                    "⚠ $w",
                    style = MaterialTheme.typography.bodySmall,
                    color = Warn,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun SuggestChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (selected) scheme.primaryContainer else Color.Transparent,
        border = BorderStroke(1.dp, if (selected) scheme.primary else scheme.outline.copy(alpha = 0.5f)),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ListRow(o: KOpt, value: String, nodes: List<KediNode>, onChange: (Any) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val choices: List<KChoice> = if (o.nodeList) {
        o.choices + nodes.map { KChoice(it.section, it.name) }
    } else o.choices
    val label = choices.firstOrNull { it.value == value }?.label ?: value.ifEmpty { "—" }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { open = true },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(o.title, style = MaterialTheme.typography.titleSmall)
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
            )
        }
        Text("›", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Description(o.desc)

    if (open) {
        ModalBottomSheet(
            onDismissRequest = { open = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(
                Modifier
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 28.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    o.title,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = 12.dp, bottom = 8.dp),
                )
                choices.forEach { ch ->
                    val sel = ch.value == value
                    Surface(
                        onClick = { onChange(ch.value); open = false },
                        shape = RoundedCornerShape(16.dp),
                        color = if (sel) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f) else Color.Transparent,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(Modifier.padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = sel, onClick = { onChange(ch.value); open = false })
                            Text(ch.label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DynListRow(o: KOpt, items: List<String>, onChange: (Any) -> Unit) {
    var add by remember { mutableStateOf("") }
    Text(o.title, style = MaterialTheme.typography.titleSmall)
    Description(o.desc)
    if (items.isEmpty()) {
        Text("Henüz yok", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    items.forEachIndexed { i, it ->
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { onChange(items.filterIndexed { j, _ -> j != i }) }) { Text("✕", color = Bad) }
            }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = add,
            onValueChange = { add = it },
            singleLine = true,
            placeholder = { Text(o.placeholder.ifEmpty { "Yeni" }) },
            shape = RoundedCornerShape(14.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.weight(1f),
        )
        Button(
            onClick = {
                val v = add.trim()
                if (v.isNotEmpty() && v !in items) onChange(items + v)
                add = ""
            },
            enabled = add.isNotBlank(),
        ) { Text("Ekle") }
    }
}

/** Ayrı fonksiyon: Column içindeki Box'ta AnimatedVisibility'nin ColumnScope sürümüyle karışmasın. */
@Composable
private fun SaveArea(
    visible: Boolean,
    count: Int,
    hasErrors: Boolean,
    saving: Boolean,
    onReset: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = modifier,
    ) {
        SaveBar(count, hasErrors, saving, onReset, onSave)
    }
}

@Composable
private fun SaveBar(count: Int, hasErrors: Boolean, saving: Boolean, onReset: () -> Unit, onSave: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shadowElevation = 12.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("$count değişiklik", fontWeight = FontWeight.SemiBold)
                Text(
                    if (hasErrors) "Hatalı alanları düzelt" else "Uygulanınca VPN kısa süre yeniden başlar",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (hasErrors) Bad else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onReset, enabled = !saving) { Text("Geri al") }
            Button(onClick = onSave, enabled = !saving && !hasErrors) {
                if (saving) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text("Kaydet ve uygula")
                }
            }
        }
    }
}

@Composable
private fun BandCard(vm: PanelViewModel) {
    var confirm by remember { mutableStateOf(false) }
    SettingsGroup {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Akıllı band seçimi", style = MaterialTheme.typography.titleSmall)
            Text(
                "Bantları tek tek deneyip en hızlısını seçen tarama uygulamanın Bant sekmesinde (Akıllı band seçimi kartı).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Bant kilidini kaldır", style = MaterialTheme.typography.titleSmall)
            Text(
                "Tüm bantlar açılır; modem en uygun bandı kendisi seçer.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = { confirm = true }) { Text("Tüm bantları aç", color = Bad) }
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Bant kilidi kaldırılsın mı?") },
            text = { Text("İnternet birkaç saniye kesilebilir.") },
            confirmButton = { TextButton(onClick = { confirm = false; vm.kediBandUnlock() }) { Text("Kaldır") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Vazgeç") } },
        )
    }
}

@Composable
private fun DnsTestCard(vm: PanelViewModel) {
    var running by remember { mutableStateOf(false) }
    var output by remember { mutableStateOf<String?>(null) }
    var dismissed by remember { mutableStateOf(false) }
    SettingsGroup {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Metin2 DNS testi", style = MaterialTheme.typography.titleSmall)
            Text(
                "Gameforge alan adlarını her DNS yolundan çözer: yanıt var mı, kaç ms sürdü.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = { running = true; dismissed = false; output = "Ölçüm başlatılıyor…" }, enabled = !running) {
                Text(if (running) "Ölçülüyor…" else "Testi çalıştır")
            }
        }
    }
    if (running) {
        LaunchedEffect(Unit) {
            try {
                vm.kediDnsTestStart()
                for (i in 0 until 60) {
                    delay(2000)
                    val (done, body) = vm.kediDnsTestRead()
                    if (!dismissed) output = body.ifEmpty { "Ölçülüyor…" } + if (done) "\n\n[Bitti]" else ""
                    if (done) break
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!dismissed) output = "Çalıştırılamadı: ${e.message}"
            }
            running = false
        }
    }
    output?.let { txt ->
        AlertDialog(
            onDismissRequest = { output = null; dismissed = true },
            title = { Text("Metin2 DNS testi") },
            text = {
                Text(
                    txt,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    modifier = Modifier.verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState()),
                )
            },
            confirmButton = { TextButton(onClick = { output = null; dismissed = true }) { Text("Kapat") } },
        )
    }
}
