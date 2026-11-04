package com.ashkb.app.ui.checkup

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * v1.2.15（外部 UI 审查 P0-1）：AI 导入的两个预览**必须按当前 kind 收窄**。
 *
 * ### 缺陷形态（为什么不是「样式问题」）
 * `AiImportDraftStore` 里 `labParsed` / `imagingParsed` 是**两个互相独立**的常驻标志，
 * 谁都不会清对方；而 sheet 里的 `pasted` 只是「当前 kind 的草稿」。旧写法
 * `remember(pasted, labParsed) { if (labParsed) parseLab(pasted) … }` 只判自己的标志、
 * 不看 kind，于是「先在化验入口解析过、再打开影像入口」会把**影像文本当化验单解析**：
 * 预览驴唇不对马嘴、保存按钮因此变可用，点下去要么写出错误数据、要么两个 import 一起落库。
 *
 * ### 为什么是静态断言
 * 这段代码在一个 `@Composable` 里，判断结果取决于 VM 里两份常驻状态与 Compose 的重组时机，
 * JVM 单测无法构造「另一个 kind 的 parsed 标志仍为 true 却切换了入口」这一现场
 * （需要整个 sheet + ViewModel + 重组循环）。能钉死的、也最容易在重构中丢掉的是：
 * 两个 `remember` 的**键里必须有 kind**、**体内必须有 kind 判断**。
 */
class AiImportKindGuardTest {

    private val src = readSource("app/src/main/java/com/ashkb/app/ui/checkup/CheckupLabImaging.kt")

    @Test
    fun `化验预览必须同时按 kind 与 labParsed 收窄`() {
        val body = rememberBlock("val labImport: LabImport? = remember(")
        assertTrue(
            "remember 的键里必须有 kind，否则切换入口时 remember 不会重算：$body",
            body.substringBefore(")").contains("kind"),
        )
        assertTrue(
            "体内必须有 `kind == ImportKind.LAB` 判断，否则影像文本会被当成化验单解析：$body",
            body.contains("kind == ImportKind.LAB"),
        )
    }

    @Test
    fun `影像预览必须同时按 kind 与 imagingParsed 收窄`() {
        val body = rememberBlock("val imagingImport: ImagingImport? = remember(")
        assertTrue(body.substringBefore(")").contains("kind"))
        assertTrue(body.contains("kind == ImportKind.IMAGING"))
    }

    @Test
    fun `两个预览的收窄条件互斥，不可能同时非空`() {
        // 一旦两条判断写成同一个 kind（或有人把判断删掉只留标志），保存按钮就会同时
        // 触发 importLabReport 与 importImagingReport——两个 import 一起落库正是 P0-1 的最坏形态。
        val lab = rememberBlock("val labImport: LabImport? = remember(")
        val imaging = rememberBlock("val imagingImport: ImagingImport? = remember(")
        assertTrue(lab.contains("ImportKind.LAB") && !lab.contains("ImportKind.IMAGING"))
        assertTrue(imaging.contains("ImportKind.IMAGING") && !imaging.contains("ImportKind.LAB"))
    }

    /** 从 `remember(` 起截到与之配对的 `}`（块体本身只含一层 if/else，够用）。 */
    private fun rememberBlock(signature: String): String {
        val start = src.indexOf(signature)
        assertTrue("找不到 $signature —— 变量被改名了？本守卫需要同步更新", start >= 0)
        val open = src.indexOf('{', start)
        var depth = 0
        var i = open
        while (i < src.length) {
            when (src[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return src.substring(start, i + 1)
                }
            }
            i++
        }
        return src.substring(start)
    }

    private fun readSource(relative: String): String {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            val candidate = File(dir, relative)
            if (candidate.isFile) return candidate.readText()
            dir = dir.parentFile
        }
        return File("../$relative").readText()
    }
}
