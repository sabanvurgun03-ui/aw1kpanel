package com.saban.aw1kpanel.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.saban.aw1kpanel.data.AppSettings
import com.saban.aw1kpanel.data.BUILTIN_THEMES
import com.saban.aw1kpanel.data.ThemeDef
import com.saban.aw1kpanel.data.hexOf
import com.saban.aw1kpanel.data.parseHex
import com.saban.aw1kpanel.data.themeFromLink
import com.saban.aw1kpanel.data.themeToLink

/** Tam ekran Temalar sayfası: Sistem/Açık/Koyu, yerleşik temalar, kendi temaların, içe aktarma. */
@Composable
internal fun ThemesDialog(settings: AppSettings, vm: PanelViewModel, onClose: () -> Unit) {
    val context = LocalContext.current
    var editing by remember { mutableStateOf<ThemeDef?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.statusBarsPadding().padding(horizontal = 20.dp)) {
                Box(Modifier.fillMaxWidth().padding(vertical = 14.dp), contentAlignment = Alignment.Center) {
                    Surface(
                        modifier = Modifier.align(Alignment.CenterStart).size(44.dp).clickable { onClose() },
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Box(contentAlignment = Alignment.Center) { Text("‹", style = MaterialTheme.typography.headlineSmall) }
                    }
                    Text("Temalar", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                }

                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { GroupLabel("YERLEŞİK") }
                    item {
                        GroupCard {
                            val modes = listOf(0 to "Sistem", 1 to "Açık", 2 to "Koyu")
                            modes.forEachIndexed { i, (mode, label) ->
                                ThemeRow(
                                    name = label,
                                    dot = null,
                                    selected = settings.themeId.isEmpty() && settings.themeMode == mode,
                                    onClick = { vm.setTheme(mode) },
                                )
                                if (i < modes.lastIndex) RowDivider()
                            }
                            RowDivider()
                            BUILTIN_THEMES.forEachIndexed { i, t ->
                                ThemeRow(
                                    name = t.name,
                                    dot = t.accent,
                                    selected = settings.themeId == t.id,
                                    onClick = { vm.setThemeId(t.id) },
                                )
                                if (i < BUILTIN_THEMES.lastIndex) RowDivider()
                            }
                        }
                    }
                    item { Spacer(Modifier.height(8.dp)); GroupLabel("TEMALARINIZ") }
                    item {
                        GroupCard {
                            if (settings.customThemes.isEmpty()) {
                                Text(
                                    "Henüz özel tema yok",
                                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            settings.customThemes.forEachIndexed { i, t ->
                                CustomRow(
                                    t = t,
                                    selected = settings.themeId == t.id,
                                    onSelect = { vm.setThemeId(t.id) },
                                    onEdit = { editing = t; showEditor = true },
                                    onShare = {
                                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        cm.setPrimaryClip(ClipData.newPlainText("tema", themeToLink(t)))
                                        Toast.makeText(context, "Tema bağlantısı kopyalandı", Toast.LENGTH_SHORT).show()
                                    },
                                    onDelete = { vm.deleteCustomTheme(t.id) },
                                )
                                if (i < settings.customThemes.lastIndex) RowDivider()
                            }
                        }
                    }
                    item {
                        Text(
                            "Tema, bağlantı ekranı dahil tüm yüzeylere uygulanır. Yalnızca uygulama logosu olduğu gibi kalır.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp),
                        )
                    }
                    item {
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { editing = null; showEditor = true },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(16.dp),
                        ) { Text("+  Yeni tema", style = MaterialTheme.typography.titleMedium) }
                    }
                    item {
                        OutlinedButton(
                            onClick = { showImport = true },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(16.dp),
                        ) { Text("Bağlantıdan içe al", style = MaterialTheme.typography.titleMedium) }
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
        }
    }

    if (showEditor) {
        ThemeEditorSheet(
            initial = editing,
            onDismiss = { showEditor = false },
            onSave = { t -> vm.saveCustomTheme(t); showEditor = false },
        )
    }
    if (showImport) {
        ImportDialog(
            onDismiss = { showImport = false },
            onImport = { t -> vm.saveCustomTheme(t); showImport = false },
        )
    }
}

@Composable
private fun GroupLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 12.dp, top = 4.dp),
    )
}

