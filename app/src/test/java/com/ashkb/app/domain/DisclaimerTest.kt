package com.ashkb.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.0.63 C12：免责声明文案回归。
 *
 * 这些是**合规底线措辞**——被无意改坏（尤其被翻译 / 精简掉关键限定词）会直接让声明失效，
 * 所以按 [RecipeSourcesTest] 的做法把关键限定词锁进单测。
 */
class DisclaimerTest {

    @Test
    fun `统一前缀点名主治医师医嘱`() {
        assertTrue("前缀必须点明「主治医师」", Disclaimer.PREFIX.contains("主治医师"))
        assertTrue("前缀必须以句号收尾", Disclaimer.PREFIX.endsWith("。"))
        assertEquals("仅供", Disclaimer.PREFIX.substring(0, 2))
    }

    @Test
    fun `核心定性同时否掉医疗建议与替代诊疗`() {
        assertTrue(Disclaimer.NOT_MEDICAL_ADVICE.contains("不构成"))
        assertTrue(Disclaimer.NOT_MEDICAL_ADVICE.contains("不能替代"))
        assertTrue(Disclaimer.NOT_MEDICAL_ADVICE.contains("医疗建议"))
    }

    @Test
    fun `数据边界声明数据仅存本机`() {
        assertTrue(Disclaimer.DATA_LOCAL.contains("本机"))
        assertTrue(Disclaimer.DATA_LOCAL.contains("不上传"))
    }

    @Test
    fun `器械边界声明不是医疗器械`() {
        assertTrue(Disclaimer.NOT_MEDICAL_DEVICE.contains("不是医疗器械"))
    }

    @Test
    fun `就医边界声明以医嘱为准`() {
        assertTrue(Disclaimer.FOLLOW_DOCTOR.contains("医嘱"))
    }

    @Test
    fun `首启要点清单包含四条且含核心定性`() {
        assertEquals(4, Disclaimer.FIRST_LAUNCH_POINTS.size)
        assertTrue(Disclaimer.FIRST_LAUNCH_POINTS.contains(Disclaimer.NOT_MEDICAL_ADVICE))
        assertTrue(Disclaimer.FIRST_LAUNCH_POINTS.contains(Disclaimer.FOLLOW_DOCTOR))
        assertTrue(Disclaimer.FIRST_LAUNCH_POINTS.contains(Disclaimer.DATA_LOCAL))
        assertTrue(Disclaimer.FIRST_LAUNCH_POINTS.contains(Disclaimer.NOT_MEDICAL_DEVICE))
        assertTrue("要点不应有空串", Disclaimer.FIRST_LAUNCH_POINTS.none { it.isBlank() })
    }
}
