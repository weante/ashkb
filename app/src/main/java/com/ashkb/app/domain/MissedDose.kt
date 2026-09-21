package com.ashkb.app.domain

import com.ashkb.app.data.entity.Medication

/**
 * 漏服 / 延迟处理指引（v1.0.33，规划缺口 C7）。
 *
 * 规划原文要求：「口服按通用补服规则、注射按『窗口期内尽快补注 / 超窗联系医师』分级」，
 * 此前注射只有「顺延」一种处理，口服没有任何补服提示。
 *
 * 本对象只做**通用安全提示**，不针对具体药品给出个体化剂量决策——具体是否补服、
 * 补多少，始终以药品说明书与主治医师医嘱为准。这是医疗类应用的边界（v2 §1.7 不做诊断决策）。
 *
 * 纯函数、无 Android 依赖，可单测。
 */
object MissedDose {

    /** 口服：超过计划时刻多久内仍建议「尽快补服」（分钟）——2 小时。 */
    const val ORAL_CATCH_UP_MIN = 120L

    /** 注射：超窗判定（小时）——48 小时。生物制剂说明书通常允许一定窗口，超窗需问医生。 */
    const val INJECTION_WINDOW_HOURS = 48L

    data class Guidance(
        /** 一句话结论，如「可以尽快补服」 */
        val headline: String,
        /** 分步说明（2–4 条） */
        val steps: List<String>,
        /** true = 需联系医生（注射超窗 / 免疫抑制类） */
        val contactDoctor: Boolean,
    )

    /**
     * 生成指引。
     *
     * @param route     "oral" / "injection"
     * @param minutesLate 距计划时刻已过分钟数（< 0 或未超容差时由调用方传 0 或不调用）
     * @param isPrn     按需用药——无固定计划，不存在「漏服」，返回 null
     * @param immunosuppressant 免疫抑制类（生物制剂 / JAK / 传统 DMARD / 糖皮质激素）
     * @return null = 无需指引（按需药 / 未超时）
     */
    fun guidance(
        route: String,
        minutesLate: Long,
        isPrn: Boolean,
        immunosuppressant: Boolean = false,
    ): Guidance? {
        if (isPrn || minutesLate <= 0L) return null

        return if (route == "injection") injection(minutesLate, immunosuppressant)
        else oral(minutesLate)
    }

    private fun oral(minutesLate: Long): Guidance {
        val hours = minutesLate / 60L
        return if (minutesLate <= ORAL_CATCH_UP_MIN) {
            Guidance(
                headline = "可尽快补服",
                steps = listOf(
                    "距计划时间不久（约 ${fmt(hours, minutesLate)}），想起后尽快补服即可。",
                    "若已接近下一次服药时间，则跳过本次，**不要一次服两份剂量**。",
                    "补服后照常在今日打卡里记录，便于依从统计。",
                ),
                contactDoctor = false,
            )
        } else {
            Guidance(
                headline = "建议跳过本次",
                steps = listOf(
                    "已超过计划时间约 ${fmt(hours, minutesLate)}，建议跳过本次、按原计划服下一次。",
                    "**切勿加倍剂量**补回——加倍会升高副作用风险。",
                    "若频繁漏服（如每周多次），请与医生沟通调整方案或改用提醒更密的剂型。",
                ),
                contactDoctor = false,
            )
        }
    }

    private fun injection(minutesLate: Long, immunosuppressant: Boolean): Guidance {
        val hours = minutesLate / 60L
        // 用分钟直接比较：若先整除成小时再比，48h+1min 会被截断成 48h 而误判仍在窗口内
        val inWindow = minutesLate <= INJECTION_WINDOW_HOURS * 60L
        return if (inWindow) {
            Guidance(
                headline = "窗口期内可尽快补注",
                steps = listOf(
                    "距计划时间约 ${fmt(hours, minutesLate)}，仍在常见补注窗口（${INJECTION_WINDOW_HOURS} 小时内）内。",
                    "尽快补注，之后按**原注射周期**顺延安排下一次，不要提前。",
                    if (immunosuppressant) "免疫抑制 / 生物制剂类：若已漏注多日或不确定是否补注，先电话咨询风湿科医生。"
                    else "如补注后出现不适，及时联系医生。",
                ),
                contactDoctor = immunosuppressant,
            )
        } else {
            Guidance(
                headline = "已超窗，请先联系医生",
                steps = listOf(
                    "距计划时间已超过 ${INJECTION_WINDOW_HOURS} 小时（约 ${fmt(hours, minutesLate)}），超出常规补注窗口。",
                    "**请先联系风湿科医生**确认补注方案（是否需要补注、何时补注、是否调整周期）。",
                    "在医生确认前不要自行加倍或缩短间隔注射。",
                    if (immunosuppressant) "生物制剂 / 免疫抑制剂中断可能影响病情控制，越早与医生沟通越好。"
                    else "任何调整以主治医师医嘱为准。",
                ),
                contactDoctor = true,
            )
        }
    }

    /** 人话化的时间跨度：< 1 小时报分钟，否则报小时（保留一位小数）。 */
    private fun fmt(hours: Long, minutes: Long): String =
        if (hours < 1L) "$minutes 分钟"
        else "%.1f 小时".format(minutes / 60.0)

    /** 从「现在」与计划时刻算已过分钟数（同一归属日内）。计划时刻非法 / 未到点返回 0。 */
    fun minutesLate(scheduledTime: String?, nowMinutesOfDay: Int): Long {
        if (scheduledTime.isNullOrBlank()) return 0L
        val parts = scheduledTime.split(":")
        if (parts.size != 2) return 0L
        val h = parts[0].toIntOrNull() ?: return 0L
        val m = parts[1].toIntOrNull() ?: return 0L
        if (h !in 0..23 || m !in 0..59) return 0L
        val diff = nowMinutesOfDay - (h * 60 + m)
        return if (diff > 0) diff.toLong() else 0L
    }

    /** 便利入口：直接吃 Medication + 当前分钟数，内部判免疫抑制。 */
    fun guidanceFor(med: Medication, minutesLate: Long, isPrn: Boolean): Guidance? =
        guidance(
            route = med.route,
            minutesLate = minutesLate,
            isPrn = isPrn,
            immunosuppressant = EmergencyMeds.isImmunosuppressant(med.medClass),
        )
}
