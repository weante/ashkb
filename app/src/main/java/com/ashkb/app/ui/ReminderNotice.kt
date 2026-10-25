package com.ashkb.app.ui

import android.content.Context
import com.ashkb.app.R
import com.ashkb.app.reminder.ReminderHealth

/**
 * v1.2.11（缺陷工单 B09）：把「这一轮提醒重排的结果」变成用户看得懂的一句话。
 *
 * ### 为什么需要它
 *
 * 工单原文的症状是：保存路径把整段重排 `runCatching` 掉，于是「数据存进去了、提醒一条没排上」
 * 全程无声——用户以为提醒已经按新时刻生效，第二天什么也没响，也无从知道该去哪里查。
 *
 * 三条产品约束（决定了本类的形状）：
 *  1. **不把数据写入伪报成失败**。文案首句固定是「数据已保存」，然后才是提醒的问题——
 *     这两件事在实现里本来就是两段（写库 → 重排），提示也必须分开说，否则用户会以为
 *     刚才那次修改没生效而重复操作。
 *  2. **正常路径绝不打扰**。[ReminderHealth.RunReport.ok] 为 true 时直接返回，
 *     一个槽位都没排（药单空、全部已打卡）也算正常——否则每次保存都弹一次「重排失败」，
 *     真失败时用户已经不看了（狼来了）。
 *  3. **提示必须可操作**。文案里写清去哪一页（「我的 → 提醒可靠性自检」），而不是只说
 *     「失败了」。该页已有「闹钟注册（最近一次重排）」实证行，进去就能看到尝试/失败计数。
 *
 * 用 [GlobalMessages] 而不是对话框：保存是连续操作（改完一条再改一条），阻塞式弹窗会打断节奏。
 */
object ReminderNotice {

    /**
     * 错误摘录上限（字符）。
     *
     * Snackbar 单行显示，超长会被截断成半句；这里主动截到一句能读的程度，完整堆栈在
     * `CrashLogger` 的 `last_nonfatal.txt` 里（`AlarmRegister.reschedule` 已留档）。
     */
    private const val MAX_ERROR_CHARS = 60

    /** 重排失败时投递可操作提示；成功（含「没槽位可排」）时什么都不做。 */
    fun postIfFailed(context: Context, report: ReminderHealth.RunReport) {
        if (report.ok) return
        val err = report.firstError?.trim()?.takeIf { it.isNotEmpty() }
        val text = if (err == null) {
            context.getString(R.string.reminder_reschedule_failed_unknown)
        } else {
            context.getString(R.string.reminder_reschedule_failed, err.excerpt())
        }
        GlobalMessages.post(text)
    }

    private fun String.excerpt(): String =
        if (length <= MAX_ERROR_CHARS) this else take(MAX_ERROR_CHARS) + "…"
}
