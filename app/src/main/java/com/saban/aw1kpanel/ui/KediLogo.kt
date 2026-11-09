package com.saban.aw1kpanel.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.saban.aw1kpanel.R

/** Mor zeminli yuvarlak köşeli kedi logosu (uygulama simgesiyle aynı çizim). */
@Composable
fun KediLogo(size: Dp = 40.dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.28f))
            .background(Brush.linearGradient(listOf(Color(0xFF4C1D95), Color(0xFF7C3AED)))),
    ) {
        // Simge çiziminin etrafında güvenli alan boşluğu var; yakınlaştırıp doldur.
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = "KediRay",
            modifier = Modifier
                .size(size)
                .scale(1.45f),
        )
    }
}
