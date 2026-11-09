package com.saban.aw1kpanel.data

import android.util.Base64
import org.json.JSONObject
import java.net.URLDecoder

/** vless:// vmess:// trojan:// ss:// bağlantılarını xwrt profil JSON'una çevirir. Tanınmazsa null. */
object LinkParser {

    fun parse(link: String): JSONObject? {
        val t = link.trim()
        return try {
            when {
                t.startsWith("vless://", true) -> standard(t, "vless")
                t.startsWith("trojan://", true) -> standard(t, "trojan")
                t.startsWith("vmess://", true) -> vmess(t)
                t.startsWith("ss://", true) -> shadowsocks(t)
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun dec(s: String): String = URLDecoder.decode(s, "UTF-8")

    private fun b64(s: String): String {
        val clean = s.trim().replace('-', '+').replace('_', '/')
        return String(Base64.decode(clean, Base64.DEFAULT or Base64.NO_WRAP), Charsets.UTF_8)
    }

    private fun query(q: String?): Map<String, String> {
        if (q.isNullOrEmpty()) return emptyMap()
        return q.split('&').mapNotNull {
            val i = it.indexOf('=')
            if (i <= 0) null else it.substring(0, i) to dec(it.substring(i + 1))
        }.toMap()
    }

    /** vless://uuid@host:port?params#name  ve  trojan://password@host:port?params#name */
    private fun standard(link: String, proto: String): JSONObject? {
        val body = link.substringAfter("://")
        val name = dec(body.substringAfter('#', ""))
        val noFrag = body.substringBefore('#')
        val main = noFrag.substringBefore('?')
        val q = query(noFrag.substringAfter('?', ""))
        val cred = main.substringBeforeLast('@', "")
        val hostPort = main.substringAfterLast('@')
        if (cred.isEmpty() || hostPort.isEmpty()) return null
        val host: String
        val port: Int
        if (hostPort.startsWith("[")) {
            host = hostPort.substringBefore(']').removePrefix("[")
            port = hostPort.substringAfter("]:", "443").toIntOrNull() ?: 443
        } else {
            host = hostPort.substringBeforeLast(':')
            port = hostPort.substringAfterLast(':', "443").toIntOrNull() ?: 443
        }
        val o = JSONObject()
            .put("name", name.ifEmpty { host })
            .put("proto", proto)
            .put("address", host)
            .put("port", port)
        if (proto == "vless") {
            o.put("uuid", dec(cred))
            o.put("encryption", q["encryption"] ?: "none")
            if (q["flow"] != null) o.put("flow", q["flow"])
        } else {
            o.put("password", dec(cred))
        }
        o.put("network", q["type"] ?: "tcp")
        o.put("security", q["security"] ?: if (proto == "trojan") "tls" else "none")
        o.put("sni", q["sni"] ?: q["peer"] ?: q["serverName"] ?: q["host"] ?: "")
        o.put("alpn", q["alpn"] ?: "")
        o.put("fingerprint", q["fp"] ?: "")
        o.put("path", q["path"] ?: q["serviceName"] ?: "")
        o.put("header_type", q["headerType"] ?: "none")
        if (q["host"] != null) o.put("host", q["host"])
        if (q["ech"] != null) o.put("ech", q["ech"])
        if (q["allowInsecure"] == "1" || q["insecure"] == "1") o.put("insecure", "1")
        if (q["pbk"] != null) o.put("public_key", q["pbk"])
        if (q["sid"] != null) o.put("short_id", q["sid"])
        if (q["spx"] != null) o.put("spider_x", q["spx"])
        return o
    }

    /** vmess://base64(json) */
    private fun vmess(link: String): JSONObject? {
        val j = JSONObject(b64(link.substringAfter("://").substringBefore('#')))
        val host = j.optString("add")
        if (host.isEmpty()) return null
        return JSONObject()
            .put("name", j.optString("ps").ifEmpty { host })
            .put("proto", "vmess")
            .put("address", host)
            .put("port", j.optString("port").toIntOrNull() ?: 443)
            .put("uuid", j.optString("id"))
            .put("alter_id", j.optString("aid").toIntOrNull() ?: 0)
            .put("encryption", j.optString("scy").ifEmpty { "auto" })
            .put("network", j.optString("net").ifEmpty { "tcp" })
            .put("security", j.optString("tls").ifEmpty { "none" })
            .put("sni", j.optString("sni").ifEmpty { j.optString("host") })
            .put("alpn", j.optString("alpn"))
            .put("fingerprint", j.optString("fp"))
            .put("path", j.optString("path"))
            .put("host", j.optString("host"))
            .put("header_type", j.optString("type").ifEmpty { "none" })
    }

    /** ss://base64(method:pass)@host:port#name  veya  ss://base64(method:pass@host:port)#name */
    private fun shadowsocks(link: String): JSONObject? {
        val body = link.substringAfter("://")
        val name = dec(body.substringAfter('#', ""))
        val main = body.substringBefore('#').substringBefore('?')
        val method: String
        val pass: String
        val hostPort: String
        if ('@' in main) {
            val cred = main.substringBeforeLast('@')
            val decoded = if (':' in cred) dec(cred) else b64(cred)
            method = decoded.substringBefore(':')
            pass = decoded.substringAfter(':')
            hostPort = main.substringAfterLast('@')
        } else {
            val decoded = b64(main)
            method = decoded.substringBefore(':')
            pass = decoded.substringAfter(':').substringBeforeLast('@')
            hostPort = decoded.substringAfterLast('@')
        }
        val host = hostPort.substringBeforeLast(':')
        val port = hostPort.substringAfterLast(':').toIntOrNull() ?: return null
        return JSONObject()
            .put("name", name.ifEmpty { host })
            .put("proto", "shadowsocks")
            .put("address", host)
            .put("port", port)
            .put("method", method)
            .put("password", pass)
            .put("network", "tcp")
            .put("security", "none")
    }

    /** xwrt alan adlarını KediRay'in uci düğüm alanlarına çevirir. */
    fun toKedi(x: JSONObject): JSONObject {
        val o = JSONObject()
        fun copy(from: String, to: String) {
            val v = x.opt(from)
            if (v != null && v.toString().isNotEmpty()) o.put(to, v.toString())
        }
        copy("proto", "protocol"); copy("address", "address"); copy("port", "port")
        copy("uuid", "uuid"); copy("password", "password"); copy("method", "method")
        copy("encryption", "encryption"); copy("network", "network"); copy("path", "path")
        copy("security", "tls"); copy("sni", "sni"); copy("fingerprint", "fp")
        copy("name", "remarks"); copy("host", "host"); copy("public_key", "pbk"); copy("short_id", "sid")
        copy("alpn", "alpn"); copy("ech", "ech"); copy("flow", "flow"); copy("insecure", "insecure")
        o.put("enabled", "1")
        return o
    }
}
