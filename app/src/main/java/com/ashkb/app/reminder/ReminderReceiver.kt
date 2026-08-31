package com.ashkb.app.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ashkb.app.AshkbApplication
import com.ashkb.app.data.repo.nowIso
import java.time.LocalDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 闹钟触发：查库——已打卡则静默取消；未打卡发通知并安排 +30 分钟升级重查（上限 2 次重查）。
 */
class ReminderReceiver : BroadcastReceiver() {
    companion object {
        const val EXTRA_MED_ID = "med_id"
        const val EXTRA_SLOT_KEY = "slot_key"
        const val EXTRA_SLOT_TIME = "slot_time"
        const val EXTRA_ESCALATION = "escalation"
        const val EXTRA_FIRE_ISO = "fire_iso"
        private const val MAX_ESCALATION = 2
    }

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? AshkbApplication ?: return
        val medId = intent.getStringExtra(EXTRA_MED_ID) ?: return
        val slotKey = intent.getStringExtra(EXTRA_SLOT_KEY)
        val slotTime = intent.getStringExtra(EXTRA_SLOT_TIME)
        val escalation = intent.getIntExtra(EXTRA_ESCALATION, 0)
        val fireIso = intent.getStringExtra(EXTRA_FIRE_ISO) ?: nowIso()

        val result = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val repo = app.medicationRepository
                val med = repo.medicationById(medId) ?: return@launch
                // 计划仍在今日复查：顺延 / 停用 / 改时刻后残留的旧闹钟静默取消
                if (slotKey != null && com.ashkb.app.domain.ScheduleCalc
                        .slotsFor(med, java.time.LocalDate.now()).none { it.key == slotKey }
                ) {
                    NotificationHelper.cancel(context, medId, slotKey)
                    return@launch
                }
                val done = repo.logsForDate(java.time.LocalDate.now())
                    .any { it.medId == medId && it.slotKey == slotKey && it.status == "done" }
                if (done) {
                    NotificationHelper.cancel(context, medId, slotKey)
                    return@launch
                }
                NotificationHelper.postMedReminder(
                    context, medId, slotKey, slotTime, med.name, med.dose, escalation
                )
                if (escalation < MAX_ESCALATION) {
                    val fire = runCatching { LocalDateTime.parse(fireIso) }
                        .getOrDefault(LocalDateTime.now().plusMinutes(30))
                    ReminderScheduler.scheduleEscalation(
                        context, medId, slotKey, slotTime,
                        fire.plusMinutes(30), escalation + 1
                    )
                }
            } finally {
                result.finish()
            }
        }
    }
}
