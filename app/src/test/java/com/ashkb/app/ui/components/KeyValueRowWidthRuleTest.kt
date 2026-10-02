package com.ashkb.app.ui.components

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.1.1（CRITICAL）：`KeyValueRow` 的**宽度规则结构守卫**（纯静态扫描）。
 *
 * ### 它守的是哪条回归
 * v1.0.90 修好、v1.0.95 又放开的那个缺陷：急救卡「药名 + 剂量说明 + 免疫抑制胶囊」那一行里，
 * 数值列排在胶囊**前面且非权重** → 第一遍测量时它拿到的 maxWidth 是"行宽 − 标签宽"，
 * 长剂量说明吃满 → 排在后面的胶囊拿到 `maxWidth = 0` → 被压成零宽（安全标记从急救卡上消失）。
 *
 * ### 为什么是静态扫描而不是 UI 测试
 * 本模块**没有 Compose UI 测试依赖**（无 `ui-test-junit4`），且 Robolectric 在本机量到的字体宽度
 * 是 1.0dp（`build.gradle.kts` 有记录），宽度断言没有意义。这与 `SupplementSkipWiringTest` /
 * `RegexLiteralGuardTest` 在本项目已用了多个版本的做法一致。
 *
 * ### 局限（明说，不含糊）
 * 文本断言证明的是「结构写成了什么样」，**不是"运行时胶囊真的拿到了固有宽度"**。
 * 运行时那一半只能靠真机截图（本批次在说明里给了三种情形的最窄宽度复算表）。
 * 具体地，本用例能挡住：
 *  · 把数值列改回**非权重**（长值会再次吃掉整行）；
 *  · 把 `trailing()` 挪进权重容器内（那时它就不再是"第一遍就拿到整行宽"的那个子项）；
 *  · 把末端对齐换回 `SpaceBetween`（短值靠右与长值折行又变成互斥）。
 * 它挡不住：容器顺序被整体重排、`Modifier` 链上又挂了一个改变约束的修饰符。
 */
class KeyValueRowWidthRuleTest {

    private val sourceRoot: File =
        listOf(File("src/main/java"), File("app/src/main/java")).firstOrNull { it.isDirectory }
            ?: error("未找到主源码目录（工作目录=${File(".").absolutePath}）")

    /** 归一化空白后再断言：避免被换行/缩进的一次重排假红。 */
    private val src: String
        get() = File(sourceRoot, "com/ashkb/app/ui/components/DataDisplay.kt").readText()

    private val kdoc: String get() = src.substringBefore("@Composable\nfun KeyValueRow(")

    private val body: String
        get() = src.substringAfter("fun KeyValueRow(").replace(Regex("\\s+"), " ")

    @Test
    fun `标签与数值必须在同一个权重容器里`() {
        assertTrue(
            "外层 Row 的非权重子项只应剩胶囊一个：标签与数值要一起放进权重容器，" +
                "否则长数值会在第一遍测量里吃掉整行、把胶囊挤成零宽",
            body.contains("Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, ) { Text( label,"),
        )
    }

    @Test
    fun `胶囊是外层 Row 的最后一个子项且不在权重容器内`() {
        assertTrue(
            "trailing() 必须是外层 Row 的最后一个子项（非权重）——它在第一遍测量里拿到整行宽，固有宽度无条件成立",
            body.contains("if (trailing != null) trailing() }"),
        )
    }

    @Test
    fun `数值列带权重并且末端对齐`() {
        assertTrue(
            "数值列必须 weight(1f)：约束被收紧成「主体宽 − 标签宽 − 间距」，长值在其中折行",
            body.contains("Modifier.weight(1f).padding(start = Spacing.lg)"),
        )
        assertTrue(
            "短值靠右用列内末端对齐实现（长值填满整列时首末端对齐等价）",
            body.contains("Arrangement.spacedBy(Spacing.xs, Alignment.End)"),
        )
        assertFalse(
            "不得回到 SpaceBetween + 非权重数值列——那正是「长值吃掉整行」的写法",
            body.contains("Arrangement.SpaceBetween"),
        )
    }

    @Test
    fun `KDoc 记录的是已被字节码证实的两遍测量机理`() {
        assertTrue("必须写明第一遍只测非权重子项", kdoc.contains("只测非权重子项"))
        assertTrue("必须写明胶囊的保证是无条件的（它前面没有非权重子项）", kdoc.contains("无条件"))
        assertTrue(
            "必须留下「v1.0.95 那句兄弟节点论证是错的」这条更正——文档描述不存在的保护比没有文档更危险",
            kdoc.contains("是错的"),
        )
    }
}
