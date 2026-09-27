package com.ashkb.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.0.72：小米系设备判定与「需要用户手动开启的开关」清单。
 *
 * 锁三件事：① 小米 / 红米 / POCO 都要命中（换皮 ROM 常见品牌与厂商不一致）；
 * ② 其它厂商一个都不能误命中（误命中会让用户被引到不存在的设置项）；
 * ③ 开关清单必须含「锁屏显示」与「后台弹出界面」——这两条是本版存在的理由。
 */
class XiaomiCompatTest {

    @Test
    fun `小米品牌与厂商都命中`() {
        assertTrue(XiaomiCompat.isXiaomi("Xiaomi", "Xiaomi"))
    }

    @Test
    fun `红米与 POCO 也算小米系`() {
        assertTrue(XiaomiCompat.isXiaomi("Redmi", "Xiaomi"))
        assertTrue(XiaomiCompat.isXiaomi("POCO", "Xiaomi"))
    }

    @Test
    fun `品牌为空但厂商是小米也算`() {
        assertTrue(XiaomiCompat.isXiaomi(null, "Xiaomi"))
        assertTrue(XiaomiCompat.isXiaomi("", "Xiaomi"))
    }

    @Test
    fun `大小写与首尾空白不敏感`() {
        assertTrue(XiaomiCompat.isXiaomi(" xiaomi ", " XIAOMI "))
        assertTrue(XiaomiCompat.isXiaomi("ReDmI", "xIaOmI"))
    }

    @Test
    fun `其它厂商不命中`() {
        assertFalse(XiaomiCompat.isXiaomi("samsung", "samsung"))
        assertFalse(XiaomiCompat.isXiaomi("HUAWEI", "HUAWEI"))
        assertFalse(XiaomiCompat.isXiaomi("google", "Google"))
        assertFalse(XiaomiCompat.isXiaomi("OnePlus", "OnePlus"))
    }

    @Test
    fun `品牌与厂商都为空不命中`() {
        assertFalse(XiaomiCompat.isXiaomi(null, null))
        assertFalse(XiaomiCompat.isXiaomi("", ""))
    }

    @Test
    fun `开关清单含锁屏显示与后台弹出界面与自启动`() {
        val s = XiaomiCompat.requiredSwitches()
        assertTrue(s.contains(XiaomiCompat.SWITCH_LOCKSCREEN))
        assertTrue(s.contains(XiaomiCompat.SWITCH_BACKGROUND))
        assertTrue(s.contains(XiaomiCompat.SWITCH_AUTOSTART))
        assertEquals(3, s.size)
    }

    @Test
    fun `锁屏显示排在最前（强提醒全屏与锁屏卡都受它约束）`() {
        assertEquals(XiaomiCompat.SWITCH_LOCKSCREEN, XiaomiCompat.requiredSwitches().first())
    }
}
