package com.ashkb.app.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.2.15（外部审查批次 3）的结构守卫：U-1 / U-2 / U-3 / U-4 / U-5 五项**逐条钉住**。
 *
 * ### 为什么是静态扫描
 * 这五项改的都是「Compose 里长什么样、读屏拿到什么、窄屏下分几列」——
 * 本模块没有 Compose UI 测试依赖，Robolectric 量到的字体宽度是 1.0dp，
 * 语义树与版式断言在 JVM 上都拿不到真实值。能守的只有「写法还在不在」这一层，
 * 真正的那一半要真机（报告 §五 给了验收清单）。
 *
 * ### 它守得住 / 守不住
 * 守得住的是**回退**：单位改回「 分」、血压换回 `cs.error`、阈值又变 `null`、
 * `loadFailed` 被删、`GridCells` 退回 `Fixed(2)`、`rememberSaveable` 退回 `remember`、
 * 清空条件退回「百分比能解析就清」。这些都是有人「顺手简化」时最容易发生的那一类。
 * 守不住的是：这些写法在运行时是否真的产生了预期效果。
 */
class UiUxBatch3GuardTest {

    private val sourceRoot: File =
        listOf(File("src/main/java"), File("app/src/main/java")).firstOrNull { it.isDirectory }
            ?: error("未找到主源码目录（工作目录=${File(".").absolutePath}）")

    private fun src(relative: String): String = File(sourceRoot, relative).readText()

    /** 归一化行尾与空白：本仓库 `.gitattributes` 是 `* -text`，不同文件是 CRLF / LF 混合的。 */
    private fun norm(text: String): String = text.replace("\r\n", "\n").replace(Regex("[ \t]+"), " ")

    /**
     * 去掉整行注释后再断言「某个写法不存在」。
     *
     * 这几处的改动注释里都引用了**旧写法**（例如「原来 `GridCells.Fixed(2)` 会…」），
     * 直接在全文里找会命中注释而不是代码。负向断言必须是"代码里没有"，不能是"全文没有"。
     */
    private fun code(text: String): String =
        norm(text).lines().filterNot { it.trimStart().startsWith("//") }.joinToString("\n")

    // ================= U-4：序列口径（单位 / 颜色 / 阈值） =================

    @Test
    fun `疼痛评分的单位不能是运动分钟`() {
        val body = norm(src("com/ashkb/app/ui/report/ReportScreen.kt"))
        val pain = body.substringAfter("MiniSeries(\n key = \"pain\"").take(400)
        assertTrue("必须能找到 pain 序列的定义（键名或位置变了就更新本守卫）", pain.isNotEmpty())
        assertFalse(
            "疼痛是 0–10 分，用 `exercise_minute_suffix`（\" 分\"）会被读成运动分钟数——" +
                "这正是外部审查 §U-4 指出的原型缺陷",
            pain.contains("exercise_minute_suffix"),
        )
        assertTrue(
            "单位必须显式写成 /10，与概览页 `String.format(\"%.1f/10\")` 的口径一致",
            pain.contains("unit = \"/10\""),
        )
    }

    @Test
    fun `疼痛序列要传严重度阈值，否则超阈值点没有任何提示`() {
        val body = norm(src("com/ashkb/app/ui/report/ReportScreen.kt"))
        val pain = body.substringAfter("MiniSeries(\n key = \"pain\"").take(400)
        assertTrue(
            "threshold 为 null 时图上不画阈值线，7 分以上与 2 分看起来一样",
            pain.contains("threshold = ClinicalThresholds.PAIN_SEVERE.toFloat()"),
        )
    }

    @Test
    fun `血压整条序列不能用错误色`() {
        val body = norm(src("com/ashkb/app/ui/report/ReportScreen.kt"))
        val bp = body.substringAfter("MiniSeries(\n key = \"bp\"").take(400)
        assertFalse(
            "`accent = cs.error` 让每一个正常血压点都是报错色——" +
                "危险色只能由「超阈值」触发，不能由「这是一条血压曲线」触发",
            bp.contains("accent = cs.error"),
        )
        assertTrue(
            "血压必须落在中性序列色上（与心率同一档 tertiary）",
            bp.contains("accent = cs.tertiary"),
        )
        assertTrue(
            "并且要显式传收缩压阈值：不传 threshold 就没有阈值线，用户看不出哪次数值是高的",
            bp.contains("threshold = ClinicalThresholds.BP_HIGH_SYS.toFloat()"),
        )
    }

