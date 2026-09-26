package com.ashkb.app.domain

import java.time.LocalDate

/**
 * v1.0.65 B12：极简模式状态机（红线三 e2：**发作期输入减负**）。
 *
 * **触发链路**：连续 [THRESHOLD_DAYS] 天没有「核心记录」→ 询问原因 →
 * 若为「身体不适 / 住院」则切极简模式（界面只留核心项）。
 *
 * **「核心记录」的口径（本版拍板）**：`symptom_daily`（症状日记录）。
 * 理由：它是本 App 唯一**每日必填且与用药清单无关**的自评入口，客观可判定（按 `date` 存在性）。
 * 不选「用药打卡」——无在用药品时它天然为空，会把「没药可吃」误判成「没记录」；
 * 也不选「运动打卡」——运动本就非每日必做。
 *
 * 纯函数、无 Android 依赖（同 [LifestylePrescription]），文案与 IO 由调用方负责。
 */
object MinimalMode {

    /** 连续未记录天数阈值。 */
    const val THRESHOLD_DAYS = 3

    const val MODE_NORMAL = "normal"
    const val MODE_MINIMAL = "minimal"

    /** 询问原因的可选值。前两项进极简模式，`other` 不进。 */
    const val REASON_ILLNESS = "illness"
    const val REASON_HOSPITAL = "hospital"
    const val REASON_OTHER = "other"

    val REASONS = listOf(REASON_ILLNESS, REASON_HOSPITAL, REASON_OTHER)

    /**
     * 该原因是否应切入极简模式。
     * 只有「身体不适 / 住院」——`other`（如单纯不想记录）不该改界面，那会掩盖真实的数据缺口。
     */
    fun entersMinimal(reason: String): Boolean =
        reason == REASON_ILLNESS || reason == REASON_HOSPITAL

    /**
     * 从「有核心记录的日期集合」反推**连续缺失天数**（含今天，向前数）。
     *
     * 计数在命中第一个有记录的日期时停止；**上限封顶为 [THRESHOLD_DAYS]**——
     * 调用方只关心「够不够触发」，不需要知道到底缺了 3 天还是 30 天，
     * 封顶也让遍历与输入规模无关（只查近几天即可）。
     */
    fun missingStreakDays(recordedDates: Set<String>, today: LocalDate): Int {
        var days = 0
        var cursor = today
        while (days < THRESHOLD_DAYS) {
            if (cursor.toString() in recordedDates) break
            days++
            cursor = cursor.minusDays(1)
        }
        return days
    }

    /** 是否达到询问阈值。 */
    fun shouldPrompt(recordedDates: Set<String>, today: LocalDate): Boolean =
        missingStreakDays(recordedDates, today) >= THRESHOLD_DAYS

    /** 状态的成对不变式：极简态必须有进入时刻，普通态必须没有。 */
    fun isConsistent(uiMode: String?, minimalSince: String?): Boolean = when (uiMode) {
        MODE_MINIMAL -> !minimalSince.isNullOrBlank()
        else -> minimalSince.isNullOrBlank()
    }
}
