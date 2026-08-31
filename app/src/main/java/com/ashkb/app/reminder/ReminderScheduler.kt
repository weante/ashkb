package com.ashkb.app.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.ashkb.app.data.entity.Medication
import com.ashkb.app.domain.ScheduleCalc
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 提醒调度：为「今日剩余 + 未来 7 天」的每个计划槽位设置精确闹钟。
 * request code = (medId + date + slotKey) 稳定哈希——重排幂等，覆盖旧闹钟。
 * 精确闹钟不可用时降级 setWindow（15 分钟窗口）。
 */
object ReminderScheduler {
    const val HORIZON_DAYS = 7L

    fun rescheduleAll(context: Context, meds: List<Medication>) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        cancelAllFuture(context, meds)
        val today = LocalDate.now()
        for (dayOffset in 0..HORIZON_DAYS) {
            val date = today.plusDays(dayOffset)
            for (med in meds) {
                for (slot in ScheduleCalc.slotsFor(med, date)) {
                    val time = slot.time ?: continue
                    val fire = LocalDateTime.of(date, java.time.LocalTime.parse(time))
                    if (fire.isBefore(LocalDateTime.now().minusMinutes(1))) continue
                    schedule(context, am, med.id, slot.key, time, fire, escalation = 0)
                }
            }
        }
    }

    /** 升级重查：+30 分钟后未打卡 → 重复提醒（M10 升级链，上限 2 次重查） */
    fun scheduleEscalation(context: Context, medId: String, slotKey: String?, slotTime: String?, fireAt: LocalDateTime, escalation: Int) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        schedule(context, am, medId, slotKey, slotTime, fireAt, escalation)
    }

    private fun schedule(
        context: Context, am: AlarmManager,
        medId: String, slotKey: String?, slotTime: String?,
        fireAt: LocalDateTime, escalation: Int,
    ) {
        val pi = pending(context, medId, slotKey, slotTime, fireAt, escalation)
        val at = fireAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val exact = if (Build.VERSION.SDK_INT >= 31) am.canScheduleExactAlarms() else true
        runCatching {
            if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else am.setWindow(AlarmManager.RTC_WAKEUP, at, 15 * 60_000L, pi)
        }
    }

    fun cancelAllFuture(context: Context, meds: List<Medication>) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val today = LocalDate.now()
        for (dayOffset in 0..HORIZON_DAYS) {
            val date = today.plusDays(dayOffset)
            for (med in meds) {
                for (slot in ScheduleCalc.slotsFor(med, date)) {
                    val time = slot.time ?: continue
                    for (esc in 0..2) {
                        val fire = LocalDateTime.of(date, java.time.LocalTime.parse(time)).plusMinutes(30L * esc)
                        am.cancel(pending(context, med.id, slot.key, time, fire, esc))
                    }
                }
            }
        }
    }

    private fun pending(
        context: Context, medId: String, slotKey: String?, slotTime: String?,
        fireAt: LocalDateTime, escalation: Int,
    ): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(ReminderReceiver.EXTRA_MED_ID, medId)
            putExtra(ReminderReceiver.EXTRA_SLOT_KEY, slotKey)
            putExtra(ReminderReceiver.EXTRA_SLOT_TIME, slotTime)
            putExtra(ReminderReceiver.EXTRA_ESCALATION, escalation)
            putExtra(ReminderReceiver.EXTRA_FIRE_ISO, fireAt.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME))
        }
        return PendingIntent.getBroadcast(
            context, reqCode(medId, slotKey, fireAt, escalation), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun reqCode(medId: String, slotKey: String?, fireAt: LocalDateTime, escalation: Int): Int =
        ("$medId|$slotKey|${fireAt.toLocalDate()}|$escalation").hashCode()
}
