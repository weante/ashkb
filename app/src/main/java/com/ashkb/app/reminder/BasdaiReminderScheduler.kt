package com.ashkb.app.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.ashkb.app.data.entity.BasdaiRecord
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * BASDAI 问卷提醒调度（v1.0.59，B5 多源提醒之一）。
 *
 * 按用户配置周期（默认 28 天，存 `reminder_config` prefs 不跨备份恢复）排
 * 「评估日 20:00」+ 「+1/+2 天 20:00」两次升级重查（与用药升级链上限一致）。
 *
 * 提醒时刻选 20:00：晚上评估不影响睡眠，又留晚一些时间补做；不卡阈值——
 * 哪怕 < 4.0 也提示评估（自评频率而非结果判定），高活动度警报已有
 * `HealthRepository.evaluateBasdaiAlert` 在 `saveBasdai` 内做。
 *
 * 与 [ReminderScheduler] 同模式：稳定 hashCode requestCode、setExactAndAllowWhileIdle
 * 降级 setWindow、now 可注入便于测试。
 *
 * **Receiver 兜底**：latest 改后旧闹钟可能残留（hashCode 不同），由 [BasdaiReminderReceiver]
 * 收到时重算 nextDueDate 比对——extra dueDate 与重算值不等则取消。
 */
object BasdaiReminderScheduler {
    /** 默认 BASDAI 评估周期（医学上推荐每 4 周自评）。 */
    const val DEFAULT_CYCLE_DAYS = 28L

    /** 升级链上限（与用药一致：+1/+2 天各一次）。 */
    const val MAX_ESCALATION = 2

    /** 20:00 提醒时刻。 */
    private const val HOUR = 20
    private const val MINUTE = 0

    data class Slot(val dueDate: LocalDate, val escalation: Int, val fire: LocalDateTime)

    /**
     * 纯函数：推算下次评估日。
     *  - latest == null → today（建档后立即提示首次评估）
     *  - latest.date + cycle ≤ today → today（已逾期，今日提示）
     *  - 否则 → latest.date + cycle
     */
    fun nextDueDate(latest: BasdaiRecord?, cycleDays: Long, today: LocalDate): LocalDate {
        if (latest == null) return today
        val lastDate = runCatching { LocalDate.parse(latest.date) }.getOrNull() ?: return today
        val due = lastDate.plusDays(cycleDays)
        return if (!due.isAfter(today)) today else due
    }

    /**
     * 纯函数：排 dueDate 20:00 + +1/+2 天 20:00 升级重查（仅未过点）。
     */
    fun slotsFor(dueDate: LocalDate, now: LocalDateTime): List<Slot> {
        val out = mutableListOf<Slot>()
        for (esc in 0..MAX_ESCALATION) {
            val day = dueDate.plusDays(esc.toLong())
            val fire = LocalDateTime.of(day, LocalTime.of(HOUR, MINUTE))
            if (fire.isAfter(now.minusMinutes(1))) out.add(Slot(dueDate, esc, fire))
        }
        return out
    }

    fun rescheduleAll(
        context: Context,
        latest: BasdaiRecord?,
        cycleDays: Long,
        today: LocalDate = LocalDate.now(),
        now: LocalDateTime = LocalDateTime.now(),
    ) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        cancelAllFuture(context, latest, cycleDays, today)
        val due = nextDueDate(latest, cycleDays, today)
        for (slot in slotsFor(due, now)) {
            schedule(context, am, slot.dueDate, slot.escalation, slot.fire)
        }
    }

    fun cancelAllFuture(
        context: Context,
        latest: BasdaiRecord?,
        cycleDays: Long,
        today: LocalDate = LocalDate.now(),
    ) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        // 取消新算 dueDate 对应的闹钟——latest 变更前的旧闹钟（dueDate 不同）
        // 由 BasdaiReminderReceiver 兜底验真取消
        val due = nextDueDate(latest, cycleDays, today)
        for (esc in 0..MAX_ESCALATION) {
            am.cancel(pending(context, due, esc))
        }
    }

    private fun schedule(
        context: Context, am: AlarmManager,
        dueDate: LocalDate, escalation: Int, fireAt: LocalDateTime,
    ) {
        val pi = pending(context, dueDate, escalation)
        val at = fireAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val exact = if (Build.VERSION.SDK_INT >= 31) am.canScheduleExactAlarms() else true
        runCatching {
            if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else am.setWindow(AlarmManager.RTC_WAKEUP, at, 15 * 60_000L, pi)
        }
    }

    private fun pending(
        context: Context, dueDate: LocalDate, escalation: Int,
    ): PendingIntent {
        val intent = Intent(context, BasdaiReminderReceiver::class.java).apply {
            putExtra(BasdaiReminderReceiver.EXTRA_DUE_DATE, dueDate.toString())
            putExtra(BasdaiReminderReceiver.EXTRA_ESCALATION, escalation)
        }
        return PendingIntent.getBroadcast(
            context, reqCode(dueDate, escalation), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun reqCode(dueDate: LocalDate, escalation: Int): Int =
        ("bas|$dueDate|$escalation").hashCode()
}
