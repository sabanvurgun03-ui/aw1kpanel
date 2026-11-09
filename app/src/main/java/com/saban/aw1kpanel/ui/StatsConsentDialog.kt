package com.saban.aw1kpanel.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** İlk girişte bir kez sorulur. Cevap verilene kadar hiçbir şey gönderilmez. */
@Composable
internal fun StatsConsentDialog(vm: PanelViewModel) {
    AlertDialog(
        onDismissRequest = { },
        title = { Text("Geliştirmeye yardımcı olur musun?") },
        text = {
            Column {
                Text("Kaç kişinin AW1K Panel kullandığını görmek istiyorum. Onay verirsen günde en çok bir kez şunlar gönderilir:")
                Spacer(Modifier.height(8.dp))
                Text("• rastgele bir kurulum kimliği (seni tanıtmaz)\n• uygulama sürümü\n• uygulama adı")
                Spacer(Modifier.height(8.dp))
                Text("Modem adresin ve şifren, VPN sunucuların, cihazların, trafiğin ve adın gönderilmez. Ayarlar sayfasından istediğin an kapatabilirsin.")
            }
        },
        confirmButton = {
            Button(onClick = { vm.setStatsConsent(true) }) { Text("Evet, yardımcı olayım") }
        },
        dismissButton = {
            TextButton(onClick = { vm.setStatsConsent(false) }) { Text("Hayır") }
        },
    )
}
