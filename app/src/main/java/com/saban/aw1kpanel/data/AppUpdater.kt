package com.saban.aw1kpanel.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/** GitHub Release'teki uygulama sürümü ([sha256] biliniyorsa indirme sonrası doğrulanır). */
data class AppRelease(
    val version: String,
    val notes: String,
    val apkName: String,
    val apkUrl: String,
    val size: Long,
    val sha256: String?,
    /** APK'nın GitHub'a yüklenme zamanı (ms); sürüm numarası aynıyken yeni derlemeyi anlamak için. */
    val publishedMs: Long = 0L,
)

/** Uygulamanın kendi güncellemesi: GitHub Releases'ten en son APK'yı bulur, indirir, kurulum ekranını açar. */
object AppUpdater {
    const val REPO = "sabanvurgun03-ui/aw1kpanel"

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private fun parts(v: String): List<Int> =
        Regex("\\d+").findAll(v).map { it.value.toIntOrNull() ?: 0 }.toList()

    /** Sürüm numaralarını sayı sayı karşılaştırır (v0.34 ile 0.34 aynıdır). */
    fun isNewer(remote: String, local: String): Boolean {
        val a = parts(remote)
        val b = parts(local)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private fun parseTime(s: String): Long =
        runCatching { java.time.Instant.parse(s).toEpochMilli() }.getOrDefault(0L)

    /** Bu APK'nın telefona kurulma/güncellenme zamanı (ms). */
    fun installedMs(ctx: Context): Long =
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).lastUpdateTime }.getOrDefault(0L)

    /**
     * Güncelleme var mı: sürüm numarası büyükse, ya da numara aynı olsa bile GitHub'daki APK
     * kurulu uygulamadan sonra yüklenmişse (sürüm artırmadan yeni derleme yayınlama).
     */
    fun hasUpdate(ctx: Context, r: AppRelease, local: String): Boolean {
        if (isNewer(r.version, local)) return true
        if (isNewer(local, r.version)) return false
        return r.publishedMs > 0 && r.publishedMs > installedMs(ctx) + 2 * 60_000L
    }

    private fun objects(a: JSONArray?): List<JSONObject> {
        if (a == null) return emptyList()
        return (0 until a.length()).mapNotNull { a.optJSONObject(it) }
    }

    private fun get(url: String, accept: String? = null): okhttp3.Response {
        val b = Request.Builder().url(url).header("User-Agent", "AW1KPanel")
        if (accept != null) b.header("Accept", accept)
        return http.newCall(b.build()).execute()
    }

    private val httpNoRedirect = http.newBuilder().followRedirects(false).build()

    /** API limiti dolunca: github.com/.../releases/latest yönlendirmesinden etiketi alır (API limiti yok). */
    private fun latestNoApi(): AppRelease? {
        val req = Request.Builder().url("https://github.com/$REPO/releases/latest").header("User-Agent", "AW1KPanel").build()
        val tag = httpNoRedirect.newCall(req).execute().use { r ->
            r.header("Location")?.substringAfter("/releases/tag/", "")?.substringBefore('?')?.takeIf { it.isNotBlank() }
        } ?: return null
        val base = "https://github.com/$REPO/releases/download/$tag"
        val sha = runCatching {
            get("$base/AW1KPanel.apk.sha256").use { sr ->
                Regex("[0-9a-fA-F]{64}").find(sr.body?.string().orEmpty())?.value
            }
        }.getOrNull()
        return AppRelease(
            version = tag, notes = "", apkName = "AW1KPanel.apk",
            apkUrl = "$base/AW1KPanel.apk", size = 0L, sha256 = sha,
        )
    }

    /** En son yayın; yayın ya da APK yoksa null. Ağ hatasında IOException fırlatır. */
    suspend fun latest(): AppRelease? = withContext(Dispatchers.IO) {
        get("https://api.github.com/repos/$REPO/releases/latest", "application/vnd.github+json").use { r ->
            if (r.code == 404) return@withContext null
            // API'nin IP başına saatlik limiti (60) dolunca 403/429 döner; API'siz yola geç.
            if (r.code == 403 || r.code == 429) return@withContext latestNoApi()
            if (!r.isSuccessful) throw IOException("GitHub yanıtı ${r.code}")
            val o = JSONObject(r.body?.string().orEmpty())
            val assets = objects(o.optJSONArray("assets"))
            val apk = assets.firstOrNull { it.optString("name").endsWith(".apk", ignoreCase = true) }
                ?: return@withContext null
            var sha = apk.optString("digest").removePrefix("sha256:").takeIf { it.length == 64 }
            if (sha == null) {
                val shaAsset = assets.firstOrNull {
                    val n = it.optString("name").lowercase()
                    n.endsWith(".sha256") || n.contains("sha256")
                }
                if (shaAsset != null) {
                    sha = runCatching {
                        get(shaAsset.optString("browser_download_url")).use { sr ->
                            Regex("[0-9a-fA-F]{64}").find(sr.body?.string().orEmpty())?.value
                        }
                    }.getOrNull()
                }
            }
            AppRelease(
                version = o.optString("tag_name").ifEmpty { o.optString("name") },
                notes = o.optString("body"),
                apkName = apk.optString("name"),
                apkUrl = apk.optString("browser_download_url"),
                size = apk.optLong("size"),
                sha256 = sha,
                publishedMs = parseTime(apk.optString("updated_at").ifEmpty { o.optString("published_at") }),
            )
        }
    }

    /** APK'yı önbelleğe indirir (SHA-256 biliniyorsa doğrular, uyuşmazsa siler). */
    suspend fun download(ctx: Context, rel: AppRelease, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(ctx.cacheDir, "updates").apply {
            mkdirs()
            listFiles()?.forEach { it.delete() }
        }
        val out = File(dir, "aw1kpanel-${rel.version}.apk")
        get(rel.apkUrl).use { r ->
            if (!r.isSuccessful) throw IOException("İndirme hatası ${r.code}")
            val body = r.body ?: throw IOException("Boş yanıt")
            val total = body.contentLength().takeIf { it > 0 } ?: rel.size
            val md = MessageDigest.getInstance("SHA-256")
            body.byteStream().use { inp ->
                out.outputStream().use { os ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    var last = 0f
                    var n = inp.read(buf)
                    while (n >= 0) {
                        os.write(buf, 0, n)
                        md.update(buf, 0, n)
                        done += n
                        if (total > 0) {
                            val p = (done.toFloat() / total).coerceAtMost(1f)
                            if (p - last >= 0.02f) {
                                last = p
                                onProgress(p)
                            }
                        }
                        n = inp.read(buf)
                    }
                }
            }
            val got = md.digest().joinToString("") { "%02x".format(it) }
            if (rel.sha256 != null && !got.equals(rel.sha256, ignoreCase = true)) {
                out.delete()
                throw IOException("SHA-256 uyuşmadı, dosya silindi")
            }
        }
        out
    }

    fun canInstall(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 26 || ctx.packageManager.canRequestPackageInstalls()

    /** "Bilinmeyen uygulamaları yükle" izin ekranını bu uygulama için açar. */
    fun openInstallSettings(ctx: Context) {
        val i = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
    }

    /** Android'in paket yükleyici ekranını açar; kurma onayı kullanıcıdadır. */
    fun install(ctx: Context, apk: File) {
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", apk)
        val i = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
    }
}
