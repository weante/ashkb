package com.ashkb.app.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ashkb.app.AshkbApplication
import com.ashkb.app.data.db.AppDatabase
import com.ashkb.app.data.repo.ReminderConfigRepository
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * BASDAI 评估提醒闹钟触发（v1.0.59 B5）。
 *
 * 验真：从 prefs 读 basdai_cycle_days，查 db.basdaiDao().latest()，重算 nextDueDate——
 * 与 extra dueDate 不等则取消（latest 改后旧闹钟的 dueDate 已不匹配新算值）。
 *
 * **不在 Receiver 内排下一级升级**：BASDAI 升级链（+1/+2 天 20:00）在
 * [BasdaiReminderScheduler.rescheduleAll] 时已全部排好（[BasdaiReminderScheduler.slotsFor]
 * 返回 0..MAX_ESCALATION 三档）。Receiver 只发通知，不追加排闹钟——
 * 用户做评估后剩余升级触发时 Receiver 验真会发现 latest 已变 → dueDate 变 → 取消。
 */
class BasdaiReminderReceiver : BroadcastReceiver() {
    companion object {
        const val EXTRA_DUE_DATE = "due_date"
        const val EXTRA_ESCALATION = "escalation"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val dueDateStr = intent.getStringExtra(EXTRA_DUE_DATE) ?: return
        val escalation = intent.getIntExtra(EXTRA_ESCALATION, 0)
        val app = context.applicationContext as? AshkbApplication ?: return

        val result = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val db = AppDatabase.get(context)
                val prefs = ReminderConfigRepository(context)
                val today = LocalDate.now()
                val latest = db.basdaiDao().latest()
                val due = BasdaiReminderScheduler.nextDueDate(latest, prefs.basdaiCycleDays(), today)
                // 兜底验真：extra dueDate 须等于新算 dueDate
                if (due.toString() != dueDateStr) {
                    NotificationHelper.cancelBasdai(context, dueDateStr, escalation)
                    return@launch
                }
                // v1.0.60 B8：免打扰时段静默投递
                val silent = NotificationHelper.isInDndNow(context)
                NotificationHelper.postBasdaiReminder(context, dueDateStr, escalation, silent)
            } finally {
                result.finish()
            }
        }
    }
}
