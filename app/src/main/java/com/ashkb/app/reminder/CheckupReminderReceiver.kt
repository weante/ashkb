package com.ashkb.app.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ashkb.app.AshkbApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 复诊提醒闹钟触发（v1.0.59 B5）。
 *
 * 验真：extra nextDate 是否还在库内任一 CheckupRecord.nextDate 中——
 * 用户改了 nextDate 后旧闹钟的 extra 与新 nextDate 不匹配，静默取消。
 * 通过则 [NotificationHelper.postCheckupReminder]。
 */
class CheckupReminderReceiver : BroadcastReceiver() {
    companion object {
        const val EXTRA_NEXT_DATE = "next_date"
        const val EXTRA_PHASE = "phase"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val nextDate = intent.getStringExtra(EXTRA_NEXT_DATE) ?: return
        val phase = intent.getStringExtra(EXTRA_PHASE) ?: return
        val app = context.applicationContext as? AshkbApplication ?: return

        val result = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val db = com.ashkb.app.data.db.AppDatabase.get(context)
                val records = db.checkupRecordDao().listAll()
                // 兜底验真：extra nextDate 须仍在任一 record.nextDate 中
                val stillActive = records.any { it.nextDate == nextDate }
                if (!stillActive) {
                    NotificationHelper.cancelCheckup(context, nextDate, phase)
                    return@launch
                }
                // v1.0.60 B8：免打扰时段静默投递
                val silent = NotificationHelper.isInDndNow(context)
                NotificationHelper.postCheckupReminder(context, nextDate, phase, silent)
            } finally {
                result.finish()
            }
        }
    }
}
