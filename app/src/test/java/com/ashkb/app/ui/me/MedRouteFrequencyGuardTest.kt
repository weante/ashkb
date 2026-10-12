package com.ashkb.app.ui.me

import com.ashkb.app.data.entity.MedFrequency
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「口服 + 每两周」被 UI 禁止的守卫（v1.2.10，审查裁决项 3）。
 *
 * ## 它守的是什么
 *
 * 排程侧的真实行为（`domain/ScheduleCalc.kt:113-115`）：
 * ```
 * isCycleGatedBy(freq, route) = CUSTOM || MONTHLY || (Q2W && route == "injection")
 * ```
 * Q2W 只在**注射**时按周期出卡；口服 Q2W 走的是「每日」。而 `cycleAnchorVisible`
 * 对口服 Q2W 也不给周期入口。于是一个标着「每两周」的口服药，
 * UI 上无处填周期、排程侧却天天出卡——14 天吃 **14 剂**而不是 7 剂
 * （口服允许一天多个时刻，见 `MedScheduleSection` 的 `single = route != "oral"`）。
 *
 * 本版处置是 **A 方案：UI 层禁止**，不动 `ScheduleCalc` 的语义
 * （那条语义被 `ScheduleCalcCycleTest.kt:113-119`「口服 每两周 仍按每日出卡」钉住）。
 *
 * ## 为什么这两条要一起测
 *
 * `routeVisible` 决定「chips 画不画」，但 chips 过滤**挡不住**「先选注射 + Q2W、
 * 再切回口服」——此时 `frequency.value` 仍是 Q2W。所以配套有两处：
 * ① `MedScheduleSection` 的过滤里保留 `|| f == frequency.value`（逃生口，否则编辑
 * 存量药单时频次区一片空白、看不出它现在是什么）；
 * ② `MedEditScreen.onPrimary()` 用 `!routeVisible(...)` 拦提交并亮 `routeFrequencyError`。
 *
 * 只测 ① 会漏掉「能保存」；只测 ② 会漏掉「显示不出来的值会被静默顶掉」。
 */
class MedRouteFrequencyGuardTest {

    // ==================== routeVisible：谁可选 ====================

    @Test
    fun `口服不提供每两周`() {
        assertFalse(
            "口服 Q2W 在排程侧就是「每日」，UI 必须禁止（裁决项 3）",
            routeVisible(MedFrequency.Q2W, "oral"),
        )
    }

    @Test
    fun `注射仍提供每两周`() {
        assertTrue(
            "Q2W 是「几周一针」的概念，注射下必须保持可选——本版只禁止口服",
            routeVisible(MedFrequency.Q2W, "injection"),
        )
    }

    @Test
    fun `其余频次在两种途径下都可选`() {
        // 只动 Q2W 一项：其余频次的历史口径一律不变（防止顺手扩大打击面）
        val others = MedFrequency.entries.filter { it != MedFrequency.Q2W }
        for (f in others) {
            assertTrue("$f 在口服下不该被本版挡住", routeVisible(f, "oral"))
            assertTrue("$f 在注射下不该被本版挡住", routeVisible(f, "injection"))
        }
    }

    @Test
    fun `自定义周期与每月一次在口服下仍可选`() {
        // v1.2.4 特意把这两个从「注射专属」放开给口服（维护者报过「自定义周期没法自定义日期」），
        // 本版**不得**把它们一起收回去——它们有 MedCycleSection 这个真实入口，与 Q2W 不同。
        assertTrue(routeVisible(MedFrequency.CUSTOM, "oral"))
        assertTrue(routeVisible(MedFrequency.MONTHLY, "oral"))
    }

    @Test
    fun `hidden 的每八小时不由本判定负责`() {
        // Q8H 的可见性归 `hidden` 管、可编辑性归「当前值保留」管，与 route 无关。
        // 这里只钉住「routeVisible 不去动它」，避免两套过滤逻辑互相打架。
        assertTrue(routeVisible(MedFrequency.Q8H, "oral"))
        assertTrue(routeVisible(MedFrequency.Q8H, "injection"))
    }

    @Test
    fun `匿名的未知途径按不提供处理`() {
        // 途径串来自落库值（`route: String`），历史数据或将来新增的途径都可能有异常值。
        // 「拿不准就不提供 Q2W」是安全侧：Q2W 在非注射下一定是「每日」行为，
        // 放出来只会让用户以为隔周生效。
        assertFalse(routeVisible(MedFrequency.Q2W, ""))
        assertFalse(routeVisible(MedFrequency.Q2W, "topical"))
    }

    // ==================== 接线：过滤 + 逃生口 + 提交拦截 ====================

    private val sectionsSource: String =
        readSource("app/src/main/java/com/ashkb/app/ui/me/MedEditSections.kt")
    private val screenSource: String =
        readSource("app/src/main/java/com/ashkb/app/ui/me/MedEditScreen.kt")

    /**
     * 定位仓库根：测试的工作目录是 `app/`（Gradle 单测的默认 cwd），源码在 `../` 之外。
     * 逐级向上找一个含 `settings.gradle.kts` 的目录，避免把 `../../..` 的层数写死。
     * （与 `ui/today/TodaySupplementCardWiringTest.kt` 同一份理由。）
     */
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

    @Test
    fun `频次 chips 用了 route 过滤`() {
        assertTrue(
            "MedScheduleSection 的 chips 必须过 routeVisible，否则「口服 + 每两周」照样能选（裁决项 3 的 A 方案就是这一条）",
            sectionsSource.contains("routeVisible(f, route.value)"),
        )
    }

    @Test
    fun `当前值仍被保留显示`() {
        // 逃生口：存量药单里可能已有「口服 + Q2W」（旧版本允许选）。若只按 routeVisible 过滤，
        // 编辑它时频次区会**一片空白**——用户看不出它现在是什么，一保存还可能被别的选项顶掉。
        // 这条与 `hidden`（每 8 小时）的既有逃生口是同一个道理，故必须一起守。
        assertTrue(
            "过滤必须保留「当前值」这一条，否则编辑存量「口服 + Q2W」时频次区一片空白",
            sectionsSource.contains("|| f == frequency.value"),
        )
    }

    @Test
    fun `提交处拦住口服加每两周`() {
        // chips 过滤挡不住「先选注射 + Q2W、再切回口服」——此时当前值仍是 Q2W（逃生口让它可见）。
        // 提交处必须再拦一次，否则「禁止」只是没画出来而已，照样能存进库。
        assertTrue(
            "MedEditScreen.onPrimary 必须用 !routeVisible(...) 拦提交",
            screenSource.contains("!routeVisible(frequency.value, route.value)"),
        )
        assertTrue(
            "拦住之后必须亮可见的错误提示，否则用户点了下一步没反应、不知道哪里不对",
            screenSource.contains("routeFrequencyError") &&
                sectionsSource.contains("R.string.med_route_frequency_error"),
        )
    }

    @Test
    fun `改途径或改频次会清掉错误位`() {
        // 与 biwError / mtxError 同款：错误位由「用户的下一次操作」复位。
        // 少了这一条，用户切到注射（组合已合法）后红字还挂着，看起来像还没改好。
        assertTrue(
            "MedRouteDoseSection 切换途径时必须清零 routeError",
            sectionsSource.contains("route.value = \"oral\"; routeError.value = false") &&
                sectionsSource.contains("route.value = \"injection\"; routeError.value = false"),
        )
        assertTrue(
            "点击频次 chips 时必须清零 routeError",
            sectionsSource.contains("routeError.value = false") &&
                sectionsSource.contains("frequency.value = f"),
        )
    }
}
