package com.ashkb.app.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ashkb.app.data.repo.ReminderConfigRepository
import com.ashkb.app.domain.SedentaryReminder
import java.time.LocalDateTime

/**
 * v1.0.68 C8a：久坐起身提醒闹钟触发。
 *
 * 三件事，顺序不可颠倒：
 *  1. **验真**：开关可能已被关掉、窗口可能已被改窄 → 落在窗口外就静默丢弃（不响）
 *  2. 发通知（免打扰时段内静默投递，与 B8 口径一致）
 *  3. **链式排下一个**——无论如何都要排，否则链断掉（一次漏响就永久失效）
 */
class SedentaryReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val cfg = ReminderConfigRepository(context)
        val now = LocalDateTime.now()
        val inWindow = cfg.sedentaryEnabled() &&
            SedentaryReminder.isWithinWindow(now, cfg.sedentaryStartHour(), cfg.sedentaryEndHour())
        if (inWindow) {
            NotificationHelper.postSedentaryReminder(context, NotificationHelper.isInDndNow(context))
        }
        // 不论是否发过通知都续链：窗口外触发（陈旧闹钟）也要把下一棒交出去
        SedentaryReminderScheduler.scheduleNext(context, now)
    }
}
