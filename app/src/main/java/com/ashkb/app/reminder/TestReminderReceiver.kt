package com.ashkb.app.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * v1.0.62 C11：测试提醒闹钟触发——发一条测试通知。
 *
 * 无验真、无升级链：这是一次性的链路自检，收到即发、发完即止。
 * 静默时段（v1.0.60 B8 DND）同样生效——用户主动点的测试也应尊重免打扰设置。
 */
class TestReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val silent = NotificationHelper.isInDndNow(context)
        NotificationHelper.postTestReminder(context, silent)
    }
}
