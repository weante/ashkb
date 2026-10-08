package com.ashkb.app.domain

import android.content.Context
import com.ashkb.app.data.entity.MedFrequency
import com.ashkb.app.data.entity.Medication
import com.ashkb.app.data.repo.ReminderConfigRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * v1.2.4 周期类频次单测。
 *
 * 维护者报了两件事，这里各钉一组断言：
 *  1. 「自定义周期没法自定义日期」——口服下周期判定根本不生效（`isInjectionDay` 只在注射分支里被调用），
 *     于是选了自定义周期等于选了每日。
 *  2. 「增加每月 1 次」——每月一次是**日历**概念，用 30 天取模会逐月漂移。
 *
 * 另外钉住两条**不许改坏的旧口径**：注射 + 每日不出卡、口服 + 每两周仍按每日。
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "zh-rCN")
class ScheduleCalcCycleTest {

    private val ctx: Context get() = RuntimeEnvironment.getApplication()

    private val created = "2026-01-01T00:00:00"

    private fun med(
        route: String = "oral",
        frequency: String = "DAILY",
        takeTimes: String? = """["08:00"]""",
        startDate: String = "2026-08-03",
        injCycleDays: Int? = null,
    ) = Medication(
        id = "med-cycle-test", name = "测试药", nameKey = "test",
        medClass = "OTHER", route = route, dose = "1 片", frequency = frequency,
        takeTimes = takeTimes, startDate = startDate, injCycleDays = injCycleDays,
        createdAt = created, updatedAt = created,
    )

    private fun d(s: String) = LocalDate.parse(s)

    // ======================= 口服 + 自定义周期 =======================

    @Test
    fun `口服 自定义周期 只在周期日出发卡`() {
        val m = med(route = "oral", frequency = "CUSTOM", injCycleDays = 7, startDate = "2026-08-03")

        assertTrue("锚点当日应出卡", ScheduleCalc.slotsFor(m, d("2026-08-03")).isNotEmpty())
        assertTrue("锚点 + 7 天应出卡", ScheduleCalc.slotsFor(m, d("2026-08-10")).isNotEmpty())
        assertTrue(
            "锚点 + 1 天不该出卡（改前这里会出卡，就是维护者说的「没法自定义日期」）",
            ScheduleCalc.slotsFor(m, d("2026-08-04")).isEmpty(),
        )
    }

    @Test
    fun `口服 自定义周期 未填周期天数时不出卡`() {
        // injCycleDays 为 null 时 isInjectionDay 直接 false——宁可不出卡也不要天天出卡
        val m = med(route = "oral", frequency = "CUSTOM", injCycleDays = null, startDate = "2026-08-03")
        assertTrue(ScheduleCalc.slotsFor(m, d("2026-08-03")).isEmpty())
        assertTrue(ScheduleCalc.slotsFor(m, d("2026-08-04")).isEmpty())
    }

    // ======================= 每月一次 =======================

    @Test
    fun `每月一次 按日历日号数出卡`() {
        val m = med(route = "oral", frequency = "MONTHLY", startDate = "2026-01-31")

        assertTrue("锚点当月", ScheduleCalc.slotsFor(m, d("2026-01-31")).isNotEmpty())
        assertTrue("三月 31 日", ScheduleCalc.slotsFor(m, d("2026-03-31")).isNotEmpty())
        assertTrue("二月没有 31 日，取该月最后一天（28 日）", ScheduleCalc.slotsFor(m, d("2026-02-28")).isNotEmpty())
        assertTrue("二月 27 日不是", ScheduleCalc.slotsFor(m, d("2026-02-27")).isEmpty())
        assertTrue("三月 30 日不是", ScheduleCalc.slotsFor(m, d("2026-03-30")).isEmpty())
    }

    @Test
    fun `每月一次 锚点之前不出卡`() {
        val m = med(route = "oral", frequency = "MONTHLY", startDate = "2026-03-15")
        assertTrue(ScheduleCalc.slotsFor(m, d("2026-02-15")).isEmpty())
        assertTrue(ScheduleCalc.slotsFor(m, d("2026-03-15")).isNotEmpty())
    }

