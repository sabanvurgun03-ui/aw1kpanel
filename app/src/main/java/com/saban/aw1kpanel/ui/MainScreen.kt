package com.saban.aw1kpanel.ui

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.AnimatedContent
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import com.saban.aw1kpanel.R
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.rememberDrawerState
import com.saban.aw1kpanel.data.SystemState
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.saban.aw1kpanel.data.AppSettings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.saban.aw1kpanel.data.Device
import com.saban.aw1kpanel.data.ModemState
import com.saban.aw1kpanel.data.SignalItem
import com.saban.aw1kpanel.data.VpnState
import com.saban.aw1kpanel.data.WifiNetwork
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Menüde gösterim grupları (sayfa numaraları PAGES sırasına göredir). */
private val DRAWER_GROUPS: List<Pair<String?, List<Int>>> = listOf(
    null to listOf(0),
    "Ağ" to listOf(1, 2, 4, 5, 3),
    "Veri" to listOf(8, 9, 7),
    "Sistem" to listOf(6, 10, 11),
)

private val PAGES = listOf("Ana sayfa", "Wi-Fi", "Ağ", "Band kilidi", "Cihaz", "VPN", "Güncelleme", "Günlük", "Veri", "Modemdata", "Ayarlar", "Hakkında")
private val PAGE_ICONS = listOf(
    R.drawable.ic_nav_home, R.drawable.ic_nav_wifi, R.drawable.ic_nav_network, R.drawable.ic_nav_band, R.drawable.ic_nav_devices,
    R.drawable.ic_nav_vpn, R.drawable.ic_nav_update, R.drawable.ic_nav_log, R.drawable.ic_nav_data, R.drawable.ic_nav_modemdata, R.drawable.ic_nav_settings,
    R.drawable.ic_nav_info,
)

