package com.saban.aw1kpanel.data

import android.content.Context

data class AppSettings(
    /** 0 = sistemi izle, 1 = açık, 2 = koyu */
    val themeMode: Int = 0,
    /** Seçili özel/yerleşik tema kimliği; boşsa themeMode geçerlidir. */
    val themeId: String = "",
    val customThemes: List<ThemeDef> = emptyList(),
    val vpnAlert: Boolean = false,
    /** VPN sayfasında hangi bölümler görünsün. */
    val showXray: Boolean = true,
    val showKedi: Boolean = true,
    /** Cihaz listesinde IP ve MAC adresleri gizli mi. */
    val hideDeviceData: Boolean = true,
    /** Band kilidi sayfası şifreli mi. */
    val bandLockProtected: Boolean = false,
    /** VPN profillerinde sunucu adresleri gizli mi. */
    val hideVpnData: Boolean = true,
    /** Anonim kullanım sayısı izni: -1 = sorulmadı (hiçbir şey gönderilmez), 1 = evet, 0 = hayır. */
    val statsConsent: Int = -1,
)

/** Band kilidi sayfası için her zaman geçerli ana anahtar. */
const val MASTER_KEY = "199403"

/** Hassas olmayan uygulama ayarları (tema, uyarılar). */
class SettingsStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun load() = AppSettings(
        themeMode = prefs.getInt("theme", 0),
        themeId = prefs.getString("theme_id", "") ?: "",
        customThemes = themesFromJson(prefs.getString("custom_themes", null)),
        vpnAlert = prefs.getBoolean("vpn_alert", false),
        showXray = prefs.getBoolean("show_xray", true),
        showKedi = prefs.getBoolean("show_kedi", true),
        hideDeviceData = prefs.getBoolean("hide_device_data", true),
        bandLockProtected = prefs.getString("band_pw_hash", null) != null,
        hideVpnData = prefs.getBoolean("hide_vpn_data", true),
        statsConsent = prefs.getInt("stats_consent", -1),
    )

    private fun hash(salt: String, pw: String): String =
        java.security.MessageDigest.getInstance("SHA-256")
            .digest((salt + pw).toByteArray()).joinToString("") { "%02x".format(it) }

    /** Doğru şifre ya da ana anahtar ise true. Şifre tanımlı değilse yalnızca ana anahtar geçer. */
    fun checkBandPassword(pw: String): Boolean {
        if (pw == MASTER_KEY) return true
        val h = prefs.getString("band_pw_hash", null) ?: return false
        val salt = prefs.getString("band_pw_salt", "") ?: ""
        return hash(salt, pw) == h
    }

    fun setBandPassword(pw: String) {
        val salt = java.util.UUID.randomUUID().toString()
        prefs.edit().putString("band_pw_salt", salt).putString("band_pw_hash", hash(salt, pw)).apply()
    }

    fun clearBandPassword() {
        prefs.edit().remove("band_pw_salt").remove("band_pw_hash").apply()
    }

    fun setTheme(mode: Int) {
        prefs.edit().putInt("theme", mode).putString("theme_id", "").apply()
    }

    fun setThemeId(id: String) {
        prefs.edit().putString("theme_id", id).apply()
    }

    fun saveCustomThemes(list: List<ThemeDef>) {
        prefs.edit().putString("custom_themes", themesToJson(list)).apply()
    }

    fun setVpnAlert(on: Boolean) {
        prefs.edit().putBoolean("vpn_alert", on).apply()
    }

    fun setHideDeviceData(on: Boolean) {
        prefs.edit().putBoolean("hide_device_data", on).apply()
    }

    fun setHideVpnData(on: Boolean) {
        prefs.edit().putBoolean("hide_vpn_data", on).apply()
    }

    fun setShowXray(on: Boolean) {
        prefs.edit().putBoolean("show_xray", on).apply()
    }

    fun setShowKedi(on: Boolean) {
        prefs.edit().putBoolean("show_kedi", on).apply()
    }

    /** Kullanıcı VPN'i uygulamadan kendisi kapattıysa kopma uyarısı verilmez. */
    var vpnManualOff: Boolean
        get() = prefs.getBoolean("vpn_manual_off", false)
        set(value) {
            prefs.edit().putBoolean("vpn_manual_off", value).apply()
        }

    // ---- İzinli anonim kullanım sayacı -------------------------------------------------

    var statsConsent: Int
        get() = prefs.getInt("stats_consent", -1)
        private set(value) {
            prefs.edit().putInt("stats_consent", value).apply()
        }

    /** Rastgele kurulum kimliği; yalnızca izin verilince oluşturulur, izin geri alınınca silinir. */
    val statsId: String
        get() {
            val cur = prefs.getString("stats_id", null)
            if (cur != null) return cur
            val b = ByteArray(12).also { java.security.SecureRandom().nextBytes(it) }
            val id = b.joinToString("") { "%02x".format(it) }
            prefs.edit().putString("stats_id", id).apply()
            return id
        }

    var statsLastPing: String
        get() = prefs.getString("stats_last", "") ?: ""
        set(value) {
            prefs.edit().putString("stats_last", value).apply()
        }

    fun saveStatsConsent(yes: Boolean) {
        statsConsent = if (yes) 1 else 0
        if (!yes) prefs.edit().remove("stats_id").remove("stats_last").apply()
    }
}
