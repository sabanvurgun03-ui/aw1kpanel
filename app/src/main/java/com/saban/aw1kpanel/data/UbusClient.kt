package com.saban.aw1kpanel.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class UbusException(message: String, val code: Int? = null) : Exception(message)

/**
 * OpenWrt ubus'a HTTP JSON-RPC (http://<host>/ubus) ile bağlanan küçük istemci.
 * Oturum süresi dolarsa (ubus kodu 6) kayıtlı bilgilerle kendiliğinden yeniden giriş yapar.
 */
class UbusClient {
    private val http = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    // İlk girişte (modem yakında değilse) uzun beklememek için kısa süreli istemci.
    private val quick = http.newBuilder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .callTimeout(8, TimeUnit.SECONDS)
        .build()
    // Modemin GitHub'dan dosya indirdiği çağrılar (güncelleme) 30 sn'den uzun sürebilir.
    private val slow = http.newBuilder().readTimeout(180, TimeUnit.SECONDS).build()
    private val loginLock = Mutex()
    // uhttpd aynı anda az sayıda isteğe cevap verir; en fazla 2 çağrıyı birlikte gönder.
    private val slots = Semaphore(2)
    private val ids = AtomicInteger(1)
    private val jsonType = "application/json".toMediaType()

    @Volatile private var session: String = NULL_SESSION
    @Volatile private var loginAt = 0L
    @Volatile private var conn: Connection? = null

    suspend fun login(c: Connection) {
        withContext(Dispatchers.IO) {
            val clean = c.copy(
                host = c.host.trim().removePrefix("http://").removePrefix("https://").trimEnd('/'),
            )
            doLogin(clean, quick)
            conn = clean
        }
    }

    fun logout() {
        session = NULL_SESSION
        conn = null
    }

    suspend fun call(
        obj: String,
        method: String,
        params: JSONObject = JSONObject(),
        longWait: Boolean = false,
    ): JSONObject = withContext(Dispatchers.IO) {
        val cl = if (longWait) slow else http
        val c = conn ?: throw UbusException("Giriş yapılmadı")
        slots.withPermit {
            val used = session
            var result = try {
                rawCall(c.host, used, obj, method, params, cl)
            } catch (e: UbusException) {
                // JSON-RPC "Access denied" (-32002): oturum geçersiz olabilir (modemde rpcd yeniden başladı, KediRay güncellendi
                // ya da süre doldu). Son girişten 20 sn geçtiyse yeniden giriş yapıp bir kez daha dene; gerçekten yetki
                // eksikse (giriş yeni) hatayı aynen ilet.
                if (e.code != -32002) throw e
                loginLock.withLock {
                    if (session == used && System.currentTimeMillis() - loginAt > 20_000L) doLogin(c)
                }
                if (session == used) throw e
                rawCall(c.host, session, obj, method, params, cl)
            }
            if (result.first == 6) {
                // Oturum süresi dolmuş olabilir; bir kez yeniden giriş yap.
                loginLock.withLock {
                    if (session == used) doLogin(c)
                }
                result = rawCall(c.host, session, obj, method, params, cl)
            }
            if (result.first != 0) {
                throw UbusException("$obj.$method çağrısı reddedildi (kod ${result.first})", result.first)
            }
            result.second ?: JSONObject()
        }
    }

    private fun doLogin(c: Connection, cl: OkHttpClient = http) {
        val params = JSONObject()
            .put("username", c.user)
            .put("password", c.password)
            .put("timeout", 600)
        val (code, data) = rawCall(c.host, NULL_SESSION, "session", "login", params, cl)
        if (code != 0 || data == null) {
            throw UbusException("Kullanıcı adı veya şifre hatalı", code)
        }
        session = data.getString("ubus_rpc_session")
        loginAt = System.currentTimeMillis()
    }

    private fun rawCall(
        host: String,
        sid: String,
        obj: String,
        method: String,
        params: JSONObject,
        cl: OkHttpClient = http,
    ): Pair<Int, JSONObject?> {
        val body = JSONObject()
            .put("jsonrpc", "2.0")
            .put("id", ids.getAndIncrement())
            .put("method", "call")
            .put("params", JSONArray().put(sid).put(obj).put(method).put(params))
        val request = Request.Builder()
            .url("http://$host/ubus")
            .post(body.toString().toRequestBody(jsonType))
            .build()
        return cl.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw UbusException("Modem HTTP ${resp.code} döndürdü")
            val json = JSONObject(resp.body?.string() ?: throw UbusException("Boş yanıt"))
            json.optJSONObject("error")?.let {
                throw UbusException(it.optString("message", "JSON-RPC hatası"), it.optInt("code"))
            }
            val arr = json.getJSONArray("result")
            arr.getInt(0) to arr.optJSONObject(1)
        }
    }

    private companion object {
        const val NULL_SESSION = "00000000000000000000000000000000"
    }
}
