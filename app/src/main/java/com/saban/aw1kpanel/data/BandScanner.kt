package com.saban.aw1kpanel.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetAddress
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/** Akıllı band seçiminde bir bandın sonucu. [ok] yanlışsa bant denemesi başarısız oldu ([note] nedenini söyler). */
data class ScanRow(
    val id: String,
    val name: String,
    val used: String = "",
    val mbps: Float? = null,
    val sinr: Float? = null,
    val rsrp: Float? = null,
    val score: Float = 0f,
    val ok: Boolean = false,
    val note: String = "",
)

data class BandScanState(
    val running: Boolean = false,
    val finished: Boolean = false,
    val cls: String = "",
    val total: Int = 0,
    val done: Int = 0,
    val current: String = "",
    val phase: String = "",
    val rows: List<ScanRow> = emptyList(),
    val bestId: String? = null,
    val applied: Boolean = false,
    val note: String? = null,
)

/**
 * Seçili bantları sırayla kilitler, her birinde sinyal (RSRP/SINR) ve istenirse gerçek hız ölçer,
 * sonunda en iyisini uygular ya da eski kilidi geri yükler (iptal ve hata durumunda da).
 * Hız ölçümü telefonun kendisinden, modemin Wi-Fi ağı üzerinden yapılır.
 */
class BandScanner(private val ctx: Context, private val repo: PanelRepository) {

    private class Sample(val rsrp: Float, val sinr: Float?, val used: String)

