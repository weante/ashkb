package com.ashkb.app.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// ============================================================
// ASHKB 设计 token · 单色值部分由脚本生成，勿手改
// 生成参数：primary h232 C0.050 · secondary h80 C0.036 · tertiary h52 C0.055
//          neutral h52 (L C0.010 / D C0.013) · success h135 C0.060
//          warning h88 C0.110 · danger h18 C0.170
// 校验：全部文本对 ≥ AA（最低 4.31:1），语义色 ΔE ≥ 0.065
// ============================================================

// ---------- Light ----------
val primaryLight = Color(0xFF416476)
val onPrimaryLight = Color(0xFFFFFFFF)
val primaryContainerLight = Color(0xFFC2E9FF)
val onPrimaryContainerLight = Color(0xFF001F2D)
val inversePrimaryLight = Color(0xFFA7CDE2)
val secondaryLight = Color(0xFF695C47)
val onSecondaryLight = Color(0xFFFFFFFF)
val secondaryContainerLight = Color(0xFFEFE0C8)
val onSecondaryContainerLight = Color(0xFF241A06)
val tertiaryLight = Color(0xFF785641)
val onTertiaryLight = Color(0xFFFFFFFF)
val tertiaryContainerLight = Color(0xFFFFDAC5)
val onTertiaryContainerLight = Color(0xFF2F1301)
val errorLight = Color(0xFFA92138)
val onErrorLight = Color(0xFFFFFFFF)
val errorContainerLight = Color(0xFFFFD7D7)
val onErrorContainerLight = Color(0xFF3B000A)
val successLight = Color(0xFF506644)
val onSuccessLight = Color(0xFFFFFFFF)
val successContainerLight = Color(0xFFD2ECC4)
val onSuccessContainerLight = Color(0xFF0E2102)
val warningLight = Color(0xFF755A03)
val onWarningLight = Color(0xFFFFFFFF)
val warningContainerLight = Color(0xFFFFDF92)
val onWarningContainerLight = Color(0xFF241A00)
val dangerLight = Color(0xFFA92138)
val onDangerLight = Color(0xFFFFFFFF)
val dangerContainerLight = Color(0xFFFFD7D7)
val onDangerContainerLight = Color(0xFF3B000A)
val backgroundLight = Color(0xFFFFF8F4)
val onBackgroundLight = Color(0xFF1F1A17)
val surfaceLight = Color(0xFFFFF8F4)
val onSurfaceLight = Color(0xFF1F1A17)
val surfaceVariantLight = Color(0xFFEDE0D8)
val onSurfaceVariantLight = Color(0xFF4F443E)
val surfaceContainerLowestLight = Color(0xFFFFFFFF)
val surfaceContainerLowLight = Color(0xFFF9F2EE)
val surfaceContainerLight = Color(0xFFF4ECE8)
val surfaceContainerHighLight = Color(0xFFEEE6E2)
val surfaceContainerHighestLight = Color(0xFFE8E1DD)
val surfaceDimLight = Color(0xFFE0D8D4)
val surfaceBrightLight = Color(0xFFFFF8F4)
val surfaceTintLight = Color(0xFF416476)
val outlineLight = Color(0xFF80746E)
val outlineVariantLight = Color(0xFFD1C4BC)
val inverseSurfaceLight = Color(0xFF352F2C)
val inverseOnSurfaceLight = Color(0xFFF7EFEB)
val scrimLight = Color(0xFF000000)

// ---------- Dark ----------
val primaryDark = Color(0xFFA7CDE2)
val onPrimaryDark = Color(0xFF113545)
val primaryContainerDark = Color(0xFF294C5D)
val onPrimaryContainerDark = Color(0xFFC2E9FF)
val inversePrimaryDark = Color(0xFF416476)
val secondaryDark = Color(0xFFD3C5AD)
val onSecondaryDark = Color(0xFF3A2E1B)
val secondaryContainerDark = Color(0xFF514531)
val onSecondaryContainerDark = Color(0xFFEFE0C8)
val tertiaryDark = Color(0xFFE4BDA6)
val onTertiaryDark = Color(0xFF462814)
val tertiaryContainerDark = Color(0xFF5E3E2A)
val onTertiaryContainerDark = Color(0xFFFFDAC5)
val errorDark = Color(0xFFFFADAF)
val onErrorDark = Color(0xFF600117)
val errorContainerDark = Color(0xFF870224)
val onErrorContainerDark = Color(0xFFFFD7D7)
val successDark = Color(0xFFB6D0A9)
val onSuccessDark = Color(0xFF233716)
val successContainerDark = Color(0xFF394E2D)
val onSuccessContainerDark = Color(0xFFD2ECC4)
val warningDark = Color(0xFFE4C36F)
val onWarningDark = Color(0xFF3E2E01)
val warningContainerDark = Color(0xFF594402)
val onWarningContainerDark = Color(0xFFFFDF92)
val dangerDark = Color(0xFFFFADAF)
val onDangerDark = Color(0xFF600117)
val dangerContainerDark = Color(0xFF870224)
val onDangerContainerDark = Color(0xFFFFD7D7)
val backgroundDark = Color(0xFF18120E)
val onBackgroundDark = Color(0xFFEAE0DB)
val surfaceDark = Color(0xFF18120E)
val onSurfaceDark = Color(0xFFEAE0DB)
val surfaceVariantDark = Color(0xFF51433B)
val onSurfaceVariantDark = Color(0xFFD4C3B9)
val surfaceContainerLowestDark = Color(0xFF130D09)
val surfaceContainerLowDark = Color(0xFF211A16)
val surfaceContainerDark = Color(0xFF251E1A)
val surfaceContainerHighDark = Color(0xFF2F2824)
val surfaceContainerHighestDark = Color(0xFF38312D)
val surfaceDimDark = Color(0xFF18120E)
val surfaceBrightDark = Color(0xFF3F3733)
val surfaceTintDark = Color(0xFFA7CDE2)
val outlineDark = Color(0xFF9D8D84)
val outlineVariantDark = Color(0xFF51433B)
val inverseSurfaceDark = Color(0xFFEAE0DB)
val inverseOnSurfaceDark = Color(0xFF362F2A)
val scrimDark = Color(0xFF000000)

