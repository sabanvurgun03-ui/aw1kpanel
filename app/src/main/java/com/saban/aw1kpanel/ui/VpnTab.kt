package com.saban.aw1kpanel.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.unit.dp
import com.saban.aw1kpanel.data.AppSettings
import com.saban.aw1kpanel.data.KediNode
import com.saban.aw1kpanel.data.KediState
import com.saban.aw1kpanel.data.PingResult
import com.saban.aw1kpanel.data.VpnProfile
import androidx.compose.ui.graphics.Color
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlinx.coroutines.delay

@Composable
internal fun VpnTab(state: UiState, settings: AppSettings, vm: PanelViewModel) {
    var importOpen by remember { mutableStateOf(false) }
    var link by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<VpnProfile?>(null) }

    LazyColumn(
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().clickable { vm.setHideVpnData(!settings.hideVpnData) }.padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Sunucu adreslerini gizle", style = MaterialTheme.typography.bodyLarge)
                Switch(checked = settings.hideVpnData, onCheckedChange = { vm.setHideVpnData(it) })
            }
        }
        if (settings.showKedi) {
            state.kediray?.let { k ->
                item { KediCard(k, vm, settings.hideVpnData) }
            }
        }
        if (!settings.showKedi && !settings.showXray) {
            item {
                Text(
                    "Her iki VPN bölümü de gizli. Ayarlar sayfasından açabilirsin.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (settings.showXray) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("xDivine-Ray profilleri", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = vm::openNewProfile, modifier = Modifier.weight(1f)) {
                        Text("JSON ile ekle", maxLines = 1)
                    }
                    Button(onClick = { importOpen = true }, modifier = Modifier.weight(1f)) {
                        Text("Link ekle", maxLines = 1)
                    }
                }
            }
        }
        if (state.profiles.isEmpty()) {
            item {
                Text(
                    "Kayıtlı profil yok. \"Link ekle\" ile vless://, vmess://, trojan:// veya ss:// linkini yapıştırabilirsin.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(state.profiles, key = { it.id }) { p ->
            val active = p.id == state.activeProfileId
            val live = active && state.vpn?.connected == true
            SectionCard {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(p.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            listOf(p.proto, p.network, p.security).filter { it.isNotEmpty() }.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "${if (settings.hideVpnData) maskHost(p.address) else p.address}:${p.port}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (p.sni.isNotEmpty()) {
                            Text(
                                "SNI ${if (settings.hideVpnData) "••••••" else p.sni}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    when {
                        live -> Text("Bağlı", color = Good, fontWeight = FontWeight.Medium)
                        active -> Text(
                            "Seçili",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.connectProfile(p.id) }, enabled = !live) {
                        Text(if (active) "Bağlan" else "Buna geç")
                    }
                    OutlinedButton(
                        onClick = { vm.pingProfile(p.id) },
                        enabled = p.id !in state.pinging,
                    ) { Text("Ping") }
                    PingBadge(state.pings[p.id], p.id in state.pinging)
                    Box {
                        var menu by remember { mutableStateOf(false) }
                        TextButton(onClick = { menu = true }) { Text("⋮") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text("Düzenle") },
                                onClick = { menu = false; vm.openProfileEditor(p.id) },
                            )
                            DropdownMenuItem(
                                text = { Text("Sil", color = Bad) },
                                onClick = { menu = false; deleteTarget = p },
                            )
                        }
                    }
                }
            }
        }
        }
    }

    state.editor?.let { ed ->
        ProfileEditor(initial = ed, onSave = vm::saveEditor, onClose = vm::closeEditor)
    }

    if (importOpen) {
        AlertDialog(
            onDismissRequest = { importOpen = false },
            title = { Text("Link ekle") },
            text = {
                OutlinedTextField(
                    value = link,
                    onValueChange = { link = it },
                    label = { Text("vless:// vmess:// trojan:// ss://") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = link.isNotBlank(),
                    onClick = {
                        vm.importLink(link)
                        link = ""
                        importOpen = false
                    },
                ) { Text("Ekle") }
            },
            dismissButton = {
                TextButton(onClick = { importOpen = false }) { Text("Vazgeç") }
            },
        )
    }

    deleteTarget?.let { p ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Profil silinsin mi?") },
            text = {
                Text(
                    if (p.id == state.activeProfileId) {
                        "\"${p.name}\" şu an seçili profil. Silersen VPN bağlantısı kesilebilir."
                    } else {
                        "\"${p.name}\" kalıcı olarak silinir."
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.deleteProfile(p.id); deleteTarget = null }) { Text("Sil") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Vazgeç") }
            },
        )
    }
}

