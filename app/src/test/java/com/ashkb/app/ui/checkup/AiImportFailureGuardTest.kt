package com.ashkb.app.ui.checkup

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * v1.2.15（外部 UI 审查 P0-2）：AI 导入失败**不许把用户原文一起弄丢**。
 *
 * ### 为什么是静态断言，不是行为测试
 * 这个缺陷的完整形态需要 `viewModelScope` 真跑起来 + 仓库层真抛异常，而
 * `CheckupViewModel` 的构造要 `AshkbApplication`、`HealthRepository`、`AttachmentRepository`、
 * `BackupRepository` 与 `DateProvider` —— JVM 单测里搭不出来（同类的 `restore()` 也是这个原因，
 * 见 `RestoreCallSiteGuardTest` 的说明）。
 *
 * 但真正会退化的不是「异常会不会被抛」，而是**两件很容易在重构中被顺手改掉的小事**：
 * 1. `catch` 分支里忘了 / 多了一句 `clearDraft`；
 * 2. 有人图省事把清理挪进 `finally`（看起来更"整洁"，实际把原文删了）。
 * 这两条都能用源码断言钉死，且比真机验证可靠——真机上要复现「导入抛异常」得先把数据库弄坏。
 */
class AiImportFailureGuardTest {

    private val vm = readSource("app/src/main/java/com/ashkb/app/ui/checkup/CheckupViewModel.kt")

    /** 两个导入入口各自的函数体（到下一个顶层 `fun` / 成员声明为止）。 */
    private fun importBody(signature: String): String {
        val start = vm.indexOf(signature)
        assertTrue("找不到 $signature —— 函数被改名了？本守卫需要同步更新", start >= 0)
        val next = vm.indexOf("\n    /**", start + signature.length)
        val nextFun = vm.indexOf("\n    fun ", start + signature.length)
        val end = listOf(next, nextFun).filter { it > start }.minOrNull() ?: vm.length
        return vm.substring(start, end)
    }

    @Test
    fun `化验导入失败时必须捕获异常，不能让 viewModelScope 崩掉进程`() {
        val body = importBody("fun importLabReport(")
        assertTrue(
            "导入前 onDismiss() 已经执行，异常抛出去就是「点保存，App 直接没了」：$body",
            body.contains("catch (expected: Exception)"),
        )
        assertTrue(
            "失败必须有用户可见的回显，否则用户只知道「没反应」",
            body.contains("R.string.lab_ai_import_failed"),
        )
    }

    @Test
    fun `影像导入失败时必须捕获异常`() {
        val body = importBody("fun importImagingReport(")
        assertTrue(body.contains("catch (expected: Exception)"))
        assertTrue(body.contains("R.string.imaging_ai_import_failed"))
    }

    @Test
    fun `协程取消要原样抛出，不能当成导入失败来提示`() {
        // 与 reminder/AlarmRegister.kt 的口径一致：取消不是失败。
        val body = importBody("fun importLabReport(")
        assertTrue(
            "必须先 catch CancellationException 再 throw e，否则用户退出页面会看到一条假的「导入失败」",
            body.contains("catch (e: CancellationException)") && body.contains("throw e"),
        )
    }

    @Test
    fun `失败分支不许清草稿——那是用户唯一还能拿回原文的地方`() {
        listOf("fun importLabReport(" to "ImportKind.LAB", "fun importImagingReport(" to "ImportKind.IMAGING")
            .forEach { (signature, kind) ->
                val body = importBody(signature)
                val catchAt = body.indexOf("catch (expected: Exception)")
                assertTrue("$signature 缺少失败分支", catchAt >= 0)
                val failureBranch = body.substring(catchAt)
                assertFalse(
                    "$signature 的失败分支里出现了 clearDraft($kind)：导入没成功却把原文删了",
                    failureBranch.contains("clearDraft($kind)"),
                )
                // 成功路径仍要清（B08 的既有口径），且只清本 kind 的。
                assertTrue(
                    "$signature 成功路径必须清掉本类型草稿（B08）",
                    body.substring(0, catchAt).contains("clearDraft($kind)"),
                )
            }
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
