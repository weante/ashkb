package com.ashkb.app.data.repo

import android.content.Context
import com.ashkb.app.data.db.AppDatabase
import com.ashkb.app.data.entity.CheckupRecord
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * v1.2.7（批次 14 / R3）：**周报「下次复诊日」按 `next_date` 查，不按就诊日查**。
 *
 * ### 这条缺陷是什么
 * 旧实现是 `checkupRecordDao().between(t, to.plusDays(120))`，而 `between` 过滤的是 `date`（就诊日）。
 * `next_date` 是医生写在**就诊当天**的「下次什么时候来」——「本月就诊、约在三个月后复查」的记录，
 * 就诊日早于今天，被 BETWEEN 直接排除 → `nextCheckupDate` **恒为 null**，周报里那一栏永远是空的。
 * 用户看到的是「我明明填了下一次复诊日期，报表却说没有」，而不是任何报错。
 *
 * ### 为什么必须过真库
 * 根因是 **SQL 过滤的是哪一列**：纯函数证明不了 `WHERE next_date BETWEEN …` 真的落在那一列上，
 * 也证明不了返回的是最近一次而不是随便一条。
 *
 * 基建约定沿用 [RecordDeletionCascadeTest]（每个用例前清掉 `AppDatabase` 进程内单例；
 * 库操作一律走 IO 线程）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class NextCheckupDateQueryTest {

    private val ctx: Context get() = RuntimeEnvironment.getApplication()
    private val db get() = AppDatabase.get(ctx)
    private val report by lazy { ReportRepository(ctx) }

    @Before
    fun resetDatabaseSingleton() {
        val field = AppDatabase::class.java.getDeclaredField("instance")
        field.isAccessible = true
        field.set(null, null)
    }

    private fun <T> io(block: suspend () -> T): T = runBlocking(Dispatchers.IO) { block() }

    private val today: LocalDate get() = LocalDate.now()

    @Test
    fun `就诊日已过、约在将来的复诊日仍能报出`() {
        val next = today.plusDays(30).toString()
        io {
            db.checkupRecordDao().upsert(
                checkup("crec-next-1", date = today.minusDays(60).toString(), nextDate = next)
            )
        }

        assertEquals(
            "就诊日早于今天就被 BETWEEN(date…) 排除——这正是周报那一栏永远为空的根因",
            next,
            io { report.periodicReport(30).nextCheckupDate },
        )
    }

    @Test
    fun `多条待复诊时取最近的一次`() {
        val near = today.plusDays(10).toString()
        io {
            db.checkupRecordDao().upsert(
                checkup("crec-next-2a", today.minusDays(30).toString(), today.plusDays(90).toString())
            )
            db.checkupRecordDao().upsert(checkup("crec-next-2b", today.minusDays(10).toString(), near))
        }

        assertEquals(
            "较远的那次（90 天）排在前面的唯一理由只能是查询没按 next_date 升序",
            near,
            io { report.periodicReport(30).nextCheckupDate },
        )
    }

    @Test
    fun `已经错过的下次复诊日与没填的都不报`() {
        io {
            db.checkupRecordDao().upsert(
                checkup("crec-next-3a", today.minusDays(5).toString(), today.minusDays(1).toString())
            )
            db.checkupRecordDao().upsert(checkup("crec-next-3b", today.minusDays(3).toString(), null))
        }

        assertNull(
            "过去的那次复诊日不是「下次复诊」；没填的更不能凭空补一个",
            io { report.periodicReport(30).nextCheckupDate },
        )
    }

    @Test
    fun `超出 120 天视野的复诊日不报`() {
        io {
            db.checkupRecordDao().upsert(
                checkup("crec-next-4", today.minusDays(1).toString(), today.plusDays(200).toString())
            )
        }

        assertNull(
            "报表口径是 120 天内——半年后那次不该占着「下次复诊日」（用户会以为下个月就要去）",
            io { report.periodicReport(30).nextCheckupDate },
        )
    }

    private fun checkup(id: String, date: String, nextDate: String?) = CheckupRecord(
        id = id, date = date, recordedAt = "${date}T09:00:00",
        itemName = "复查", checkType = "LAB", nextDate = nextDate,
    )
}
