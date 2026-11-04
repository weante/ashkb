package com.ashkb.app.ui.symptom

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * v1.2.15（外部 UI 审查 P1-6）：发作历史删除**必须先问一句**。
 *
 * ### 缺陷形态
 * 同一屏有两个删除按钮：BASDAI 自评（`BasdaiList`）走 `DestructiveAction` + 确认弹窗，
 * 发作历史（`FlareHistoryList`）却是一按就落库。误触的代价是**永久删除一次发作记录**
 * 以及它派生的「已第 7 天」提醒——`deleteFlare` 立即执行，没有任何撤销机会。
 * 两个相邻按钮一个问一个不问，本身就是交互不一致。
 *
 * ### 为什么是静态断言
 * 断的是「删除按钮是不是被 `DestructiveAction` 包着」，而不是点下去发生了什么——
 * 后者需要 Compose UI 测试基建（项目没有），而前者恰恰是本条唯一会退化的地方：
 * 有人把 `DestructiveAction` 换回裸 `TextButton` 图省事，行为就整条丢掉。
 */
class FlareDeleteConfirmGuardTest {

    private val cards = readSource("app/src/main/java/com/ashkb/app/ui/symptom/SymptomCards.kt")

    private fun flareListBody(): String {
        val start = cards.indexOf("fun FlareHistoryList(")
        assertTrue("找不到 FlareHistoryList —— 被改名了？本守卫需要同步更新", start >= 0)
        val end = cards.indexOf("fun BasdaiList(", start)
        assertTrue("找不到 BasdaiList —— 本守卫靠它定位 FlareHistoryList 的结尾", end > start)
        return cards.substring(start, end)
    }

    @Test
    fun `发作删除必须走确认弹窗，不能直接回调落库`() {
        val body = flareListBody()
        assertTrue(
            "删除按钮必须包在 DestructiveAction 里（BasdaiList 就是这么做的）：$body",
            body.contains("DestructiveAction("),
        )
        assertFalse(
            "不得再出现直接调 onDelete 的裸按钮 —— 那就是误触即永久删除的形态",
            body.contains("onClick = { onDelete("),
        )
    }

    @Test
    fun `确认文案必须说清删的是哪一次发作、以及连带后果`() {
        val body = flareListBody()
        assertTrue(
            "标题要带上发作日期，用户才知道点的是哪一条",
            body.contains("R.string.flare_delete_confirm"),
        )
        assertTrue(
            "正文要说明派生提醒也会撤销",
            body.contains("R.string.flare_delete_note"),
        )
        assertTrue(
            "还要说清不可逆（与 BasdaiList 同一口径）",
            body.contains("R.string.common_delete_irreversible"),
        )
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
