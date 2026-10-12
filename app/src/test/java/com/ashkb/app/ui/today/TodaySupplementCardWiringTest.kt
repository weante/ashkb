package com.ashkb.app.ui.today

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 首页补剂卡的**接线**守卫（v1.2.9）。
 *
 * 维护者反馈（m02219）：「设置了补剂用药提示时间，但是在首页总是看不到，需要到营养与骨健康中打卡」
 * ——这个缺陷的本质是「功能存在，但首页没有任何入口」，**不是逻辑算错**。逻辑错会有别的测试红，
 * 入口缺失却不会：`TodayViewModel` 一条不动、`SupplementReminderScheduler` 九条全绿，用户照样
 * 在首页看不到补剂。所以这里必须守住「接线」本身。
 *
 * 为什么是源码文本扫描而不是 Compose UI 测试：本模块没有 `androidx.compose.ui:ui-test-junit4`
 * 依赖（`app/build.gradle.kts` 测试依赖只有 junit / json / robolectric / room-testing），
 * 起不了 `createComposeRule`。同款做法的先例见 `ui/wellness/SupplementSkipWiringTest.kt`
 * 的类注释——它的理由与本文件完全相同。
 *
 * 断言刻意**不锁死实现细节**（不查函数名、不查行号、不查具体控件）：一旦有人把
 * `SectionCard` 换成别的容器，或把按钮从 `Button` 换成 `FilledTonalButton`，这些断言仍应通过。
 * 真正要守的是三件事：
 *  ① 今日页确实登记了补剂卡（否则首页看不到）；
 *  ② 卡上确实有「打卡」这一条写库路径（否则只能看不能记）；
 *  ③ 打卡走的是**补剂**的写入与重排，不是药品的（张冠李戴的话编译也能过）。
 */
class TodaySupplementCardWiringTest {

    private val screenSource: String = readSource("app/src/main/java/com/ashkb/app/ui/today/TodayScreen.kt")
    private val viewModelSource: String = readSource("app/src/main/java/com/ashkb/app/ui/today/TodayViewModel.kt")

    /**
     * 定位仓库根：测试的工作目录是 `app/`（Gradle 单测的默认 cwd），源码在 `../` 之外。
     * 逐级向上找一个含 `settings.gradle.kts` 的目录，避免把 `../../..` 的层数写死。
     */
    private fun readSource(relative: String): String {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            val candidate = File(dir, relative)
            if (candidate.isFile) return candidate.readText()
            if (File(dir, "settings.gradle.kts").isFile) break
            dir = dir.parentFile
        }
        // 退回 app/ 为 cwd 的常见情形
        val fallback = File("../$relative").absoluteFile
        return fallback.readText()
    }

    @Test
    fun `今日页登记了补剂卡`() {
        assertTrue(
            "TodayScreen.kt 必须登记 TodaySupplementCard，否则首页看不到补剂（m02219 的原缺陷）",
            screenSource.contains("TodaySupplementCard("),
        )
    }

    @Test
    fun `补剂卡有打卡与跳过两个入口`() {
        assertTrue(
            "补剂卡必须给「已服」打卡入口（调 vm.checkInSupplement 且状态为 DONE）",
            screenSource.contains("vm.checkInSupplement(sup, AdherenceCalc.DONE)"),
        )
        assertTrue(
            "补剂卡必须给「跳过」入口（同样是合法结算，不该逼用户只能记已服）",
            screenSource.contains("vm.checkInSupplement(sup, AdherenceCalc.SKIPPED)"),
        )
        assertTrue(
            "补剂卡必须给撤销入口，否则误点后当天回不到未记录态",
            screenSource.contains("undoSupplementCheckIn"),
        )
    }

    @Test
    fun `打卡写入的是补剂而不是药品`() {
        // 张冠李戴（把 sup 传给药品的 checkIn 之类）编译也能过，所以显式守一条：
        // 今日页的补剂写入路径必须是 SupplementLog 实体
        assertTrue(
            "TodayViewModel 的补剂打卡必须写 SupplementLog，而不是药品的 MedicationLog",
            viewModelSource.contains("SupplementLog("),
        )
        assertTrue(
            "打卡后必须重排补剂提醒（补剂打卡按天记，当天记过其余时刻要撤下）",
            viewModelSource.contains("SupplementReminderScheduler"),
        )
    }

    @Test
    fun `补剂卡复用营养页的状态口径`() {
        assertTrue(
            "今日状态必须走 SupplementLogStatus.loggedToday，不能在首页另写一套（同一天多行取 recordedAt 最大者的口径只该有一份）",
            screenSource.contains("SupplementLogStatus.loggedToday"),
        )
        assertTrue(
            "状态文案 / 色调必须复用 SupplementLogStatus 的 labelRes / tone",
            screenSource.contains("SupplementLogStatus.labelRes") &&
                screenSource.contains("SupplementLogStatus.tone"),
        )
    }

    @Test
    fun `提醒时刻在首页可见`() {
        // 只显示「已服 / 未记录」而不显示用户设的提醒时刻，用户仍然无法确认「我设的到底生效没」，
        // 这正是 m02219 场景的核心困惑。时刻展示必须与排程用同一份解析（JSON 口径）。
        assertTrue(
            "补剂卡必须展示提醒时刻，且用 SupplementReminderScheduler.parseTimes 解析（与排程同一份口径）",
            screenSource.contains("SupplementReminderScheduler.parseTimes"),
        )
    }
}
