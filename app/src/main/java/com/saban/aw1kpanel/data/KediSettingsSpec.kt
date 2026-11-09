package com.saban.aw1kpanel.data

/**
 * KediRay Ayarlar sayfasının (luci-app-kediray settings.js) uygulamadaki karşılığı.
 * Anahtarlar, varsayılanlar, seçenekler ve açıklamalar modemdeki panelle birebir aynıdır;
 * değerler `uci kediray.main` bölümüne yazılır.
 */
enum class KOptType { FLAG, LIST, VALUE, DYNLIST }

data class KChoice(val value: String, val label: String)

data class KOpt(
    val key: String,
    val title: String,
    val type: KOptType,
    val desc: String = "",
    val default: String = "",
    val choices: List<KChoice> = emptyList(),
    /** Bu ayar yalnızca [dependsOn] anahtarı [dependsValue] değerindeyken görünür. */
    val dependsOn: String? = null,
    val dependsValue: String? = null,
    /** Sayı aralığı (dahil). */
    val min: Int? = null,
    val max: Int? = null,
    val placeholder: String = "",
    /** "port", "ip4", "repo" gibi ek doğrulama. */
    val check: String? = null,
    /** Seçenek listesi sunucu listesinden doldurulur. */
    val nodeList: Boolean = false,
    /** Önemli uyarı (ör. TCP Mux ve oyunlar). */
    val warn: String? = null,
)

data class KTab(val id: String, val title: String, val opts: List<KOpt>)

/** Uci'den okunan değerler: tek değerler String, listeler List<String>. */
data class KediSettingsData(
    val values: Map<String, Any>,
    /** Çekirdek ayarı modu açıksa (mihomo/sing-box kendi dosyasıyla) motor adı; değilse null. */
    val ownEngine: String?,
)

private fun c(v: String, l: String) = KChoice(v, l)

object KediSettingsSpec {

