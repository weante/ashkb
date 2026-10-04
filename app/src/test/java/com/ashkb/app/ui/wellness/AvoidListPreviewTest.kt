package com.ashkb.app.ui.wellness

import com.ashkb.app.data.entity.FoodAvoidItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.1.2：忌口清单卡的**预览规则**——高危（`high`）条目一条都不许被截掉。
 *
 * ### 这次修的是什么
 * `WellnessScreen.AvoidListCard` 此前是 `avoids.take(5)`：排在第 6 位及以后的忌口条目
 * 在这张卡上**整条不出现**。卡片是本屏唯一的忌口入口，「管理」弹层之外的任何地方都看不到
 * 被折叠的那几条——而症状是**不报错的静默丢失**。批次 18 已经把 DAO 的 `ORDER BY CASE`
 * 修成高危在前，但那只是让「高危在前」碰巧成立；本用例钉的是展示层自己必须保证的性质。
 *
 * ### 为什么不用 UI 测试
 * 本模块没有 Compose UI 测试依赖（见 [WellnessScreenStateScopeTest] 的类注释），所以被测的是
 * 纯函数 [prioritizeHigh] 本身——它是这条规则的**唯一实现**，UI 只消费它的返回值。
 */
class AvoidListPreviewTest {

    private fun item(name: String, severity: String) = FoodAvoidItem(
        id = "fa-$name",
        name = name,
        category = "allergy",
        severity = severity,
        kbRef = null,
        symptoms = null,
        notes = null,
        createdAt = "2026-01-01T00:00:00",
        updatedAt = "2026-01-01T00:00:00",
    )

    private fun list(vararg pairs: Pair<String, String>) = pairs.map { item(it.first, it.second) }

    /** 不超过上限时原样返回：正常用户（几条忌口）看到的与改动前完全一致。 */
    @Test
    fun `short lists are returned untouched`() {
        val items = list("牛奶" to "high", "花生" to "medium", "香菇" to "low")
        assertEquals(items, prioritizeHigh(items, 5))
        assertTrue(prioritizeHigh(emptyList(), 5).isEmpty())
    }

    /**
     * 核心性质：**无论怎么排、条数多少，每一条 `high` 都必须出现在预览里。**
     *
     * 特意把 `high` 放在末尾（与 DAO 排序相反的输入）——展示层不能把安全性外包给 SQL。
     */
    @Test
    fun `no high item is ever dropped regardless of input order`() {
        val items = list(
            "低1" to "low", "低2" to "low", "低3" to "low",
            "高1" to "high", "低4" to "low", "高2" to "high", "低5" to "low", "高3" to "high",
        )
        val shown = prioritizeHigh(items, 5)
        val shownNames = shown.map { it.name }.toSet()
        for (high in items.filter { it.severity == "high" }) {
            assertTrue("${high.name} 是高危项却被截掉了（预览=$shownNames）", high.name in shownNames)
        }
    }

    /** 高危项多于上限时，**高危全给**，其余一条都不占位。 */
    @Test
    fun `high items overflow the limit instead of the low ones`() {
        val items = list(
            "低1" to "low", "高1" to "high", "高2" to "high", "高3" to "high",
            "高4" to "high", "高5" to "high", "高6" to "high", "低2" to "low",
        )
        val shown = prioritizeHigh(items, 5)
        assertEquals(
            "高危 6 条必须全部展示，其余（低1/低2）被折叠",
            listOf("高1", "高2", "高3", "高4", "高5", "高6"),
            shown.map { it.name },
        )
    }

    /**
     * 「只展示前 N 条」的信息意图保留：高危没超限时，其余项仍按**原顺序**补足到 N，
     * 且不重排（输入本来有序时，输出与 `take(N)` 逐字相同）。
     */
    @Test
    fun `non-high items keep original order and fill up to the limit`() {
        val items = list(
            "高1" to "high",
            "中1" to "medium", "低1" to "low", "中2" to "medium", "低2" to "low", "低3" to "low",
        )
        val shown = prioritizeHigh(items, 5)
        assertEquals(listOf("高1", "中1", "低1", "中2", "低2"), shown.map { it.name })
        assertEquals(items.take(5).map { it.name }, shown.map { it.name })
    }

    /**
     * 未知 / 空 `severity` 按「非高危」处理：它照样可能被折叠，但**不会**因此把高危项挤出预览。
     * 之所以不为未知值放宽上限：那会让一条脏数据把卡片的条数承诺（见 `AVOID_PREVIEW_LIMIT`）撑开，
     * 而它的风险等级本来就无从判断。
     */
    @Test
    fun `unknown severity is treated as non-high`() {
        val items = list("高1" to "high", "怪1" to "", "怪2" to "weird", "低1" to "low", "低2" to "low")
        val shown = prioritizeHigh(items, 5)
        assertEquals(5, shown.size)
        assertTrue(shown.map { it.name }.contains("高1"))
    }
}
