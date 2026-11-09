package com.saban.aw1kpanel.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

/** Kart başlığı: solda mor simge, yanında başlık. */
@Composable
internal fun CardTitle(icon: Int, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
    }
}

/** Yuvarlak, renkli zeminli simge. */
@Composable
internal fun IconBadge(icon: Int, tint: Color, background: Color, size: Int = 44) {
    Box(
        Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size((size * 0.5f).dp),
        )
    }
}

/** Küçük etiket (bant, güvenlik türü gibi). */
@Composable
internal fun Chip(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

internal fun maskIp(ip: String): String {
    val p = ip.split('.')
    return if (p.size == 4) "•••.•••.•••.${p[3]}" else "••••"
}

/** Sunucu adresi: IP ise son bölüm kalır, alan adı ise tamamen gizlenir. */
internal fun maskHost(h: String): String =
    if (Regex("^\\d+\\.\\d+\\.\\d+\\.\\d+$").matches(h)) maskIp(h) else "••••••"

internal fun maskMac(mac: String): String {
    val p = mac.split(':')
    return if (p.size == 6) "••:••:••:••:${p[4]}:${p[5]}" else "••••"
}
