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

    /** v1.0.73（P1-2）：「稍后」的 snooze 间隔（分钟）。 */
    const val SNOOZE_MINUTES = 15L

    /**
     * v1.0.73（P1-2）：snooze 用的级数取 1——复用「仍未确认」文案，且 < [MAX_ESCALATION]
     * 故**不会**再弹全屏；「不再继续升级」由 intent 里的 `EXTRA_SNOOZE` 标志控制
     * （接收器只在非 snooze 时才排下一级），不依赖这个数值。
     */
    const val SNOOZE_ESCALATION = 1

    /**
     * v1.0.61 B9：末级升级 = **强提醒**（全屏 Intent）。
     *
     * 语义：升级链最后一级（esc == [MAX_ESCALATION]）是「漏服的最后一道防线」——
     * 此时已连续提醒两次仍未确认，改用全屏 Intent 唤醒锁屏并接管界面。
     * 仅用药链启用（BASDAI / 运动为非紧急源，全屏会过度打扰）。
     *
     * 纯函数，供 [NotificationHelper.postMedReminder] 与单测共用。
     */
    fun isStrongEscalation(escalation: Int): Boolean = escalation >= MAX_ESCALATION

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
                    schedule(context, am, med.id, slot.key, time, date, fire, escalation = 0)
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
                    // v1.0.73：槽位日期一并传给排程——跨零点的 +30/+60 归**今天**这个槽位，
                    // 而不是它触发时所在的明天。
                    if (fire.isAfter(now)) schedule(context, am, med.id, slot.key, time, today, fire, esc)
                }
            }
        }
    }

    /**
     * 升级重查：+30 分钟后未打卡 → 重复提醒（M10 升级链，上限 2 次重查）。
     *
     * v1.0.73：新增 [slotDate]——**槽位所属日期**（不是触发时刻的日期）。跨零点的 +30/+60
     * 属于**前一天**的槽位，身份与判定都必须锚在这个日期上，否则「已服用」校验会查错日子。
     */
    fun scheduleEscalation(
        context: Context, medId: String, slotKey: String?, slotTime: String?,
        slotDate: LocalDate, fireAt: LocalDateTime, escalation: Int,
    ) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        schedule(context, am, medId, slotKey, slotTime, slotDate, fireAt, escalation)
    }

    /**
     * v1.0.73（P1-2）：全屏强提醒页「稍后」真正排一次 snooze。
     *
     * 原实现里「稍后」与「已服用」都只 `finish()`，而末级 esc=2 已是链尾——于是那次剂量
     * **此后再也不会被提醒**（按钮语义与实现不符）。本方法在 [minutes] 分钟后补一次提醒：
     * 走 esc=1 的「仍未确认」文案（不进全屏、不再继续升级），避免 snooze 变成无限循环。
     */
    fun scheduleSnooze(
        context: Context, medId: String, slotKey: String?, slotTime: String?,
        slotDate: LocalDate, minutes: Long = SNOOZE_MINUTES,
    ) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val fireAt = LocalDateTime.now().plusMinutes(minutes)
        val pi = pending(context, medId, slotKey, slotTime, slotDate, fireAt, SNOOZE_ESCALATION, snooze = true)
        val at = fireAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val exact = if (Build.VERSION.SDK_INT >= 31) am.canScheduleExactAlarms() else true
        AlarmRegister.set(context, am, at, pi, exact, "med")
    }

    private fun schedule(
        context: Context, am: AlarmManager,
        medId: String, slotKey: String?, slotTime: String?,
        slotDate: LocalDate, fireAt: LocalDateTime, escalation: Int,
    ) {
        val pi = pending(context, medId, slotKey, slotTime, slotDate, fireAt, escalation)
        val at = fireAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val exact = if (Build.VERSION.SDK_INT >= 31) am.canScheduleExactAlarms() else true
        // v1.0.73（P0-1）：统一走 AlarmRegister——失败留档 + 计数，绝不静默
        AlarmRegister.set(context, am, at, pi, exact, "med")
    }

    fun cancelAllFuture(context: Context, meds: List<Medication>, today: LocalDate = LocalDate.now()) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        // v1.0.73（D2）：从**昨天**开始扫。跨零点的 +30/+60 属于前一天的槽位（例如 23:50 的
        // 剂量其 esc1 落在次日 00:20），原来只扫 today..+7，这些闹钟**永远取消不掉**：
        // 成为孤儿，每次冷启动都清不掉，还会对已服的药补发提醒。
        for (dayOffset in -1..HORIZON_DAYS) {
            val date = today.plusDays(dayOffset)
            for (med in meds) {
                for (slot in ScheduleCalc.slotsFor(med, date)) {
                    val time = slot.time ?: continue
                    for (esc in 0..MAX_ESCALATION) {
                        val fire = LocalDateTime.of(date, LocalTime.parse(time))
                            .plusMinutes(ESCALATION_STEP_MINUTES * esc)
                        am.cancel(pending(context, med.id, slot.key, time, date, fire, esc))
                    }
                    // snooze 是独立的一次性闹钟（escalation 用哨兵值），也要一并取消
                    val snoozeFire = LocalDateTime.of(date, LocalTime.parse(time))
                    am.cancel(
                        pending(context, med.id, slot.key, time, date, snoozeFire, SNOOZE_ESCALATION, snooze = true)
                    )
                }
            }
        }
    }

    private fun pending(
        context: Context, medId: String, slotKey: String?, slotTime: String?,
        slotDate: LocalDate, fireAt: LocalDateTime, escalation: Int, snooze: Boolean = false,
    ): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(ReminderReceiver.EXTRA_MED_ID, medId)
            putExtra(ReminderReceiver.EXTRA_SLOT_KEY, slotKey)
            putExtra(ReminderReceiver.EXTRA_SLOT_TIME, slotTime)
            putExtra(ReminderReceiver.EXTRA_SLOT_DATE, slotDate.toString())
            putExtra(ReminderReceiver.EXTRA_ESCALATION, escalation)
            putExtra(ReminderReceiver.EXTRA_FIRE_ISO, fireAt.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME))
            if (snooze) putExtra(ReminderReceiver.EXTRA_SNOOZE, true)
        }
        return PendingIntent.getBroadcast(
            context, reqCode(medId, slotKey, slotDate, escalation, snooze), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /**
     * v1.0.73（P1-5）：requestCode 由 `String.hashCode()` 改为 **SHA-256 派生**，身份锚定
     * 「槽位（medId + slotKey + **槽位所属日期**）+ 升级级数」。
     *
     * 原实现把 `fireAt.toLocalDate()` 当作身份的一部分，而升级链要跨零点：23:50 的 +30 落在
     * 次日 00:20，其 requestCode 的日期也随之变成次日——于是「按槽位日期枚举」的取消路径
     * （[cancelAllFuture]）永远算不出同一个码。锚到槽位日期后，取消与判定落在同一维度上。
     * 同时改用 SHA-256 取前 32 位：分布均匀，避免相似字符串在 `hashCode` 下的系统性聚集。
     */
    private fun reqCode(medId: String, slotKey: String?, slotDate: LocalDate, escalation: Int, snooze: Boolean): Int {
        val identity = "$medId|$slotKey|$slotDate|$escalation${if (snooze) "|snooze" else ""}"
        val d = java.security.MessageDigest.getInstance("SHA-256")
            .digest(identity.toByteArray(Charsets.UTF_8))
        return ((d[0].toInt() and 0xFF) shl 24) or ((d[1].toInt() and 0xFF) shl 16) or
            ((d[2].toInt() and 0xFF) shl 8) or (d[3].toInt() and 0xFF)
    }
}
