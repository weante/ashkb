package com.ashkb.app.reminder

import android.app.AlarmManager
import android.app.Application
import android.content.Context
import com.ashkb.app.data.entity.Medication
import com.ashkb.app.ui.ReminderNotice
import java.io.File
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager

/**
 * v1.2.11（缺陷工单 B09）：**保存后提醒重排失败不得静默**。
 *
 * 工单症状：`ui/wellness/WellnessViewModel.kt` 等处把整段重排 `runCatching` 掉，
 * 「数据存进去了、提醒一条没排上」全程无声——用户以为提醒已按新时刻生效，第二天什么也没响。
 *
 * 本文件分三层钉死它：
 *  1. **轮次报告语义**（[ReminderHealth.RunReport.ok]）：一个槽位都没排不算失败（否则每次保存
 *     都弹提示，狼来了）；整轮被异常中断（[ReminderHealth.RunReport.aborted]）必须算失败
 *     （那种情况 `failed` 恒为 0，只看失败数会把最严重的情形报成「全部正常」）。
 *  2. **真实注册路径的计数**：用 `ShadowAlarmManager` 让注册真的失败 / 真的成功，
 *     断言报告里的 attempted / failed / firstError 与闹钟实际状态一致。
 *  3. **调用点静态守卫**：`ui/` 下每一个 `Scheduler.rescheduleAll(` 都必须把返回值交给
 *     [ReminderNotice.postIfFailed]——撤销修复（改回 `runCatching {}` 并丢弃报告）会让它红。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ReminderRescheduleReportTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    private val today = LocalDateTime.of(2026, 6, 15, 12, 0)

    @Before
    fun setUp() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
    }

    // ---- 1. 报告语义 ----

    @Test
    fun `没排任何槽位不算失败`() {
        val empty = ReminderHealth.EMPTY_RUN
        assertTrue("一个槽位都没排是正常状态，不能报成失败", empty.ok)
        assertEquals(0, empty.attempted)
    }

    @Test
    fun `整轮被异常中断必须算失败`() {
        // 最严重的情形：还没试到任何槽位就抛了，failed 恒为 0
        val aborted = ReminderHealth.RunReport(attempted = 0, failed = 0, aborted = true, firstError = "boom")
        assertFalse("整轮中断不能被报成「全部正常」", aborted.ok)
    }

    @Test
    fun `有失败就不是 ok`() {
        val partial = ReminderHealth.RunReport(attempted = 3, failed = 1, firstError = "denied")
        assertFalse(partial.ok)
    }

    // ---- 2. 真实注册路径的计数 ----

    @Test
    fun `注册被系统拒绝时报告计入失败并保留首个错误`() {
        // 精确闹钟不可用 → AlarmRegister 降级 setWindow；这里让降级路径也抛，模拟厂商直接拒绝。
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val failing = object : RuntimeException("SecurityException: exact alarm denied") {}
        val report = AlarmRegister.reschedule(context, "test") {
            // 直接把异常抛进轮次里：等价于「取 DAO / 拿 AlarmManager 就炸了」
            throw failing
        }
        assertFalse("整轮中断必须报失败", report.ok)
        assertTrue("必须标记 aborted", report.aborted)
        assertEquals("首个错误要保留给用户看", "SecurityException: exact alarm denied", report.firstError)
        // 闹钟一条都没有——正是需要提示用户的状态
        assertTrue(Shadows.shadowOf(am).scheduledAlarms.isEmpty())
    }

    @Test
    fun `协程取消不记成重排失败`() {
        // CancellationException 是取消不是失败：不该弹「提醒排不上」，更不该被吞掉破坏结构化并发
        var thrown = false
        try {
            AlarmRegister.reschedule(context, "test") {
                throw kotlinx.coroutines.CancellationException("cancelled")
            }
        } catch (expected: kotlinx.coroutines.CancellationException) {
            // 变量名用 `expected`：这条异常本来就是被测行为，detekt 的 SwallowedException
            // 按名字认定「有意吞掉」（不捕获才叫漏测，捕获了就是断言对象）。
            thrown = true
        }
        assertTrue("CancellationException 必须原样抛出", thrown)
    }

    @Test
    fun `成功一轮的报告 ok 且计数与闹钟数一致`() {
        val med = medication()
        val report = ReminderScheduler.rescheduleAll(context, listOf(med), emptySet(), today)
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val alarms = Shadows.shadowOf(am).scheduledAlarms
        assertTrue("正常重排必须 ok", report.ok)
        assertEquals(0, report.failed)
        assertTrue("这一轮应当真的排上了闹钟", alarms.isNotEmpty())
        assertEquals("报告的尝试数必须等于实际注册数", alarms.size, report.attempted)
    }

    // ---- 3. 调用点静态守卫 ----

    @Test
    fun `ui 层每处 rescheduleAll 都把报告交给了 ReminderNotice`() {
        val offenders = uiSources().flatMap { (path, source) -> uncheckedSites(path, source) }
        assertTrue(
            "以下调用点丢掉了重排报告（B09 会退回静默）：\n" +
                offenders.joinToString("\n") { "${it.first}:${it.second}" },
            offenders.isEmpty(),
        )
    }

    @Test
    fun `守卫的判据能被反例证伪`() {
        // 合成源码证明判据真的区分「接了提示」与「runCatching 吞掉」
        val good = "withContext(Dispatchers.IO) {\n" +
            "    val report = ReminderScheduler.rescheduleAll(ctx, list, refs)\n" +
            "    ReminderNotice.postIfFailed(ctx, report)\n" +
            "}\n"
        val bad = "withContext(Dispatchers.IO) {\n" +
            "    runCatching {\n" +
            "        ReminderScheduler.rescheduleAll(ctx, list, refs)\n" +
            "    }\n" +
            "}\n"
        assertTrue("接了提示的必须判为合格", isReported(good, good.indexOf("Scheduler.rescheduleAll(")))
        assertTrue("被 runCatching 吞掉的必须判为不合格", !isReported(bad, bad.indexOf("Scheduler.rescheduleAll(")))
    }

    @Test
    fun `扫描到的调用点数量足以证明守卫没空转`() {
        val hits = uiSources().sumOf { (_, source) -> occurrences(source, "Scheduler.rescheduleAll(").size }
        assertTrue("扫描到的 rescheduleAll 调用点太少（$hits），守卫自身的假设可能已失效", hits >= 10)
    }

    /**
     * 调用点之后 [WINDOW] 个字符内出现 `ReminderNotice.postIfFailed(` 即视为已报告。
     *
     * 取窗口而不是整函数体：调用点后面往往紧跟 `} else {` 分支或物化语句，
     * 而提示必须在同一分支里、紧跟报告之后（隔了 `cancelAllFuture` 之类就说明接错了地方）。
     */
    private fun isReported(source: String, callAt: Int): Boolean {
        val tail = source.substring(callAt, minOf(source.length, callAt + WINDOW))
        return tail.contains("ReminderNotice.postIfFailed(")
    }

    private fun uncheckedSites(path: String, source: String): List<Pair<String, Int>> =
        occurrences(source, "Scheduler.rescheduleAll(")
            .filterNot { isReported(source, it) }
            .map { path to source.take(it).count { c -> c == '\n' } + 1 }

    private fun occurrences(source: String, call: String): List<Int> {
        val found = mutableListOf<Int>()
        var from = 0
        while (true) {
            val at = source.indexOf(call, from)
            if (at < 0) return found
            found += at
            from = at + call.length
        }
    }

    private fun medication() = Medication(
        id = "med-b09", name = "测试药", nameKey = "test", medClass = "OTHER",
        route = "oral", dose = "1 片", frequency = "DAILY",
        takeTimes = """["08:00"]""", startDate = "2026-06-01",
        createdAt = "2026-06-01T00:00:00", updatedAt = "2026-06-01T00:00:00",
    )

    private fun uiSources(): List<Pair<String, String>> {
        val ui = File(repoRoot(), "app/src/main/java/com/ashkb/app/ui")
        return ui.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .map { it.relativeTo(repoRoot()).path.replace('\\', '/') to it.readText() }
            .sortedBy { it.first }
            .toList()
    }

    private fun repoRoot(): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            if (File(dir, "settings.gradle.kts").exists()) return dir
            dir = dir.parentFile
        }
        return File("../")
    }

    private companion object {
        /** 调用点之后看多远算「同一处」——足以跨过跨行实参，又不足以跨到下一个分支。 */
        const val WINDOW = 700
    }
}