    val tabs: List<KTab> = listOf(
        KTab(
            "general", "Genel",
            listOf(
                KOpt(
                    "enabled", "VPN açık · açılışta otomatik bağlan", KOptType.FLAG,
                    "İşaretliyken VPN şimdi çalışır ve modem her açıldığında internet gelince kendiliğinden bağlanır (seçili sunucuya, \"Sunucu: Otomatik\" ise en hızlısına). Kapatırsan VPN durur ve açılışta başlamaz; internet doğrudan çıkar.",
                    default = "0",
                ),
                KOpt(
                    "mode", "Yönlendirme modu", KOptType.LIST, default = "global",
                    choices = listOf(
                        c("global", "Tümü VPN (yerel ağ hariç)"),
                        c("list", "Sadece seçilenler VPN — Kurallar ve VPN'e ayarlı uygulamalar"),
                    ),
                ),
                KOpt(
                    "node", "Sunucu", KOptType.LIST,
                    "Sunucu değiştirmek için VPN sekmesinde sunucu kartına dokunmak bağlantıları koparmadan geçer.",
                    default = "auto", nodeList = true,
                    choices = listOf(c("auto", "Otomatik — en hızlı, düşerse diğerine geç")),
                ),
                KOpt(
                    "connect_ip", "Sunucuya IP ile bağlan", KOptType.FLAG,
                    "Adreste alan adı olsa bile bir kez IP'ye çevrilir ve doğrudan IP'ye bağlanılır; alan adı SNI/Host olarak kalır. DNS sorunlarından etkilenmez.",
                    default = "1",
                ),
                KOpt(
                    "capture", "Trafik yakalama modu", KOptType.LIST,
                    "Mixed: TCP çekirdekten, UDP TUN'dan. TUN: her şey TUN'dan geçer; bazı hatlarda sayfalar hızlanır, UDP biraz gecikir. TPROXY: TCP ve UDP çekirdekte. Redirect: yalnız TCP. Paketler yoksa otomatik TPROXY'ye düşer (apk add hev-socks5-tunnel kmod-tun).",
                    default = "mixed",
                    choices = listOf(
                        c("mixed", "Mixed — TCP çekirdekte, UDP hev ile  [önerilen]"),
                        c("tun", "TUN — her şey (TCP+UDP) hev üzerinden"),
                        c("tproxy", "TPROXY — TCP ve UDP çekirdekte"),
                        c("redirect", "Redirect — sadece TCP (UDP/oyun/arama direkt)"),
                    ),
                ),
                KOpt(
                    "default_device", "Listede olmayan cihazlar", KOptType.LIST, default = "vpn",
                    choices = listOf(
                        c("vpn", "VPN kurallarını kullansın"),
                        c("direct", "Direkt çıksın (sadece listedekiler VPN)"),
                    ),
                ),
                KOpt(
                    "failsafe", "VPN çekirdeği çökerse", KOptType.LIST, default = "killswitch",
                    choices = listOf(
                        c("killswitch", "Kill switch — VPN trafiğini kes (WhatsApp asla direkt çıkmaz)"),
                        c("open", "İnterneti direkt aç"),
                    ),
                ),
                KOpt(
                    "lan", "LAN arayüzleri", KOptType.DYNLIST,
                    "Trafiği yakalanacak ağlar (misafir ağı eklemek için guest yaz).",
                    default = "lan", placeholder = "örn. guest",
                ),
                KOpt(
                    "update_check", "Güncellemeleri kontrol et", KOptType.FLAG,
                    "Her gece 01:00'de GitHub'da yeni bir sürüm çıkmış mı diye sorar ve Hakkında sayfasında söyler. Sen düğmeye basmadan hiçbir şey kurulmaz.",
                    default = "1",
                ),
                KOpt(
                    "update_auto_install", "Otomatik kur", KOptType.FLAG,
                    "Gece denetiminde yeni sürüm çıkmışsa kendiliğinden indirilir (SHA256 ile doğrulanır) ve kurulur; kurulum sırasında VPN kısa süre yeniden başlar. Ayarların ve sunucuların korunur. Kapalıysa yalnızca haber verilir.",
                    default = "0", dependsOn = "update_check", dependsValue = "1",
                ),
                KOpt(
                    "stats", "Geliştirmeye yardımcı ol", KOptType.FLAG,
                    "Açıksa günde bir kez anonim kullanım sayısı gönderilir: rastgele bir kurulum kimliği, KediRay sürümü ve uygulama adı. Sunucu bilgilerin, trafiğin, adın ya da IP adresin kaydedilmez. Kapatınca kimlik silinir.",
                    default = "0",
                ),
                KOpt(
                    "update_repo", "Güncelleme kaynağı", KOptType.VALUE,
                    "Sürümlerin alınacağı GitHub deposu, sahip/depo biçiminde. Yalnızca bir fork'tan kuruyorsan değiştir; boş bırakırsan varsayılan kullanılır.",
                    placeholder = "sabanvurgun03-ui/kediray", check = "repo",
                ),
            ),
        ),
        KTab(
            "dns", "DNS",
            listOf(
                KOpt(
                    "dns_mode", "DNS modu", KOptType.LIST,
                    "\"Tünelden\": sorgular tünel içinden gider, operatöre sızmaz; TUN modunda tek çalışan seçenektir. \"Direkt\": tünele girmez, tünel kopsa da çalışır; siteler operatörün DNS'ine görünür. \"DNS'e dokunma\": KediRay ad çözümlemeye karışmaz; yalnız sorun gidermek için.",
                    default = "private",
                    choices = listOf(
                        c("private", "Tünelden  [önerilen]"),
                        c("fast", "Direkt — çekirdekten ama tünele girmeden"),
                        c("off", "DNS'e dokunma — modemin kendi DNS'i kalır"),
                    ),
                ),
                KOpt(
                    "dns_remote", "Uzak DNS (VPN üzerinden)", KOptType.VALUE,
                    "Tünelden çözülen sorguların gittiği sunucu. Varsayılan Google TCP: en uyumlusu. Tünelde sayfalar askıda kalıyorsa (günlükte \"closed pipe\") Cloudflare ya da Google DoH'a geç. Adres IP olmalı.",
                    default = "tcp://8.8.8.8",
                    choices = listOf(
                        c("tcp://8.8.8.8", "Google, TCP  [önerilen]"),
                        c("https://1.1.1.1/dns-query", "Cloudflare DoH"),
                        c("https://8.8.8.8/dns-query", "Google DoH"),
                        c("tcp://1.1.1.1", "Cloudflare, TCP"),
                    ),
                ),
                KOpt(
                    "dns_direct", "Direkt DNS (Türkiye/direkt alan adları)", KOptType.VALUE,
                    "auto = modeme operatörden gelen DNS (önerilen): her zaman ayakta ve tünele girmez. Bazı hatlarda DoH adreslerine hiç ulaşılamıyor ve her sorgu saniyelerce bekleyip sayfayı kilitliyor.",
                    default = "auto",
                    choices = listOf(
                        c("auto", "Otomatik — operatörün DNS sunucusu"),
                        c("https+local://1.1.1.1/dns-query", "Cloudflare DoH (sansürsüz, direkt)"),
                        c("https+local://8.8.8.8/dns-query", "Google DoH (direkt)"),
                    ),
                ),
                KOpt(
                    "dns_hijack", "Cihazların DNS'ini çekirdekte yakala", KOptType.FLAG,
                    "Cihaz kendi DNS'ini (8.8.8.8 gibi) yazsa bile sorgu modemin DNS'ine yönlendirilir. Kapatmayın: açıkken sayfalar belirgin şekilde hızlı açılır (205 ms yerine 1 ms).",
                    default = "1",
                ),
                KOpt(
                    "block_doh", "Tarayıcıların kendi DNS'ini (DoH) engelle", KOptType.FLAG,
                    "Chrome/Firefox \"Güvenli DNS\" bu hatta askıda kalabiliyor; açıkken bu istekler anında reddedilir. Varsayılan KAPALI: açıkken telefonun \"Özel DNS\" ayarı da reddedildiği için Speedtest gibi uygulamalar \"Check your connection\" uyarısı verebilir. Sayfalar askıda kalıyorsa aç.",
                    default = "0",
                ),
                KOpt(
                    "dns_direct2", "Direkt DNS (yedek)", KOptType.VALUE,
                    "İlki yanıt vermezse buraya düşer. auto = operatörün ikinci DNS sunucusu.",
                    default = "auto",
                    choices = listOf(
                        c("auto", "Otomatik — operatörün ikinci DNS'i"),
                        c("https+local://8.8.8.8/dns-query", "Google DoH (direkt)"),
                        c("https+local://1.1.1.1/dns-query", "Cloudflare DoH (direkt)"),
                        c("", "Yedek yok"),
                    ),
                ),
                KOpt(
                    "dns_stale", "Tünel takılırsa önbellekten yanıt ver", KOptType.FLAG,
                    "Mobil hatta tünel anlık koparsa bilinen adresler beklemeden verilir, arkada yenilenir (Xray serveStale). Uygulamalar \"DNS zaman aşımı\" yaşamaz.",
                    default = "1",
                ),
                KOpt(
                    "dns_bootstrap", "Önyükleme DNS", KOptType.VALUE,
                    "Sunucu adreslerini çözmek için düz DNS IP'si.",
                    default = "1.1.1.1", check = "ip4",
                ),
                KOpt(
                    "ipv6", "IPv6 desteği", KOptType.FLAG,
                    "Kapalıyken IPv6 (AAAA) kayıtları filtrelenir; sızıntı olmaz.",
                    default = "0",
                ),
                KOpt(
                    "v6_block", "IPv6 sızıntı koruması", KOptType.FLAG,
                    "VPN cihazlarının IPv6 ile tüneli atlamasını engeller.",
                    default = "1", dependsOn = "ipv6", dependsValue = "0",
                ),
            ),
        ),
        KTab(
            "stable", "Kararlılık",
            listOf(
                KOpt(
                    "push_fix", "Bildirimleri direkt gönder", KOptType.FLAG,
                    "Önerilen: KAPALI. Kapalıyken Google (FCM) / Apple (APNs) bildirim bağlantıları tünelden gider ve heartbeat ile canlı kalır. Açıkken operatör boştaki bağlantıyı sessizce kesebilir: bildirimler gecikir ya da gelmez.",
                    default = "0",
                ),
                KOpt(
                    "conn_idle", "Boşta bağlantı süresi (sn)", KOptType.VALUE,
                    "Xray varsayılanı 300 sn; telefonun bildirim bağlantısını koparır. Önerilen 14400 (4 saat).",
                    default = "14400", min = 60, max = 86400,
                ),
                KOpt(
                    "ws_heartbeat", "WebSocket heartbeat (sn)", KOptType.VALUE,
                    "Boştaki WebSocket tünelini canlı tutar (varsayılan 30). Kesilirse bildirimler (WhatsApp vb.) telefona ulaşmaz. 0 = kapalı.",
                    default = "30", min = 0, max = 600,
                ),
                KOpt(
                    "tcp_keepalive", "TCP keepalive (sn)", KOptType.VALUE,
                    "Sunucuya giden TCP bağlantısında keepalive (varsayılan 30). Operatör NAT kaydı boş bağlantıyı unutmasın. 0 = kapalı.",
                    default = "30", min = 0, max = 600,
                ),
                KOpt(
                    "block_quic", "QUIC'i (UDP 443) reddet — önerilen", KOptType.FLAG,
                    "Tarayıcıların QUIC bağlantısı tünelde takılabiliyor (ör. Google giriş sayfası dönüp duruyor). Reddedilince tarayıcı beklemeden TCP'ye geçer. Oyun, arama ve DNS etkilenmez.",
                    default = "1",
                ),
                KOpt(
                    "sniff_mode", "Alan adı yönlendirme (hız / uyumluluk)", KOptType.LIST,
                    "Otomatik modda hedef alan adı sunucuya çözdürülür; her yeni bağlantıya biraz gecikme ekler. \"Hız öncelikli\" bu gecikmeyi kaldırır ama operatörün DNS'i bozduğu siteler (ör. Discord) açılmayabilir.",
                    default = "auto",
                    choices = listOf(
                        c("auto", "Otomatik — DNS'i bozuk uygulama varsa sunucu çözer"),
                        c("off", "Hız öncelikli — hiçbir zaman sunucuya çözdürme"),
                    ),
                ),
                KOpt(
                    "sniff_override", "Alan adını VPN sunucusu çözsün", KOptType.FLAG,
                    "Varsayılan kapalı: hedef IP korunur, her bağlantıda ikinci DNS sorgusu olmaz (daha düşük gecikme).",
                    default = "0",
                ),
                KOpt(
                    "speed_tune", "Hız ayarı (TCP + Xray tamponu)", KOptType.FLAG,
                    "Hız testi ya da büyük indirme yavaş hızlanıyorsa: TCP tamponlarını büyütür ve Xray'in bağlantı başına tamponunu artırır (~1 MB). Sorun çıkarsa kapat.",
                    default = "1",
                ),
                KOpt(
                    "buffer_size", "Xray bağlantı tamponu (KB)", KOptType.VALUE,
                    min = 64, max = 8192, placeholder = "1024",
                    dependsOn = "speed_tune", dependsValue = "1",
                ),
                KOpt(
                    "mobile_tune", "Mobil hat ayarı (5G/CGNAT)", KOptType.FLAG,
                    "Düşen bağlantı isteği yüzünden sayfa ~50 sn askıda kalmasın: açıkken 3 denemeye (≈15 sn) iner, tarayıcı kendisi tekrar dener.",
                    default = "1",
                ),
                KOpt(
                    "mux", "TCP Mux", KOptType.FLAG,
                    "Açıkken bağlantılar tek tünelde birleşir; sadece sayfalar sürekli askıda kalıyorsa dene. Varsayılan: kapalı.",
                    default = "0",
                    warn = "Oyun (Metin2 vb.) oynuyorsan KAPALI tut: açıkken oyuna girilemiyor ya da oyundan atılıyorsun.",
                ),
                KOpt(
                    "ws_early", "WebSocket erken veri", KOptType.FLAG,
                    "Path'e \"?ed=2048\" ekler. Bazı sunucu/CDN kurulumlarında sorun çıkarabildiği için varsayılan kapalı.",
                    default = "0",
                ),
                KOpt("ad_block", "Reklam engelleme", KOptType.FLAG, "geosite:category-ads-all", default = "0"),
            ),
        ),
        KTab(
            "sub", "Abonelik",
            listOf(
                KOpt(
                    "sub_url", "Abonelik adresleri", KOptType.DYNLIST,
                    "x-ui / 3x-ui abonelik linki (base64 veya düz liste).",
                    placeholder = "https://…",
                ),
                KOpt(
                    "sub_interval", "Otomatik güncelleme", KOptType.LIST, default = "12",
                    choices = listOf(c("0", "Kapalı")) + listOf("3", "6", "12", "24").map { c(it, "$it saatte bir") },
                ),
            ),
        ),
        KTab(
            "adv", "Gelişmiş",
            listOf(
                KOpt(
                    "log_level", "Günlük seviyesi", KOptType.LIST, default = "warning",
                    choices = listOf("none", "error", "warning", "info", "debug").map { c(it, it) },
                ),
                KOpt(
                    "probe_url", "Ölçüm adresi (ping / sağlık)", KOptType.VALUE,
                    "Ping ve sunucu sağlık kontrolü bu adresle yapılır. 204 döndüren küçük bir adres olmalı.",
                    default = "https://www.gstatic.com/generate_204",
                    choices = listOf(
                        c("https://www.gstatic.com/generate_204", "Google (gstatic)"),
                        c("https://cp.cloudflare.com/generate_204", "Cloudflare"),
                    ),
                ),
                KOpt(
                    "probe_url2", "Yedek ölçüm adresi", KOptType.VALUE,
                    "Birincisi yanıt vermezse bu denenir; ikisi de yanıt vermezse sunucu düşmüş sayılır. Boş = yedek yok.",
                    default = "https://cp.cloudflare.com/generate_204",
                    choices = listOf(
                        c("https://cp.cloudflare.com/generate_204", "Cloudflare"),
                        c("https://www.gstatic.com/generate_204", "Google (gstatic)"),
                        c("", "Yedek yok"),
                    ),
                ),
                KOpt("probe_interval", "Otomatik mod ölçüm aralığı", KOptType.VALUE, default = "2m", placeholder = "2m"),
                KOpt("tproxy_port", "Yakalama (redirect/TPROXY) portu", KOptType.VALUE, default = "12345", check = "port"),
                KOpt("dns_port", "Xray DNS portu", KOptType.VALUE, default = "15353", check = "port"),
                KOpt("socks_port", "Yerel SOCKS5 portu", KOptType.VALUE, "hev-socks5-tunnel bu porta bağlanır.", default = "10808", check = "port"),
                KOpt(
                    "tun_mtu", "TUN MTU", KOptType.VALUE,
                    "Yalnızca TUN yakalama modunda ve UDP için kullanılır. Varsayılan 1380 (hücresel hatta WhatsApp foto/video takılmasını önler); sorun yoksa 1400-1500 denenebilir.",
                    min = 576, max = 9000, placeholder = "1380",
                ),
                KOpt(
                    "mss_clamp", "TCP MSS ayarı", KOptType.FLAG,
                    "Açıkken büyük indirmeler (WhatsApp foto/video) yol MTU'suna uydurulur. Hücresel hatta indirmeler takılıyorsa açık kalsın.",
                    default = "1",
                ),
                KOpt("hev_bin", "hev-socks5-tunnel yolu", KOptType.VALUE, "Boş = sistemdeki hev-socks5-tunnel"),
                KOpt("api_port", "Xray API portu", KOptType.VALUE, default = "10085", check = "port"),
                KOpt("xray_bin", "Xray çekirdek yolu", KOptType.VALUE, "Boş = /usr/share/kediray/bin/xray"),
            ),
        ),
        KTab("band", "Bant", emptyList()),
    )

    val all: List<KOpt> = tabs.flatMap { it.opts }

    /** Uygun değilse hata metni döner. */
    fun validate(o: KOpt, v: String): String? {
        val t = v.trim()
        if (t.isEmpty()) return null
        if (o.min != null || o.max != null) {
            val n = t.toIntOrNull() ?: return "Sayı olmalı"
            if (o.min != null && n < o.min) return "En az ${o.min}"
            if (o.max != null && n > o.max) return "En çok ${o.max}"
        }
        return when (o.check) {
            "port" -> t.toIntOrNull()?.takeIf { it in 1..65535 }?.let { null } ?: "1-65535 arası port"
            "ip4" -> if (Regex("^((25[0-5]|2[0-4]\\d|1?\\d?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1?\\d?\\d)$").matches(t)) null else "IPv4 adresi olmalı"
            "repo" -> if (Regex("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$").matches(t)) null else "sahip/depo biçiminde yaz"
            else -> null
        }
    }
}
