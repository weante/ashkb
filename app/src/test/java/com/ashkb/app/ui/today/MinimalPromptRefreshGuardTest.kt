package com.ashkb.app.ui.today

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.1.4（批次 20）：**回答极简询问后弹窗必须当场消失**。
 *
 * ### 为什么需要这条守卫
 * 维护者真机反馈「点击其他原因没有反应」——`answerMinimalPrompt` 把答案写进
 * **SharedPreferences**（`MinimalPromptStore`），而 `minimalPrompt` 这条流的输入是
 * `todayDate` 与 `profile`，**都不包含那个 prefs**。于是答案确实写进去了，但**不触发重算**，
 * 弹窗当场不消失；下次进页面才发现"其实点过了"。三个选项共用同一个回调，所以三个都受影响。
 *
 * ### 它证明什么、不证明什么
 * 证明的是「**代码里怎么写的**」：答案写入后必须有一处**主动驱动重算**的输入，且它在 combine 里。
 * 它**不证明**运行时真的重算了（本模块无 Compose UI 测试依赖，也没有 compose 规则可用），
 * 也不证明弹窗视觉上消失。真机表现需人工确认。
 *
 * ### 已知漏检
 * 只认 `combine(..., answeredTick)` 这一种写法。若将来改用 `callbackFlow` / 强制 `stateIn` 重订阅
 * 等方式达成同样效果，这条断言会误报——届时请连同本注释一起更新，**不要直接删测试**。
 */
class MinimalPromptRefreshGuardTest {

    private val sourceRoot: File =
        listOf(File("src/main/java"), File("app/src/main/java")).firstOrNull { it.isDirectory }
            ?: error("未找到主源码目录（工作目录=${File(".").absolutePath}）")

    private val vm: String by lazy {
        val f = File(sourceRoot, "com/ashkb/app/ui/today/TodayViewModel.kt")
        assertTrue("主源码不存在：${f.path}", f.isFile)
        f.readText()
    }

    @Test
    fun `回答询问后有一条输入专门用于驱动弹窗重算`() {
        assertTrue(
            "answerMinimalPrompt 之后必须自增 answeredTick（答案写在 prefs 里，不触发重算）",
            Regex("""fun answerMinimalPrompt[\s\S]*?answeredTick\.value\s*=\s*answeredTick\.value\s*\+""")
                .containsMatchIn(vm),
        )
    }

    @Test
    fun `该输入必须真的参与 minimalPrompt 的 combine`() {
        assertTrue(
            "answeredTick 必须进 combine，否则自增了也没人听——弹窗照样不消失",
            // ⚠️ 不能写 `combine\([^)]*answeredTick`：`[^)]*` 会在 `observeProfile()` 的
            // 那个 `)` 处停下，明明写对了也判红（我第一版就是这么错的）。改为在 combine 的
            // 整个语句范围内找它——用 `[\s\S]*?` 跨行、以 `) {` 收尾界定参数表。
            Regex("""combine\([\s\S]*?\)\s*\{""").find(vm)?.value
                ?.let { it.contains("answeredTick") } == true,
        )
    }

    @Test
    fun `三个选项共用同一回调（不得只修其中一个）`() {
        // 若将来把某个选项单独接一条路径，只有它消失而其余两个仍然"没反应"，
        // 正是这个 bug 的复发形态。断言 UI 侧仍是 forEach 一个回调。
        val screen = File(sourceRoot, "com/ashkb/app/ui/today/TodayScreen.kt").readText()
        assertTrue(
            "MinimalMode.REASONS 必须统一走一个 onClick（只给某个选项单开路径 = 旧 bug 复发）",
            Regex("""MinimalMode\.REASONS\.forEach""").containsMatchIn(screen),
        )
    }
}
