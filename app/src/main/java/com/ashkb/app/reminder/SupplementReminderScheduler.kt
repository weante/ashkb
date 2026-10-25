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

    /**
     * v1.2.11（缺陷工单 A01）：**已注册槽位的本地记录**。
     *
     * 为什么需要它：[cancelAllFuture] 只能遍历「当前」`sup.times` 去取消，而用户把 08:00 改成
     * 20:00 之后，08:00 已经不在 `times` 里了——那个闹钟谁也取消不掉，第二天照旧响，
     * 点进去却找不到任何 08:00 的补剂。系统 `AlarmManager` 没有「列出我排了哪些闹钟」的公开
     * API（`AlarmManager` 只提供 cancel），所以必须自己记下「我排过哪些」。
     *
     * 为什么用 `SharedPreferences` 而不是新表：这是一份**可丢弃的缓存**——记错了最多漏取消
     * 一个闹钟（接收器还有第二道校验会静默撤下），而加一张表要动 schema、要迁移、要维护。
     * 键格式 `supId|yyyy-MM-dd|HH:mm`；`supId` 由 `Ids.new` 生成，不含 `|`。
     */
    private const val PREFS = "supplement_alarms"
    private const val KEY_REGISTERED = "registered"
    private const val KEY_SEP = '|'

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
     * v1.2.10（审查 CRITICAL-1）：改为直接委托 [ScheduleCalc.parseTimes] —— 本函数与
     * [ScheduleCalc.takeTimesOf] 此前是**两份逐字重复的实现**，重复的代价就是 CRITICAL-1：
     * 写入侧改了格式、读取侧漏改一处，而绿色的测试钉着旧假设。留一份实现，漏改就不可能发生。
     */
    fun parseTimes(raw: String?): List<String> = ScheduleCalc.parseTimes(raw)

    fun rescheduleAll(
        context: Context,
        supplements: List<Supplement>,
        loggedToday: Set<String>,
        today: LocalDate = LocalDate.now(),
        now: LocalDateTime = LocalDateTime.now(),
    ) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        cancelAllFuture(context, supplements, today)
        val registered = mutableSetOf<String>()
        for (sup in supplements) {
            for (dayOffset in 0..HORIZON_DAYS) {
                val date = today.plusDays(dayOffset)
                // 「今日已打卡」只对今天成立：未来 7 天当然还没有打卡记录
                val logged = date == today && sup.id in loggedToday
                for (slot in slotsFor(sup, date, logged, now)) {
                    schedule(context, am, slot)
                    registered += slotKey(slot.supId, slot.date, slot.time)
                }
            }
        }
        writeRegistered(context, registered)
    }

    /**
     * 取消未来窗口内该批补剂的全部闹钟。
     *
     * v1.2.11（缺陷工单 A01）两段式：
     *  ① 先取消**记录在案**的槽位——与当前 `times` 无关。这是 A01 的修复点：用户把 08:00
     *     改成 20:00 后 08:00 已不在 `times` 里，只有记录能把它捞出来取消；
     *  ② 再按当前定义扫一遍，覆盖「本机制上线前就排下」的历史闹钟（它们没被记录过）。
     *
     * 不做这一步的后果（A01 原文）：改时刻后旧闹钟照旧响，点进去找不到对应槽位；
     * 删除时刻 / 每日改每周 / 归档 三条路径同样留下哑闹钟。
     */
    fun cancelAllFuture(
        context: Context,
        supplements: List<Supplement>,
        today: LocalDate = LocalDate.now(),
    ) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        cancelRegistered(context, am)
        for (sup in supplements) {
            for (dayOffset in 0..HORIZON_DAYS) {
                val date = today.plusDays(dayOffset)
                for (time in parseTimes(sup.times)) {
                    am.cancel(pending(context, sup.id, date, time))
                }
            }
        }
        writeRegistered(context, emptySet())
    }

    /**
     * 取消**登记表里记着**的那些闹钟（A01 的第一段）。
     *
     * 抽成独立函数有两个理由：
     * · detekt `LoopWithTooManyJumpStatements`：原地两个 `continue`（键格式坏了 / 日期解析失败）
     *   会让循环体有两个跳出口，规则要求拆分。拆出来之后「坏键跳过」落在 [registeredKeyOf] 里，
     *   循环只剩一次调用。
     * · 语义上这确实是两件事：**取消登记过的** 与 **取消按当前定义算出来的**。
     *   旧实现只做了后者，所以用户把 08:00 改成 20:00 后，08:00 那个闹钟永远没人取消。
     */
    private fun cancelRegistered(context: Context, am: AlarmManager) {
        for (key in readRegistered(context)) {
            val slot = registeredKeyOf(key) ?: continue
            am.cancel(pending(context, slot.first, slot.second, slot.third))
        }
    }

    /**
     * 解析登记键 `supId|date|time`，任一环节不合法就返回 null。
     *
     * 登记表是 prefs 里的字符串集合，可能被旧版本、手改或写坏——解析失败必须**静默跳过**，
     * 不能让一次脏数据把整轮取消失败（那才是「提醒永远不响」的真凶）。
     */
    private fun registeredKeyOf(key: String): Triple<String, LocalDate, String>? {
        val parts = key.split(KEY_SEP)
        if (parts.size != 3) return null
        val date = runCatching { LocalDate.parse(parts[1]) }.getOrNull() ?: return null
        return Triple(parts[0], date, parts[2])
    }
    /**
     * 接收器侧的第二道校验：`time` 是否仍是该补剂**当前**的合法槽位。
     *
     * 纯函数（不碰 Context / AlarmManager），供 [SupplementReminderReceiver] 与单测共用。
     * 为什么接收器也要查一遍：闹钟取消可能因为进程被杀、`SharedPreferences` 写入失败、
     * 或用户清数据而漏掉；**宁可静默不响，也不要发出「已经不需要」的提醒**。
     */
    fun isCurrentSlot(sup: Supplement, time: String): Boolean {
        if (sup.isArchived) return false
        if (!sup.frequency.equals(FREQ_DAILY, ignoreCase = true)) return false
        return time in parseTimes(sup.times)
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

    /** 记录键：`supId|date|time`——与 [reqCode] 的槽位身份一一对应。 */
    private fun slotKey(supId: String, date: LocalDate, time: String): String =
        "$supId$KEY_SEP$date$KEY_SEP$time"

    private fun readRegistered(context: Context): Set<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY_REGISTERED, emptySet())
            ?.toSet()
            .orEmpty()

    private fun writeRegistered(context: Context, keys: Set<String>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(KEY_REGISTERED, keys)
            .apply()
    }

    /**
     * 槽位身份。`internal` 而非 private：**两支补剂设成同一个时刻**是常态（早饭后一起吃），
     * 如果身份里漏掉 `supId`，后一次 `setExactAndAllowWhileIdle` 会把前一次的闹钟覆盖掉，
     * 用户只会收到一支的提醒且毫无报错。这条不变量由单测直接钉住。
     */
    internal fun reqCode(supId: String, date: LocalDate, time: String): Int =
        ("sup|$supId|$date|$time").hashCode()
}
