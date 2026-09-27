package com.ashkb.app.domain

import java.time.LocalDateTime
import java.time.LocalTime

/**
 * v1.0.68 C8a：**久坐起身提醒**的时刻计算。
 *
 * 与 [ExerciseEngine] / [LifestylePrescription] 同一层：纯函数、无 Android 依赖、可单测。
 *
 * **为什么不是「每 N 分钟无条件响」**：那会在夜间和非工作时段持续打扰。
 * 这里把提醒约束在**活动窗口** `[startHour, endHour)` 内，按固定间隔在网格上触发：
 * 例如 9–18 点 + 45 分钟 → 9:00 / 9:45 / … / 17:30。
 *
 * **窗口结束后返回次日窗口起点**（而不是 null）：这样「一次只排下一个闹钟」的
 * 链式调度可以一直延续下去，不需要额外的每日重新武装。
 * 仅在窗口配置非法时才返回 null（视同功能关闭）。
 *
 * **与 B13 生活方式画像的关系**：本版**不消费** `Lifestyle.sedentaryHours`——
 * 提醒是用户的显式选择（默认关闭），不因画像自动开启；将来如需「久坐少则提示可不开启」，
 * 用画像数据在设置面板给一句提示即可，不需要改本对象的时刻计算。
 */
object SedentaryReminder {

    /** 可选间隔（分钟）。 */
    val INTERVAL_CHOICES = listOf(30, 45)

    const val DEFAULT_INTERVAL_MIN = 45
    const val DEFAULT_START_HOUR = 9
    const val DEFAULT_END_HOUR = 18

    /** 窗口是否合法（不合法 = 功能等效关闭）。 */
    fun isValidWindow(startHour: Int, endHour: Int): Boolean =
        startHour in 0..23 && endHour in 0..24 && startHour < endHour

    /**
     * 该时刻是否落在活动窗口 `[startHour, endHour)` 内。
     *
     * Receiver 用它做**陈旧闹钟兜底**：用户改窄窗口后，早先排下的闹钟可能落在窗口外，
     * 此时应静默丢弃并重排，而不是照响。
     */
    fun isWithinWindow(now: LocalDateTime, startHour: Int, endHour: Int): Boolean {
        if (!isValidWindow(startHour, endHour)) return false
        val h = now.toLocalTime().hour
        return h in startHour until endHour
    }

    /**
     * 下一次提醒时刻：**严格晚于** now 的第一个网格点；今日网格已走完则返回次日 [startHour]:00。
     *
     * @return null = 窗口配置非法（`startHour >= endHour` / 越界 / 间隔非正）
     */
    fun nextFire(
        now: LocalDateTime,
        startHour: Int = DEFAULT_START_HOUR,
        endHour: Int = DEFAULT_END_HOUR,
        intervalMin: Int = DEFAULT_INTERVAL_MIN,
    ): LocalDateTime? {
        if (intervalMin <= 0 || !isValidWindow(startHour, endHour)) return null
        val day = now.toLocalDate()
        val endMin = endHour * 60
        var minutes = startHour * 60
        while (minutes < endMin) {
            val fire = LocalDateTime.of(day, LocalTime.of(minutes / 60, minutes % 60))
            if (fire.isAfter(now)) return fire
            minutes += intervalMin
        }
        return LocalDateTime.of(day.plusDays(1), LocalTime.of(startHour, 0))
    }
}
