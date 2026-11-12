# AW1K Panel (Android) — proje notları

Arcadyan AW1000 modem için Kotlin/Jetpack Compose uygulaması. Sahibi: Şaban Vurgun (Türkçe konuşur).
Repo: sabanvurgun03-ui/aw1kpanel — her push'ta GitHub Actions sürüm (tag = sürüm) yayınlar.

## Çalışma kuralları
- Cevaplar kısa; yapılanlar 2-3 satır. Dosya yolunu tam yaz.
- Sürümü kendin artır (`app/build.gradle.kts` + `ui/AboutTab.kt` + `RELEASE-NOTES-X.md`).
- Teslimde yalnızca değişiklik SONRASI zip (`/tmp/claude-0/out/AW1KPanel.zip`, .git hariç) gönder.
- Her şey responsive olsun. Tahmin etme, araştır ve kaynak göster; karar veremezsen sor.
- İş bitince `/compact` hatırlat.

## Derleme
`ANDROID_HOME=/home/claude/android-sdk /opt/gradle/bin/gradle assembleDebug --max-workers=1 -q`
Commit/zip öncesi `app/build`, `build`, `.gradle` sil.

## Yapılanlar (yeniden eskiye)
- 0.58: VPN kartındaki çift hız kutuları kaldırıldı; RAM/Depolama/Geçici bellek kutularında % doluluk; süreler "g sa dk sn" (dar yerde 2 birim); Veri sekmesi 12 ay, 3 aydan sonrası açılır liste.
- 0.5x: Ağ sayfasında "Yeniden bağlanıyor…/Modem yeniden başlatılıyor…" ilerleme yazısı; güncelleme kontrolü GitHub 403'ünde API'siz yedek yol.
- Ayrıntı: `RELEASE-NOTES-*.md`.
