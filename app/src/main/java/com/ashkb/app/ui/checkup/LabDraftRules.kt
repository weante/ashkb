package com.ashkb.app.ui.checkup

/**
 * v1.2.11（缺陷工单 A07）：化验表单的输入校验。
 *
 * 为什么单独抽出来：这几个判据此前**根本不存在**——`LabDraft.toRecord` 直接
 * `value.trim().toDoubleOrNull()`，非法输入静默变成 null 落库；`refLow > refHigh`
 * 也能存进去，随后 `HealthRepository.saveLabResult` 会拿这对反向边界算出
 * 「`value > refHigh` → high」这种伪异常，用户看到的是一个凭空冒出来的「偏高」。
 * 抽成纯函数才能纯 JVM 单测（表单本身是 Compose，Robolectric 也验不出判据）。
 *
 * 判据只覆盖**能在本地确定**的错：数字格式与反向区间。
 * 「单位与参考范围明显不一致」（工单 A07 第 5 条的后半句）**刻意不判**——
 * 单位是自由文本（`mg/L`、`10^9/L`、`mm/h` 都合法），本地没有任何权威映射能区分
 * 「单位写错」与「单位罕见」，硬判只会把正确输入挡在门外。这条留给后续有数据源时再做。
 */
internal object LabDraftRules {

    /** 留空合法（= 未填）；非空必须是数字。 */
    fun numberOrNull(raw: String): Double? = raw.trim().toDoubleOrNull()

    /** 非空且不是数字 —— 表单该报字段错、且不许保存。 */
    fun invalidNumber(raw: String): Boolean = raw.isNotBlank() && numberOrNull(raw) == null

    /** 两侧都填了数字、且下限 > 上限。只填一侧时不算错（单侧范围是合法形态）。 */
    fun reversedRange(refLow: String, refHigh: String): Boolean {
        val lo = numberOrNull(refLow) ?: return false
        val hi = numberOrNull(refHigh) ?: return false
        return lo > hi
    }

    /** 保存按钮的启用条件（与字段错提示共用同一组判据，避免「红框了却还能存」）。 */
    fun canSave(testName: String, value: String, refLow: String, refHigh: String): Boolean =
        testName.isNotBlank() &&
            !invalidNumber(value) &&
            !invalidNumber(refLow) &&
            !invalidNumber(refHigh) &&
            !reversedRange(refLow, refHigh)
}
