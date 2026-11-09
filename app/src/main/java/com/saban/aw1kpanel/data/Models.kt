package com.saban.aw1kpanel.data

data class Connection(val host: String, val user: String, val password: String)

data class SignalItem(
    val label: String,
    val value: String,
    val unit: String,
    val fraction: Float?,
)

data class ModemState(
    val operatorName: String = "—",
    val networkMode: String = "—",
    val connected: Boolean = false,
    val rxRate: String = "—",
    val txRate: String = "—",
    val signal: List<SignalItem> = emptyList(),
    val modemName: String = "—",
    val temperature: String = "—",
    /** RSRP, SINR ve RSRQ'dan hesaplanan 0-100 arası kalite; ölçüm yoksa null. */
    val signalPercent: Int? = null,
    /** Taşıyıcı birleştirme sayısı (LTE-A için); bilinmiyorsa 1. */
    val carriers: Int = 1,
    val country: String = "—",
    /** Mobil bağlantının süresi (sn), gönderilen ve alınan bayt; bilinmiyorsa null. */
    val connSeconds: Long? = null,
    val sentBytes: Long? = null,
    val recvBytes: Long? = null,
    val cells: List<CellRow> = emptyList(),
)

/** Bir taşıyıcının (PCC / SCC) hücre bilgisi. */
data class CellRow(
    val role: String,
    val band: String,
    val bandwidth: String,
    val pci: String,
    val earfcn: String,
    val rssi: String,
    val rsrp: String,
    val rsrq: String,
    val sinr: String,
)

data class WifiNetwork(
    val section: String,
    val ssid: String,
    val radio: String,
    val enabled: Boolean,
    val encryption: String,
)

data class Device(
    val hostname: String,
    val ip: String,
    val mac: String,
    /** KediRay yönü: vpn | direct | block (kayıt yoksa varsayılan). */
    val mode: String = "vpn",
    /** Wi-Fi'de bağlı kalma süresi (sn); bilinmiyorsa null (kablolu vb.). */
    val connectedSecs: Long? = null,
)

/** Bir cihazın bugünkü toplam (indirme + yükleme) veri kullanımı; KediRay sayaçlarından. */
/** [rx] cihaza gelen (indirme), [tx] cihazdan giden (yükleme) bugünkü bayt; [bytes] ikisinin toplamı. */
data class DeviceShare(val name: String, val ip: String, val bytes: Long, val rx: Long = 0L, val tx: Long = 0L)

/** Bir günün KediRay sayacı: VPN ve direkt çıkan trafik (indirme/yükleme). */
data class TrafficDay(val day: String, val vpnRx: Long, val vpnTx: Long, val dirRx: Long, val dirTx: Long)

/** `kediray stats` çıktısından: cihaz payları + son 30 günün VPN/direkt dökümü. */
data class KediStats(val shares: DeviceShares, val days: List<TrafficDay>)

/** Cihaz payları: listedeki cihazlar + ağdan ayrılmış cihazların bugünkü toplamı. */
data class DeviceShares(val devices: List<DeviceShare>, val othersBytes: Long)

data class VpnState(
    val connected: Boolean,
    val profileName: String,
    val profileId: String,
    val uptimeSeconds: Long,
    val uplinkRate: Long,
    val downlinkRate: Long,
    val fault: String?,
    /** Hangi VPN çalışıyor: "xDivine-Ray" (xwrt) ya da "KediRay". */
    val provider: String = "xDivine-Ray",
)

data class VpnProfile(
    val id: String,
    val name: String,
    val proto: String,
    val address: String,
    val port: Int,
    val network: String,
    val security: String,
    val sni: String,
)

data class UsageDay(val date: String, val rx: Long, val tx: Long)

/** Modemdeki kayıt betiğinin verdiği günlük kullanım. [today] modemin kendi tarihidir. */
data class UsageData(val today: String, val days: List<UsageDay>)

data class Router(
    val id: String,
    val name: String,
    val host: String,
    val user: String,
    val password: String,
)

data class SystemState(
    /** /proc/stat örneklerinden hesaplanır; ilk ölçümde null. */
    val cpuPercent: Int?,
    val load1: Float,
    val memPercent: Int,
    val memUsed: Long,
    val memTotal: Long,
    val uptimeSeconds: Long,
)

data class ModemdataItem(
    val key: String,
    val label: String,
    val value: String,
    val unit: String,
    val fraction: Float?,
    val extra: String,
)

data class ModemdataGroup(val title: String, val items: List<ModemdataItem>)

/** Bir Wi-Fi ağının (wifi-iface) ve bağlı olduğu radyonun (wifi-device) düzenlenebilir alanları. */
data class WifiDetail(
    val section: String,
    val ssid: String,
    val key: String,
    val encryption: String,
    val hidden: Boolean,
    val isolate: Boolean,
    val radio: String,
    val band: String,
    val channel: String,
    val htmode: String,
    val txpower: String,
    val country: String,
)

