package com.ashkb.app.domain

import com.ashkb.app.data.entity.KbEntry
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.2.16：知识库「个人条目」的 domain 纯函数。
 *
 * 这一层承担的是**所有**校验与编解码：UI 只负责把 [KbUserEntry.Error] 翻成人话，
 * 真正决定「这条能不能进库」的是这里。故用例集中在四件事：
 *  · 校验（哪些算不合规、边界值在哪一格翻转）；
 *  · payload 的写读往返（详情页靠它渲染，写错一个 key 就是一条点开空白）；
 *  · 落库行（[KbUserEntry.toEntry]）的身份与版本语义（`usr-` 前缀、`USR` 层级、
 *    永不复核、version 自增、个人备注必须原样带回）；
 *  · 导出 / 导入（JSON 形状、坏文件的各种拒绝理由、坏条目只跳过不整体失败）。
 */
class KbUserEntryTest {

    private fun draft(
        id: String? = null,
        category: String = "interaction",
        title: String = "晨僵热敷",
        summary: String = "起床后先热敷 10 分钟",
        severity: String = "low",
        content: String = "用热毛巾敷腰背，水温以不烫手为准。",
        actions: List<String> = listOf("水温不超过 45 度"),
        evidence: List<String> = emptyList(),
        sourceName: String = "",
        sourceUrl: String = "",
    ) = KbUserEntry.Draft(
        id = id,
        category = category,
        title = title,
        summary = summary,
        severity = severity,
        content = content,
        actions = actions,
        evidence = evidence,
        sourceName = sourceName,
        sourceUrl = sourceUrl,
    )

    // ---- 身份 ----

    @Test
    fun `only the usr prefix counts as a user entry`() {
        assertTrue(KbUserEntry.isUserEntry("usr-abc"))
        assertFalse(KbUserEntry.isUserEntry("itx-001"))
        assertFalse(KbUserEntry.isUserEntry("edu-th-001"))
        assertFalse(KbUserEntry.isUserEntry(""))
    }

    // ---- normalize ----

    @Test
    fun `normalize trims fields and drops blank lines`() {
        val n = KbUserEntry.normalize(
            draft(
                title = "  晨僵热敷  ",
                summary = "  摘要 ",
                content = "  正文 ",
                actions = listOf("  一  ", "   ", ""),
                evidence = listOf(" ", " 二 "),
                sourceName = "  医生口述 ",
                sourceUrl = "  https://example.org/a  ",
            ),
        )
        assertEquals("晨僵热敷", n.title)
        assertEquals("摘要", n.summary)
        assertEquals("正文", n.content)
        assertEquals(listOf("一"), n.actions)
        assertEquals(listOf("二"), n.evidence)
        assertEquals("医生口述", n.sourceName)
        assertEquals("https://example.org/a", n.sourceUrl)
    }

    // ---- validate ----

    @Test
    fun `a well formed draft has no errors`() {
        assertTrue(KbUserEntry.validate(draft()).isEmpty())
    }

    @Test
    fun `title and content are required`() {
        val errors = KbUserEntry.validate(draft(title = "", content = ""))
        assertTrue(errors.contains(KbUserEntry.Error.TITLE_REQUIRED))
        assertTrue(errors.contains(KbUserEntry.Error.CONTENT_REQUIRED))
    }

    @Test
    fun `length limits flip exactly at the boundary`() {
        // 恰好等于上限：通过；多一个字：报错。
        assertFalse(
            KbUserEntry.validate(draft(title = "字".repeat(KbUserEntry.MAX_TITLE)))
                .contains(KbUserEntry.Error.TITLE_TOO_LONG),
        )
        assertTrue(
            KbUserEntry.validate(draft(title = "字".repeat(KbUserEntry.MAX_TITLE + 1)))
                .contains(KbUserEntry.Error.TITLE_TOO_LONG),
        )
        assertFalse(
            KbUserEntry.validate(draft(summary = "字".repeat(KbUserEntry.MAX_SUMMARY)))
                .contains(KbUserEntry.Error.SUMMARY_TOO_LONG),
        )
        assertTrue(
            KbUserEntry.validate(draft(summary = "字".repeat(KbUserEntry.MAX_SUMMARY + 1)))
                .contains(KbUserEntry.Error.SUMMARY_TOO_LONG),
        )
        assertFalse(
            KbUserEntry.validate(draft(content = "字".repeat(KbUserEntry.MAX_CONTENT)))
                .contains(KbUserEntry.Error.CONTENT_TOO_LONG),
        )
        assertTrue(
            KbUserEntry.validate(draft(content = "字".repeat(KbUserEntry.MAX_CONTENT + 1)))
                .contains(KbUserEntry.Error.CONTENT_TOO_LONG),
        )
    }

