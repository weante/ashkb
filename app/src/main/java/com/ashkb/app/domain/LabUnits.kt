package com.ashkb.app.domain

import com.ashkb.app.data.entity.LabResult

/**
 * C3（v1.0.37）：化验「按单位分组 + 跨院数据仅供参考提示」。
 *
 * 同一项目在不同医院可能用不同单位（如 CRP `mg/L` vs `mg/dL`），数值**不可直接比较**。
 * 因此：① 列表按「项目名 + 单位」分组展示，避免把不同单位的值混在一列；
 * ② 同一项目存在多个单位时给出「跨院数据仅供参考」提示。
 *
 * 纯函数、无 Android 依赖，可单测。
 */
object LabUnits {

    /** 分组键：项目名 + 单位（单位空白归一为 null）。 */
    data class Group(val testName: String, val unit: String?, val results: List<LabResult>) {
        /** 组内是否跨单位（理论上同组不会，保留给调用方做防御性判断）。 */
        val mixedUnit: Boolean get() = LabUnits.isMixedUnit(results)
    }

    /** 按「项目名 + 单位」分组；组内按日期倒序，组间按项目名 / 单位排序。 */
    fun groupByUnit(results: List<LabResult>): List<Group> =
        results.groupBy { it.testName.trim() to normalizeUnit(it.unit) }
            .map { (key, values) -> Group(key.first, key.second, values.sortedByDescending { it.date }) }
            .sortedWith(compareBy({ it.testName }, { it.unit ?: "" }))

    /** 同一项目出现多个单位 → 返回这些项目名（用于列表顶部「跨院数据仅供参考」提示）。 */
    fun mixedUnitTests(results: List<LabResult>): List<String> =
        results.groupBy { it.testName.trim() }
            .filter { (_, values) -> values.map { normalizeUnit(it.unit) }.distinct().size > 1 }
            .keys
            .sorted()

    /** 给定项目下的结果是否跨单位。 */
    fun isMixedUnit(resultsForTest: List<LabResult>): Boolean =
        resultsForTest.map { normalizeUnit(it.unit) }.distinct().size > 1

    private fun normalizeUnit(unit: String?): String? = unit?.trim()?.ifBlank { null }
}