    // ================= U-3：错误态建模（loading / content / error / stale） =================

    @Test
    fun `统计刷新失败必须单独建模，不能只发一条一次性提示`() {
        val vm = norm(src("com/ashkb/app/ui/report/ReportViewModel.kt"))
        assertTrue(
            "`_message` 是一次性 Snackbar，页面读不到它——失败后页面会永远停在「统计加载中…」",
            vm.contains("val loadFailed: StateFlow<String?>"),
        )
        val refresh = vm.substringAfter("fun refresh()").take(900)
        assertTrue("成功路径要把失败态清掉", refresh.contains("_loadFailed.value = null"))
        assertTrue("失败路径要把它置上", refresh.contains("_loadFailed.value = text"))
    }

    @Test
    fun `首次失败要有内联错误块和重试入口`() {
        val screen = norm(src("com/ashkb/app/ui/report/ReportScreen.kt"))
        assertTrue("错误块本身", screen.contains("private fun StatsErrorBlock("))
        assertTrue("必须有重试按钮，否则用户只能杀进程重进", screen.contains("R.string.common_retry"))
        val tab = screen.substringAfter("private fun OverviewTab(").take(1200)
        assertTrue(
            "首次失败（o == null 且 loadFailed != null）不能走加载骨架——那正是原来「转圈转到天荒地老」的写法",
            tab.contains("if (failure != null) {") &&
                tab.contains("StatsErrorBlock(message = failure, onRetry = vm::refresh"),
        )
    }

    @Test
    fun `已有数据时刷新失败要保留旧数据并标注可能过期`() {
        val screen = norm(src("com/ashkb/app/ui/report/ReportScreen.kt"))
        assertTrue("过期提示组件", screen.contains("private fun StatsStaleNote("))
        assertTrue("文案要明说「可能不是最新的」", screen.contains("R.string.report_stats_stale"))
        // 概览页的失败态在 OverviewTab（委派属性取成局部值 failure），趋势页在 TrendsPage。
        assertTrue(
            "趋势页与概览页都要挂这条提示，漏掉一页就等于那页的旧数字没有任何过期标记",
            screen.indexOf("StatsStaleNote(onRetry = vm::refresh)") > 0 &&
                screen.indexOf("StatsStaleNote(onRetry = onRetry)") > 0,
        )
    }

    // ================= U-1：无障碍语义 =================

    @Test
    fun `输入框要把 label 与错误并成一个读屏节点`() {
        val forms = norm(src("com/ashkb/app/ui/components/Forms.kt"))
        assertTrue(
            "label 是兄弟节点，不 mergeDescendants 的话焦点落在输入框时读屏读不到「这个框要填什么」",
            forms.contains(".semantics(mergeDescendants = true)"),
        )
        assertTrue(
            "`isError` 原先只改边框颜色，读屏完全察觉不到填错了",
            forms.contains("if (isError) error(invalidText)"),
        )
    }

    @Test
    fun `错误文案要在出现时播报`() {
        val forms = norm(src("com/ashkb/app/ui/components/Forms.kt"))
        assertTrue(
            "错误原因在 supportingText 里，不标 liveRegion 的话它出现时不会播报，" +
                "用户必须自己再滑到那一行才知道哪里不对",
            forms.contains("liveRegion = LiveRegionMode.Polite"),
        )
    }

    @Test
    fun `底部 dock 与分段控件要声明自己是 Tab 且带选中态`() {
        val shell = norm(src("com/ashkb/app/ui/AppShell.kt"))
        assertTrue("dock 必须用 selectable 而不是 clickable", shell.contains(".selectable("))
        assertTrue("角色声明成 Tab，读屏才知道这是一组标签页", shell.contains("role = Role.Tab"))
        assertTrue("必须把选中态交给语义树", shell.contains("selected = selected"))
        assertFalse(
            "`clickable` 不带组内选中语义——退回它就等于把整组标签页变成一排普通按钮",
            shell.contains(".clickable(enabled = !selected)"),
        )

        val chrome = norm(src("com/ashkb/app/ui/components/PageChrome.kt"))
        assertTrue(
            "同一类缺陷（分段控件）顺手一起修：它也自绘选中态、也没有语义",
            chrome.contains(".selectable(selected = on, role = Role.Tab)"),
        )
    }

