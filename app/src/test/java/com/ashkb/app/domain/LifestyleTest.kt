package com.ashkb.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.0.64 B13：生活方式画像编解码与置顶挂点回归。
 */
class LifestyleTest {

    @Test
    fun `全字段往返一致`() {
        val l = Lifestyle(
            smoking = Lifestyle.SMOKING_CURRENT,
            sedentaryHours = 8,
            exerciseHabit = Lifestyle.HABIT_NONE,
            sleepHours = 6,
        )
        assertEquals(l, Lifestyle.fromJson(l.toJson()))
    }

    @Test
    fun `未填数值项整键省略 解析回落 null`() {
        val l = Lifestyle(smoking = Lifestyle.SMOKING_NEVER, exerciseHabit = Lifestyle.HABIT_REGULAR)
        val json = l.toJson()
        assertFalse("null 数值不该写进 JSON", json.contains("sedentary_hours"))
        assertFalse("null 数值不该写进 JSON", json.contains("sleep_hours"))
        val back = Lifestyle.fromJson(json)
        assertNull(back.sedentaryHours)
        assertNull(back.sleepHours)
        assertEquals(Lifestyle.SMOKING_NEVER, back.smoking)
    }

    @Test
    fun `空串与 null 退化为全默认`() {
        assertTrue(Lifestyle.fromJson(null) == Lifestyle())
        assertTrue(Lifestyle.fromJson("") == Lifestyle())
        assertTrue(Lifestyle.fromJson("{}") == Lifestyle())
    }

    @Test
    fun `残缺或非法输入不抛异常`() {
        // 残缺 JSON、未知枚举值、非数字数值、越界数值
        val l = Lifestyle.fromJson("""{"smoking":"yes","sedentary_hours":"abc","sleep_hours":99}""")
        assertEquals(Lifestyle.SMOKING_UNKNOWN, l.smoking)
        assertNull("非数字应回落 null", l.sedentaryHours)
        assertNull("越界应回落 null", l.sleepHours)
        assertEquals(Lifestyle.HABIT_UNKNOWN, l.exerciseHabit)
    }

    @Test
    fun `吸烟史判定含现吸与已戒`() {
        assertTrue(Lifestyle(smoking = Lifestyle.SMOKING_CURRENT).hasSmokingHistory)
        assertTrue(Lifestyle(smoking = Lifestyle.SMOKING_FORMER).hasSmokingHistory)
        assertFalse(Lifestyle(smoking = Lifestyle.SMOKING_NEVER).hasSmokingHistory)
        assertFalse(Lifestyle(smoking = Lifestyle.SMOKING_UNKNOWN).hasSmokingHistory)
    }

    @Test
    fun `吸烟登记后置顶吸烟条目 未登记不置顶`() {
        assertEquals(
            listOf(Lifestyle.KB_SMOKING),
            Lifestyle(smoking = Lifestyle.SMOKING_CURRENT).pinnedKbIds(),
        )
        assertEquals(
            listOf(Lifestyle.KB_SMOKING),
            Lifestyle(smoking = Lifestyle.SMOKING_FORMER).pinnedKbIds(),
        )
        assertTrue(Lifestyle(smoking = Lifestyle.SMOKING_NEVER).pinnedKbIds().isEmpty())
        assertTrue(Lifestyle().pinnedKbIds().isEmpty())
    }

    @Test
    fun `置顶条目 id 与知识库种子一致`() {
        // 种子 kb_seed_edu.json 的 id 一旦改名，此处会失败——提醒同步
        assertEquals("edu-003", Lifestyle.KB_SMOKING)
    }
}
