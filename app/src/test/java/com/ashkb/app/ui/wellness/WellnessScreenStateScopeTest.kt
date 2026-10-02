package com.ashkb.app.ui.wellness

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.0.91（批次 16）：`WellnessScreen` 根级状态的**作用域守卫**（纯静态扫描，随 `testDebugUnitTest` 一起跑）。
 *
 * ### 为什么用静态扫描
 * 本模块**没有 Compose UI 测试依赖**（无 `androidx.compose.ui:ui-test-junit4`，也没有
 * `createComposeRule` 的可用环境），所以「根级还挂着几条 Flow」这类结构事实无法用组合测试钉住。
 * 退而求其次但不含糊：把主源码当文本读，按**唯一无歧义的行模式**判定，并同时断言
 * 「确实扫到了、确实找到了根函数」——路径写错导致一个文件都没扫到时必须**大声失败**，
 * 而不是静默通过（同 [TodayScreenStateScopeTest] / `RegexLiteralGuardTest` 的做法）。
 *
 * ### 它挡的是哪一类错
 * 本批是纯重构（同一套约定已用在 v1.0.85 的 `BackupScreen` 与 v1.0.87 的 `TodayScreen`）：
 * 某个状态只被一个 section 渲染，就必须在那个 section 里 collect。这条约定靠人守迟早会松——
 * 下一批加个字段时，顺手把 `vm.xxx.collectAsStateWithLifecycle()` 写回根级是完全自然的动作，
 * 而代价（每次体重录入 / 补剂打卡都重组整屏）**不会**以任何形式表现出来。本用例把这条约定变成红灯。
 *
 * ### 三个 section 文件的分工（本用例逐条钉住）
 *  · `WellnessScreen.kt`——根骨架 + 各 section 的槽位登记 + 补剂行 / 合并时间表 + 四张内容卡
 *    + 全部表单弹层（弹层留在本文件是 detekt 基线所迫，理由见主源码 `VitalsHero` 的注释）；
 *  · `WellnessVitalsSections.kt`——体征 section 的取数层与体重卡 / 身体成分卡（各自收自己的 Flow）；
 *  · `WellnessSheetState.kt`——「当前打开哪个弹层」的唯一一份状态。
 *
 * ### 已知局限（明说）
 * ① 只认 `val x by vm.<flow>.collectAsStateWithLifecycle()` 这一种写法：改成 `collectAsState()` /
 *    `.collect { }` / 显式 `StateFlow` 收集都会**漏检**。因此钉的是「根函数里出现的是哪几个 Flow 名」
 *    与「VM 里的每条流还找得到收集点」两件事，而不是只数条数——换个写法混进来的流仍会被抓到（名字是新的）。
 * ② 它证明的是「代码里怎么写的」，不是「运行时的重组范围」。运行时那一半由 Compose 自身的快照作用域
 *    保证：`collectAsStateWithLifecycle` 的读点在哪，重组就限定在哪。
 * ③ 它不能证明**没有行为回归**：接线断链（点了没反应）、弹层打不开、宽度被挤成零宽这类问题，
 *    静态扫描看不到。本批为此逐字保留了所有 `R.string.*` 调用点与回调形状，见回报里的等价性论证。
 */
class WellnessScreenStateScopeTest {

    /** 主源码目录：单测工作目录是模块根（`app/`），两种布局都试一次。 */
    private val sourceRoot: File =
        listOf(File("src/main/java"), File("app/src/main/java")).firstOrNull { it.isDirectory }
            ?: error("未找到主源码目录（工作目录=${File(".").absolutePath}）")

    private fun source(relative: String): String {
        val f = File(sourceRoot, relative)
        assertTrue("主源码不存在：${f.path}", f.isFile)
        return f.readText()
    }

    private val screen get() = source("com/ashkb/app/ui/wellness/WellnessScreen.kt")
    private val vitals get() = source("com/ashkb/app/ui/wellness/WellnessVitalsSections.kt")
    private val sheetState get() = source("com/ashkb/app/ui/wellness/WellnessSheetState.kt")
    private val viewModel get() = source("com/ashkb/app/ui/wellness/WellnessViewModel.kt")
    private val sectionFiles get() = listOf(screen, vitals, sheetState)

