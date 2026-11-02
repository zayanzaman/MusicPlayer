package com.zxn.musicplayer.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.zxn.musicplayer.R

/**
 * Figtree (SIL Open Font License, bundled in res/font): a friendly geometric sans that is the closest free match to
 * Proxima Nova. Proxima Nova itself is a paid font and can't be bundled.
 */
val AppFont = FontFamily(
    Font(R.font.figtree_regular, FontWeight.Normal),
    Font(R.font.figtree_medium, FontWeight.Medium),
    Font(R.font.figtree_semibold, FontWeight.SemiBold),
    Font(R.font.figtree_bold, FontWeight.Bold),
    Font(R.font.figtree_extrabold, FontWeight.ExtraBold),
)

private fun TextStyle.app(weight: FontWeight? = null, spacing: Float? = null) = copy(
    fontFamily = AppFont,
    fontWeight = weight ?: fontWeight,
    letterSpacing = (spacing ?: 0f).sp,
)

private val b = Typography()

val AppTypography = Typography(
    displayLarge = b.displayLarge.app(), displayMedium = b.displayMedium.app(), displaySmall = b.displaySmall.app(),
    headlineLarge = b.headlineLarge.app(FontWeight.Bold, -0.2f),
    headlineMedium = TextStyle(fontSize = 34.sp, lineHeight = 40.sp).app(FontWeight.Bold, -0.2f),
    headlineSmall = b.headlineSmall.app(FontWeight.Bold, -0.2f),
    titleLarge = b.titleLarge.app(FontWeight.Bold, -0.2f),
    titleMedium = b.titleMedium.copy(fontSize = 17.sp).app(FontWeight.SemiBold, -0.2f),
    titleSmall = b.titleSmall.copy(fontSize = 15.sp).app(FontWeight.SemiBold, -0.2f),
    bodyLarge = b.bodyLarge.copy(fontSize = 17.sp).app(),
    bodyMedium = b.bodyMedium.copy(fontSize = 15.sp).app(),
    bodySmall = b.bodySmall.copy(fontSize = 13.sp).app(),
    labelLarge = b.labelLarge.copy(fontSize = 15.sp).app(FontWeight.SemiBold),
    labelMedium = b.labelMedium.app(FontWeight.Medium),
    labelSmall = b.labelSmall.app(FontWeight.Medium),
)