/** Çekmece öğesi: seçiliyken mor zemin ve sol çubuk "nefes alıp verir", simge hafifçe büyüyüp küçülür. */
@Composable
private fun DrawerItem(
    label: String,
    icon: Int,
    selected: Boolean,
    onClick: () -> Unit,
    tint: Color? = null,
    index: Int = 0,
    open: Boolean = true,
    badge: String? = null,
    badgeColor: Color? = null,
) {
    val enter by animateFloatAsState(
        targetValue = if (open) 1f else 0f,
        animationSpec = tween(durationMillis = 320, delayMillis = if (open) 40 + index * 28 else 0, easing = FastOutSlowInEasing),
        label = "menuGiris",
    )
    val selAnim by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "secili",
    )
    val breathe = if (selected) {
        rememberInfiniteTransition(label = "nefes").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "nefes",
        ).value
    } else {
        0f
    }
    val primary = MaterialTheme.colorScheme.primary
    val content = tint ?: if (selected) primary else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier
            .graphicsLayer {
                alpha = enter
                translationX = (1f - enter) * -48f
            }
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(primary.copy(alpha = selAnim * (0.12f + 0.14f * breathe)))
            .clickable(onClick = onClick)
            .padding(end = 16.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .padding(start = 6.dp)
                .width(4.dp)
                .height(((4 + 12 * selAnim) + 12 * breathe * selAnim).dp)
                .clip(RoundedCornerShape(2.dp))
                .background(primary.copy(alpha = selAnim * (0.55f + 0.45f * breathe))),
        )
        Spacer(Modifier.width(12.dp))
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = content,
            modifier = Modifier
                .size(24.dp)
                .scale(1f + 0.12f * breathe),
        )
        Spacer(Modifier.width(16.dp))
        Text(
            label,
            color = content,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
        if (badge != null) {
            Spacer(Modifier.weight(1f))
            val bc = badgeColor ?: primary
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(bc.copy(alpha = 0.18f))
                    .padding(horizontal = 10.dp, vertical = 2.dp),
            ) {
                Text(badge, style = MaterialTheme.typography.labelMedium, color = bc, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun MainScreen(state: UiState, settings: AppSettings, vm: PanelViewModel) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }
    val owner = LocalLifecycleOwner.current
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // Ekran görünürken modemi düzenli sorgula, arka plana geçince dur.
    LaunchedEffect(owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            coroutineScope {
                launch { while (true) { vm.refreshModem(); delay(3000) } }
                launch { while (true) { vm.refreshSystem(); delay(3000) } }
                launch { while (true) { vm.refreshSlow(); delay(10000) } }
            }
        }
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            vm.consumeMessage()
        }
    }

    BackHandler(enabled = drawer.isOpen) { scope.launch { drawer.close() } }

    val routerName = state.routers.firstOrNull { it.id == state.activeRouterId }?.name

    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            ModalDrawerSheet {
                val vpnOn = state.vpn?.connected == true || state.kediray?.running == true
                val drawerOpen = drawer.targetValue == DrawerValue.Open
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    Box(
                        Modifier
                            .padding(12.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(HeaderBrush)
                            .padding(16.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            KediLogo(52.dp)
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text(
                                    "AW1K Panel",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                )
                                Text(
                                    routerName ?: state.modem.modemName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = 0.8f),
                                )
                                Spacer(Modifier.height(6.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(if (vpnOn) Color(0xFF5BE584) else Color(0xFFFF7A7A)),
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        if (vpnOn) "VPN çalışıyor" else "VPN kapalı",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color.White.copy(alpha = 0.9f),
                                    )
                                }
                            }
                        }
                    }
                    var n = 0
                    DRAWER_GROUPS.forEach { (groupTitle, items) ->
                        if (groupTitle != null) {
                            Text(
                                groupTitle,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 28.dp, top = 12.dp, bottom = 4.dp),
                            )
                        }
                        items.forEach { i ->
                            val badge: String? = when (i) {
                                1 -> state.wifiClients.size.takeIf { it > 0 }?.toString()
                                4 -> state.devices.size.takeIf { it > 0 }?.toString()
                                5 -> if (vpnOn) "Açık" else null
                                6 -> if (state.appUpdate.newer) "Yeni" else null
                                else -> null
                            }
                            DrawerItem(
                                label = PAGES[i],
                                icon = PAGE_ICONS[i],
                                selected = page == i,
                                index = n++,
                                open = drawerOpen,
                                badge = badge,
                                badgeColor = if (i == 5 || i == 6) Good else null,
                                onClick = {
                                    page = i
                                    scope.launch { drawer.close() }
                                },
                            )
                        }
                    }
                }
                DrawerItem(
                    label = "Çıkış",
                    icon = R.drawable.ic_nav_logout,
                    selected = false,
                    index = PAGES.size,
                    open = drawer.targetValue == DrawerValue.Open,
                    tint = Bad,
                    onClick = {
                        scope.launch { drawer.close() }
                        vm.logout()
                    },
                )
                Spacer(Modifier.height(16.dp))
            }
        },
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            containerColor = MaterialTheme.colorScheme.background,
        ) { pad ->
            Column(
                Modifier
                    .padding(pad)
                    .padding(horizontal = 16.dp),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { scope.launch { drawer.open() } }) {
                        Text("☰", style = MaterialTheme.typography.titleLarge)
                    }
                    KediLogo(32.dp)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        AnimatedContent(
                            targetState = page,
                            transitionSpec = {
                                (slideInVertically(tween(280, easing = FastOutSlowInEasing)) { it / 2 } + fadeIn(tween(240))) togetherWith
                                    (slideOutVertically(tween(200)) { -it / 2 } + fadeOut(tween(140))) using
                                    SizeTransform(clip = true) { _, _ -> snap() }
                            },
                            label = "baslik",
                        ) { p ->
                            Text(
                                PAGES[p],
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        routerName?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                state.wifiEditor?.let { WifiEditor(it, onSave = vm::saveWifi, onClose = vm::closeWifiEditor) }
                AnimatedContent(
                    targetState = page,
                    modifier = Modifier.fillMaxSize(),
                    transitionSpec = {
                        val dir = if (targetState > initialState) 1 else -1
                        (slideInHorizontally(tween(340, easing = FastOutSlowInEasing)) { (it * 0.18f).toInt() * dir } +
                            fadeIn(tween(280, delayMillis = 60))) togetherWith
                            (slideOutHorizontally(tween(240, easing = FastOutSlowInEasing)) { -(it * 0.18f).toInt() * dir } +
                                fadeOut(tween(160))) using
                            SizeTransform(clip = false) { _, _ -> snap() }
                    },
                    label = "sayfa",
                ) { p ->
                    when (p) {
                        0 -> HomeTab(state, vm)
                        1 -> WifiTab(state, settings, vm)
                        2 -> NetworkTab(
                            state.modem, state.vpn, state.vpnProfileId != null,
                            vm::setVpn, vm::redial, vm::rebootRouter,
                        )
                        3 -> BandLockTab(state, settings, vm)
                        4 -> DevicesTab(state, settings, vm)
                        5 -> VpnTab(state, settings, vm)
                        6 -> UpdateTab(state, vm)
                        7 -> LogTab(state, vm)
                        8 -> UsageTab(state.usage, state.trafficDays)
                        9 -> ModemdataTab(state, vm)
                        10 -> SettingsTab(state, settings, vm)
                        else -> AboutTab(state.kediray)
                    }
                }
            }
        }
    }
}

