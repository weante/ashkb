package com.ashkb.app.data.repo

import android.content.Context
import com.ashkb.app.data.db.AppDatabase
import com.ashkb.app.data.entity.Medication
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * v1.2.10（维护者指令）：**按需用药常驻首页，但每天只记一次**。
 *
 * ### 这条缺陷是什么（两个半边，缺一不可）
 * ① 写侧：按需打卡的 `slot_key` 恒为 NULL，而 `MedicationLogDao.find` 用 `slot_key = :slotKey`
 *    比较——SQL 里 `NULL = NULL` 不为真 → 幂等守卫**永远匹配不到** → 每点一次「记录使用」
 *    就插入新行。这条与 v1.1.1（HIGH-1）修掉的补剂打卡缺陷是同一个根因（见
 *    [SupplementCheckInIdempotencyTest] 的类注释）。
 * ② 读侧：`buildTodayItems` 给按需卡**恒传 log = null**，于是 `TodayItem.done` 永远为 false：
 *    卡片永远一副"还没记"的样子（按钮常驻、无「已服 HH:mm」），用户看不出自己已经记过，
 *    自然会再点——两侧叠加就是"点几次落几行，而卡上什么都看不出来"。
 *
 * ### 为什么必须过真库
 * ① 的根因是 **SQL 的 NULL 语义**：`slot_key = NULL` 与 `slot_key IS NULL` 的差别只有真的跑一遍
 * SQLite 才知道；② 涉及「同日多行的存量数据取哪一条」，纯算术测试证明不了库里那几行怎么被选中。
 *
 * 与 [PostponeInjectionSlotTest] / [SupplementCheckInIdempotencyTest] 共用两条基建约定
 * （每个用例前清 [AppDatabase] 进程内单例；库操作走 IO 线程——Room 默认禁止主线程访问，
 * 而 Robolectric 的测试线程就是主线程）。每个用例用各自独立的 id，用例之间互不重叠。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class PrnDailyCapTest {

    private val ctx: Context get() = RuntimeEnvironment.getApplication()
    private val db get() = AppDatabase.get(ctx)
    private val repo by lazy { MedicationRepository(ctx) }

    @Before
    fun resetDatabaseSingleton() {
        val field = AppDatabase::class.java.getDeclaredField("instance")
        field.isAccessible = true
        field.set(null, null)
    }

    private fun <T> io(block: suspend () -> T): T = runBlocking(Dispatchers.IO) { block() }

    private val today: LocalDate get() = LocalDate.now()

    @Test
    fun `同一天连点两次记录使用只留一行`() {
        val med = prnMed("med-prn-1")

        io {
            db.medicationDao().upsert(med)
            // 与今日页 onPrnTaken 逐字同款：slotKey / slotTime 都是 null
            repo.checkIn(med, null, null, date = today)
            repo.checkIn(med, null, null, date = today)
            repo.checkIn(med, null, null, date = today)
        }

        val rows = io { db.medicationLogDao().byDate(today.toString()) }
        assertEquals(
            "同一天同一按需药连点三次必须只有一行——`slot_key = NULL` 的判等是这条守卫失效的根因",
            1,
            rows.size,
        )
        assertTrue("按需行必须带 prn_flag（依从率据此排除）", rows.single().prnFlag)
        assertNull("按需行没有槽位键", rows.single().slotKey)
        assertEquals("done", rows.single().status)
    }

    @Test
    fun `每天一次而不是总共一次——次日可以再记`() {
        val med = prnMed("med-prn-2")
        val tomorrow = today.plusDays(1)

        io {
            db.medicationDao().upsert(med)
            repo.checkIn(med, null, null, date = today)
            repo.checkIn(med, null, null, date = today)
            repo.checkIn(med, null, null, date = tomorrow)
        }

        val todayRows = io { db.medicationLogDao().byDate(today.toString()) }
        val tomorrowRows = io { db.medicationLogDao().byDate(tomorrow.toString()) }
        assertEquals("今天一条", 1, todayRows.size)
        assertEquals("次日是新的一天，必须能再记一条（上限是「每天一次」不是「一共一次」）", 1, tomorrowRows.size)
        assertFalse(
            "两条日志的主键必须不同，否则次日那条会覆盖今天那条",
            todayRows.single().id == tomorrowRows.single().id,
        )
    }

    @Test
    fun `今日卡挂上当天按需记录后不再是永远未记录`() {
        val med = prnMed("med-prn-3")
        val date = today

        val before = io { repo.buildTodayItems(listOf(med), emptyList(), date) }
        assertEquals("按需药每天都要出卡（常驻首页）", 1, before.size)
        assertTrue("按需卡以 slotKey == null 认定", before.single().isPrn)
        assertNull("还没记过时卡上没有日志", before.single().log)
        assertFalse("还没记过时不是 done", before.single().done)

        io { repo.checkIn(med, null, null, date = date) }
        val logs = io { db.medicationLogDao().byDate(date.toString()) }
        val after = io { repo.buildTodayItems(listOf(med), logs, date) }

        assertNotNull("记过之后卡上必须能看见那条日志（此前恒为 null，故 done 永远 false）", after.single().log)
        assertTrue("记过之后卡上必须是已服态，按钮才会撤掉", after.single().done)
        assertTrue("已服态下仍必须认得出这是按需卡", after.single().isPrn)
        assertTrue(
            "按需卡不进「待办」计数（pendingCount 用 !isSettled && !isPrn）",
            after.single().isSettled,
        )
    }

    @Test
    fun `存量同一天多行时卡与写入都取最新那条`() {
        val med = prnMed("med-prn-4")
        val date = today

        io {
            db.medicationDao().upsert(med)
            // 旧版本允许一天落多行：手工造两条（不同 recorded_at），模拟升级前留下的存量数据
            db.medicationLogDao().upsert(logRow("mlog-prn-4-old", med, date, "2026-01-01T08:00:00"))
            db.medicationLogDao().upsert(logRow("mlog-prn-4-new", med, date, "2026-01-01T20:00:00"))
        }

        val logs = io { db.medicationLogDao().byDate(date.toString()) }
        val item = io { repo.buildTodayItems(listOf(med), logs, date) }.single()
        assertEquals(
            "卡上显示的必须是 recorded_at 最新那条（与写入侧 findPrn 的 ORDER BY 同一口径）",
            "mlog-prn-4-new",
            item.log?.id,
        )

        // 再点一次：更新的必须是同一条（最新那条），而不是新插第三行
        io { repo.checkIn(med, null, null, date = date) }
        val after = io { db.medicationLogDao().byDate(date.toString()) }
        assertEquals("存量多行不被改写、也不新增——只是从此不再增长", 2, after.size)
        assertEquals(
            "被更新的是最新那条（recorded_at 20:00），不是最早那条",
            "mlog-prn-4-new",
            io { db.medicationLogDao().findPrn(med.id, date.toString()) }?.id,
        )
    }

    @Test
    fun `find 查不到按需行而 findPrn 查得到`() {
        val med = prnMed("med-prn-5")
        val date = today

        io {
            db.medicationDao().upsert(med)
            repo.checkIn(med, null, null, date = date)
        }

        assertNull(
            "`slot_key = NULL` 恒不成立——这正是旧实现每次点击都插新行的根因",
            io { db.medicationLogDao().find(med.id, date.toString(), null) },
        )
        assertNotNull(
            "`slot_key IS NULL` 才能定位按需行",
            io { db.medicationLogDao().findPrn(med.id, date.toString()) },
        )
    }

    // ---- 夹具 ----

    private fun prnMed(id: String) = Medication(
        id = id, name = "布洛芬", nameKey = "ibuprofen", medClass = "ANALGESIC",
        route = "oral", dose = "200mg", frequency = "PRN", prnReason = "备用镇痛",
        startDate = today.toString(), createdAt = nowIso(), updatedAt = nowIso(),
    )

    private fun logRow(id: String, med: Medication, date: LocalDate, recordedAt: String) =
        com.ashkb.app.data.entity.MedicationLog(
            id = id, date = date.toString(), recordedAt = recordedAt,
            medId = med.id, medKey = med.nameKey, medName = med.name, doseSnapshot = med.dose,
            slotKey = null, status = "done", takenAt = recordedAt, prnFlag = true,
        )
}
