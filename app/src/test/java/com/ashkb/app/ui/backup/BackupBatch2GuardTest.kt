package com.ashkb.app.ui.backup

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * v1.2.15（外部 UI 审查批次 2：P1-4 / P1-5）：两处「下一步不在用户眼前」的接线守卫。
 *
 * ### 为什么还是静态断言
 * 两个缺陷都不是"算错了"，而是**时序与归属**：
 * - P1-4：闸位是在 `launch` **体内**才翻的，而 `viewModelScope.launch` 只是把协程排进队列。
 *   要复现「从点击到协程执行之间的空窗里再点一次」得在 JVM 上精确控制调度点，而
 *   `BackupViewModel` 的构造要 `AshkbApplication` + 真 `BackupRepository`（`AppDatabase`、
 *   AndroidKeyStore、filesDir），搭不出来。
 * - P1-5：待确认块渲染在哪张卡里，是纯 Compose 组合结果，项目没有 Compose UI 测试基建。
 *
 * 能钉死、且最容易在后续重构中悄悄丢掉的，是这几件事：
 * 1. 闸必须在 `launch` 之前翻转（挪进协程 = 缺陷原状）；
 * 2. `finally` 必须复位（不漏则一次失败之后按钮永久变灰）；
 * 3. 按钮的 `running` 必须由闸派生，不能退回 `attachSyncStage != null`；
 * 4. 待确认块与结果行的渲染必须由 `restoreOrigin` 分流，且两处各认自己那一个来源。
 */
class BackupBatch2GuardTest {

    private val vmSrc = readSource("app/src/main/java/com/ashkb/app/ui/backup/BackupViewModel.kt")
    private val sectionSrc = readSource("app/src/main/java/com/ashkb/app/ui/backup/BackupSyncSection.kt")
    private val restoreSrc = readSource("app/src/main/java/com/ashkb/app/ui/backup/BackupRestoreSection.kt")

    // ---------------- P1-4：附件同步 / 校验的互斥闸 ----------------

    @Test
    fun `互斥闸必须在起协程之前翻转，否则双击仍会一起穿过`() {
        listOf("fun syncAttachments()", "fun verifyAttachments()").forEach { signature ->
            val body = body(vmSrc, signature)
            val guardAt = body.indexOf("if (_attachOperationRunning.value) return")
            val setAt = body.indexOf("_attachOperationRunning.value = true")
            val launchAt = body.indexOf("viewModelScope.launch")
            assertTrue("$signature 缺少互斥闸（_attachOperationRunning 检查）", guardAt >= 0)
            assertTrue("$signature 缺少闸位翻转（_attachOperationRunning = true）", setAt >= 0)
            assertTrue("$signature 找不到 viewModelScope.launch —— 函数结构变了？", launchAt >= 0)
            // 这条是本缺陷的全部要害：先占位再起协程。顺序反了就等于没有任何闸。
            assertTrue(
                "$signature 的闸位翻转必须发生在 viewModelScope.launch 之前" +
                    "（guard=$guardAt / set=$setAt / launch=$launchAt）",
                guardAt < launchAt && setAt < launchAt,
            )
        }
    }

    @Test
    fun `闸必须在 finally 里复位，否则一次失败就把按钮永久锁死`() {
        listOf("fun syncAttachments()", "fun verifyAttachments()").forEach { signature ->
            val body = body(vmSrc, signature)
            val finallyAt = body.indexOf("finally {")
            assertTrue("$signature 缺少 finally 块", finallyAt >= 0)
            assertTrue(
                "$signature 的 finally 必须复位 _attachOperationRunning",
                body.substring(finallyAt).contains("_attachOperationRunning.value = false"),
            )
        }
    }

    @Test
    fun `按钮的运行态必须由闸派生，不能退回进度文案`() {
        // 缺陷原状就是 running = attachSyncStage != null：进度串要等协程跑到第一句才置位。
        assertFalse(
            "running 不得再派生自 attachSyncStage——那正是 P1-4 的空窗来源",
            sectionSrc.contains("running = attachSyncStage != null"),
        )
        assertTrue(
            "section 必须收集 attachOperationRunning",
            sectionSrc.contains("vm.attachOperationRunning.collectAsStateWithLifecycle()"),
        )
        assertTrue(
            "running 必须由闸派生",
            sectionSrc.contains("running = attachRunning"),
        )
    }

