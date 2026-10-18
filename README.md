<p align="center"><img src="docs/banner.png" alt="AW1K Panel" width="640"></p>

<p align="center"><a href="https://buymeacoffee.com/sabanvurgun"><img src="https://img.buymeacoffee.com/button-api/?text=Bana%20bir%20kahve%20%C4%B1smarla&emoji=%E2%98%95&slug=sabanvurgun&button_colour=FFDD00&font_colour=000000&font_family=Cookie&outline_colour=000000&coffee_colour=ffffff" alt="Buy me a coffee" height="60"></a></p>

# AW1K Panel

**Türkçe** · [English](#english)

Arcadyan AW1000 (OpenWrt/LuCI) modemin için Android uygulaması. Modemin durumunu, bağlı cihazları, Wi-Fi'yi, hücresel bağlantı bilgilerini ve [KediRay VPN](https://github.com/sabanvurgun03-ui/kediray)'i telefondan yönetirsin. Geliştirici: Şaban Vurgun.

## Ekran görüntüleri

<p align="center"><img src="docs/ekranlar.jpg" alt="AW1K Panel ekranları"></p>

*Ana sayfa, VPN, Wi-Fi, Ağ ve Cihaz ekranları.*

## Özellikler

- **Ana sayfa:** operatör, bağlantı türü (örn. LTE-A 2CA, B3+B3), sinyal yüzdesi, canlı yükleme/indirme hızı; CPU, bellek, çalışma süresi ve yük ortalaması; VPN durumu
- **VPN:** KediRay'i çalıştır, durdur, yeniden başlat; sunucular arasında "Buna geç"; link ya da JSON ile sunucu ekle, elle düğüm ekle, sertifikayı sabitle
- **xDivine-Ray profilleri:** link ya da JSON ekleyerek profil yönet
- **Wi-Fi:** ağlar (SSID, radyo, şifreleme), her ağa bağlı istemciler (sinyal, hız, bağlı süre, trafik, yön) ve "Wi-Fi'dan engelle"
- **Ağ:** bağlantı durumu, operatör, ülke, teknoloji, bağlantı süresi, gönderilen/alınan veri; PCC ve SCC bantları için bant genişliği, fiziksel hücre kimliği, EARFCN, RSSI, RSRP, RSRQ, SINR
- **Cihazlar:** bağlı cihaz listesi, bağlı süre, her cihaz için yön seçimi (**VPN / Direkt**) ve **Engelle**
- **Gizlilik:** IP / MAC adreslerini ve sunucu adreslerini gizle (ekran görüntüsü paylaşırken işe yarar)

## Kurulum

1. **Modem tarafı:** uygulama KediRay'in modem eklentisini kullanır. KediRay'i kur: [KediRay kurulum](https://github.com/sabanvurgun03-ui/kediray#kurulum). Eklenti (`aw1k` rpcd modülü) onunla birlikte gelir.
2. **Uygulama:** [KediRay sürümlerinden](https://github.com/sabanvurgun03-ui/kediray/releases) en son `Kediray-*.apk` dosyasını indirip telefona kur (bilinmeyen kaynaklardan kuruluma izin vermen gerekebilir).
3. Uygulamayı aç ve modemine bağlan (varsayılan LuCI adresi `192.168.1.1`).

## Notlar

- Test edilen cihaz: Arcadyan AW1000 (DivineWRT / OpenWrt, LuCI).
- Geri bildirim ve sorunlar için bu depodaki **Issues** bölümünü kullanabilirsin.

## Destek

Beğendiysen:

<p align="center"><a href="https://buymeacoffee.com/sabanvurgun"><img src="https://img.buymeacoffee.com/button-api/?text=Bana%20bir%20kahve%20%C4%B1smarla&emoji=%E2%98%95&slug=sabanvurgun&button_colour=FFDD00&font_colour=000000&font_family=Cookie&outline_colour=000000&coffee_colour=ffffff" alt="Buy me a coffee" height="60"></a></p>

---

## English

Android app for the Arcadyan AW1000 (OpenWrt/LuCI) modem. Check the modem, connected devices, Wi-Fi, cellular link details and the [KediRay VPN](https://github.com/sabanvurgun03-ui/kediray) from your phone. Author: Şaban Vurgun.

### Screenshots

<p align="center"><img src="docs/ekranlar.jpg" alt="AW1K Panel screens"></p>

*Home, VPN, Wi-Fi, Network and Devices screens.*

### Features

- **Home:** operator, connection type (e.g. LTE-A 2CA, B3+B3), signal percentage, live upload/download speed; CPU, memory, uptime and load average; VPN status
- **VPN:** start, stop and restart KediRay; "switch to this" between servers; add servers by link or JSON, add a node by hand, pin the certificate
- **xDivine-Ray profiles:** manage profiles by adding a link or JSON
- **Wi-Fi:** networks (SSID, radio, encryption), clients per network (signal, rate, connected time, traffic, route) and "block from Wi-Fi"
- **Network:** link status, operator, country, technology, connection time, sent/received data; PCC and SCC band details: bandwidth, physical cell ID, EARFCN, RSSI, RSRP, RSRQ, SINR
- **Devices:** connected device list, connected time, per-device route (**VPN / Direct**) and **Block**
- **Privacy:** hide IP / MAC and server addresses (handy when sharing screenshots)

### Install

1. **Router side:** the app uses KediRay's router add-on. Install KediRay: [KediRay install](https://github.com/sabanvurgun03-ui/kediray#english). The `aw1k` rpcd module ships with it.
2. **App:** download the latest `Kediray-*.apk` from the [KediRay releases](https://github.com/sabanvurgun03-ui/kediray/releases) and install it on your phone (you may need to allow installs from unknown sources).
3. Open the app and connect to your router (default LuCI address `192.168.1.1`).

### Notes

- Tested device: Arcadyan AW1000 (DivineWRT / OpenWrt, LuCI).
- Use **Issues** in this repo for feedback and problems.

### Support

If you like it:

<p align="center"><a href="https://buymeacoffee.com/sabanvurgun"><img src="https://img.buymeacoffee.com/button-api/?text=Bana%20bir%20kahve%20%C4%B1smarla&emoji=%E2%98%95&slug=sabanvurgun&button_colour=FFDD00&font_colour=000000&font_family=Cookie&outline_colour=000000&coffee_colour=ffffff" alt="Buy me a coffee" height="60"></a></p>
