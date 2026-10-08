package com.ashkb.app.domain

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * v1.0.63 C12：免责声明文案回归。
 *
 * 这些是**合规底线措辞**——被无意改坏（尤其被翻译 / 精简掉关键限定词）会直接让声明失效，
 * 所以按 [RecipeSourcesTest] 的做法把关键限定词锁进单测。
 *
 * i18n（v1.2.6）：文案已移到 `values/strings_domain.xml`，本测试改为**读中文资源**来断言措辞，
 * 断言强度不变。`@Config(qualifiers = "zh-rCN")` 不可省——`values-en` 已存在，
 * Robolectric 默认跟随进程 locale（en-rUS）会取到英文，「主治医师」必然断不出来。
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "zh-rCN")
class DisclaimerTest {

    private val ctx: Context get() = RuntimeEnvironment.getApplication()

    private fun text(res: Int): String = ctx.getString(res)

    @Test
    fun `统一前缀点名主治医师医嘱`() {
        val prefix = text(Disclaimer.PREFIX)
        assertTrue("前缀必须点明「主治医师」", prefix.contains("主治医师"))
        assertTrue("前缀必须以句号收尾", prefix.endsWith("。"))
        assertEquals("仅供", prefix.substring(0, 2))
    }

    @Test
    fun `核心定性同时否掉医疗建议与替代诊疗`() {
        val s = text(Disclaimer.NOT_MEDICAL_ADVICE)
        assertTrue(s.contains("不构成"))
        assertTrue(s.contains("不能替代"))
        assertTrue(s.contains("医疗建议"))
    }

    @Test
    fun `数据边界声明数据仅存本机`() {
        val s = text(Disclaimer.DATA_LOCAL)
        assertTrue(s.contains("本机"))
        assertTrue(s.contains("不上传"))
    }

    @Test
    fun `器械边界声明不是医疗器械`() {
        assertTrue(text(Disclaimer.NOT_MEDICAL_DEVICE).contains("不是医疗器械"))
    }

    @Test
    fun `就医边界声明以医嘱为准`() {
        assertTrue(text(Disclaimer.FOLLOW_DOCTOR).contains("医嘱"))
    }

    @Test
    fun `首启要点清单包含四条且含核心定性`() {
        assertEquals(4, Disclaimer.FIRST_LAUNCH_POINTS.size)
        assertTrue(Disclaimer.FIRST_LAUNCH_POINTS.contains(Disclaimer.NOT_MEDICAL_ADVICE))
        assertTrue(Disclaimer.FIRST_LAUNCH_POINTS.contains(Disclaimer.FOLLOW_DOCTOR))
        assertTrue(Disclaimer.FIRST_LAUNCH_POINTS.contains(Disclaimer.DATA_LOCAL))
        assertTrue(Disclaimer.FIRST_LAUNCH_POINTS.contains(Disclaimer.NOT_MEDICAL_DEVICE))
        // 改成资源 id 之后多了一种坏法：四条指向同一个 id（列表看着是 4 条、渲染出来重复）
        assertEquals("四条要点必须互不相同", 4, Disclaimer.FIRST_LAUNCH_POINTS.toSet().size)
        assertTrue("要点不应有空串", Disclaimer.FIRST_LAUNCH_POINTS.none { text(it).isBlank() })
    }
}
