package com.ashkb.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.0.67 C1：档案标签映射回归（骶髂关节影像分期）。
 */
class LabelsTest {

    @Test
    fun `骶髂关节分期 0 到 IV 各有中文标签`() {
        assertEquals("0 正常", Labels.sacroiliitisGrade("0"))
        assertEquals("I 可疑", Labels.sacroiliitisGrade("1"))
        assertEquals("II 轻度", Labels.sacroiliitisGrade("2"))
        assertEquals("III 中度", Labels.sacroiliitisGrade("3"))
        assertEquals("IV 重度", Labels.sacroiliitisGrade("4"))
    }

    @Test
    fun `未填与未知值统一为未评估`() {
        assertEquals("未评估", Labels.sacroiliitisGrade(null))
        assertEquals("未评估", Labels.sacroiliitisGrade(""))
        assertEquals("未评估", Labels.sacroiliitisGrade("unknown"))
        assertEquals("未评估", Labels.sacroiliitisGrade("5"))
    }

    @Test
    fun `可选值清单与标签一一对应且无未评估`() {
        assertEquals(listOf("0", "1", "2", "3", "4"), Labels.SACROILIITIS_KEYS)
        Labels.SACROILIITIS_KEYS.forEach { k ->
            assertTrue("key=$k 应有非「未评估」标签", Labels.sacroiliitisGrade(k) != "未评估")
        }
    }

    @Test
    fun `标签里不出现原始 key 之外的空值`() {
        // 防回归：key 本身不该出现在 UI（改版方案 §11）——这里只确保映射非空
        Labels.SACROILIITIS_KEYS.forEach { k ->
            assertTrue(Labels.sacroiliitisGrade(k).isNotBlank())
        }
    }
}
