package com.ashkb.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * v1.2.19：「今日用药」空态判据的单测。
 *
 * 真机走查（2026-11-18）发现非用药日首页写「当前没有在用药品」，与药单页「在用 1 种 · 依那西普」
 * 矛盾。这三条用例把「缺档案 / 没有药 / 今天不排药」三种情形钉死，防止再退回两态实现。
 */
class TodayMedsEmptyTest {

    @Test
    fun `缺档案时先引导建档`() {
        assertEquals(
            TodayMedsEmpty.Kind.PROFILE_MISSING,
            TodayMedsEmpty.kindOf(profileMissing = true, activeMedCount = 0),
        )
    }

    @Test
    fun `缺档案优先于有没有药`() {
        // 档案缺失时即使库里有药（例如建档未完成的历史数据），也先说的是「去建档」
        assertEquals(
            TodayMedsEmpty.Kind.PROFILE_MISSING,
            TodayMedsEmpty.kindOf(profileMissing = true, activeMedCount = 2),
        )
    }

    @Test
    fun `没有任何在用药品`() {
        assertEquals(
            TodayMedsEmpty.Kind.NO_ACTIVE_MEDS,
            TodayMedsEmpty.kindOf(profileMissing = false, activeMedCount = 0),
        )
    }

    @Test
    fun `有在用药品但今天不排药`() {
        // 依那西普 BIW 的周三：today 为空但 activeMedCount = 1 → 不能说「没有在用药品」
        assertEquals(
            TodayMedsEmpty.Kind.NOTHING_SCHEDULED_TODAY,
            TodayMedsEmpty.kindOf(profileMissing = false, activeMedCount = 1),
        )
    }

    @Test
    fun `负数按没有药品处理`() {
        // 防御性：上游给负数（不该发生）时不要落到「今天不排药」那条安慰性文案
        assertEquals(
            TodayMedsEmpty.Kind.NO_ACTIVE_MEDS,
            TodayMedsEmpty.kindOf(profileMissing = false, activeMedCount = -1),
        )
    }
}