// ---- Ana sayfa -----------------------------------------------------------

@Composable
private fun HomeTab(state: UiState, vm: PanelViewModel) {
    LazyColumn(
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { HeaderCard(state.modem, state.modemError) }
        item { SystemStrip(state.system) }
        item {
            val v = state.vpn
            SectionCard {
                Text(
                    "VPN" + (v?.provider?.let { " · $it" } ?: ""),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(4.dp))
                if (v == null) {
                    Text("Durum okunamadı", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text(
                        if (v.connected) "Bağlı · ${v.profileName}" else "Bağlı değil",
                        color = if (v.connected) Good else Bad,
                        fontWeight = FontWeight.Medium,
                    )
                    if (v.connected) {
                        Text(
                            "Süre ${formatUptime(v.uptimeSeconds)} · ↑ ${formatRate(v.uplinkRate)} · ↓ ${formatRate(v.downlinkRate)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SystemStrip(s: SystemState?) {
    SectionCard {
        CardTitle(R.drawable.ic_nav_home, "Sistem")
        Spacer(Modifier.height(8.dp))
        if (s == null) {
            Text("Okunuyor…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@SectionCard
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatBlock("CPU", s.cpuPercent?.let { "%$it" } ?: "—", Modifier.weight(1f))
            StatBlock(
                "Bellek",
                "%${s.memPercent}",
                Modifier.weight(1f),
                "${s.memUsed / 1_048_576} / ${s.memTotal / 1_048_576} MB",
            )
            StatBlock("Çalışma süresi", formatUptimeLong(s.uptimeSeconds), Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        LinearProgressIndicator(
            progress = { ((s.cpuPercent ?: 0) / 100f).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { (s.memPercent / 100f).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "Yük ortalaması (1 dk): %.2f".format(s.load1),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun StatBlock(label: String, value: String, modifier: Modifier, sub: String? = null) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        sub?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, color: Color = Color.Unspecified) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = color)
    }
}

private fun fmtNetBytes(b: Long): String = when {
    b >= (1L shl 30) -> "%.2f GB".format(b / 1073741824.0)
    b >= (1L shl 20) -> "%.1f MB".format(b / 1048576.0)
    b >= 1024L -> "%.0f KB".format(b / 1024.0)
    else -> "$b B"
}

private fun formatUptimeLong(seconds: Long): String {
    val d = seconds / 86400
    val h = (seconds % 86400) / 3600
    val m = (seconds % 3600) / 60
    return when {
        d > 0 -> "${d} g ${h} sa"
        h > 0 -> "${h} sa ${m} dk"
        else -> "${m} dk"
    }
}

@Composable
internal fun SectionCard(brush: Brush? = null, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Card(
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = if (brush != null) Color.Transparent else MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (brush != null) 0.dp else 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier
                .then(if (brush != null) Modifier.background(brush, shape) else Modifier)
                .padding(18.dp),
            content = content,
        )
    }
}

@Composable
private fun HeaderCard(m: ModemState, error: String?) {
    val (statusText, statusColor) = when {
        error != null -> "Bağlantı sorunu" to Color(0xFFFCA5A5)
        m.connected -> "Normal" to Color(0xFF86EFAC)
        else -> "Bağlı değil" to Color(0xFFFCA5A5)
    }
    val soft = Color.White.copy(alpha = 0.82f)
    SectionCard(brush = HeaderBrush) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(m.operatorName, color = soft)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(technology(m), color = soft)
                    if (m.cells.isNotEmpty()) {
                        Text(
                            m.cells.joinToString(" + ") { it.band },
                            style = MaterialTheme.typography.bodySmall,
                            color = soft,
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                SignalBars(level = signalLevel(m.signalPercent), color = soft)
                m.signalPercent?.let {
                    Spacer(Modifier.width(6.dp))
                    Text("%$it", color = soft, fontWeight = FontWeight.Medium)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            statusText,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = statusColor,
        )
        if (error != null) {
            Text(error, style = MaterialTheme.typography.bodySmall, color = Color(0xFFFCA5A5))
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            RateBlock("Yükleme", m.txRate, soft)
            RateBlock("İndirme", m.rxRate, soft)
        }
    }
}

@Composable
private fun RateBlock(label: String, value: String, tint: Color) {
    Column {
        Text(label, style = MaterialTheme.typography.bodySmall, color = tint)
        Text(value, style = MaterialTheme.typography.titleLarge, color = Color.White)
    }
}

private fun signalLevel(percent: Int?): Int = when {
    percent == null -> 0
    else -> ((percent + 19) / 20).coerceIn(0, 5)
}

/** "LTE-A (2CA)" gibi: birden fazla taşıyıcı varsa LTE'ye -A ve CA sayısı eklenir. */
private fun technology(m: ModemState): String =
    if (m.carriers > 1 && m.networkMode.uppercase().startsWith("LTE") && !m.networkMode.contains("-A")) {
        "LTE-A (${m.carriers}CA)"
    } else {
        m.networkMode
    }

@Composable
private fun SignalBars(level: Int, color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
        for (i in 1..5) {
            Box(
                Modifier
                    .width(4.dp)
                    .height((4 + i * 2).dp)
                    .background(if (i <= level) color else color.copy(alpha = 0.25f), RoundedCornerShape(1.dp)),
            )
        }
    }
}

// ---- Ağ sekmesi ----------------------------------------------------------

@Composable
private fun NetworkTab(
    modem: ModemState,
    vpn: VpnState?,
    vpnProfileKnown: Boolean,
    onVpnToggle: (Boolean) -> Unit,
    onRedial: () -> Unit,
    onReboot: () -> Unit,
) {
    var confirmReboot by remember { mutableStateOf(false) }
    var confirmRedial by remember { mutableStateOf(false) }
    var confirmVpnOff by remember { mutableStateOf(false) }

    LazyColumn(
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            SectionCard {
                CardTitle(R.drawable.ic_nav_network, "Bağlantı durumu")
                Spacer(Modifier.height(8.dp))
                InfoRow("Bağlantı durumu", if (modem.connected) "Bağlandı" else "Bağlı değil", if (modem.connected) Good else Bad)
                InfoRow("Operatör", modem.operatorName)
                InfoRow("Ülke", modem.country)
                InfoRow("Teknoloji", technology(modem))
                InfoRow("Bağlantı süresi", modem.connSeconds?.let { formatUptimeLong(it) } ?: "—")
                InfoRow("▲ Gönderildi", modem.sentBytes?.let { fmtNetBytes(it) } ?: "—")
                InfoRow("▼ Alındı", modem.recvBytes?.let { fmtNetBytes(it) } ?: "—")
            }
        }
        items(modem.cells) { cell ->
            SectionCard {
                CardTitle(R.drawable.ic_nav_network, "${cell.role} · ${cell.band}")
                Spacer(Modifier.height(8.dp))
                InfoRow("Bant genişliği", cell.bandwidth)
                InfoRow("Fiziksel hücre kimliği", cell.pci)
                InfoRow("EARFCN", cell.earfcn)
                InfoRow("RSSI", cell.rssi + if (cell.rssi != "—") " dBm" else "")
                InfoRow("RSRP", cell.rsrp + if (cell.rsrp != "—") " dBm" else "")
                InfoRow("RSRQ", cell.rsrq + if (cell.rsrq != "—") " dB" else "")
                InfoRow("SINR", cell.sinr + if (cell.sinr != "—") " dB" else "")
            }
        }
        item {
            SectionCard {
                CardTitle(R.drawable.ic_nav_network, "Sinyal")
                Spacer(Modifier.height(8.dp))
                if (modem.signal.isEmpty()) {
                    Text("Veri yok", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                modem.signal.forEach { s ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(s.label, style = MaterialTheme.typography.bodyMedium)
                        Text("${s.value} ${s.unit}".trim(), style = MaterialTheme.typography.bodyMedium)
                    }
                    s.fraction?.let {
                        LinearProgressIndicator(
                            progress = { it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 2.dp, bottom = 8.dp),
                        )
                    }
                }
            }
        }
        item {
            SectionCard {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CardTitle(R.drawable.ic_nav_vpn, "VPN")
                    if (vpn != null) {
                        Switch(
                            checked = vpn.connected,
                            enabled = vpn.connected || vpnProfileKnown,
                            onCheckedChange = { on -> if (on) onVpnToggle(true) else confirmVpnOff = true },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (vpn == null) {
                    Text("Durum okunamadı", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text(
                        if (vpn.connected) "Bağlı · ${vpn.profileName}" else "Bağlı değil",
                        color = if (vpn.connected) Good else Bad,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        "Süre ${formatUptime(vpn.uptimeSeconds)} · ↑ ${formatRate(vpn.uplinkRate)} · ↓ ${formatRate(vpn.downlinkRate)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    vpn.fault?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(it, style = MaterialTheme.typography.bodySmall, color = Bad)
                    }
                }
            }
        }
        item {
            SectionCard {
                CardTitle(R.drawable.ic_modem, "Modem")
                Spacer(Modifier.height(8.dp))
                Text("Model: ${modem.modemName}")
                Text("Sıcaklık: ${modem.temperature}")
                Spacer(Modifier.height(12.dp))
                Button(onClick = { confirmRedial = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Yeniden bağlan")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { confirmReboot = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Modemi yeniden başlat", color = Bad)
                }
            }
        }
    }

    if (confirmVpnOff) {
        AlertDialog(
            onDismissRequest = { confirmVpnOff = false },
            title = { Text("VPN kapatılsın mı?") },
            text = { Text("Modemin VPN üzerinden geçen trafiği kesilir veya doğrudan çıkar.") },
            confirmButton = {
                TextButton(onClick = { onVpnToggle(false); confirmVpnOff = false }) { Text("Kapat") }
            },
            dismissButton = {
                TextButton(onClick = { confirmVpnOff = false }) { Text("Vazgeç") }
            },
        )
    }

    if (confirmReboot) {
        AlertDialog(
            onDismissRequest = { confirmReboot = false },
            title = { Text("Modem yeniden başlatılsın mı?") },
            text = { Text("Tüm bağlantılar (internet, Wi-Fi, VPN) 2-3 dakika kesilir. Uygulama modem açılınca yeniden bağlanır.") },
            confirmButton = {
                TextButton(onClick = { onReboot(); confirmReboot = false }) { Text("Yeniden başlat", color = Bad) }
            },
            dismissButton = { TextButton(onClick = { confirmReboot = false }) { Text("Vazgeç") } },
        )
    }

    if (confirmRedial) {
        AlertDialog(
            onDismissRequest = { confirmRedial = false },
            title = { Text("Yeniden bağlanılsın mı?") },
            text = { Text("Mobil veri bağlantısı birkaç saniyeliğine kesilir.") },
            confirmButton = {
                TextButton(onClick = { onRedial(); confirmRedial = false }) { Text("Evet") }
            },
            dismissButton = {
                TextButton(onClick = { confirmRedial = false }) { Text("Vazgeç") }
            },
        )
    }
}

private fun formatRate(bytesPerSec: Long): String = when {
    bytesPerSec >= 1_048_576 -> "%.1f MB/s".format(bytesPerSec / 1_048_576.0)
    bytesPerSec >= 1024 -> "%.1f KB/s".format(bytesPerSec / 1024.0)
    else -> "$bytesPerSec B/s"
}

private fun formatUptime(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    return if (h > 0) "${h} sa ${m} dk" else "${m} dk"
}

// ---- Ayarlar sekmesi -----------------------------------------------------

@Composable
private fun SettingsTab(state: UiState, settings: AppSettings, vm: PanelViewModel) {
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            vm.setVpnAlert(true)
        } else {
            vm.showMessage("Bildirim izni verilmediği için uyarı açılamadı")
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { RoutersCard(state, vm) }
        item { BandPasswordCard(settings, vm) }
        item {
            SectionCard {
                Text("VPN sayfası", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Kullanmadığın bölümü gizleyebilirsin.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("KediRay bölümünü göster")
                    Switch(checked = settings.showKedi, onCheckedChange = vm::setShowKedi)
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("xDivine-Ray bölümünü göster")
                    Switch(checked = settings.showXray, onCheckedChange = vm::setShowXray)
                }
            }
        }
        item {
            var showThemes by rememberSaveable { mutableStateOf(false) }
            val curName = (com.saban.aw1kpanel.data.BUILTIN_THEMES + settings.customThemes)
                .firstOrNull { it.id == settings.themeId }?.name
                ?: when (settings.themeMode) { 1 -> "Açık"; 2 -> "Koyu"; else -> "Sistem" }
            SectionCard {
                Row(
                    Modifier.fillMaxWidth().clickable { showThemes = true },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Görünüm", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Tema: $curName. Açık/koyu mod, hazır temalar ya da kendi renklerin.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text("›", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            if (showThemes) ThemesDialog(settings, vm) { showThemes = false }
        }
        item {
            SectionCard {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("VPN kopma uyarısı", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "VPN bağlıyken kopunca bildirim gönderir. Telefon modemin Wi-Fi ağındayken çalışır ve arka planda küçük bir bildirim gösterir.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(
                        checked = settings.vpnAlert,
                        onCheckedChange = { on ->
                            val needsPermission = on &&
                                Build.VERSION.SDK_INT >= 33 &&
                                ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.POST_NOTIFICATIONS,
                                ) != PackageManager.PERMISSION_GRANTED
                            when {
                                needsPermission -> permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                else -> vm.setVpnAlert(on)
                            }
                        },
                    )
                }
            }
        }
        item {
            SectionCard {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Geliştirmeye yardımcı ol", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Açıksa günde en çok bir kez rastgele bir kurulum kimliği, uygulama sürümü ve uygulama adı gönderilir. Modem adresin, şifren, VPN sunucuların, cihazların ve trafiğin gönderilmez. Kapatınca kimlik silinir.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(
                        checked = settings.statsConsent == 1,
                        onCheckedChange = { vm.setStatsConsent(it) },
                    )
                }
            }
        }
        item {
            Text(
                "AW1K Panel v$APP_VERSION",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
