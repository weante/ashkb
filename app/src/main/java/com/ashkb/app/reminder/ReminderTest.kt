package com.ashkb.app.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * v1.0.62 C11：测试提醒。
 *
 * 目的：让用户**端到端**验证提醒链是否真的能响——不是查权限状态（那只是必要条件），
 * 而是真排一个精确闹钟 → 到点由 [TestReminderReceiver] 发通知。
 * 「权限授予了但厂商后台策略把闹钟掐了」这类问题只有这样才暴露得出来。
 *
 * 与其它 Scheduler 同模式：`setExactAndAllowWhileIdle` 降级 `setWindow`、
 * 固定 requestCode、`nowMs` 可注入以便确定性测试。
 */
object ReminderTest {

    /** 延迟秒数——留出用户放下手机、锁屏的时间，才验得出锁屏投递。 */
    const val DELAY_SECONDS = 10L

    /** 稳定 requestCode（单发测试闹钟，固定即可）。 */
    private const val REQUEST_CODE = 0x7E57

    fun schedule(
        context: Context,
        delaySeconds: Long = DELAY_SECONDS,
        nowMs: Long = System.currentTimeMillis(),
    ) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val at = nowMs + delaySeconds * 1000L
        val pi = pending(context)
        val exact = if (Build.VERSION.SDK_INT >= 31) am.canScheduleExactAlarms() else true
        runCatching {
            if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else am.setWindow(AlarmManager.RTC_WAKEUP, at, 15 * 60_000L, pi)
        }
    }

    fun cancel(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pending(context))
    }

    private fun pending(context: Context): PendingIntent {
        val intent = Intent(context, TestReminderReceiver::class.java)
        return PendingIntent.getBroadcast(
            context, REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
