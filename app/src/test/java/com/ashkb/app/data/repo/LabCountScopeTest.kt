package com.ashkb.app.data.repo

import android.content.Context
import com.ashkb.app.data.db.AppDatabase
import com.ashkb.app.data.entity.LabResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * v1.0.87（批次 13）：健康页摘要「化验 N 项」的**口径**（Robolectric + 真库）。
 *
 * 维护者真机反馈：摘要写「化验 100 条」，删掉一条 4 项化验单后**没变**。
 * 原口径是 `CheckupViewModel.labRecent.size`——那是**分页窗口**（初值 100）的长度，
 * 库里行数 ≥ 窗口时它恒等于窗口值，删几行当然不动。本文件把两种口径的差别钉在库里：
 *  · [HealthRepository.observeLabRecent]（窗口）：装不下就截断在窗口大小；
 *  · [HealthRepository.observeLabCount]（总数）：COUNT(*)，随增删改变化。
 *
 * 断言一律用**增量**（先量一次再量一次）而不是绝对值：Robolectric 里 [AppDatabase] 是进程内
 * 单例、化验表在用例之间可能互相看得见（同 [LabAbnormalPriorityTest] 的约定），
 * 只有「相对变化」是稳定的；用例之间的指标名也互不重叠。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class LabCountScopeTest {

    private val ctx: Context get() = RuntimeEnvironment.getApplication()
    private val repo by lazy { HealthRepository(ctx) }
    private val db get() = AppDatabase.get(ctx)

    @Before
    fun resetDatabaseSingleton() {
        val field = AppDatabase::class.java.getDeclaredField("instance")
        field.isAccessible = true
        field.set(null, null)
    }

    private fun <T> io(block: suspend () -> T): T = runBlocking(Dispatchers.IO) { block() }

    private fun seed(tag: String, count: Int, date: String) = io {
        repeat(count) { i ->
            db.labResultDao().upsert(
                LabResult(
                    id = "lab-$tag-$i", date = date, recordedAt = "${date}T08:00:00",
                    testName = "$tag 指标 $i", value = 1.0, unit = "x",
                ),
            )
        }
    }

    /**
     * 窗口口径 vs 总数口径：一次种入 120 行时，窗口只能给 100（旧摘要显示的那个数），
     * 总数口径给的是真实行数。这条用例就是维护者那句「100 条对不上」的根因。
     */
    @Test
    fun `行数超过窗口时总数与窗口长度不同`() {
        val before = io { repo.observeLabCount().first() }
        seed(tag = "scope-a", count = 120, date = "2026-03-01")

        val window = io { repo.observeLabRecent(limit = 100).first() }
        val after = io { repo.observeLabCount().first() }

        assertEquals("窗口应被截断在 limit", 100, window.size)
        assertEquals("总数口径必须如实反映新增的 120 行", 120, after - before)
    }

    /**
     * 「删掉 4 条后数字没变」——旧口径（窗口长度）在饱和时不动，总数口径会跟着变。
     * 这正是这一批要修的刷新语义。
     */
    @Test
    fun `删除后总数会变而饱和窗口长度不变`() {
        val before = io { repo.observeLabCount().first() }
        seed(tag = "scope-b", count = 120, date = "2026-04-01")
        assertEquals(120, io { repo.observeLabCount().first() } - before)

        io { repeat(4) { i -> db.labResultDao().delete("lab-scope-b-$i") } }

        val totalAfter = io { repo.observeLabCount().first() }
        val windowAfter = io { repo.observeLabRecent(limit = 100).first() }

        assertEquals("总数必须随删除更新（旧口径正是在这里不动）", 116, totalAfter - before)
        assertEquals("窗口仍是满的：这解释了「删掉 4 条后摘要没变」", 100, windowAfter.size)
    }

    /** 行数少于窗口时两种口径一致（窗口不是上限，只是分页大小）。 */
    @Test
    fun `行数少于窗口时总数等于窗口长度`() {
        val before = io { repo.observeLabCount().first() }
        seed(tag = "scope-c", count = 7, date = "2026-05-01")

        val window = io { repo.observeLabRecent(limit = 100).first() }
        val after = io { repo.observeLabCount().first() }

        assertEquals("不足窗口时应全部返回", after - before, window.size)
        assertEquals(7, after - before)
    }
}
