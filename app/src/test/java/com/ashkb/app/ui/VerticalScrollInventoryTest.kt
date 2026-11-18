package com.ashkb.app.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.2.18：**`verticalScroll` 的用途清单必须是被审过的**。
 *
 * ### 为什么有这条测试
 * `HANDOFF-STATUS.md` §3.2 长期挂着「20 处 verticalScroll（14 文件）未整理」。2026-11-18 逐处看过，
 * 实际是 **26 处 / 18 文件**，而且**没有一处该换成 `LazyColumn`**：它们要么是 `AlertDialog.text`、
 * 要么是 `ModalBottomSheet` 的正文、要么是条目数固定的单列页面、要么是刻意「限高内滚」的小块
 * （`DailyValuesList` 的 `heightIn(max = 220dp)`、AI 导入模板预览的 `heightIn(max = 160.dp)`）。
 * 真正的长列表（知识库 48 条、化验 50 条、复诊记录、运动库）早就是 `LazyColumn`。
 *
 * 所以这一项的正确收尾不是「改代码」，而是**把清单钉死**：以后往页面级容器上加 `verticalScroll`、
 * 或把某处悄悄换成 `LazyColumn`，这里会红，必须回来更新清单并写清理由。清单本身就是结论。
 *
 * ### 它不能证明什么
 * 它只数「哪个文件里有几处」。它不判断某处用得对不对——那需要人看上下文，结论见
 * `docs/v1218-perf-audit.md` 的分类表。它也不覆盖 `horizontalScroll`。
 */
class VerticalScrollInventoryTest {

    /** 主源码目录：单测工作目录是模块根（`app/`），两种布局都试一次。 */
    private val sourceRoot: File =
        listOf(File("src/main/java"), File("app/src/main/java")).firstOrNull { it.isDirectory }
            ?: error("未找到主源码目录（工作目录=${File(".").absolutePath}）")

    /** 审过的清单：相对 `com/ashkb/app/` 的路径 → `verticalScroll` 处数。 */
    private val expected: Map<String, Int> = mapOf(
        // 整页单列（条目数固定）
        "ui/DisclaimerScreen.kt" to 1,
        "ui/backup/BackupScreen.kt" to 1,
        "ui/me/MedEditScreen.kt" to 1,
        "ui/me/ProfileEditScreen.kt" to 1,
        "ui/me/ReminderCheckScreen.kt" to 1,
        // 对话框正文（AlertDialog.text）
        "ui/symptom/SymptomDialogs.kt" to 2,
        "ui/knowledge/KbDetailDialog.kt" to 1,
        "ui/emergency/EmergencyScreen.kt" to 1,
        "ui/exercise/ExerciseScreen.kt" to 2,
        "ui/checkup/CheckupLabImaging.kt" to 6,
        // sheet 正文与 sheet 骨架
        "ui/knowledge/KbEditSheet.kt" to 1,
        "ui/backup/BackupSheets.kt" to 2,
        "ui/checkup/AttachmentSheet.kt" to 1,
        "ui/emergency/EmergencyEventSheet.kt" to 1,
        "ui/me/ReminderSettingsSheet.kt" to 1,
        "ui/wellness/RecipesScreen.kt" to 1,
        "ui/wellness/WellnessScreen.kt" to 1,
        // 刻意限高内滚
        "ui/components/DailyValuesList.kt" to 1,
    )

    private fun scan(): Map<String, Int> {
        val result = sortedMapOf<String, Int>()
        sourceRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { f ->
                val n = Regex(Regex.escape(".verticalScroll(")).findAll(f.readText()).count()
                if (n > 0) {
                    result[f.relativeTo(sourceRoot).invariantSeparatorsPath
                        .removePrefix("com/ashkb/app/")] = n
                }
            }
        return result
    }

    @Test
    fun `verticalScroll 只出现在审过的这些文件里`() {
        val actual = scan()
        // 路径写错时大声失败，而不是空跑通过（本仓惯例）
        assertTrue(
            "没扫到任何 verticalScroll，说明扫描路径不对（sourceRoot=${sourceRoot.absolutePath}）",
            actual.isNotEmpty(),
        )
        assertEquals(
            "verticalScroll 的分布变了。新增/删除都必须先想清楚：页面级容器不该用它（该用 LazyColumn）；" +
                "对话框/sheet 正文、条目数固定的单列页、刻意限高内滚才该用它。改完请同步 " +
                "docs/v1218-perf-audit.md 的分类表与这里的清单。实际分布=$actual",
            expected,
            actual,
        )
    }

    @Test
    fun `总数是 26 处 18 个文件`() {
        val actual = scan()
        assertEquals("文件数", 18, actual.size)
        assertEquals("处数", 26, actual.values.sum())
    }
}
