package com.ashkb.app.reminder

import android.content.Context
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * v1.0.73（P0-1）：**提醒链健康台账**——闹钟注册的「尝试 / 失败」必须可观测。
 *
 * 为什么需要它（第三份审查报告 P0-1，已逐行复核）：`canScheduleExactAlarms()` 返回 true
 * **不等于系统真的放行**——小米 / HyperOS 的私有 appop `MIUIOP(10014)`（精确闹钟）默认为
 * `ignore`（见 `WALKTHROUGH-v1.0.72.md`）。此时每一个槽位闹钟都被静默丢弃，而自检页仍显示
 * 「精确闹钟 已授权」：**App 自信地报告一切正常，用户一条提醒都收不到**。
 *
 * 本台账只做一件事：把注册尝试与失败落盘，供自检页展示与事后排查。
 * **纯本地、不上报**，与项目「零网络」红线一致。
 */
object ReminderHealth {
    private const val PREF = "reminder_health"
    private const val K_AT = "last_run_at"
    private const val K_SOURCE = "last_source"
    private const val K_ATTEMPTED = "last_attempted"
    private const val K_FAILED = "last_failed"
    private const val K_ERROR = "last_error"

    private val TIME_FMT = DateTimeFormatter.ofPattern("MM-dd HH:mm")

    /** 自检页展示用的快照。 */
    data class Snapshot(
        val at: String?,
        val source: String?,
        val attempted: Int,
        val failed: Int,
        val lastError: String?,
    ) {
        /** 有失败即需用户注意（自检页据此显示红项）。 */
        val hasFailure: Boolean get() = failed > 0
    }

    /**
     * v1.2.11（缺陷工单 B09）：**一轮重排**的注册结果。
     *
     * 与 [Snapshot] 的分工：快照是**跨轮次累计**台账（自检页展示「最近一次重排」的累计尝试/失败），
     * 它回答不了「刚才这次保存有没有把提醒排上」——而这正是 B09 要回答的问题。原实现里
     * 保存路径把整段重排 `runCatching` 掉，于是「数据存进去了、提醒没排上」全程无声。
     *
     * [attempted] 为 0 表示这一轮**没有需要排的槽位**（药单为空、补剂改成每周、全部已打卡），
     * 这不是失败，[ok] 仍为 true。
     */
    data class RunReport(
        val attempted: Int,
        val failed: Int,
        val aborted: Boolean = false,
        val firstError: String? = null,
    ) {
        /**
         * 这一轮是否全部排上。
         *
         * 刻意判「失败数为 0」而不是「`attempted > 0 && failed == 0`」：一轮里一个槽位都没排
         * 完全可能是正常状态，把它当失败会让用户每次保存都被弹一次「提醒重排失败」，
         * 比不提示更糟（狼来了，真失败时用户也不看了）。
         *
         * [aborted]（整轮被异常中断，例如取 DAO 抛错、`AlarmManager` 拿不到）单独算失败：
         * 那种情况一个槽位都没试过，`failed` 恒为 0，只看失败数会把最严重的情形报成「全部正常」。
         */
        val ok: Boolean get() = failed == 0 && !aborted
    }

    /** 没有任何尝试的报告：调用方在「未走到重排」的分支上用它，语义是「没排，但也不算失败」。 */
    val EMPTY_RUN: RunReport = RunReport(attempted = 0, failed = 0, firstError = null)

    fun snapshot(context: Context): Snapshot {
        val p = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        return Snapshot(
            at = p.getString(K_AT, null),
            source = p.getString(K_SOURCE, null),
            attempted = p.getInt(K_ATTEMPTED, 0),
            failed = p.getInt(K_FAILED, 0),
            lastError = p.getString(K_ERROR, null),
        )
    }

    /** 由 [AlarmRegister] 调用；每次注册尝试后落盘（`apply` 异步，代价可忽略）。 */
    internal fun persist(context: Context, source: String, attempted: Int, failed: Int, lastError: String?) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putString(K_AT, LocalDateTime.now().format(TIME_FMT))
            .putString(K_SOURCE, source)
            .putInt(K_ATTEMPTED, attempted)
            .putInt(K_FAILED, failed)
            .putString(K_ERROR, lastError)
            .apply()
    }
}
