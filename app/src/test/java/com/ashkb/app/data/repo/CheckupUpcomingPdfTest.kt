package com.ashkb.app.data.repo

import android.content.Context
import com.ashkb.app.data.db.AppDatabase
import com.ashkb.app.data.entity.CheckupRecord
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * v1.2.11（缺陷工单 B02）：复诊 PDF 的「未来复诊」段必须按 `next_date` 查，不按就诊日查。
 *
 * 为什么必须过真库：这个缺陷整个长在 SQL 的 WHERE 列上——旧写法 `between(就诊日 -180天, 今天+90天)`
 * 在 Kotlin 侧看起来完全正常（还额外 `.filter { nextDate >= 今天 }`），只有真的把一条
 * 「半年前就诊、约在两个月后复查」的记录塞进库，才会看到它被 `date` 条件排除掉。
 * 纯函数或 mock 都验不出这一层。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class CheckupUpcomingPdfTest {

    private val ctx: Context get() = RuntimeEnvironment.getApplication()
    private val db get() = AppDatabase.get(ctx)
    private val repo by lazy { ReportRepository(ctx) }

    @Before
    fun resetDatabaseSingleton() {
        val field = AppDatabase::class.java.getDeclaredField("instance")
        field.isAccessible = true
        field.set(null, null)
    }

    private fun <T> io(block: suspend () -> T): T = runBlocking(Dispatchers.IO) { block() }

    private fun record(id: String, visitDate: String, nextDate: String?): CheckupRecord = CheckupRecord(
        id = id,
        date = visitDate,
        recordedAt = "$visitDate" + "T08:00:00",
        itemName = "复诊-$id",
        checkType = "clinic",
        nextDate = nextDate,
    )

    @Test
    fun `半年前就诊但复查日在未来的记录仍进 PDF`() {
        val today = LocalDate.now()
        // 就诊日 200 天前（落在旧的 180 天窗口之外），复查日 30 天后
        val oldVisit = today.minusDays(200).toString()
        val soon = today.plusDays(30).toString()
        io {
            db.checkupRecordDao().upsert(record("b02-old-visit", oldVisit, soon))
            val report = repo.checkupReport()
            assertTrue(
                "就诊日在 180 天窗口外的记录，只要 next_date 在未来就必须出现在 PDF 的未来复诊段——" +
                    "旧实现按就诊日过滤，会把它整条排除",
                report.nextCheckups.any { it.id == "b02-old-visit" },
            )
            assertEquals(
                "未来复诊必须按 next_date 排序",
                listOf(soon),
                report.nextCheckups.filter { it.id == "b02-old-visit" }.map { it.nextDate },
            )
        }
    }

    @Test
    fun `多次未来复诊按 next_date 升序`() {
        val today = LocalDate.now()
        val far = today.plusDays(60).toString()
        val near = today.plusDays(10).toString()
        io {
            // 故意按「远 → 近」的插入顺序，证明排序来自查询而不是插入顺序
            db.checkupRecordDao().upsert(record("b02-far", today.minusDays(3).toString(), far))
            db.checkupRecordDao().upsert(record("b02-near", today.minusDays(1).toString(), near))
            val dates = repo.checkupReport().nextCheckups
                .filter { it.id.startsWith("b02-") }
                .mapNotNull { it.nextDate }
            assertEquals("必须按 next_date 升序", dates.sorted(), dates)
            assertEquals(listOf(near, far), dates)
        }
    }

    @Test
    fun `空 nextDate 与过去日期仍被排除`() {
        val today = LocalDate.now()
        io {
            db.checkupRecordDao().upsert(record("b02-empty", today.minusDays(1).toString(), null))
            db.checkupRecordDao().upsert(record("b02-blank", today.minusDays(2).toString(), ""))
            db.checkupRecordDao().upsert(record("b02-past", today.minusDays(5).toString(), today.minusDays(2).toString()))
            val ids = repo.checkupReport().nextCheckups.map { it.id }
            assertTrue("空 nextDate 不能进未来复诊", ids.none { it == "b02-empty" || it == "b02-blank" })
            assertTrue("过去日期不能进未来复诊", ids.none { it == "b02-past" })
        }
    }
}
