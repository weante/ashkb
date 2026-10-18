package com.ashkb.app.ui

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **全仓 ViewModel 的「init 声明顺序」守卫**（v1.2.10，2026-10-18 真机间歇崩溃的回归防线）。
 *
 * ## 它守的是什么
 *
 * Kotlin 按**声明顺序**初始化属性。如果一个 `ViewModel` 的 `init { … }` 里写了
 * `viewModelScope.launch { … }`，那么在主线程上 `launch` 会**同步内联**到协程体的第一个真正挂起点
 * （通常是 `withContext(Dispatchers.IO)`），在那里把协程派发给 IO 线程后立即返回；主线程继续跑
 * 构造器里剩下的属性初始化，而 IO 线程**可以并发**跑到协程体里引用某个「还没轮到赋值」的字段。
 * 两条线程之间没有 happens-before 边 ⇒ 读到 null。
 *
 * 这不是理论：2026-10-18 真机 release（R8 混淆）包冷启动偶发闪退，首页浮出「上次异常」遮罩。
 * `mapping.txt` 把崩溃帧钉到 `TodayViewModel$refreshYesterdayPending$2.invokeSuspend:255`，
 * 即 `_yesterdayPending.value = PendingDoses.unsettledOn(…)`，NPE 接收者就是当时仍为 null 的
 * `_yesterdayPending`（`dexdump -d` 逐指令复核确认）。而它**从 v1.0.74 起就一直声明在 `init` 之后**，
 * 只是熄屏 / Doze 下 IO 派发被推迟、构造窗口拉长，才变得可复现。
 *
 * 逐点回归见 `ui/today/TodayViewModelInitOrderTest.kt`（针对 TodayViewModel 的三条具名断言）；
 * **本类做全仓扫描**，把同类形状一次性锁死，避免下次新增 ViewModel / 新增字段时再踩同一个坑。
 *
 * ## 为什么用静态源码断言
 *
 * 竞态窗口只有「构造器跑到 `withContext` 之后的那些初始化」这一小段，单测里几乎无法稳定复现
 * （真机上也只是熄屏冷启动才偶发）。声明顺序是**源码形状**，读文件断言即可 100% 锁住——与
 * `ui/me/MedRouteFrequencyGuardTest.kt` 的读源文件断言同一路数。
 *
 * ⚠️ 规则故意做得很"钝"：只要求「`init` 块里不出现任何声明在它之后的类成员名」，不管这个引用
 * 出现在什么上下文里。今天全仓 4 个带 `init` 的 ViewModel 都满足；将来若出现合法的例外
 * （例如字段虽在 init 之后声明、但只在 `init` 里注册的监听器回调中才被引用），请**改代码**
 * 而不是放宽这条断言——那条路径同样有构造期竞态。
 */
class ViewModelInitOrderGuardTest {

    /** `app/src/main` 下所有 `*ViewModel.kt`（相对仓库根）。 */
    private val viewModels: List<String> by lazy {
        val root = repoRoot()
        val dir = java.io.File(root, "app/src/main/java/com/ashkb/app")
        assertTrue("找不到 app/src/main/java/com/ashkb/app（仓库根 = $root）", dir.isDirectory)
        dir.walkTopDown()
            .filter { it.isFile && it.name.endsWith("ViewModel.kt") }
            .map { it.relativeTo(root).path.replace('\\', '/') }
            .sorted()
            .toList()
    }

