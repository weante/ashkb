package com.ashkb.app.ui.components

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

import com.ashkb.app.ui.theme.Spacing

/**
 * 窄屏 / 大字号的**版式决策**（外部审查报告 §U-2）。
 *
 * 为什么抽成纯函数：这两条判据是「2× 字号下版式会不会挤爆」的唯一决定因素，而
 * Compose 的 `GridCells.Adaptive` / `Row` 都无法在 JVM 单测里验证——抽成
 * `(fontScale, 可用宽度) -> 决策` 之后，边界值就有了可断言的落点（见
 * `app/src/test/java/com/ashkb/app/ui/components/AdaptiveLayoutTest.kt`）。
 */

/**
 * 趋势页小多图的**单格最小宽度**，喂给 `GridCells.Adaptive`。
 *
 * 原始缺陷：`GridCells.Fixed(2)` 硬编码两列。320dp 窄屏下每格只剩约 138dp，而格子
 * 里有标题、单位、读数行与 Y 轴刻度——字号放大到 2× 时这些文字会把画布挤到只剩几十 dp。
 * 改成 `Adaptive` 后由本函数给最小宽度，放不下就自动降为一列。
 *
 * 取值依据（页面边距 `Spacing.lg`×2、格间距 `Spacing.md`）：
 *   · 360dp 屏 → 可用 328dp；(328 − 12) / 2 = 158 ≥ 150 ⇒ **两列**（保持原有观感）
 *   · 320dp 屏 → 可用 288dp；(288 − 12) / 2 = 138 < 150 ⇒ **一列**（正是要的降级）
 *   · 字号 1.3× → 最小宽 195：360dp 屏需 2×195+12 = 402 > 328 ⇒ 一列（文字不再被压）
 * 上限取 1.5× 而非 2.0×：1.5× 时常见宽度已全部降到一列，再抬只会让宽屏也退成一列、
 * 在右侧留下大片空白，没有收益。
 */
internal fun trendGridMinCell(fontScale: Float): Dp {
    val scale = fontScale.coerceIn(MIN_SCALE, MAX_CELL_SCALE)
    return TREND_CELL_BASE * scale
}

/**
 * 健康页「体温 / 血压 / 心率」三连格是否要改成**上下排**。
 *
 * 原始缺陷：三个 `Box(Modifier.weight(1f))` 硬挤一行，320dp 屏每格约 77dp——2× 字号时
 * 「未记录」要折两行、数值被压到不可读。这三个数值是健康页的主要信息，不能靠缩字号解决。
 *
 * 判据用**可用宽度**（`BoxWithConstraints` 的 `maxWidth`）而不是屏幕宽度：卡片自身还有
 * 内边距，按屏幕宽度算会把「刚好放得下」误判成「放不下」。
 * 每格保底 [MIN_STAT_TILE] × 字号缩放；低于「三格 + 两条间距」就上下排。
 *
 * 边界（实测值见单测）：
 *   · 360dp 屏（可用 296dp）× 1.0 → 296 ≥ 294 ⇒ **一行**（三值横向可比，是这三个数字的主要价值）
 *   · 320dp 屏（可用 256dp）× 1.0 → 256 < 294 ⇒ 上下排
 *   · 360dp 屏 × 1.3 → 296 < 375 ⇒ 上下排
 */
internal fun stackThreeStats(availableWidth: Dp, fontScale: Float): Boolean {
    val minTile = MIN_STAT_TILE * fontScale.coerceIn(MIN_SCALE, MAX_TILE_SCALE)
    return availableWidth < minTile * 3 + Spacing.md * 2
}

/** 趋势小多图的单格基准宽度（见 [trendGridMinCell] 的宽度推算）。 */
private val TREND_CELL_BASE = 150.dp

/** 三连格每格的基准最小宽度：装得下「120/80」+ 单位。 */
private val MIN_STAT_TILE = 90.dp

/**
 * 单格宽度缩放的参与区间。下限 1.0 —— 系统字号调到 0.85× 时不该反过来把两列变三列
 * （那会让原本刚好的一行变得更挤，与「大字体才降级」的初衷相反）。
 */
private const val MIN_SCALE = 1.0f
private const val MAX_CELL_SCALE = 1.5f
private const val MAX_TILE_SCALE = 2.0f
