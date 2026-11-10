package com.saban.aw1kpanel

import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.saban.aw1kpanel.data.BUILTIN_THEMES
import com.saban.aw1kpanel.ui.AW1KTheme
import com.saban.aw1kpanel.ui.AppUpdateDialog
import com.saban.aw1kpanel.ui.LoginScreen
import com.saban.aw1kpanel.ui.MainScreen
import com.saban.aw1kpanel.ui.OfflineScreen
import com.saban.aw1kpanel.ui.PanelViewModel
import com.saban.aw1kpanel.ui.StatsConsentDialog
import com.saban.aw1kpanel.watch.VpnWatchService

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: PanelViewModel = viewModel()
            val state by vm.state.collectAsStateWithLifecycle()
            val settings by vm.settings.collectAsStateWithLifecycle()

            val sysDark = isSystemInDarkTheme()
            val picked = remember(settings.themeId, settings.customThemes) {
                (BUILTIN_THEMES + settings.customThemes).firstOrNull { it.id == settings.themeId }
            }
            val dark = picked?.dark ?: when (settings.themeMode) {
                1 -> false
                2 -> true
                else -> sysDark
            }

            // Durum çubuğu simgelerini uygulamanın temasına uydur.
            DisposableEffect(dark) {
                val bar = if (dark) {
                    SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                } else {
                    SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = bar, navigationBarStyle = bar)
                onDispose {}
            }

            // VPN ya da SMS uyarısı açıksa izleme servisini çalışır tut, ikisi de kapalıysa durdur.
            LaunchedEffect(settings.vpnAlert, settings.smsAlert, state.loggedIn) {
                val intent = Intent(this@MainActivity, VpnWatchService::class.java)
                val wanted = settings.vpnAlert || settings.smsAlert
                if (wanted && state.loggedIn) {
                    startForegroundService(intent)
                } else if (!wanted) {
                    stopService(intent)
                }
            }

            // Açılışta uygulamanın yeni sürümü var mı diye bir kez bak (sessiz)
            LaunchedEffect(Unit) { vm.checkAppUpdate(false) }

            // İzin verilmişse günde bir kez anonim sayaca haber ver
            LaunchedEffect(settings.statsConsent) {
                if (settings.statsConsent == 1) vm.pingStatsIfDue()
            }

            AW1KTheme(darkTheme = dark, theme = picked) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    when {
                        state.checkingSaved -> Box(
                            Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                        !state.loggedIn && state.offline -> OfflineScreen(
                            state = state,
                            onRetry = vm::retryConnect,
                            onLoginForm = vm::showLoginForm,
                        )
                        !state.loggedIn -> LoginScreen(
                            state = state,
                            initialHost = vm.lastHost,
                            initialUser = vm.lastUser,
                            onLogin = vm::login,
                            onPick = vm::connectRouter,
                        )
                        else -> {
                            MainScreen(state, settings, vm)
                            if (settings.statsConsent == -1) StatsConsentDialog(vm)
                        }
                    }
                    AppUpdateDialog(state.appUpdate, vm)
                    WhatsNewDialog(state.whatsNew, vm)
                }
            }
        }
    }
}