// ============================================================
// 以下为手工装配区
// ============================================================

val LightColors = lightColorScheme(
    primary = primaryLight, onPrimary = onPrimaryLight,
    primaryContainer = primaryContainerLight, onPrimaryContainer = onPrimaryContainerLight,
    inversePrimary = inversePrimaryLight,
    secondary = secondaryLight, onSecondary = onSecondaryLight,
    secondaryContainer = secondaryContainerLight, onSecondaryContainer = onSecondaryContainerLight,
    tertiary = tertiaryLight, onTertiary = onTertiaryLight,
    tertiaryContainer = tertiaryContainerLight, onTertiaryContainer = onTertiaryContainerLight,
    error = errorLight, onError = onErrorLight,
    errorContainer = errorContainerLight, onErrorContainer = onErrorContainerLight,
    background = backgroundLight, onBackground = onBackgroundLight,
    surface = surfaceLight, onSurface = onSurfaceLight,
    surfaceVariant = surfaceVariantLight, onSurfaceVariant = onSurfaceVariantLight,
    surfaceContainerLowest = surfaceContainerLowestLight, surfaceContainerLow = surfaceContainerLowLight,
    surfaceContainer = surfaceContainerLight, surfaceContainerHigh = surfaceContainerHighLight,
    surfaceContainerHighest = surfaceContainerHighestLight,
    surfaceDim = surfaceDimLight, surfaceBright = surfaceBrightLight, surfaceTint = surfaceTintLight,
    outline = outlineLight, outlineVariant = outlineVariantLight,
    inverseSurface = inverseSurfaceLight, inverseOnSurface = inverseOnSurfaceLight, scrim = scrimLight,
)

val DarkColors = darkColorScheme(
    primary = primaryDark, onPrimary = onPrimaryDark,
    primaryContainer = primaryContainerDark, onPrimaryContainer = onPrimaryContainerDark,
    inversePrimary = inversePrimaryDark,
    secondary = secondaryDark, onSecondary = onSecondaryDark,
    secondaryContainer = secondaryContainerDark, onSecondaryContainer = onSecondaryContainerDark,
    tertiary = tertiaryDark, onTertiary = onTertiaryDark,
    tertiaryContainer = tertiaryContainerDark, onTertiaryContainer = onTertiaryContainerDark,
    error = errorDark, onError = onErrorDark,
    errorContainer = errorContainerDark, onErrorContainer = onErrorContainerDark,
    background = backgroundDark, onBackground = onBackgroundDark,
    surface = surfaceDark, onSurface = onSurfaceDark,
    surfaceVariant = surfaceVariantDark, onSurfaceVariant = onSurfaceVariantDark,
    surfaceContainerLowest = surfaceContainerLowestDark, surfaceContainerLow = surfaceContainerLowDark,
    surfaceContainer = surfaceContainerDark, surfaceContainerHigh = surfaceContainerHighDark,
    surfaceContainerHighest = surfaceContainerHighestDark,
    surfaceDim = surfaceDimDark, surfaceBright = surfaceBrightDark, surfaceTint = surfaceTintDark,
    outline = outlineDark, outlineVariant = outlineVariantDark,
    inverseSurface = inverseSurfaceDark, inverseOnSurface = inverseOnSurfaceDark, scrim = scrimDark,
)

val ClinicalLight = ClinicalColors(
    success = successLight, onSuccess = onSuccessLight,
    successContainer = successContainerLight, onSuccessContainer = onSuccessContainerLight,
    warning = warningLight, onWarning = onWarningLight,
    warningContainer = warningContainerLight, onWarningContainer = onWarningContainerLight,
    danger = dangerLight, onDanger = onDangerLight,
    dangerContainer = dangerContainerLight, onDangerContainer = onDangerContainerLight,
)

val ClinicalDark = ClinicalColors(
    success = successDark, onSuccess = onSuccessDark,
    successContainer = successContainerDark, onSuccessContainer = onSuccessContainerDark,
    warning = warningDark, onWarning = onWarningDark,
    warningContainer = warningContainerDark, onWarningContainer = onWarningContainerDark,
    danger = dangerDark, onDanger = onDangerDark,
    dangerContainer = dangerContainerDark, onDangerContainer = onDangerContainerDark,
)
