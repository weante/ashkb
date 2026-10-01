package com.ashkb.app.data.repo

import android.content.Context
import com.ashkb.app.data.db.AppDatabase
import com.ashkb.app.data.entity.Supplement
import com.ashkb.app.data.entity.SupplementLog
import com.ashkb.app.domain.SupplementHistory
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
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
 * v1.0.81（批次 7）：详情弹层「最近服用记录」的数据源——`SupplementLogDao.observeHistoryFor`。
 *
 * 为什么过真库：这段取数全靠 SQL 表达（窗口 / 只取 done / 两级倒序 / 按名称快照兜底），
 * 只有真的跑一遍 SQLite 才知道 ORDER BY 与 WHERE 是不是写成了想要的样子。
 * 弹层的顺序错了不会崩、也不会有人报错——只是「最近服用记录」不再是最新的，静默地骗人。
 *
 * 与 [SupplementDeletionCascadeTest] 共用两条基建约定（每个用例前清单例；库操作走 IO 线程）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class SupplementHistoryQueryTest {

    private val ctx: Context get() = RuntimeEnvironment.getApplication()
    private val db get() = AppDatabase.get(ctx)
    private val repo by lazy { HealthRepository(ctx) }

    @Before
    fun resetDatabaseSingleton() {
        val field = AppDatabase::class.java.getDeclaredField("instance")
        field.isAccessible = true
        field.set(null, null)
    }

    private fun <T> io(block: suspend () -> T): T = runBlocking(Dispatchers.IO) { block() }

    /** 相对今天取日期：窗口是按 `LocalDate.now()` 算的，写死日期会在 90 天后自己失效。 */
    private fun daysAgo(days: Long): String = LocalDate.now().minusDays(days).toString()

    @Test
    fun `没有记录时是空列表`() {
        io { db.supplementDao().upsert(supplement("sup-b7-h1")) }

        assertTrue(
            "空态走的是 UI 的 EmptyState 分支，这里必须真的返回空而不是 null 或占位行",
            io { repo.observeSupplementHistory("sup-b7-h1", "测试补剂 sup-b7-h1").first() }.isEmpty(),
        )
    }

    @Test
    fun `只有跳过状态的记录时同样是空列表`() {
        val date = daysAgo(1)
        io {
            db.supplementDao().upsert(supplement("sup-b7-h2"))
            // skipped = 「今天决定不吃」，不是一次服用——它不该出现在「已服」历史里
            db.supplementLogDao().upsert(log("slog-b7-h2-1", "sup-b7-h2", date, status = "skipped"))
        }

        assertTrue(io { repo.observeSupplementHistory("sup-b7-h2", "测试补剂 sup-b7-h2").first() }.isEmpty())
    }

    @Test
    fun `按日期倒序返回`() {
        io {
            db.supplementDao().upsert(supplement("sup-b7-h3"))
            db.supplementLogDao().upsert(log("slog-b7-h3-1", "sup-b7-h3", daysAgo(3)))
            db.supplementLogDao().upsert(log("slog-b7-h3-2", "sup-b7-h3", daysAgo(1)))
            db.supplementLogDao().upsert(log("slog-b7-h3-3", "sup-b7-h3", daysAgo(2)))
        }

        val rows = io { repo.observeSupplementHistory("sup-b7-h3", "测试补剂 sup-b7-h3").first() }

        assertEquals("弹层顶部是「最近」，顺序反了用户第一眼看到的就是最老的一次", 3, rows.size)
        assertEquals(listOf(daysAgo(1), daysAgo(2), daysAgo(3)), rows.map { it.date })
    }

    @Test
    fun `同一天内按记录时间倒序`() {
        val date = daysAgo(4)
        io {
            db.supplementDao().upsert(supplement("sup-b7-h4"))
            // 同一天连点两次打卡会留下两行（slot_key 为 NULL，SQLite 里 NULL 互不相等）
            db.supplementLogDao().upsert(log("slog-b7-h4-1", "sup-b7-h4", date, recordedAt = "${date}T08:00:00"))
            db.supplementLogDao().upsert(log("slog-b7-h4-2", "sup-b7-h4", date, recordedAt = "${date}T20:00:00"))
        }

        val rows = io { repo.observeSupplementHistory("sup-b7-h4", "测试补剂 sup-b7-h4").first() }

        assertEquals(2, rows.size)
        assertEquals("同一天里最近一次记录必须排在最上面", "slog-b7-h4-2", rows.first().id)
    }

    @Test
    fun `窗口外的老记录不出现`() {
        io {
            db.supplementDao().upsert(supplement("sup-b7-h5"))
            db.supplementLogDao().upsert(log("slog-b7-h5-keep", "sup-b7-h5", daysAgo(SupplementHistory.WINDOW_DAYS - 1)))
            db.supplementLogDao().upsert(log("slog-b7-h5-drop", "sup-b7-h5", daysAgo(SupplementHistory.WINDOW_DAYS + 1)))
        }

        val rows = io { repo.observeSupplementHistory("sup-b7-h5", "测试补剂 sup-b7-h5").first() }

        assertEquals("窗口内的一条必须留着", listOf("slog-b7-h5-keep"), rows.map { it.id })
    }

    @Test
    fun `sup_id 为空但名称快照相同的记录也算这个补剂的历史`() {
        val date = daysAgo(5)
        io {
            db.supplementDao().upsert(supplement("sup-b7-h6"))
            // 补剂被真删后重新添加同名的，旧记录只剩名称快照可追（SQL 的 sup_id IS NULL 分支）——
            // 这条兜底是弹层「历史还能看见」的前提，不能被后来的改动顺手删掉
            db.supplementLogDao().upsert(log("slog-b7-h6-1", "sup-b7-h6", date))
            db.supplementLogDao().upsert(log("slog-b7-h6-snapshot", null, date, supName = "测试补剂 sup-b7-h6"))
        }

        val rows = io { repo.observeSupplementHistory("sup-b7-h6", "测试补剂 sup-b7-h6").first() }

        assertEquals(listOf("slog-b7-h6-1", "slog-b7-h6-snapshot"), rows.map { it.id }.sorted())
    }

    @Test
    fun `别的补剂的记录不会混进来`() {
        io {
            db.supplementDao().upsert(supplement("sup-b7-h7"))
            db.supplementLogDao().upsert(log("slog-b7-h7-1", "sup-b7-h7", daysAgo(6)))
            db.supplementLogDao().upsert(log("slog-b7-h7-other", "sup-b7-h7-别的补剂", daysAgo(6)))
        }

        val rows = io { repo.observeSupplementHistory("sup-b7-h7", "测试补剂 sup-b7-h7").first() }

        assertEquals(listOf("slog-b7-h7-1"), rows.map { it.id })
    }

    // ---- 夹具 ----

    private fun supplement(id: String) = Supplement(
        id = id, name = "测试补剂 $id", category = "OTHER", dose = "1 粒",
        createdAt = nowIso(), updatedAt = nowIso(),
    )

    private fun log(
        id: String,
        supId: String?,
        date: String,
        recordedAt: String = "${date}T08:00:00",
        status: String = "done",
        supName: String = "测试补剂 $supId",
    ) = SupplementLog(
        id = id, date = date, recordedAt = recordedAt,
        supId = supId, supKey = id, supName = supName,
        doseSnapshot = "1 粒", status = status,
        takenAt = if (status == "done") recordedAt else null,
    )
}
