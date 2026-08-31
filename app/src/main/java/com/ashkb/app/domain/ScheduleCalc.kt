package com.ashkb.app.domain

import com.ashkb.app.data.entity.MedFrequency
import com.ashkb.app.data.entity.Medication
import java.time.LocalDate
import org.json.JSONArray

/** 槽位键：口服 = "HH:mm"；注射日 = "inj"；PRN 无 */
data class PlanSlot(val key: String, val time: String?, val label: String)

object ScheduleCalc {

    /** 解析 take_times JSON → List<"HH:mm">，空安全；过滤非法时刻（如 25:99——防 LocalTime.parse 崩溃） */
    fun takeTimesOf(med: Medication): List<String> {
        val raw = med.takeTimes ?: return emptyList()
        return runCatching {
            JSONArray(raw).let { arr -> (0 until arr.length()).map { arr.getString(it) } }
        }.getOrDefault(emptyList()).filter { TIME_PATTERN.matches(it) }
    }

    /** 合法时刻：00:00–23:59（P5 修订：原 \d{2}:\d{2} 会放行 25:99 → 提醒重排崩溃） */
    val TIME_PATTERN = Regex("([01]\\d|2[0-3]):[0-5]\\d")

    /**
     * 判定药品在某日是否有注射任务：
     * 锚点 = start_date；周期 = inj_cycle_days（q2w=14）。
     * 当日与锚点差值 mod 周期 == 0 → 注射日。锚点当日也算。
     */
    fun isInjectionDay(med: Medication, date: LocalDate): Boolean {
        val cycle = med.injCycleDays ?: return false
        if (cycle <= 0) return false
        val anchor = runCatching { LocalDate.parse(med.startDate) }.getOrNull() ?: return false
        val days = java.time.temporal.ChronoUnit.DAYS.between(anchor, date)
        return days >= 0 && days % cycle == 0L
    }

    /** 某药品某日的计划槽位列表（PRN 返回空——使用记录不受计划约束） */
    fun slotsFor(med: Medication, date: LocalDate): List<PlanSlot> {
        val freq = MedFrequency.fromKey(med.frequency)
        if (freq == MedFrequency.PRN) return emptyList()

        // 固定星期给药（WEEKLY 一天 / BIW 两天）：口服按时刻出多槽；注射按默认/首选时刻出一针
        if (freq == MedFrequency.WEEKLY || freq == MedFrequency.BIW) {
            val wds = if (freq == MedFrequency.BIW) listOfNotNull(med.weeklyWeekday, med.weeklyWeekday2)
            else listOfNotNull(med.weeklyWeekday)
            if (wds.isEmpty() || date.dayOfWeek.value !in wds) return emptyList()
            if (med.route == "injection") {
                val t = takeTimesOf(med).firstOrNull() ?: "09:00"
                return listOf(PlanSlot("inj", t, "注射"))
            }
            val times = takeTimesOf(med)
            return times.map { PlanSlot(it, it, slotLabel(it, med)) }
        }

        if (med.route == "injection") {
            return if (isInjectionDay(med, date) && (freq == MedFrequency.Q2W || freq == MedFrequency.CUSTOM)) {
                val t = takeTimesOf(med).firstOrNull() ?: "09:00"
                listOf(PlanSlot("inj", t, "注射"))
            } else emptyList()
        }

        val times = takeTimesOf(med)
        return times.map { PlanSlot(it, it, slotLabel(it, med)) }
    }

    /** 晨起空腹药（itx-015 双膦酸盐类）槽位标签加提示 */
    private fun slotLabel(time: String, med: Medication): String {
        if (med.takeWithFood == "empty_stomach") return "$time · 晨起空腹"
        return time
    }

    /** late 判定：实际执行晚于计划时刻 + 容差（分钟） */
    const val LATE_TOLERANCE_MIN = 30L

    fun isLate(scheduledTime: String?, takenAtIso: String?, date: LocalDate): Boolean {
        if (scheduledTime == null || takenAtIso == null) return false
        val taken = runCatching { java.time.LocalDateTime.parse(takenAtIso) }.getOrNull() ?: return false
        // P5 修订：计划时刻必须锚定在归属日 date 上（原实现用打卡日重建，
        // 次日补打卡差值恒为 0，late 判定失效污染依从统计）
        val scheduled = runCatching {
            date.atTime(scheduledTime.split(":")[0].toInt(), scheduledTime.split(":")[1].toInt())
        }.getOrNull() ?: return false
        return java.time.Duration.between(scheduled, taken).toMinutes() > LATE_TOLERANCE_MIN
    }
}
