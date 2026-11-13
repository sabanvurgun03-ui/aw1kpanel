package com.saban.aw1kpanel.data

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

class PanelRepository(private val client: UbusClient) {

    private var modemSection: String? = null

    suspend fun login(c: Connection) {
        client.login(c)
        modemSection = null
        baseCache = emptyList()
        lastCpu = null
    }

    fun logout() {
        kediVer = null
        client.logout()
    }

    // ---- Modem (QModem) -------------------------------------------------

    /** qmodem yapılandırmasındaki "modem-device" bölümünü bulur (bu modemde "4_1"). */
    private suspend fun section(): String {
        modemSection?.let { return it }
        val found = try {
            val values = client.call("uci", "get", JSONObject().put("config", "qmodem"))
                .optJSONObject("values")
            values?.let { v ->
                v.keys().asSequence().firstOrNull {
                    v.optJSONObject(it)?.optString(".type") == "modem-device"
                }
            }
        } catch (e: UbusException) {
            null
        }
        return (found ?: "4_1").also { modemSection = it }
    }

    private suspend fun atPort(): String {
        atPortCache?.let { return it }
        val port = try {
            client.call("uci", "get", JSONObject().put("config", "qmodem"))
                .optJSONObject("values")?.optJSONObject(section())?.optString("at_port").orEmpty()
        } catch (e: UbusException) {
            ""
        }
        return port.ifEmpty { "/dev/ttyUSB3" }.also { atPortCache = it }
    }

    /** AT+QCAINFO satırları: her biri virgülle ayrılmış alan listesi (tırnaklar atılmış). */
    private suspend fun caInfo(): List<List<String>> {
        val now = System.currentTimeMillis()
        if (now - caAt < 10_000) return caCache
        caAt = now
        caCache = try {
            val r = client.call(
                "qmodem", "send_at",
                JSONObject().put("config_section", section())
                    .put("params", JSONObject().put("at", "AT+QCAINFO").put("port", atPort()).toString()),
            )
            val cfg = r.optJSONObject("at_cfg")
            val res = if (cfg?.optString("status") == "1") cfg.optString("res") else ""
            res.split('\n').map { it.trim() }.filter { it.startsWith("+QCAINFO:") }.map { line ->
                splitCsv(line.removePrefix("+QCAINFO:").trim())
            }.filter { it.size >= 4 }
        } catch (e: UbusException) {
            emptyList()
        }
        return caCache
    }

