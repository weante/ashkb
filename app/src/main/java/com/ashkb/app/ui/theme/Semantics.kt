package com.ashkb.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * `ColorScheme` 是 data class，无法扩展字段。
 * success / warning / danger 三组临床语义色经 CompositionLocal 注入。
 */
@Immutable
data class ClinicalColors(
    val success: Color, val onSuccess: Color, val successContainer: Color, val onSuccessContainer: Color,
    val warning: Color, val onWarning: Color, val warningContainer: Color, val onWarningContainer: Color,
    val danger: Color, val onDanger: Color, val dangerContainer: Color, val onDangerContainer: Color,
)

val LocalClinicalColors = staticCompositionLocalOf {
    ClinicalColors(
        Color.Unspecified, Color.Unspecified, Color.Unspecified, Color.Unspecified,
        Color.Unspecified, Color.Unspecified, Color.Unspecified, Color.Unspecified,
        Color.Unspecified, Color.Unspecified, Color.Unspecified, Color.Unspecified,
    )
}

object Clinical {
    val colors: ClinicalColors
        @Composable @ReadOnlyComposable get() = LocalClinicalColors.current
}
