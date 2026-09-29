package com.ashkb.app.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import com.ashkb.app.CrashLogger

/**
 * v1.0.73（P0-1）：**闹钟注册的唯一入口**——注册失败绝不静默。
 *
 * 原实现是 6 处各自 `runCatching { setExactAndAllowWhileIdle / setWindow }`，异常被完全吞掉：
 * 无日志、无计数、无用户可见信号。配合小米 `MIUIOP(10014)`（精确闹钟）默认 `ignore` 的行为，
 * 结果是「所有闹钟静默丢弃 + 自检页显示已授权」的自相矛盾状态（第三份审查报告 P0-1）。
 *
 * 本入口统一做三件事：
 *  1. `setExactAndAllowWhileIdle`，精确闹钟不可用时降级 `setWindow(15min)`（原行为不变）；
 *  2. 失败 → `CrashLogger.recordNonFatal` 留档（首因可取）+ 计入 [ReminderHealth]；
 *  3. 每次尝试都更新台账，供自检页显示「尝试 N / 失败 M」。
 *
 * @param source 来源标签（med / checkup / basdai / exercise / sedentary / test），便于定位是哪条链失败。
 * @return true 表示已交给系统；false 表示注册失败（已留档）。
 */
internal object AlarmRegister {
    private var source: String = "?"
    private var attempted: Int = 0
    private var failed: Int = 0
    private var lastError: String? = null

    fun set(
        context: Context,
        am: AlarmManager,
        at: Long,
        pi: PendingIntent,
        exact: Boolean,
        source: String,
    ): Boolean {
        this.source = source
        attempted++
        return runCatching {
            if (exact) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } else {
                am.setWindow(AlarmManager.RTC_WAKEUP, at, 15 * 60_000L, pi)
            }
        }.fold(
            onSuccess = {
                ReminderHealth.persist(context, source, attempted, failed, lastError)
                true
            },
            onFailure = { e ->
                failed++
                lastError = e.message ?: e.javaClass.simpleName
                CrashLogger.recordNonFatal(context, "闹钟注册失败（$source）", e)
                ReminderHealth.persist(context, source, attempted, failed, lastError)
                false
            },
        )
    }
}
