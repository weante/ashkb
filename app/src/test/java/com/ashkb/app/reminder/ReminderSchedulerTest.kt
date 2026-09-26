package com.ashkb.app.reminder

import android.app.AlarmManager
import android.app.Application
import android.content.Context
import com.ashkb.app.data.entity.Medication
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
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
 * S1（v1.0.53）：提醒链「取消-重建」语义回归。
 *
 * **为什么必须用 Robolectric**：`ReminderScheduler` 的全部行为都落在 `AlarmManager` 上，
 * 而纯 JVM 单测（`isReturnDefaultValues = true`）下 `android.*` 是空实现——闹钟排了没排、
 * 排了几次、有没有被清掉，一个字都验不了。A1（开机后误提醒）与 N1（开机广播漏传
 * doneRefs 导致已服药槽位仍排升级提醒）这类**只在真机暴露**的缺陷正源于此。
 *
 * **为什么把「现在」写成常量**：本方法的可观察效果完全由「现在」与各槽位时刻的相对关系决定。
 * 若用「相对当下 ±10 分钟」构造，测试就会在贴着零点时跨日而失效——`assumeTrue` 跳过是
 * **静默失效**，回归锁一旦静默失效就等于没有。Robolectric 又改不动 `java.time` 的挂钟
 * （`ShadowSystemClock` 只影响 `SystemClock`，实测 `advanceBy` 后 `LocalDateTime.now()` 不变），
 * 故改由 `rescheduleAll(..., now)` 从调用侧注入，做到**任何时刻运行结果都一致**。
 *
 * 断言一律以「闹钟的触发时刻集合」为准（而非 PendingIntent 内部字段）——
 * 触发时刻是 `rescheduleAll` 对外唯一的可观察效果，口径最稳。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ReminderSchedulerTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Before
    fun setUp() {
        // 固定为「精确闹钟可用」：否则走 setWindow 分支，两条路径的断言会不一致
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
    }

    // ---- 工具 ----

    private fun alarmTimes(): List<Long> {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        // Robolectric 4.13 的 ScheduledAlarm 暴露触发时刻；字段 triggerAtTime 已 deprecated，
        // 用 getTriggerAtMs() 对应的属性（已 javap 核对 shadows-framework-4.13.jar）
        return Shadows.shadowOf(am).scheduledAlarms.map { it.triggerAtMs }
    }

    private fun epoch(dt: LocalDateTime): Long =
        dt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun med(slotTime: String, id: String = "med-1") = Medication(
        id = id, name = "测试药", nameKey = "test", medClass = "OTHER",
        route = "oral", dose = "1 片", frequency = "DAILY",
        takeTimes = """["$slotTime"]""", startDate = "2026-01-01",
        createdAt = "2026-01-01T00:00:00", updatedAt = "2026-01-01T00:00:00",
    )

    /** 20:00 的槽位相对固定的「现在」= 20:10：刚好过点，且 +30 / +60 都还没到 */
    private val pastSlot = "20:00"
    private val pastBase = LocalDateTime.of(2026, 6, 15, 20, 0)

    /** 20:20 的槽位：还没到点 */
    private val futureSlot = "20:20"
    private val futureBase = LocalDateTime.of(2026, 6, 15, 20, 20)

    private fun reschedule(
        meds: List<Medication>,
        done: Set<String> = emptySet(),
        now: LocalDateTime = NOW,
    ) = ReminderScheduler.rescheduleAll(context, meds, done, now)

    private fun escAt(level: Int, base: LocalDateTime) =
        epoch(base.plusMinutes(ReminderScheduler.ESCALATION_STEP_MINUTES * level))

    // ---- 未来槽位 ----

    @Test
    fun `每日一次的药 未来 7 天各排一个首次提醒`() {
        reschedule(listOf(med(pastSlot)))

        val times = alarmTimes()
        // 今日的槽位已过点 → 第 1 步跳过（由第 2 步只补升级重查）；未来第 1..7 天各一个
        for (d in 1..ReminderScheduler.HORIZON_DAYS) {
            val expected = epoch(pastBase.plusDays(d))
            assertTrue("第 $d 天 $pastSlot 的首次提醒缺失", times.contains(expected))
        }
        assertTrue(
            "第 8 天不该排提醒",
            !times.contains(epoch(pastBase.plusDays(ReminderScheduler.HORIZON_DAYS + 1))),
        )
    }

    // ---- 升级重查（A1 / N1 的正面与反面） ----

    @Test
    fun `今日已过点且未打卡 会重建 +30 与 +60 两次升级重查`() {
        reschedule(listOf(med(pastSlot)))

        val times = alarmTimes()
        assertTrue("+30 分钟升级重查缺失（v1.0.43 修复的核心行为）", times.contains(escAt(1, pastBase)))
        assertTrue("+60 分钟升级重查缺失（v1.0.43 修复的核心行为）", times.contains(escAt(2, pastBase)))

        // 恰为：未来 7 天首次提醒 + 今日 2 次升级重查
        assertEquals(7 + ReminderScheduler.MAX_ESCALATION, times.size)
    }

    @Test
    fun `今日已打卡的槽位不再重建升级重查（N1 回归锁）`() {
        val m = med(pastSlot)
        // v1.0.44：doneSlotRefs 是唯一判据；开机广播若漏传，已服药的槽位会被排上 +30 / +60 误提醒
        reschedule(listOf(m), setOf(ReminderScheduler.slotRef(m.id, pastSlot)))

        val times = alarmTimes()
        assertTrue("已打卡槽位仍被排了 +30 升级重查（N1 复发）", !times.contains(escAt(1, pastBase)))
        assertTrue("已打卡槽位仍被排了 +60 升级重查（N1 复发）", !times.contains(escAt(2, pastBase)))
        // 未来 7 天的首次提醒不受影响——打卡只影响今天
        assertEquals(7, times.size)
    }

    @Test
    fun `slotRef 的维度是 medId 加 slotKey`() {
        // 判据必须与 request code 同维度：换成别的维度（如带日期）会让 doneRefs 永远不命中，
        // 表现为「打卡后仍收到升级提醒」——即 N1 的另一种写法。
        assertEquals("med-1|$pastSlot", ReminderScheduler.slotRef("med-1", pastSlot))
        assertEquals("med-1|null", ReminderScheduler.slotRef("med-1", null))
    }

    // ---- 未到点 / 幂等 / 取消 ----

    @Test
    fun `未到点的槽位不排升级重查`() {
        reschedule(listOf(med(futureSlot)))

        val times = alarmTimes()
        // 今日 + 未来 7 天，各一个首次提醒；升级重查只在「已过点」时才补
        assertEquals(1 + 7, times.size)
        assertTrue(
            "未到点的槽位不该有 +30 重查",
            !times.contains(escAt(1, futureBase)),
        )
    }

    @Test
    fun `重复重排是幂等的（先取消再重建 不会累积）`() {
        val m = med(pastSlot)
        reschedule(listOf(m))
        val first = alarmTimes().size
        reschedule(listOf(m))
        val second = alarmTimes().size
        assertTrue("首次重排应排出闹钟", first > 0)
        assertEquals("重排后闹钟数应不变（request code 稳定 + 先 cancelAllFuture）", first, second)
    }

    /**
     * **取消-重建对称性**（S1 的核心）：「排出来的」必须能被「取消掉」精确清空。
     *
     * `schedule` 与 `cancelAllFuture` 各自**独立**算了一遍触发时刻（前者按第 1/2 步的实际时刻，
     * 后者按 `base + 30*esc` 遍历 esc=0..2）。两处算法必须一致——一旦有人只改了一处，
     * request code 就对不上：表现为「闹钟取消不掉，重复累加、旧提醒照响」。
     */
    @Test
    fun `cancelAllFuture 能精确清空 rescheduleAll 排出的全部闹钟`() {
        val m = med(pastSlot)
        reschedule(listOf(m))
        assertTrue("应先排出闹钟", alarmTimes().isNotEmpty())

        ReminderScheduler.cancelAllFuture(context, listOf(m), pastBase.toLocalDate())
        assertEquals("应为 0（取消与排出的 request code 必须一一对应）", 0, alarmTimes().size)
    }

    /**
     * **R6 回归锁**：`rescheduleAll` 只取消**传入药单**的闹钟（它按当前计划反算 request code），
     * 所以「已离开活跃列表的药」必须由调用方**显式**取消——`MeViewModel.stopMedication`
     * 就是这么做的（先 `cancelAllFuture(listOf(stopped))`，再 `rescheduleAll(listActive())`）。
     *
     * 残留的孤儿闹钟也不是无声隐患：`ReminderReceiver` 有两条兜底——归档药直接取消；
     * 槽位已不在今日计划中的（改时刻 / 顺延 / 换频次）也静默取消，**都不发通知**。
     * 但这两条是「兜底」而非设计意图，故此处仍按正确流程断言「显式取消后不留残留」。
     */
    @Test
    fun `停药时显式取消该药闹钟后 重排不留残留`() {
        val old = med(pastSlot, id = "med-old")
        reschedule(listOf(old))
        val oldEsc1 = escAt(1, pastBase)
        assertTrue("前置条件：旧药的 +30 重查应已排上", alarmTimes().contains(oldEsc1))

        // 生产流程（MeViewModel.stopMedication）：先单独取消被停药的全部闹钟，再重排活跃药
        ReminderScheduler.cancelAllFuture(context, listOf(old), pastBase.toLocalDate())
        // 新药的槽位也必须是「刚过点」的（20:05 < 现在的 20:10），否则走的是首次提醒而非升级重查
        val newSlot = "20:05"
        val newBase = LocalDateTime.of(2026, 6, 15, 20, 5)
        reschedule(listOf(med(newSlot, id = "med-new")))

        val times = alarmTimes()
        assertTrue("旧药的 +30 重查仍残留（显式取消没生效）", !times.contains(oldEsc1))
        assertTrue("新药的 +30 重查缺失", times.contains(escAt(1, newBase)))
    }

    // ---- 注射药只在注射日排 ----

    @Test
    fun `Q2W 注射药只在注射日排提醒`() {
        val m = med(pastSlot).copy(
            route = "injection", frequency = "Q2W", injCycleDays = 14,
            startDate = NOW.toLocalDate().toString(),
        )
        reschedule(listOf(m))

        val times = alarmTimes()
        // 锚点是今天、周期 14 天：7 天视野内只有今天（明天到第 7 天都不是注射日）
        // 今日已过点 → 只有 2 次升级重查
        assertEquals(
            "7 天视野内应只有今天这一个注射日",
            ReminderScheduler.MAX_ESCALATION,
            times.size,
        )
        assertTrue(times.contains(escAt(1, pastBase)))
    }

    // ---- B9（v1.0.61）：末级升级 = 强提醒 ----

    @Test
    fun `末级升级判定为强提醒 其余不是`() {
        // 0 首次 / 1 重复 —— 仍走普通通知
        for (esc in 0 until ReminderScheduler.MAX_ESCALATION) {
            assertTrue(
                "esc=$esc 不该判定为强提醒",
                !ReminderScheduler.isStrongEscalation(esc),
            )
        }
        // 末级（== MAX_ESCALATION）→ 强提醒（全屏 Intent）
        assertTrue(
            "末级升级应判定为强提醒",
            ReminderScheduler.isStrongEscalation(ReminderScheduler.MAX_ESCALATION),
        )
        // 越界防御：超过上限仍为强提醒（不会退回普通通知）
        assertTrue(ReminderScheduler.isStrongEscalation(ReminderScheduler.MAX_ESCALATION + 1))
    }

    private companion object {
        /** 固定的「现在」：20:10——`pastSlot` 刚好过点，`futureSlot` 还没到点 */
        val NOW: LocalDateTime = LocalDateTime.of(2026, 6, 15, 20, 10)
    }
}
