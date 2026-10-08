package com.ashkb.app.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ashkb.app.AshkbApplication
import com.ashkb.app.data.db.AppDatabase
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 补剂提醒闹钟触发（v1.2.4）。
 *
 * 验真（照 [ExerciseReminderReceiver] 的范式）——三条都要查，缺一条就会发出「已经不需要」的提醒：
 *  1. `extra date` ≠ 今日 → 跨日残留，撤下通知；
 *  2. 该补剂已不在 `listActive()`（被归档 / 被删除）→ 撤下；
 *  3. 该补剂今日已打卡 → 撤下（补剂打卡按天记，理由见 [SupplementReminderScheduler] 的说明）。
 *
 * **不在 Receiver 内排下一次**：未来 7 天在 [SupplementReminderScheduler.rescheduleAll] 时
 * 已全部排好（[SupplementReminderScheduler.slotsFor] 的窗口就是 0..[SupplementReminderScheduler.HORIZON_DAYS]），
 * Receiver 只负责发通知——与运动提醒链同构。
 */
class SupplementReminderReceiver : BroadcastReceiver() {
    companion object {
        const val EXTRA_SUP_ID = "sup_id"
        const val EXTRA_DATE = "date"
        const val EXTRA_TIME = "time"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val supId = intent.getStringExtra(EXTRA_SUP_ID) ?: return
        val dateStr = intent.getStringExtra(EXTRA_DATE) ?: return
        val time = intent.getStringExtra(EXTRA_TIME) ?: return
        // 应用未初始化（进程刚被拉起但 Application 还没就绪）→ 直接放弃，不崩
        if (context.applicationContext !is AshkbApplication) return

        val result = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val db = AppDatabase.get(context)
                val today = LocalDate.now()
                if (dateStr != today.toString()) {
                    NotificationHelper.cancelSupplement(context, supId, dateStr, time)
                    return@launch
                }
                val sup = db.supplementDao().listActive().firstOrNull { it.id == supId }
                if (sup == null) {
                    NotificationHelper.cancelSupplement(context, supId, dateStr, time)
                    return@launch
                }
                val logged = db.supplementLogDao().byDate(dateStr).any { it.supId == supId }
                if (logged) {
                    NotificationHelper.cancelSupplement(context, supId, dateStr, time)
                    return@launch
                }
                // v1.0.60 B8：免打扰时段静默投递（通道降级为 IMPORTANCE_LOW，不响不震但通知栏可见）
                val silent = NotificationHelper.isInDndNow(context)
                NotificationHelper.postSupplementReminder(
                    context, supId, sup.name, sup.dose, dateStr, time, silent,
                )
            } finally {
                result.finish()
            }
        }
    }
}
