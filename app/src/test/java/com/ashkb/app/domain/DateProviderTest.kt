package com.ashkb.app.domain

import android.content.Context
import android.content.Intent
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

/**
 * v1.2.20：`DateProvider` 的 start/stop 与广播分支**此前零测试**。
 *
 * 为什么值得测：这是全应用唯一的「今天」来源。它的历史缺陷不是算法错，而是**触发路径**错
 * （`postDelayed` 按 uptime 计时、深睡不走 → 早上打开 App 日期停在前一天）。修法把主路径
 * 换成系统广播，于是「广播进来会不会真的推进」「stop 之后还会不会推进」就成了承重逻辑：
 * 前者坏 = 跨日不刷新；后者坏 = 进程收尾后仍持有 receiver（泄漏 / 重复回调）。
 *
 * 时钟可注入，所以不需要真的等到明天：改 `clockNow` 再发广播即可。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class DateProviderTest {

    private val context: Context = RuntimeEnvironment.getApplication()
    private var clockNow: LocalDate = LocalDate.of(2026, 11, 21)
    private var started: DateProvider? = null

    private fun provider() = DateProvider(CoroutineScope(Dispatchers.Unconfined)) { clockNow }

    private fun start(p: DateProvider): DateProvider {
        p.start(context)
        started = p
        return p
    }

    private fun broadcast(action: String) {
        context.sendBroadcast(Intent(action))
        ShadowLooper.idleMainLooper()
    }

    @After
    fun tearDown() {
        started?.stop(context)
        started = null
    }

    @Test
    fun `构造时取一次系统时钟`() {
        assertEquals(LocalDate.of(2026, 11, 21), provider().today.value)
    }

    @Test
    fun `start 会立刻与系统时钟对齐（覆盖注册前已跨日的窗口）`() {
        val p = provider()
        clockNow = LocalDate.of(2026, 11, 22)
        start(p)
        assertEquals(LocalDate.of(2026, 11, 22), p.today.value)
    }

    @Test
    fun `日期变更广播会推进今天`() {
        val p = start(provider())
        clockNow = LocalDate.of(2026, 11, 23)
        broadcast(Intent.ACTION_DATE_CHANGED)
        assertEquals(LocalDate.of(2026, 11, 23), p.today.value)
    }

    @Test
    fun `手动改时间与改时区同样会推进今天`() {
        val p = start(provider())
        clockNow = LocalDate.of(2026, 11, 24)
        broadcast(Intent.ACTION_TIME_CHANGED)
        assertEquals(LocalDate.of(2026, 11, 24), p.today.value)

        clockNow = LocalDate.of(2026, 11, 25)
        broadcast(Intent.ACTION_TIMEZONE_CHANGED)
        assertEquals(LocalDate.of(2026, 11, 25), p.today.value)
    }

    @Test
    fun `同值广播是空操作，不重复通知监听器`() {
        val p = start(provider())
        var calls = 0
        p.addOnDateChangedListener { calls++ }
        repeat(3) { broadcast(Intent.ACTION_DATE_CHANGED) } // 时钟没动 → 值相同
        assertEquals(0, calls)
        assertEquals(LocalDate.of(2026, 11, 21), p.today.value)
    }

    @Test
    fun `stop 之后广播不再推进今天`() {
        val p = start(provider())
        p.stop(context)
        started = null
        clockNow = LocalDate.of(2026, 12, 1)
        broadcast(Intent.ACTION_DATE_CHANGED)
        assertEquals(LocalDate.of(2026, 11, 21), p.today.value)
    }

    @Test
    fun `stop 重复调用安全`() {
        val p = start(provider())
        p.stop(context)
        p.stop(context)
        started = null
    }

    @Test
    fun `监听器可以被反注册，之后不再收到回调`() {
        val p = provider()
        var first = 0
        var second = 0
        val removeFirst = p.addOnDateChangedListener { first++ }
        p.addOnDateChangedListener { second++ }

        p.onDateKnown(LocalDate.of(2026, 11, 22))
        assertEquals(1, first)
        assertEquals(1, second)

        removeFirst()
        p.onDateKnown(LocalDate.of(2026, 11, 23))
        assertEquals(1, first)
        assertEquals(2, second)
    }
}