    private fun splitCsv(s: String): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        var q = false
        for (ch in s) {
            when {
                ch == '"' -> q = !q
                ch == ',' && !q -> { out += sb.toString().trim(); sb.setLength(0) }
                else -> sb.append(ch)
            }
        }
        out += sb.toString().trim()
        return out
    }

    // ---- Band kilidi ----------------------------------------------------

    /** Band kilidi şifresini modemde /root/band-kilidi-sifre.txt dosyasına yazar (yalnızca root okur). */
    suspend fun writeBandPassword(pw: String) {
        val r = client.call("aw1k", "bandpw_write", JSONObject().put("pw", pw))
        if (!r.optBoolean("ok")) throw UbusException("Şifre modeme yazılamadı")
    }

    suspend fun lockBands(): Map<String, BandGroup> {
        val lb = client.call("qmodem", "get_lockband", JSONObject().put("config_section", section()))
            .optJSONObject("lockband") ?: throw UbusException("Band bilgisi okunamadı")
        val out = linkedMapOf<String, BandGroup>()
        for (cls in listOf("UMTS", "LTE", "NR", "NR_NSA")) {
            val g = lb.optJSONObject(cls) ?: continue
            val av = g.optJSONArray("available_band") ?: continue
            val avail = (0 until av.length()).map {
                val o = av.getJSONObject(it)
                o.optString("band_id") to o.optString("band_name")
            }
            val lk = g.optJSONArray("lock_band")
            val locked = if (lk == null) emptySet() else (0 until lk.length()).map { lk.optString(it) }.toSet()
            out[cls] = BandGroup(avail, locked)
        }
        return out
    }

    suspend fun setLockBand(bandClass: String, ids: List<String>) {
        client.call(
            "qmodem", "set_lockband",
            JSONObject().put("config_section", section())
                .put("params", JSONObject().put("band_class", bandClass).put("lock_band", ids.joinToString(",")).toString()),
        )
    }

    private suspend fun items(method: String): List<JSONObject> {
        val res = client.call("qmodem", method, JSONObject().put("config_section", section()))
        val arr = res.optJSONArray("modem_info") ?: return emptyList()
        return (0 until arr.length()).map { arr.getJSONObject(it) }
    }

    private fun List<JSONObject>.value(key: String): String? =
        firstOrNull { it.optString("key") == key }
            ?.optString("value")
            ?.takeIf { it.isNotEmpty() }

    private var baseCache: List<JSONObject> = emptyList()
    private var baseAt = 0L
    private var bandCount = 1
    private var bandCells: List<JSONObject> = emptyList()
    private var bandAt = 0L
    private var atPortCache: String? = null
    private var caAt = 0L
    private var caCache: List<List<String>> = emptyList()

    /** Sinyal yüzdesi: RSSI varsa CSQ ölçeğiyle (LuCI gibi), yoksa RSRP/SINR/RSRQ ağırlıklı toplamı. */
    private fun signalQuality(signal: List<SignalItem>): Int? {
        fun avg(prefix: String): Float? = signal
            .filter { it.label.uppercase().startsWith(prefix) }
            .mapNotNull { it.value.toFloatOrNull() }
            .takeIf { it.isNotEmpty() }?.average()?.toFloat()
        fun scale(v: Float, lo: Float, hi: Float) = ((v - lo) / (hi - lo)).coerceIn(0f, 1f)
        // LuCI'deki gibi: RSSI -> CSQ (0-31) -> yüzde. 23 CSQ = %74.
        avg("RSSI")?.let { rssi ->
            val csq = ((rssi + 113f) / 2f).coerceIn(0f, 31f)
            return (csq / 31f * 100).roundToInt()
        }
        val parts = mutableListOf<Pair<Float, Float>>() // ağırlık, 0..1 değer
        avg("RSRP")?.let { parts += 0.5f to scale(it, -120f, -70f) }
        (avg("SINR") ?: avg("SNR"))?.let { parts += 0.3f to scale(it, -3f, 25f) }
        avg("RSRQ")?.let { parts += 0.2f to scale(it, -20f, -6f) }
        if (parts.isEmpty()) return null
        val w = parts.sumOf { it.first.toDouble() }
        return (parts.sumOf { (it.first * it.second).toDouble() } / w * 100).roundToInt()
    }

    /** Aktif hücre sayısı (PCC + SCC'ler); AT trafiğini azaltmak için 30 sn önbellekli. */
    private suspend fun carrierCount(c: List<JSONObject>): Int {
        val now = System.currentTimeMillis()
        if (now - bandAt > 30_000) {
            bandAt = now
            val arr = try {
                client.call("qmodem", "get_current_band", JSONObject().put("config_section", section()))
                    .optJSONObject("current_band")?.optJSONArray("cells")
            } catch (e: UbusException) {
                null
            }
            bandCells = if (arr == null) emptyList() else (0 until arr.length()).map { arr.getJSONObject(it) }
            bandCount = if (bandCells.isEmpty()) 1 else bandCells.size
        }
        val scc = c.map { it.optString("key") + " " + it.optString("extra_info") }
            .filter { it.contains("SCC", ignoreCase = true) }
            .mapNotNull { Regex("SCC\\s*(\\d+)", RegexOption.IGNORE_CASE).find(it)?.groupValues?.get(1)?.toIntOrNull() }
            .distinct().size
        return maxOf(bandCount, scc + 1, 1)
    }

    suspend fun modemState(): ModemState {
        // Modem AT komutlarıyla konuştuğu için sorgular sırayla yapılır.
        val c = items("cell_info")
        val n = items("network_info")
        val now = System.currentTimeMillis()
        if (baseCache.isEmpty() || now - baseAt > 30_000) {
            baseCache = items("base_info")
            baseAt = now
        }
        val b = baseCache

        val signal = c.filter { it.optString("type") == "progress_bar" }.map { o ->
            val value = o.optString("value")
            val v = value.toFloatOrNull()
            val min = o.optString("min_value").toFloatOrNull()
            val max = o.optString("max_value").toFloatOrNull()
            val fraction = if (v != null && min != null && max != null && max > min) {
                ((v - min) / (max - min)).coerceIn(0f, 1f)
            } else {
                null
            }
            val extra = o.optString("extra_info")
            SignalItem(
                label = o.optString("key") + if (extra.isNotEmpty()) " · $extra" else "",
                value = value,
                unit = o.optString("unit"),
                fraction = fraction,
            )
        }

        val ca = caInfo()
        val carriers = maxOf(carrierCount(c), ca.count { it[0].equals("PCC", true) || (it.getOrNull(4) != "0" || it[3].contains("NR", true)) })
        val mcc = c.value("MCC")
        var conn: Triple<Long?, Long?, Long?> = Triple(null, null, null)
        try {
            val w = client.call("aw1k", "wan_info")
            conn = Triple(
                w.optLong("uptime", -1).takeIf { it >= 0 },
                w.optLong("tx", -1).takeIf { it >= 0 },
                w.optLong("rx", -1).takeIf { it >= 0 },
            )
        } catch (e: UbusException) {
        }
        // Canlı hız: modemin "Rx/Tx Rate" değeri gecikmeli; WAN bayt sayaçlarının farkından anlık hız hesaplanır.
        var rxRate = n.value("Rx Rate") ?: "—"
        var txRate = n.value("Tx Rate") ?: "—"
        val sent = conn.second
        val recv = conn.third
        if (sent != null && recv != null) {
            val nowMs = System.currentTimeMillis()
            val last = wanLast
            wanLast = Triple(nowMs, sent, recv)
            if (last != null && nowMs - last.first in 800L..20000L && sent >= last.second && recv >= last.third) {
                val dt = (nowMs - last.first) / 1000.0
                rxRate = fmtBps((recv - last.third) * 8 / dt)
                txRate = fmtBps((sent - last.second) * 8 / dt)
            }
        }
        return ModemState(
            country = countryFor(mcc),
            connSeconds = conn.first,
            sentBytes = conn.second,
            recvBytes = conn.third,
            cells = cellRows(c, ca),
            operatorName = operatorFor(c.value("MCC"), c.value("MNC")),
            networkMode = (c.value("network_mode") ?: n.value("Network Type") ?: "—")
                .removeSuffix(" Mode"),
            connected = b.value("connect_status") == "Yes",
            rxRate = rxRate,
            txRate = txRate,
            signal = signal,
            modemName = b.value("name") ?: "—",
            temperature = b.value("temperature") ?: "—",
            signalPercent = signalQuality(signal),
            carriers = carriers,
        )
    }

    /** Son WAN sayaç örneği (zaman ms, gönderilen, alınan bayt): anlık hız için. */
    private var wanLast: Triple<Long, Long, Long>? = null

    private fun fmtBps(bps: Double): String = when {
        bps >= 1_000_000 -> "%.2f Mbps".format(java.util.Locale.US, bps / 1_000_000)
        bps >= 1_000 -> "%.2f Kbps".format(java.util.Locale.US, bps / 1_000)
        else -> "%.0f bps".format(java.util.Locale.US, bps)
    }

    suspend fun redial() {
        client.call("qmodem", "modem_redial", JSONObject().put("config_section", section()))
    }

    private fun countryFor(mcc: String?): String = when (mcc) {
        "286" -> "Türkiye"
        "262" -> "Almanya"
        "208" -> "Fransa"
        "234", "235" -> "Birleşik Krallık"
        "310", "311", "312", "313" -> "ABD"
        "202" -> "Yunanistan"
        "284" -> "Bulgaristan"
        "283" -> "Ermenistan"
        "282" -> "Gürcistan"
        "400" -> "Azerbaycan"
        "257" -> "Belarus"
        "250" -> "Rusya"
        "255" -> "Ukrayna"
        "222" -> "İtalya"
        "214" -> "İspanya"
        "204" -> "Hollanda"
        "206" -> "Belçika"
        "232" -> "Avusturya"
        "228" -> "İsviçre"
        "420" -> "Suudi Arabistan"
        "424" -> "BAE"
        null, "" -> "—"
        else -> "MCC $mcc"
    }

    /** LTE EARFCN (DL) -> bant numarası; bilinmiyorsa boş. */
    private fun lteBandFromEarfcn(e: Int): String = when (e) {
        in 0..599 -> "B1"
        in 1200..1949 -> "B3"
        in 2400..2649 -> "B5"
        in 2750..3449 -> "B7"
        in 3450..3799 -> "B8"
        in 6150..6449 -> "B20"
        in 9210..9659 -> "B28"
        in 37750..38249 -> "B38"
        in 38650..39649 -> "B40"
        in 39650..41589 -> "B41"
        else -> ""
    }

    /** get_current_band hücreleri + cell_info ölçümlerinden taşıyıcı satırları. */
    private fun lteBw(idx: String): String = when (idx) {
        "6" -> "1.4"; "15" -> "3"; "25" -> "5"; "50" -> "10"; "75" -> "15"; "100" -> "20"
        else -> idx
    }

    private fun nrBw(idx: String): String = when (idx) {
        "0" -> "5"; "1" -> "10"; "2" -> "15"; "3" -> "20"; "4" -> "25"; "5" -> "30"; "6" -> "40"
        "7" -> "50"; "8" -> "60"; "9" -> "70"; "10" -> "80"; "11" -> "90"; "12" -> "100"
        else -> idx
    }

    /** AT+QCAINFO satırlarından taşıyıcı satırları; PCC'nin ölçümleri cell_info'dan tamamlanır. */
    private fun caRows(c: List<JSONObject>, ca: List<List<String>>): List<CellRow> {
        fun dash(v: String?) = if (v.isNullOrEmpty()) "—" else v
        val rows = mutableListOf<CellRow>()
        var scc = 0
        for (f in ca) {
            val nr = f[3].contains("NR", true)
            val isPcc = f[0].equals("PCC", true)
            if (!isPcc && !nr && f.getOrNull(4) == "0") continue
            val num = Regex("(\\d+)").find(f[3])?.value ?: f[3]
            val band = (if (nr) "N" else "B") + num
            val pci: String; val rsrp: String; val rsrq: String; val rssi: String; val sinr: String
            if (nr) {
                pci = f.getOrNull(4).orEmpty(); rsrp = f.getOrNull(5).orEmpty(); rsrq = f.getOrNull(6).orEmpty()
                rssi = ""; sinr = f.getOrNull(7).orEmpty()
            } else {
                pci = f.getOrNull(5).orEmpty(); rsrp = f.getOrNull(6).orEmpty(); rsrq = f.getOrNull(7).orEmpty()
                rssi = f.getOrNull(8).orEmpty(); sinr = f.getOrNull(9).orEmpty()
            }
            val idx = if (isPcc) 0 else ++scc
            fun sigCell(key: String, own: String): String {
                if (idx != 0) return dash(own)
                val v = c.filter { it.optString("key") == key }
                    .firstOrNull { !it.optString("extra_info").contains("SCC", true) }?.optString("value")
                return dash(if (v.isNullOrEmpty()) own else v)
            }
            rows += CellRow(
                role = if (isPcc) "PCC" else "SCC $idx",
                band = band,
                bandwidth = if (f[2].isEmpty()) "—" else (if (nr) nrBw(f[2]) else lteBw(f[2])) + " MHz",
                pci = dash(pci),
                earfcn = dash(f[1]),
                rssi = sigCell("RSSI", rssi), rsrp = sigCell("RSRP", rsrp),
                rsrq = sigCell("RSRQ", rsrq), sinr = sigCell("SINR", sinr),
            )
        }
        return rows
    }

    private fun cellRows(c: List<JSONObject>, ca: List<List<String>>): List<CellRow> {
        caRows(c, ca).takeIf { it.isNotEmpty() }?.let { return it }
        fun sig(key: String, scc: Int): String {
            val cand = c.filter { it.optString("key") == key }
            val pick = if (scc == 0) {
                cand.firstOrNull { !it.optString("extra_info").contains("SCC", true) }
            } else {
                cand.firstOrNull { Regex("SCC\\s*$scc\\b", RegexOption.IGNORE_CASE).containsMatchIn(it.optString("extra_info")) }
            }
            val v = pick?.optString("value").orEmpty()
            return if (v.isEmpty()) "—" else v
        }
        val rows = mutableListOf<CellRow>()
        var sccIdx = 0
        bandCells.forEachIndexed { i, cell ->
            val isScc = cell.optString("role").contains("scc", true)
            val idx = if (isScc) ++sccIdx else 0
            val ch = cell.optString("channel")
            val band = cell.optString("band_name").ifEmpty { cell.optString("band") }
                .ifEmpty { ch.toIntOrNull()?.let { lteBandFromEarfcn(it) } ?: "" }
            val bwMain = cell.optString("dl_bandwidth")
            rows += CellRow(
                role = if (isScc) "SCC $idx" else "PCC",
                band = band.ifEmpty { "—" },
                bandwidth = if (bwMain.isEmpty()) "—" else "$bwMain MHz",
                pci = cell.optString("pci").ifEmpty { "—" },
                earfcn = ch.ifEmpty { "—" },
                rssi = sig("RSSI", idx),
                rsrp = sig("RSRP", idx),
                rsrq = sig("RSRQ", idx),
                sinr = sig("SINR", idx),
            )
        }
        if (rows.isEmpty()) {
            val e = c.value("EARFCN")
            val bw = c.value("DL Bandwidth")
            rows += CellRow(
                role = "PCC",
                band = e?.toIntOrNull()?.let { lteBandFromEarfcn(it) }?.ifEmpty { null } ?: "—",
                bandwidth = if (bw == null) "—" else "$bw MHz",
                pci = c.value("Physical Cell ID") ?: "—",
                earfcn = e ?: "—",
                rssi = sig("RSSI", 0), rsrp = sig("RSRP", 0), rsrq = sig("RSRQ", 0), sinr = sig("SINR", 0),
            )
        }
        return rows
    }

    private fun operatorFor(mcc: String?, mnc: String?): String = when ("$mcc-$mnc") {
        "286-01" -> "Turkcell"
        "286-02" -> "Vodafone"
        "286-03" -> "Türk Telekom"
        else -> if (mcc.isNullOrEmpty()) "—" else "$mcc-$mnc"
    }

    // ---- Wi-Fi ----------------------------------------------------------

    suspend fun wifiNetworks(): List<WifiNetwork> {
        val values = client.call("uci", "get", JSONObject().put("config", "wireless"))
            .optJSONObject("values") ?: return emptyList()
        val out = mutableListOf<WifiNetwork>()
        for (name in values.keys()) {
            val s = values.optJSONObject(name) ?: continue
            if (s.optString(".type") != "wifi-iface") continue
            out += WifiNetwork(
                section = name,
                ssid = s.optString("ssid"),
                radio = s.optString("device"),
                enabled = s.optString("disabled") != "1",
                encryption = s.optString("encryption").ifEmpty { "none" },
            )
        }
        return out.sortedBy { it.radio }
    }

    suspend fun setWifiEnabled(section: String, enabled: Boolean) {
        client.call(
            "uci", "set",
            JSONObject()
                .put("config", "wireless")
                .put("section", section)
                .put("values", JSONObject().put("disabled", if (enabled) "0" else "1")),
        )
        client.call("uci", "apply", JSONObject().put("rollback", false))
    }

    suspend fun guestWifi(): GuestWifi {
        val o = client.call("aw1k", "guest_get")
        return GuestWifi(
            exists = o.optBoolean("exists"),
            enabled = o.optBoolean("enabled"),
            ssid = o.optString("ssid"),
            key = o.optString("key"),
        )
    }

    suspend fun setGuestWifi(enabled: Boolean, ssid: String, key: String) {
        val r = client.call(
            "aw1k", "guest_set",
            JSONObject().put("enabled", enabled).put("ssid", ssid).put("key", key),
        )
        if (!r.optBoolean("ok")) throw UbusException(r.optString("error").ifEmpty { "Misafir Wi-Fi kaydedilemedi" })
    }

    /** Wi-Fi'ye bağlı istemciler (aw1k eklentisi: hostapd + bağlanma zamanı takibi). */
    /** `iw station dump`'tan okunan sinyaller (mac -> dBm) ve okunma zamanı. */
    private var iwSig: Map<String, Int> = emptyMap()
    private var iwSigAt = 0L

    suspend fun wifiClients(networks: List<WifiNetwork>): List<WifiClient> {
        val o = client.call("aw1k", "wifi_clients")
        val now = o.optLong("now")
        val seen = o.optJSONObject("seen")
        // arayüz adı -> ağ bölümü
        val ifToSection = mutableMapOf<String, String>()
        o.optJSONObject("status")?.let { st ->
            st.keys().forEach { radio ->
                st.optJSONObject(radio)?.optJSONArray("interfaces")?.let { a ->
                    for (i in 0 until a.length()) {
                        val e = a.getJSONObject(i)
                        val ifn = e.optString("ifname")
                        if (ifn.isNotEmpty()) ifToSection[ifn] = e.optString("section")
                    }
                }
            }
        }
        val out = mutableListOf<WifiClient>()
        val ifaces = o.optJSONArray("ifaces") ?: return out
        for (i in 0 until ifaces.length()) {
            val ifc = ifaces.getJSONObject(i)
            val name = ifc.optString("name")
            val data = ifc.optJSONObject("data") ?: continue
            val clients = data.optJSONObject("clients") ?: continue
            val freq = data.optInt("freq")
            val section = ifToSection[name].orEmpty()
            val net = networks.firstOrNull { it.section == section }
            // iwinfo bağlı süresi varsa onu kullan
            val assocTimes = mutableMapOf<String, Long>()
            val assocSignal = mutableMapOf<String, Int>()
            ifc.optJSONObject("assoc")?.optJSONArray("results")?.let { a ->
                for (k in 0 until a.length()) {
                    val e = a.getJSONObject(k)
                    if (e.has("connected_time")) assocTimes[e.optString("mac").lowercase()] = e.optLong("connected_time")
                    val sg = e.optInt("signal", 0).takeIf { it != 0 && it > -95 }
                        ?: e.optInt("signal_avg", 0).takeIf { it != 0 && it > -95 }
                    if (sg != null) assocSignal[e.optString("mac").lowercase()] = sg
                }
            }
            clients.keys().forEach { mac ->
                val c = clients.getJSONObject(mac)
                if (!c.optBoolean("authorized", true)) return@forEach
                val key = mac.lowercase()
                val secs = assocTimes[key]
                    ?: seen?.takeIf { it.has(key) }?.let { (now - it.optLong(key)).coerceAtLeast(0) }
                out += WifiClient(
                    mac = mac.uppercase(),
                    section = section,
                    ssid = net?.ssid ?: name,
                    band = if (freq >= 5000) "5 GHz" else "2.4 GHz",
                    // Bazı sürücüler hostapd'ye sinyali 0 verir: iwinfo değeri kullanılır; o da yoksa 0 = bilinmiyor.
                    signal = c.optInt("signal").takeIf { it != 0 && it > -95 } ?: assocSignal[key] ?: 0,
                    rxBytes = c.optJSONObject("bytes")?.optLong("rx") ?: 0,
                    txBytes = c.optJSONObject("bytes")?.optLong("tx") ?: 0,
                    rxMbps = ((c.optJSONObject("rate")?.optLong("rx") ?: 0) / 1_000_000).toInt(),
                    txMbps = ((c.optJSONObject("rate")?.optLong("tx") ?: 0) / 1_000_000).toInt(),
                    secs = secs,
                )
            }
        }
        // Sinyal hâlâ bilinmiyorsa (0) Wi-Fi sürücüsünden oku: `iw dev <arayüz> station dump`
        // Bu modemde (ath11k) hostapd/iwinfo "signal" 0, "signal avg" sabit -95 veriyor: ikisi de geçersiz.
        // Gerçek değer `iw station dump` içindeki "avg ack signal" (yoksa "last ack signal") satırında.
        // iw her saniye çalıştırılmasın diye sonuç 5 sn saklanır.
        val nowMs = System.currentTimeMillis()
        if (out.any { it.signal == 0 }) {
            if (nowMs - iwSigAt >= 5_000L) {
                val names = (0 until ifaces.length()).map { ifaces.getJSONObject(it).optString("name") }.filter { it.isNotEmpty() }.toSet()
                val sig = mutableMapOf<String, Int>()
                for (ifn in names) {
                    val txt = try {
                        fileExec("/usr/sbin/iw", "dev", ifn, "station", "dump").optString("stdout")
                    } catch (e: Exception) {
                        ""
                    }
                    var cur: String? = null
                    var avgAck: Int? = null
                    var lastAck: Int? = null
                    var plain: Int? = null
                    fun flush() {
                        val m = cur ?: return
                        val v = avgAck ?: lastAck ?: plain
                        if (v != null) sig[m] = v
                    }
                    fun num(t: String, prefix: String): Int? =
                        t.removePrefix(prefix).trim().substringBefore(' ').substringBefore('[').toIntOrNull()
                            ?.takeIf { it != 0 && it > -95 }
                    txt.lineSequence().forEach { line ->
                        val t = line.trim()
                        when {
                            t.startsWith("Station ") -> {
                                flush()
                                cur = t.removePrefix("Station ").substringBefore(' ').lowercase()
                                avgAck = null; lastAck = null; plain = null
                            }
                            t.startsWith("avg ack signal:") -> avgAck = num(t, "avg ack signal:")
                            t.startsWith("last ack signal:") -> lastAck = num(t, "last ack signal:")
                            t.startsWith("signal:") -> plain = num(t, "signal:")
                        }
                    }
                    flush()
                }
                iwSig = sig
                iwSigAt = nowMs
            }
            if (iwSig.isNotEmpty()) {
                for (i in out.indices) {
                    val c = out[i]
                    if (c.signal == 0) iwSig[c.mac.lowercase()]?.let { out[i] = c.copy(signal = it) }
                }
            }
        }
        return out.sortedBy { it.ssid }
    }

    suspend fun wifiDetail(section: String): WifiDetail {
        val values = client.call("uci", "get", JSONObject().put("config", "wireless"))
            .optJSONObject("values") ?: throw UbusException("Wi-Fi ayarı okunamadı")
        val s = values.optJSONObject(section) ?: throw UbusException("Ağ bulunamadı")
        val radio = s.optString("device")
        val r = values.optJSONObject(radio)
        return WifiDetail(
            section = section,
            ssid = s.optString("ssid"),
            key = s.optString("key"),
            encryption = s.optString("encryption").ifEmpty { "none" },
            hidden = s.optString("hidden") == "1",
            isolate = s.optString("isolate") == "1",
            radio = radio,
            band = r?.optString("band").orEmpty(),
            channel = r?.optString("channel").orEmpty().ifEmpty { "auto" },
            htmode = r?.optString("htmode").orEmpty(),
            txpower = r?.optString("txpower").orEmpty(),
            country = r?.optString("country").orEmpty(),
        )
    }

    /** Ağ ve radyo ayarlarını yazar. Radyo ayarları aynı radyodaki diğer ağları da etkiler. */
    suspend fun saveWifi(d: WifiDetail) {
        val iface = JSONObject()
            .put("ssid", d.ssid)
            .put("encryption", d.encryption)
            .put("hidden", if (d.hidden) "1" else "0")
            .put("isolate", if (d.isolate) "1" else "0")
        if (d.encryption != "none" && d.key.isNotEmpty()) iface.put("key", d.key)
        client.call("uci", "set", JSONObject().put("config", "wireless").put("section", d.section).put("values", iface))
        if (d.radio.isNotEmpty()) {
            val radio = JSONObject()
            if (d.channel.isNotEmpty()) radio.put("channel", d.channel)
            if (d.htmode.isNotEmpty()) radio.put("htmode", d.htmode)
            if (d.txpower.isNotEmpty()) radio.put("txpower", d.txpower)
            if (d.country.isNotEmpty()) radio.put("country", d.country.uppercase())
            if (radio.length() > 0) {
                client.call("uci", "set", JSONObject().put("config", "wireless").put("section", d.radio).put("values", radio))
            }
        }
        client.call("uci", "apply", JSONObject().put("rollback", false))
    }

    private fun maclist(s: JSONObject): List<String> {
        val a = s.optJSONArray("maclist")
        if (a != null) return (0 until a.length()).map { a.getString(it).uppercase() }
        val one = s.optString("maclist")
        return if (one.isEmpty()) emptyList() else listOf(one.uppercase())
    }

    /** Wi-Fi ağlarında "reddet" listesine alınmış MAC adresleri. */
    suspend fun blockedMacs(): Set<String> {
        val values = client.call("uci", "get", JSONObject().put("config", "wireless"))
            .optJSONObject("values") ?: return emptySet()
        val out = mutableSetOf<String>()
        values.keys().forEach { name ->
            val s = values.optJSONObject(name) ?: return@forEach
            if (s.optString(".type") == "wifi-iface" && s.optString("macfilter") == "deny") out += maclist(s)
        }
        return out
    }

    /** Cihazı tüm Wi-Fi ağlarında engeller ya da engelini kaldırır (kablolu cihazlar etkilenmez). */
    suspend fun setBlocked(mac: String, block: Boolean) {
        val m = mac.uppercase()
        val values = client.call("uci", "get", JSONObject().put("config", "wireless"))
            .optJSONObject("values") ?: throw UbusException("Wi-Fi ayarı okunamadı")
        val ifaces = values.keys().asSequence().filter {
            values.optJSONObject(it)?.optString(".type") == "wifi-iface"
        }.toList()
        for (name in ifaces) {
            val s = values.getJSONObject(name)
            if (s.optString("macfilter") == "allow") {
                throw UbusException("\"${s.optString("ssid")}\" ağında MAC 'izin ver' listesi açık; engelleme yapılamıyor")
            }
        }
        for (name in ifaces) {
            val s = values.getJSONObject(name)
            val cur = maclist(s).toMutableList()
            if (block) { if (m !in cur) cur += m } else cur.remove(m)
            if (cur.isEmpty()) {
                client.call(
                    "uci", "delete",
                    JSONObject().put("config", "wireless").put("section", name)
                        .put("options", JSONArray().put("maclist").put("macfilter")),
                )
            } else {
                client.call(
                    "uci", "set",
                    JSONObject().put("config", "wireless").put("section", name).put(
                        "values",
                        JSONObject().put("macfilter", "deny").put("maclist", JSONArray(cur)),
                    ),
                )
            }
        }
        client.call("uci", "apply", JSONObject().put("rollback", false))
    }

    // ---- Cihazlar -------------------------------------------------------

    suspend fun devices(): List<Device> {
        val leases = client.call("luci-rpc", "getDHCPLeases").optJSONArray("dhcp_leases")
            ?: return emptyList()
        // KediRay yönü ve Wi-Fi bağlantı süresi (eklenti yoksa sessizce atlanır)
        val modes = mutableMapOf<String, String>()
        val secs = mutableMapOf<String, Long>()
        var def = "vpn"
        try {
            val o = client.call("aw1k", "kediray_devices")
            def = o.optString("default").ifEmpty { "vpn" }.let { if (it == "default") "vpn" else it }
            o.optJSONArray("devices")?.let { a ->
                for (i in 0 until a.length()) {
                    val d = a.getJSONObject(i)
                    modes[d.optString("mac").uppercase()] = d.optString("mode")
                }
            }
            o.optJSONArray("assoc")?.let { a ->
                for (i in 0 until a.length()) {
                    val d = a.getJSONObject(i)
                    val t = d.optLong("secs", -1)
                    if (t >= 0) secs[d.optString("mac").uppercase()] = t
                }
            }
        } catch (e: UbusException) {
        }
        // Gerçekten bağlı olanlar: KediRay'in cihaz listesi (Wi-Fi bağlı listesi + ARP). DHCP kirası cihaz ayrıldıktan
        // saatlerce kalır; bu yüzden yalnızca şu an çevrimiçi olanlar gösterilir. Komut yoksa kira listesine dönülür.
        try {
            val r = fileExec("/usr/bin/kediray", "devices")
            val arr = JSONArray(r.optString("stdout").trim())
            val live = mutableListOf<Device>()
            for (i in 0 until arr.length()) {
                val d = arr.getJSONObject(i)
                if (!d.optBoolean("online", false)) continue
                val mac = d.optString("mac").uppercase()
                val m = d.optString("mode")
                live += Device(
                    hostname = d.optString("name").let { h -> if (h.isEmpty() || h == "?") "Bilinmeyen cihaz" else h },
                    ip = d.optString("ip"),
                    mac = mac,
                    mode = if (m.isEmpty() || m == "default") def else m,
                    connectedSecs = d.optLong("uptime", -1).takeIf { t -> t >= 0 } ?: secs[mac],
                )
            }
            return live.sortedBy { d -> d.ip.substringAfterLast('.').toIntOrNull() ?: 0 }
        } catch (e: Exception) {
            // komut yok ya da çıktı okunamadı: kira listesi
        }
        return (0 until leases.length()).map {
            val o = leases.getJSONObject(it)
            val mac = o.optString("macaddr")
            Device(
                hostname = o.optString("hostname").let { h ->
                    if (h.isEmpty() || h == "?") "Bilinmeyen cihaz" else h
                },
                ip = o.optString("ipaddr"),
                mac = mac,
                mode = modes[mac.uppercase()]?.takeIf { m -> m.isNotEmpty() } ?: def,
                connectedSecs = secs[mac.uppercase()],
            )
        }.sortedBy { d -> d.ip.substringAfterLast('.').toIntOrNull() ?: 0 }
    }

    /** `kediray stats`: cihaz başına bugünkü kullanım + son 30 günün VPN/direkt dökümü; KediRay kurulu değilse null. */
    suspend fun kediStats(): KediStats? {
        val r = fileExec("/usr/bin/kediray", "stats")
        val o = JSONObject(r.optString("stdout").trim())
        val arr = o.optJSONArray("devices") ?: return null
        val list = mutableListOf<DeviceShare>()
        for (i in 0 until arr.length()) {
            val d = arr.getJSONObject(i)
            val t = d.optJSONObject("today") ?: continue
            val name = d.optString("name").ifEmpty { d.optString("ip") }
            list += DeviceShare(name, d.optString("ip"), t.optLong("rx") + t.optLong("tx"), t.optLong("rx"), t.optLong("tx"))
        }
        val ot = o.optJSONObject("others")?.optJSONObject("today")
        val shares = DeviceShares(list, if (ot != null) ot.optLong("rx") + ot.optLong("tx") else 0L)
        val days = mutableListOf<TrafficDay>()
        o.optJSONArray("days")?.let { a ->
            for (i in 0 until a.length()) {
                val d = a.getJSONObject(i)
                days += TrafficDay(d.optString("day"), d.optLong("vr"), d.optLong("vt"), d.optLong("dr"), d.optLong("dt"))
            }
        }
        return KediStats(shares, days)
    }

    // ---- SMS (qmodem ubus eklentisi) ---------------------------------------

    private var smsSection: String? = null

    /** qmodem'deki modem bölümünün adı (ör. "4_1"); bulunamazsa SMS desteklenmiyor demektir. */
    private suspend fun smsSection(): String {
        smsSection?.let { return it }
        val values = try {
            client.call("uci", "get", JSONObject().put("config", "qmodem")).optJSONObject("values")
        } catch (e: UbusException) {
            null
        } ?: throw UbusException("Bu modemde SMS arayüzü (qmodem) bulunamadı")
        var found: String? = null
        values.keys().forEach { k ->
            if (found == null && values.optJSONObject(k)?.optString(".type") == "modem-device") found = k
        }
        return (found ?: throw UbusException("qmodem'de modem bulunamadı")).also { smsSection = it }
    }

    /** SIM'deki SMS'ler; yeni olan üstte. Aynı gönderen ve saatteki parçalar tek mesajda birleştirilir. */
    suspend fun smsInbox(): List<SmsMessage> {
        val o = client.call("qmodem", "get_sms", JSONObject().put("config_section", smsSection()))
        val arr = o.optJSONArray("msg") ?: return emptyList()
        val rows = (0 until arr.length()).map { arr.getJSONObject(it) }.sortedBy { it.optInt("index") }
        val groups = LinkedHashMap<String, MutableList<JSONObject>>()
        for (r in rows) groups.getOrPut(r.optString("sender") + "|" + r.optLong("timestamp")) { mutableListOf() }.add(r)
        return groups.values.map { g ->
            SmsMessage(
                indexes = g.map { it.optInt("index") },
                sender = g.first().optString("sender"),
                timestamp = g.first().optLong("timestamp"),
                text = g.joinToString("") { it.optString("content") },
            )
        }.sortedByDescending { it.timestamp }
    }

    /** SMS gönderir. qmodem `params` alanını metin olarak bekliyor (JSON metni); kabul edilmezse nesne olarak dener. */
    suspend fun smsSend(number: String, text: String) {
        val inner = JSONObject().put("phone_number", number).put("message_content", text)
        val base = JSONObject().put("config_section", smsSection())
        try {
            client.call("qmodem", "send_sms", JSONObject(base.toString()).put("params", inner.toString()))
        } catch (e: UbusException) {
            // Geçersiz bağımsız değişken (kod 2): metin biçimi reddedildi, mesaj gitmedi; nesne olarak dene.
            if (e.code != 2) throw e
            client.call("qmodem", "send_sms", JSONObject(base.toString()).put("params", inner))
        }
    }

    /** SIM'den siler. qmodem'in delete_sms'i AW1000'de silmediği için modemdeki aw1k-smsdel (sms_tool) kullanılır. */
    suspend fun smsDelete(indexes: List<Int>) {
        if (indexes.isEmpty()) return
        val r = try {
            client.call("aw1k", "sms_delete", JSONObject().put("index", indexes.joinToString(" ")))
        } catch (e: UbusException) {
            throw UbusException("Silmek için modemde KediRay'i 1.19.96 ya da yenisine güncelle (${e.message})")
        }
        if (!r.optBoolean("ok")) throw UbusException(r.optString("error").ifEmpty { "Modem mesajı silmedi" })
    }

    // ---- SMS spam filtresi (modemdeki aw1k-smsfilter) ----------------------

    suspend fun smsFilter(): SmsFilterState {
        val o = client.call("aw1k", "sms_rules")
        val r = o.optJSONObject("rules") ?: JSONObject()
        fun list(k: String): List<String> = r.optJSONArray(k)?.let { a ->
            (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() }
        } ?: emptyList()
        val log = o.optJSONArray("log")?.let { a ->
            (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map {
                SmsFilterLogEntry(it.optLong("t"), it.optString("sender"), it.optString("reason"), it.optString("text"))
            }
        } ?: emptyList()
        return SmsFilterState(
            SmsRules(r.optBoolean("enabled"), r.optBoolean("auto_block", true), list("keywords"), list("blocked"), list("allow")),
            log,
            o.optBoolean("ucode", true),
        )
    }

    suspend fun smsSaveRules(r: SmsRules) {
        val j = JSONObject()
            .put("enabled", r.enabled)
            .put("auto_block", r.autoBlock)
            .put("keywords", JSONArray(r.keywords))
            .put("blocked", JSONArray(r.blocked))
            .put("allow", JSONArray(r.allow))
        val res = client.call("aw1k", "sms_rules_set", JSONObject().put("rules", j.toString()))
        if (!res.optBoolean("ok")) throw UbusException(res.optString("error").ifEmpty { "Kurallar kaydedilemedi" })
    }

    /** [apply] false: deneme (hiçbir şey silinmez); true: kurallar açıksa şimdi uygular. */
    suspend fun smsFilterRun(apply: Boolean): SmsRunResult {
        val o = client.call("aw1k", "sms_filter_run", JSONObject().put("mode", if (apply) "run" else "dry"))
        fun strs(k: String): List<String> = o.optJSONArray(k)?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList()
        val hits = o.optJSONArray("matched")?.let { a ->
            (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map {
                SmsFilterHit(it.optString("sender"), it.optString("reason"), it.optString("text"), it.optLong("timestamp"))
            }
        } ?: emptyList()
        return SmsRunResult(
            ok = o.optBoolean("ok"),
            dry = o.optBoolean("dry"),
            skipped = o.optString("skipped").takeIf { it.isNotEmpty() },
            scanned = o.optInt("scanned"),
            deleted = o.optInt("deleted"),
            blocked = strs("blocked"),
            matched = hits,
            error = o.optString("error").takeIf { it.isNotEmpty() },
        )
    }

    /** KediRay cihaz yönünü değiştirir (vpn | direct | block). */
    suspend fun setDeviceMode(mac: String, mode: String, name: String) {
        val r = client.call(
            "aw1k", "kediray_device",
            JSONObject().put("mac", mac).put("mode", mode).put("name", name),
        )
        if (!r.optBoolean("ok")) throw UbusException("Yön değiştirilemedi")
    }

    // ---- Sistem bilgisi -------------------------------------------------

    private var lastCpu: Pair<Long, Long>? = null

    suspend fun systemState(): SystemState {
        val info = client.call("system", "info")
        val mem = info.optJSONObject("memory")
        val total = mem?.optLong("total") ?: 0L
        val available = when {
            mem == null -> 0L
            mem.has("available") -> mem.optLong("available")
            else -> mem.optLong("free") + mem.optLong("buffered") + mem.optLong("cached")
        }
        val used = (total - available).coerceAtLeast(0L)
        val load = (info.optJSONArray("load")?.optDouble(0, 0.0) ?: 0.0) / 65536.0
        return SystemState(
            cpuPercent = cpuPercent(),
            load1 = load.toFloat(),
            memPercent = if (total > 0) (used * 100 / total).toInt() else 0,
            memUsed = used,
            memTotal = total,
            uptimeSeconds = info.optLong("uptime"),
        )
    }

    /** Ağ arayüzleri ve internet (WAN) bağlantısı: IP, ağ geçidi, DNS, protokol, süre. */
    suspend fun netDetails(): NetDetails {
        val arr = client.call("network.interface", "dump").optJSONArray("interface") ?: JSONArray()
        val rows = mutableListOf<IfaceRow>()
        var wan: WanInfo? = null
        var wanScore = -1
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val name = o.optString("interface")
            if (name.isEmpty() || name == "loopback") continue
            val up = o.optBoolean("up")
            val dev = o.optString("l3_device").ifEmpty { o.optString("device") }
            val proto = o.optString("proto")
            val v4 = o.optJSONArray("ipv4-address")?.optJSONObject(0)?.let { a ->
                a.optString("address").takeIf { it.isNotEmpty() }?.let { ip -> if (a.has("mask")) "$ip/${a.optInt("mask")}" else ip }
            }
            rows += IfaceRow(name, dev, proto, up, v4)
            if (!up) continue
            val routes = o.optJSONArray("route") ?: JSONArray()
            var gw: String? = null
            var hasDefault = false
            for (k in 0 until routes.length()) {
                val r = routes.optJSONObject(k) ?: continue
                if (r.optString("target") == "0.0.0.0" && r.optInt("mask", -1) == 0) {
                    hasDefault = true
                    gw = r.optString("nexthop").takeIf { it.isNotEmpty() && it != "0.0.0.0" }
                }
            }
            if (!hasDefault) continue
            // Birden fazla varsayılan rota varsa gerçek hattı (wwan / wan) tercih et, VPN tünelini değil
            val score = when {
                dev.startsWith("wwan") || name.contains("wan", true) -> 3
                name.contains("kedi", true) || dev.startsWith("tun") || dev.startsWith("kediray") -> 0
                else -> 1
            }
            if (score <= wanScore) continue
            wanScore = score
            val v6 = o.optJSONArray("ipv6-address")?.optJSONObject(0)?.optString("address")?.takeIf { it.isNotEmpty() }
                ?: o.optJSONArray("ipv6-prefix-assignment")?.optJSONObject(0)?.optString("address")?.takeIf { it.isNotEmpty() }
            val dns = o.optJSONArray("dns-server")?.let { d -> (0 until d.length()).map { d.optString(it) }.filter { it.isNotEmpty() } }.orEmpty()
            wan = WanInfo(
                iface = name,
                device = dev,
                proto = proto,
                up = up,
                uptime = o.optLong("uptime", -1).takeIf { it >= 0 },
                ipv4 = v4?.substringBefore('/'),
                ipv6 = v6,
                gateway = gw,
                dns = dns,
            )
        }
        return NetDetails(wan, rows.sortedWith(compareByDescending<IfaceRow> { it.up }.thenBy { it.name }))
    }

    /** Sistem detayları: bellek / önbellek, açık bağlantı sayısı, depolama, OpenWrt ve çekirdek sürümü. */
    suspend fun sysDetails(): SysDetails {
        val info = client.call("system", "info")
        val mem = info.optJSONObject("memory")
        val total = mem?.optLong("total") ?: 0L
        val available = when {
            mem == null -> 0L
            mem.has("available") -> mem.optLong("available")
            else -> mem.optLong("free") + mem.optLong("buffered") + mem.optLong("cached")
        }
        val cached = (mem?.optLong("cached") ?: 0L) + (mem?.optLong("buffered") ?: 0L)
        // root / tmp değerleri KiB
        fun kb(o: JSONObject?, k: String) = o?.optLong(k, -1)?.takeIf { it >= 0 }?.let { it * 1024 }
        val root = info.optJSONObject("root")
        val tmp = info.optJSONObject("tmp")
        val board = try { client.call("system", "board") } catch (e: UbusException) { null }
        val rel = board?.optJSONObject("release")
        val release = rel?.let {
            listOf(it.optString("description").ifEmpty { it.optString("distribution") + " " + it.optString("version") }, it.optString("revision"))
                .filter { s -> s.isNotBlank() }.joinToString(" ").trim().ifEmpty { null }
        }
        suspend fun readNum(path: String): Int? = try {
            client.call(
                "file", "exec",
                JSONObject().put("command", "/bin/cat").put("params", JSONArray().put(path)),
            ).optString("stdout").trim().toIntOrNull()
        } catch (e: UbusException) {
            null
        }
        return SysDetails(
            memUsed = (total - available).coerceAtLeast(0L),
            memTotal = total,
            cached = cached,
            conn = readNum("/proc/sys/net/netfilter/nf_conntrack_count"),
            connMax = readNum("/proc/sys/net/netfilter/nf_conntrack_max"),
            rootFree = kb(root, "free"),
            rootTotal = kb(root, "total"),
            tmpFree = kb(tmp, "free"),
            tmpTotal = kb(tmp, "total"),
            release = release,
            kernel = board?.optString("kernel")?.ifEmpty { null },
            model = board?.optString("model")?.ifEmpty { null },
            cpu = board?.optString("system")?.ifEmpty { null },
        )
    }

    /** /proc/stat'ı iki ölçüm arasındaki farkla CPU yüzdesine çevirir. */
    private suspend fun cpuPercent(): Int? {
        val out = try {
            client.call(
                "file", "exec",
                JSONObject().put("command", "/bin/cat").put("params", JSONArray().put("/proc/stat")),
            ).optString("stdout")
        } catch (e: UbusException) {
            return null
        }
        val line = out.lineSequence().firstOrNull { it.startsWith("cpu ") } ?: return null
        val n = line.trim().split(Regex("\\s+")).drop(1).mapNotNull { it.toLongOrNull() }
        if (n.size < 4) return null
        val idle = n[3] + (n.getOrNull(4) ?: 0L)
        val total = n.sum()
        val prev = lastCpu
        lastCpu = total to idle
        if (prev == null) return null
        val dt = total - prev.first
        val di = idle - prev.second
        if (dt <= 0) return null
        return (((dt - di) * 100.0) / dt).roundToInt().coerceIn(0, 100)
    }

    suspend fun reboot() {
        client.call("system", "reboot")
    }

    // ---- Modemdata (qmodem çıktılarından genel sayfa) ------------------

    suspend fun modemdata(): List<ModemdataGroup> {
        val groups = linkedMapOf<String, MutableList<ModemdataItem>>()

        suspend fun load(method: String) {
            val res = try {
                client.call("qmodem", method, JSONObject().put("config_section", section()))
            } catch (e: UbusException) {
                return
            }
            val band = res.optJSONObject("current_band")
            if (band != null) {
                val cells = band.optJSONArray("cells")
                val list = groups.getOrPut("Bant bilgisi") { mutableListOf() }
                fun add(k: String, l: String, v: String, u: String = "") {
                    if (v.isNotEmpty()) list += ModemdataItem(k, l, v, u, null, "")
                }
                add("network_mode", "Ağ modu", band.optString("network_mode"))
                if (cells != null) {
                    for (i in 0 until cells.length()) {
                        val c = cells.getJSONObject(i)
                        val role = c.optString("role").uppercase()
                        val tag = if (cells.length() > 1) "$role ${i + 1}" else role
                        add("band$i", "$tag bant", c.optString("band_name").ifEmpty { c.optString("band") })
                        add("ch$i", "$tag ${c.optString("channel_type").ifEmpty { "Kanal" }}", c.optString("channel"))
                        add("pci$i", "$tag PCI", c.optString("pci"))
                        add("dl$i", "$tag DL bant genişliği", c.optString("dl_bandwidth"), "MHz")
                        add("ul$i", "$tag UL bant genişliği", c.optString("ul_bandwidth"), "MHz")
                    }
                }
                return
            }
            val arr = res.optJSONArray("modem_info")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val group = o.optString("class").ifEmpty { method }
                    val v = o.optString("value").toFloatOrNull()
                    val min = o.optString("min_value").toFloatOrNull()
                    val max = o.optString("max_value").toFloatOrNull()
                    val fraction = if (v != null && min != null && max != null && max > min) {
                        ((v - min) / (max - min)).coerceIn(0f, 1f)
                    } else {
                        null
                    }
                    groups.getOrPut(group) { mutableListOf() } += ModemdataItem(
                        key = o.optString("key"),
                        label = o.optString("full_name").ifEmpty { o.optString("key") },
                        value = o.optString("value"),
                        unit = o.optString("unit"),
                        fraction = fraction,
                        extra = o.optString("extra_info"),
                    )
                }
            } else {
                // modem_info olmayan çağrılarda (ör. get_imei) düz alanları listele
                for (k in res.keys()) {
                    val v = res.opt(k)
                    if (v is String || v is Number || v is Boolean) {
                        groups.getOrPut("Diğer") { mutableListOf() } +=
                            ModemdataItem(k, k, v.toString(), "", null, "")
                    }
                }
            }
        }

        for (m in listOf("base_info", "sim_info", "cell_info", "network_info", "get_current_band")) {
            load(m)
        }
        return groups.map { ModemdataGroup(it.key, it.value) }
    }

    // ---- VPN (xwrt) -----------------------------------------------------

    suspend fun vpnConnect(profileId: String) {
        client.call("xwrt", "connect", JSONObject().put("id", profileId))
    }

    /** Ayarlardaki aktif profil kimliği ve kayıtlı tüm profiller (gizli alanlar okunmaz). */
    suspend fun vpnProfiles(): Pair<String?, List<VpnProfile>> {
        val o = client.call("xwrt", "config")
        val active = o.optJSONObject("settings")?.optString("active")?.takeIf { it.isNotEmpty() }
        val arr = o.optJSONArray("profiles")
        val list = if (arr == null) {
            emptyList()
        } else {
            (0 until arr.length()).map {
                val p = arr.getJSONObject(it)
                VpnProfile(
                    id = p.optString("id"),
                    name = p.optString("name").ifEmpty { p.optString("id") },
                    proto = p.optString("proto"),
                    address = p.optString("address"),
                    port = p.optInt("port"),
                    network = p.optString("network"),
                    security = p.optString("security"),
                    sni = p.optString("sni"),
                )
            }
        }
        return active to list
    }

    /** Düzenleme için profilin tüm alanlarıyla ham hali (gizli alanlar dahil, yalnızca bellekte). */
    suspend fun vpnProfileRaw(id: String): JSONObject {
        val arr = client.call("xwrt", "config").optJSONArray("profiles")
            ?: throw UbusException("Profil listesi boş")
        for (i in 0 until arr.length()) {
            val p = arr.getJSONObject(i)
            if (p.optString("id") == id) return p
        }
        throw UbusException("Profil bulunamadı")
    }

    suspend fun vpnUpdateProfile(id: String, profile: JSONObject) {
        client.call("xwrt", "update_profile", JSONObject().put("id", id).put("profile", profile))
    }

    suspend fun vpnAddProfile(profile: JSONObject) {
        client.call("xwrt", "add_profile", JSONObject().put("profile", profile))
    }

    suspend fun vpnImport(uri: String) {
        client.call("xwrt", "import", JSONObject().put("uri", uri))
    }

    suspend fun vpnDeleteProfile(id: String) {
        client.call("xwrt", "delete_profile", JSONObject().put("id", id))
    }

    /** Gecikmeyi ms olarak döndürür; sayı bulunamazsa hata fırlatır. */
    suspend fun vpnPing(id: String): Int {
        val o = client.call("xwrt", "ping", JSONObject().put("id", id))
        val key = Regex("ms|latency|delay|rtt", RegexOption.IGNORE_CASE)
        var fallback: Number? = null
        for (k in o.keys()) {
            val v = o.opt(k)
            if (v is Number) {
                if (key.containsMatchIn(k)) return v.toDouble().toInt()
                if (fallback == null) fallback = v
            }
        }
        fallback?.let { return it.toDouble().toInt() }
        throw UbusException("Ping sonucu okunamadı: ${o.toString().take(100)}")
    }

    // ---- Veri kullanımı (modemdeki aw1k-usage betiği) -------------------

    /** Betik kurulu değilse ubus nesnesi yoktur; bu durumda UbusException fırlar. */
    suspend fun usage(): UsageData? {
        val o = client.call("aw1k", "usage")
        val arr = o.optJSONArray("days") ?: return null
        val days = (0 until arr.length()).map {
            val d = arr.getJSONObject(it)
            UsageDay(d.optString("d"), d.optLong("rx"), d.optLong("tx"))
        }
        return UsageData(o.optString("today"), days)
    }

    suspend fun vpnDisconnect() {
        client.call("xwrt", "disconnect")
    }

    // ---- KediRay (modemdeki kendi VPN'imiz) -----------------------------

    /** Son okunan KediRay durumu (vpnState içinde güncellenir). */
    var kedi: KediState? = null
        private set
    private var kediLast: Triple<Long, Long, Long>? = null

    /** Okunmuş KediRay sürümü (sürüm, çekirdek); her yenilemede sorulmasın diye saklanır. */
    private var kediVer: Pair<String, String>? = null

    private suspend fun kediVersion(): Pair<String, String>? {
        kediVer?.let { return it }
        return try {
            val r = fileExec("/usr/bin/kediray", "version", "--json")
            val j = JSONObject(r.optString("stdout").trim())
            val v = j.optString("version").trim()
            if (v.isEmpty() || v == "?") null else (v to j.optString("core").trim()).also { kediVer = it }
        } catch (e: Exception) {
            null
        }
    }

    /** aw1k rpcd eklentisi kurulu değilse null. */
    private suspend fun kediray(): KediState? {
        val o = try {
            client.call("aw1k", "kediray")
        } catch (e: UbusException) {
            return null
        }
        val nowMs = System.currentTimeMillis()
        val rx = o.optLong("rx")
        val tx = o.optLong("tx")
        val last = kediLast
        kediLast = Triple(nowMs, rx, tx)
        var down = 0L
        var up = 0L
        if (last != null && nowMs > last.first && rx >= last.second && tx >= last.third) {
            val dt = (nowMs - last.first) / 1000.0
            down = ((rx - last.second) / dt).toLong()   // tünele giren paketler = indirme
            up = ((tx - last.third) / dt).toLong()
        }
        val since = o.optLong("since")
        val now = o.optLong("now")
        val uptime = if (since > 0 && now >= since) now - since else 0L

        val nodes = mutableListOf<KediNode>()
        var activeSec: String? = null
        try {
            val values = client.call("uci", "get", JSONObject().put("config", "kediray"))
                .optJSONObject("values")
            val sel = values?.optJSONObject("main")?.optString("node").orEmpty()
            if (sel.isNotEmpty() && sel != "auto") activeSec = sel
            // Liste sırası: kediray.main.node_order ("ad|adres:port" anahtarları); yoksa dosyadaki sıra
            val ord = mutableListOf<String>()
            when (val raw = values?.optJSONObject("main")?.opt("node_order")) {
                is org.json.JSONArray -> for (i in 0 until raw.length()) ord += raw.optString(i)
                is String -> if (raw.isNotEmpty()) ord += raw
            }
            values?.keys()?.forEach { name ->
                val n = values.optJSONObject(name) ?: return@forEach
                if (n.optString(".type") != "node") return@forEach
                nodes += KediNode(
                    section = name,
                    name = n.optString("remarks").ifEmpty { n.optString("address") },
                    protocol = n.optString("protocol"),
                    address = n.optString("address"),
                    port = n.optString("port"),
                    network = n.optString("network"),
                    tls = n.optString("tls"),
                    enabled = n.optString("enabled") != "0",
                    rawName = n.optString("remarks"),
                )
            }
            if (ord.isNotEmpty()) {
                val pos = HashMap<String, Int>()
                ord.forEachIndexed { i, k -> if (k !in pos) pos[k] = i }
                val idx = nodes.withIndex().associate { it.value.section to it.index }
                nodes.sortWith(compareBy<KediNode>(
                    { pos["${it.rawName}|${it.address}:${it.port}"] ?: 1_000_000 },
                    { idx[it.section] ?: 0 },
                ))
            }
            for (i in nodes.indices) nodes[i] = nodes[i].copy(order = i)
        } catch (e: UbusException) {
            // düğümler okunamadı; durum yine de gösterilir
        }
        if (activeSec == null && o.optBoolean("running")) {
            // Otomatik seçimde aktif sunucuyu KediRay'e sor
            try {
                val r = fileExec("/usr/bin/kediray", "status", "--json")
                activeSec = JSONObject(r.optString("stdout")).optJSONObject("active")?.optString("sec")?.ifEmpty { null }
            } catch (e: Exception) {
                // bilinmiyor; işaret gösterilmez
            }
        }
        // Aktif (seçili) sunucu her zaman listenin başında gösterilir
        activeSec?.let { a ->
            val i = nodes.indexOfFirst { it.section == a }
            if (i > 0) nodes.add(0, nodes.removeAt(i))
        }
        val kv = kediVersion()
        return KediState(
            running = o.optBoolean("running"),
            enabled = o.optBoolean("enabled"),
            uptimeSeconds = uptime,
            uplinkRate = up,
            downlinkRate = down,
            nodes = nodes,
            activeSection = activeSec,
            version = kv?.first,
            core = kv?.second?.ifEmpty { null },
        ).also { kedi = it }
    }

    /** Bir KediRay düğümünün düz (metin) alanları; düzenleyiciye verilir. */
    suspend fun kediNodeRaw(section: String): JSONObject {
        val values = client.call("uci", "get", JSONObject().put("config", "kediray").put("section", section))
            .optJSONObject("values") ?: throw UbusException("Düğüm okunamadı")
        val out = JSONObject()
        values.keys().forEach { k ->
            val v = values.opt(k)
            if (!k.startsWith(".") && (v is String || v is Number || v is Boolean)) out.put(k, v.toString())
        }
        return out
    }

    private fun stringValues(o: JSONObject): JSONObject {
        val v = JSONObject()
        o.keys().forEach { k -> if (!k.startsWith(".")) v.put(k, o.opt(k).toString()) }
        return v
    }

    private suspend fun kediCommit() {
        client.call("uci", "commit", JSONObject().put("config", "kediray"))
    }

    /** `kediray` komutunu çalıştırır; JSON çıktıda ok=false ise ilk hata iletisiyle fırlatır. */
    private suspend fun kediCli(vararg args: String): JSONObject {
        val r = fileExec("/usr/bin/kediray", *args)
        val out = r.optString("stdout").trim()
        val err = r.optString("stderr").trim()
        val j = try { JSONObject(out) } catch (e: Exception) { null }
        if (j != null && !j.optBoolean("ok", true)) {
            val e0 = j.optJSONArray("errors")?.optJSONObject(0)
            val msg = e0?.optString("msg").orEmpty().ifEmpty { "Kaydedilemedi" }
            val field = e0?.optString("field").orEmpty()
            throw UbusException(if (field.isNotEmpty()) "$field: $msg" else msg)
        }
        if (j == null && r.optInt("code") != 0) {
            throw UbusException(err.ifEmpty { out.ifEmpty { "Komut başarısız" } })
        }
        return j ?: JSONObject()
    }

    private suspend fun kediSaveNode(section: String, o: JSONObject) {
        val raw = o.optString("raw")
        if (raw.isNotEmpty()) {
            kediCli("node", "save", section, "json", raw, o.optString("remarks"))
        } else {
            kediCli("node", "save", section, "form", stringValues(o).toString())
        }
    }

    suspend fun kediUpdateNode(section: String, o: JSONObject) = kediSaveNode(section, o)

    suspend fun kediAddNode(o: JSONObject) = kediSaveNode("new", o)

    /** Sunucuyu listede komşusuyla yer değiştirir (`kediray node-move <bölüm> up|down`). */
    suspend fun kediMoveNode(section: String, up: Boolean, steps: Int = 1) {
        repeat(steps.coerceAtLeast(1)) { kediCli("node-move", section, if (up) "up" else "down") }
    }

    suspend fun kediDeleteNode(section: String) {
        kediCli("node", "del", section)
    }

    /** KediRay'in kullanacağı sunucuyu seçer (bağlantılar kopmadan geçer). */
    suspend fun kediUseNode(section: String) {
        kediCli("use", section)
    }

    /** vless/vmess/trojan/ss linki ya da Xray JSON ekler; modemin yanıtını döndürür. */
    suspend fun kediAddLink(link: String): String {
        val r = fileExec("/usr/bin/kediray", "add", link.trim())
        val out = r.optString("stdout").trim()
        if (r.optInt("code") != 0 || !Regex("\\d+ sunucu").containsMatchIn(out)) {
            throw UbusException(r.optString("stderr").trim().ifEmpty { out.ifEmpty { "Geçerli link veya JSON bulunamadı" } })
        }
        return out
    }

    /**
     * KediRay'in kendi ölçümü (`kediray ping <sunucu|all> --json`): modemin ölçüm adresi (Ayarlar > Ölçüm adresi,
     * yedek: Cloudflare cp.cloudflare.com) üzerinden tünel gecikmesi + sunucuya TCP gecikmesi.
     * [section] null ise tüm sunucular ölçülür. Dönen harita yalnızca ölçülen düğümleri içerir.
     */
    suspend fun kediPing(section: String?): Map<String, KediPing> {
        val r = fileExec("/usr/bin/kediray", "ping", section ?: "all", "--json")
        val out = r.optString("stdout").trim()
        val start = out.indexOf('[')
        if (start < 0) {
            throw UbusException(r.optString("stderr").trim().ifEmpty { "Ping sonucu alınamadı" })
        }
        val arr = JSONArray(out.substring(start))
        val res = linkedMapOf<String, KediPing>()
        for (i in 0 until arr.length()) {
            val n = arr.optJSONObject(i) ?: continue
            val sec = n.optString("sec")
            if (sec.isEmpty() || (section != null && sec != section)) continue
            val tunnel = if (n.has("ping") && !n.isNull("ping")) n.optInt("ping", -1) else null
            val rtt = if (n.has("rtt") && !n.isNull("rtt")) n.optInt("rtt", -1) else null
            if (tunnel == null && rtt == null) continue   // bu düğüm ölçülmedi (ör. kapalı düğüm)
            res[sec] = KediPing(
                tunnelMs = tunnel?.takeIf { it > 0 },
                rttMs = rtt?.takeIf { it > 0 },
            )
        }
        return res
    }

    // ---- KediRay ayarları (modemdeki Ayarlar sayfasının karşılığı) ------------

    /** `kediray.main` bölümündeki değerler + çekirdek ayarı modu bilgisi. */
    suspend fun kediSettings(): KediSettingsData {
        val v = client.call("uci", "get", JSONObject().put("config", "kediray").put("section", "main"))
            .optJSONObject("values") ?: throw UbusException("KediRay ayarları okunamadı")
        val map = linkedMapOf<String, Any>()
        v.keys().forEach { k ->
            if (k.startsWith(".")) return@forEach
            when (val x = v.opt(k)) {
                is JSONArray -> map[k] = (0 until x.length()).map { x.optString(it) }
                null -> {}
                else -> map[k] = x.toString()
            }
        }
        val own = try {
            val r = fileExec("/usr/bin/kediray", "dash", "info")
            val j = JSONObject(r.optString("stdout").trim())
            if (j.optBoolean("own")) j.optString("engine").ifEmpty { "çekirdek" } else null
        } catch (e: Exception) {
            null
        }
        return KediSettingsData(map, own)
    }

    /**
     * Değişen ayarları yazar ve uygular (LuCI'deki "Kaydet & Uygula" gibi): boş değerler silinir,
     * listeler dizi olarak yazılır; uygulama KediRay'i yeniden başlatır.
     */
    suspend fun kediSaveSettings(changes: Map<String, Any>) {
        if (changes.isEmpty()) return
        val set = JSONObject()
        val del = JSONArray()
        changes.forEach { (k, v) ->
            when (v) {
                is List<*> -> {
                    val items = v.map { it.toString().trim() }.filter { it.isNotEmpty() }
                    if (items.isEmpty()) del.put(k) else set.put(k, JSONArray(items))
                }
                else -> {
                    val s = v.toString().trim()
                    if (s.isEmpty()) del.put(k) else set.put(k, s)
                }
            }
        }
        if (del.length() > 0) {
            client.call(
                "uci", "delete",
                JSONObject().put("config", "kediray").put("section", "main").put("options", del),
            )
        }
        if (set.length() > 0) {
            client.call(
                "uci", "set",
                JSONObject().put("config", "kediray").put("section", "main").put("values", set),
            )
        }
        client.call("uci", "apply", JSONObject().put("rollback", false))
    }

    /** Bant kilidini kaldırır (tüm bantlar açılır). */
    suspend fun kediBandUnlock(): String {
        val r = fileExec("/usr/bin/kediray", "band", "unlock")
        return (r.optString("stdout").ifBlank { r.optString("stderr") }).trim().ifEmpty { "Tamam" }
    }

    /** Metin2 DNS testini arka planda başlatır. */
    suspend fun kediDnsTestStart() {
        fileExec("/usr/bin/kediray", "dnstest", "--bg")
    }

    /** DNS testi çıktısı: ilk satır durum (BITTI ise bitti), gerisi sonuç. */
    suspend fun kediDnsTestRead(): Pair<Boolean, String> {
        val txt = fileExec("/usr/bin/kediray", "dnstest", "--read").optString("stdout").trim()
        val nl = txt.indexOf('\n')
        val state = (if (nl < 0) txt else txt.substring(0, nl)).trim()
        val body = if (nl < 0) "" else txt.substring(nl + 1)
        return (state == "BITTI") to body
    }

    suspend fun kediSetNodeEnabled(section: String, on: Boolean) {
        kediCli("node-enable", section, if (on) "1" else "0")
    }

    // ---- KediRay güncelleme (uygulamadan .sh yükle) ---------------------------

    private suspend fun fileExec(command: String, vararg args: String): JSONObject =
        client.call(
            "file", "exec",
            JSONObject().put("command", command).put("params", JSONArray().also { a -> args.forEach { a.put(it) } }),
        )

    /** fileExec ile aynı, ama modemin dosya indirdiği uzun çağrılar için okuma süresi 180 sn. */
    private suspend fun fileExecLong(command: String, vararg args: String): JSONObject =
        client.call(
            "file", "exec",
            JSONObject().put("command", command).put("params", JSONArray().also { a -> args.forEach { a.put(it) } }),
            longWait = true,
        )

    /** Kurulum dosyasını modeme yükler (parça parça, /tmp/kediray-update.sh'a eklenerek) ve `kediray selfupdate` ile başlatır. Dönen metin: modemin yanıtı. */
    suspend fun kediUploadAndStart(bytes: ByteArray): String {
        val head = String(bytes, 0, minOf(bytes.size, 600), Charsets.UTF_8)
        val text = String(bytes, Charsets.UTF_8)
        require(head.contains("KediRay") && text.contains("\nKR_VERSION=\"")) {
            "Bu dosya bir KediRay kurulum dosyası gibi görünmüyor"
        }
        val chunk = 24000
        var i = 0
        var first = true
        while (i < bytes.size) {
            val end = minOf(i + chunk, bytes.size)
            val b64 = android.util.Base64.encodeToString(bytes.copyOfRange(i, end), android.util.Base64.NO_WRAP)
            client.call(
                "file", "write",
                JSONObject().put("path", "/tmp/kediray-update.sh").put("data", b64).put("base64", true)
                    .put("append", !first),
            )
            first = false
            i = end
        }
        kediVer = null   // güncelleme sonrası sürüm yeniden okunur
        val s = fileExec("/usr/bin/kediray", "selfupdate", "/tmp/kediray-update.sh")
        val out = (s.optString("stdout") + s.optString("stderr")).trim()
        if (s.optInt("code") != 0) throw UbusException(out.ifEmpty { "Güncelleme başlatılamadı" })
        return out
    }

    /** GitHub'da yeni sürüm var mı? Modem kontrol eder (`kediray update check --json`). */
    suspend fun kediUpdateCheck(): UpdateCheck {
        val r = fileExec("/usr/bin/kediray", "update", "check", "--json")
        val out = r.optString("stdout").trim()
        if (!out.startsWith("{")) {
            throw UbusException(r.optString("stderr").trim().ifEmpty { "Güncelleme denetlenemedi (KediRay 1.19.36 veya yeni mi?)" })
        }
        val j = JSONObject(out)
        return UpdateCheck(
            ok = j.optBoolean("ok"),
            current = j.optString("current"),
            latest = j.optString("latest"),
            newer = j.optBoolean("newer"),
            error = j.optString("error"),
        )
    }

    /** Kurulum bittiyse "güncelleme yüklendi" bilgisini verir (yoksa null). Eski KediRay'de komut yoksa null döner. */
    suspend fun kediWhatsNew(): WhatsNew? {
        val r = fileExec("/usr/bin/kediray", "update", "whatsnew")
        val out = r.optString("stdout").trim()
        if (!out.startsWith("{")) return null
        val j = JSONObject(out)
        // Kurulum henüz bitmedi: sonra tekrar sorulsun (hata sayılır, 10 sn sonra yeniden denenir)
        if (j.optBoolean("installing")) throw UbusException("kurulum sürüyor")
        if (!j.optBoolean("pending")) return null
        val arr = j.optJSONArray("changes")
        val ch = buildList { if (arr != null) for (i in 0 until arr.length()) add(arr.optString(i)) }.filter { it.isNotBlank() }
        return WhatsNew(from = j.optString("from"), to = j.optString("to"), changes = ch)
    }

    /** "Güncelleme yüklendi" bilgisini gördüm diye işaretler (bir daha gösterilmez). */
    suspend fun kediWhatsNewAck() {
        fileExec("/usr/bin/kediray", "update", "whatsnew", "ack")
    }

    /** GitHub'dan indirir, SHA256 ile doğrular ve kurulumu başlatır (`kediray update install`). */
    suspend fun kediUpdateInstall(): String {
        kediVer = null
        val r = fileExecLong("/usr/bin/kediray", "update", "install")
        val out = (r.optString("stdout") + "\n" + r.optString("stderr")).trim()
        if (r.optInt("code") != 0) throw UbusException(out.ifEmpty { "Güncelleme başlatılamadı" })
        return out
    }

    /** Dönen: durum ("DONE 0" / "RUNNING" ...), adım sayısı, adım adı, günlük. */
    suspend fun kediUpdateLog(): UpdateLog {
        val r = fileExec("/usr/bin/kediray", "selfupdate", "log")
        val lines = r.optString("stdout").split("\n").toMutableList()
        val st = if (lines.isNotEmpty()) lines.removeAt(0) else ""
        val sl = if (lines.isNotEmpty()) lines.removeAt(0) else ""
        val m = Regex("^STEPS (\\d+)\\|(.*)$").find(sl)
        return UpdateLog(
            status = st.trim(),
            steps = m?.groupValues?.get(1)?.toIntOrNull() ?: 0,
            label = m?.groupValues?.get(2)?.trim().orEmpty(),
            log = lines.joinToString("\n").trim(),
        )
    }

    /**
     * Sunucu sertifikasını şimdi elle sabitler (`kediray pin`). Sertifika değiştiyse modem tüneli yeniden başlatır.
     * Dönen metin: modemin yanıtı (ne yapıldığı). Sertifika okunamazsa UbusException fırlatır.
     */
    suspend fun kediPin(): String {
        val r = fileExec("/usr/bin/kediray", "pin")
        val out = (r.optString("stdout") + "\n" + r.optString("stderr")).trim().lines().filter { it.isNotBlank() }.joinToString(" · ")
        if (r.optInt("code") != 0) throw UbusException(out.ifEmpty { "Sertifika sabitlenemedi" })
        return out.ifEmpty { "Tamam" }
    }

    /** KediRay / Xray / hev günlüğü (`kediray logs`): seviye ve bileşene ayrılmış satırlar + gruplanmış hatalar. */
    suspend fun kediLogs(): LogData {
        val r = fileExec("/usr/bin/kediray", "logs")
        val out = r.optString("stdout").trim()
        require(out.startsWith("{")) { r.optString("stderr").ifBlank { "KediRay günlüğü okunamadı (KediRay kurulu mu?)" } }
        val j = JSONObject(out)
        val clock = Regex("\\d{2}:\\d{2}:\\d{2}")
        val fmt = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
        fun whenOf(ts: String?, t: Long): String {
            val m = ts?.let { clock.find(it)?.value }
            if (m != null) return m
            return if (t > 0) fmt.format(java.util.Date(t)) else ""
        }
        val ents = j.optJSONArray("entries")
        val entries = (0 until (ents?.length() ?: 0)).map { i ->
            val e = ents!!.getJSONObject(i)
            LogEntry(
                id = e.optLong("id"),
                time = whenOf(e.optString("ts").takeIf { it.isNotEmpty() && it != "null" }, e.optLong("t")),
                level = e.optString("level", "info"),
                comp = e.optString("comp", "Xray"),
                text = e.optString("text"),
                raw = e.optString("raw"),
                title = e.optString("title").takeIf { it.isNotEmpty() && it != "null" },
                fix = e.optString("fix").takeIf { it.isNotEmpty() && it != "null" },
            )
        }
        val errs = j.optJSONArray("errors")
        val errors = (0 until (errs?.length() ?: 0)).map { i ->
            val e = errs!!.getJSONObject(i)
            LogError(
                comp = e.optString("comp", "Xray"),
                title = e.optString("title").takeIf { it.isNotEmpty() && it != "null" },
                fix = e.optString("fix").takeIf { it.isNotEmpty() && it != "null" },
                count = e.optInt("count", 1),
                last = whenOf(e.optString("ts").takeIf { it.isNotEmpty() && it != "null" }, e.optLong("last")),
                text = e.optString("text"),
            )
        }
        return LogData(entries, errors)
    }

    /** kind: clear-stream (akışı temizle) ya da clear-errors (gruplanmış hataları sil). */
    suspend fun kediLogsClear(kind: String) {
        fileExec("/usr/bin/kediray", "logs", kind)
    }

    /** action: start, stop ya da restart. */
    suspend fun kedirayControl(action: String) {
        client.call("aw1k", "kediray_ctl", JSONObject().put("action", action))
    }

    /** Modemde hız testini başlatır (aw1k-speedtest, KediRay 1.20.2+). */
    suspend fun speedtestStart() {
        client.call("aw1k", "speedtest_start")
    }

    suspend fun speedtestStop() {
        client.call("aw1k", "speedtest_stop")
    }

    suspend fun speedtestStatus(): SpeedStatus = SpeedStatus.from(client.call("aw1k", "speedtest_status"))

    suspend fun vpnState(): VpnState? {
        val k = kediray()
        if (k != null && k.running) {
            return VpnState(true, k.activeNode, "kediray", k.uptimeSeconds, k.uplinkRate, k.downlinkRate, null, "KediRay")
        }
        val o = try {
            client.call("xwrt", "status")
        } catch (e: UbusException) {
            return if (k != null) VpnState(false, k.activeNode, "kediray", 0, 0, 0, null, "KediRay") else null
        }
        val stats = o.optJSONObject("stats")
        val fault = o.optJSONArray("core_faults")?.optJSONObject(0)?.let {
            "${it.optString("source")}: ${it.optString("message")} (${it.optInt("count")}×)"
        }
        return VpnState(
            connected = o.optBoolean("connected"),
            profileName = o.optString("profile_name"),
            profileId = o.optString("profile_id"),
            uptimeSeconds = o.optLong("uptime_seconds"),
            uplinkRate = stats?.optLong("uplink_rate") ?: 0L,
            downlinkRate = stats?.optLong("downlink_rate") ?: 0L,
            fault = fault,
        )
    }
}
