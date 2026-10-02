package com.ashkb.app.domain

import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * v1.0.86（批次 11 / B3）：进程级日期源 [DateProvider] 的语义。
 *
 * 只测**纯 JVM 部分**（构造 + 推进 + 监听通知）：`start/stop` 要注册系统广播，属 Robolectric 范畴。
 * 这里要锁住的是各 ViewModel 依赖的那三条契约：
 *   ① 同值不通知（避免跨日后重复查库）；
 *   ② 值变才通知（跨零点重算的唯一触发口径）；
 *   ③ 所有使用方共用同一个日期实例（7 份 ticker 收成一份之后，不可能再各算各的）。
 */
class DateProviderTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @Test
    fun `same date does not notify listeners`() {
        val fixed = LocalDate.of(2026, 10, 2)
        val provider = DateProvider(scope) { fixed }
        var calls = 0
        provider.addOnDateChangedListener { calls++ }

        provider.refreshIfStale()
        provider.refreshIfStale()
        provider.onDateKnown(fixed)

        assertEquals(0, calls)
        assertEquals(fixed, provider.today.value)
    }

    @Test
    fun `date change notifies listeners exactly once with the new date`() {
        var now = LocalDate.of(2026, 10, 2)
        val provider = DateProvider(scope) { now }
        val seen = mutableListOf<LocalDate>()
        provider.addOnDateChangedListener { seen.add(it) }

        // 跨零点：设备深睡时系统发 ACTION_DATE_CHANGED，醒来补送
        now = LocalDate.of(2026, 10, 3)
        provider.refreshIfStale()
        // 同一新值再来一次（例如同时收到 DATE_CHANGED 与 TIME_CHANGED）不应重复通知
        provider.onDateKnown(LocalDate.of(2026, 10, 3))

        assertEquals(listOf(LocalDate.of(2026, 10, 3)), seen)
        assertEquals(LocalDate.of(2026, 10, 3), provider.today.value)
    }

    @Test
    fun `unregister handle stops notifications`() {
        var now = LocalDate.of(2026, 10, 2)
        val provider = DateProvider(scope) { now }
        var calls = 0
        val off = provider.addOnDateChangedListener { calls++ }

        now = LocalDate.of(2026, 10, 3)
        provider.refreshIfStale()
        assertEquals(1, calls)

        off()
        now = LocalDate.of(2026, 10, 4)
        provider.refreshIfStale()
        assertEquals(1, calls)
        assertEquals(LocalDate.of(2026, 10, 4), provider.today.value)
    }

    /** 多个使用方必须看到**同一个**日期流实例（这正是"7 份 ticker 收成一份"的可验证形态）。 */
    @Test
    fun `all consumers share one date flow`() {
        val provider = DateProvider(scope) { LocalDate.of(2026, 10, 2) }
        val a = provider.today
        val b = provider.today
        assertSame(a, b)
        assertEquals(LocalDate.of(2026, 10, 2), a.value)
    }

    /** 改系统时区 / 手动改时间可能把日期改到**过去**，也必须照样通知（不能只处理"往后走"）。 */
    @Test
    fun `date moving backwards is also propagated`() {
        var now = LocalDate.of(2026, 10, 2)
        val provider = DateProvider(scope) { now }
        val seen = mutableListOf<LocalDate>()
        provider.addOnDateChangedListener { seen.add(it) }

        now = LocalDate.of(2026, 10, 1)
        provider.refreshIfStale()

        assertEquals(listOf(LocalDate.of(2026, 10, 1)), seen)
    }
}
