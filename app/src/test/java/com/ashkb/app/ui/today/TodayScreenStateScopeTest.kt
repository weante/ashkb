package com.ashkb.app.ui.today

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.0.87（批次 12）：`TodayScreen` 根级状态的**作用域守卫**（纯静态扫描，随 `testDebugUnitTest` 一起跑）。
 *
 * ### 为什么用静态扫描
 * 本模块**没有 Compose UI 测试依赖**（无 `androidx.compose.ui:ui-test-junit4`，也没有
 * `createComposeRule` 的可用环境），所以「根级还挂着几条 Flow」这类结构事实无法用组合测试钉住。
 * 退而求其次但不含糊：把主源码当文本读，按**唯一无歧义的行模式**判定（见 [rootIndex] /
 * [flowNamesAt]），并同时断言「确实扫到了、确实找到了根函数」——路径写错导致一个文件都没扫到时
 * 必须**大声失败**，而不是静默通过（同 `RegexLiteralGuardTest` 的做法）。
 *
 * ### 它挡的是哪一类错
 * 本批是纯重构（v1.0.85 拆 `BackupScreen` 时的同一套约定）：某个状态只被一个 section 渲染，
 * 就必须在那个 section 里 collect。这个约定靠人守迟早会松——下一批加个字段时，
 * 顺手把 `vm.xxx.collectAsStateWithLifecycle()` 写回根级是完全自然的动作，
 * 而代价（每次进度 tick 重组整屏）**不会**以任何形式表现出来。本用例把这条约定变成红灯。
 *
 * ### 白名单为什么是这三条（每条都有「不能下移」的硬理由）
 *  · `profile`：Hero / 极简横幅 / 补记卡空态三处共用，`isMinimal` 也由它派生；
 *  · `todayDate`：Hero 标题 / 漏服判定 / 补记卡的「昨天」必须是同一个「今天」（批次 9 的缺陷）；
 *  · `items`：药品列表的**内容**，而 `LazyListScope.item { }` / `items(...) { }` 只在列表作用域
 *    解析得到，`collectAsStateWithLifecycle` 又只在组合上下文可用——「既收 Flow 又往宿主列表
 *    发 item」的写法在 Compose 里编译不过（两条路都实测过，见 TodayScreen 的类注释）。
 * 四个弹层 target（skipTarget / injTarget / postponeTarget / missedGuideTarget）留在根级，
 * 但它们是 `remember` 槽位、**不是 Flow**；本用例单独把它们钉成「只能有这四个」。
 * 其余六条（alerts / symptomRecorded / exerciseDone / minimalPrompt / yesterdayPending /
 * 派生值 yesterdayDate）一律下移到对应 section。
 *
 * ### 已知局限（明说）
 * ① 只认 `val x by vm.<flow>.collectAsStateWithLifecycle()` 这一种写法：改成
 *    `collectAsState()` / `.collect { }` / 显式 `StateFlow` 收集都会**漏检**。
 *    因此下面钉的是「根函数里出现的是哪几个 Flow 名」这一**白名单**，而不是只数条数——
 *    换个写法混进来的流仍会被白名单抓到（名字是新的）。
 * ② 它证明的是「代码里怎么写的」，不是「运行时的重组范围」。运行时那一半由 Compose 自身的
 *    快照作用域保证：`collectAsStateWithLifecycle` 的读点在哪，重组就限定在哪。
 */
class TodayScreenStateScopeTest {

    /** 主源码路径：单测工作目录是模块根（`app/`），两种布局都试一次。 */
    private val sourceFile: File =
        listOf(
            File("src/main/java/com/ashkb/app/ui/today/TodayScreen.kt"),
            File("app/src/main/java/com/ashkb/app/ui/today/TodayScreen.kt"),
        ).firstOrNull { it.isFile }
            ?: error("未找到 TodayScreen.kt（工作目录=${File(".").absolutePath}）")

    private val source: String = sourceFile.readText()

    /** 去掉行注释：本文件里解释性的代码片段（如 `if (minimalPrompt)`）都写在注释里，不能当代码扫。 */
    private val code: String = source.lines().joinToString("\n") { it.substringBefore("//") }

