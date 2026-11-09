package com.saban.aw1kpanel.watch

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.saban.aw1kpanel.MainActivity
import com.saban.aw1kpanel.data.Connection
import com.saban.aw1kpanel.data.CredentialStore
import com.saban.aw1kpanel.data.PanelRepository
import com.saban.aw1kpanel.data.SettingsStore
import com.saban.aw1kpanel.data.UbusClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * VPN bağlıyken kopmasını izleyen ön plan servisi.
 * Modemin xwrt durumunu 20 saniyede bir sorar; üst üste iki kez "bağlı değil" görürse uyarı verir.
 * Telefon modemin ağında değilse sessizce yeniden dener.
 */
class VpnWatchService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannels()
        try {
            ServiceCompat.startForeground(
                this,
                ONGOING_ID,
                ongoing("VPN izleniyor"),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } catch (e: Exception) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (job == null) job = scope.launch { watch() }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun watch() {
        val creds = CredentialStore(applicationContext)
        val settings = SettingsStore(applicationContext)
        val repo = PanelRepository(UbusClient())
        var loggedIn = false
        var wasConnected = false
        var downPolls = 0
        var alerted = false
        var current: Connection? = null

        while (currentCoroutineContext().isActive) {
            val conn = creds.load()
            if (conn == null || !settings.load().vpnAlert) {
                stopSelf()
                return
            }
            if (conn != current) {
                // Modem değişti ya da bilgiler güncellendi: yeniden giriş yap, sayaçları sıfırla.
                current = conn
                loggedIn = false
                wasConnected = false
                downPolls = 0
                alerted = false
            }
            var wait = 20_000L
            try {
                if (!loggedIn) {
                    repo.login(conn)
                    loggedIn = true
                }
                val vpn = repo.vpnState()
                if (vpn != null) {
                    if (vpn.connected) {
                        if (alerted) {
                            notifyRecovered()
                            alerted = false
                        }
                        wasConnected = true
                        downPolls = 0
                        settings.vpnManualOff = false
                    } else if (settings.vpnManualOff) {
                        wasConnected = false
                        downPolls = 0
                    } else if (wasConnected) {
                        downPolls++
                        if (downPolls >= 2 && !alerted) {
                            notifyDropped()
                            alerted = true
                        }
                    }
                    update(if (vpn.connected) "VPN bağlı · ${vpn.profileName}" else "VPN bağlı değil")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Modeme ulaşılamıyor (telefon başka ağda olabilir); daha seyrek dene.
                loggedIn = false
                wait = 60_000L
            }
            delay(wait)
        }
    }

    // ---- Bildirimler ----------------------------------------------------

    private fun createChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_WATCH, "VPN izleme", NotificationManager.IMPORTANCE_LOW),
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_ALERT, "VPN uyarıları", NotificationManager.IMPORTANCE_HIGH),
        )
    }

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun ongoing(text: String): Notification =
        Notification.Builder(this, CH_WATCH)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("AW1K Panel")
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(openApp())
            .build()

    private fun update(text: String) {
        getSystemService(NotificationManager::class.java).notify(ONGOING_ID, ongoing(text))
    }

    private fun notifyDropped() {
        val n = Notification.Builder(this, CH_ALERT)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("VPN bağlantısı koptu")
            .setContentText("Modem VPN'e bağlı değil. Uygulamayı açıp yeniden bağlanabilirsin.")
            .setAutoCancel(true)
            .setContentIntent(openApp())
            .build()
        getSystemService(NotificationManager::class.java).notify(ALERT_ID, n)
    }

    private fun notifyRecovered() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.cancel(ALERT_ID)
        val n = Notification.Builder(this, CH_WATCH)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("VPN yeniden bağlandı")
            .setAutoCancel(true)
            .setTimeoutAfter(15_000)
            .build()
        nm.notify(RECOVERED_ID, n)
    }

    private companion object {
        const val CH_WATCH = "vpn_watch"
        const val CH_ALERT = "vpn_alert"
        const val ONGOING_ID = 1
        const val ALERT_ID = 2
        const val RECOVERED_ID = 3
    }
}
