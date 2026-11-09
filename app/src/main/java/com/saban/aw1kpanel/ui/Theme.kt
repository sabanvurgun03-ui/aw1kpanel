package com.saban.aw1kpanel.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.lerp
import com.saban.aw1kpanel.data.ThemeDef
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

private val LightColors = lightColorScheme(
    primary = Color(0xFF6D28D9),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE9DDFF),
    onPrimaryContainer = Color(0xFF2E1065),
    secondaryContainer = Color(0xFFE9DDFF),
    onSecondaryContainer = Color(0xFF2E1065),
    background = Color(0xFFF6F3FF),
    surface = Color.White,
    surfaceVariant = Color(0xFFEDE7FB),
    onSurfaceVariant = Color(0xFF6B6485),
    outline = Color(0xFFB9AEDB),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFC4B5FD),
    onPrimary = Color(0xFF2E1065),
    primaryContainer = Color(0xFF4C1D95),
    onPrimaryContainer = Color(0xFFEDE9FE),
    secondaryContainer = Color(0xFF3B2A66),
    onSecondaryContainer = Color(0xFFEDE9FE),
    background = Color(0xFF110B1E),
    surface = Color(0xFF1B1330),
    surfaceVariant = Color(0xFF2B2145),
    onSurfaceVariant = Color(0xFFB8AED3),
    outline = Color(0xFF5B4B8A),
)

/** Şu an etkin renk takımı. Durum olarak okunduğu için tema değişince ekran kendiliğinden yenilenir. */
private object ThemeState {
    var good by mutableStateOf(Color(0xFF22A04B))
    var bad by mutableStateOf(Color(0xFFD94341))
    var warn by mutableStateOf(Color(0xFFE0A100))
    var header by mutableStateOf(
        Brush.linearGradient(listOf(Color(0xFF4C1D95), Color(0xFF7C3AED), Color(0xFF9F67FF))),
    )
}

val Good: Color get() = ThemeState.good
val Bad: Color get() = ThemeState.bad
val Warn: Color get() = ThemeState.warn

/** Ana sayfadaki üst kartın degradesi. */
val HeaderBrush: Brush get() = ThemeState.header

private fun readableOn(c: Color): Color = if (c.luminance() > 0.5f) Color(0xFF10151C) else Color.White

/** Yedi renkten tam bir Material renk şeması türetir; yazılar zeminde okunabilir kalır. */
private fun schemeFor(t: ThemeDef): ColorScheme {
    val bg = Color(t.bg)
    val accent = Color(t.accent)
    val dark = t.dark
    val ink = if (dark) lerp(Color.White, bg, 0.08f) else lerp(Color.Black, bg, 0.10f)
    val surface = lerp(bg, ink, if (dark) 0.06f else 0.035f)
    val variant = lerp(bg, ink, if (dark) 0.12f else 0.08f)
    val container = lerp(bg, accent, if (dark) 0.22f else 0.18f)
    val soft = lerp(ink, bg, 0.35f)
    val outline = lerp(bg, ink, if (dark) 0.28f else 0.25f)
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = accent,
        onPrimary = readableOn(accent),
        primaryContainer = container,
        onPrimaryContainer = ink,
        secondary = accent,
        onSecondary = readableOn(accent),
        secondaryContainer = container,
        onSecondaryContainer = ink,
        tertiary = accent,
        background = bg,
        onBackground = ink,
        surface = surface,
        onSurface = ink,
        surfaceVariant = variant,
        onSurfaceVariant = soft,
        surfaceTint = Color.Transparent,
        outline = outline,
        outlineVariant = lerp(bg, ink, 0.16f),
        surfaceContainerLowest = bg,
        surfaceContainerLow = lerp(bg, ink, 0.04f),
        surfaceContainer = surface,
        surfaceContainerHigh = lerp(bg, ink, if (dark) 0.09f else 0.06f),
        surfaceContainerHighest = variant,
        error = Color(t.bad),
        onError = readableOn(Color(t.bad)),
    )
}

/**
 * [theme] null ise klasik mor tema ([darkTheme] açık/koyu seçer), değilse seçilen tema uygulanır.
 */
@Composable
fun AW1KTheme(darkTheme: Boolean, theme: ThemeDef? = null, content: @Composable () -> Unit) {
    // Yalnızca tema değişince yazılır (kompozisyon sırasında gereksiz durum yazımı olmasın).
    remember(theme) {
        if (theme == null) {
            ThemeState.good = Color(0xFF22A04B)
            ThemeState.bad = Color(0xFFD94341)
            ThemeState.warn = Color(0xFFE0A100)
            ThemeState.header = Brush.linearGradient(listOf(Color(0xFF4C1D95), Color(0xFF7C3AED), Color(0xFF9F67FF)))
        } else {
            ThemeState.good = Color(theme.good)
            ThemeState.bad = Color(theme.bad)
            ThemeState.warn = Color(theme.warn)
            val end = theme.panelEnd?.let { Color(it) } ?: Color(theme.panel)
            ThemeState.header = Brush.linearGradient(listOf(Color(theme.panel), end))
        }
        theme
    }
    MaterialTheme(
        colorScheme = if (theme != null) schemeFor(theme) else if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
