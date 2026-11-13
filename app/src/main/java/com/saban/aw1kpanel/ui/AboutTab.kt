package com.saban.aw1kpanel.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.saban.aw1kpanel.R

private const val SUPPORT_URL = "https://buymeacoffee.com/sabanvurgun"

internal const val APP_VERSION = "0.64"

private val APP_FEATURES = listOf(
    "Operatör, ağ tipi (LTE-A / CA) ve sinyal yüzdesi, anlık hız",
    "Modemdata: SIM, hücre, bant ve kalite bilgileri",
    "CPU, bellek ve çalışma süresi; modemi yeniden başlatma",
    "Wi-Fi ağlarını açıp kapatma, ad / şifre / kanal düzenleme",
    "Bağlı cihazlar, IP ve MAC gizleme, cihaz engelleme",
    "KediRay ve xDivine-Ray VPN durumu, düğüm / profil yönetimi",
    "KediRay / Xray günlüğü: hata gruplama ve ne yapmalı önerileri",
    "KediRay güncellemesi GitHub'dan: denetle, modem indirip SHA256 ile doğrulayıp kurar",
    "Temalar: açık/koyu mod, hazır temalar, kendi renklerin ve bağlantıdan içe alma",
    "Günlük, haftalık ve aylık veri kullanımı",
    "VPN kopma uyarısı, birden fazla modem, açık / koyu / sistem teması",
    "İsteğe bağlı anonim kullanım sayısı (varsayılan sorulur, istediğin an kapatırsın)",
)

@Composable
internal fun AboutTab(kediray: com.saban.aw1kpanel.data.KediState? = null) {
    LazyColumn(
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionCard(brush = HeaderBrush) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    KediLogo(72.dp)
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(
                            "AW1K Panel",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = androidx.compose.ui.graphics.Color.White,
                        )
                        Text(
                            "Sürüm $APP_VERSION · KediRay ile uyumlu",
                            style = MaterialTheme.typography.bodyMedium,
                            color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.85f),
                        )
                    }
                }
            }
        }
        item {
            SectionCard {
                CardTitle(R.drawable.ic_nav_info, "Uygulama özellikleri")
                Spacer(Modifier.height(8.dp))
                APP_FEATURES.forEach { Bullet(it) }
            }
        }
        item {
            SectionCard {
                CardTitle(R.drawable.ic_nav_vpn, "KediRay VPN")
                Text(
                    "OpenWrt için Xray tabanlı akıllı VPN",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Bullet("KediRay sürümü: " + (kediray?.version?.let { "v$it" } ?: "okunamadı"))
                Bullet("Proxy çekirdeği: Xray" + (kediray?.core?.let { " $it" } ?: ""))
                Bullet("Cihaz bazında yön: VPN / Direkt / Engelli")
                Bullet("Geliştirici: Şaban Vurgun · 2026")
            }
        }
        item {
            val uri = androidx.compose.ui.platform.LocalUriHandler.current
            SectionCard {
                CardTitle(R.drawable.ic_nav_info, "Beni destekle")
                Spacer(Modifier.height(6.dp))
                Text(
                    SUPPORT_URL,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { uri.openUri(SUPPORT_URL) },
                )
                Spacer(Modifier.height(10.dp))
                androidx.compose.material3.Button(
                    onClick = { uri.openUri(SUPPORT_URL) },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(26.dp),
                ) { Text("Bana bir kahve ısmarla") }
                Spacer(Modifier.height(10.dp))
                Text(
                    "Bağışlarınız için teşekkürler, bu iş için harcanan emek için ufak bir hediye.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            Text(
                "Arcadyan AW1000 (OpenWrt / QModem) için yapıldı. Modemle yerel ağda ubus üzerinden konuşur; veriler telefonda değil modemde kalır.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Bullet(text: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text("•", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
