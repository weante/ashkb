package com.ashkb.app.ui.today

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 今日页「昨日待补」卡上的日期口径（v1.0.84，批次 9）。
 *
 * 背景（跨零点正确性 bug）：原实现是 `remember { LocalDate.now().minusDays(1) }`——**无 key**，
 * 首次组合后永不重算。跨零点后卡里的剂量已按新的「昨天」重算（VM 的 ticker 会
 * `refreshYesterdayPending()`），标题上的日期却还指着**前天**；而且它绕开了 VM 的日期流，
 * 与 Hero 上的「今天」各读一次系统时钟，两处可能分属不同的「今天」。
 *
 * 现在标签由 [TodayViewModel.todayDate] 这条**可观察**日期流推导，本测试锁住
 * [yesterdayIso] 这个纯函数口径（跨月 / 跨年 / 闰日）。
 * 注：本模块没有 Compose UI 测试依赖，故这里覆盖的是推导函数本身，
 * 「日期流变化 → 重新组合」由 `remember(todayDate)` 的 key 保证（见 TodayScreen）。
 */
class TodayYesterdayDateTest {

    @Test
    fun `昨日是入参今天的减一天（与系统时钟无关）`() {
        assertEquals("2026-10-01", yesterdayIso(LocalDate.of(2026, 10, 2)))
    }

    @Test
    fun `跨月与跨年都正确`() {
        assertEquals("2026-09-30", yesterdayIso(LocalDate.of(2026, 10, 1)))
        assertEquals("2025-12-31", yesterdayIso(LocalDate.of(2026, 1, 1)))
    }

    @Test
    fun `闰年三月一日的前一天是二月二十九`() {
        assertEquals("2024-02-29", yesterdayIso(LocalDate.of(2024, 3, 1)))
        // 非闰年同日则是 02-28（防止把闰日写死）
        assertEquals("2025-02-28", yesterdayIso(LocalDate.of(2025, 3, 1)))
    }

    @Test
    fun `跨零点：日期流前进一天，昨日标签随之前进（而不是冻在首次组合的日期上）`() {
        val beforeMidnight = LocalDate.of(2026, 10, 1) // 23:59 时的「今天」
        val afterMidnight = beforeMidnight.plusDays(1) // 00:01 时的「今天」
        assertEquals("2026-09-30", yesterdayIso(beforeMidnight))
        // 旧实现（remember 无 key）在这里会继续返回 2026-09-30 —— 卡片内容却已是 10-01 的
        assertEquals("2026-10-01", yesterdayIso(afterMidnight))
    }
}
