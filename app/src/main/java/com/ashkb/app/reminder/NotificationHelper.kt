package com.ashkb.app.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.ashkb.app.MainActivity
import com.ashkb.app.R

object NotificationHelper {
    const val CHANNEL_MED = "med_reminders"
    const val CHANNEL_SYS = "sys_notices"

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_MED, context.getString(R.string.notif_channel_med_name), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.notif_channel_med_desc)
                enableVibration(true)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_SYS, context.getString(R.string.notif_channel_sys_name), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = context.getString(R.string.notif_channel_sys_desc)
            }
        )
    }

    fun canPost(context: Context): Boolean =
        androidx.core.content.ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

    /**
     * 服药提醒通知：点击进入应用；动作按钮「已服用」直接写库免开应用。
     * escalation: 0 首次 / 1 重复提醒（文案加急）
     */
    fun postMedReminder(
        context: Context,
        medId: String,
        slotKey: String?,
        slotTime: String?,
        medName: String,
        dose: String,
        escalation: Int,
    ) {
        if (!canPost(context)) return
        val open = PendingIntent.getActivity(
            context, medId.hashCode(),
            Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val doneIntent = Intent(context, CheckInActionReceiver::class.java).apply {
            putExtra(CheckInActionReceiver.EXTRA_MED_ID, medId)
            putExtra(CheckInActionReceiver.EXTRA_SLOT_KEY, slotKey)
            putExtra(CheckInActionReceiver.EXTRA_SLOT_TIME, slotTime)
            putExtra(CheckInActionReceiver.EXTRA_NOTIF_ID, notifId(medId, slotKey))
        }
        val done = PendingIntent.getBroadcast(
            context, (medId + (slotKey ?: "prn")).hashCode(), doneIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val title = if (escalation > 0)
            context.getString(R.string.notif_med_escalated_title, medName, dose)
        else context.getString(R.string.notif_med_title, medName, dose)
        val text = if (escalation > 0)
            context.getString(R.string.notif_med_escalated_text)
        else context.getString(R.string.notif_med_text)
        val n = NotificationCompat.Builder(context, CHANNEL_MED)
            .setSmallIcon(R.drawable.ic_stat_pill)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(open)
            .addAction(0, context.getString(R.string.notif_action_taken), done)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(notifId(medId, slotKey), n) }
    }

    fun cancel(context: Context, medId: String, slotKey: String?) {
        NotificationManagerCompat.from(context).cancel(notifId(medId, slotKey))
    }

    private fun notifId(medId: String, slotKey: String?): Int =
        ("n" + medId + (slotKey ?: "prn")).hashCode()
}
