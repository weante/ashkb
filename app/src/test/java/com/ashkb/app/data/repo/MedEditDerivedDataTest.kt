package com.ashkb.app.data.repo

import android.app.AlarmManager
import android.content.Context
import com.ashkb.app.data.db.AppDatabase
import com.ashkb.app.data.entity.Medication
import com.ashkb.app.reminder.ReminderScheduler
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
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
 * v1.0.80（批次 6）：**改药品时刻 → 计划槽位与提醒都要跟着变**（过真库 + 真 AlarmManager）。
 *
 * 这条需求有两个独立的坑，各自都能让「改了但没生效」：
 *
 * ① **计划槽位是 `INSERT OR IGNORE` 幂等写入的**，而滚动窗口（今天-1 .. 今天+7）里的行
 *    早就写好了。只加不清，改完时刻那些行仍是旧时刻——提醒在 20:00 响、完成度却拿 08:00 算，
 *    用户会看到一剂自己从没被告知过的漏服。故 `MedicationRepository.saveMedication`
 *    会删掉**今天起**的快照再让重排流程重新物化（历史不动，与停药同款口径）。
 *
 * ② **闹钟的 request code 由槽位派生**（medId + slotKey + 槽位日期 + 级数），
 *    而 slotKey 就是时刻。改完时刻若只用**新**药单去取消，算不出旧时刻的码，
 *    旧闹钟就成了孤儿继续按旧时刻响。故 `MeViewModel.saveMedication` 先按**改动前**的药单
 *    取消一次（本文件第二个用例把这条必要性钉住）。
 *
 * 纯函数测试证明不了这两件事里的任何一件：一件是 SQL 的删除范围，一件是 AlarmManager 上的实际排程。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class MedEditDerivedDataTest {

    private val ctx: Context get() = RuntimeEnvironment.getApplication()
    private val db get() = AppDatabase.get(ctx)
    private val medRepo by lazy { MedicationRepository(ctx) }

    /** 固定「现在」= 今天 07:00：08:00 与 20:00 两个时刻都还没到点，用例在任何时刻跑结果一致。 */
    private val today: LocalDate get() = LocalDate.now()
    private val now: LocalDateTime get() = LocalDateTime.of(today, LocalTime.of(7, 0))

    @Before
    fun setUp() {
        val field = AppDatabase::class.java.getDeclaredField("instance")
        field.isAccessible = true
        field.set(null, null)
        // 固定为「精确闹钟可用」：否则走 setWindow 分支，断言口径会不一致（同 ReminderSchedulerTest）
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
    }

    private fun <T> io(block: suspend () -> T): T = runBlocking(Dispatchers.IO) { block() }

    // ---- 计划槽位 ----

    @Test
    fun `改服药时刻后今天起的计划槽位跟着重排且历史不动`() {
        val id = "med-批次6-slot"
        val before = med(id, "08:00")
        io {
            medRepo.saveMedication(before)
            medRepo.materializePlannedSlots(listOf(before), today.minusDays(1), today.plusDays(2))
        }
        assertTrue("改动前今天应有 08:00 的计划", slotTimes(id, today).contains("08:00"))
        assertTrue("昨天的历史计划应当在", slotTimes(id, today.minusDays(1)).contains("08:00"))

        val after = before.copy(takeTimes = """["20:00"]""")
        io {
            medRepo.saveMedication(after)
            // 与生产同序：重排流程 materialize 用的是**库里重新读出来的**药单
            // （MeViewModel.rescheduleReminders → medicationDao().listActive()），
            // 而不是调用方手里那份旧对象——updatedAt 也以库里的为准。
            val fromDb = medRepo.medicationById(id)!!
            medRepo.materializePlannedSlots(listOf(fromDb), today.minusDays(1), today.plusDays(2))
        }

        val todaySlots = slotTimes(id, today)
        assertFalse(
            "今天仍是旧时刻的计划——提醒在 20:00 响、完成度却拿 08:00 算（幽灵漏服）",
            todaySlots.contains("08:00"),
        )
        assertTrue("新时刻的计划没有物化出来", todaySlots.contains("20:00"))
        assertEquals(
            "历史（昨天）不该被今天的编辑改写——那是当时真实存在过的计划",
            listOf("08:00"),
            slotTimes(id, today.minusDays(1)),
        )
    }

    // ---- 提醒 ----

    @Test
    fun `改服药时刻后提醒按新时刻排且旧时刻不再响`() {
        val before = med("med-批次6-alarm", "08:00")
        val after = before.copy(takeTimes = """["20:00"]""")

        ReminderScheduler.rescheduleAll(ctx, listOf(before), emptySet(), now)
        assertTrue("改动前应有 08:00 的提醒", alarmLocalTimes().contains(LocalDateTime.of(today, LocalTime.of(8, 0))))

        // 与 MeViewModel.saveMedication 完全同序：先按**改动前**的药单取消，再按新药单重排
        ReminderScheduler.cancelAllFuture(ctx, listOf(before), today)
        ReminderScheduler.rescheduleAll(ctx, listOf(after), emptySet(), now)

        val times = alarmLocalTimes()
        assertFalse(
            "旧时刻的闹钟成了孤儿——它会继续在每天 8 点提醒一条已经改到晚间的药",
            times.any { it.toLocalTime() == LocalTime.of(8, 0) },
        )
        assertTrue("新时刻没有排上提醒", times.any { it.toLocalTime() == LocalTime.of(20, 0) })
    }

    @Test
    fun `仅用新药单重排无法取消旧时刻闹钟——这条钉住上一步的必要性`() {
        val before = med("med-批次6-alarm-2", "08:00")
        val after = before.copy(takeTimes = """["20:00"]""")

        ReminderScheduler.rescheduleAll(ctx, listOf(before), emptySet(), now)
        // 只按新药单重排（漏掉「先按旧药单取消」这一步）
        ReminderScheduler.rescheduleAll(ctx, listOf(after), emptySet(), now)

        assertTrue(
            "如果这里变成 false，说明 request code 的派生方式变了——" +
                "此时应重新审视 MeViewModel.saveMedication 里那次「按旧药单取消」是否还需要",
            alarmLocalTimes().any { it.toLocalTime() == LocalTime.of(8, 0) },
        )
    }

    // ---- 夹具 ----

    private fun alarmLocalTimes(): List<LocalDateTime> {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val zone = ZoneId.systemDefault()
        return Shadows.shadowOf(am).scheduledAlarms.map {
            LocalDateTime.ofInstant(Instant.ofEpochMilli(it.triggerAtMs), zone)
        }
    }

    private fun slotTimes(medId: String, date: LocalDate): List<String> = io {
        db.plannedSlotDao().betweenForMed(medId, date.toString(), date.toString()).map { it.slotTime }
    }

    private fun med(id: String, time: String) = Medication(
        id = id, name = "测试药 $id", nameKey = "test-med-batch6", medClass = "OTHER",
        route = "oral", dose = "1 片", frequency = "DAILY",
        takeTimes = """["$time"]""", startDate = "2026-01-01",
        createdAt = "2026-01-01T00:00:00", updatedAt = "2026-01-01T00:00:00",
    )
}
