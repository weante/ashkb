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
import java.time.LocalTime
import java.time.ZoneId

/**
 * 提醒调度：为「今日剩余 + 未来 7 天」的每个计划槽位设置精确闹钟。
 * request code = (medId + date + slotKey) 稳定哈希——重排幂等，覆盖旧闹钟。
 * 精确闹钟不可用时降级 setWindow（15 分钟窗口）。
 */
object ReminderScheduler {
    const val HORIZON_DAYS = 7L

    /** M10 升级链：未确认后最多重查次数（+30 / +60 分钟各一次）。 */
    const val MAX_ESCALATION = 2

    /** 每级升级重查的间隔（分钟）。 */
    const val ESCALATION_STEP_MINUTES = 30L

    /** 槽位标识（`medId|slotKey`）：用于「今日已打卡槽位」集合，维度与 request code 一致。 */
    fun slotRef(medId: String?, slotKey: String?): String = "$medId|$slotKey"

    /**
     * 重排「今日剩余 + 未来 7 天」的提醒。
     *
     * v1.0.43 修复：本方法先 `cancelAllFuture` 取消全部闹钟（含升级重查），所以必须**同时重建**
     * 今日「已过点但未打卡」槽位尚未到时的升级重查。此前只重建 `escalation = 0` 且跳过已过时刻，
     * 于是每次冷启动 / 打卡 / 改药单都会把当天后续的 +30 / +60 提醒静默清掉（M10 升级链失效）。
     *
     * @param doneSlotRefs 今日已打卡的槽位（[slotRef] 形态）——这些不再重建升级重查。
     *   **刻意不设默认值**（v1.0.44）：v1.0.43 引入本参数时给了 `emptySet()` 默认值，
     *   结果开机广播那条调用路径漏传，重启后已服药的槽位仍会被排上 +30 / +60 误提醒。
     *   去掉默认值后，任何新增调用点都必须显式想一次「哪些槽位今天已完成」。
     * @param now 「现在」。刻意留成可注入参数（默认真实当下）以便**确定性地**回归本方法：
     *   本方法的可观察效果完全由「现在」决定（哪些槽位已过点、哪些升级重查还没到），
     *   而 Robolectric **改不动 `java.time` 的挂钟**（`ShadowSystemClock` 只影响
     *   `SystemClock`，实测 `advanceBy` 后 `LocalDateTime.now()` 不变），
     *   所以只能从调用侧注入，测试才能在任何时刻运行都不漂移、不静默跳过。
     *   这与 `ScheduleCalc.slotsFor(med, date)` 显式传入日期的风格一致。
     */
    fun rescheduleAll(
        context: Context,
        meds: List<Medication>,
        doneSlotRefs: Set<String>,
        now: LocalDateTime = LocalDateTime.now(),
    ) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        cancelAllFuture(context, meds, now.toLocalDate())
        val today = now.toLocalDate()

        // 1) 未来（含今日未到点）槽位：按 esc=0 排首次提醒
        for (dayOffset in 0..HORIZON_DAYS) {
            val date = today.plusDays(dayOffset)
            for (med in meds) {
                for (slot in ScheduleCalc.slotsFor(med, date)) {
                    val time = slot.time ?: continue
                    val fire = LocalDateTime.of(date, LocalTime.parse(time))
                    if (fire.isBefore(now.minusMinutes(1))) continue
                    schedule(context, am, med.id, slot.key, time, fire, escalation = 0)
                }
            }
        }

        // 2) 今日已过点但未打卡的槽位：重建尚未到时的升级重查（幂等：request code 稳定，覆盖旧值）
        for (med in meds) {
            for (slot in ScheduleCalc.slotsFor(med, today)) {
                val time = slot.time ?: continue
                val base = LocalDateTime.of(today, LocalTime.parse(time))
                if (base.isAfter(now)) continue                        // 未到点：已在第 1 步排好
                if (slotRef(med.id, slot.key) in doneSlotRefs) continue // 已打卡：无需重查
                for (esc in 1..MAX_ESCALATION) {
                    val fire = base.plusMinutes(ESCALATION_STEP_MINUTES * esc)
                    if (fire.isAfter(now)) schedule(context, am, med.id, slot.key, time, fire, esc)
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

    fun cancelAllFuture(context: Context, meds: List<Medication>, today: LocalDate = LocalDate.now()) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        for (dayOffset in 0..HORIZON_DAYS) {
            val date = today.plusDays(dayOffset)
            for (med in meds) {
                for (slot in ScheduleCalc.slotsFor(med, date)) {
                    val time = slot.time ?: continue
                    for (esc in 0..MAX_ESCALATION) {
                        val fire = LocalDateTime.of(date, LocalTime.parse(time))
                            .plusMinutes(ESCALATION_STEP_MINUTES * esc)
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
