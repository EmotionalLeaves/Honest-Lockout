package com.honestlockout.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.honestlockout.app.R

/** Inter, bundled (res/font). Four weights; no synthetic bold. */
val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

private fun style(size: Int, weight: FontWeight, line: Int, tracking: Float = 0f) =
    TextStyle(fontFamily = Inter, fontSize = size.sp, fontWeight = weight, lineHeight = line.sp, letterSpacing = tracking.sp, fontFeatureSettings = "tnum")

/** A tight scale: bold display titles, semibold headings, regular body, medium labels. */
val AppTypography = Typography(
    displayLarge = style(56, FontWeight.Bold, 64, -1.5f),
    displayMedium = style(44, FontWeight.Bold, 52, -1f),
    displaySmall = style(36, FontWeight.Bold, 44, -0.8f),
    headlineLarge = style(32, FontWeight.Bold, 40, -0.6f),
    headlineMedium = style(28, FontWeight.Bold, 36, -0.5f),
    headlineSmall = style(24, FontWeight.SemiBold, 32, -0.4f),
    titleLarge = style(20, FontWeight.SemiBold, 28, -0.2f),
    titleMedium = style(16, FontWeight.SemiBold, 24, -0.1f),
    titleSmall = style(14, FontWeight.SemiBold, 20, 0f),
    bodyLarge = style(16, FontWeight.Normal, 24, 0f),
    bodyMedium = style(14, FontWeight.Normal, 20, 0f),
    bodySmall = style(13, FontWeight.Normal, 18, 0f),
    labelLarge = style(14, FontWeight.Medium, 20, 0f),
    labelMedium = style(12, FontWeight.Medium, 16, 0.2f),
    labelSmall = style(11, FontWeight.Medium, 16, 0.4f),
)

/** The countdown number. */
val HugeNumber = TextStyle(fontFamily = Inter, fontSize = 112.sp, fontWeight = FontWeight.Bold, letterSpacing = (-4).sp, lineHeight = 112.sp, fontFeatureSettings = "tnum")
