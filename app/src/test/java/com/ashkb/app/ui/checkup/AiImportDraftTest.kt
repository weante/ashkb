package com.ashkb.app.ui.checkup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.2.11（缺陷工单 B08）：**AI 导入草稿不得因转屏 / sheet 重开而丢**。
 *
 * ## 守的是什么
 *
 * 旧实现（`CheckupLabImaging.kt` 的 `AiImportSheet`）把粘贴文本放在普通 `remember` 里：
 * 用户粘好一份几十行的化验单、点完解析，**转个屏就全没了**。现在草稿移进常驻
 * [CheckupViewModel]（状态机抽成 [AiImportDraftStore]），本类直接测那个状态机。
 *
 * 三条不变量（工单验收点逐条对应）：
 * 1. **超限拒收且保留原草稿**——静默截断会解析出缺行的化验单，用户看不出少了什么。
 * 2. **草稿一变就作废旧预览**——否则改了几个字却还挂着上一次的预览，用户以为改动已生效。
 * 3. **入库即清草稿**——重复导入也算「已在库里」，留草稿会让用户再点一次保存，真的重复写。
 *
 * ## 为什么测状态机而不实例化 ViewModel
 *
 * `CheckupViewModel` 的构造参数里有 `AshkbApplication`；在 Robolectric 里造它要跑完整
 * `onCreate`（KB 种子导入 + 全量闹钟重排），既慢又有副作用，而草稿行为与 Android 无关。
 * 「VM 真的把这些方法接上了」由本类末尾的源码守卫断言（`AiImportDraftWiring`）。
 *
 * 顺带锁住一个曾经踩过的坑：`ImportKind` 是 `internal`，VM 的公开方法若以它作参数，
 * Kotlin 会报 `Function 'public' exposes its 'internal' parameter type`（编译期直接失败）。
 */
class AiImportDraftTest {

    private fun store() = AiImportDraftStore()

    // ---- 不变量 1：超限拒收 ----

    @Test
    fun `超限写入被整体拒收且保留原草稿`() {
        val s = store()
        assertTrue("正常报告单（几 KB）必须能收下", s.setDraft(ImportKind.LAB, "血常规\n白细胞 6.2 10^9/L"))

        val tooLong = "x".repeat(AiImportDraft.MAX_CHARS + 1)
        assertFalse(
            "超过上限必须返回 false，调用方据此提示用户（而不是静默截断）",
            s.setDraft(ImportKind.LAB, tooLong),
        )
        assertEquals(
            "被拒的那一次不得污染草稿——用户原来粘好的内容要原样还在",
            "血常规\n白细胞 6.2 10^9/L",
            s.labDraft.value,
        )
    }

    @Test
    fun `恰好等于上限仍被收下`() {
        val s = store()
        val exactly = "x".repeat(AiImportDraft.MAX_CHARS)
        assertTrue("边界值本身合法（判据是 > 而不是 >=）", s.setDraft(ImportKind.LAB, exactly))
        assertEquals(AiImportDraft.MAX_CHARS, s.labDraft.value.length)
    }

    @Test
    fun `上限足够容纳真实报告单`() {
        assertTrue(
            "上限是兜住异常输入用的，不该成为正常使用的门槛：一份影像报告 1–3 KB、" +
                "整页生化检验单约 5 KB，上限必须远大于此",
            AiImportDraft.MAX_CHARS >= 100_000,
        )
    }

    // ---- 不变量 2：草稿一变就作废旧预览 ----

    @Test
    fun `解析后再编辑草稿会作废预览标记`() {
        val s = store()
        s.setDraft(ImportKind.LAB, "血常规")
        s.markParsed(ImportKind.LAB)
        assertTrue("点过解析后标记应为 true（转屏据此重建预览）", s.labParsed.value)

        s.setDraft(ImportKind.LAB, "血常规 改")
        assertFalse(
            "改了文本还挂着旧预览会误导用户以为改动已生效",
            s.labParsed.value,
        )
    }

    @Test
    fun `解析标记不因无关入口的写入而串台`() {
        val s = store()
        s.setDraft(ImportKind.LAB, "血常规")
        s.markParsed(ImportKind.LAB)
        s.setDraft(ImportKind.IMAGING, "胸部 CT")
        assertTrue("写影像草稿不得影响化验的解析标记", s.labParsed.value)
        assertFalse(s.imagingParsed.value)
    }

    // ---- 不变量 3：入库即清草稿 ----

    @Test
    fun `入库后清空草稿同时清掉解析标记`() {
        val s = store()
        s.setDraft(ImportKind.LAB, "血常规")
        s.markParsed(ImportKind.LAB)
        s.clearDraft(ImportKind.LAB)
        assertEquals("入库后草稿必须清空，否则用户以为还有未保存内容、再点一次就重复写了", "", s.labDraft.value)
        assertFalse(s.labParsed.value)
    }