    suspend fun run(
        cls: String,
        bands: List<Pair<String, String>>,
        original: Set<String>,
        allIds: List<String>,
        waitSec: Int,
        useSpeed: Boolean,
        autoApply: Boolean,
        onUpdate: (BandScanState) -> Unit,
    ) {
        var st = BandScanState(running = true, cls = cls, total = bands.size, phase = "Hazırlanıyor")
        onUpdate(st)
        // Eski kilit boşsa (kilitsiz) tüm bantları geri gönder: "hepsini seç" kilidi kaldırır.
        val restoreIds = if (original.isEmpty()) allIds else allIds.filter { it in original }
        val rows = mutableListOf<ScanRow>()
        var completed = false
        try {
            for ((i, b) in bands.withIndex()) {
                currentCoroutineContext().ensureActive()
                st = st.copy(done = i, current = b.second, phase = "Kilitleniyor: ${b.second}")
                onUpdate(st)
                val row = try {
                    repo.setLockBand(cls, listOf(b.first))
                    delay(2500)
                    val s = waitForSignal(waitSec)
                    if (s == null) {
                        ScanRow(b.first, b.second, note = "bağlanamadı")
                    } else {
                        var mbps: Float? = null
                        if (useSpeed) {
                            st = st.copy(phase = "Hız ölçülüyor: ${b.second}")
                            onUpdate(st)
                            mbps = speedTest()
                        }
                        val sinr = s.sinr ?: 0f
                        val ok = !useSpeed || (mbps ?: 0f) > 0f
                        val score = if (useSpeed) (mbps ?: 0f) * 10f + sinr else sinr * 10f + (s.rsrp + 140f)
                        ScanRow(
                            b.first, b.second, used = s.used, mbps = mbps, sinr = s.sinr, rsrp = s.rsrp,
                            score = score, ok = ok, note = if (ok) "" else "hız ölçülemedi",
                        )
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    ScanRow(b.first, b.second, note = "kilitlenemedi")
                }
                rows += row
                st = st.copy(done = i + 1, rows = sorted(rows))
                onUpdate(st)
            }
            completed = true
        } finally {
            withContext(NonCancellable) {
                val sortedRows = sorted(rows)
                val best = sortedRows.firstOrNull { it.ok }
                onUpdate(st.copy(phase = "Band kilidi ayarlanıyor", rows = sortedRows))
                var applied = false
                var note: String
                try {
                    if (completed && autoApply && best != null) {
                        repo.setLockBand(cls, listOf(best.id))
                        applied = true
                        note = "En iyi bant uygulandı: ${best.name}"
                    } else {
                        repo.setLockBand(cls, restoreIds)
                        note = when {
                            !completed -> "İptal edildi; eski band kilidi geri yüklendi."
                            best == null -> "Hiçbir bantta ölçüm alınamadı; eski band kilidi geri yüklendi."
                            else -> "Eski band kilidi geri yüklendi. En iyi: ${best.name}"
                        }
                    }
                } catch (e: Exception) {
                    note = "Band kilidi ayarlanamadı (${e.message}). Aşağıdaki kartlardan kilidi elle gönder."
                }
                onUpdate(
                    st.copy(
                        running = false, finished = true, rows = sortedRows, bestId = best?.id,
                        applied = applied, phase = "", note = note,
                        done = if (completed) bands.size else st.done,
                    ),
                )
            }
        }
    }

    private fun sorted(rows: List<ScanRow>) =
        rows.sortedByDescending { if (it.ok) it.score else -1e9f }

    private suspend fun sample(): Sample? {
        val s = try {
            repo.modemState()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return null
        }
        fun avg(prefix: String): Float? = s.signal
            .filter { it.label.uppercase().startsWith(prefix) }
            .mapNotNull { it.value.toFloatOrNull() }
            .takeIf { it.isNotEmpty() }?.average()?.toFloat()
        val rsrp = avg("RSRP") ?: return null
        val used = s.cells.map { it.band }.filter { it.isNotBlank() && it != "—" }.distinct().joinToString("+")
        return Sample(rsrp, avg("SINR") ?: avg("SNR"), used)
    }

    /** Modem yeniden bağlanıp sinyal değeri verene kadar bekler; [waitSec] içinde olmazsa null. */
    private suspend fun waitForSignal(waitSec: Int): Sample? {
        val end = System.currentTimeMillis() + waitSec * 1000L
        while (System.currentTimeMillis() < end) {
            val first = sample()
            if (first != null) {
                delay(2000)
                return sample() ?: first
            }
            delay(1500)
        }
        return null
    }

    // ---- Hız ölçümü -----------------------------------------------------

    @Suppress("DEPRECATION")
    private fun wifiNetwork(): Network? = try {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        cm.allNetworks.firstOrNull {
            cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        }
    } catch (e: Exception) {
        null
    }

    /** Wi-Fi'ye bağlı istemci: Wi-Fi'de internet yoksa Android mobil veriye kaçıp yanlış ölçmesin. */
    private fun client(): OkHttpClient {
        val b = OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
        wifiNetwork()?.let { n ->
            b.socketFactory(n.socketFactory).dns(object : Dns {
                override fun lookup(hostname: String): List<InetAddress> = n.getAllByName(hostname).toList()
            })
        }
        return b.build()
    }

    private suspend fun speedTest(durationMs: Long = 8000): Float? = withContext(Dispatchers.IO) {
        val cl = client()
        for (url in SPEED_URLS) {
            val r = download(cl, url, durationMs)
            if (r != null) return@withContext r
        }
        null
    }

    /** 4 paralel indirme; ilk 1 sn ısınma sayılmaz. Mbps döner, hiç veri gelmezse null. */
    private suspend fun download(cl: OkHttpClient, url: String, durationMs: Long): Float? = coroutineScope {
        val start = System.nanoTime()
        val warm = start + 1_000_000_000L
        val end = start + durationMs * 1_000_000L
        val bytes = AtomicLong(0)
        List(4) {
            async(Dispatchers.IO) {
                val buf = ByteArray(64 * 1024)
                try {
                    while (isActive && System.nanoTime() < end) {
                        val req = Request.Builder().url(url).header("Cache-Control", "no-cache").build()
                        cl.newCall(req).execute().use { resp ->
                            if (!resp.isSuccessful) return@async
                            val src = resp.body?.byteStream() ?: return@async
                            while (isActive && System.nanoTime() < end) {
                                val n = src.read(buf)
                                if (n < 0) break
                                if (System.nanoTime() >= warm) bytes.addAndGet(n.toLong())
                            }
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // bu bağlantı koptu; diğerleri devam eder
                }
            }
        }.awaitAll()
        val secs = (minOf(System.nanoTime(), end) - warm) / 1e9
        if (secs < 0.5 || bytes.get() == 0L) null else (bytes.get() * 8 / secs / 1e6).toFloat()
    }

    private companion object {
        val SPEED_URLS = listOf(
            "https://speed.cloudflare.com/__down?bytes=200000000",
            "http://speedtest.tele2.net/100MB.zip",
        )
    }
}
