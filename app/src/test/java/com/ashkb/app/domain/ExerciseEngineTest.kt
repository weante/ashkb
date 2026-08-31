package com.ashkb.app.domain

import com.ashkb.app.data.entity.KbEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P5 运动分级矩阵单测（R27 安全核心）：
 * 红榜 grade_matrix 按分期判定、黑榜 stage 条件拦截、颈椎受累全期拦截、
 * 停用项不进当日处方、反馈判读（exc-010）。
 * 矩阵判定错误会直接把高危动作放给活动期患者——任何一条失败都不应定版。
 */
class ExerciseEngineTest {

    private fun entry(payload: String, category: String = "exercise") = KbEntry(
        id = "exb-test", category = category, title = "测试条目", summary = "测试",
        severityLevel = "medium", applicableScene = "self_use",
        sourceName = "S1", sourceUrl = "https://example.com", sourceTier = "S1",
        adaptedAt = "2026-08-30", reviewDue = "2027-08-30", version = 1, payload = payload,
    )

    // ======================= 红榜矩阵 =======================

    @Test
    fun `红榜 L1 缓解期推荐`() {
        val e = entry("""{"list_type":"red","grade":"L1","dose":"每日 10 分钟",
            "grade_matrix":{"stable":"recommend","active":"allow"}}""")
        val c = ExerciseEngine.evaluate(e, diseaseStage = "stable", spineMobility = null)
        assertEquals("recommend", c.verdict)
        assertTrue(c.hint.contains("今日推荐"))
    }

    @Test
    fun `红榜 L2 活动期降级`() {
        val e = entry("""{"list_type":"red","grade":"L2","dose":"每周 3 次",
            "grade_matrix":{"stable":"recommend","active":"downgrade"}}""")
        val c = ExerciseEngine.evaluate(e, diseaseStage = "active", spineMobility = null)
        assertEquals("downgrade", c.verdict)
        assertTrue(c.hint.contains("减量"))
    }

    @Test
    fun `红榜 L3 活动期暂停`() {
        val e = entry("""{"list_type":"red","grade":"L3",
            "grade_matrix":{"stable":"allow","active":"pause"}}""")
        val c = ExerciseEngine.evaluate(e, diseaseStage = "active", spineMobility = null)
        assertEquals("pause", c.verdict)
    }

    @Test
    fun `未建档 unknown 分期按活动期保守处理`() {
        val e = entry("""{"list_type":"red","grade":"L2",
            "grade_matrix":{"stable":"recommend","active":"pause"}}""")
        assertEquals("pause", ExerciseEngine.evaluate(e, null, null).verdict)
        assertEquals("pause", ExerciseEngine.evaluate(e, "unknown", null).verdict)
    }

    @Test
    fun `矩阵缺失时红榜默认 allow`() {
        val e = entry("""{"list_type":"red","grade":"L2"}""")
        assertEquals("allow", ExerciseEngine.evaluate(e, "stable", null).verdict)
    }

    // ======================= 黑榜拦截 =======================

    @Test
    fun `黑榜 活动期命中 stage 拦截`() {
        val e = entry("""{"list_type":"black","grade":"L3","risk":"高冲击",
            "block_rule":{"stage":["active","stable"]}}""")
        assertEquals("block", ExerciseEngine.evaluate(e, "active", null).verdict)
    }

    @Test
    fun `黑榜 仅活动期条目在缓解期不拦截`() {
        // 种子真实结构：黑榜条目带 grade_matrix（stable 期显示「不建议」而非硬拦截）
        val e = entry("""{"list_type":"black","grade":"L3","risk":"高冲击",
            "grade_matrix":{"stable":"advise_against","active":"block"},
            "block_rule":{"stage":["active"]}}""")
        assertEquals("advise_against", ExerciseEngine.evaluate(e, "stable", null).verdict)
        assertEquals("block", ExerciseEngine.evaluate(e, "active", null).verdict)
    }

    @Test
    fun `黑榜无矩阵时保守默认拦截（不放行）`() {
        // 异常载荷兜底：矩阵缺失的黑榜宁可硬拦截也不放行
        val e = entry("""{"list_type":"black","grade":"L2","risk":"倒立类"}""")
        val c = ExerciseEngine.evaluate(e, "stable", null)
        assertEquals("block", c.verdict)
        assertTrue(c.hint.contains("已拦截"))
    }

    // ======================= 颈椎受累（R27 §3） =======================

    @Test
    fun `颈椎受累加 cervical_gated 条件仍拦截`() {
        val e = entry("""{"list_type":"black","risk":"深度后仰",
            "block_rule":{"stage":["active"],"cervical":true}}""")
        assertEquals("block", ExerciseEngine.evaluate(e, "active", "severe").verdict)
    }

    @Test
    fun `cervical_gated 但颈椎未受累不拦截`() {
        // 条件化拦截（exb-004/005 语义）：仅「活动期 且 颈椎受累」才硬拦
        val e = entry("""{"list_type":"black","risk":"深度后仰",
            "grade_matrix":{"stable":"advise_against","active":"advise_against"},
            "block_rule":{"stage":["active"],"cervical":true}}""")
        assertEquals("advise_against", ExerciseEngine.evaluate(e, "active", "mild").verdict)
        assertEquals("block", ExerciseEngine.evaluate(e, "active", "severe").verdict)
    }

