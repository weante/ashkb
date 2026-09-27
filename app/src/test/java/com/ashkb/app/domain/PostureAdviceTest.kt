package com.ashkb.app.domain

import com.ashkb.app.data.entity.KbEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.0.70 C8c：姿势 / 睡姿建议回归。
 *
 * 本对象只是 `kb_seed_edu.json` 的 `edu-005` 条目的展示位拆分，
 * 测试锁住「条目 id 一致」「要点非空且不重复」「编排顺序稳定」三件事。
 */
class PostureAdviceTest {

    private fun card(id: String, grade: String) = ExerciseEngine.ExerciseCard(
        entry = KbEntry(
            id = id, category = "exercise", title = "动作", summary = "",
            severityLevel = "low", applicableScene = "", sourceName = "测试",
            sourceUrl = "", sourceTier = "S4", adaptedAt = "2026-01-01",
            reviewDue = "2027-01-01", version = 1, payload = "{}",
        ),
        verdict = "allow", hint = "", grade = grade,
        listType = "red", movements = emptyList(), dose = null,
    )

    @Test
    fun `条目 id 与知识库 edu-005 一致`() {
        assertEquals("edu-005", PostureAdvice.KB_POSTURE)
    }

    @Test
    fun `日常姿势与睡姿要点都非空`() {
        assertTrue(PostureAdvice.DAILY.isNotEmpty())
        assertTrue(PostureAdvice.SLEEP.isNotEmpty())
    }

    @Test
    fun `ALL 是 DAILY 与 SLEEP 的稳定拼接`() {
        assertEquals(PostureAdvice.DAILY + PostureAdvice.SLEEP, PostureAdvice.ALL)
        assertEquals(PostureAdvice.DAILY.size + PostureAdvice.SLEEP.size, PostureAdvice.ALL.size)
    }

    @Test
    fun `全部要点无重复`() {
        assertEquals(PostureAdvice.ALL.size, PostureAdvice.ALL.toSet().size)
    }

    @Test
    fun `睡姿要点明确包含避免俯卧`() {
        assertTrue(PostureAdvice.SLEEP.any { it.contains("俯卧") })
    }

    @Test
    fun `处方非空时展示提示块`() {
        assertTrue(PostureAdvice.shouldShow(listOf(card("exb-001", "L1"))))
    }

    @Test
    fun `处方为空时不展示提示块`() {
        assertFalse(PostureAdvice.shouldShow(emptyList()))
    }

    @Test
    fun `睡眠时长登记后置顶 edu-005`() {
        val l = Lifestyle(sleepHours = 6)
        assertTrue(PostureAdvice.KB_POSTURE in l.pinnedKbIds())
    }

    @Test
    fun `未登记睡眠时不置顶 edu-005`() {
        val l = Lifestyle(smoking = Lifestyle.SMOKING_CURRENT)
        assertFalse(PostureAdvice.KB_POSTURE in l.pinnedKbIds())
        assertTrue(Lifestyle.KB_SMOKING in l.pinnedKbIds())
    }
}
