package com.ashkb.app.domain

import com.ashkb.app.data.entity.Supplement

/**
 * B11（v1.0.38）：营养素「每日上限警示」。
 *
 * ⚠️ **刻意不内置任何医学上限数值**——上限由用户在补剂里自行填写（医嘱 / 营养师给的值），
 * App 只做「当日累计 vs 上限」的算术比较与提示，不代替专业判断。
 *
 * 纯函数、无 Android 依赖，可单测。
 */
object SupplementLimits {

    /**
     * 每日服用次数：优先按 `times`（形如 `"08:00,20:00"`）的槽位数，
     * 否则按 frequency 粗判（daily=1 / bid=2 / tid=3）。
     */
    fun dosesPerDay(sup: Supplement): Int {
        val slots = sup.times?.split(',')?.count { it.trim().isNotBlank() } ?: 0
        if (slots > 0) return slots
        return when (sup.frequency.lowercase()) {
            "bid", "twice_daily" -> 2
            "tid" -> 3
            else -> 1
        }
    }

    /** 当日累计剂量 = 单次剂量 × 每日次数；缺任一要素返回 null（表示无法判断，不警示）。 */
    fun dailyTotal(sup: Supplement): Double? {
        val per = sup.doseAmount ?: return null
        if (per <= 0) return null
        return per * dosesPerDay(sup)
    }

    /**
     * 是否超上限。
     * @return null = 无法判断（未填上限 / 未填单次剂量 / 上限非正）；true/false = 是否超限
     */
    fun exceedsDailyMax(sup: Supplement): Boolean? {
        val max = sup.dailyMax?.takeIf { it > 0 } ?: return null
        val total = dailyTotal(sup) ?: return null
        return total > max
    }

    /** 单位缺失时的展示兜底。 */
    fun unitLabel(sup: Supplement): String = sup.doseUnit?.trim()?.ifBlank { null } ?: ""
}
