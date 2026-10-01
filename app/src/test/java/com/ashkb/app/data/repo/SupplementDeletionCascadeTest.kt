package com.ashkb.app.data.repo

import android.content.Context
import com.ashkb.app.data.db.AppDatabase
import com.ashkb.app.data.entity.Supplement
import com.ashkb.app.data.entity.SupplementLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * v1.0.81（批次 7）：补剂的**两个删除**在真库里的实际范围。
 *
 * 为什么必须过真库：这一批的全部主张都是「删了 A，B 会/不会跟着没了」这种跨表事实——
 * 纯函数证明不了 `DELETE ... WHERE sup_id = ?` 真的只动了这个补剂的行。而且本批的
 * 用户困惑正来自「以为删的是一条记录，实际删的是整个补剂」，这种错位只在库里看得见。
 *
 * 沿用 [RecordDeletionCascadeTest] 的两条基建约定（原因见那里的注释）：
 *  · 每个用例前清掉 [AppDatabase] 的进程内单例；
 *  · 所有库操作走 IO 线程（Room 默认禁止主线程访问，而 Robolectric 的测试线程就是主线程）。
 *
 * 每个用例用**各自独立的 id / 日期**：Robolectric 里库是进程内单例，用例之间会互相看见。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class SupplementDeletionCascadeTest {

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

    // ---- 详情里的删除：只删这一条 ----

    @Test
    fun `删单条记录只删这一条而同一天的另一条还在`() {
        val date = "2026-09-30"
        io {
            db.supplementDao().upsert(supplement("sup-b7-a"))
            db.supplementLogDao().upsert(log("slog-b7-a1", "sup-b7-a", date, "${date}T08:00:00"))
            db.supplementLogDao().upsert(log("slog-b7-a2", "sup-b7-a", date, "${date}T09:00:00"))
        }
        assertEquals(2, io { db.supplementLogDao().countBySupId("sup-b7-a") })

        io { repo.deleteSupplementLog("slog-b7-a1") }

        val left = io { db.supplementLogDao().byDate(date) }.filter { it.supId == "sup-b7-a" }
        assertEquals("只该少一行（同一天连点会留下多行，删一行不能连带删掉别的）", 1, left.size)
        assertEquals("slog-b7-a2", left.first().id)
    }

    @Test
    fun `删单条记录不动同补剂其它日期的记录`() {
        io {
            db.supplementDao().upsert(supplement("sup-b7-b"))
            db.supplementLogDao().upsert(log("slog-b7-b1", "sup-b7-b", "2026-09-29"))
            db.supplementLogDao().upsert(log("slog-b7-b2", "sup-b7-b", "2026-09-28"))
        }

        io { repo.deleteSupplementLog("slog-b7-b1") }

        assertEquals(0, io { db.supplementLogDao().byDate("2026-09-29") }.size)
        assertEquals("别的日期的记录被误删了", "slog-b7-b2", io { db.supplementLogDao().byDate("2026-09-28") }.single().id)
        assertNotNull("补剂档案被误删了", io { db.supplementDao().byId("sup-b7-b") })
    }

    @Test
    fun `删单条记录不动别的补剂`() {
        val date = "2026-09-27"
        io {
            db.supplementDao().upsert(supplement("sup-b7-c"))
            db.supplementDao().upsert(supplement("sup-b7-c-keep"))
            db.supplementLogDao().upsert(log("slog-b7-c1", "sup-b7-c", date))
            db.supplementLogDao().upsert(log("slog-b7-c2", "sup-b7-c-keep", date))
        }

        io { repo.deleteSupplementLog("slog-b7-c1") }

        assertEquals(0, io { db.supplementLogDao().countBySupId("sup-b7-c") })
        assertEquals("别的补剂当天的打卡被误删了", 1, io { db.supplementLogDao().countBySupId("sup-b7-c-keep") })
    }

    // ---- 档案行上的删除：整条 + 级联 ----

    @Test
    fun `删整个补剂会连它的全部记录一起删掉并返回条数`() {
        io {
            db.supplementDao().upsert(supplement("sup-b7-d"))
            db.supplementLogDao().upsert(log("slog-b7-d1", "sup-b7-d", "2026-09-26"))
            db.supplementLogDao().upsert(log("slog-b7-d2", "sup-b7-d", "2026-09-26", "2026-09-26T09:00:00"))
            db.supplementLogDao().upsert(log("slog-b7-d3", "sup-b7-d", "2026-09-25"))
        }

        val removed = io { repo.deleteSupplement("sup-b7-d") }

        assertEquals("确认框要报出的条数与实际删除不符", 3, removed)
        assertNull("补剂档案没删掉", io { db.supplementDao().byId("sup-b7-d") })
        assertEquals("名下的记录成了 sup_id 悬空的孤儿行", 0, io { db.supplementLogDao().countBySupId("sup-b7-d") })
        assertEquals(0, io { db.supplementLogDao().byDate("2026-09-25") }.size)
    }

    @Test
    fun `删整个补剂时窗口外的老记录也一并删掉`() {
        io {
            db.supplementDao().upsert(supplement("sup-b7-e"))
            // 详情弹层只列最近 90 天，但「删掉这个补剂」必须是删掉它的全部记录——
            // 只删窗口内的会让 90 天前的记录永远留在库里且无人可见、无人能删
            db.supplementLogDao().upsert(log("slog-b7-e-old", "sup-b7-e", "2026-01-05"))
            db.supplementLogDao().upsert(log("slog-b7-e-new", "sup-b7-e", "2026-09-24"))
        }

        val removed = io { repo.deleteSupplement("sup-b7-e") }

        assertEquals(2, removed)
        assertEquals("窗口外的老记录没被删掉", 0, io { db.supplementLogDao().byDate("2026-01-05") }.size)
    }

    @Test
    fun `删整个补剂不碰别的补剂的记录`() {
        io {
            db.supplementDao().upsert(supplement("sup-b7-f"))
            db.supplementDao().upsert(supplement("sup-b7-f-keep"))
            db.supplementLogDao().upsert(log("slog-b7-f1", "sup-b7-f", "2026-09-23"))
            db.supplementLogDao().upsert(log("slog-b7-f2", "sup-b7-f-keep", "2026-09-23"))
        }

        io { repo.deleteSupplement("sup-b7-f") }

        assertNull(io { db.supplementDao().byId("sup-b7-f") })
        assertNotNull("别的补剂档案被误删了", io { db.supplementDao().byId("sup-b7-f-keep") })
        assertEquals("别的补剂的记录被误删了", 1, io { db.supplementLogDao().countBySupId("sup-b7-f-keep") })
    }

    @Test
    fun `删不存在的补剂返回 0 且零写入`() {
        io {
            db.supplementDao().upsert(supplement("sup-b7-g"))
            db.supplementLogDao().upsert(log("slog-b7-g1", "sup-b7-g", "2026-09-22"))
        }

        val removed = io { repo.deleteSupplement("sup-不存在") }

        assertEquals(0, removed)
        assertNotNull("零写入被破坏", io { db.supplementDao().byId("sup-b7-g") })
        assertEquals(1, io { db.supplementLogDao().countBySupId("sup-b7-g") })
    }

    @Test
    fun `删除前的计数与实际删掉的条数一致且计数无副作用`() {
        io {
            db.supplementDao().upsert(supplement("sup-b7-h"))
            db.supplementDao().upsert(supplement("sup-b7-h-keep"))
            db.supplementLogDao().upsert(log("slog-b7-h1", "sup-b7-h", "2026-09-21"))
            db.supplementLogDao().upsert(log("slog-b7-h2", "sup-b7-h", "2026-09-20"))
            db.supplementLogDao().upsert(log("slog-b7-h3", "sup-b7-h-keep", "2026-09-20"))
        }

        val counted = io { repo.countSupplementLogs("sup-b7-h") }

        assertEquals(2, counted)
        assertEquals(
            "计数不该有副作用（提前把数据删了）",
            2,
            io { db.supplementLogDao().countBySupId("sup-b7-h") },
        )
        assertEquals("报出的条数必须与实际删掉的条数是同一个数", counted, io { repo.deleteSupplement("sup-b7-h") })
        assertEquals("别的补剂的记录被算进了本条的条数", 1, io { db.supplementLogDao().countBySupId("sup-b7-h-keep") })
    }

    // ---- 夹具 ----

    private fun supplement(id: String) = Supplement(
        id = id, name = "测试补剂 $id", category = "OTHER", dose = "1 粒",
        createdAt = nowIso(), updatedAt = nowIso(),
    )

    /** 一次补剂打卡（`slot_key` 留空 = 补剂打卡的真实形态，见 `WellnessViewModel.checkInSupplement`）。 */
    private fun log(
        id: String,
        supId: String,
        date: String,
        recordedAt: String = "${date}T08:00:00",
    ) = SupplementLog(
        id = id, date = date, recordedAt = recordedAt,
        supId = supId, supKey = supId, supName = "测试补剂 $supId",
        doseSnapshot = "1 粒", status = "done", takenAt = recordedAt,
    )
}
