package com.ashkb.app.reminder

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.ashkb.app.data.db.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 开机 / 应用更新 / 系统时间变更 / 时区变更 / 精确闹钟权限授予后：重建全部精确闹钟。
 * （内存态调度不持久；RTC_WAKEUP 闹钟随系统时间走——用户改时钟或换时区必须重排，
 * 否则提醒整体偏移——P5 提醒可靠性专项的代码层保障。）
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val rebuild = when (action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> true
            // 用户手动改时钟 / 换时区：已排的 RTC 闹钟时刻全部失效
            Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED -> true
            // Android 12+：用户从系统设置回授精确闹钟权限 → 从 setWindow 升级回 setExact
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED -> true
            else -> false
        }
        if (!rebuild) return

        val result = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val meds = AppDatabase.get(context).medicationDao().listActive()
                ReminderScheduler.rescheduleAll(context, meds)
                // 权限回授场景顺手把 sys 通道告知一声（通道存在才发，免打扰用户）
                if (action == AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED &&
                    Build.VERSION.SDK_INT >= 31
                ) {
                    val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                    if (am.canScheduleExactAlarms()) {
                        NotificationHelper.ensureChannels(context)
                    }
                }
            } finally {
                result.finish()
            }
        }
    }
}
