package com.ashkb.app.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ashkb.app.AshkbApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** 通知「已服用」动作：免开应用直接写库打卡，取消通知并重排后续提醒 */
class CheckInActionReceiver : BroadcastReceiver() {
    companion object {
        const val EXTRA_MED_ID = "med_id"
        const val EXTRA_SLOT_KEY = "slot_key"
        const val EXTRA_SLOT_TIME = "slot_time"
        const val EXTRA_NOTIF_ID = "notif_id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? AshkbApplication ?: return
        val medId = intent.getStringExtra(EXTRA_MED_ID) ?: return
        val slotKey = intent.getStringExtra(EXTRA_SLOT_KEY)
        val slotTime = intent.getStringExtra(EXTRA_SLOT_TIME)
        val notifId = intent.getIntExtra(EXTRA_NOTIF_ID, -1)

        val result = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val repo = app.medicationRepository
                repo.checkInByMedId(medId, slotKey, slotTime)
                NotificationHelper.cancel(context, medId, slotKey)
                if (notifId != -1) {
                    androidx.core.app.NotificationManagerCompat.from(context).cancel(notifId)
                }
                // 该槽位后续升级重查闹钟已无必要，且打卡后 rescheduleAll 由下次进应用/日界完成
            } finally {
                result.finish()
            }
        }
    }
}
