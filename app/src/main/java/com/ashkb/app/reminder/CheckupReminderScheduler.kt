package com.ashkb.app.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.ashkb.app.data.entity.CheckupRecord
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * 复诊提醒调度（v1.0.59，B5 多源提醒之一）。
 *
 * 从 [CheckupRecord.nextDate] 抽取未来 7 天内的复诊日，排：
 *  - **提前 1 天 09:00**「准备清单」提醒（prep）：留空腹 / 带既往单据的时间
 *  - **当日 09:00**「今天复诊」提醒（day）
 *
 * 复诊无升级链——提前 1 天已是二次提醒。其余与 [ReminderScheduler] 同模式：
 * 稳定 hashCode requestCode、setExactAndAllowWhileIdle 降级 setWindow、now 可注入便于测试。
 *
 * **Receiver 兜底**：nextDate 改后旧闹钟可能残留（hashCode 不同），由 [CheckupReminderReceiver]
 * 收到时查库验真——extra nextDate 与库内 active nextDate 不匹配则取消。
 */
object CheckupReminderScheduler {
    const val HORIZON_DAYS = 7L

    const val PHASE_PREP = "prep"
    const val PHASE_DAY = "day"

    /** 09:00 提醒时刻——早晨既不早打扰，又留足当日准备时间。 */
    private const val HOUR = 9
    private const val MINUTE = 0

    data class Slot(val nextDate: LocalDate, val phase: String, val fire: LocalDateTime)

    /**
     * 纯函数：从复诊记录抽取未来 7 天内的提醒槽。
     *
     * @param records 全部复诊记录（含历史）；按 nextDate 去重取每个日期
     * @param today 今天
     * @param now 「现在」——过滤已过点的提醒（允许 1 分钟容差，与 [ReminderScheduler] 一致）
     */
    fun slotsFor(
        records: List<CheckupRecord>,
        today: LocalDate,
        now: LocalDateTime,
    ): List<Slot> {
        val horizon = today.plusDays(HORIZON_DAYS)
        val upcoming = records.mapNotNull { it.nextDate?.takeIf { d -> d.isNotBlank() } }
            .mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }
            .filterTo(mutableSetOf()) { !it.isBefore(today) && !it.isAfter(horizon) }
            .sorted()

        val out = mutableListOf<Slot>()
        for (nextDate in upcoming) {
            if (nextDate == today) {
                // 当日复诊：只排 day 提醒（prep 已过点）
                val fire = LocalDateTime.of(nextDate, LocalTime.of(HOUR, MINUTE))
                if (fire.isAfter(now.minusMinutes(1))) out.add(Slot(nextDate, PHASE_DAY, fire))
                continue
            }
            val prepFire = LocalDateTime.of(nextDate.minusDays(1), LocalTime.of(HOUR, MINUTE))
            if (prepFire.isAfter(now.minusMinutes(1))) out.add(Slot(nextDate, PHASE_PREP, prepFire))
            val dayFire = LocalDateTime.of(nextDate, LocalTime.of(HOUR, MINUTE))
            if (dayFire.isAfter(now.minusMinutes(1))) out.add(Slot(nextDate, PHASE_DAY, dayFire))
        }
        return out
    }

    /**
     * 重排「今日 + 未来 7 天」内的复诊提醒。先 [cancelAllFuture] 取消旧闹钟，再按 [slotsFor] 重排。
     * 幂等：requestCode 稳定，重复调用结果一致。
     */
    fun rescheduleAll(
        context: Context,
        records: List<CheckupRecord>,
        today: LocalDate = LocalDate.now(),
        now: LocalDateTime = LocalDateTime.now(),
    ) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        cancelAllFuture(context, records)
        for (slot in slotsFor(records, today, now)) {
            schedule(context, am, slot.nextDate, slot.phase, slot.fire)
        }
    }

    /**
     * 取消 records 中每个 nextDate 对应的 prep/day 闹钟。
     *
     * 注：旧 nextDate 改后已不在 records 列表里的闹钟由 [CheckupReminderReceiver] 兜底验真取消。
     */
    fun cancelAllFuture(context: Context, records: List<CheckupRecord>) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val allDates = records.mapNotNull { it.nextDate?.takeIf { d -> d.isNotBlank() } }
            .mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }
            .toSet()
        for (nextDate in allDates) {
            am.cancel(pending(context, nextDate, PHASE_PREP))
            am.cancel(pending(context, nextDate, PHASE_DAY))
        }
    }

    private fun schedule(
        context: Context, am: AlarmManager,
        nextDate: LocalDate, phase: String, fireAt: LocalDateTime,
    ) {
        val pi = pending(context, nextDate, phase)
        val at = fireAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val exact = if (Build.VERSION.SDK_INT >= 31) am.canScheduleExactAlarms() else true
        runCatching {
            if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else am.setWindow(AlarmManager.RTC_WAKEUP, at, 15 * 60_000L, pi)
        }
    }

    private fun pending(
        context: Context, nextDate: LocalDate, phase: String,
    ): PendingIntent {
        val intent = Intent(context, CheckupReminderReceiver::class.java).apply {
            putExtra(CheckupReminderReceiver.EXTRA_NEXT_DATE, nextDate.toString())
            putExtra(CheckupReminderReceiver.EXTRA_PHASE, phase)
        }
        return PendingIntent.getBroadcast(
            context, reqCode(nextDate, phase), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun reqCode(nextDate: LocalDate, phase: String): Int =
        ("chk|$nextDate|$phase").hashCode()
}
