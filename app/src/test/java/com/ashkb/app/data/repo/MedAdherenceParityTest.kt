package com.ashkb.app.data.repo

import android.content.Context
import com.ashkb.app.data.db.AppDatabase
import com.ashkb.app.data.entity.MedicationLog
import com.ashkb.app.domain.AdherenceCalc
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * v1.2.10（审查 §6.10）：**报表的用药完成度与药单页弹层是同一个口径**。
 *
 * ### 这条缺陷是什么
 * 改前 `ReportRepository.overview()` / `periodicReport()` 各写三条
 * `countScheduledBetweenStatus(f, t, 状态)` 字面量 SQL 再求和，分母只认它列举得出的
 * `done / partial / skipped`；而药单页的「用药记录」弹层一直走
 * `AdherenceCalc.completion(logs)`，[AdherenceCalc.completionByStatus] 把**认不出的状态**
 * 也计入 `total`（取值偏保守）。
 *
 * 于是同一批用药记录在两页上有两个完成度：报表说「共 10 条记录、90%」，药单页说
 * 「共 11 条记录、82%」。用户不会把这两页并排比，所以它比「同一张卡片上两个百分比打架」
 * 更隐蔽——`MedsScreen.kt` 的 KDoc 甚至明写「口径与报表**完全同源**」，而当时那句话是假的。
 *
 * ### 为什么必须过真库
 * 根因是**报表取了哪一份数**：`COUNT(*)` 的 `WHERE status = :status` 到底漏掉了什么，
 * 纯函数证明不了——纯函数两边都调 `AdherenceCalc` 的话，测试本身就把缺陷假设掉了。
 * 所以这里写真库、调真 `ReportRepository`，再把结果与「同一批记录直接过
 * `AdherenceCalc.completion`」比对：两条路必须给出同一个 `total` / `done` / `ratePct`。
 *
 * 基建约定沿用 [NextCheckupDateQueryTest]（每个用例前清掉 `AppDatabase` 进程内单例；
 * 库操作一律走 IO 线程）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class MedAdherenceParityTest {

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

    private suspend fun addLogs(vararg logs: MedicationLog) {
        for (l in logs) db.medicationLogDao().upsert(l)
    }

    private fun log(id: String, status: String, prn: Boolean = false, daysAgo: Long = 0) = MedicationLog(
        id = id,
        date = today.minusDays(daysAgo).toString(),
        recordedAt = nowIso(),
        medId = "med-parity",
        medKey = "测试药",
        medName = "测试药",
        doseSnapshot = "1 片",
        status = status,
        prnFlag = prn,
    )

    /**
     * **本批的主断言**：报表概览里的 `record` 与药单页弹层（`AdherenceCalc.completion`）
     * 对同一批记录给出同一个分母。
     *
     * 三条记录里有一条是 `AdherenceCalc.completionByStatus` 认不出的状态（模拟旧备份 /
     * 手工导入的脏值）：改前的报表会把它整个丢掉（total=2），改后它与药单页一致地进分母（total=3）。
     */
    @Test
    fun `报表与药单页的分母是同一个数（含认不出的状态）`() {
        io {
            addLogs(
                log("mlog-p1", AdherenceCalc.DONE),
                log("mlog-p2", AdherenceCalc.SKIPPED, daysAgo = 1),
                log("mlog-p3", "something_new", daysAgo = 2),
            )
        }

        val overview = io { report.overview(30) }
        val record = overview.adherence.record

        val allLogs = io { db.medicationLogDao().listBetween(today.minusDays(29).toString(), today.toString()) }
        val direct = AdherenceCalc.completion(allLogs)

        assertEquals("报表分母必须与药单页同源", direct.total, record.total)
        assertEquals("完成数同源", direct.done, record.done)
        assertEquals("部分完成数同源", direct.partial, record.partial)
        assertEquals("跳过数同源", direct.skipped, record.skipped)
        assertEquals("百分比同源", direct.ratePct, record.ratePct)
        assertEquals("认不出的状态要进分母（取值保守，不假装它不存在）", 3, record.total)
    }

    /**
     * 回归：老数据（只有 done）的数字一个都不能变。
     *
     * 改前那条 SQL 路径在「全库只有 done」时给出的分母就是 done 条数，`ratePct` 是 100。
     * 本用例把这份数字写死，防止日后有人顺手把 `completion` 的语义改成「先过滤已结算」——
     * 那会让认不出的状态从分母里消失，而这条断言不会红（它只有 done），所以另配下面的脏值用例。
     */
    @Test
    fun `只有 done 的老数据报表给 100 且分母等于记录条数`() {
        io { addLogs(log("mlog-l1", AdherenceCalc.DONE), log("mlog-l2", AdherenceCalc.DONE, daysAgo = 3)) }

        val a = io { report.overview(30) }.adherence
        assertEquals(2, a.medTotal)
        assertEquals(2, a.medDone)
        assertEquals(0, a.medSkipped)
        assertEquals(100, a.medRatePct)
        assertEquals("record 与展平后的字段必须同源", a.medTotal, a.record.total)
    }

    /**
     * PRN（按需）记录整体不进这个指标——只由 `medPrnCount` 单独报数。
     * 这条同时锁住「报表改走 `AdherenceCalc.completion` 之后 PRN 排除仍然生效」：
     * 旧 SQL 靠 `prn_flag = 0` 排除，新路径靠 `completion` 内部的 `filterNot { it.prnFlag }`。
     */
    @Test
    fun `按需记录不进分母只单独报数`() {
        io {
            addLogs(
                log("mlog-d1", AdherenceCalc.DONE),
                log("mlog-d2", AdherenceCalc.DONE, daysAgo = 1),
                log("mlog-prn1", AdherenceCalc.DONE, prn = true),
                log("mlog-prn2", AdherenceCalc.DONE, prn = true, daysAgo = 1),
            )
        }

        val a = io { report.overview(30) }.adherence
        assertEquals("分母只算计划打卡", 2, a.medTotal)
        assertEquals("PRN 单独报数", 2, a.medPrnCount)
        assertEquals(100, a.medRatePct)
    }

    /** 周月报与概览同源：同一批记录、同一个 `days` 窗口，两个入口的总数必须相等。 */
    @Test
    fun `概览与周月报对同一窗口给出同一个分母`() {
        io {
            addLogs(
                log("mlog-w1", AdherenceCalc.DONE),
                log("mlog-w2", AdherenceCalc.PARTIAL, daysAgo = 1),
                log("mlog-w3", "something_new", daysAgo = 2),
                log("mlog-w4", AdherenceCalc.SKIPPED, daysAgo = 3),
            )
        }

        val o = io { report.overview(30) }.adherence
        val p = io { report.periodicReport(30) }

        assertEquals(o.medTotal, p.medTotal)
        assertEquals(o.medDone, p.medDone)
        assertEquals(o.medPartial, p.medPartial)
        assertEquals(o.medSkipped, p.medSkipped)
        assertEquals(o.medRatePct, p.medRatePct)
        assertEquals(4, p.medTotal)
    }

    /**
     * 窗口边界：`periodicReport(7)` 看的是闭区间 [今天-6, 今天]——第 6 天前那条**要**算，
     * 第 7 天前那条**不**算。这条防的是「改走 `listBetween` 之后窗口边界被写偏一」（`days - 1`
     * 那个 `-1` 是闭区间的代价，漏掉它每周就多算或少算一天）。
     */
    @Test
    fun `周月报窗口是闭区间`() {
        io {
            addLogs(
                log("mlog-in1", AdherenceCalc.DONE),
                log("mlog-edge1", AdherenceCalc.DONE, daysAgo = 6),
                log("mlog-out1", AdherenceCalc.DONE, daysAgo = 7),
            )
        }

        assertEquals("7 天窗口 = [今天-6, 今天]", 2, io { report.periodicReport(7) }.medTotal)
        assertEquals("30 天窗口三条都在", 3, io { report.periodicReport(30) }.medTotal)
    }
}