/** VPN profili ping sonucu; [ms] null ise başarısız. */
data class PingResult(val ms: Int?)

/**
 * KediRay düğümü ölçümü (`kediray ping`): [tunnelMs] tünelden tam web isteği (ölçüm adresi; null/eksi = ulaşılamadı),
 * [rttMs] modemden sunucuya TCP bağlantı süresi (null = ölçülemedi).
 */
data class KediPing(val tunnelMs: Int?, val rttMs: Int?)

data class KediNode(
    val section: String,
    val name: String,
    val protocol: String,
    val address: String,
    val port: String,
    val network: String,
    val tls: String,
    val enabled: Boolean,
)

data class KediState(
    val running: Boolean,
    val enabled: Boolean,
    val uptimeSeconds: Long,
    val uplinkRate: Long,
    val downlinkRate: Long,
    val nodes: List<KediNode>,
    /** KediRay'in şu an kullandığı sunucunun bölüm adı (main.node ya da otomatik seçilen). */
    val activeSection: String? = null,
    /** Modemdeki KediRay paneli sürümü (`kediray version --json`); okunamazsa null. */
    val version: String? = null,
    /** Xray çekirdeği sürümü; okunamazsa null. */
    val core: String? = null,
) {
    val activeNode: String get() = (nodes.firstOrNull { it.section == activeSection } ?: nodes.firstOrNull { it.enabled } ?: nodes.firstOrNull())?.name ?: "KediRay"
}

/** Wi-Fi'ye bağlı bir istemci (hostapd). */
data class WifiClient(
    val mac: String,
    val section: String,
    val ssid: String,
    val band: String,
    val signal: Int,
    val rxBytes: Long,
    val txBytes: Long,
    val rxMbps: Int,
    val txMbps: Int,
    val secs: Long?,
)

/** Misafir Wi-Fi durumu (aw1k eklentisi). */
data class GuestWifi(
    val exists: Boolean,
    val enabled: Boolean = false,
    val ssid: String = "",
    val key: String = "",
)

/** Band kilidi: bir teknolojinin (UMTS / LTE / NR / NR_NSA) seçilebilir ve şu an kilitli bantları. */
data class BandGroup(
    val available: List<Pair<String, String>>,
    val locked: Set<String>,
)

/** `kediray selfupdate log` çıktısı. */
data class UpdateLog(val status: String, val steps: Int, val label: String, val log: String)

/** GitHub'da yeni KediRay sürümü var mı (`kediray update check --json`). */
data class UpdateCheck(val ok: Boolean, val current: String, val latest: String, val newer: Boolean, val error: String)

/** `kediray logs` çıktısındaki tek satır. level: error | warn | info | debug. */
data class LogEntry(
    val id: Long,
    val time: String,
    val level: String,
    val comp: String,
    val text: String,
    val raw: String,
    val title: String?,
    val fix: String?,
)

/** Aynı hataların gruplanmış hali (sayaç + ne yapmalı). */
data class LogError(
    val comp: String,
    val title: String?,
    val fix: String?,
    val count: Int,
    val last: String,
    val text: String,
)

data class LogData(val entries: List<LogEntry>, val errors: List<LogError>)

/** İnternet (WAN) arayüzü: varsayılan rotası olan ve açık olan arayüz. */
data class WanInfo(
    val iface: String,
    val device: String,
    val proto: String,
    val up: Boolean,
    val uptime: Long?,
    val ipv4: String?,
    val ipv6: String?,
    val gateway: String?,
    val dns: List<String>,
)

/** `network.interface dump` satırı. */
data class IfaceRow(val name: String, val device: String, val proto: String, val up: Boolean, val ipv4: String?)

data class NetDetails(val wan: WanInfo?, val ifaces: List<IfaceRow>)

/** Ana sayfa Sistem kartındaki "Detaylar": bellek, bağlantı, depolama ve sürüm bilgileri (bayt). */
data class SysDetails(
    val memUsed: Long,
    val memTotal: Long,
    val cached: Long,
    val conn: Int?,
    val connMax: Int?,
    val rootFree: Long?,
    val rootTotal: Long?,
    val tmpFree: Long?,
    val tmpTotal: Long?,
    val release: String?,
    val kernel: String?,
    val model: String?,
    val cpu: String?,
)

/** Gelen kutusundaki bir SMS. Uzun mesajlar parçalı gelir; aynı gönderen ve saatteki parçalar birleştirilir ([indexes] hepsini tutar). */
data class SmsMessage(val indexes: List<Int>, val sender: String, val timestamp: Long, val text: String)
