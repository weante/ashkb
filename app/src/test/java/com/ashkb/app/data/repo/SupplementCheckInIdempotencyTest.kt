package com.ashkb.app.data.repo

import android.content.Context
import com.ashkb.app.data.db.AppDatabase
import com.ashkb.app.data.entity.Supplement
import com.ashkb.app.data.entity.SupplementLog
import com.ashkb.app.domain.AdherenceCalc
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * v1.1.1（HIGH-1）：**补剂打卡的幂等性与依从率分母**。
 *
 * ### 这条缺陷是什么（两个半边，缺一不可）
 * ① 写侧：`SupplementLogDao.find` 用 `slot_key = :slotKey` 比较，而补剂打卡的 `slot_key` 恒为
 *    NULL——SQL 里 `NULL = NULL` 不为真 → 幂等守卫**永远匹配不到** → 每次点击都插入新行。
 * ② 读侧：报表用 `COUNT(*)` 按**行**统计补剂完成度 → 连点三次，分母 +3。
 * 于是"用户手速"能直接改写自己的依从率，而卡片上只有一个胶囊（取 `recordedAt` 最新那条）——
 * 同一个指标两处口径。
 *
 * ### 为什么必须过真库
 * ① 的根因是 **SQL 的 NULL 语义**，只有真的跑一遍 SQLite 才知道 `IS` 是否按预期匹配；
 * ② 涉及窗口查询 + Kotlin 侧去重的组合，纯算术测试证明不了"库里那几行会被算成几次"。
 *
 * 与 [SupplementHistoryQueryTest] 共用两条基建约定（每个用例前清单例；库操作走 IO 线程）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class SupplementCheckInIdempotencyTest {

    private val ctx: Context get() = RuntimeEnvironment.getApplication()
    private val db get() = AppDatabase.get(ctx)
    private val repo by lazy { HealthRepository(ctx) }
    private val report by lazy { ReportRepository(ctx) }

    @Before
    fun resetDatabaseSingleton() {
        val field = AppDatabase::class.java.getDeclaredField("instance")
        field.isAccessible = true
        field.set(null, null)
    }

    private fun <T> io(block: suspend () -> T): T = runBlocking(Dispatchers.IO) { block() }

    private val today: String get() = LocalDate.now().toString()

    @Test
    fun `连点两次打卡只留一行`() {
        io {
            db.supplementDao().upsert(supplement("sup-idem-1"))
            // 与 WellnessViewModel.checkInSupplement 逐字同款：id 空串、不带 slotKey
            repo.checkInSupplement(log("sup-idem-1"))
            repo.checkInSupplement(log("sup-idem-1"))
        }

        val rows = io { db.supplementLogDao().byDate(today) }
        assertEquals(
            "同一天同一补剂连点两次必须只有一行——`slot_key = NULL` 的判等是这条守卫失效的根因",
            1,
            rows.size,
        )
    }

    @Test
    fun `连点后状态取最后一次表态`() {
        io {
            db.supplementDao().upsert(supplement("sup-idem-2"))
            repo.checkInSupplement(log("sup-idem-2", status = AdherenceCalc.SKIPPED, at = "${today}T08:00:00"))
            repo.checkInSupplement(log("sup-idem-2", status = AdherenceCalc.DONE, at = "${today}T20:00:00"))
        }

        val rows = io { db.supplementLogDao().byDate(today) }
        assertEquals(1, rows.size)
        assertEquals("后一次表态必须覆盖前一次（同一行原地更新）", AdherenceCalc.DONE, rows.first().status)
    }

    @Test
    fun `依从率分母不被连点虚增`() {
        val (before, after) = io {
            db.supplementDao().upsert(supplement("sup-idem-3"))
            val b = report.overview(30).supplement.total
            repo.checkInSupplement(log("sup-idem-3"))
            repo.checkInSupplement(log("sup-idem-3"))
            repo.checkInSupplement(log("sup-idem-3"))
            b to report.overview(30).supplement.total
        }

        assertEquals("打卡前没有记录 → 分母 0", 0, before)
        assertEquals("连点三次仍然只算一次——旧实现会给出 3", 1, after)
    }

    @Test
    fun `库里已有的重复行按天去重只算一次`() {
        io {
            db.supplementDao().upsert(supplement("sup-idem-4"))
            // 模拟 v1.1.1 之前写进去的重复行（三条 id 不同、slot_key 全为 NULL）：
            // 写侧修好之后这些行**不会自己消失**，报表侧必须把它们算成一次。
            repeat(3) { i ->
                db.supplementLogDao().upsert(
                    log("sup-idem-4", id = "slog-legacy-$i", at = "${today}T0${8 + i}:00:00"),
                )
            }
        }

        val sup = io { report.overview(30).supplement }
        assertEquals("同一天同一补剂的三行重复记录只能进分母一次", 1, sup.total)
        assertEquals(1, sup.done)
        assertEquals(100, sup.ratePct)
    }

    @Test
    fun `同日先跳过再补记已服只算最后一条`() {
        io {
            db.supplementDao().upsert(supplement("sup-idem-5"))
            db.supplementLogDao().upsert(
                log("sup-idem-5", id = "slog-skip", status = AdherenceCalc.SKIPPED, at = "${today}T08:00:00"),
            )
            db.supplementLogDao().upsert(
                log("sup-idem-5", id = "slog-done", status = AdherenceCalc.DONE, at = "${today}T20:00:00"),
            )
        }

        val sup = io { report.overview(30).supplement }
        assertEquals("两行是同一天的一次表态，不是两次剂量", 1, sup.total)
        assertEquals("最后一次表态是已服", 1, sup.done)
        assertEquals("旧实现会显示约 50%（一行 done + 一行 skipped）", 0, sup.skipped)
        assertEquals(100, sup.ratePct)
    }

    @Test
    fun `不同补剂与不同日期仍然是各自的分母`() {
        val yesterday = LocalDate.now().minusDays(1).toString()
        io {
            db.supplementDao().upsert(supplement("sup-idem-6a"))
            db.supplementDao().upsert(supplement("sup-idem-6b"))
            repo.checkInSupplement(log("sup-idem-6a"))
            repo.checkInSupplement(log("sup-idem-6b"))
            db.supplementLogDao().upsert(log("sup-idem-6a", id = "slog-6a-y", at = "${yesterday}T08:00:00", date = yesterday))
        }

        val sup = io { report.overview(30).supplement }
        assertEquals("去重键是「天 × 补剂」：两个补剂 + 两天 = 3 条", 3, sup.total)
    }

    // ---- 夹具 ----

    private fun supplement(id: String) = Supplement(
        id = id, name = "测试补剂 $id", category = "OTHER", dose = "1 粒",
        createdAt = nowIso(), updatedAt = nowIso(),
    )

    /** 与 `WellnessViewModel.checkInSupplement` 构造的行同款（id 空串、slotKey 缺省为 null）。 */
    private fun log(
        supId: String,
        id: String = "",
        status: String = AdherenceCalc.DONE,
        at: String = nowIso(),
        date: String = today,
    ) = SupplementLog(
        id = id, date = date, recordedAt = at,
        supId = supId, supKey = supId, supName = "测试补剂 $supId",
        doseSnapshot = "1 粒", status = status,
        takenAt = if (status == AdherenceCalc.DONE) at else null,
    )

    @Test
    fun `夹具自身不会退化成同一个 id`() {
        // 防空转断言：上面几条"只留一行"的用例如果因为 id 相同而覆盖，就证明不了幂等守卫在起作用。
        assertNotEquals(log("sup-x", id = "a").id, log("sup-x", id = "b").id)
    }
}
