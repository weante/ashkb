package com.ashkb.app.domain

import com.ashkb.app.data.entity.CheckupItem
import com.ashkb.app.data.entity.CheckupRecord
import com.ashkb.app.data.entity.CheckupType
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 复诊前准备清单（v1.0.33，规划缺口 C4）。
 *
 * 规划 M6 要求「复诊前准备清单（自动打包 + 空腹等抽血准备提示）」，
 * 此前以「复诊报告 PDF」替代了清单，没有任何准备提示——用户常忘记空腹、忘带既往单据。
 *
 * 纯函数、无 Android 依赖，可单测。
 */
object CheckupPrep {

    data class Plan(
        /** 下次复诊日（ISO），无未来计划时为 null */
        val nextDate: String?,
        /** 距今天数（负 = 已过；null = 无计划） */
        val daysLeft: Long?,
        /** 是否需空腹（含化验类项目） */
        val fasting: Boolean,
        /** 需要做的检查项目名（来自在用复诊项目） */
        val checkItems: List<String>,
        /** 需要携带的材料（固定项 + 条件项） */
        val bringItems: List<String>,
    ) {
        val hasPlan: Boolean get() = nextDate != null
        val isSoon: Boolean get() = daysLeft != null && daysLeft in 0..3
    }

    /** 固定携带项——任何复诊都建议带。 */
    private val BASE_BRING = listOf(
        "既往化验单 / 影像报告（纸质或本应用附件归档）",
        "当前用药清单（本应用可导出紧急卡 / 复诊报告 PDF）",
        "医保卡 / 就诊卡",
    )

    /**
     * 生成准备计划。
     *
     * @param items   复诊项目（取 isActive）
     * @param records 复诊记录（用于推算下次复诊日——取最近一条未来的 next_date）
     * @param today   今天
     */
    fun plan(items: List<CheckupItem>, records: List<CheckupRecord>, today: LocalDate): Plan {
        val todayStr = today.toString()
        val nextDate = records
            .mapNotNull { it.nextDate?.takeIf { d -> d.isNotBlank() } }
            .filter { it >= todayStr }
            .minOrNull()

        val daysLeft = nextDate?.let {
            runCatching { ChronoUnit.DAYS.between(today, LocalDate.parse(it)) }.getOrNull()
        }

        val active = items.filter { it.isActive }
        val hasLab = active.any { CheckupType.fromKey(it.checkType) == CheckupType.LAB }
        // 眼科 / 牙科等无需空腹；只有抽血化验类需要
        val fasting = hasLab

        val bring = buildList {
            addAll(BASE_BRING)
            if (hasLab) add("如当日需抽血：请空腹（前一晚起禁食 8–12 小时，可少量饮水；具体以医院要求为准）")
            if (active.any { CheckupType.fromKey(it.checkType) == CheckupType.IMAGE }) {
                add("如当日需影像检查：去除金属饰品，携带既往片子以便对比")
            }
        }

        return Plan(
            nextDate = nextDate,
            daysLeft = daysLeft,
            fasting = fasting,
            checkItems = active.map { it.name },
            bringItems = bring,
        )
    }
}