    @Test
    fun `cervical_only 条目颈椎受累时全期拦截（不看 stage）`() {
        val e = entry("""{"list_type":"black","risk":"蛙泳换气",
            "cervical_condition":"cervical_only"}""")
        assertEquals("block", ExerciseEngine.evaluate(e, "stable", "moderate").verdict)
        assertEquals("block", ExerciseEngine.evaluate(e, "active", "severe").verdict)
    }

    @Test
    fun `cervical_only 但颈椎未受累不因颈椎拦截`() {
        val e = entry("""{"list_type":"black","risk":"蛙泳换气",
            "grade_matrix":{"stable":"advise_against","active":"block"},
            "cervical_condition":"cervical_only"}""")
        assertEquals("advise_against", ExerciseEngine.evaluate(e, "stable", null).verdict)
    }

    @Test
    fun `颈椎受累提示附加在 hint`() {
        val e = entry("""{"list_type":"black","risk":"颈椎过伸","cervical_condition":"cervical_only"}""")
        val c = ExerciseEngine.evaluate(e, "stable", "moderate")
        assertTrue(c.hint.contains("颈椎受累提示"))
    }

    @Test
    fun `spine_mobility 阈值判定 moderate 以上为受累`() {
        assertTrue(ExerciseEngine.cervicalInvolved("moderate"))
        assertTrue(ExerciseEngine.cervicalInvolved("severe"))
        assertFalse(ExerciseEngine.cervicalInvolved("mild"))
        assertFalse(ExerciseEngine.cervicalInvolved(null))
        assertFalse(ExerciseEngine.cervicalInvolved("unknown"))
    }

    @Test
    fun `拦截条目带替代方案提示`() {
        val e = entry("""{"list_type":"black","risk":"仰卧起坐",
            "block_rule":{"stage":["active","stable"]},"alternative_hint":"改为平板支撑 30 秒"}""")
        val c = ExerciseEngine.evaluate(e, "active", null)
        assertTrue(c.hint.contains("替代方案"))
        assertTrue(c.hint.contains("平板支撑"))
    }

    // ======================= 当日处方 =======================

    @Test
    fun `当日处方排除 pause 项且黑榜全进拦截区`() {
        val redOk = entry("""{"list_type":"red","grade":"L1","grade_matrix":{"stable":"recommend"}}""")
        val redPause = entry("""{"list_type":"red","grade":"L3","grade_matrix":{"stable":"pause"}}""")
        val black = entry("""{"list_type":"black","risk":"高风险"}""")
        val (plan, blocked) = ExerciseEngine.todayPlan(listOf(redOk, redPause, black), "stable", null)
        assertEquals(1, plan.size)
        assertEquals(redOk.id, plan[0].entry.id)
        assertEquals(1, blocked.size)
        assertEquals(black.id, blocked[0].entry.id)
    }

    @Test
    fun `处方按 L1 优先排序`() {
        val l3 = entry("""{"list_type":"red","grade":"L3","grade_matrix":{"stable":"allow"}}""")
        val l1 = entry("""{"list_type":"red","grade":"L1","grade_matrix":{"stable":"allow"}}""")
        val l2 = entry("""{"list_type":"red","grade":"L2","grade_matrix":{"stable":"allow"}}""")
        val (plan, _) = ExerciseEngine.todayPlan(listOf(l3, l1, l2), "stable", null)
        assertEquals(listOf("L1", "L2", "L3"), plan.map { it.grade })
    }

    @Test
    fun `非法 payload JSON 安全降级为 allow（不崩溃）`() {
        val e = entry("not-json")
        val c = ExerciseEngine.evaluate(e, "stable", null)
        assertEquals("allow", c.verdict)
    }

    // ======================= R21 反馈判读（exc-010） =======================

    @Test
    fun `疼痛加重且符合肌肉酸痛 观察不加量`() {
        val msg = ExerciseEngine.interpretFeedback("worse", null, true)
        assertTrue(msg.contains("延迟性酸痛"))
        assertFalse(msg.contains("减量"))
    }

    @Test
    fun `疼痛加重非肌肉酸痛 建议减量 20 percent`() {
        val msg = ExerciseEngine.interpretFeedback("worse", null, false)
        assertTrue(msg.contains("减量"))
        assertTrue(msg.contains("20%"))
    }

    @Test
    fun `晨僵加重提示炎症活动降 L1`() {
        val msg = ExerciseEngine.interpretFeedback(null, "worse", false)
        assertTrue(msg.contains("晨僵"))
        assertTrue(msg.contains("L1"))
    }

    @Test
    fun `反馈良好按进展原则加量`() {
        val msg = ExerciseEngine.interpretFeedback("better", null, null)
        assertTrue(msg.contains("加量"))
    }

    @Test
    fun `反馈平稳维持当前量`() {
        val msg = ExerciseEngine.interpretFeedback(null, null, null)
        assertTrue(msg.contains("维持"))
    }
}
