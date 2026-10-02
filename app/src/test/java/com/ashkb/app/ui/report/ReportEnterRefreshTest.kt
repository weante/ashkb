package com.ashkb.app.ui.report

import android.content.Context
import android.os.Looper
import com.ashkb.app.data.db.AppDatabase
import com.ashkb.app.data.entity.Supplement
import com.ashkb.app.data.entity.SupplementLog
import com.ashkb.app.data.repo.ReportRepository
import com.ashkb.app.data.repo.nowIso
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
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * v1.1.1（HIGH-3）：**重新进入报表页必须重查，而不是沿用上一次的缓存**。
 *
 * 缺陷形态：报表 VM 由 `AppShell` 的 `composable<Report>` 持有（作用域 = 该路由的返回栈条目），
 * 「报表页 → 备份页恢复数据 → 返回报表页」时 VM 还活着，而 v1.0.86 的守卫
 * `if (_overview.value != null || _busy.value) return` 会让返回后的那次取数变成空操作——
 * 用户看到的是**恢复前**的数字，界面上没有任何提示。周月报的 `loadedPeriodDays` 参数级去重
 * 有同样的问题（同参数重进 = 空操作）。
 *
 * ### 为什么这是本仓库第一个真正驱动 ViewModel 的测试
 * 这条缺陷**只存在于 VM 的缓存判据里**：仓库层每次都老实查库，UI 层每次都老实调用。
 * 纯算术或源码扫描都证明不了"第二次进入时数字变了没有"，只有把真 VM + 真库跑起来才能钉住。
 * 这里不引入任何测试依赖（不用 `coroutines-test`）：`viewModelScope` 在 Robolectric 下走主
 * Looper，用 `shadowOf(Looper.getMainLooper()).idle()` 推进即可。
 *
 * 反过来说，本用例在**旧实现**上必须是红的——这正是它存在的意义（`onEnterReport` 之前的
 * `loadOnce` 会让第二条断言停在 1）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class ReportEnterRefreshTest {

    private val ctx: Context get() = RuntimeEnvironment.getApplication()
    private val db get() = AppDatabase.get(ctx)

    @Before
    fun resetDatabaseSingleton() {
        val field = AppDatabase::class.java.getDeclaredField("instance")
        field.isAccessible = true
        field.set(null, null)
    }

    private fun <T> io(block: suspend () -> T): T = runBlocking(Dispatchers.IO) { block() }

    private fun newVm() = ReportViewModel(ctx, ReportRepository(ctx))

    @Test
    fun `重新进入报表页会重查而不是沿用缓存`() {
        val vm = newVm()
        addLog("sup-r1")
        vm.onEnterReport()
        awaitTotal(vm, 1)

        // 用户离开报表页去恢复备份 / 打卡（VM 不会被销毁，返回时也没有重建）→ 数据变了
        addLog("sup-r1", date = LocalDate.now().minusDays(1).toString())

        vm.onEnterReport()
        awaitTotal(vm, 2)
    }

    @Test
    fun `重新进入后周月报窗口也会重查`() {
        val vm = newVm()
        addLog("sup-r2")
        vm.onEnterReport()
        awaitTotal(vm, 1)
        vm.loadPeriodic(7)
        awaitPeriodicTotal(vm, 1)

        addLog("sup-r2", date = LocalDate.now().minusDays(1).toString())

        // 页面重新进入：overview 重查，且周月报的"参数级去重"键一并失效
        vm.onEnterReport()
        vm.loadPeriodic(7)
        awaitPeriodicTotal(vm, 2)
    }

    @Test
    fun `同一窗口连点仍然只查一次（IO 修剪没有被改回去）`() {
        val vm = newVm()
        addLog("sup-r3")
        vm.onEnterReport()
        awaitTotal(vm, 1)

        vm.loadPeriodic(7)
        awaitPeriodicTotal(vm, 1)
        val first = vm.periodic.value

        // 同一次访问内再点同一个窗口：必须是空操作（沿用同一份数据对象，而不是又查一遍）
        vm.loadPeriodic(7)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("同窗口重复调用不应重新取数", first, vm.periodic.value)
    }

    // ---- 等待：VM 的取数走 IO 线程，回到主线程后才写状态 ----

    private fun awaitTotal(vm: ReportViewModel, expected: Int, timeoutMs: Long = 10_000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            val o = vm.overview.value
            if (!vm.busy.value && o != null && o.supplement.total == expected) return
            Thread.sleep(5)
        }
        throw AssertionError(
            "等待报表概览 total=$expected 超时（当前 ${vm.overview.value?.supplement?.total}）" +
                "——若停在旧值，说明「重新进入本页」被缓存守卫跳过了",
        )
    }

    private fun awaitPeriodicTotal(vm: ReportViewModel, expected: Int, timeoutMs: Long = 10_000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            val p = vm.periodic.value
            if (!vm.busy.value && p != null && p.suppTotal == expected) return
            Thread.sleep(5)
        }
        throw AssertionError("等待周月报 suppTotal=$expected 超时（当前 ${vm.periodic.value?.suppTotal}）")
    }

    // ---- 夹具：直接写库，绕开 VM（模拟"离开报表页时别处发生的写入"） ----

    private fun addLog(supId: String, date: String = LocalDate.now().toString()) = io {
        db.supplementDao().upsert(
            Supplement(
                id = supId, name = "测试补剂 $supId", category = "OTHER", dose = "1 粒",
                createdAt = nowIso(), updatedAt = nowIso(),
            ),
        )
        db.supplementLogDao().upsert(
            SupplementLog(
                id = "slog-$supId-$date", date = date, recordedAt = nowIso(),
                supId = supId, supKey = supId, supName = "测试补剂 $supId",
                doseSnapshot = "1 粒", status = AdherenceCalc.DONE, takenAt = nowIso(),
            ),
        )
    }
}