@Composable
private fun GroupCard(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) { Column { content() } }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(Modifier.padding(horizontal = 18.dp), color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun ThemeRow(name: String, dot: Int?, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 18.dp, vertical = 17.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot != null) {
            Box(Modifier.size(14.dp).clip(CircleShape).background(Color(dot)))
            Spacer(Modifier.width(12.dp))
        }
        Text(name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        if (selected) Text("✓", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun CustomRow(
    t: ThemeDef,
    selected: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clickable { onSelect() }.padding(start = 18.dp, top = 6.dp, bottom = 6.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(14.dp).clip(CircleShape).background(Color(t.accent)))
        Spacer(Modifier.width(12.dp))
        Text(t.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        if (selected) Text("✓", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleLarge)
        Box {
            TextButton(onClick = { menu = true }) { Text("⋮", style = MaterialTheme.typography.titleLarge) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Düzenle") }, onClick = { menu = false; onEdit() })
                DropdownMenuItem(text = { Text("Bağlantıyı kopyala") }, onClick = { menu = false; onShare() })
                DropdownMenuItem(text = { Text("Sil", color = Bad) }, onClick = { menu = false; onDelete() })
            }
        }
    }
}

// ---- Düzenleyici -----------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeEditorSheet(initial: ThemeDef?, onDismiss: () -> Unit, onSave: (ThemeDef) -> Unit) {
    val base = initial ?: BUILTIN_THEMES.first()
    var name by remember { mutableStateOf(initial?.name ?: "Aurora") }
    var bg by remember { mutableStateOf(base.bg) }
    var accent by remember { mutableStateOf(base.accent) }
    var panel by remember { mutableStateOf(base.panel) }
    var panelEnd by remember { mutableStateOf(base.panelEnd) }
    var good by remember { mutableStateOf(base.good) }
    var warn by remember { mutableStateOf(base.warn) }
    var bad by remember { mutableStateOf(base.bad) }
    var picking by remember { mutableStateOf<String?>(null) }

    fun load(t: ThemeDef) {
        bg = t.bg; accent = t.accent; panel = t.panel; panelEnd = t.panelEnd
        good = t.good; warn = t.warn; bad = t.bad
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                if (initial == null) "Yeni tema" else "Temayı düzenle",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(10.dp))
            Text("Tema adı", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(24) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Şununla başla",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BUILTIN_THEMES.forEach { t ->
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color(t.bg))
                            .border(1.dp, Color(t.accent).copy(alpha = 0.35f), RoundedCornerShape(18.dp))
                            .clickable { load(t) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(12.dp).clip(CircleShape).background(Color(t.accent)))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            t.name,
                            fontWeight = FontWeight.SemiBold,
                            color = if (t.dark) Color.White else Color(0xFF10151C),
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))

            ColorRow("Zemin", bg) { picking = "bg" }
            ColorRow("Vurgu", accent) { picking = "accent" }
            ColorRow("Bağlantı paneli", panel) { picking = "panel" }
            ColorRow("Panel gradyan bitişi", panelEnd, auto = "Otomatik", onClear = { panelEnd = null }) { picking = "end" }
            ColorRow("Bağlı", good) { picking = "good" }
            ColorRow("Uyarı", warn) { picking = "warn" }
            ColorRow("Hata", bad) { picking = "bad" }

            // Küçük önizleme
            Spacer(Modifier.height(6.dp))
            val end = panelEnd ?: panel
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(bg))
                    .padding(12.dp),
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Brush.linearGradient(listOf(Color(panel), Color(end))))
                        .padding(14.dp),
                ) {
                    Text("Önizleme", color = Color.White.copy(alpha = 0.85f))
                    Text("Normal", color = Color(good), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Uyarı", color = Color(warn))
                        Text("Hata", color = Color(bad))
                        Text("Vurgu", color = Color(accent), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Text(
                "Kartlar, kenarlıklar ve yazı renkleri bu renklerden türetilir ve okunabilir kalmaları için her zaman ayarlanır. Tek renkli panel için gradyan bitişini Otomatik bırakın.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    val id = initial?.id ?: ("c" + java.lang.Long.toString(System.currentTimeMillis(), 36))
                    onSave(ThemeDef(id, name.trim().ifBlank { "Tema" }, bg, accent, panel, panelEnd, good, warn, bad))
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
            ) { Text("Kaydet", style = MaterialTheme.typography.titleMedium) }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Vazgeç") }
            Spacer(Modifier.height(20.dp))
        }
    }

    picking?.let { key ->
        val (title, cur) = when (key) {
            "bg" -> "Zemin" to bg
            "accent" -> "Vurgu" to accent
            "panel" -> "Bağlantı paneli" to panel
            "end" -> "Panel gradyan bitişi" to (panelEnd ?: panel)
            "good" -> "Bağlı" to good
            "warn" -> "Uyarı" to warn
            else -> "Hata" to bad
        }
        ColorDialog(
            title = title,
            initial = cur,
            onDismiss = { picking = null },
            onPick = { v ->
                when (key) {
                    "bg" -> bg = v
                    "accent" -> accent = v
                    "panel" -> panel = v
                    "end" -> panelEnd = v
                    "good" -> good = v
                    "warn" -> warn = v
                    else -> bad = v
                }
                picking = null
            },
        )
    }
}

