package com.ashkb.app.domain

import androidx.annotation.StringRes
import com.ashkb.app.R
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
 * i18n：本对象不再拼文案，只给**资源 id + 参数**（[ResText]）。时间跨度在中英里的语序与量词
 * 都不同（「约 1.5 小时」/ "about 1.5 hours"），故按「< 1 小时报分钟，否则报小时（一位小数）」
 * 的口径在域层选好条目（`…_1_min` / `…_1_hour`），而不是让调用方自己拼 `"$n 分钟"`。
 *
 * 纯函数、无 Android 依赖（只用 `@StringRes Int` 标注，不碰 Context），可单测。
 */
object MissedDose {

    /** 口服：超过计划时刻多久内仍建议「尽快补服」（分钟）——2 小时。 */
    const val ORAL_CATCH_UP_MIN = 120L

    /** 注射：超窗判定（小时）——48 小时。生物制剂说明书通常允许一定窗口，超窗需问医生。 */
    const val INJECTION_WINDOW_HOURS = 48L

    data class Guidance(
        /** 一句话结论（无插值） */
        @StringRes val headlineRes: Int,
        /** 分步说明（2–4 条；首条含时间跨度插值） */
        val steps: List<ResText>,
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

    private fun oral(minutesLate: Long): Guidance =
        if (minutesLate <= ORAL_CATCH_UP_MIN) oralCatchUp(minutesLate) else oralSkip(minutesLate)

    private fun oralCatchUp(minutesLate: Long): Guidance = Guidance(
        headlineRes = R.string.dom_missed_head_oral_soon,
        steps = listOf(
            span(minutesLate, R.string.dom_missed_oral_soon_1_min, R.string.dom_missed_oral_soon_1_hour),
            ResText(R.string.dom_missed_oral_soon_2),
            ResText(R.string.dom_missed_oral_soon_3),
        ),
        contactDoctor = false,
    )

    private fun oralSkip(minutesLate: Long): Guidance = Guidance(
        headlineRes = R.string.dom_missed_head_oral_skip,
        steps = listOf(
            span(minutesLate, R.string.dom_missed_oral_skip_1_min, R.string.dom_missed_oral_skip_1_hour),
            ResText(R.string.dom_missed_oral_skip_2),
            ResText(R.string.dom_missed_oral_skip_3),
        ),
        contactDoctor = false,
    )

    private fun injection(minutesLate: Long, immunosuppressant: Boolean): Guidance {
        // 用分钟直接比较：若先整除成小时再比，48h+1min 会被截断成 48h 而误判仍在窗口内
        val inWindow = minutesLate <= INJECTION_WINDOW_HOURS * 60L
        return if (inWindow) {
            Guidance(
                headlineRes = R.string.dom_missed_head_inj_window,
                steps = listOf(
                    // 这两条里「48 小时」是常量、时间跨度是变量，故参数顺序与 span 的默认相反
                    span(
                        minutesLate,
                        R.string.dom_missed_inj_window_1_min,
                        R.string.dom_missed_inj_window_1_hour,
                        extra = listOf(INJECTION_WINDOW_HOURS.toInt()),
                    ),
                    ResText(R.string.dom_missed_inj_window_2),
                    ResText(
                        if (immunosuppressant) R.string.dom_missed_inj_window_3_immuno
                        else R.string.dom_missed_inj_window_3_other
                    ),
                ),
                contactDoctor = immunosuppressant,
            )
        } else {
            Guidance(
                headlineRes = R.string.dom_missed_head_inj_overdue,
                steps = listOf(
                    span(
                        minutesLate,
                        R.string.dom_missed_inj_overdue_1_min,
                        R.string.dom_missed_inj_overdue_1_hour,
                        extra = listOf(INJECTION_WINDOW_HOURS.toInt()),
                        extraFirst = true,
                    ),
                    ResText(R.string.dom_missed_inj_overdue_2),
                    ResText(R.string.dom_missed_inj_overdue_3),
                    ResText(
                        if (immunosuppressant) R.string.dom_missed_inj_overdue_4_immuno
                        else R.string.dom_missed_inj_overdue_4_other
                    ),
                ),
                contactDoctor = true,
            )
        }
    }

    /**
     * 时间跨度资源二选一：`minRes` 吃分钟数（`%1$d`），`hourRes` 吃小时串（`%1$s`）。
     *
     * @param extra      该条目里除时间跨度之外还要填的参数（如补注窗口的 48 小时）
     * @param extraFirst true = [extra] 排在时间跨度之前（「已超过 48 小时（约 20 分钟）」的语序）
     */
    private fun span(
        minutesLate: Long,
        @StringRes minRes: Int,
        @StringRes hourRes: Int,
        extra: List<Any> = emptyList(),
        extraFirst: Boolean = false,
    ): ResText {
        val useHours = minutesLate / 60L >= 1L
        val value: Any = if (useHours) hoursText(minutesLate) else minutesLate.toInt()
        return ResText(
            res = if (useHours) hourRes else minRes,
            args = if (extraFirst) extra + value else listOf(value) + extra,
        )
    }

    /** 人话化的时间跨度：< 1 小时报分钟，否则报小时（保留一位小数）。 */
    private fun hoursText(minutesLate: Long): String = "%.1f".format(minutesLate / 60.0)

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
