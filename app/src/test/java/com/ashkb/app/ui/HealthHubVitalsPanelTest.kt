package com.ashkb.app.ui

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 健康 Hub 首屏的两处设计稿 §06 ③ 要求（「今日体征」面板 + 紧急卡 danger-soft 瓦片）。
 *
 * 为什么用源码静态断言而不是 Compose 测试：本仓没有 Compose UI 测试基础设施
 * （`androidTest` 只有 `RealDatabaseSchemaTest` 一条 Robolectric 用例），而这两条要求
 * 是**结构性**的（面板在不在、瓦片是不是 danger-soft），静态扫描足以钉住，
 * 也与 `ui/me/MedRouteFrequencyGuardTest.kt`、`ui/ViewModelInitOrderGuardTest.kt` 同一套做法。
 *
 * 这些是**防漂移**断言，不是行为断言：面板的数值渲染由 `StatTile` 的既有测试覆盖，
 * 这里只保证「设计稿要求的那两块 UI 没有被后续重构顺手删掉」。
 */
class HealthHubVitalsPanelTest {

    private val appShellSource: String =
        readSource("app/src/main/java/com/ashkb/app/ui/AppShell.kt")

    /**
     * 定位仓库根：测试的工作目录是 `app/`（Gradle 单测的默认 cwd），源码在 `../` 之外。
     * 逐级向上找一个含 `settings.gradle.kts` 的目录，避免把 `../../..` 的层数写死。
     * （与 `ui/me/MedRouteFrequencyGuardTest.kt:102-111` 同一份理由。）
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

    /** 取 `HealthHub` 函数体：从 `private fun HealthHub(` 到其后的 `VitalsPanel` 文档注释之前。 */
    private fun healthHubBody(): String {
        val start = appShellSource.indexOf("private fun HealthHub(")
        assertTrue("AppShell.kt 里必须还有 HealthHub（设计稿 §06 ③ 的宿主）", start > 0)
        val end = appShellSource.indexOf("private fun VitalsPanel(")
        assertTrue("AppShell.kt 里必须还有 VitalsPanel 的声明", end > start)
        return appShellSource.substring(start, end)
    }

    /** 取指定标题字符串所在的 `NavRow(` 调用块（到该调用结束的 `)` + 换行）。 */
    private fun navRowBlock(titleRes: String): String {
        val body = healthHubBody()
        val titleAt = body.indexOf(titleRes)
        assertTrue("HealthHub 里必须有 $titleRes 那个 NavRow", titleAt > 0)
        val callStart = body.lastIndexOf("NavRow(", titleAt)
        assertTrue("$titleRes 必须在 NavRow 调用里", callStart > 0)
        val callEnd = body.indexOf(")\n", titleAt)
        return body.substring(callStart, if (callEnd > 0) callEnd else body.length)
    }

    @Test
    fun `Hub 首屏渲染今日体征面板`() {
        assertTrue(
            "设计稿 §06 ③ 要求 Hub 首屏新增「今日体征」面板（不点进二级页也能看到核心数值）；" +
                "HealthHub 里必须真的调用 VitalsPanel",
            healthHubBody().contains("VitalsPanel("),
        )
        assertTrue(
            "面板标题用 vitals_today_title（「今日体征」），不要另起硬编码文案",
            appShellSource.contains("R.string.vitals_today_title"),
        )
    }

    @Test
    fun `今日体征面板四个数值格都在`() {
        val panel = appShellSource.substring(appShellSource.indexOf("private fun VitalsPanel("))
        // 设计稿 §06 ③ 是四格：血压 / 心率 / 体温 / 体重。
        for (res in listOf(
            "R.string.vitals_temperature_short",
            "R.string.vitals_bp",
            "R.string.vitals_heart_rate",
            "R.string.vitals_today_weight",
        )) {
            assertTrue("今日体征面板缺了 $res 这一格（设计稿 §06 ③ 是四格）", panel.contains(res))
        }
    }

    @Test
    fun `没记录时不隐藏该格而是显示未记录`() {
        val panel = appShellSource.substring(appShellSource.indexOf("private fun VitalsPanel("))
        assertTrue(
            "没记的格必须显示「未记录」而不是隐藏——面板的价值是让用户看出「哪项今天还没量」，" +
                "留白反而看不出缺口",
            panel.contains("R.string.common_not_recorded"),
        )
        assertTrue(
            "四格的取数都要有 ?: 回退到未记录（用 `?: notRecorded`，不是把 StatTile 整个条件掉）",
            panel.contains("?: notRecorded"),
        )
    }

    @Test
    fun `判色口径与体征二级页一致`() {
        // 判色阈值必须引用 ClinicalThresholds 的具名常量，不许在 UI 层写魔数——
        // 否则二级页改了阈值、Hub 首屏不跟着改，同一个体温会显示两种颜色。
        val panel = appShellSource.substring(appShellSource.indexOf("private fun VitalsPanel("))
        for (const in listOf(
            "ClinicalThresholds.FEVER_ALERT",
            "ClinicalThresholds.FEVER_LOW",
            "ClinicalThresholds.BP_HIGH_SYS",
            "ClinicalThresholds.BP_HIGH_DIA",
            "ClinicalThresholds.BP_LOW_SYS",
            "ClinicalThresholds.HR_HIGH",
            "ClinicalThresholds.HR_LOW",
        )) {
            assertTrue("判色必须引用 $const（与 WellnessScreen.VitalsHero 同一口径）", panel.contains(const))
        }
        assertTrue(
            "未记录必须给 Neutral，不能染警示色——未记录不是坏消息",
            panel.contains("StatusTone.Neutral"),
        )
    }

    @Test
    fun `紧急卡是 danger-soft 瓦片`() {
        val emergencyRow = navRowBlock("R.string.emergency_card_title")
        assertTrue(
            "设计稿 §06 ③ 要求紧急卡用 danger-soft 瓦片提示其特殊地位；" +
                "NavRow 的 tone 参数就是为此而加（见 Navigation.kt 的 KDoc）",
            emergencyRow.contains("tone = StatusTone.Danger"),
        )
    }

    @Test
    fun `另外两个入口保持默认瓦片配色`() {
        // 反向断言：tone 只给紧急卡。若「营养与骨健康 / 复诊管理」也染上语义色，
        // danger-soft 就不再「特殊」，等于白做。
        for (res in listOf("R.string.nutrition_bone_health_title", "R.string.checkup_manage_title")) {
            assertTrue(
                "$res 的瓦片不应带 tone（语义色只留给紧急卡）",
                !navRowBlock(res).contains("tone ="),
            )
        }
    }

    @Test
    fun `浮点渲染走 Locale US`() {
        // FormatLocaleGuardTest 只拦 `"…".format(` 隐式 locale；体温/体重必须显式给 Locale.US，
        // 否则法语/德语等区域会把小数点渲染成逗号，与设计稿的 `36.5 ℃` / `62.4 kg` 不符。
        val panel = appShellSource.substring(appShellSource.indexOf("private fun VitalsPanel("))
        assertTrue(
            "体温与体重必须 String.format(Locale.US, \"%.1f\", …)",
            panel.contains("String.format(Locale.US, \"%.1f\", it)"),
        )
    }
}
