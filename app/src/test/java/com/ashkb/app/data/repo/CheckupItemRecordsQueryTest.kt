package com.ashkb.app.data.repo

import android.content.Context
import com.ashkb.app.data.db.AppDatabase
import com.ashkb.app.data.entity.CheckupItem
import com.ashkb.app.data.entity.CheckupRecord
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
 * v1.0.87（批次 13）：**「项目」→「记录」的项目筛选**到底能筛出什么（Robolectric + 真库）。
 *
 * 为什么必须过真库：这一批的核心是「维护者点『MRI』要能看到 MRI 的记录」，而能不能看到
 * 完全取决于 SQL 的匹配条件与库里的真实数据形态——纯函数证明不了 SQL 选对了行。
 *
 * 本文件锁住三条事实：
 *  ① `checkup_records.item_id` 列**至今没有写入路径**（表单只填自由文本 `item_name`），
 *     故现存记录的 `item_id` 全是 NULL：只按 item_id 过滤会筛出空列表；
 *  ② 显式关联（item_id）与名字快照（item_name）**都要认**；
 *  ③ 筛选走 SQL，不受「最近 N 条」窗口的限制——内存筛选会把窗口外的老记录判成「没有记录」。
 *
 * 沿用 [LabAbnormalPriorityTest] 的两条基建约定：每个用例前清掉 [AppDatabase] 的进程内单例；
 * 所有库操作走 IO 线程（Room 默认禁止主线程访问，而 Robolectric 的测试线程就是主线程）。
 * 用例之间用**互不重叠的 id / 名字**，不依赖「每个方法一个干净库」。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class CheckupItemRecordsQueryTest {

    private val ctx: Context get() = RuntimeEnvironment.getApplication()
    private val repo by lazy { HealthRepository(ctx) }

    @Before
    fun resetDatabaseSingleton() {
        val field = AppDatabase::class.java.getDeclaredField("instance")
        field.isAccessible = true
        field.set(null, null)
    }

    private fun <T> io(block: suspend () -> T): T = runBlocking(Dispatchers.IO) { block() }

    private fun item(id: String, name: String) = CheckupItem(
        id = id, name = name, checkType = "IMAGE",
        createdAt = "2026-10-01T00:00:00", updatedAt = "2026-10-01T00:00:00",
    )

    private fun record(
        id: String,
        date: String,
        itemName: String,
        itemId: String? = null,
    ) = CheckupRecord(
        id = id, date = date, recordedAt = "${date}T09:00:00",
        itemId = itemId, itemName = itemName, checkType = "IMAGE",
    )

    /**
     * 筛选条件必须是 `item_id OR item_name`：库里两条真实形态的记录都要能被筛出来——
     * 一条带显式关联（item_id 命中，名字是别的写法），一条只有名字快照（现存记录的真实形态）。
     */
    @Test
    fun `按项目筛选同时认显式关联与名字快照`() {
        val mri = item("cki-q-mri", "MRI")
        io {
            repo.saveCheckupItem(mri)
            repo.saveCheckupRecord(record("crec-q-1", "2026-09-01", "核磁共振（骶髂关节）", itemId = mri.id))
            repo.saveCheckupRecord(record("crec-q-2", "2026-08-01", "MRI"))
            // 别的项目：名字不同、item_id 也不指向 MRI —— 不能被筛进来
            repo.saveCheckupRecord(record("crec-q-3", "2026-07-01", "血常规", itemId = "cki-q-other"))
            // 名字相近但不是同一个项目（快照口径是精确匹配）
            repo.saveCheckupRecord(record("crec-q-4", "2026-06-01", "MRI 复查"))
        }

        val found = io { repo.observeCheckupByItem(mri.id, mri.name, limit = 50).first() }

        assertEquals("筛选结果条数不对", 2, found.size)
        // 按日期倒序：9-01 在 8-01 之前
        assertEquals(listOf("crec-q-1", "crec-q-2"), found.map { it.id })
    }

    /** 项目名下一条记录都没有时必须是**空**（界面据此显示「该项目暂无复诊记录」）。 */
    @Test
    fun `项目没有记录时筛出空列表`() {
        val empty = item("cki-q-empty", "眼科年检")
        io { repo.saveCheckupItem(empty) }

        val found = io { repo.observeCheckupByItem(empty.id, empty.name, limit = 50).first() }

        assertTrue("没有记录的项目不该筛出任何行，实际：${found.map { it.id }}", found.isEmpty())
    }

    /**
     * 筛选不能受「最近 N 条」窗口限制。
     *
     * 这条用例是**做法本身**的证明：若在内存里对 `observeCheckupRecent(50)` 的结果做筛选，
     * 该项目那条被 60 条更新的记录挤出窗口的老记录就会消失——用户看到「该项目没有记录」，
     * 而库里明明有。故筛选必须在 SQL 里做。
     */
    @Test
    fun `窗口外的老记录也能被筛出来`() {
        val old = item("cki-q-old", "骨密度")
        io {
            repo.saveCheckupItem(old)
            // 该项目唯一的一条记录：日期最老，会被 60 条更新的记录挤出「最近 50 条」窗口
            repo.saveCheckupRecord(record("crec-q-oldest", "2020-01-01", "骨密度"))
            repeat(60) { i ->
                repo.saveCheckupRecord(record("crec-q-filler-$i", "2026-05-01", "血常规", itemId = "cki-q-filler"))
            }
        }

        val window = io { repo.observeCheckupRecent(limit = 50).first() }
        val found = io { repo.observeCheckupByItem(old.id, old.name, limit = 50).first() }

        assertTrue(
            "前置条件不成立：那条老记录应已被挤出最近 50 条窗口（窗口 ${window.size} 条）",
            window.none { it.id == "crec-q-oldest" },
        )
        assertEquals("筛选走 SQL 时应仍能找到窗口外的老记录", listOf("crec-q-oldest"), found.map { it.id })
    }
}
