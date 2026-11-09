package com.saban.aw1kpanel.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Modem bilgilerini (adres, kullanıcı, şifre) Android Keystore ile şifrelenmiş olarak saklar. */
class CredentialStore(context: Context) {

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "secure_prefs",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    init {
        // Eski tek modemli sürümden geçiş
        if (!prefs.contains("routers")) {
            val host = prefs.getString("host", null)
            val user = prefs.getString("user", null)
            val pass = prefs.getString("pass", null)
            if (host != null && user != null) {
                val r = Router(newId(), "Modem", host, user, pass ?: "")
                write(listOf(r))
                if (pass != null) setActive(r.id)
            }
        }
    }

    /** Band kilidi şifresi (şifrelenmiş saklanır); "şifremi unuttum" ile modeme yazılabilsin diye. */
    var bandPasswordPlain: String?
        get() = prefs.getString("band_pw_plain", null)
        set(v) {
            if (v == null) prefs.edit().remove("band_pw_plain").apply() else prefs.edit().putString("band_pw_plain", v).apply()
        }

    fun newId(): String = UUID.randomUUID().toString().take(8)

    fun routers(): List<Router> {
        val raw = prefs.getString("routers", null) ?: return emptyList()
        return try {
            val a = JSONArray(raw)
            (0 until a.length()).map {
                val o = a.getJSONObject(it)
                Router(
                    id = o.getString("id"),
                    name = o.optString("name"),
                    host = o.getString("host"),
                    user = o.getString("user"),
                    password = o.optString("pass"),
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun write(list: List<Router>) {
        val a = JSONArray()
        list.forEach {
            a.put(
                JSONObject()
                    .put("id", it.id)
                    .put("name", it.name)
                    .put("host", it.host)
                    .put("user", it.user)
                    .put("pass", it.password),
            )
        }
        prefs.edit().putString("routers", a.toString()).apply()
    }

    fun upsert(r: Router) {
        val list = routers().toMutableList()
        val i = list.indexOfFirst { it.id == r.id }
        if (i >= 0) list[i] = r else list += r
        write(list)
    }

    fun delete(id: String) {
        write(routers().filterNot { it.id == id })
    }

    fun activeId(): String? = prefs.getString("active_router", null)

    fun setActive(id: String?) {
        prefs.edit().putString("active_router", id).apply()
    }

    fun autoLogin(): Boolean = prefs.getBoolean("auto_login", true)

    fun setAutoLogin(on: Boolean) {
        prefs.edit().putBoolean("auto_login", on).apply()
    }

    /** Aktif modemin giriş bilgisi; çıkış yapıldıysa ya da şifre yoksa null. */
    fun load(): Connection? {
        if (!autoLogin()) return null
        val r = routers().firstOrNull { it.id == activeId() } ?: return null
        if (r.password.isEmpty()) return null
        return Connection(r.host, r.user, r.password)
    }

    private fun lastUsed(): Router? =
        routers().firstOrNull { it.id == activeId() } ?: routers().firstOrNull()

    fun lastHost(): String = lastUsed()?.host ?: "192.168.1.1"

    fun lastUser(): String = lastUsed()?.user ?: "root"

    /** VPN bağlıyken görülen son profil kimliği (modem başına). */
    fun lastVpnProfile(): String? = prefs.getString("vpn_profile_${activeId().orEmpty()}", null)

    fun saveVpnProfile(id: String) {
        prefs.edit().putString("vpn_profile_${activeId().orEmpty()}", id).apply()
    }

    /** Son çalışırken görülen VPN: "kediray" ya da "xwrt" (modem başına). */
    fun lastBackend(): String? = prefs.getString("vpn_backend_${activeId().orEmpty()}", null)

    fun saveBackend(b: String) {
        prefs.edit().putString("vpn_backend_${activeId().orEmpty()}", b).apply()
    }
}
