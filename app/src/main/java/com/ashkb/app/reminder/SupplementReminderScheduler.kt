package com.ashkb.app.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.ashkb.app.data.entity.Supplement
import com.ashkb.app.domain.ScheduleCalc
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import org.json.JSONArray

/**
 * 补剂提醒调度（v1.2.4）。
 *
 * **为什么现在才补上**：`Supplement` 从 M2 起就有 `frequency` / `times` 两列，但全仓
 * **没有任何补剂调度器**——「编辑补剂」弹层也从来没有时刻输入，于是补剂永远不会有提醒
 * （用户反馈的原话：「补剂没法设置每日提醒」）。本对象补上这一环。
 *
 * **窗口与 [ReminderScheduler] 一致（未来 7 天）**：用药链就是这个窗口，补剂跟它对齐——
 * 一周不开应用也不会漏提醒，更长的缺口靠启动 / 开机重排续上。
 *
 * ⚠️ **补剂打卡是「按天」记的**：`SupplementLog.slotKey` 恒为 NULL（一天一行，见
 * `WellnessViewModel.checkInSupplement`）。所以「今日已打卡」是**整支补剂**的状态，
 * 而不是某个时刻的状态——一天里第一个时刻打过卡，当天其余时刻的提醒会被静默撤下。
 * 这不是缺陷，而是与界面一致的语义：卡片上那支补剂此刻就显示「已服用」，
 * 再为它发第二次提醒只会自相矛盾。
 *
 * requestCode = `sup|supId|date|time` 稳定哈希——重排幂等，覆盖旧闹钟。
 */
object SupplementReminderScheduler {
    /** 与 [ReminderScheduler.HORIZON_DAYS] 对齐：未来 7 天。 */
    const val HORIZON_DAYS = 7L

    /**
     * 只有「每日」才有定时提醒的语义。
     *
     * 其余取值（`weekly` / `prn` / 将来的新值）**一律不排**而不是「猜一个」——
     * 猜错会变成误提醒（比没有提醒更糟）。将来谁给这些值定义了语义，再来这里放开。
     */
    const val FREQ_DAILY = "daily"

    data class Slot(val date: LocalDate, val supId: String, val time: String, val fire: LocalDateTime)

    /**
     * 纯函数：某补剂在某日的提醒槽位。
     *  - 已归档 / 非「每日」频次 / 没填时刻 → 不排
     *  - [loggedToday]（该补剂今日已打卡）→ 不排
     *  - 已过点（fire ≤ now-1 分钟）→ 不排
     */
    fun slotsFor(
        sup: Supplement,
        date: LocalDate,
        loggedToday: Boolean,
        now: LocalDateTime,
    ): List<Slot> {
        if (sup.isArchived || loggedToday) return emptyList()
        if (!sup.frequency.equals(FREQ_DAILY, ignoreCase = true)) return emptyList()
        return parseTimes(sup.times).mapNotNull { time ->
            val fire = LocalDateTime.of(date, LocalTime.parse(time))
            if (fire.isBefore(now.minusMinutes(1))) null else Slot(date, sup.id, time, fire)
        }
    }

    /**
     * `times` JSON 数组 → List<"HH:mm">。
     *
     * 与 [ScheduleCalc.takeTimesOf] 同一口径（`runCatching` + [ScheduleCalc.TIME_PATTERN]）：
     * 脏数据（`not-json`、`25:99`）必须被丢弃而不是崩在 `LocalTime.parse` 上——
     * 补剂的 times 走的是备份恢复路径，历史脏值完全可能进来。
     */
    fun parseTimes(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            JSONArray(raw).let { arr -> (0 until arr.length()).map { arr.getString(it) } }
        }.getOrDefault(emptyList()).filter { ScheduleCalc.TIME_PATTERN.matches(it) }
    }

    fun rescheduleAll(
        context: Context,
        supplements: List<Supplement>,
        loggedToday: Set<String>,
        today: LocalDate = LocalDate.now(),
        now: LocalDateTime = LocalDateTime.now(),
    ) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        cancelAllFuture(context, supplements, today)
        for (sup in supplements) {
            for (dayOffset in 0..HORIZON_DAYS) {
                val date = today.plusDays(dayOffset)
                // 「今日已打卡」只对今天成立：未来 7 天当然还没有打卡记录
                val logged = date == today && sup.id in loggedToday
                for (slot in slotsFor(sup, date, logged, now)) {
                    schedule(context, am, slot)
                }
            }
        }
    }

    fun cancelAllFuture(
        context: Context,
        supplements: List<Supplement>,
        today: LocalDate = LocalDate.now(),
    ) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        for (sup in supplements) {
            for (dayOffset in 0..HORIZON_DAYS) {
                val date = today.plusDays(dayOffset)
                for (time in parseTimes(sup.times)) {
                    am.cancel(pending(context, sup.id, date, time))
                }
            }
        }
    }

    /**
     * `canScheduleExactAlarms()` 的起始 API（Android 12 / S）。
     *
     * 低于此版本**没有这个方法**，调它会抛 `NoSuchMethodError`——必须先判版本再调，
     * 而不是把它当成"老版本返回 false"。与本项目 `DateProvider.API_REGISTER_RECEIVER_FLAGS`
     * 同一处理方式：写成具名常量，而不是在表达式里留一个字面量 31。
     */
    private const val API_S = 31

    private fun schedule(context: Context, am: AlarmManager, slot: Slot) {
        val pi = pending(context, slot.supId, slot.date, slot.time)
        val at = slot.fire.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val exact = if (Build.VERSION.SDK_INT >= API_S) am.canScheduleExactAlarms() else true
        // v1.0.73（P0-1）：统一走 AlarmRegister——失败留档 + 计数，绝不静默
        AlarmRegister.set(context, am, at, pi, exact, "supplement")
    }

    private fun pending(
        context: Context,
        supId: String,
        date: LocalDate,
        time: String,
    ): PendingIntent {
        val intent = Intent(context, SupplementReminderReceiver::class.java).apply {
            putExtra(SupplementReminderReceiver.EXTRA_SUP_ID, supId)
            putExtra(SupplementReminderReceiver.EXTRA_DATE, date.toString())
            putExtra(SupplementReminderReceiver.EXTRA_TIME, time)
        }
        return PendingIntent.getBroadcast(
            context, reqCode(supId, date, time), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /**
     * 槽位身份。`internal` 而非 private：**两支补剂设成同一个时刻**是常态（早饭后一起吃），
     * 如果身份里漏掉 `supId`，后一次 `setExactAndAllowWhileIdle` 会把前一次的闹钟覆盖掉，
     * 用户只会收到一支的提醒且毫无报错。这条不变量由单测直接钉住。
     */
    internal fun reqCode(supId: String, date: LocalDate, time: String): Int =
        ("sup|$supId|$date|$time").hashCode()
}
