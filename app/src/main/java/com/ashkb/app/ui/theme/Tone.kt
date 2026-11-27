package com.ashkb.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/** 全 App 统一的语义色调。解析函数只在本文件实现，禁止各屏幕自己写 `when (tone)`。 */
enum class StatusTone { Neutral, Brand, Info, Success, Warning, Danger }

/** 容器底色 → 容器上的前景色。 */
@Composable
@ReadOnlyComposable
fun StatusTone.colors(): Pair<Color, Color> {
    val cs = MaterialTheme.colorScheme
    val cl = Clinical.colors
    return when (this) {
        // 审核卡 C2-09：中性胶囊原为 onSurfaceVariant（5A6B78）压在 surfaceContainerHighest（E2E8ED）上，
        // 浅色主题对比度 4.46:1，低于 WCAG AA 小字号 4.5:1（verify-palette-contrast.ps1 只校验 M3 的
        // on*/container 标准配对，这一对不在其中）。底色降一档到 surfaceContainerHigh：浅色 4.85:1、深色 6.67:1。
        StatusTone.Neutral -> cs.surfaceContainerHigh to cs.onSurfaceVariant
        StatusTone.Brand -> cs.tertiaryContainer to cs.onTertiaryContainer
        StatusTone.Info -> cs.primaryContainer to cs.onPrimaryContainer
        StatusTone.Success -> cl.successContainer to cl.onSuccessContainer
        StatusTone.Warning -> cl.warningContainer to cl.onWarningContainer
        StatusTone.Danger -> cl.dangerContainer to cl.onDangerContainer
    }
}

/** 前景强调色：图标、左侧色条、文字强调。 */
@Composable
@ReadOnlyComposable
fun StatusTone.accent(): Color {
    val cs = MaterialTheme.colorScheme
    val cl = Clinical.colors
    return when (this) {
        StatusTone.Neutral -> cs.onSurfaceVariant
        StatusTone.Brand -> cs.tertiary
        StatusTone.Info -> cs.primary
        StatusTone.Success -> cl.success
        StatusTone.Warning -> cl.warning
        StatusTone.Danger -> cl.danger
    }
}

/** 容器底色（只要背景不要前景时用）。 */
@Composable
@ReadOnlyComposable
fun StatusTone.container(): Color = colors().first

/** 容器上的前景色。 */
@Composable
@ReadOnlyComposable
fun StatusTone.onContainer(): Color = colors().second