    /** 去掉行注释：本文件与主源码里解释性的代码片段都写在注释里，不能当代码扫。 */
    private fun code(src: String): String = src.lines().joinToString("\n") { it.substringBefore("//") }

    // ------------------------------------------------------------------
    // 根函数正文的定位：不用大括号配对（Kotlin 的 ${…} 模板会让朴素计数失真），
    // 而是「从 `fun WellnessScreen(` 起，按行扫描到第一行以 `}` 开头的顶层结束行」。
    // 根函数体内的行都有缩进（4 空格以上），其结束 `}` 顶格——这个前提在 ktlint 格式化下成立。
    // ------------------------------------------------------------------
    private fun rootBody(): String {
        val raw = screen
        val start = raw.indexOf("fun WellnessScreen(")
        assertTrue("根函数的声明锚点没找到（守卫形同虚设）：fun WellnessScreen(", start >= 0)
        val bodyStart = raw.indexOf('\n', start)
        assertTrue("根函数声明后没有换行（守卫形同虚设）", bodyStart >= 0)

        val end = findRootFunctionEnd(raw, bodyStart)
        return raw.substring(bodyStart, end)
    }

    private fun findRootFunctionEnd(src: String, from: Int): Int {
        var i = from
        while (i < src.length) {
            val lineEnd = src.indexOf('\n', i).let { if (it < 0) src.length else it }
            val line = src.substring(i, lineEnd)
            if (line.startsWith("}")) return i
            i = lineEnd + 1
        }
        throw AssertionError("根函数的结束行没找到（守卫形同虚设）")
    }

    /** 根级绑定的 Flow 本地名（按文本顺序）。 */
    private fun rootFlows(): List<String> =
        Regex("""val (\w+) by vm\.(\w+)\.collectAsStateWithLifecycle\(\)""")
            .findAll(code(rootBody()))
            .map { it.groupValues[1] }
            .toList()

    /** 根级持有的输入 state 名（`val x = remember { mutableStateOf(…) }`）。 */
    private fun rootStates(): List<String> =
        Regex("""val (\w+) = remember \{ mutableStateOf""")
            .findAll(code(rootBody()))
            .map { it.groupValues[1] }
            .toList()

    // ------------------------------------------------------------------
    // 白名单
    // ------------------------------------------------------------------

    /**
     * 根级**不留任何 Flow**——这是本批的核心结论，改动它等于改动本页的重组边界。
     *
     * 为什么本屏可以一条都不留（而 TodayScreen 必须留三条）：本屏没有任何「多处必须共用同一个值、
     * 各收一份会漂移」的状态。时间窗口（今日体征 / 今日体重 / 今日补剂记录）全在
     * `WellnessViewModel` 里按 `dateProvider.today` 取好，UI 层不参与「今天」的判定；
     * `profile` 是只读档案流，各 section 各收一份不存在两个真相。
     */
    private val rootFlowAllowList = emptyList<String>()

    /**
     * 根级输入 state——只有这四类，且每类都有「不能在 section 里持」的硬理由：
     *  · `sheets`：[WellnessSheetState] 的可见性，七个入口分散在五个 section 里，是**跨 section 的胶水**；
     *    弹层本体在 `WellnessOverlays` 里渲染（`ModalBottomSheet` 是独立窗口，登记成列表 item 的话
     *    一旦滚出视口就会被回收、弹层跟着消失）。
     *  · `detailSup` / `editSup` / `deletingSup`：补剂行的三个档案类动作目标。它们的**写入点是补剂行的回调**，
     *    而补剂行由 `LazyListScope` 扩展在列表作用域里登记——section 里 `remember` 出来的 state，
     *    行回调拿不到。三者与 `WellnessOverlays` 读的是**同一份引用**（写成两份就会「点了没反应」，
     *    即 v1.0.81 的缺陷形状）。
     * 它们**都不是 Flow**：不会自己发射，留着只是四个 `MutableState` 槽位。
     */
    private val rootStateAllowList = listOf("sheets", "detailSup", "editSup", "deletingSup")

