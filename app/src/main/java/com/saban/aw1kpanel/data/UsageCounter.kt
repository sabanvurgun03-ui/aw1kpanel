package com.saban.aw1kpanel.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * İzinli anonim kullanım sayacı. Kullanıcı "Evet" demediyse HİÇBİR ŞEY gönderilmez.
 * Gönderilen (günde en çok bir kez): rastgele kurulum kimliği, uygulama sürümü, uygulama adı ("aw1k").
 * Gönderilmeyen: modem adresi/şifresi, VPN sunucuları, cihazlar, trafik, ad, IP kaydı.
 * Sunucu kodu: github.com/sabanvurgun03-ui/kediray → server/stats-worker
 */
object UsageCounter {
    private const val URL = "https://kediray-stats.kediray.workers.dev/ping"

    private val http = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS)
        .build()

    /** İzin verilmişse ve bugün henüz gönderilmediyse gönderir. Hata olursa sessizce vazgeçer (yarın tekrar dener). */
    suspend fun pingIfDue(store: SettingsStore, version: String) = withContext(Dispatchers.IO) {
        if (store.statsConsent != 1) return@withContext
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        if (store.statsLastPing == today) return@withContext
        val body = """{"app":"aw1k","id":"${store.statsId}","v":"$version"}"""
            .toRequestBody("application/json".toMediaType())
        try {
            http.newCall(Request.Builder().url(URL).post(body).build()).execute().use { r ->
                if (r.isSuccessful) store.statsLastPing = today
            }
        } catch (_: Exception) {
        }
    }
}