    @Test
    fun `每月一次 不看周期天数`() {
        // 每月一次按日历日号数，injCycleDays 填什么都不该改变判定
        val a = med(route = "oral", frequency = "MONTHLY", startDate = "2026-05-10", injCycleDays = null)
        val b = med(route = "oral", frequency = "MONTHLY", startDate = "2026-05-10", injCycleDays = 14)
        assertEquals(
            ScheduleCalc.slotsFor(a, d("2026-06-10")).isNotEmpty(),
            ScheduleCalc.slotsFor(b, d("2026-06-10")).isNotEmpty(),
        )
        assertTrue(ScheduleCalc.slotsFor(b, d("2026-06-10")).isNotEmpty())
    }

    // ======================= 不许改坏的旧口径 =======================

    @Test
    fun `注射 每日 仍不出卡`() {
        val m = med(route = "injection", frequency = "DAILY")
        assertTrue(ScheduleCalc.slotsFor(m, d("2026-08-03")).isEmpty())
        assertTrue(ScheduleCalc.slotsFor(m, d("2026-08-04")).isEmpty())
    }

    @Test
    fun `口服 每两周 仍按每日出卡`() {
        // Q2W 保持旧口径：只有注射按周期出卡
        val m = med(route = "oral", frequency = "Q2W", injCycleDays = 14, startDate = "2026-08-03")
        assertTrue(ScheduleCalc.slotsFor(m, d("2026-08-03")).isNotEmpty())
        assertTrue(ScheduleCalc.slotsFor(m, d("2026-08-04")).isNotEmpty())
    }

    @Test
    fun `注射 每月一次 只在到日子那天出卡`() {
        val m = med(route = "injection", frequency = "MONTHLY", startDate = "2026-01-05")
        assertTrue(ScheduleCalc.slotsFor(m, d("2026-01-05")).isNotEmpty())
        assertTrue(ScheduleCalc.slotsFor(m, d("2026-02-05")).isNotEmpty())
        assertTrue(ScheduleCalc.slotsFor(m, d("2026-02-06")).isEmpty())
        assertEquals(
            "注射槽位 key 固定为 inj",
            "inj",
            ScheduleCalc.slotsFor(m, d("2026-02-05")).first().key,
        )
    }

    // ======================= 频次枚举 =======================

    @Test
    fun `每 8 小时 从选择列表隐去但仍可解析`() {
        assertTrue("Q8H 必须还在枚举里，否则历史数据会静默退回每日", MedFrequency.Q8H.hidden)
        assertEquals(
            "已落库的 Q8H 仍要解析成 Q8H（不是 DAILY）",
            MedFrequency.Q8H,
            MedFrequency.fromKey("Q8H"),
        )
        val visible = MedFrequency.entries.filter { !it.hidden }
        assertFalse("选择列表里不该再出现它", visible.contains(MedFrequency.Q8H))
        assertTrue("每月一次必须可选", visible.contains(MedFrequency.MONTHLY))
    }

    @Test
    fun `频次选择项文案已去掉括号说明`() {
        MedFrequency.entries.filter { !it.hidden }.forEach { f ->
            // v1.2.5（i18n）：文案改成字符串资源，先解析再查括号（断言的事实不变）
            val label = ctx.getString(f.labelRes)
            assertFalse(
                "「$label」里不该再有括号提示（v1.2.4 要求删掉（）内容）",
                label.contains('（') || label.contains('('),
            )
        }
    }

    // ======================= BASDAI 评估间隔 =======================

    @Test
    fun `BASDAI 周期只剩 每日 每周 每月 三档`() {
        assertEquals(listOf(1L, 7L, 30L), ReminderConfigRepository.CYCLE_CHOICES)
        assertTrue(
            "默认值必须在候选清单里，否则设置页一个单选都不会被选中",
            ReminderConfigRepository.DEFAULT_BASDAI_CYCLE in ReminderConfigRepository.CYCLE_CHOICES,
        )
        assertEquals(30L, ReminderConfigRepository.DEFAULT_BASDAI_CYCLE)
    }
}
