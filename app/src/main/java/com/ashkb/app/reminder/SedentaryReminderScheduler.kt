package com.ashkb.app.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.ashkb.app.data.repo.ReminderConfigRepository
import com.ashkb.app.domain.SedentaryReminder
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * v1.0.68 C8a：久坐起身提醒的调度（v1.0.59 B5 三源之后的**第四源**）。
 *
 * **链式单发**：任何时刻最多只有一个待触发闹钟——触发后由
 * [SedentaryReminderReceiver] 排下一个（窗口走完则排次日窗口起点）。
 * 不用 `setRepeating`：API 19 起它一律不精确，且改配置后取消不干净。
 *
 * 与其它 Scheduler 同模式：`setExactAndAllowWhileIdle` 降级 `setWindow`、
 * 固定 requestCode、`now` 可注入以便确定性测试。
 */
object SedentaryReminderScheduler {

    /** 固定 requestCode——同时只有一个待触发闹钟（链式单发）。 */
    private const val REQUEST_CODE = 0x5EDE

    /** 按当前配置重排（先取消旧的）。开关关闭或窗口非法时不排。 */
    fun rescheduleAll(context: Context, now: LocalDateTime = LocalDateTime.now()) {
        cancelAllFuture(context)
        val cfg = ReminderConfigRepository(context)
        if (!cfg.sedentaryEnabled()) return
        scheduleNext(context, cfg, now)
    }

    /** 排下一个提醒时刻（Receiver 触发后链式调用）。 */
    fun scheduleNext(context: Context, now: LocalDateTime = LocalDateTime.now()) {
        scheduleNext(context, ReminderConfigRepository(context), now)
    }

    private fun scheduleNext(context: Context, cfg: ReminderConfigRepository, now: LocalDateTime) {
        val next = SedentaryReminder.nextFire(
            now, cfg.sedentaryStartHour(), cfg.sedentaryEndHour(), cfg.sedentaryIntervalMin(),
        ) ?: return
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pending(context)
        val at = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val exact = if (Build.VERSION.SDK_INT >= 31) am.canScheduleExactAlarms() else true
        // v1.0.73（P0-1）：统一走 AlarmRegister——失败留档 + 计数，绝不静默
        AlarmRegister.set(context, am, at, pi, exact, "sedentary")
    }

    fun cancelAllFuture(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        runCatching { am.cancel(pending(context)) }
    }

    private fun pending(context: Context): PendingIntent {
        val intent = Intent(context, SedentaryReminderReceiver::class.java)
        return PendingIntent.getBroadcast(
            context, REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
