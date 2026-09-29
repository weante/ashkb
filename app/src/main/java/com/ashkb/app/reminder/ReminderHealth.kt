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
