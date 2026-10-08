package com.ashkb.app.domain

import android.content.Context
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * v1.0.64 B13：生活方式 → 运动处方个性化提示回归。
 *
 * i18n（v1.2.6）：`advice` 返回 `List<ResText>`，断言措辞的用例先落地成中文文本。
 * `@Config(qualifiers = "zh-rCN")` 不可省（`values-en` 存在，Robolectric 默认走 en-rUS）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "zh-rCN")
class LifestylePrescriptionTest {

    private val ctx: Context get() = RuntimeEnvironment.getApplication()

    /** 把 `advice` 的资源片段按当前（中文）语言落地成可断言的文本。 */
    private fun texts(l: Lifestyle): List<String> =
        LifestylePrescription.advice(l).map { ctx.getString(it.res, *it.args.toTypedArray()) }

    @Test
    fun `全默认画像无任何提示`() {
        assertTrue(LifestylePrescription.advice(Lifestyle()).isEmpty())
        assertFalse(LifestylePrescription.hasContent(Lifestyle()))
    }

    @Test
    fun `现吸烟给戒烟提示且不夸大专项获益`() {
        val out = texts(Lifestyle(smoking = Lifestyle.SMOKING_CURRENT))
        assertTrue(out.any { it.contains("戒烟") })
        // 诚实口径：必须说明专项获益尚无正式研究
        assertTrue("须保留「尚无正式研究」的诚实口径", out.any { it.contains("尚无正式研究") })
    }

    @Test
    fun `已戒烟给正反馈而非戒烟建议`() {
        val out = texts(Lifestyle(smoking = Lifestyle.SMOKING_FORMER))
        assertTrue(out.any { it.contains("已戒烟") })
        assertTrue("已戒烟不该再劝戒烟", out.none { it.contains("建议戒烟") })
    }

    @Test
    fun `久坐达到阈值才提示`() {
        val below = texts(Lifestyle(sedentaryHours = 5))
        assertTrue(below.none { it.contains("久坐") })
        val at = texts(
            Lifestyle(sedentaryHours = LifestylePrescription.SEDENTARY_THRESHOLD_HOURS),
        )
        assertTrue(at.any { it.contains("久坐") })
    }

    @Test
    fun `睡眠不足才提示`() {
        assertTrue(texts(Lifestyle(sleepHours = 7)).none { it.contains("睡眠") })
        assertTrue(texts(Lifestyle(sleepHours = 6)).any { it.contains("睡眠") })
    }

    @Test
    fun `运动习惯驱动起步或加量建议`() {
        val none = texts(Lifestyle(exerciseHabit = Lifestyle.HABIT_NONE))
        assertTrue(none.any { it.contains("L1") })
        val regular = texts(Lifestyle(exerciseHabit = Lifestyle.HABIT_REGULAR))
        assertTrue(regular.any { it.contains("进展原则") })
    }

    @Test
    fun `多项命中按优先级全部给出`() {
        val out = texts(
            Lifestyle(
                smoking = Lifestyle.SMOKING_CURRENT,
                sedentaryHours = 10,
                exerciseHabit = Lifestyle.HABIT_NONE,
                sleepHours = 5,
            ),
        )
        // 吸烟 > 久坐 > 睡眠 > 运动习惯
        assertTrue(out.size >= 4)
        assertTrue(out[0].contains("戒烟"))
    }

    @Test
    fun `已登记任一项即视为有内容`() {
        assertTrue(LifestylePrescription.hasContent(Lifestyle(sedentaryHours = 8)))
        assertTrue(LifestylePrescription.hasContent(Lifestyle(smoking = Lifestyle.SMOKING_NEVER)))
        assertTrue(LifestylePrescription.hasContent(Lifestyle(sleepHours = 8)))
    }
}
