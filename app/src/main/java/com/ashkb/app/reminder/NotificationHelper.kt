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
import com.ashkb.app.data.repo.ReminderConfigRepository
import com.ashkb.app.domain.DndWindow
import java.time.LocalDateTime

object NotificationHelper {
    const val CHANNEL_MED = "med_reminders"
    const val CHANNEL_SYS = "sys_notices"
    /** v1.0.59 B5：三源提醒通道——让用户可分别静音（与用药提醒独立）。 */
    const val CHANNEL_CHECKUP = "checkup_reminders"
    const val CHANNEL_QUESTIONNAIRE = "questionnaire_reminders"
    const val CHANNEL_EXERCISE = "exercise_reminders"
    /** v1.0.60 B8：免打扰时段的静默通道——不响不震，通知栏仍可见。 */
    const val CHANNEL_REMINDER_SILENT = "reminder_silent"
    /** v1.0.60 B8：所有提醒归入同一通知组，2+ 条时折叠为 summary。 */
    const val GROUP_REMINDERS = "ashkb_reminders"
    private const val SUMMARY_ID = 100001

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
        // v1.0.59 B5：复诊用 IMPORTANCE_HIGH（不漏），问卷与运动用 DEFAULT（非紧急）
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_CHECKUP, context.getString(R.string.notif_channel_checkup_name), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.notif_channel_checkup_desc)
                enableVibration(true)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_QUESTIONNAIRE, context.getString(R.string.notif_channel_questionnaire_name), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = context.getString(R.string.notif_channel_questionnaire_desc)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_EXERCISE, context.getString(R.string.notif_channel_exercise_name), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = context.getString(R.string.notif_channel_exercise_desc)
            }
        )
        // v1.0.60 B8：免打扰时段静默通道——IMPORTANCE_LOW，不响不震，通知栏仍可见
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_REMINDER_SILENT, context.getString(R.string.notif_channel_silent_name), NotificationManager.IMPORTANCE_LOW).apply {
                description = context.getString(R.string.notif_channel_silent_desc)
                enableVibration(false)
                setSound(null, null)
            }
        )
    }

    /**
     * v1.0.60 B8：更新提醒通知组的 summary。
     * 当前有 2+ 条提醒通知时显示 summary（折叠多条，仅 summary 发声）；
     * 不足 2 条则取消 summary（单条提醒自身发声）。
     */
    private fun updateGroupSummary(context: Context) {
        val nm = NotificationManagerCompat.from(context)
        val count = runCatching {
            nm.activeNotifications.count {
                it.id != SUMMARY_ID && it.notification?.group == GROUP_REMINDERS
            }
        }.getOrDefault(0)
        if (count >= 2) {
            val summary = NotificationCompat.Builder(context, CHANNEL_SYS)
                .setSmallIcon(R.drawable.ic_stat_pill)
                .setGroup(GROUP_REMINDERS)
                .setGroupSummary(true)
                .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)
                .setContentTitle(context.getString(R.string.notif_group_summary_title))
                .setContentText(context.getString(R.string.notif_group_summary_text, count))
                .setAutoCancel(true)
                .build()
            runCatching { nm.notify(SUMMARY_ID, summary) }
        } else {
            runCatching { nm.cancel(SUMMARY_ID) }
        }
    }

    fun canPost(context: Context): Boolean =
        androidx.core.content.ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

    /**
     * v1.0.60 B8：当前时刻是否在免打扰时段内。
     * Receiver 调用以决定通知走正常通道还是静默通道。
     */
    fun isInDndNow(context: Context): Boolean {
        val cfg = ReminderConfigRepository(context)
        if (!cfg.dndEnabled()) return false
        return runCatching {
            DndWindow.isInDnd(LocalDateTime.now(), cfg.dndStart(), cfg.dndEnd())
        }.getOrDefault(false)
    }

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
        silent: Boolean = false,
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
        val channel = if (silent) CHANNEL_REMINDER_SILENT else CHANNEL_MED
        val n = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_pill)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(if (silent) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(open)
            .setGroup(GROUP_REMINDERS)
            .addAction(0, context.getString(R.string.notif_action_taken), done)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(notifId(medId, slotKey), n) }
        updateGroupSummary(context)
    }

    fun cancel(context: Context, medId: String, slotKey: String?) {
        NotificationManagerCompat.from(context).cancel(notifId(medId, slotKey))
        updateGroupSummary(context)
    }

    private fun notifId(medId: String, slotKey: String?): Int =
        ("n" + medId + (slotKey ?: "prn")).hashCode()

    // ======================= v1.0.59 B5：三源提醒通知 =======================
    //
    // 通知点击仅打开 MainActivity（落到首页），用户自行导航到对应 tab——
    // 复诊在「健康」tab、BASDAI 在「症状」tab、运动在「运动」tab。
    // 刻意不做 intent extras 路由：避免给 MainActivity 加状态机，破坏既有简洁性。

    /** 复诊提醒。phase = "prep" 提前 1 天准备清单 / "day" 当日复诊。 */
    fun postCheckupReminder(
        context: Context,
        nextDate: String,
        phase: String,
        silent: Boolean = false,
    ) {
        if (!canPost(context)) return
        val open = openMainActivity(context, "chk|$nextDate|$phase")
        val title = if (phase == "prep")
            context.getString(R.string.notif_checkup_prep_title)
        else context.getString(R.string.notif_checkup_day_title)
        val text = if (phase == "prep")
            context.getString(R.string.notif_checkup_prep_text, nextDate)
        else context.getString(R.string.notif_checkup_day_text, nextDate)
        val channel = if (silent) CHANNEL_REMINDER_SILENT else CHANNEL_CHECKUP
        val n = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_pill)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(if (silent) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(open)
            .setGroup(GROUP_REMINDERS)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(notifIdCheckup(nextDate, phase), n) }
        updateGroupSummary(context)
    }

    /** BASDAI 评估提醒。escalation: 0 首次 / 1-2 重复提醒（文案加急）。 */
    fun postBasdaiReminder(
        context: Context,
        dueDate: String,
        escalation: Int,
        silent: Boolean = false,
    ) {
        if (!canPost(context)) return
        val open = openMainActivity(context, "bas|$dueDate|$escalation")
        val title = if (escalation > 0)
            context.getString(R.string.notif_basdai_escalated_title)
        else context.getString(R.string.notif_basdai_title)
        val text = if (escalation > 0)
            context.getString(R.string.notif_basdai_escalated_text)
        else context.getString(R.string.notif_basdai_text)
        val channel = if (silent) CHANNEL_REMINDER_SILENT else CHANNEL_QUESTIONNAIRE
        val n = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_pill)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(if (silent) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(open)
            .setGroup(GROUP_REMINDERS)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(notifIdBasdai(dueDate, escalation), n) }
        updateGroupSummary(context)
    }

    /** 运动提醒。escalation: 0 首次 / 1-2 重复提醒（文案加急）。 */
    fun postExerciseReminder(
        context: Context,
        date: String,
        escalation: Int,
        silent: Boolean = false,
    ) {
        if (!canPost(context)) return
        val open = openMainActivity(context, "exc|$date|$escalation")
        val title = if (escalation > 0)
            context.getString(R.string.notif_exercise_escalated_title)
        else context.getString(R.string.notif_exercise_title)
        val text = if (escalation > 0)
            context.getString(R.string.notif_exercise_escalated_text)
        else context.getString(R.string.notif_exercise_text)
        val channel = if (silent) CHANNEL_REMINDER_SILENT else CHANNEL_EXERCISE
        val n = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_pill)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(if (silent) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(open)
            .setGroup(GROUP_REMINDERS)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(notifIdExercise(date, escalation), n) }
        updateGroupSummary(context)
    }

    fun cancelCheckup(context: Context, nextDate: String, phase: String) {
        NotificationManagerCompat.from(context).cancel(notifIdCheckup(nextDate, phase))
        updateGroupSummary(context)
    }

    fun cancelBasdai(context: Context, dueDate: String, escalation: Int) {
        NotificationManagerCompat.from(context).cancel(notifIdBasdai(dueDate, escalation))
        updateGroupSummary(context)
    }

    fun cancelExercise(context: Context, date: String, escalation: Int) {
        NotificationManagerCompat.from(context).cancel(notifIdExercise(date, escalation))
        updateGroupSummary(context)
    }

    private fun openMainActivity(context: Context, key: String): PendingIntent =
        PendingIntent.getActivity(
            context, key.hashCode(),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /** 三源 notifId 用前缀分隔，防与用药通知撞 ID。 */
    private fun notifIdCheckup(nextDate: String, phase: String): Int =
        ("n_chk|$nextDate|$phase").hashCode()

    private fun notifIdBasdai(dueDate: String, escalation: Int): Int =
        ("n_bas|$dueDate|$escalation").hashCode()

    private fun notifIdExercise(date: String, escalation: Int): Int =
        ("n_exc|$date|$escalation").hashCode()
}
