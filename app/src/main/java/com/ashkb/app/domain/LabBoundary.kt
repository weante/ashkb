package com.ashkb.app.domain

/**
 * v1.2.11（缺陷工单 A06）：**化验值的越界判定**——边界包含与否决定用 `>` 还是 `>=`。
 *
 * 缺陷原状：`<5` 与 `≤5` 在导入解析时被压成同一个 `refHigh = 5`，判读一律按包含边界，
 * 于是 `<5` 被悄悄放宽成 `≤5`——真实异常值被标成正常，从趋势图与 PDF 里消失。
 * 现在解析器把「是否包含」落库（[com.ashkb.app.data.entity.LabResult.refHighInclusive] /
 * `refLowInclusive`），判读按列取值。
 *
 * 为什么抽成独立纯函数而不留在 `HealthRepository.saveLabResult` 里：
 * · 它是**唯一**的判读入口，四处展示（列表 / PDF / 趋势 / 导入预览）都该走同一口径，
 *   留在仓库里会让「谁来判」变成一个需要读大文件才能回答的问题；
 * · 纯函数可以直接单测边界（`<5` 时 5 必须 high、`≤5` 时 5 必须 normal），
 *   不必过 Robolectric + Room 才能验一个比较运算符。
 *
 * @return `"high"` / `"low"` / `"normal"`；`null` = 没有参考范围，本地判不了（调用方保留 AI 标记兜底）。
 */
internal object LabBoundary {

    /** 单侧越界判定：`high` 优先于 `low`（与既有 `when` 分支顺序一致）。 */
    fun judge(value: Double, refLow: Double?, refHigh: Double?, lowInclusive: Boolean, highInclusive: Boolean): String? {
        val overHigh = refHigh != null &&
            if (highInclusive) value > refHigh else value >= refHigh
        val underLow = refLow != null &&
            if (lowInclusive) value < refLow else value <= refLow
        return when {
            overHigh -> "high"
            underLow -> "low"
            refHigh != null || refLow != null -> "normal"
            else -> null
        }
    }
}
