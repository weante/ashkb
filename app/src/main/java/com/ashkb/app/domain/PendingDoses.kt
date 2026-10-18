package com.ashkb.app.domain

import com.ashkb.app.data.entity.Medication
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * v1.0.74：**未记录的计划剂量**（跨零点补记入口的判定核心）。
 *
 * 为什么需要它（2026-09-30 真机实测暴露的缺口）：
 * 23:55 那剂药的追问会在**次日** 00:25 / 00:55 弹出。用户被提醒后**回到应用**打卡时，
 * 今日页显示的是**今天**的计划（次日 23:55 那剂），于是：
 *  · 打卡记录被写到次日（`计划 23:55` 却早打了 23 小时）；
 *  · **昨天那剂仍然算没吃**，而且在应用里**没有任何入口**能补记——
 *    只有通知上的「已服用」才会写回槽位所属日（v1.0.73 修的正是那条路径）。
 *
 * 本对象负责把「昨天已到点、却既未已服也未跳过」的槽位列出来，供今日页顶部的
 * 「昨天还有 N 剂未记录」卡渲染与补记（补记写入**槽位所属日**）。
 *
 * 纯函数、无 Android 依赖，可单测（判定口径与 `ReminderScheduler` 的结算判定保持一致：
 * **已结算 = `AdherenceCalc.SETTLED_STATUSES`，即 done / partial / skipped 三态都算**）。
 *
 * v1.2.10（审查 L5）：上面这行此前写的是「done 与 skipped 都算已结算」——**代码是对的、注释是旧的**。
 * v1.0.77（批次 3b）引入 `settledSlotRefs` 时把 partial 纳入了已结算，但两处 KDoc 没跟着改
 * （本文件与 `ui/today/TodayViewModel.kt`）。这种漂移很危险：下一个维护者会照着注释
 * 把 partial 从已结算里剔出去，于是「记了部分完成仍被当成没记录」——正是裁决项 2 修掉的那个 bug。
 */
object PendingDoses {

    /** 一条未记录的剂量。 */
    data class Pending(
        val medId: String,
        val medName: String,
        val slotKey: String?,
        val slotTime: String,
        /** **槽位所属日**——补记时写入的日期就是它。 */
        val date: LocalDate,
    )

    /**
     * 某日「已到点但未结算」的槽位。
     *
     * @param date 要检查的归属日（跨零点补记场景传**昨天**）
     * @param meds 在用药品
     * @param now 现在（可注入，便于确定性地单测）
     * @param isSettled 该槽位是否已有记录（`AdherenceCalc.SETTLED_STATUSES`：done / partial / skipped
     *   三态都算）——由调用方用仓库查询构造，避免领域层依赖 `ReminderScheduler.slotRef` 的字符串格式
     *   （少一处格式漂移的机会）
     */
    fun unsettledOn(
        date: LocalDate,
        meds: List<Medication>,
        now: LocalDateTime,
        isSettled: (medId: String, slotKey: String?) -> Boolean,
    ): List<Pending> = meds.flatMap { med ->
        // 药还没开始：**不能**把「昨天」算成漏服。
        // `ScheduleCalc.slotsFor` 刻意不按日期过滤（调度器只排未来，用不到），所以这里必须自己判，
        // 否则今天新建一支药，今日页立刻会出现「昨天还有 1 剂未记录」的假阳性。
        // startDate 解析不出来时按「不过滤」处理——宁可多提示一次，也不要静默藏掉真实漏服。
        val started = runCatching { LocalDate.parse(med.startDate) }.getOrNull()
        if (started != null && date.isBefore(started)) return@flatMap emptyList()

        ScheduleCalc.slotsFor(med, date).mapNotNull { slot ->
            val time = slot.time ?: return@mapNotNull null
            // 时刻非法（脏数据）直接跳过：宁可少一条提示，也不能让今日页崩
            val planned = runCatching { LocalDateTime.of(date, LocalTime.parse(time)) }.getOrNull()
                ?: return@mapNotNull null
            // 还没到点的不算「未记录」（它是今天/该日尚未到来的计划）
            if (planned.isAfter(now)) return@mapNotNull null
            if (isSettled(med.id, slot.key)) return@mapNotNull null
            Pending(
                medId = med.id,
                medName = med.name,
                slotKey = slot.key,
                slotTime = time,
                date = date,
            )
        }
    }.sortedWith(compareBy({ it.slotTime }, { it.medName }))
}
