package com.ashkb.app.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.ashkb.app.data.entity.ExercisePlan
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * 运动提醒调度（v1.0.59，B5 多源提醒之一）。
 *
 * 今日有处方但傍晚未完成时排 **18:00 首次提醒** + **20:00 / 22:00 升级重查**（各 +2h）。
 *
 * 无 [ExercisePlan] 启用 / 今日已 [com.ashkb.app.data.entity.ExerciseLog] → 不排。
 * 不依赖「今日处方是否完成」——处方是 `ExerciseEngine.todayPlan()` 算出的可执行项，
 * 是否「完成」用户说了算，不强制打卡数量。
 *
 * 与 [ReminderScheduler] 同模式：稳定 hashCode requestCode、setExactAndAllowWhileIdle
 * 降级 setWindow、now 可注入便于测试。
 *
 * **跨日无遗留**：requestCode 用 `date|esc`，明天又是新的链；今日跨日后旧闹钟已触发完成。
 */
object ExerciseReminderScheduler {
    const val MAX_ESCALATION = 2

    /** 18:00 首次提醒时刻——傍晚既留够白天活动时间，又不太晚打扰晚餐。 */
    private const val BASE_HOUR = 18
    private const val BASE_MINUTE = 0

    /** 每级升级间隔（分钟）——+2h：20:00 / 22:00。 */
    const val ESCALATION_STEP_MINUTES = 120L

    data class Slot(val date: LocalDate, val escalation: Int, val fire: LocalDateTime)

    /**
     * 纯函数：排今日 18:00 + 20:00 / 22:00 升级重查（仅未过点）。
     *  - plan == null || !plan.isActive → 不排
     *  - hasLoggedToday → 不排（今日已完成）
     *  - 已过点（fire ≤ now-1min）→ 不排
     */
    fun slotsFor(
        plan: ExercisePlan?,
        hasLoggedToday: Boolean,
        today: LocalDate,
        now: LocalDateTime,
    ): List<Slot> {
        if (plan == null || !plan.isActive || hasLoggedToday) return emptyList()
        val out = mutableListOf<Slot>()
        for (esc in 0..MAX_ESCALATION) {
            val fire = LocalDateTime.of(today, LocalTime.of(BASE_HOUR, BASE_MINUTE))
                .plusMinutes(ESCALATION_STEP_MINUTES * esc)
            if (fire.isAfter(now.minusMinutes(1))) out.add(Slot(today, esc, fire))
        }
        return out
    }

    fun rescheduleAll(
        context: Context,
        plan: ExercisePlan?,
        hasLoggedToday: Boolean,
        today: LocalDate = LocalDate.now(),
        now: LocalDateTime = LocalDateTime.now(),
    ) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        cancelAllFuture(context, today)
        for (slot in slotsFor(plan, hasLoggedToday, today, now)) {
            schedule(context, am, slot.date, slot.escalation, slot.fire)
        }
    }

    fun cancelAllFuture(context: Context, today: LocalDate = LocalDate.now()) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        for (esc in 0..MAX_ESCALATION) {
            am.cancel(pending(context, today, esc))
        }
    }

    private fun schedule(
        context: Context, am: AlarmManager,
        date: LocalDate, escalation: Int, fireAt: LocalDateTime,
    ) {
        val pi = pending(context, date, escalation)
        val at = fireAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val exact = if (Build.VERSION.SDK_INT >= 31) am.canScheduleExactAlarms() else true
        runCatching {
            if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else am.setWindow(AlarmManager.RTC_WAKEUP, at, 15 * 60_000L, pi)
        }
    }

    private fun pending(
        context: Context, date: LocalDate, escalation: Int,
    ): PendingIntent {
        val intent = Intent(context, ExerciseReminderReceiver::class.java).apply {
            putExtra(ExerciseReminderReceiver.EXTRA_DATE, date.toString())
            putExtra(ExerciseReminderReceiver.EXTRA_ESCALATION, escalation)
        }
        return PendingIntent.getBroadcast(
            context, reqCode(date, escalation), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun reqCode(date: LocalDate, escalation: Int): Int =
        ("exc|$date|$escalation").hashCode()
}
