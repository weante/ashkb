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
 * 运动提醒闹钟触发（v1.0.59 B5）。
 *
 * 验真：今日是否已有 ExerciseLog（用户打卡过） → 取消；否则发通知。
 * 跨日残留兜底：extra date ≠ 今日 → 取消。
 *
 * **不在 Receiver 内排下一级升级**：运动升级链（20:00 / 22:00）在
 * [ExerciseReminderScheduler.rescheduleAll] 时已全部排好（[ExerciseReminderScheduler.slotsFor]
 * 返回 0..MAX_ESCALATION 三档）。Receiver 只发通知，不追加排闹钟——
 * 用户做完运动后剩余升级触发时 Receiver 验真会发现 hasLogged=true → 取消。
 */
class ExerciseReminderReceiver : BroadcastReceiver() {
    companion object {
        const val EXTRA_DATE = "date"
        const val EXTRA_ESCALATION = "escalation"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val dateStr = intent.getStringExtra(EXTRA_DATE) ?: return
        val escalation = intent.getIntExtra(EXTRA_ESCALATION, 0)
        val app = context.applicationContext as? AshkbApplication ?: return

        val result = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val db = AppDatabase.get(context)
                val today = LocalDate.now()
                // 跨日残留兜底：extra date ≠ 今日 → 取消
                if (dateStr != today.toString()) {
                    NotificationHelper.cancelExercise(context, dateStr, escalation)
                    return@launch
                }
                val hasLogged = db.exerciseLogDao().byDate(today.toString()).isNotEmpty()
                if (hasLogged) {
                    NotificationHelper.cancelExercise(context, dateStr, escalation)
                    return@launch
                }
                NotificationHelper.postExerciseReminder(context, dateStr, escalation)
            } finally {
                result.finish()
            }
        }
    }
}
