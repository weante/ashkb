package com.ashkb.app.domain

import com.ashkb.app.data.entity.ExerciseLog
import com.ashkb.app.data.entity.ExercisePlan
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * B7（v1.0.39）：周期康复计划的**完成度反算**。
 *
 * 进度不另存（避免双份真相）：直接由 `exercise_logs` 中 `status="done"` 的**去重日期**
 * 与计划的周窗口比较得出。
 *
 * 纯函数、无 Android 依赖，可单测。
 */
object ExercisePlanProgress {

    data class Progress(
        /** 当前处于第几周（1..weeks；已超出总周数时钳到 weeks） */
        val currentWeek: Int,
        val totalWeeks: Int,
        /** 本周目标天数（来自 week_structure） */
        val weekTargetDays: Int,
        /** 本周已完成天数 */
        val weekDoneDays: Int,
        /** 整个周期已完成天数 */
        val overallDoneDays: Int,
        /** 整个周期目标天数（各周 days 之和） */
        val overallTargetDays: Int,
        /** 完成百分比 0..100 */
        val pct: Int,
        /** 是否已走完总周数 */
        val finished: Boolean,
    )

    /**
     * @param plan 计划（`startDate` 为空时视为今天开始）
     * @param spec 已解析的每周结构
     * @param logs 运动日志（只用 status="done" 的日期）
     * @param today 计算基准日
     */
    fun of(
        plan: ExercisePlan,
        spec: List<ExercisePlanTemplates.WeekSpec>,
        logs: List<ExerciseLog>,
        today: LocalDate,
    ): Progress {
        val totalWeeks = plan.weeks.coerceAtLeast(1)
        val start = runCatching { LocalDate.parse(plan.startDate) }.getOrNull() ?: today
        val daysSince = ChronoUnit.DAYS.between(start, today)
        val rawWeek = if (daysSince < 0) 1 else (daysSince / 7).toInt() + 1
        val currentWeek = rawWeek.coerceIn(1, totalWeeks)
        val finished = daysSince >= totalWeeks * 7L

        // 计划窗口内的完成日期（去重）
        val windowEnd = start.plusDays(totalWeeks * 7L - 1)
        val doneDates = logs
            .filter { it.status == "done" }
            .mapNotNull { runCatching { LocalDate.parse(it.date) }.getOrNull() }
            .filter { !it.isBefore(start) && !it.isAfter(windowEnd) }
            .toSet()

        val weekStart = start.plusDays((currentWeek - 1) * 7L)
        val weekEnd = weekStart.plusDays(6)
        val weekDone = doneDates.count { !it.isBefore(weekStart) && !it.isAfter(weekEnd) }

        val overallTarget = spec.sumOf { it.days }
        val overallDone = doneDates.size
        val pct = if (overallTarget <= 0) 0 else (overallDone * 100 / overallTarget).coerceIn(0, 100)

        return Progress(
            currentWeek = currentWeek,
            totalWeeks = totalWeeks,
            weekTargetDays = ExercisePlanTemplates.targetDays(spec, currentWeek),
            weekDoneDays = weekDone,
            overallDoneDays = overallDone,
            overallTargetDays = overallTarget,
            pct = pct,
            finished = finished,
        )
    }
}