    // ---------------- P1-5：谁发起、谁显示 ----------------

    @Test
    fun `待确认块只在发起它的那张卡里渲染`() {
        val restoreCard = body(restoreSrc, "internal fun RestoreSection(")
        val undoCard = body(restoreSrc, "internal fun UndoSnapshotBlock(")

        // 用**整行相等**而不是 contains：`if (false && restoreOrigin == …)` 之类
        // 在子串意义上仍然"含有"该条件，但渲染实际上被短路了——那正是要拦的形态。
        assertTrue(
            "恢复卡必须先判 pending 再判来源，且条件是整行 `pending != null && restoreOrigin == PICKED_FILE`：" +
                "实际写了 ${linesOf(restoreCard).filter { it.contains("pending != null") }}",
            linesOf(restoreCard).contains("if (pending != null && restoreOrigin == RestoreOrigin.PICKED_FILE) {"),
        )
        assertTrue(
            "撤销块必须以整行 `if (restoreOrigin == RestoreOrigin.SNAPSHOT_UNDO) {` 包住待确认块：" +
                "实际写了 ${linesOf(undoCard).filter { it.contains("SNAPSHOT_UNDO") }}",
            linesOf(undoCard).contains("if (restoreOrigin == RestoreOrigin.SNAPSHOT_UNDO) {"),
        )
        assertTrue(
            "撤销块必须自己渲染待确认块（否则确认步骤仍在上方另一张卡里）",
            undoCard.contains("RestorePendingBlock("),
        )
        // 两个来源互斥：任一处认错枚举值，就会两处同时渲染（或都不渲染）。
        assertFalse(
            "恢复卡不得认 SNAPSHOT_UNDO —— 那会让两处同时渲染",
            restoreCard.contains("RestoreOrigin.SNAPSHOT_UNDO"),
        )
        assertFalse(
            "撤销块不得认 PICKED_FILE —— 同样会两处同时渲染",
            undoCard.contains("RestoreOrigin.PICKED_FILE"),
        )
    }

    @Test
    fun `两个发起入口各自把来源写进 VM`() {
        val pickedAt = vmSrc.indexOf("RestoreOrigin.PICKED_FILE)")
        val undoAt = vmSrc.indexOf("RestoreOrigin.SNAPSHOT_UNDO)")
        assertTrue("verifyBackup 必须传 PICKED_FILE", pickedAt >= 0)
        assertTrue("undoFromPreRestoreSnapshot 必须传 SNAPSHOT_UNDO", undoAt >= 0)
        // 顺序即归属：文件选择在 undo 之前（verifyBackup 定义在前）。
        assertTrue("两个来源被写反了？PICKED_FILE 应出现在 SNAPSHOT_UNDO 之前", pickedAt < undoAt)
        assertTrue(
            "verifyBytes 必须真的落库来源，否则上面的传参是空的",
            body(vmSrc, "private suspend fun verifyBytes(").contains("_restoreOrigin.value = origin"),
        )
    }

    @Test
    fun `取消恢复要把来源一并复位`() {
        assertTrue(
            "cancelRestore 必须把 _restoreOrigin 复位成 PICKED_FILE，" +
                "否则下一次「选了文件但还没解密」的空窗里，待确认块归属会停在上一次的撤销入口",
            body(vmSrc, "fun cancelRestore()")
                .contains("_restoreOrigin.value = RestoreOrigin.PICKED_FILE"),
        )
    }

    /** 从签名起到下一个成员声明（KDoc / @Composable / fun）为止的函数体。 */
    private fun body(src: String, signature: String): String {
        val start = src.indexOf(signature)
        assertTrue("找不到 $signature —— 被改名了？本守卫需要同步更新", start >= 0)
        val from = start + signature.length
        val ends = listOf("\n    /**", "\n    fun ", "\n    @Composable", "\n@Composable", "\ninternal fun ", "\nprivate fun ")
            .map { src.indexOf(it, from) }
            .filter { it > start }
        return src.substring(start, ends.minOrNull() ?: src.length)
    }

    /** 去空白后的整行集合——用于「必须整行相等」的断言。 */
    private fun linesOf(text: String): List<String> = text.lines().map { it.trim() }

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
