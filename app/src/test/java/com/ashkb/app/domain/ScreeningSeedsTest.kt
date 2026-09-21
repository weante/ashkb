package com.ashkb.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** v1.0.37：C10 生物制剂筛查 / 续方节点种子（幂等去重）。 */
class ScreeningSeedsTest {

    @Test
    fun `pending returns all seeds when none exist`() {
        assertEquals(ScreeningSeeds.BIOLOGIC.size, ScreeningSeeds.pending(emptySet()).size)
    }

    /** 幂等：已存在的项不再返回（重复点击「一键添加」不会建重复条目）。 */
    @Test
    fun `pending skips existing names`() {
        val first = ScreeningSeeds.BIOLOGIC.first().name
        val pending = ScreeningSeeds.pending(setOf(first))
        assertEquals(ScreeningSeeds.BIOLOGIC.size - 1, pending.size)
        assertTrue(pending.none { it.name == first })
    }

    @Test
    fun `pending is empty when all exist`() {
        assertTrue(ScreeningSeeds.pending(ScreeningSeeds.BIOLOGIC.map { it.name }.toSet()).isEmpty())
    }

    /** 规划要求的三项筛查 + 续方提醒都在种子里，且都带说明。 */
    @Test
    fun `seeds cover tb hbv hcv and refill`() {
        val names = ScreeningSeeds.BIOLOGIC.map { it.name }
        assertTrue(names.any { it.contains("结核") })
        assertTrue(names.any { it.contains("乙肝") })
        assertTrue(names.any { it.contains("丙肝") })
        assertTrue(names.any { it.contains("续方") })
        assertTrue(ScreeningSeeds.BIOLOGIC.all { it.notes.isNotBlank() })
    }
}