    /** 根级白名单——改动这条清单等于改动本页的重组边界，必须同时改注释说明理由。 */
    private val rootAllowList = listOf("profile", "todayDate", "items")

    /** 本批**必须**下移到 section 的 Flow（每条都要能在全文件里找到它的新收集点）。 */
    private val movedDown = listOf(
        "alerts", "symptomRecorded", "exerciseDone", "minimalPrompt", "yesterdayPending",
    )

    /**
     * 根级正文的起点。锚点是**唯一无歧义**的一行
     * `val x by vm.y.collectAsStateWithLifecycle()`（同一文本在一个文件里只出现一次），
     * 而不是做大括号配对——Kotlin 的 `${…}` 字符串模板会让朴素的花括号计数失真。
     */
    private fun rootIndex(pattern: String): Int {
        val i = code.indexOf(pattern)
        assertTrue("根函数的锚点没找到（守卫形同虚设）：$pattern", i >= 0)
        return i
    }

    /**
     * 根级正文的**终点**：紧随其后的下一个顶层函数（[RefreshDateOnResume]）的声明处。
     *
     * 为什么需要它：若从根锚点一直扫到文件末尾，后面那些 section 里**下移之后**的收集点
     * 会被当成「根级还在收」——守卫就会永远红灯（本用例第一版正是这么错的）。
     */
    private fun rootEnd(from: Int): Int {
        val i = code.indexOf("private fun RefreshDateOnResume(", from)
        assertTrue("根函数的终点锚点没找到（守卫形同虚设）", i >= 0)
        return i
    }

    /** 根级正文（供「不得出现」类断言用）。 */
    private fun rootBody(): String {
        val start = rootIndex("val profile by vm.profile.collectAsStateWithLifecycle()")
        return code.substring(start, rootEnd(start))
    }

    /**
     * 某个下标之后，根级绑定的**本地名字**（按文本顺序）。
     *
     * 用本地名而不是流名：本页 `vm.today` 绑定到 `items`（语义是「今天的药品项」）、
     * `vm.yesterdayPending` 绑定到 `pending`——守卫要钉的是「根级留下了哪几个值」，
     * 局部变量名才是读代码的人看到的东西。
     */
    private fun flowNamesAt(from: Int): List<String> =
        Regex("""val (\w+) by vm\.(\w+)\.collectAsStateWithLifecycle\(\)""")
            .findAll(code.substring(from, rootEnd(from)))
            .map { it.groupValues[1] }
            .toList()

    @Test
    fun `根级只剩白名单里的三条`() {
        val root = rootIndex("val profile by vm.profile.collectAsStateWithLifecycle()")
        val names = flowNamesAt(root)

        assertEquals(
            "根级 Flow 白名单 = profile / todayDate / items（理由见类注释）。" +
                "其余状态请收进对应 section——实际扫到：$names",
            rootAllowList,
            names,
        )
    }

    /** 白名单每条都有「不能下移」的硬理由，且这些理由在代码里看得见。 */
    @Test
    fun `白名单三条各有跨 section 或列表层的读点`() {
        val body = rootBody()
        assertTrue("profile 派生 isMinimal", body.contains("val isMinimal = profile?.uiMode"))
        assertTrue("isMinimal 喂给极简横幅", body.contains("visible = isMinimal"))
        assertTrue("isMinimal 喂给快捷入口行（否则运动入口不会隐藏）", body.contains("isMinimal = isMinimal"))
        assertTrue("profile 是否为空决定空态文案，必须传给药品列表", body.contains("profileMissing = profile == null"))
        assertTrue("todayDate 喂给药品列表（漏服判定要求同一个「今天」）", body.contains("todayDate = todayDate"))
        assertTrue(
            "todayDate 喂给补记卡（「昨天」由它推导）",
            body.contains("yesterdayPendingSection(vm = vm, todayDate = todayDate)"),
        )
        assertTrue("items 喂给药品列表", body.contains("items = items,"))
        assertTrue("items 只用于 Hero 的两个计数", body.contains("pendingCount = items.count"))
    }

