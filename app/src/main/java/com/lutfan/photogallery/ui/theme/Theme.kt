package com.lutfan.photogallery.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object Palette {
    val Cream = Color(0xFFF6F3F0)
    val Paper = Color(0xFFF7F7F7)
    val Card = Color(0xFFFFFFFF)
    val Charcoal = Color(0xFF3B3B3B)
    val CharcoalSoft = Color(0xFF4A4A4A)
    val CharcoalChip = Color(0xFF555555)
    val Orange = Color(0xFFF0893E)
    val OrangeSoft = Color(0xFFFCE7D6)
    val Ink = Color(0xFF2A2A2A)
    val Muted = Color(0xFF9B9B9B)
    val Line = Color(0xFFE8E4E0)
    val OnDark = Color(0xFFEDEDED)
    val OnDarkMuted = Color(0xFFA8A8A8)
}

private val colors = lightColorScheme(
    primary = Palette.Orange,
    onPrimary = Color.White,
    primaryContainer = Palette.OrangeSoft,
    onPrimaryContainer = Palette.Ink,
    secondary = Palette.Charcoal,
    onSecondary = Color.White,
    background = Palette.Paper,
    onBackground = Palette.Ink,
    surface = Palette.Card,
    onSurface = Palette.Ink,
    surfaceVariant = Palette.Cream,
    onSurfaceVariant = Palette.Muted,
    outline = Palette.Line,
)

private val base = TextStyle(fontFamily = FontFamily.SansSerif, color = Palette.Ink)

private val typography = Typography(
    displaySmall = base.copy(fontSize = 32.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.3).sp),
    headlineSmall = base.copy(fontSize = 24.sp, fontWeight = FontWeight.Medium),
    titleLarge = base.copy(fontSize = 20.sp, fontWeight = FontWeight.Medium),
    titleMedium = base.copy(fontSize = 16.sp, fontWeight = FontWeight.Medium),
    bodyLarge = base.copy(fontSize = 16.sp),
    bodyMedium = base.copy(fontSize = 14.sp),
    labelLarge = base.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    labelMedium = base.copy(fontSize = 13.sp),
    labelSmall = base.copy(fontSize = 11.sp),
)

@Composable
fun GalleryTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = typography, content = content)
}