/** <100 ms yeşil, 100-129 ms sarı, 130 ms ve üzeri kırmızı. */
@Composable
private fun PingBadge(result: PingResult?, busy: Boolean) {
    val (text, color) = when {
        busy -> "…" to MaterialTheme.colorScheme.onSurfaceVariant
        result == null -> return
        result.ms == null -> "Hata" to Bad
        result.ms < 100 -> "${result.ms} ms" to Good
        result.ms < 130 -> "${result.ms} ms" to Warn
        else -> "${result.ms} ms" to Bad
    }
    Row(Modifier.height(40.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, color = color, fontWeight = FontWeight.Bold)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KediCard(k: KediState, vm: PanelViewModel, hide: Boolean) {
    var confirmStop by remember { mutableStateOf(false) }
    var deleteNode by remember { mutableStateOf<KediNode?>(null) }
    var addLinkOpen by remember { mutableStateOf(false) }
    var sheetOpen by remember { mutableStateOf(false) }
    var kediLink by remember { mutableStateOf("") }

    // Canlı hız geçmişi: 2,5 sn'de bir son gerçek değer eklenir (en fazla 60 örnek).
    val latest by rememberUpdatedState(k)
    val downShown by animateFloatAsState(k.downlinkRate.toFloat(), tween(700), label = "indirme")
    val upShown by animateFloatAsState(k.uplinkRate.toFloat(), tween(700), label = "yukleme")
    val upHist = remember { mutableStateListOf<Float>() }
    val downHist = remember { mutableStateListOf<Float>() }
    LaunchedEffect(Unit) {
        while (true) {
            val cur = latest
            upHist.add(if (cur.running) cur.uplinkRate.toFloat() else 0f)
            downHist.add(if (cur.running) cur.downlinkRate.toFloat() else 0f)
            while (upHist.size > SPARK_POINTS) upHist.removeAt(0)
            while (downHist.size > SPARK_POINTS) downHist.removeAt(0)
            delay(2500)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Hero durum kartı
        SectionCard(brush = HeaderBrush) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(k.running)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("KediRay", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.75f))
                        Text(
                            if (k.running) "Çalışıyor" else "Durdu",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                    }
                }
                if (k.running) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color.White.copy(alpha = 0.18f))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text(
                            formatUptimeShort(k.uptimeSeconds),
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White,
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                Column {
                    Text("↓ İndirme", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.75f))
                    Text(
                        rateText(downShown.toLong()),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }
                Column {
                    Text("↑ Yükleme", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.75f))
                    Text(
                        rateText(upShown.toLong()),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White.copy(alpha = 0.9f),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Sparkline(downHist, upHist, Modifier.fillMaxWidth().height(64.dp))
        }

        Text(
            "Sunucular",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 4.dp, start = 4.dp),
        )
        k.nodes.forEach { n ->
            NodeCard(
                n = n,
                active = n.section == k.activeSection,
                hide = hide,
                onUse = { vm.useKediNode(n.section, n.name) },
                onEdit = { vm.openKediNode(n.section) },
                onToggle = { vm.setKediNodeEnabled(n.section, !n.enabled) },
                onDelete = { deleteNode = n },
            )
        }

        Button(
            onClick = { sheetOpen = true },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(26.dp),
        ) { Text("+ Sunucu ekle") }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (k.running) {
                FilledTonalButton(onClick = { vm.kedirayAction("restart") }) { Text("Yeniden başlat") }
                TextButton(onClick = { confirmStop = true }) { Text("Durdur", color = Bad) }
            } else {
                Button(onClick = { vm.kedirayAction("start") }) { Text("Başlat") }
            }
        }
    }

    if (sheetOpen) {
        ModalBottomSheet(
            onDismissRequest = { sheetOpen = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(
                Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Sunucu ekle", style = MaterialTheme.typography.titleLarge)
                SheetAction("Link / JSON ekle", "vless://, vmess://, trojan://, ss:// ya da Xray JSON") {
                    sheetOpen = false
                    addLinkOpen = true
                }
                SheetAction("Elle düğüm ekle", "Alanları kendin doldur") {
                    sheetOpen = false
                    vm.openNewKediNode()
                }
                Text(
                    "Gelişmiş",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                SheetAction("🔒 Sertifikayı sabitle", "Sunucu sertifikasını sabitler") {
                    sheetOpen = false
                    vm.kediPin()
                }
            }
        }
    }
    if (addLinkOpen) {
        AlertDialog(
            onDismissRequest = { addLinkOpen = false },
            title = { Text("Link / JSON ekle") },
            text = {
                OutlinedTextField(
                    value = kediLink,
                    onValueChange = { kediLink = it },
                    label = { Text("vless:// vmess:// trojan:// ss:// ya da Xray JSON") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = kediLink.isNotBlank(),
                    onClick = {
                        vm.addKediLink(kediLink)
                        kediLink = ""
                        addLinkOpen = false
                    },
                ) { Text("Ekle") }
            },
            dismissButton = { TextButton(onClick = { addLinkOpen = false }) { Text("Vazgeç") } },
        )
    }
    deleteNode?.let { n ->
        AlertDialog(
            onDismissRequest = { deleteNode = null },
            title = { Text("Düğüm silinsin mi?") },
            text = { Text("\"${n.name}\" kalıcı olarak silinir. Değişiklik KediRay yeniden başlayınca geçerli olur.") },
            confirmButton = {
                TextButton(onClick = { vm.deleteKediNode(n.section); deleteNode = null }) { Text("Sil") }
            },
            dismissButton = { TextButton(onClick = { deleteNode = null }) { Text("Vazgeç") } },
        )
    }

    if (confirmStop) {
        AlertDialog(
            onDismissRequest = { confirmStop = false },
            title = { Text("KediRay durdurulsun mu?") },
            text = { Text("Hat doğrudan internete çıkamıyorsa durdurunca modemde internet kesilir.") },
            confirmButton = {
                TextButton(onClick = { vm.kedirayAction("stop"); confirmStop = false }) { Text("Durdur") }
            },
            dismissButton = { TextButton(onClick = { confirmStop = false }) { Text("Vazgeç") } },
        )
    }
}

private const val SPARK_POINTS = 60

@Composable
private fun StatusDot(running: Boolean) {
    val t = rememberInfiniteTransition(label = "pulse")
    val a by t.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulseAlpha",
    )
    val base = if (running) Color(0xFF5BE584) else Color(0xFFFF7A7A)
    Box(
        Modifier
            .size(14.dp)
            .clip(CircleShape)
            .background(base.copy(alpha = if (running) a else 1f)),
    )
}

/** Gerçek hız geçmişi: dolgulu çizgi indirme, ince çizgi yükleme. */
@Composable
private fun Sparkline(down: List<Float>, up: List<Float>, modifier: Modifier) {
    Canvas(modifier) {
        val d = down.toList()
        val u = up.toList()
        val peak = maxOf(d.maxOrNull() ?: 0f, u.maxOrNull() ?: 0f, 1024f)
        val w = size.width
        val h = size.height
        fun points(v: List<Float>): List<Offset> = v.mapIndexed { i, value ->
            val x = w * (i + (SPARK_POINTS - v.size)) / (SPARK_POINTS - 1).toFloat()
            val y = h - (value / peak) * h * 0.9f - 2f
            Offset(x, y)
        }
        fun line(p: List<Offset>): Path = Path().apply {
            p.forEachIndexed { i, o -> if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y) }
        }
        if (d.size >= 2) {
            val p = points(d)
            val fill = line(p).apply {
                lineTo(p.last().x, h)
                lineTo(p.first().x, h)
                close()
            }
            drawPath(fill, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.35f), Color.Transparent)))
            drawPath(line(p), Color.White, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        if (u.size >= 2) {
            drawPath(line(points(u)), Color.White.copy(alpha = 0.55f), style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

@Composable
private fun NodeCard(
    n: KediNode,
    active: Boolean,
    hide: Boolean,
    onUse: () -> Unit,
    onEdit: () -> Unit,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val border by animateColorAsState(
        if (active) scheme.primary else scheme.outline.copy(alpha = 0.35f),
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "nodeBorder",
    )
    val container by animateColorAsState(
        if (active) scheme.primaryContainer.copy(alpha = 0.55f) else scheme.surface,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "nodeContainer",
    )
    Surface(
        onClick = onUse,
        enabled = !active,
        shape = RoundedCornerShape(22.dp),
        color = container,
        border = BorderStroke(if (active) 2.dp else 1.dp, border),
        modifier = Modifier.fillMaxWidth().alpha(if (n.enabled) 1f else 0.6f),
    ) {
        Row(
            Modifier.padding(start = 16.dp, top = 14.dp, bottom = 14.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    n.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.onSurface,
                )
                if (active) {
                    Text("● Aktif sunucu", style = MaterialTheme.typography.labelMedium, color = Good)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(n.protocol, n.network, n.tls).filter { it.isNotEmpty() }.forEach { Chip(it) }
                    if (!n.enabled) Chip("Kapalı", Bad)
                }
                Text(
                    "${if (hide) maskHost(n.address) else n.address}:${n.port}",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
            Box {
                var menu by remember { mutableStateOf(false) }
                Box(
                    Modifier.size(44.dp).clip(CircleShape).clickable { menu = true },
                    contentAlignment = Alignment.Center,
                ) { Text("⋮", style = MaterialTheme.typography.titleLarge, color = scheme.onSurfaceVariant) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Düzenle") }, onClick = { menu = false; onEdit() })
                    DropdownMenuItem(
                        text = { Text(if (n.enabled) "Kapat" else "Aç") },
                        onClick = { menu = false; onToggle() },
                    )
                    DropdownMenuItem(text = { Text("Sil", color = Bad) }, onClick = { menu = false; onDelete() })
                }
            }
        }
    }
}

@Composable
private fun SheetAction(title: String, subtitle: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun rateText(b: Long): String = when {
    b >= 1_048_576 -> "%.1f MB/s".format(b / 1_048_576.0)
    b >= 1024 -> "%.1f KB/s".format(b / 1024.0)
    else -> "$b B/s"
}

private fun formatUptimeShort(s: Long): String {
    val h = s / 3600
    val m = (s % 3600) / 60
    return if (h > 0) "${h} sa ${m} dk" else "${m} dk"
}
