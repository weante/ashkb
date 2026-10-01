package com.ashkb.app.reminder

import android.content.Context
import com.ashkb.app.data.db.AppDatabase
import com.ashkb.app.data.repo.ReminderConfigRepository
import com.ashkb.app.domain.MissedDoses
import java.time.LocalDate

/**
 * v1.0.77（批次 3b）：**漏服补发**（昨天有 N 剂未记录 → 一条汇总通知）。
 *
 * 为什么需要它：昨天那一剂既没点「已服用」也没点「跳过」时，只有今日页有提示（v1.0.74 的补记卡）。
 * 用户当天不打开应用就再也看不到——补记入口明明存在，缺的只是一条把他叫回来的通知。
 *
 * 三条硬约束与它们的理由：
 *  ① **每天最多一条**：记录「已提醒过的归属日」到 [ReminderConfigRepository]（见那里的注释）。
 *     冷启动 / 开机 / 改时钟会在一天内多次触发本方法，没有去重就会连发多条同样的提醒。
 *  ② **免打扰时段静默投递**：复用 [NotificationHelper.isInDndNow] 与既有的静默通道，
 *     不响不震、通知栏仍可见（与 v1.0.60 B8 的其它提醒一致）。
 *  ③ **通道复用**：不新建通道，避免用户再授权一遍、也不给他多一个要调的通知类别。
 *
 * 判定本身是纯函数 [MissedDoses.unsettled]（口径与今日页补记卡共用同一份「已结算」定义），
 * 这里只负责取数、投递与去重——提醒层因此不依赖 `ReminderScheduler`，也不需要 DAO 之外的逻辑。
 */
object MissedDoseReminder {

    /** 昨天一整天的计划槽位 + 日志 → 未记录条数与药名（取数在 IO 线程上执行，故是 suspend）。 */
    suspend fun summarize(context: Context, yesterday: LocalDate): MissedDoses.Summary {
        val db = AppDatabase.get(context)
        val day = yesterday.toString()
        val planned = db.plannedSlotDao().between(day, day)
        val logs = db.medicationLogDao().byDate(day)
        return MissedDoses.unsettled(planned, logs)
    }

    /**
     * 若昨天有未记录的剂量 → 发一条汇总通知。
     *
     * 调用点与 [ReminderScheduler.rescheduleAll] 同一批（应用启动 / 开机广播），
     * 且**必须在计划槽位物化之后**调用：没有昨天的计划快照，就不知道昨天该吃几剂。
     *
     * @param today 「今天」——可注入便于确定性单测；昨天 = `today - 1 天`
     */
    suspend fun checkAndNotify(context: Context, today: LocalDate = LocalDate.now()) {
        val yesterday = today.minusDays(1)
        val store = ReminderConfigRepository(context)
        // ① 去重：这个「昨天」已经发过了（同一归属日只提醒一次）
        if (store.missedDoseAlertedDate() == yesterday.toString()) return

        val summary = summarize(context, yesterday)
        if (!summary.shouldRemind) return

        // ② / ③ 投递（免打扰时段自动走静默通道，见 NotificationHelper.postMissedDoses）
        val posted = NotificationHelper.postMissedDoses(context, summary.count, summary.medNames)
        // 只有真的投出去了才记「已提醒」：没授予通知权限时不留标记，
        // 免得用户随后授予权限、当天却再也收不到这条提醒。
        if (posted) store.setMissedDoseAlertedDate(yesterday.toString())
    }
}
