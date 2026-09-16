package com.ashkb.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember

/**
 * ASHKB 主题入口。
 *
 * `dynamicColor` 默认关闭：医疗记录类 App 需要稳定的状态色语义，
 * 壁纸取色会让"偏高 / 偏低 / 达标"的辨识度不可控。
 */
@Composable
fun AshkbTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    @Suppress("UNUSED_EXPRESSION")
    dynamicColor // 预留开关：后续可在「我的」里开放给用户
    val scheme = if (darkTheme) DarkColors else LightColors
    val clinical = if (darkTheme) ClinicalDark else ClinicalLight
    CompositionLocalProvider(LocalClinicalColors provides clinical) {
        MaterialTheme(
            colorScheme = scheme,
            typography = remember { ashkbTypography() },
            shapes = AshkbShapes,
            content = content,
        )
    }
}