    // ================= U-2：窄屏 / 大字号版式 =================

    @Test
    fun `趋势小多图不能硬编码两列`() {
        val screen = code(src("com/ashkb/app/ui/report/ReportScreen.kt"))
        assertTrue(
            "320dp 屏两列时每格仅 138dp，标题与刻度会互相压——必须交给 Adaptive 按最小宽自决",
            screen.contains("GridCells.Adaptive(minSize = minCell)"),
        )
        assertFalse("硬编码两列是要消除的那个写法", screen.contains("GridCells.Fixed(2)"))
        assertTrue(
            "最小宽必须按 fontScale 缩放（大字号下每格要更宽才能放下同样的字）",
            screen.contains("trendGridMinCell(LocalDensity.current.fontScale)"),
        )
    }

    @Test
    fun `服药操作按钮要能折行`() {
        val today = norm(src("com/ashkb/app/ui/today/TodayScreen.kt"))
        assertTrue(
            "「服用 / 跳过 / 顺延」在窄屏 + 大字号下会被挤出屏幕，必须用 FlowRow 折行",
            today.contains("FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm))"),
        )
    }

    @Test
    fun `已知会溢出屏幕的弹层正文必须可滚`() {
        // 报告 §U-2 点名的四类：提醒设置、化验详情、紧急联系人、运动打卡
        val cases = mapOf(
            "com/ashkb/app/ui/me/ReminderSettingsSheet.kt" to "提醒设置（4 组开关 + 免打扰 + 久坐 + BASDAI 周期）",
            "com/ashkb/app/ui/checkup/CheckupLabImaging.kt" to "化验详情（一次抽血十几项很常见）",
            "com/ashkb/app/ui/emergency/EmergencyScreen.kt" to "紧急联系人（4 个输入框 + 2 个开关）",
            "com/ashkb/app/ui/exercise/ExerciseScreen.kt" to "运动打卡（时长 + 强度 + 保存）",
        )
        cases.forEach { (path, label) ->
            val body = norm(src(path))
            assertTrue(
                "$label 的正文加了 verticalScroll 才能在大字号 / 键盘弹起时够到底部按钮",
                body.contains(".verticalScroll(rememberScrollState())"),
            )
        }
    }

    @Test
    fun `带输入框的弹层要避让键盘`() {
        // 只有含输入框的两处需要 IME 避让：提醒设置里只有 Switch/FilterChip/RadioButton，
        // 时间是系统 TimePickerDialog，所以那边只加滚动、不加 imePadding 是对的。
        listOf(
            "com/ashkb/app/ui/emergency/EmergencyScreen.kt",
            "com/ashkb/app/ui/exercise/ExerciseScreen.kt",
        ).forEach { path ->
            val body = norm(src(path))
            assertTrue(
                "$path 的弹层有输入框：键盘弹起后不 imePadding，保存键会被顶到可视区外",
                body.contains(".imePadding()"),
            )
        }
    }

    // ================= U-5：容差页输入不被误清 =================

    @Test
    fun `清空输入的条件必须是「父级接收了」，不是「百分比能解析」`() {
        val body = norm(src("com/ashkb/app/ui/me/LabToleranceScreen.kt"))
        assertTrue(
            "回调必须返回是否接收——这正是原来丢失的契约：校验失败时父级什么也没加进去",
            body.contains("private fun ToleranceAddRow(onAdd: (String, Float?) -> Boolean)"),
        )
        assertTrue(
            "清空必须挂在返回值上",
            body.contains("if (onAdd(name, parsed)) {"),
        )
        assertFalse(
            "退回「parsed != null 就清空」= 名称留空 / 百分比越界时把用户刚填的两格一起擦掉",
            body.contains("if (parsed != null) {"),
        )
    }

    @Test
    fun `容差页两格要用 rememberSaveable`() {
        val body = norm(src("com/ashkb/app/ui/me/LabToleranceScreen.kt"))
        val row = body.substringAfter("private fun ToleranceAddRow(").take(900)
        assertTrue(
            "这一页的使用姿势是「对照化验单一项项慢慢录」，转屏 / 分屏 / 进程回收后丢稿代价很高",
            row.contains("var name by rememberSaveable"),
        )
        assertTrue("同上", row.contains("var percent by rememberSaveable"))
    }
}
