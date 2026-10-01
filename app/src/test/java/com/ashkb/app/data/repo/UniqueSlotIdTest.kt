package com.ashkb.app.data.repo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.0.77（批次 3b）：计划槽位物化的**一批插入内主键去重**（[uniqueSlotId]）。
 *
 * 为什么要锁这一条：[Ids.new] 是「毫秒时间基 + 3 位随机尾」，而物化一次要在同一两毫秒内生成
 * 几十行（`今天-1 .. 今天+7` × 每天几剂）——同毫秒撞尾的概率不可忽略，
 * 而撞上主键的行会被 `INSERT OR IGNORE` **静默丢掉**：表现是「某天的计划凭空少一剂」，
 * 没有任何报错。去重逻辑一旦被改坏，症状同样静默，故必须有测试。
 */
class UniqueSlotIdTest {

    @Test
    fun `首次出现的主键原样返回`() {
        val used = mutableSetOf<String>()
        assertEquals("pslot-abc123", uniqueSlotId("pslot-abc123", used))
    }

    @Test
    fun `同一批内撞尾时加计数后缀`() {
        val used = mutableSetOf<String>()
        assertEquals("pslot-abc", uniqueSlotId("pslot-abc", used))
        assertEquals("pslot-abc-2", uniqueSlotId("pslot-abc", used))
        assertEquals("pslot-abc-3", uniqueSlotId("pslot-abc", used))
    }

    @Test
    fun `后缀本身已被占用时继续递增`() {
        val used = mutableSetOf("pslot-abc", "pslot-abc-2")
        assertEquals("pslot-abc-3", uniqueSlotId("pslot-abc", used))
    }

    @Test
    fun `大批量生成时主键两两不同`() {
        // 模拟真实物化：几十行在同一毫秒内生成（这里用固定 base 模拟「随机尾全撞」的最坏情况）
        val used = mutableSetOf<String>()
        val ids = (1..80).map { uniqueSlotId("pslot-same", used) }
        assertEquals(80, ids.toSet().size)
        assertTrue("全部保留前缀形态", ids.all { it.startsWith("pslot-same") })
    }
}
