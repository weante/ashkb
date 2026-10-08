package com.ashkb.app.domain

import android.content.Context
import com.ashkb.app.data.entity.KbEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * v1.0.69 C8b：晨僵时长驱动起床热身序列回归。
 *
 * i18n（v1.2.6）：`Sequence.headline` 是 `ResText`、`note` 是 `@StringRes Int`，
 * 断言**措辞**的用例改为读中文资源落地后的文本。`@Config(qualifiers = "zh-rCN")` 不可省
 * （`values-en` 存在，Robolectric 默认走 en-rUS，英文里没有「炎症活动」）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "zh-rCN")
class MorningWarmupTest {

    private val ctx: Context get() = RuntimeEnvironment.getApplication()

    private fun headline(s: MorningWarmup.Sequence): String =
        ctx.getString(s.headline.res, *s.headline.args.toTypedArray())

    private fun note(s: MorningWarmup.Sequence): String = ctx.getString(s.noteRes)

    private fun entry(id: String, title: String) = KbEntry(
        id = id, category = "exercise", title = title, summary = "",
        severityLevel = "low", applicableScene = "", sourceName = "测试",
        sourceUrl = "", sourceTier = "S4", adaptedAt = "2026-01-01",
        reviewDue = "2027-01-01", version = 1, payload = "{}",
    )

    private fun card(id: String, title: String, grade: String) =
        ExerciseEngine.ExerciseCard(
            entry = entry(id, title), verdict = "allow", hintParts = emptyList(), grade = grade,
            listType = "red", movements = emptyList(), dose = null,
        )

    private val l1 = card("exb-001", "猫牛式", "L1")
    private val l2 = card("exb-002", "桥式", "L2")
    private val l3 = card("exb-003", "平板支撑", "L3")
    private val plan = listOf(l1, l2, l3)

    @Test
    fun `未记录晨僵 不提示`() {
        assertNull(MorningWarmup.build(null, plan))
    }

    @Test
    fun `低于 15 分钟 不提示`() {
        assertNull(MorningWarmup.build(0, plan))
        assertNull(MorningWarmup.build(14, plan))
    }

    @Test
    fun `刚好 15 分钟开始提示`() {
        val s = MorningWarmup.build(15, plan)!!
        assertEquals("晨僵 15 分钟", headline(s))
        assertTrue("15 分钟档不该说炎症活动", !note(s).contains("炎症活动"))
    }

    @Test
    fun `刚好 30 分钟升级为明显延长`() {
        val s = MorningWarmup.build(30, plan)!!
        assertEquals("晨僵 30 分钟（明显延长）", headline(s))
        assertTrue(note(s).contains("炎症活动"))
        assertTrue("应提示复诊告知医生", note(s).contains("医生"))
    }

    @Test
    fun `29 与 30 是分档边界`() {
        assertTrue(headline(MorningWarmup.build(29, plan)!!).endsWith("分钟"))
        assertTrue(headline(MorningWarmup.build(30, plan)!!).endsWith("（明显延长）"))
    }

    @Test
    fun `只取当日处方里的 L1 轻柔项`() {
        val s = MorningWarmup.build(40, plan)!!
        assertEquals(listOf("猫牛式"), s.steps)
    }

    @Test
    fun `步骤封顶 4 条`() {
        val many = (1..7).map { card("exb-$it", "动作$it", "L1") }
        val s = MorningWarmup.build(40, many)!!
        assertEquals(MorningWarmup.MAX_STEPS, s.steps.size)
        assertEquals(listOf("动作1", "动作2", "动作3", "动作4"), s.steps)
    }

    @Test
    fun `处方里没有 L1 时 steps 为空但仍有解释`() {
        val s = MorningWarmup.build(40, listOf(l2, l3))!!
        assertTrue("无 L1 项时 steps 为空，UI 自行兜底文案", s.steps.isEmpty())
        assertTrue(note(s).isNotBlank())
    }

    @Test
    fun `空处方不崩`() {
        val s = MorningWarmup.build(60, emptyList())!!
        assertTrue(s.steps.isEmpty())
        assertTrue(headline(s).contains("60"))
    }
}
