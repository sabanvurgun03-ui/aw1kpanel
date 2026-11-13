package com.saban.aw1kpanel.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Modemin (aw1k-speedtest) bildirdiği anlık durum. phase: idle | starting | ping | down | up | done | error */
data class SpeedStatus(
    val phase: String = "idle",
    val live: Float = 0f,
    val ping: Float? = null,
    val jitter: Float? = null,
    val down: Float? = null,
    val up: Float? = null,
    val isp: String = "",
    val server: String = "",
    val ip: String = "",
    val error: String = "",
) {
    val running get() = phase == "starting" || phase == "ping" || phase == "down" || phase == "up"

    companion object {
        fun from(o: JSONObject): SpeedStatus {
            fun f(k: String): Float? = if (o.isNull(k) || !o.has(k)) null else o.optDouble(k).toFloat()
            return SpeedStatus(
                phase = o.optString("phase", "idle"),
                live = o.optDouble("live", 0.0).toFloat(),
                ping = f("ping"), jitter = f("jitter"), down = f("down"), up = f("up"),
                isp = o.optString("isp"), server = o.optString("server"), ip = o.optString("ip"),
                error = o.optString("error"),
            )
        }
    }
}

/** Kaydedilen tek bir hız testi sonucu. */
data class SpeedResult(
    val ts: Long,
    val down: Float,
    val up: Float,
    val ping: Float,
    val jitter: Float,
    val isp: String,
    val server: String,
    val ip: String,
)

/** Hız testi geçmişi telefonda tutulur (en fazla 200 kayıt). */
class SpeedStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("speedtests", Context.MODE_PRIVATE)

    fun load(): List<SpeedResult> = try {
        val a = JSONArray(prefs.getString("list", "[]"))
        (0 until a.length()).map {
            val o = a.getJSONObject(it)
            SpeedResult(
                o.getLong("ts"), o.optDouble("down").toFloat(), o.optDouble("up").toFloat(),
                o.optDouble("ping").toFloat(), o.optDouble("jitter").toFloat(),
                o.optString("isp"), o.optString("server"), o.optString("ip"),
            )
        }.sortedByDescending { it.ts }
    } catch (e: Exception) {
        emptyList()
    }

    fun save(list: List<SpeedResult>) {
        val a = JSONArray()
        list.take(200).forEach {
            a.put(
                JSONObject().put("ts", it.ts).put("down", it.down.toDouble()).put("up", it.up.toDouble())
                    .put("ping", it.ping.toDouble()).put("jitter", it.jitter.toDouble())
                    .put("isp", it.isp).put("server", it.server).put("ip", it.ip),
            )
        }
        prefs.edit().putString("list", a.toString()).apply()
    }
}
