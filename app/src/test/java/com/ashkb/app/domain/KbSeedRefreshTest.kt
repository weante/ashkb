package com.ashkb.app.domain

import com.ashkb.app.data.entity.KbEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.0.44（N3）：知识库种子增量刷新判定。
 *
 * 旧行为是「库里有一条就整体跳过」，导致老设备永远拿不到种子增补与修订；
 * 本组用例把「补入 / 修订 / 跳过」三种裁决与「个人备注必须保留」钉死。
 */
class KbSeedRefreshTest {

    private fun entry(
        id: String = "exc-001",
        title: String = "标题",
        summary: String = "摘要",
        payload: String = """{"a":1}""",
        version: Int = 1,
        searchText: String? = "标题摘要",
        userNote: String? = null,
    ) = KbEntry(
        id = id,
        category = "exercise",
        title = title,
        summary = summary,
        severityLevel = "low",
        applicableScene = "日常",
        sourceName = "S1",
        sourceUrl = "https://example.org",
        sourceTier = "S1",
        adaptedAt = "2026-09-01",
        reviewDue = "2027-09-01",
        version = version,
        payload = payload,
        searchText = searchText,
        userNote = userNote,
    )

    /** 全新设备（库为空）：全部进 fresh，一条都不该进 revised。 */
    @Test
    fun `empty store seeds everything as fresh`() {
        val plan = KbSeedRefresh.plan(emptyMap(), listOf(entry("a"), entry("b")))
        assertEquals(listOf("a", "b"), plan.fresh.map { it.id })
        assertTrue(plan.revised.isEmpty())
    }

    /** 内容完全一致 → 不产生任何写入（否则每次启动都白写一遍全表）。 */
    @Test
    fun `identical content produces no write`() {
        val s = entry("a")
        val plan = KbSeedRefresh.plan(mapOf("a" to s), listOf(s))
        assertTrue(plan.isEmpty)
    }

    /**
     * 核心场景：v1.0.43 改写了 exc-004 的文案，老设备必须能拿到新文案，
     * **且用户的个人备注不能被抹掉**（v10 双层结构的承诺）。
     */
    @Test
    fun `revised seed updates seed columns and keeps user note`() {
        val old = entry("a", title = "旧标题", userNote = "我吃了会胃痛")
        val new = entry("a", title = "新标题")
        val plan = KbSeedRefresh.plan(mapOf("a" to old), listOf(new))

        assertTrue(plan.fresh.isEmpty())
        assertEquals(1, plan.revised.size)
        assertEquals("新标题", plan.revised[0].title)
        assertEquals("我吃了会胃痛", plan.revised[0].userNote)
    }

    /** 只 bump `version`（内容未动）也要触发刷新。 */
    @Test
    fun `version bump alone triggers refresh`() {
        assertTrue(KbSeedRefresh.isRevised(entry("a", version = 1), entry("a", version = 2)))
    }

    /** 忘了 bump `version` 时，字段级比对是安全网。 */
    @Test
    fun `content change is detected without version bump`() {
        assertTrue(KbSeedRefresh.isRevised(entry("a"), entry("a", payload = """{"a":2}""")))
    }

    /** 旧备份恢复后 `search_text` 可能为 NULL（未回填），应顺带补齐。 */
    @Test
    fun `null search text is repaired`() {
        val old = entry("a", searchText = null)
        val new = entry("a", searchText = "标题摘要")
        val plan = KbSeedRefresh.plan(mapOf("a" to old), listOf(new))
        assertEquals(1, plan.revised.size)
    }

    /** 个人备注是用户数据，不是种子内容——种子无权据此判定「已修订」。 */
    @Test
    fun `user note alone never counts as revised`() {
        assertFalse(KbSeedRefresh.isRevised(entry("a", userNote = "备注"), entry("a")))
    }

    /** 混合场景：一条缺失、一条修订、一条未变。 */
    @Test
    fun `mixed store splits fresh revised and untouched`() {
        val existing = mapOf(
            "a" to entry("a", title = "旧A"),
            "b" to entry("b"),
        )
        val seeds = listOf(entry("a", title = "新A"), entry("b"), entry("c"))
        val plan = KbSeedRefresh.plan(existing, seeds)

        assertEquals(listOf("c"), plan.fresh.map { it.id })
        assertEquals(listOf("a"), plan.revised.map { it.id })
    }
}