    @Test
    fun `清一个入口不影响另一个入口`() {
        val s = store()
        s.setDraft(ImportKind.LAB, "血常规")
        s.setDraft(ImportKind.IMAGING, "胸部 CT")
        s.clearDraft(ImportKind.LAB)
        assertEquals("", s.labDraft.value)
        assertEquals("两个入口是两份独立草稿，清一个不该动另一个", "胸部 CT", s.imagingDraft.value)
    }

    @Test
    fun `两个入口的草稿互不覆盖`() {
        val s = store()
        s.setDraft(ImportKind.LAB, "血常规")
        s.setDraft(ImportKind.IMAGING, "胸部 CT")
        assertEquals(
            "用户从「化验」切到「影像」时，刚粘好的化验单不能被冲掉",
            "血常规",
            s.labDraft.value,
        )
    }

    @Test
    fun `初值是空草稿且未解析`() {
        val s = store()
        assertEquals("", s.labDraft.value)
        assertEquals("", s.imagingDraft.value)
        assertFalse(s.labParsed.value)
        assertFalse(s.imagingParsed.value)
        assertTrue("空草稿按定义就是 isBlank", AiImportDraft("").isBlank)
    }

    // ---- 接线守卫：VM 真的用上了状态机，且入库路径都清了草稿 ----

    @Test
    fun `导入完成后两条路径都清掉对应草稿`() {
        val source = readSource("app/src/main/java/com/ashkb/app/ui/checkup/CheckupViewModel.kt")
        val lab = functionBody(source, "fun importLabReport(")
        val imaging = functionBody(source, "fun importImagingReport(")
        assertTrue("importLabReport 没取到（守卫失焦）", lab.isNotBlank())
        assertTrue("importImagingReport 没取到（守卫失焦）", imaging.isNotBlank())
        assertTrue(
            "化验入库后必须清化验草稿——重复导入也算「已在库里」，留草稿会让用户再点一次保存",
            lab.contains("clearDraft(ImportKind.LAB)"),
        )
        assertTrue(
            "影像入库后必须清影像草稿，理由同上",
            imaging.contains("clearDraft(ImportKind.IMAGING)"),
        )
    }

    @Test
    fun `VM 的草稿入口是 internal 且委托给状态机`() {
        val source = readSource("app/src/main/java/com/ashkb/app/ui/checkup/CheckupViewModel.kt")
        assertTrue(
            "状态机必须真的被 VM 持有（否则草稿又回到 sheet 的 remember 里了）",
            source.contains("private val drafts = AiImportDraftStore()"),
        )
        for (fn in listOf("setDraft", "markParsed", "clearDraft")) {
            assertTrue(
                "`$fn` 必须是 internal：`ImportKind` 是 internal 类型，公开方法会编译失败" +
                    "（Function 'public' exposes its 'internal' parameter type）",
                source.contains("internal fun $fn(kind: ImportKind"),
            )
        }
    }

    @Test
    fun `sheet 里不再有装草稿的普通 remember`() {
        val source = readSource("app/src/main/java/com/ashkb/app/ui/checkup/CheckupLabImaging.kt")
        val sheet = functionBody(source, "internal fun AiImportSheet(")
        assertTrue("AiImportSheet 没取到（守卫失焦）", sheet.isNotBlank())
        assertTrue(
            "草稿必须来自 VM 的 StateFlow（转屏才不丢）",
            sheet.contains("vm.labDraft") && sheet.contains("vm.imagingDraft"),
        )
        assertFalse(
            "不得再用 `var pasted by remember` 之类的本地状态存草稿——B08 的原始缺陷就是它",
            Regex("""var\s+pasted\s+by\s+remember""").containsMatchIn(sheet),
        )
        assertFalse(
            "也不得把解析结果塞回本地 mutableStateOf（预览应由草稿重算）",
            Regex("""var\s+(lab|imaging)Import\s+by\s+remember""").containsMatchIn(sheet),
        )
        assertTrue(
            "超限时必须提示用户，而不是静默丢弃",
            sheet.contains("ai_import_draft_too_long"),
        )
    }

    // ---- helpers ----

    /** 取某个函数声明处开始、到下一个「同类缩进」的函数结束为止的源码片段（够用即可，不求精确解析）。 */
    private fun functionBody(source: String, header: String): String {
        val start = source.indexOf(header)
        if (start < 0) return ""
        val next = source.indexOf("\n    fun ", start + header.length)
        val nextInternal = source.indexOf("\n    internal fun ", start + header.length)
        val candidates = listOfNotNull(next, nextInternal).filter { it > 0 }
        val end = candidates.minOrNull() ?: source.length
        return source.substring(start, end)
    }

    private fun readSource(relative: String): String {
        var dir: java.io.File? = java.io.File(".").absoluteFile
        while (dir != null) {
            val candidate = java.io.File(dir, relative)
            if (candidate.isFile) return candidate.readText()
            if (java.io.File(dir, "settings.gradle.kts").isFile) break
            dir = dir.parentFile
        }
        return java.io.File("../$relative").absoluteFile.readText()
    }
}