    /** 本批**必须**下移到 section 的 Flow（每条都要能在全文件里找到它的新收集点）。 */
    private val movedDown = listOf(
        "vitalsToday", "weightToday", "weightRecent", "profile", "bodyMeasureLatest",
        "supplements", "supplementLogsToday", "medications", "dietProfile", "foodAvoidItems",
    )

    private val sheetFields = listOf(
        "vitals", "weight", "weightManage", "bodyMeasure", "supplementForm", "dietForm", "avoidManage",
    )

    // ------------------------------------------------------------------
    // 断言
    // ------------------------------------------------------------------

    @Test
    fun `根级一条 Flow 都不留`() {
        val flows = rootFlows()
        assertEquals(
            "根级 Flow 白名单为空（理由见类注释：本屏没有必须共用同一份的状态）。" +
                "其余状态请收进对应 section——实际扫到：$flows",
            rootFlowAllowList,
            flows,
        )
    }

    @Test
    fun `根级只留四个 state 槽位`() {
        val states = rootStates()
        assertEquals(
            "根级只允许 sheets / detailSup / editSup / deletingSup 四个 MutableState 槽位" +
                "（理由见类注释）。实际扫到：$states",
            rootStateAllowList,
            states,
        )
        val body = code(rootBody())
        // 七个可见性**必须**收在一份 WellnessSheetState 里，不能再摊成七个布尔（那正是拆分前的形状）
        assertTrue(
            "弹层可见性必须收成一份 WellnessSheetState（拆成七个布尔就是把根级正文又摊回去了）",
            body.contains("remember { mutableStateOf(WellnessSheetState()) }"),
        )
        for (field in sheetFields) {
            assertTrue(
                "WellnessSheetState 少了 $field —— 对应的弹层将永远打不开（或永远关不掉）",
                sheetState.contains("val $field: Boolean = false"),
            )
            assertTrue(
                "$field 没有任何 section 能打开它（有字段没入口 = 功能消失）",
                sectionFiles.any { it.contains("copy($field = true)") },
            )
            assertTrue(
                "$field 没有关闭点（弹层会关不掉）",
                screen.contains("copy($field = false)"),
            )
        }
    }

    /** 下移的十条不得再出现在根级，且各自确实被某个 section 收了（防「删代码冒充下移」）。 */
    @Test
    fun `下移的 Flow 不再由根级收集但全屏仍收得到`() {
        val rootNames = rootFlows().toSet()
        val leaked = movedDown.filter { it in rootNames }
        assertTrue("这些必须留在各自 section 内收集，根级不得出现：$leaked", leaked.isEmpty())

        for (flow in movedDown) {
            assertTrue(
                "根级没有了，但全屏三个文件里也找不到 $flow 的收集点——功能被删了，不是被下移了",
                sectionFiles.any { it.contains("vm.$flow.collectAsStateWithLifecycle()") },
            )
        }
    }

    /** 收集点必须落在 section 文件里：`WellnessViewModel` 的每条流都要有归属。 */
    @Test
    fun `ViewModel 的每条流都有 section 收集它`() {
        val declared = Regex("""val (\w+): StateFlow<""")
            .findAll(code(viewModel))
            .map { it.groupValues[1] }
            .toList()
        assertTrue("VM 里一条 StateFlow 都没扫到（守卫形同虚设）：$declared", declared.size >= 10)

        val collected = sectionFiles.joinToString("\n") { code(it) }
        for (flow in declared) {
            assertTrue(
                "$flow 在界面上没有任何收集点（要么该用没用、要么收集点被改名了）",
                collected.contains("vm.$flow.collectAsStateWithLifecycle()"),
            )
        }
    }

    /** 分流之后仍然**只有一条**补剂行的落点链：三个 target 都必须被弹层消费。 */
    @Test
    fun `补剂行的三个档案类动作仍接到弹层`() {
        val body = code(screen)
        for (target in listOf("detailSup", "editSup", "deletingSup")) {
            assertTrue(
                "$target 必须由根级持有并传给弹层（写入点在列表作用域的回调里）",
                body.contains("$target = $target"),
            )
        }
        assertTrue("补剂详情弹层必须挂在 detailSup 上", body.contains("detailSup.value?.let"))
        assertTrue("补剂编辑表单必须挂在 editSup 上", body.contains("editSup.value?.let"))
        assertTrue("补剂整体删除确认必须挂在 deletingSup 上", body.contains("deletingSup.value?.let"))
    }

