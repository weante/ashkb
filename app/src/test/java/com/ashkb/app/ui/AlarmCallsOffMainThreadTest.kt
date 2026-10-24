package com.ashkb.app.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.2.11（缺陷工单 C05）：UI 层不得在 Main dispatcher 上跑批量闹钟 IPC。
 *
 * **这是补充守卫，不是行为测试。** C05 的行为回归由既有的六个 scheduler 行为测试覆盖
 * （`ReminderSchedulerTest` / `ReminderTestSchedulerTest` / `CheckupReminderSchedulerTest` /
 * `BasdaiReminderSchedulerTest` / `ExerciseReminderSchedulerTest` / `MedEditDerivedDataTest`
 * 都通过 `ShadowAlarmManager.scheduledAlarms` 断言真实排出来的闹钟），
 * 那几条一旦本批改动破坏了顺序 / 身份 / 取消语义就会红。
 *
 * 这里补的是**调用位置**这一条静态事实：`ui/` 下每一处 AlarmManager 批量调用，
 * 都必须落在 `withContext(Dispatchers.IO) { … }` 的花括号块内。
 * 撤销修复（把某处挪回 `viewModelScope.launch {` 直调）会让这条红。
 */
class AlarmCallsOffMainThreadTest {

    /** 工单 C05 点名的批量入口；`scheduleSnooze` 是单次调用，不在约束内。 */
    private val bulkCalls = listOf(
        "Scheduler.rescheduleAll(",
        "Scheduler.cancelAllFuture(",
    )

    @Test
    fun `ui 层的批量闹钟调用都在 IO 块内`() {
        val hits = uiSources().flatMap { (path, source) -> callSites(path, source) }
        assertTrue("扫描到的调用点太少（${hits.size}），守卫自身的假设可能已失效", hits.size >= 10)
        val offenders = hits.filterNot { it.inIoBlock }
        assertTrue(
            "以下批量闹钟调用不在 withContext(Dispatchers.IO) 内（C05）：\n" +
                offenders.joinToString("\n") { "${it.path}:${it.line}  ${it.call.trimEnd('(')}" },
            offenders.isEmpty(),
        )
    }

    @Test
    fun `IO 块判据本身能被反例证伪`() {
        // 造两段合成源码，证明 insideIoBlock 真的区分了「块内」与「块外」——
        // 否则上面那条全绿可能只是判据永远返回 true。
        val inBlock = "fun f() = viewModelScope.launch {\n" +
            "    withContext(Dispatchers.IO) {\n" +
            "        ReminderScheduler.rescheduleAll(ctx, list, refs)\n" +
            "    }\n" +
            "}\n"
        val outBlock = "fun f() = viewModelScope.launch {\n" +
            "    ReminderScheduler.rescheduleAll(ctx, list, refs)\n" +
            "}\n"
        assertTrue(
            "块内的调用必须被判为块内",
            insideIoBlock(inBlock, inBlock.indexOf("Scheduler.rescheduleAll(")),
        )
        assertTrue(
            "块外的调用必须被判为块外",
            !insideIoBlock(outBlock, outBlock.indexOf("Scheduler.rescheduleAll(")),
        )
    }

    private data class Site(val path: String, val line: Int, val call: String, val inIoBlock: Boolean)

    private fun callSites(path: String, source: String): List<Site> =
        bulkCalls.flatMap { call -> occurrences(source, call).map { at ->
            Site(
                path = path,
                line = source.take(at).count { it == '\n' } + 1,
                call = call,
                inIoBlock = insideIoBlock(source, at),
            )
        } }

    private fun occurrences(source: String, call: String): List<Int> {
        val found = mutableListOf<Int>()
        var from = 0
        while (true) {
            val at = source.indexOf(call, from)
            if (at < 0) return found
            found += at
            from = at + call.length
        }
    }

    /**
     * 从调用点向上找最近的 `withContext(Dispatchers.IO)`，再看两者之间 `{`/`}` 的净差：
     * 净差 > 0 说明调用仍在该块内。单行形态 `withContext(Dispatchers.IO) { Scheduler.xxx(…) }`
     * 也覆盖（开括号在调用点之前、闭括号在其之后）。
     */
    private fun insideIoBlock(source: String, callAt: Int): Boolean {
        val marker = "withContext(Dispatchers.IO)"
        val ioAt = source.lastIndexOf(marker, callAt)
        if (ioAt < 0) return false
        val between = source.substring(ioAt + marker.length, callAt)
        val open = between.count { it == '{' }
        val close = between.count { it == '}' }
        return open > close
    }

    private fun uiSources(): List<Pair<String, String>> {
        val root = repoRoot()
        val ui = File(root, "app/src/main/java/com/ashkb/app/ui")
        return ui.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .map { it.relativeTo(root).path.replace('\\', '/') to it.readText() }
            .sortedBy { it.first }
            .toList()
    }

    private fun repoRoot(): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            if (File(dir, "settings.gradle.kts").exists()) return dir
            dir = dir.parentFile
        }
        return File("../")
    }
}
