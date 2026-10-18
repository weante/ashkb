package com.ashkb.app.ui.today

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `TodayViewModel` 的**声明顺序守卫**（v1.2.10，2026-10-18 真机间歇崩溃的回归防线）。
 *
 * ## 它守的是什么
 *
 * Kotlin 按**声明顺序**初始化属性，而 `init { viewModelScope.launch { refreshYesterdayPending() } }`
 * 用的是 `Dispatchers.Main.immediate`：主线程上 `launch` 会同步内联到 `refreshYesterdayPending()`
 * 的第一句 `withContext(Dispatchers.IO)`，在那里派发 IO 协程并返回；主线程随后继续跑构造器里
 * 剩下的 `stateIn` / `flatMapLatest` 链，而 IO 线程**可以并发**跑到 `_yesterdayPending.value = …`。
 * 两条线程之间没有 happens-before 边，于是「本字段此刻是否已赋值」变成竞态。
 *
 * 崩溃实证（release + R8 混淆包，2026-10-18 12:30:52）：
 * ```
 * java.lang.NullPointerException: Attempt to invoke virtual method
 *   'java.lang.Class java.lang.Object.getClass()' on a null object reference
 *     at W1.I.u(Unknown Source:353)
 * ```
 * `mapping.txt` 把 `W1.I.u` 钉到 `TodayViewModel$refreshYesterdayPending$2.invokeSuspend:255`，
 * 且 dex 里偏移 `0x161`(=353) 正是 `iget-object v5, v5, LW1/U;.o:LA2/Z;` 之后、读 `_yesterdayPending`
 * 并在 `StateFlowImpl.updateState` 上做空检查的那条 `getClass()`。
 *
 * ## 为什么用静态源码断言而不是运行时测试
 *
 * 竞态窗口只有「构造器跑到 `withContext` 之后的那些初始化」这一小段，单测里几乎无法稳定复现
 * （真机上也只是熄屏/doze 冷启动才偶发）。声明顺序是**源码形状**，读文件断言即可 100% 锁住——
 * 与 `ui/me/MedRouteFrequencyGuardTest.kt` 的读源文件断言同一路数。
 *
 * ⚠️ 这些断言故意做得很"钝"：只要求「声明在 init 之前」，不要求任何具体行号。将来重构这个文件时，
 * 只要顺序仍然正确就不会误报。
 */
class TodayViewModelInitOrderTest {

    private val source: String = readSource("app/src/main/java/com/ashkb/app/ui/today/TodayViewModel.kt")

    /** `init {` 之后那一段（含 init 块本身），用于判断「声明在 init 之后」。 */
    private val afterInit: String = source.substringAfter("    init {", missingDelimiterValue = "")

    @Test
    fun `补记字段声明在 init 之前`() {
        assertTrue(
            "文件里必须能找到 init 块（本守卫依赖它的位置）",
            source.contains("    init {"),
        )
        assertFalse(
            "_yesterdayPending 必须在 init 之前声明：init 里的 launch 会在主线程上同步内联到 " +
                "withContext(Dispatchers.IO)，IO 线程可能赶在构造器给该字段赋值之前读到它，从而在 " +
                "TodayViewModel.kt 的 `_yesterdayPending.value = …` 处抛 NPE（2026-10-18 真机崩溃）",
            afterInit.contains("private val _yesterdayPending"),
        )
        assertTrue(
            "_yesterdayPending 的声明必须存在（名字被改掉时本守卫要报错，而不是静默通过）",
            source.contains("private val _yesterdayPending = MutableStateFlow<List<PendingDoses.Pending>>(emptyList())"),
        )
    }

    @Test
    fun `只读视图声明在 init 之前`() {
        // `yesterdayPending = _yesterdayPending` 是构造期就要求值的初始化表达式，字段为 null 时
        // 它拿到的是 null 快照（不是随后的空流），UI 侧同样会读到未初始化值。
        assertFalse(
            "yesterdayPending 也必须在 init 之前声明（它直接读 _yesterdayPending 求值）",
            afterInit.contains("val yesterdayPending: StateFlow<List<PendingDoses.Pending>> = _yesterdayPending"),
        )
        assertTrue(
            "yesterdayPending 的声明必须存在",
            source.contains("val yesterdayPending: StateFlow<List<PendingDoses.Pending>> = _yesterdayPending"),
        )
    }

    @Test
    fun `init 与补记函数不引用 init 之后声明的属性`() {
        val initBody = braceBody(source, "    init {")
        assertTrue(
            "init 块必须真的调用 refreshYesterdayPending（否则这条守卫在守一个空块）",
            initBody.contains("refreshYesterdayPending()"),
        )
        val refreshBody = braceBody(source, "suspend fun refreshYesterdayPending() = withContext(Dispatchers.IO) {")
        assertTrue(
            "refreshYesterdayPending 必须真的写 _yesterdayPending.value（否则守卫失焦）",
            refreshBody.contains("_yesterdayPending.value = PendingDoses.unsettledOn("),
        )

        // 通用规则：任何「声明在 init 之后」的属性，都不能被 init 块或 refreshYesterdayPending 引用。
        // 逐个具名断言容易漏（下次新增字段又会踩同一个坑），这条按名字自动扫。
        val lateProperties = propertyNamesAfter(source, "    init {")
        for (name in lateProperties) {
            assertFalse(
                "属性 `$name` 声明在 init 之后，却被 init 块引用——构造期它尚未赋值",
                initBody.contains(name),
            )
            assertFalse(
                "属性 `$name` 声明在 init 之后，却被 refreshYesterdayPending 引用——" +
                    "该函数可能在构造期就跑到（IO 线程与构造器并发），会读到 null",
                refreshBody.contains(name),
            )
        }
    }

    // ==================== 工具 ====================

    /** 从 `header` 起，取到与它同一缩进层级的下一个 `    }` 为止的块体。 */
    private fun braceBody(text: String, header: String): String {
        val start = text.indexOf(header)
        if (start < 0) return ""
        val from = start + header.length
        val end = text.indexOf("\n    }", from)
        return if (end < 0) text.substring(from) else text.substring(from, end)
    }

    /**
     * 找出声明位置在 `afterMarker` 之后、且缩进**正好 4 空格**（= 类体成员，不是函数内局部变量）
     * 的 `val` / `private val` / `var` 属性名。
     */
    private fun propertyNamesAfter(text: String, afterMarker: String): List<String> {
        val marker = text.indexOf(afterMarker)
        if (marker < 0) return emptyList()
        val regex = Regex("""^ {4}(?:private\s+)?(?:val|var)\s+(\w+)\s*[:=]""", RegexOption.MULTILINE)
        return regex.findAll(text)
            .filter { it.range.first > marker }
            .map { it.groupValues[1] }
            .toList()
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