    /** `collectAsStateWithLifecycle` 一律无参：初值取 VM 里 `stateIn` 的那一份，不另写一份。 */
    @Test
    fun `不用显式 initialValue 覆盖初值`() {
        for ((name, src) in listOf(
            "WellnessScreen" to screen,
            "WellnessVitalsSections" to vitals,
            "WellnessSheetState" to sheetState,
        )) {
            assertTrue(
                "$name 里的 collectAsStateWithLifecycle 必须用无参重载：显式 initialValue 等于在同一条流上" +
                    "再声明一份初值，两处迟早不一致（本屏 10 条流的初值都在 VM 的 stateIn 里）",
                !code(src).contains("collectAsStateWithLifecycle(initialValue"),
            )
        }
    }

    /**
     * 列表内容仍由宿主 `LazyColumn` 发出：全屏只有一个 `LazyColumn`（嵌套滚动容器在无界高度下布局失败），
     * 且卡片一律以 `item { }` 登记，不能在 section 里另起列表。
     */
    @Test
    fun `列表仍走宿主的 LazyColumn`() {
        assertEquals(
            "全屏只允许一个 LazyColumn（嵌套滚动容器在无界高度下布局失败，且 key 与滚动位置会丢）",
            1,
            Regex("""\bLazyColumn\(""").findAll(sectionFiles.joinToString("\n") { code(it) }).count(),
        )
        assertEquals(
            "stickyHeader 必须还在（三处分组头）",
            3,
            Regex("""stickyHeader \{""").findAll(code(screen) + code(vitals)).count(),
        )
    }

    /**
     * v1.0.91（批次 16）：把「`item { }` 的内容 lambda 是组合上下文」这条**实测结论**钉住。
     *
     * 批次 12 的 `TodayScreen` 注释断言「`item { }` 里不能用 `remember` / `collectAsStateWithLifecycle`」。
     * 本批做了一次一次性编译探针：`LazyListScope.item { }` 的 lambda 里**可以**直接写这两样
     * （编译通过）；真正编译不过的是**另一个**写法——`@Composable LazyListScope.` 扩展在
     * `LazyColumn` 内容 lambda 里被调用（`@Composable invocations can only happen from the context
     * of a @Composable function`）。探针本身已删，结论容易在下一次重构时被记反，故用本用例固化：
     *  · 正向：`item { }` 里出现 `remember` 是**允许**的（`WeightCard` 的 TrendChart points 就在 item 里 remember）；
     *  · 反向：section 扩展函数**不得**标 `@Composable`。
     *
     * ### 它不能覆盖什么
     * 它只证明「代码里没有那种编译不过的写法」，不证明运行时行为；也无法保证未来某个
     * `@Composable LazyListScope.` 扩展不会以**别的方式**被调用（那种情况编译器会直接拦下，不需要本用例）。
     */
    @Test
    fun `item 内容 lambda 可用 remember 而 section 扩展不得标 Composable`() {
        val body = code(screen) + code(vitals)

        // 正向：item 的内容 composable 里确实在用 remember（例如体重卡的趋势图 points）
        assertTrue(
            "item 内容 lambda 是组合上下文，`remember` 应当可用——若这里找不到，说明本批的实测结论被改回去了",
            body.contains("val points = remember(weightList)"),
        )

        // 反向：四个 section 槽位登记函数都必须是「非 @Composable 的 LazyListScope 扩展」
        for (name in listOf("vitalsSections", "nutritionSection", "dietProfileSection", "avoidListSection")) {
            assertTrue(
                "$name 必须是 LazyListScope 扩展（卡片要由宿主 LazyColumn 发出）",
                body.contains("fun LazyListScope.$name("),
            )
            assertTrue(
                "$name 不得标 @Composable——在 LazyColumn 的内容 lambda 里调用 @Composable 扩展编译不过" +
                    "（批次 12 / 16 两次实测）",
                !body.contains("@Composable\nfun LazyListScope.$name(") &&
                    !body.contains("@Composable fun LazyListScope.$name("),
            )
        }
    }
}
