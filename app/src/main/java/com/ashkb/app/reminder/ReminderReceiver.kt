package com.ashkb.app.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ashkb.app.AshkbApplication
import com.ashkb.app.data.repo.nowIso
import com.ashkb.app.domain.AdherenceCalc
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

        /** v1.0.73：槽位**所属日期**（不是触发时刻的日期）——跨零点判定的锚点。 */
        const val EXTRA_SLOT_DATE = "slot_date"

        /** v1.0.73（P1-2）：本次是「稍后」触发的 snooze（不再继续升级）。 */
        const val EXTRA_SNOOZE = "snooze"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? AshkbApplication ?: return
        val medId = intent.getStringExtra(EXTRA_MED_ID) ?: return
        val slotKey = intent.getStringExtra(EXTRA_SLOT_KEY)
        val slotTime = intent.getStringExtra(EXTRA_SLOT_TIME)
        val escalation = intent.getIntExtra(EXTRA_ESCALATION, 0)
        val fireIso = intent.getStringExtra(EXTRA_FIRE_ISO) ?: nowIso()
        val snooze = intent.getBooleanExtra(EXTRA_SNOOZE, false)
        // v1.0.73（P1-4）：槽位日期缺失时（老版本排下的闹钟）退化为「触发当天」，
        // 与旧行为一致，不引入新的崩溃面。
        val slotDate = intent.getStringExtra(EXTRA_SLOT_DATE)
            ?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() }
            ?: java.time.LocalDate.now()

        val lock = WakeLock.acquire(context)
        val result = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val repo = app.medicationRepository
                val med = repo.medicationById(medId) ?: return@launch
                // R6 兜底：停药（归档）后不得再提醒——防停药路径之外任何来源残留的孤儿闹钟
                if (med.isArchived) {
                    NotificationHelper.cancel(context, medId, slotKey)
                    return@launch
                }
                // 计划仍在**槽位所属日**复查：顺延 / 停用 / 改时刻后残留的旧闹钟静默取消
                // （v1.0.73：改用 slotDate；用触发日会在跨零点时误判「今天没这个槽位」而取消链条）
                if (slotKey != null && com.ashkb.app.domain.ScheduleCalc
                        .slotsFor(med, slotDate).none { it.key == slotKey }
                ) {
                    NotificationHelper.cancel(context, medId, slotKey)
                    return@launch
                }
                // v1.0.73（P1-1）：done 与 skipped **都算已结算**。此前只认 "done"，于是用户
                // 主动「跳过（有原因）」后，+30 加急与 +60 锁屏全屏照样轰炸——这正是
                // 「骚扰 → 用户关掉通知权限 → 提醒彻底失效」的路径。判定常量化，避免裸字符串漂移。
                // v1.0.73（P1-4）：查的是**槽位所属日**的记录（跨零点时 LocalDate.now() 已是次日，
                // 会查不到 23:50 那个槽位的打卡，从而对已服的药补发提醒）。
                val settled = repo.logsForDate(slotDate)
                    .any {
                        it.medId == medId && it.slotKey == slotKey &&
                            (it.status == AdherenceCalc.DONE || it.status == AdherenceCalc.SKIPPED)
                    }
                if (settled) {
                    NotificationHelper.cancel(context, medId, slotKey)
                    return@launch
                }
                // v1.0.60 B8：免打扰时段静默投递（不响不震，通知栏仍可见）
                val silent = NotificationHelper.isInDndNow(context)
                // v1.0.61 B9：末级升级 → 强提醒（全屏 Intent）。
                // v1.0.73（P1-2）：snooze 一律不升级为全屏（否则「稍后」= 立刻再全屏一次）。
                val strong = !snooze && ReminderScheduler.isStrongEscalation(escalation)
                NotificationHelper.postMedReminder(
                    context, medId, slotKey, slotTime, slotDate, med.name, med.dose, escalation, silent, strong
                )
                // v1.0.73（P1-2）：snooze 只补一次提醒，**不再向上链**（避免无限循环）
                if (!snooze && escalation < ReminderScheduler.MAX_ESCALATION) {
                    val fire = runCatching { LocalDateTime.parse(fireIso) }
                        .getOrDefault(LocalDateTime.now().plusMinutes(ReminderScheduler.ESCALATION_STEP_MINUTES))
                    ReminderScheduler.scheduleEscalation(
                        context, medId, slotKey, slotTime, slotDate,
                        fire.plusMinutes(ReminderScheduler.ESCALATION_STEP_MINUTES), escalation + 1
                    )
                }
            } finally {
                lock?.let { runCatching { if (it.isHeld) it.release() } }
                result.finish()
            }
        }
    }
}