    /** 下移的六条（含派生值）不得再出现在根级，且各自确实被某个 section 收了。 */
    @Test
    fun `下移的状态不再由根级收集`() {
        val rootNames = flowNamesAt(rootIndex("val profile by vm.profile.collectAsStateWithLifecycle()")).toSet()

        val leaked = (movedDown + listOf("yesterdayDate")).filter { it in rootNames }
        assertTrue("这些必须留在各自 section 内收集，根级不得出现：$leaked", leaked.isEmpty())

        for (flow in movedDown) {
            assertTrue(
                "根级没有了，但全文件也找不到 $flow 的收集点——功能被删了，不是被下移了",
                code.contains("vm.$flow.collectAsStateWithLifecycle()"),
            )
        }
        assertTrue(
            "yesterdayDate 这个派生值已下移到补记卡内部（yesterdayIso(入参今天)），根级不该再有 remember 包装",
            !code.contains("val yesterdayDate"),
        )
    }

    /**
     * 四个弹层 target 是**唯一**留在根级的输入状态，且必须只留这四个。
     *
     * 为什么它们下不去：写入点在卡片回调里（卡片在列表作用域上创建），而 `remember` 只能在
     * 组合上下文里调用——两者无法同处一个函数（详见 TodayScreen 类注释）。
     * 为什么仍然可接受：它们是 `remember` 槽位、**不是 Flow**，不会自己发射。
     */
    @Test
    fun `根级只留四个弹层 target`() {
        val body = rootBody()

        // state 变量名 → TodayDialogTargets 里的字段名：两条链都要在根级正文里出现，
        // 否则「卡片点了没反应」或「弹层永远打不开」这两类断链都不会被任何测试发现
        val wiring = mapOf(
            "skipTarget" to "skip = skipTarget",
            "injTarget" to "injSite = injTarget",
            "postponeTarget" to "postpone = postponeTarget",
            "missedGuideTarget" to "missedGuide = missedGuideTarget",
        )
        for ((state, passed) in wiring) {
            assertTrue("$state 必须留在根级（写入点在卡片回调里）", body.contains("var $state by remember"))
            assertTrue("$state 的写入点不该消失（卡片回调里赋值）", body.contains("$state = it"))
            assertTrue("$state 必须传进 TodayDialogs，否则对应弹层永远打不开", body.contains(passed))
        }
        assertEquals(
            "根级 `mutableStateOf` 只能有这四个弹层 target（多出来的就是被顺手加回根级的状态）",
            4,
            Regex("""var \w+ by remember \{ mutableStateOf""").findAll(body).count(),
        )
    }

    /** 日期流必须只有一条真相：section 里不得再收一条 todayDate（批次 9 修的正是「两个今天」）。 */
    @Test
    fun `日期流全页只收一次`() {
        assertEquals(
            "todayDate 被收了不止一次——同屏会出现两个「今天」（批次 9 的缺陷）",
            1,
            Regex("""vm\.todayDate\.collectAsStateWithLifecycle\(\)""").findAll(code).count(),
        )
    }

    /** `collectAsStateWithLifecycle` 一律无参：初值取 VM 里 `stateIn` 的那一份，不另写一份。 */
    @Test
    fun `不用显式 initialValue 覆盖初值`() {
        assertTrue(
            "本页所有 collectAsStateWithLifecycle 都必须用无参重载：显式 initialValue 等于在同一条流上" +
                "再声明一份初值（todayDate 的初值就是 DateProvider 的「今天」，重写会引入第三个日期源）",
            !code.contains("collectAsStateWithLifecycle(initialValue"),
        )
    }

    /** 列表项必须仍由宿主 `LazyColumn` 发出（虚拟化与 key 都不能丢）。 */
    @Test
    fun `药品列表仍走宿主的 LazyColumn`() {
        assertTrue(
            "药品卡片必须是宿主 LazyColumn 的 item（`items(items, key = …)`），不能在 section 里另起 LazyColumn",
            code.contains("items(items, key = { it.med.id to it.slotKey })"),
        )
        assertEquals(
            "全文件只允许一个 LazyColumn（嵌套滚动容器在无界高度下布局失败）",
            1,
            Regex("""\bLazyColumn\(""").findAll(code).count(),
        )
    }
}
