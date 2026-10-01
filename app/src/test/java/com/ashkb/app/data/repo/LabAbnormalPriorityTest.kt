package com.ashkb.app.data.repo

import android.content.Context
import com.ashkb.app.data.db.AppDatabase
import com.ashkb.app.data.entity.LabResult
import com.ashkb.app.domain.LabImport
import com.ashkb.app.domain.LabImportRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
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
 * v1.0.78（批次 4 收尾）：**本地判读优先 + AI 标记并列展示**的落库语义（库 v19 的 `ai_abnormal`）。
 *
 * 为什么要拿真库真仓库来测：这条口径的全部风险都在「谁覆盖谁」上——
 * v1.0.77 的本地判读把 AI 原始标记**就地覆盖**，于是「AI 说正常、本地判读偏高」这类分歧
 * 在库里彻底消失（第三份审查报告 S-12）。这种「写进去之后还能不能读出来」的事，
 * 只有真的过一遍 Room 才算数，纯函数测试证明不了。
 *
 * 每个用例用**各不相同的指标名**：Robolectric 里 [AppDatabase] 是进程内单例，
 * 同名字段在方法之间可能互相看见，用独立名字就不必依赖「每个方法一个干净库」这种前提。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class LabAbnormalPriorityTest {

    private val ctx: Context get() = RuntimeEnvironment.getApplication()
    private val repo by lazy { HealthRepository(ctx) }
    private val dao by lazy { AppDatabase.get(ctx).labResultDao() }

    /**
     * 每个用例都要一份**全新的库**。
     *
     * Robolectric 每个测试方法都会重建 Application 并重置 SQLite 影子状态（旧连接指针随即失效），
     * 而 [AppDatabase] 是**进程内单例**——它会把上一个用例的库实例（连着已失效的连接）带进来，
     * 于是第二个用例必定撞上 `Illegal connection pointer`。生产代码里没有、也不该有「重置单例」的入口，
     * 故只在测试侧清掉那个私有静态字段（字段一旦改名会立刻抛 `NoSuchFieldException`，不会静默放过）。
     */
    @Before
    fun resetDatabaseSingleton() {
        val field = AppDatabase::class.java.getDeclaredField("instance")
        field.isAccessible = true
        field.set(null, null)
    }

    /**
     * Room 默认**禁止主线程访问**，而 Robolectric 的测试线程正是主线程——
     * 故所有库操作都丢到 IO 线程上跑（这也是生产代码里协程的真实处境）。
     */
    private fun <T> io(block: suspend () -> T): T = runBlocking(Dispatchers.IO) { block() }

    private fun importOne(row: LabImportRow) = io {
        repo.importLabReport(LabImport(date = "2026-08-02", hospital = null, note = null, rows = listOf(row)))
    }

    private fun saved(testName: String): LabResult =
        io { dao.observeTrend(testName).first() }.first { it.testName == testName }

    @Test
    fun `AI 标记与本地判读不一致时两列都保留`() {
        val name = "TEST-ESR-AI正常本地偏高"
        importOne(
            LabImportRow(
                testName = name, value = 25.0, valueText = "25", unit = "mm/h",
                refLow = 0.0, refHigh = 20.0,
                abnormal = "normal", aiAbnormal = "normal",
            )
        )
        val row = saved(name)
        // AI 原始标记必须原样留档——被覆盖掉就没有「并列展示」可言
        assertEquals("AI 原始标记被本地判读覆盖了", "normal", row.aiAbnormal)
        // 本地参考范围判读（25 > 20）必须赢：这正是 v1.0.77 要修的那类静默漏报
        assertEquals("本地判读没有覆盖 abnormal", "high", row.abnormal)
    }

    @Test
    fun `本地判读不覆盖 AI 原始标记`() {
        val name = "TEST-ESR-AI偏高本地正常"
        importOne(
            LabImportRow(
                testName = name, value = 15.0, valueText = "15", unit = "mm/h",
                refLow = 0.0, refHigh = 20.0,
                abnormal = "high", aiAbnormal = "high",
            )
        )
        val row = saved(name)
        // 本地判读说正常，但**不许回头改写** AI 当初标了什么——两列各自独立
        assertEquals("本地判读说正常", "normal", row.abnormal)
        assertEquals("AI 原始标记被本地判读改写了", "high", row.aiAbnormal)
    }

    @Test
    fun `没有参考范围时 abnormal 沿用 AI 兜底值`() {
        val name = "TEST-HBSAG-无参考范围"
        importOne(
            LabImportRow(
                testName = name, value = null, valueText = "阴性", unit = null,
                refLow = null, refHigh = null,
                abnormal = "normal", aiAbnormal = "normal",
            )
        )
        val row = saved(name)
        assertEquals("本地判不了时应保留 AI 值兜底", "normal", row.abnormal)
        assertEquals("normal", row.aiAbnormal)
    }

    @Test
    fun `手工录入的本地判读不会凭空产生 AI 标记`() {
        val name = "TEST-CRP-手工录入"
        io {
            repo.saveLabResult(
                LabResult(
                    id = "", date = "2026-08-02", recordedAt = "2026-08-02T08:00:00",
                    testName = name, value = 25.0, refLow = 0.0, refHigh = 20.0,
                )
            )
        }
        val row = saved(name)
        assertEquals("high", row.abnormal)
        assertNull("非 AI 导入的行不该有 AI 标记", row.aiAbnormal)
    }
}
