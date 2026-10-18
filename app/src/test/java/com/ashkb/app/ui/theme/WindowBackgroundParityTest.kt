package com.ashkb.app.ui.theme

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 窗口背景与 Compose 设计令牌的一致性守卫。
 *
 * **为什么需要这个测试**：v1.2.10 的 UI 2.0 批次 D 验收（设计稿 §06 帧②「深色」）照出一个
 * 真实缺陷 —— App **从未真正绘制过页面底色**。`Scaffold(containerColor = Color.Transparent)`
 * 是 v1.1.6 为消掉手势区横带而定的，之后页面背景**由窗口背景兜底**；而 `Theme.Ashkb` 的
 * parent 是 `android:Theme.Material.Light.NoActionBar`，其 `android:windowBackground`
 * = `background_material_light` = **#FAFAFA**。
 *
 * 浅色下 #FAFAFA 与设计稿的冷调瓷白 #F3F6F8 极接近，所以一直没人发现；深色下卡片变深、
 * 页面底仍是 #FAFAFA，于是**每张深色卡片周围都出现白边**。也就是说：批次 A「换色」把
 * `backgroundLight` / `backgroundDark` 改成瓷白 / 深墨，**从未真正生效**。
 *
 * 修法是把窗口背景显式钉到设计令牌（`res/values/colors.xml` + `res/values-night/colors.xml`
 * + 两个 `themes.xml` 的 `android:windowBackground`）。代价是**同一组颜色现在有两份副本**
 * （Kotlin 令牌 + XML 资源），两份一旦漂移就会重新出现「白边」。本测试就是那条防线。
 *
 * 静态扫文件，不依赖 Compose（同 `EnglishStringsParityTest` / `FormatLocaleGuardTest` 的做法），
 * 并按本仓惯例**断言确实读到了文件**：路径写错时大声失败，而不是悄悄跳过。
 */
class WindowBackgroundParityTest {

    private fun resFile(relative: String): File =
        listOf(File("app/src/main/res/$relative"), File("src/main/res/$relative"))
            .firstOrNull { it.isFile }
            ?: error("未找到资源文件 res/$relative（工作目录=${File(".").absolutePath}）")

    private fun sourceFile(relative: String): File =
        listOf(File("app/src/main/java/com/ashkb/app/$relative"), File("src/main/java/com/ashkb/app/$relative"))
            .firstOrNull { it.isFile }
            ?: error("未找到源文件 $relative（工作目录=${File(".").absolutePath}）")

    /** 取 `Color.kt` 里某个 `val <name> = Color(0xAARRGGBB)` 的 ARGB 十六进制（8 位，大写、带 0x 前缀）。 */
    private fun tokenHex(tokenName: String): String {
        val text = sourceFile("ui/theme/Color.kt").readText()
        val m = Regex("""val\s+$tokenName\s*=\s*Color\(0x([0-9A-Fa-f]{8})\)""").find(text)
            ?: error("Color.kt 里找不到 val $tokenName = Color(0x……) 的声明")
        return m.groupValues[1].uppercase()
    }

    /** 取某个 `colors.xml` 里 `ashkb_window_background` 的 `#AARRGGBB`。 */
    private fun xmlWindowBackground(relative: String): String {
        val text = resFile(relative).readText()
        val m = Regex("""<color\s+name="ashkb_window_background">\s*#([0-9A-Fa-f]{8})\s*</color>""").find(text)
            ?: error("res/$relative 里找不到 ashkb_window_background 的 #AARRGGBB 声明")
        return m.groupValues[1].uppercase()
    }

    /** 取某个 `themes.xml` 里 `android:windowBackground` 的引用值。 */
    private fun themeWindowBackground(relative: String): String {
        val text = resFile(relative).readText()
        val m = Regex("""<item\s+name="android:windowBackground">\s*([^<\s]+)\s*</item>""").find(text)
            ?: error("res/$relative 的 Theme.Ashkb 里没有 android:windowBackground —— 页面底色会退回 Material 默认值")
        return m.groupValues[1]
    }

    private fun themeParent(relative: String): String {
        val text = resFile(relative).readText()
        val m = Regex("""<style\s+name="Theme\.Ashkb"\s+parent="([^"]+)"""").find(text)
            ?: error("res/$relative 里找不到 Theme.Ashkb 的 parent")
        return m.groupValues[1]
    }

    @Test
    fun `light window background equals backgroundLight token`() {
        assertEquals(
            "res/values/colors.xml 的窗口底色与 Color.kt 的 backgroundLight 不一致（浅色页面底色会漂移）",
            tokenHex("backgroundLight"),
            xmlWindowBackground("values/colors.xml"),
        )
    }

    @Test
    fun `dark window background equals backgroundDark token`() {
        assertEquals(
            "res/values-night/colors.xml 的窗口底色与 Color.kt 的 backgroundDark 不一致（深色下会出现白边）",
            tokenHex("backgroundDark"),
            xmlWindowBackground("values-night/colors.xml"),
        )
    }

    @Test
    fun `both themes point window background at the shared color`() {
        for (relative in listOf("values/themes.xml", "values-night/themes.xml")) {
            assertEquals(
                "res/$relative 的 android:windowBackground 必须引用 @color/ashkb_window_background",
                "@color/ashkb_window_background",
                themeWindowBackground(relative),
            )
        }
    }

    /**
     * 深色主题不得再挂浅色 parent。
     *
     * 缺陷的另一半：`values-night/themes.xml` 原先是「深色主题挂 `android:Theme.Material.Light.NoActionBar`」，
     * 于是窗口背景、系统绘制部件的基准全按浅色走，`windowLightStatusBar=false` 只是打了单个补丁。
     */
    @Test
    fun `night theme does not use a Light parent`() {
        val parent = themeParent("values-night/themes.xml")
        assertTrue(
            "values-night/themes.xml 的 parent 仍是浅色主题（$parent）——深色主题必须挂深色 parent",
            !parent.contains("Light"),
        )
        assertTrue(
            "values-night/themes.xml 的 parent 应指向 Material 深色主题，实际是 $parent",
            parent == "android:Theme.Material.NoActionBar",
        )
    }
}