    @Test
    fun `key points and evidence share one item budget`() {
        val tenEach = List(KbUserEntry.MAX_ITEMS / 2) { "条目$it" }
        assertFalse(
            KbUserEntry.validate(draft(actions = tenEach, evidence = tenEach))
                .contains(KbUserEntry.Error.TOO_MANY_ITEMS),
        )
        assertTrue(
            KbUserEntry.validate(draft(actions = tenEach + listOf("多一条"), evidence = tenEach))
                .contains(KbUserEntry.Error.TOO_MANY_ITEMS),
        )
        assertTrue(
            KbUserEntry.validate(draft(actions = listOf("x".repeat(KbUserEntry.MAX_ITEM_LENGTH + 1))))
                .contains(KbUserEntry.Error.ITEM_TOO_LONG),
        )
    }

    @Test
    fun `unknown category and severity are rejected`() {
        val errors = KbUserEntry.validate(draft(category = "surgery", severity = "critical"))
        assertTrue(errors.contains(KbUserEntry.Error.CATEGORY_UNKNOWN))
        assertTrue(errors.contains(KbUserEntry.Error.SEVERITY_UNKNOWN))
    }

    @Test
    fun `source url must be http or https when present`() {
        assertTrue(KbUserEntry.validate(draft(sourceUrl = "https://a.example/x")).isEmpty())
        assertTrue(KbUserEntry.validate(draft(sourceUrl = "http://a.example")).isEmpty())
        assertTrue(
            KbUserEntry.validate(draft(sourceUrl = "ftp://a.example"))
                .contains(KbUserEntry.Error.SOURCE_URL_INVALID),
        )
        assertTrue(
            KbUserEntry.validate(draft(sourceUrl = "a.example/x"))
                .contains(KbUserEntry.Error.SOURCE_URL_INVALID),
        )
    }

    // ---- payload ----

    @Test
    fun `payload round trips through the detail page keys`() {
        val payload = KbUserEntry.payloadOf(draft(actions = listOf("一", "二"), evidence = listOf("文献甲")))
        val json = JSONObject(payload)
        assertEquals("用热毛巾敷腰背，水温以不烫手为准。", json.getString("content"))
        assertEquals(2, json.getJSONArray("action").length())
        assertEquals("二", json.getJSONArray("action").getString(1))
        assertEquals("文献甲", json.getJSONArray("evidence").getString(0))
        // 空数组不写：详情页的 PayloadSection 拿到长度 0 的数组等于不显示，但少一个 key 更干净
        assertFalse(JSONObject(KbUserEntry.payloadOf(draft(evidence = emptyList()))).has("evidence"))
    }

    @Test
    fun `draftOf survives a malformed payload instead of throwing`() {
        val broken = KbEntry(
            id = "usr-x",
            category = "interaction",
            title = "标题",
            summary = "摘要",
            severityLevel = "low",
            applicableScene = "",
            sourceName = "",
            sourceUrl = "",
            sourceTier = KbUserEntry.TIER,
            adaptedAt = "2026-01-01",
            reviewDue = KbUserEntry.REVIEW_DUE_NEVER,
            version = 1,
            payload = "not json at all",
        )
        val d = KbUserEntry.draftOf(broken)
        assertEquals("usr-x", d.id)
        assertEquals("标题", d.title)
        assertEquals("", d.content)
        assertTrue(d.actions.isEmpty())
    }

    // ---- toEntry ----

    @Test
    fun `toEntry stamps the user identity and bumps the version`() {
        val existing = KbEntry(
            id = "usr-1",
            category = "exercise",
            title = "旧标题",
            summary = "旧摘要",
            severityLevel = "low",
            applicableScene = "",
            sourceName = "",
            sourceUrl = "",
            sourceTier = KbUserEntry.TIER,
            adaptedAt = "2026-01-02",
            reviewDue = KbUserEntry.REVIEW_DUE_NEVER,
            version = 4,
            payload = "{}",
            searchText = "旧",
            userNote = "我的备注",
        )
        val row = KbUserEntry.toEntry(draft(id = "usr-1"), existing = existing, id = "usr-1", today = "2026-11-15")

        assertEquals("usr-1", row.id)
        assertEquals(KbUserEntry.TIER, row.sourceTier)
        assertEquals(KbUserEntry.REVIEW_DUE_NEVER, row.reviewDue)
        // 首次适配日沿用旧值，不被每次编辑改动
        assertEquals("2026-01-02", row.adaptedAt)
        assertEquals(5, row.version)
        // 个人备注是独立一层，不能被编辑正文抹掉
        assertEquals("我的备注", row.userNote)
        // search_text 必须与检索口径一致，否则新条目搜不到
        assertEquals(
            KbSearch.searchText("晨僵热敷", "起床后先热敷 10 分钟", KbUserEntry.payloadOf(draft())),
            row.searchText,
        )
    }