@Composable
private fun ColorRow(
    label: String,
    color: Int?,
    auto: String = "",
    onClear: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 6.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        Text(
            if (color == null) auto else hexOf(color),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.width(12.dp))
        Box(
            Modifier
                .size(width = 50.dp, height = 38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (color == null) Color.Transparent else Color(color))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp)),
        )
        if (onClear != null && color != null) {
            TextButton(onClick = onClear) { Text("✕", color = MaterialTheme.colorScheme.primary) }
        }
    }
}

@Composable
private fun ColorDialog(title: String, initial: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    var r by remember { mutableStateOf(((initial shr 16) and 0xFF).toFloat()) }
    var g by remember { mutableStateOf(((initial shr 8) and 0xFF).toFloat()) }
    var b by remember { mutableStateOf((initial and 0xFF).toFloat()) }
    var hex by remember { mutableStateOf(hexOf(initial)) }
    fun current(): Int = (0xFF shl 24) or (r.toInt() shl 16) or (g.toInt() shl 8) or b.toInt()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(current()))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp)),
                )
                OutlinedTextField(
                    value = hex,
                    onValueChange = { v ->
                        hex = v.take(7)
                        parseHex(hex)?.let {
                            r = ((it shr 16) and 0xFF).toFloat(); g = ((it shr 8) and 0xFF).toFloat(); b = (it and 0xFF).toFloat()
                        }
                    },
                    singleLine = true,
                    label = { Text("Renk kodu") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("K", style = MaterialTheme.typography.labelMedium)
                Slider(value = r, onValueChange = { r = it; hex = hexOf(current()) }, valueRange = 0f..255f)
                Text("Y", style = MaterialTheme.typography.labelMedium)
                Slider(value = g, onValueChange = { g = it; hex = hexOf(current()) }, valueRange = 0f..255f)
                Text("M", style = MaterialTheme.typography.labelMedium)
                Slider(value = b, onValueChange = { b = it; hex = hexOf(current()) }, valueRange = 0f..255f)
            }
        },
        confirmButton = { TextButton(onClick = { onPick(current()) }) { Text("Tamam") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } },
    )
}

@Composable
private fun ImportDialog(onDismiss: () -> Unit, onImport: (ThemeDef) -> Unit) {
    var text by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Bağlantıdan içe al") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Başka bir cihazda \"Bağlantıyı kopyala\" ile aldığın tema bağlantısını yapıştır.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it; err = null },
                    placeholder = { Text("aw1ktema:…") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                err?.let { Text(it, color = Bad, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Start) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val t = themeFromLink(text)
                if (t == null) err = "Geçerli bir tema bağlantısı değil" else onImport(t)
            }) { Text("İçe al") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } },
    )
}
