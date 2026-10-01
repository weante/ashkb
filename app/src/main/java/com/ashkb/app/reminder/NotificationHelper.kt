package com.ashkb.app.reminder

import android.app.Notification
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
import com.ashkb.app.ReminderFullScreenActivity
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
    /** v1.0.66 B6a：锁屏紧急信息的常驻通道——静默、锁屏公开可见。 */
    const val CHANNEL_EMERGENCY_LOCKSCREEN = "emergency_lockscreen"
    /** v1.0.68 C8a：久坐起身提醒通道。 */
    const val CHANNEL_SEDENTARY = "sedentary_reminders"
    /** v1.0.60 B8：所有提醒归入同一通知组，2+ 条时折叠为 summary。 */
    const val GROUP_REMINDERS = "ashkb_reminders"
    private const val SUMMARY_ID = 100001
    /** v1.0.62 C11：测试提醒通知 ID（固定单发）。 */
    private const val NOTIF_ID_TEST = 100002
    /** v1.0.66 B6a：锁屏紧急信息常驻通知 ID（固定单条）。 */
    private const val NOTIF_ID_EMERGENCY_CARD = 100003
    /** v1.0.68 C8a：久坐提示 ID（固定单条，覆盖而非堆积）。 */
    private const val NOTIF_ID_SEDENTARY = 100004
    /** v1.0.77（批次 3b）：漏服补发 ID（固定单条——同一天重复调用只更新同一条）。 */
    private const val NOTIF_ID_MISSED_DOSES = 100005

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
        // v1.0.66 B6a：锁屏紧急信息——静默 + 锁屏公开可见
        //
        // ⚠️ v1.0.72 真机结论（小米 15 Pro / HyperOS 2）：这里的 `lockscreenVisibility = PUBLIC`
        // **在小米上不生效也不会回写**——`dumpsys notification` 里该通道始终是
        // `mLockscreenVisibility=-1000`（NO_OVERRIDE），用户改与不改都一样；MIUI 把「每通道锁屏
        // 可见性」存在它自己的存储里。实测**唯一的开关在系统侧**：
        //   设置 → 应用设置 → ASHKB → 通知管理 → 锁屏紧急信息 → 在锁定屏幕上 → 显示通知及其内容
        // （默认**不是**这一项；用户改成它之后，锁屏上即稳定可见 —— 见 HANDOFF §7）。
        // AOSP 侧保留 PUBLIC 仍然正确（Pixel 等原生系统按此显示内容），故不删，仅在此备案。
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_EMERGENCY_LOCKSCREEN, context.getString(R.string.notif_channel_emergency_name), NotificationManager.IMPORTANCE_LOW).apply {
                description = context.getString(R.string.notif_channel_emergency_desc)
                enableVibration(false)
                setSound(null, null)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
        )
        // v1.0.68 C8a：久坐起身提醒——DEFAULT（要有存在感），免打扰时段内改走静默通道
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_SEDENTARY, context.getString(R.string.notif_channel_sedentary_name), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = context.getString(R.string.notif_channel_sedentary_desc)
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
        slotDate: java.time.LocalDate?,
        medName: String,
        dose: String,
        escalation: Int,
        silent: Boolean = false,
        strong: Boolean = false,
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
            // v1.0.73（P1-4）：把**槽位所属日期**带到打卡动作上——跨零点时 LocalDate.now()
            // 已是次日，会把日志记到次日、原槽位永远空缺（依从率虚低）。
            slotDate?.let { putExtra(CheckInActionReceiver.EXTRA_SLOT_DATE, it.toString()) }
            putExtra(CheckInActionReceiver.EXTRA_NOTIF_ID, notifId(medId, slotKey))
        }
        val done = PendingIntent.getBroadcast(
            context, (medId + (slotKey ?: "prn")).hashCode(), doneIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        // v1.0.61 B9：末级升级 → 强提醒（全屏 Intent 唤醒锁屏）；免打扰时段内不升级全屏
        val effectiveStrong = strong && !silent
        val title = when {
            effectiveStrong -> context.getString(R.string.notif_med_strong_title, medName, dose)
            escalation > 0 -> context.getString(R.string.notif_med_escalated_title, medName, dose)
            else -> context.getString(R.string.notif_med_title, medName, dose)
        }
        val text = when {
            effectiveStrong -> context.getString(R.string.notif_med_strong_text)
            escalation > 0 -> context.getString(R.string.notif_med_escalated_text)
            else -> context.getString(R.string.notif_med_text)
        }
        val channel = if (silent) CHANNEL_REMINDER_SILENT else CHANNEL_MED
        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_pill)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(if (silent) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(open)
            .setGroup(GROUP_REMINDERS)
            .addAction(0, context.getString(R.string.notif_action_taken), done)
        if (effectiveStrong && canUseFullScreen(context)) {
            val fsIntent = Intent(context, ReminderFullScreenActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra(ReminderFullScreenActivity.EXTRA_MED_ID, medId)
                putExtra(ReminderFullScreenActivity.EXTRA_SLOT_KEY, slotKey)
                putExtra(ReminderFullScreenActivity.EXTRA_SLOT_TIME, slotTime)
                // v1.0.73（P1-4/P1-2）：全屏页的「已服用」要写回**槽位所属日**，「稍后」要能排 snooze
                slotDate?.let { putExtra(ReminderFullScreenActivity.EXTRA_SLOT_DATE, it.toString()) }
            }
            val fs = PendingIntent.getActivity(
                context, ("fs|" + medId + (slotKey ?: "prn")).hashCode(), fsIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder.setFullScreenIntent(fs, true)
        } else if (effectiveStrong) {
            // v1.0.73（P0-3）：**不再挂一个会被系统忽略的 FSI**。targetSdk 34 下若未授予
            // USE_FULL_SCREEN_INTENT，`setFullScreenIntent` 会被静默忽略，被设计为「漏服最后
            // 一道防线」的末级提醒会悄悄退化成一条普通横幅。此处改为**明确降级**：
            //   · 常驻（setOngoing）——用户不处理就不会消失，仍走 CHANNEL_MED（IMPORTANCE_HIGH，有声音）
            //   · 文案写明「未授予全屏权限、已用常驻提醒代替」，并指向自检页开启
            // 打卡 / 跳过 / 全屏页「已服用」三条路径都会 cancel 它，故不会赖着不走。
            // 注：即便本机已授予，MIUI 的私有开关（MIUIOP 10020/10021）仍可能拦住全屏——
            // 那是系统行为、应用无法查询，故自检页给的是指引而非保证（见 HANDOFF §7）。
            builder.setOngoing(true)
            builder.setContentText(context.getString(R.string.notif_med_strong_degraded_text))
        }
        val n = builder.build()
        runCatching { NotificationManagerCompat.from(context).notify(notifId(medId, slotKey), n) }
        updateGroupSummary(context)
    }

    fun cancel(context: Context, medId: String, slotKey: String?) {
        NotificationManagerCompat.from(context).cancel(notifId(medId, slotKey))
        updateGroupSummary(context)
    }

    private fun notifId(medId: String, slotKey: String?): Int =
        ("n" + medId + (slotKey ?: "prn")).hashCode()

    /**
     * v1.0.73（P0-3）：本机是否**真的**能用全屏 Intent。
     *
     * API 34+ 起 `USE_FULL_SCREEN_INTENT` 是「按应用授予」的 appop：未授予时
     * `setFullScreenIntent` 会被系统静默忽略（不报错、不异常），因此投递前必须先问能力，
     * 否则「末级强提醒」会一路静默降级成普通横幅而无人知晓。
     * 34 以下该权限安装即授予，无需检查。
     */
    private fun canUseFullScreen(context: Context): Boolean {
        if (android.os.Build.VERSION.SDK_INT < 34) return true
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return false
        return runCatching { nm.canUseFullScreenIntent() }.getOrDefault(false)
    }

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

    /**
     * v1.0.62 C11：测试提醒通知——用于「提醒可靠性自检」的端到端链路验证。
     *
     * 刻意**不归入 `ashkb_reminders` 组**：它是自检的一次性通知，不参与提醒折叠统计。
     */
    fun postTestReminder(context: Context, silent: Boolean = false) {
        if (!canPost(context)) return
        val open = openMainActivity(context, "test")
        val channel = if (silent) CHANNEL_REMINDER_SILENT else CHANNEL_SYS
        val n = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_pill)
            .setContentTitle(context.getString(R.string.notif_test_title))
            .setContentText(context.getString(R.string.notif_test_text))
            .setPriority(if (silent) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(NOTIF_ID_TEST, n) }
    }

    /**
     * v1.0.66 B6a：锁屏紧急信息——**常驻 + 锁屏公开可见**。
     *
     * `VISIBILITY_PUBLIC` 让内容在锁屏直接显示、无需解锁（急救场景的关键）；
     * `setOngoing(true)` 使其不可被划掉（避免家人误清）；
     * 通道为 IMPORTANCE_LOW，故不响不震（常驻卡不是"提醒"）。
     * 刻意**不归入提醒折叠组**——它不是待处理提醒。
     */
    fun postLockscreenEmergencyCard(context: Context, title: String, lines: List<String>) {
        if (!canPost(context)) return
        val open = openMainActivity(context, "emergency_lockscreen")
        val body = lines.joinToString("\n")
        val n = NotificationCompat.Builder(context, CHANNEL_EMERGENCY_LOCKSCREEN)
            .setSmallIcon(R.drawable.ic_stat_pill)
            .setContentTitle(title)
            .setContentText(lines.firstOrNull().orEmpty())
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(NOTIF_ID_EMERGENCY_CARD, n) }
    }

    fun cancelLockscreenEmergencyCard(context: Context) {
        runCatching { NotificationManagerCompat.from(context).cancel(NOTIF_ID_EMERGENCY_CARD) }
    }

    /**
     * v1.0.68 C8a：久坐起身提醒。
     *
     * 固定 ID（单条覆盖）：几次提醒在通知栏里叠成一堆没有意义；
     * 清掉旧的一条、换成新的时刻即可。
     */
    fun postSedentaryReminder(context: Context, silent: Boolean = false) {
        if (!canPost(context)) return
        val open = openMainActivity(context, "sedentary")
        val channel = if (silent) CHANNEL_REMINDER_SILENT else CHANNEL_SEDENTARY
        val n = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_pill)
            .setContentTitle(context.getString(R.string.notif_sedentary_title))
            .setContentText(context.getString(R.string.notif_sedentary_text))
            .setPriority(if (silent) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(NOTIF_ID_SEDENTARY, n) }
    }

    /**
     * v1.0.77（批次 3b）：**漏服补发**——昨天有 N 剂未记录的汇总提醒（一条，不逐剂发）。
     *
     * 通道**复用现有的**，不新建：新建通道在系统里等于又让用户授权 / 重新调一遍重要性，
     * 而这条通知本来就是用药提醒的一种（未记录 = 没打卡）。
     *  · 正常时段 → [CHANNEL_MED]（用户已有的「用药提醒」，重要级别高、有声音）；
     *  · 免打扰时段 → [CHANNEL_REMINDER_SILENT]（v1.0.60 B8 的静默通道：不响不震、通知栏仍可见）。
     * 与用药提醒同样归入 [GROUP_REMINDERS]，多条提醒会被折叠统计（不会各自刷屏）。
     *
     * 通知 id **固定**（[NOTIF_ID_MISSED_DOSES]）：同一天重复调用只更新同一条。
     * 点击只进应用（MainActivity 落到首页）——补记入口在今日页的「昨天未记录」卡上，
     * 刻意不做 intent 路由（同三源提醒的取舍：不给 MainActivity 加状态机）。
     *
     * @return 是否真的投递了（未授予通知权限时为 false——调用方据此决定要不要记「今天已提醒」，
     *   免得权限补授之后这条提醒被那次失败静默吃掉）
     */
    fun postMissedDoses(context: Context, count: Int, medNames: List<String>): Boolean {
        if (!canPost(context)) return false
        val silent = isInDndNow(context)
        val channel = if (silent) CHANNEL_REMINDER_SILENT else CHANNEL_MED
        val names = medNames.joinToString("、")
        val n = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_pill)
            .setContentTitle(context.getString(R.string.notif_missed_doses_title, count))
            .setContentText(context.getString(R.string.notif_missed_doses_text, names))
            .setPriority(if (silent) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openMainActivity(context, "missed_doses"))
            .setGroup(GROUP_REMINDERS)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(NOTIF_ID_MISSED_DOSES, n) }
        updateGroupSummary(context)
        return true
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