    @Test
    fun `toEntry starts at version one for a new entry`() {
        val row = KbUserEntry.toEntry(draft(), existing = null, id = "usr-new", today = "2026-11-15")
        assertEquals(1, row.version)
        assertEquals("2026-11-15", row.adaptedAt)
        assertTrue(row.id.startsWith(KbUserEntry.ID_PREFIX))
    }

    // ---- export ----

    private fun entryRow(id: String, title: String) = KbEntry(
        id = id,
        category = "food_drug",
        title = title,
        summary = "摘要",
        severityLevel = "medium",
        applicableScene = "",
        sourceName = "",
        sourceUrl = "",
        sourceTier = KbUserEntry.TIER,
        adaptedAt = "2026-11-01",
        reviewDue = KbUserEntry.REVIEW_DUE_NEVER,
        version = 1,
        payload = """{"content":"正文","action":["一"]}""",
        searchText = "$title\n摘要",
    )

    @Test
    fun `exportJson keeps only user entries and stays parseable`() {
        val json = KbUserEntry.exportJson(
            listOf(entryRow("usr-1", "我写的"), entryRow("itx-001", "种子条目")),
            exportedAt = "2026-11-15T10:00:00Z",
        )
        val root = JSONObject(json)
        assertEquals(KbUserEntry.EXPORT_FORMAT, root.getString("format"))
        assertEquals(KbUserEntry.EXPORT_FORMAT_VERSION, root.getInt("format_version"))
        assertEquals(1, root.getInt("count"))
        assertEquals(1, root.getJSONArray("entries").length())
        assertEquals("我写的", root.getJSONArray("entries").getJSONObject(0).getString("title"))
    }

    @Test
    fun `export then import round trips the content`() {
        val json = KbUserEntry.exportJson(listOf(entryRow("usr-1", "我写的")), exportedAt = "2026-11-15T10:00:00Z")
        val plan = KbUserEntry.parseExport(json)
        assertTrue(plan is KbUserEntry.ImportPlan.Ready)
        val ready = plan as KbUserEntry.ImportPlan.Ready
        assertEquals(1, ready.drafts.size)
        assertEquals(0, ready.skipped)
        val d = ready.drafts.first()
        assertEquals("我写的", d.title)
        assertEquals("正文", d.content)
        assertEquals(listOf("一"), d.actions)
        // 导入的是「新条目」，不带原 id（否则会覆盖同 id 的既有条目）
        assertEquals(null, d.id)
    }

    @Test
    fun `import accepts a bare array as well as an entries object`() {
        val bare = """[{"title":"甲","content":"正文"},{"title":"乙","content":"正文"}]"""
        val plan = KbUserEntry.parseExport(bare)
        assertTrue(plan is KbUserEntry.ImportPlan.Ready)
        assertEquals(2, (plan as KbUserEntry.ImportPlan.Ready).drafts.size)
    }

    @Test
    fun `import rejects malformed files with a specific reason`() {
        val badJson = KbUserEntry.parseExport("这不是 JSON")
        assertEquals(
            KbUserEntry.ImportError.BAD_JSON,
            (badJson as KbUserEntry.ImportPlan.Rejected).error,
        )

        val empty = KbUserEntry.parseExport("""{"entries":[]}""")
        assertEquals(
            KbUserEntry.ImportError.NO_ENTRIES,
            (empty as KbUserEntry.ImportPlan.Rejected).error,
        )

        val tooMany = KbUserEntry.parseExport(
            """[${(0..KbUserEntry.MAX_IMPORT_ENTRIES).joinToString(",") { """{"title":"t$it","content":"c"}""" }}]""",
        )
        assertEquals(
            KbUserEntry.ImportError.TOO_MANY,
            (tooMany as KbUserEntry.ImportPlan.Rejected).error,
        )
    }

    @Test
    fun `invalid entries are skipped instead of failing the whole import`() {
        val mixed = """
            [
              {"title":"好的","content":"正文"},
              {"title":"","content":"没有标题"},
              {"title":"没正文","content":""},
              {"title":"坏链接","content":"正文","source_url":"javascript:alert(1)"}
            ]
        """.trimIndent()
        val ready = KbUserEntry.parseExport(mixed) as KbUserEntry.ImportPlan.Ready
        assertEquals(1, ready.drafts.size)
        assertEquals("好的", ready.drafts.first().title)
        assertEquals(3, ready.skipped)
    }

    @Test
    fun `exported entries always carry a created_at stamp`() {
        val json = KbUserEntry.exportJson(listOf(entryRow("usr-1", "我写的")), exportedAt = "2026-11-15T10:00:00Z")
        val first = JSONObject(json).getJSONArray("entries").getJSONObject(0)
        assertNotNull(first.optString("created_at"))
        assertFalse(first.optString("created_at").isBlank())
    }
}
