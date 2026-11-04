package com.ashkb.app.ui.components

import androidx.compose.ui.unit.dp
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.2.15（外部审查 §U-2）：窄屏 / 大字号下的**版式判据**。
 *
 * ### 为什么这两条要抽成纯函数再测
 * 缺陷本身（320dp 屏或 2× 字号下两列小多图挤成一条、三连格把数值压到不可读）只有真机
 * 截图能证明；但「在哪个宽度上该降级」是一个确定的算术，把它写成 `(fontScale, 宽度) -> 决策`
 * 之后，边界就不再靠肉眼试。`GridCells.Adaptive` 与 `Row(weight)` 的分支结果无法在 JVM
 * 里断言（本模块无 Compose UI 测试依赖，Robolectric 量到的字体宽度是 1.0dp），
 * 所以可守的那一半就在这里。
 *
 * ### 这些用例能挡住什么
 *  · 把基准宽度改成随字号**反向**缩放（大字反而塞更多列）；
 *  · 把 `coerceIn` 的下限去掉（系统字号 0.85× 时三连格变一行更窄）；
 *  · 把上限抬到 2.0× 之外（宽屏也退成一列，右侧全空）；
 *  · 把三连格的判据从「可用宽度」换成「屏幕宽度」（卡片内边距没扣，边界整体偏移）。
 *
 * 挡不住的：`BoxWithConstraints` 实际给出的 `maxWidth`、以及降级后文字是否真的不折行。
 */
class AdaptiveLayoutTest {

    /** 两列版式在给定可用宽度下每格分到多少（`Spacing.md` = 12dp 为格间距）。 */
    private fun cellWidth(available: Float): Float = (available - 12f) / 2f

    private fun assertNear(expected: Float, actual: Float) {
        assertTrue("期望 $expected，实际 $actual", abs(expected - actual) < 0.01f)
    }

    // ---- trendGridMinCell：小多图单格最小宽 ----

    @Test
    fun `默认字号下基准宽度就是 150dp`() {
        assertEquals("1.0× 时不该对基准值做任何缩放", 150.dp, trendGridMinCell(1.0f))
    }

    @Test
    fun `360dp 屏在默认字号下仍然两列`() {
        // 页面左右各 Spacing.lg = 16dp，故可用 328dp
        assertTrue(
            "360dp 屏（可用 328dp）两列时每格 158dp，必须 ≥ 单格最小宽，否则常规机型会无端降成一列",
            cellWidth(328f) >= trendGridMinCell(1.0f).value,
        )
    }

    @Test
    fun `320dp 屏在默认字号下降级为单列`() {
        // 可用 288dp；(288 − 12) / 2 = 138 < 150 ⇒ 一列
        assertTrue(
            "320dp 窄屏两列时每格仅 138dp，带标题与刻度已放不下——这正是要降级的场景",
            cellWidth(288f) < trendGridMinCell(1.0f).value,
        )
    }

    @Test
    fun `字号放大后单格最小宽按比例上升`() {
        assertTrue("1.3× 时最小宽必须大于 1.0×", trendGridMinCell(1.3f) > trendGridMinCell(1.0f))
        assertNear(195f, trendGridMinCell(1.3f).value)
    }

    @Test
    fun `字号放大到 1_3 倍时 360dp 屏也降为单列`() {
        assertTrue(
            "两列需 2 × 195 + 12 = 402dp > 可用 328dp ⇒ 必须降级，否则文字被压扁",
            cellWidth(328f) < trendGridMinCell(1.3f).value,
        )
    }

    @Test
    fun `缩放有上限，避免宽屏也被迫单列`() {
        assertEquals(
            "2.5× 字号不该让单格最小宽无限增长（宽屏会退成一列、右侧大片留白）",
            trendGridMinCell(1.5f),
            trendGridMinCell(2.5f),
        )
    }

    @Test
    fun `字号调小时不反向增多列数`() {
        assertEquals(
            "系统字号 0.85× 时不该把两列变三列——那与「大字体才降级」的初衷相反",
            trendGridMinCell(1.0f),
            trendGridMinCell(0.85f),
        )
    }

    // ---- stackThreeStats：体温 / 血压 / 心率 是否上下排 ----

    @Test
    fun `360dp 屏默认字号下三格并排`() {
        // 360 − 32（页面边距）= 328，卡片内边距再扣，实测可用约 296dp；三格需 90 × 3 + 12 × 2 = 294
        assertTrue(
            "三个数值横向可比正是它们的主要价值，360dp 屏上必须保持一行",
            !stackThreeStats(availableWidth = 296.dp, fontScale = 1.0f),
        )
    }

    @Test
    fun `320dp 屏默认字号下改为上下排`() {
        // 320 − 32 = 288，扣卡片内边距后约 256dp < 294
        assertTrue(
            "256dp 分三格每格只剩 77dp，「未记录」会折两行、数值被压到不可读",
            stackThreeStats(availableWidth = 256.dp, fontScale = 1.0f),
        )
    }

    @Test
    fun `360dp 屏字号 1_3 倍时改为上下排`() {
        // 90 × 1.3 × 3 + 24 = 375 > 296
        assertTrue(
            "大字号下三格并排会把每格压到 90dp 以下，必须让它们上下排",
            stackThreeStats(availableWidth = 296.dp, fontScale = 1.3f),
        )
    }

    @Test
    fun `边界值恰好在阈值上时仍并排`() {
        // 恰好 90 × 3 + 12 × 2 = 294 ⇒ 不小于阈值，保持一行（用 < 而不是 <=）
        assertTrue(
            "阈值用严格小于：正好放得下时不该白白降级",
            !stackThreeStats(availableWidth = 294.dp, fontScale = 1.0f),
        )
        assertTrue(
            "差 1dp 就放不下，必须降级",
            stackThreeStats(availableWidth = 293.dp, fontScale = 1.0f),
        )
    }

    @Test
    fun `三连格的缩放上限高于两列版式`() {
        assertEquals(
            "字号 2.5× 时三格判据不该继续放大（此时任何手机都已是窄屏，上下排已成定局）",
            stackThreeStats(availableWidth = 200.dp, fontScale = 2.0f),
            stackThreeStats(availableWidth = 200.dp, fontScale = 2.5f),
        )
    }
}
