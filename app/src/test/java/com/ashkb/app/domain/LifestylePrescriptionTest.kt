package com.ashkb.app.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.0.64 B13：生活方式 → 运动处方个性化提示回归。
 */
class LifestylePrescriptionTest {

    @Test
    fun `全默认画像无任何提示`() {
        assertTrue(LifestylePrescription.advice(Lifestyle()).isEmpty())
        assertFalse(LifestylePrescription.hasContent(Lifestyle()))
    }

    @Test
    fun `现吸烟给戒烟提示且不夸大专项获益`() {
        val out = LifestylePrescription.advice(Lifestyle(smoking = Lifestyle.SMOKING_CURRENT))
        assertTrue(out.any { it.contains("戒烟") })
        // 诚实口径：必须说明专项获益尚无正式研究
        assertTrue("须保留「尚无正式研究」的诚实口径", out.any { it.contains("尚无正式研究") })
    }

    @Test
    fun `已戒烟给正反馈而非戒烟建议`() {
        val out = LifestylePrescription.advice(Lifestyle(smoking = Lifestyle.SMOKING_FORMER))
        assertTrue(out.any { it.contains("已戒烟") })
        assertTrue("已戒烟不该再劝戒烟", out.none { it.contains("建议戒烟") })
    }

    @Test
    fun `久坐达到阈值才提示`() {
        val below = LifestylePrescription.advice(Lifestyle(sedentaryHours = 5))
        assertTrue(below.none { it.contains("久坐") })
        val at = LifestylePrescription.advice(
            Lifestyle(sedentaryHours = LifestylePrescription.SEDENTARY_THRESHOLD_HOURS),
        )
        assertTrue(at.any { it.contains("久坐") })
    }

    @Test
    fun `睡眠不足才提示`() {
        assertTrue(LifestylePrescription.advice(Lifestyle(sleepHours = 7)).none { it.contains("睡眠") })
        assertTrue(LifestylePrescription.advice(Lifestyle(sleepHours = 6)).any { it.contains("睡眠") })
    }

    @Test
    fun `运动习惯驱动起步或加量建议`() {
        val none = LifestylePrescription.advice(Lifestyle(exerciseHabit = Lifestyle.HABIT_NONE))
        assertTrue(none.any { it.contains("L1") })
        val regular = LifestylePrescription.advice(Lifestyle(exerciseHabit = Lifestyle.HABIT_REGULAR))
        assertTrue(regular.any { it.contains("进展原则") })
    }

    @Test
    fun `多项命中按优先级全部给出`() {
        val out = LifestylePrescription.advice(
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
