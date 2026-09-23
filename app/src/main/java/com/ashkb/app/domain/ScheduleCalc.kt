package com.ashkb.app.domain

import com.ashkb.app.data.entity.MedFrequency
import com.ashkb.app.data.entity.Medication
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
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
     * 未设置计划时刻时的兜底时刻。
     *
     * **唯一来源**：`slotsFor` 的注射分支与「添加/编辑药品」表单的初始值都取它——
     * 此前表单默认写 08:00、而 `slotsFor` 兜底 09:00，两者不一致，
     * 编辑一支没有存过时刻的老药时，表单显示的并不是它实际生效的时刻。
     */
    const val DEFAULT_PLAN_TIME = "09:00"

    /**
     * "HH:mm" → (时, 分)，供时间选择器的初始值使用。
     *
     * 必须**容得下任何历史值**（如 07:30、或脏数据 25:99）：解析不了或越界就回退
     * [DEFAULT_PLAN_TIME]。编辑页要能如实显示用户当初设过的时刻，
     * 而不是因为「不在常用候选里」就显示不出来。
     */
    fun timeParts(hhmm: String?): Pair<Int, Int> {
        val parts = hhmm?.split(":")
        val h = parts?.getOrNull(0)?.toIntOrNull()
        val m = parts?.getOrNull(1)?.toIntOrNull()
        if (h == null || m == null || h !in 0..23 || m !in 0..59) {
            val d = DEFAULT_PLAN_TIME.split(":")
            return d[0].toInt() to d[1].toInt()
        }
        return h to m
    }

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

    /**
     * 某药品某日的计划槽位列表（PRN 返回空——使用记录不受计划约束）。
     *
     * **`label` 一律是计划时刻**（口服与注射都一样）：今日卡的 chip 直接显示它，
     * 用户才能看到「计划用药时间」。注射曾把 label 写死为「注射」，
     * 于是计划时刻在全应用无处可见（`key="inj"` 已经表达了「这是注射槽位」，
     * 「注射」二字由卡片上的给药途径 chip 承担，不必重复）。
     */
    fun slotsFor(med: Medication, date: LocalDate): List<PlanSlot> {
        val freq = MedFrequency.fromKey(med.frequency)
        if (freq == MedFrequency.PRN) return emptyList()

        // 固定星期给药（WEEKLY 一天 / BIW 两天）：口服按时刻出多槽；注射按默认/首选时刻出一针
        if (freq == MedFrequency.WEEKLY || freq == MedFrequency.BIW) {
            val wds = if (freq == MedFrequency.BIW) listOfNotNull(med.weeklyWeekday, med.weeklyWeekday2)
            else listOfNotNull(med.weeklyWeekday)
            if (wds.isEmpty() || date.dayOfWeek.value !in wds) return emptyList()
            if (med.route == "injection") {
                val t = takeTimesOf(med).firstOrNull() ?: DEFAULT_PLAN_TIME
                return listOf(PlanSlot("inj", t, t))
            }
            val times = takeTimesOf(med)
            return times.map { PlanSlot(it, it, slotLabel(it, med)) }
        }

        if (med.route == "injection") {
            return if (isInjectionDay(med, date) && (freq == MedFrequency.Q2W || freq == MedFrequency.CUSTOM)) {
                val t = takeTimesOf(med).firstOrNull() ?: DEFAULT_PLAN_TIME
                listOf(PlanSlot("inj", t, t))
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

    private val HHMM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    /**
     * ISO 时刻串 → "HH:mm"（展示用）；解析不了返回 null。
     *
     * **不要用 `takeLast(5)` 从串尾截取**：写入侧 `nowIso()` 用的是
     * `DateTimeFormatter.ISO_LOCAL_DATE_TIME`，它在**纳秒非零时会追加小数秒**，
     * 于是串长在 19 / 23 / 26 之间浮动（如 `2026-09-23T22:31:15.019981`），
     * 从尾部截 5 个字符截到的是小数秒的数字——今日页曾因此把「已服 22:31」
     * 显示成「已服 19981」。
     *
     * 凡是要从 ISO 时刻里取「时刻」，都必须**解析**（本方法）或**从头截**
     * （`take(16)` 取到分钟），不能从尾截。
     */
    fun hhmm(iso: String?): String? = iso?.let {
        runCatching { LocalDateTime.parse(it).format(HHMM) }.getOrNull()
    }

    fun isLate(scheduledTime: String?, takenAtIso: String?, date: LocalDate): Boolean {
        if (scheduledTime == null || takenAtIso == null) return false
        val taken = runCatching { LocalDateTime.parse(takenAtIso) }.getOrNull() ?: return false
        // P5 修订：计划时刻必须锚定在归属日 date 上（原实现用打卡日重建，
        // 次日补打卡差值恒为 0，late 判定失效污染依从统计）
        val scheduled = runCatching {
            date.atTime(scheduledTime.split(":")[0].toInt(), scheduledTime.split(":")[1].toInt())
        }.getOrNull() ?: return false
        return java.time.Duration.between(scheduled, taken).toMinutes() > LATE_TOLERANCE_MIN
    }
}
