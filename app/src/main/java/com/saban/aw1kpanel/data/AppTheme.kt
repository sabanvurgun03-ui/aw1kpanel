package com.saban.aw1kpanel.data

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject

/**
 * Uygulama teması. Renkler ARGB Int olarak tutulur.
 * Kartlar, kenarlıklar ve yazı renkleri bu yedi renkten türetilir.
 */
data class ThemeDef(
    val id: String,
    val name: String,
    /** Zemin */
    val bg: Int,
    /** Vurgu */
    val accent: Int,
    /** Bağlantı paneli (üstteki büyük kart) */
    val panel: Int,
    /** Panel gradyan bitişi; null = tek renkli panel */
    val panelEnd: Int?,
    /** Bağlı */
    val good: Int,
    /** Uyarı */
    val warn: Int,
    /** Hata */
    val bad: Int,
) {
    /** Zemin koyuysa tema koyudur. */
    val dark: Boolean get() = luminance(bg) < 0.5f
}

fun luminance(argb: Int): Float {
    fun ch(v: Int): Float {
        val c = v / 255f
        return if (c <= 0.03928f) c / 12.92f else Math.pow(((c + 0.055f) / 1.055f).toDouble(), 2.4).toFloat()
    }
    val r = ch((argb shr 16) and 0xFF)
    val g = ch((argb shr 8) and 0xFF)
    val b = ch(argb and 0xFF)
    return 0.2126f * r + 0.7152f * g + 0.0722f * b
}

private fun c(rgb: Long): Int = (0xFF000000L or rgb).toInt()

val BUILTIN_THEMES: List<ThemeDef> = listOf(
    ThemeDef("aurora", "Aurora", c(0x0A1226), c(0x7AE0FF), c(0x0A3154), c(0x07506B), c(0x5BE7B4), c(0xFFC46B), c(0xFF7A93)),
    ThemeDef("ember", "Ember", c(0x1A0F0D), c(0xFF8A5B), c(0x5A1E10), c(0x8A3414), c(0x7EDC9A), c(0xFFC46B), c(0xFF7A8A)),
    ThemeDef("graphite", "Graphite", c(0x121417), c(0x9AA4B2), c(0x2A3038), c(0x3A424C), c(0x6FD3A0), c(0xF2C26B), c(0xF28B8B)),
    ThemeDef("orchid", "Orchid", c(0x150F24), c(0xC79BFF), c(0x4B2A7A), c(0x7A3FB5), c(0x6FE0B0), c(0xFFC46B), c(0xFF7AA8)),
    ThemeDef("paper", "Paper", c(0xF6F1E7), c(0x0E7490), c(0x0E6B80), c(0x14899F), c(0x1F8A4C), c(0xB7791F), c(0xC0392B)),
    ThemeDef("matcha", "Matcha", c(0xF2F5EC), c(0x2F7D4F), c(0x2F6B45), c(0x4C8F63), c(0x2F7D4F), c(0xA8741A), c(0xC0392B)),
)

fun hexOf(argb: Int): String = "#%06X".format(argb and 0xFFFFFF)

fun parseHex(text: String): Int? {
    val t = text.trim().removePrefix("#")
    if (t.length != 6 || !t.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return null
    return (0xFF000000L or t.toLong(16)).toInt()
}

fun ThemeDef.toJson(): JSONObject = JSONObject().apply {
    put("id", id); put("name", name)
    put("bg", hexOf(bg)); put("accent", hexOf(accent)); put("panel", hexOf(panel))
    if (panelEnd != null) put("panelEnd", hexOf(panelEnd))
    put("good", hexOf(good)); put("warn", hexOf(warn)); put("bad", hexOf(bad))
}

fun themeFromJson(o: JSONObject, id: String = o.optString("id")): ThemeDef? {
    fun col(k: String) = parseHex(o.optString(k))
    return ThemeDef(
        id = id,
        name = o.optString("name").ifBlank { "Tema" }.take(24),
        bg = col("bg") ?: return null,
        accent = col("accent") ?: return null,
        panel = col("panel") ?: return null,
        panelEnd = col("panelEnd"),
        good = col("good") ?: return null,
        warn = col("warn") ?: return null,
        bad = col("bad") ?: return null,
    )
}

fun themesToJson(list: List<ThemeDef>): String = JSONArray().apply { list.forEach { put(it.toJson()) } }.toString()

fun themesFromJson(text: String?): List<ThemeDef> {
    if (text.isNullOrBlank()) return emptyList()
    return try {
        val a = JSONArray(text)
        (0 until a.length()).mapNotNull { themeFromJson(a.getJSONObject(it)) }
    } catch (e: Exception) {
        emptyList()
    }
}

private const val LINK_PREFIX = "aw1ktema:"

/** Paylaşılabilir bağlantı: aw1ktema:<base64url(JSON)> */
fun themeToLink(t: ThemeDef): String {
    val o = t.toJson().apply { remove("id") }
    val b = Base64.encodeToString(o.toString().toByteArray(Charsets.UTF_8), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    return LINK_PREFIX + b
}

fun themeFromLink(text: String): ThemeDef? {
    val t = text.trim()
    val i = t.indexOf(LINK_PREFIX)
    if (i < 0) return null
    val payload = t.substring(i + LINK_PREFIX.length).takeWhile { !it.isWhitespace() }
    return try {
        val json = String(Base64.decode(payload, Base64.URL_SAFE or Base64.NO_WRAP), Charsets.UTF_8)
        themeFromJson(JSONObject(json), id = "c" + java.lang.Long.toString(System.nanoTime(), 36))
    } catch (e: Exception) {
        null
    }
}