    @Test
    fun `每个 ViewModel 的 init 块都不得引用声明在它之后的属性`() {
        val offenders = mutableListOf<String>()
        val withInit = mutableListOf<String>()

        for (path in viewModels) {
            val source = java.io.File(repoRoot(), path).readText()
            val initMarker = "    init {"
            val initAt = source.indexOf(initMarker)
            if (initAt < 0) continue
            withInit += path

            val initBody = blockBody(source, initMarker)
            assertTrue("$path 的 init 块没取到（守卫失焦）", initBody.isNotBlank())

            for (name in lateMemberNames(source, initAt)) {
                if (Regex("\\b${Regex.escape(name)}\\b").containsMatchIn(initBody)) {
                    offenders += "$path: init 块引用了声明在它之后的 `$name`"
                }
            }
        }

        assertTrue(
            "扫描到的 ViewModel 太少（只找到 ${viewModels.size} 个）——守卫的目录假设可能已失效",
            viewModels.size >= 10,
        )
        assertTrue(
            "一个带 init 块的 ViewModel 都没扫到——守卫的 `    init {` 标记假设可能已失效",
            withInit.isNotEmpty(),
        )
        assertTrue(
            "下列成员在构造期尚未赋值，却被 init 块引用（IO 协程可能与构造器并发跑到这里）——" +
                "把它们上移到 init 之前，或把 init 里的引用移出构造期：\n" +
                offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
    }

    @Test
    fun `已知的四个带 init 的 ViewModel 仍在扫描范围内`() {
        // 具名钉住：这四个是今天全仓唯一带 init 块的 ViewModel。将来它们被改名 / 搬家时，
        // 本守卫必须报错（否则会静默地"扫不到"而不是"通过"）。
        val expected = listOf(
            "app/src/main/java/com/ashkb/app/ui/backup/BackupViewModel.kt",
            "app/src/main/java/com/ashkb/app/ui/exercise/ExerciseViewModel.kt",
            "app/src/main/java/com/ashkb/app/ui/symptom/SymptomViewModel.kt",
            "app/src/main/java/com/ashkb/app/ui/today/TodayViewModel.kt",
        )
        val missing = expected.filterNot { it in viewModels }
        assertTrue("下列 ViewModel 不在扫描范围内：$missing", missing.isEmpty())
    }

    @Test
    fun `SymptomViewModel 的今天字段仍声明在 init 之前`() {
        // `init` 里注册的日期回调**不是**同步执行的，所以它引用 `_selectedDate` 是安全的
        // （这是与 TodayViewModel 的关键差别：那里是 `viewModelScope.launch`，会同步内联）；
        // 但 `_selectedDate` 的初值表达式 `dateProvider.today.value` 仍在构造期求值，
        // 因此它必须声明在 init 之前。这条把「安全的那一半」也钉住。
        val source = java.io.File(
            repoRoot(),
            "app/src/main/java/com/ashkb/app/ui/symptom/SymptomViewModel.kt",
        ).readText()
        val initAt = source.indexOf("    init {")
        assertTrue("SymptomViewModel 的 init 块不见了", initAt >= 0)
        val selectedDateAt = source.indexOf("private val _selectedDate")
        assertTrue("_selectedDate 必须声明在 init 之前", selectedDateAt in 0 until initAt)
    }

    // ==================== 工具 ====================

    /** 从 `header` 起取到同级缩进的下一个 `    }`（`init` 块的闭合括号）为止的块体。 */
    private fun blockBody(text: String, header: String): String {
        val start = text.indexOf(header)
        if (start < 0) return ""
        val from = start + header.length
        val end = text.indexOf("\n    }", from)
        return if (end < 0) text.substring(from) else text.substring(from, end)
    }

    /**
     * 找出**声明位置在 `init` 之后**、缩进正好 4 空格（= 类体成员，不是函数内局部变量）
     * 的 `val` / `private val` / `var` 名字。
     */
    private fun lateMemberNames(source: String, initAt: Int): List<String> =
        Regex("""^ {4}(?:private\s+)?(?:val|var)\s+(\w+)\s*[:=]""", RegexOption.MULTILINE)
            .findAll(source)
            .filter { it.range.first > initAt }
            .map { it.groupValues[1] }
            .toList()

    /** 上溯找 `settings.gradle.kts` 定位仓库根（风格同 `ui/me/MedRouteFrequencyGuardTest.kt`）。 */
    private fun repoRoot(): java.io.File {
        var dir: java.io.File? = java.io.File(".").absoluteFile
        while (dir != null) {
            if (java.io.File(dir, "settings.gradle.kts").isFile) return dir
            dir = dir.parentFile
        }
        return java.io.File("../").absoluteFile
    }
}
