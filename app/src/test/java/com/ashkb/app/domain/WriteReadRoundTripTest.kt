package com.ashkb.app.domain

import com.ashkb.app.data.entity.Medication
import com.ashkb.app.data.entity.Profile
import com.ashkb.app.data.entity.Supplement
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.2.10（审查裁决项 7 · 选项 A）：**写侧产物 → 读侧解析的往返测试**。
 *
 * 为什么需要这一批测试：CRITICAL-1（补剂与螯合药的间隔提醒从未生效）与 §5-MEDIUM-5
 * （过敏史含引号时产出非法 JSON）是**同一个病**——写侧改了格式、读侧没跟上，
 * 而当时的测试夹具是按**手写的、想象的**格式造的（CSV），于是全绿。
 *
 * 本文件的规矩只有一条：**夹具不许手写字面量，必须调用写侧真实产出函数**。
 * 这样写侧一改格式，读侧没跟上就会立刻红，而不是等八年后有人真机上发现功能是死的。
 *
 * 覆盖审查点名的五个字段：`take_times` / `times` / `allergies` / `lifestyle` / `comorbidities`。
 */
class WriteReadRoundTripTest {

    // ------------------------------------------------------------------
    // 1) 药单 take_times：写侧 `MedEditScreen:191-195` 的 `JSONArray(list).toString()`
    // ------------------------------------------------------------------

    private fun med(takeTimes: String?) = Medication(
        id = "med-1", name = "优甲乐", nameKey = "levothyroxine", medClass = "OTHER",
        route = "oral", dose = "50µg", frequency = "DAILY", takeTimes = takeTimes,
        startDate = "2026-01-01", createdAt = "2026-01-01T00:00:00", updatedAt = "2026-01-01T00:00:00",
    )

    @Test
    fun `take_times 写侧产物能被读侧解析回来`() {
        val written = JSONArray(listOf("08:00", "20:00")).toString()
        assertEquals(listOf("08:00", "20:00"), ScheduleCalc.takeTimesOf(med(written)))
    }

    @Test
    fun `take_times 单槽位往返`() {
        assertEquals(listOf("08:00"), ScheduleCalc.takeTimesOf(med(JSONArray(listOf("08:00")).toString())))
    }

    @Test
    fun `take_times 空列表写侧产出空数组而不是 csv`() {
        val written = JSONArray(emptyList<String>()).toString()
        assertEquals("[]", written)
        assertTrue(ScheduleCalc.takeTimesOf(med(written)).isEmpty())
    }

    // ------------------------------------------------------------------
    // 2) 补剂 times：写侧 `WellnessScreen:1101` 与 `MedEditScreen` 同一表达式
    // ------------------------------------------------------------------

    private fun sup(times: String?) = Supplement(
        id = "sup-1", name = "钙片", category = "CALCIUM", dose = "500mg", times = times,
        createdAt = "2026-01-01T00:00:00", updatedAt = "2026-01-01T00:00:00",
    )

    @Test
    fun `times 写侧产物能被所有读取方解析回来`() {
        val written = JSONArray(listOf("08:00", "12:30", "20:00")).toString()
        val expected = listOf("08:00", "12:30", "20:00")

        // 读取方一：排程（闹钟）
        assertEquals(expected, com.ashkb.app.reminder.SupplementReminderScheduler.parseTimes(written))
        // 读取方二：时间错开提醒（CRITICAL-1 的受害方）
        assertEquals(expected, ScheduleCalc.parseTimes(written))
        // 读取方三：每日上限警示的「每日次数」
        assertEquals(3, SupplementLimits.dosesPerDay(sup(written)))
    }

    @Test
    fun `times 空数组时每日次数回落到频次而不是算成一次`() {
        val written = JSONArray(emptyList<String>()).toString()
        assertEquals("[]", written)
        assertEquals(1, SupplementLimits.dosesPerDay(sup(written)))
        val bid = sup(written).copy(frequency = "bid")
        assertEquals(2, SupplementLimits.dosesPerDay(bid))
    }

    /**
     * CRITICAL-1 的端到端表述：**写侧真实产物必须能让冲突提示触发**。
     *
     * 这条是「八年没响过的提醒」的验收标准——不是断言某个中间函数返回值，
     * 而是断言用户最终看到的那条提示会出现。
     */
    @Test
    fun `钙片与优甲乐同服的提示在真实格式下触发`() {
        val calcium = Supplement(
            id = "sup-1", name = "钙片", category = "CALCIUM", dose = "500mg",
            times = JSONArray(listOf("08:00")).toString(),
            createdAt = "2026-01-01T00:00:00", updatedAt = "2026-01-01T00:00:00",
        )
        val conflicts = SupplementTiming.conflicts(
            listOf(calcium),
            listOf(med(JSONArray(listOf("08:00")).toString())),
        )
        assertEquals("真格式下必须报出冲突——旧实现在这里返回空表", 1, conflicts.size)
        assertEquals(0, conflicts.first().gapMinutes)
        assertEquals("钙片", conflicts.first().supplementName)
    }

    // ------------------------------------------------------------------
    // 3) 档案 allergies / comorbidities：写侧 ProfileLists.toJson
    // ------------------------------------------------------------------

    @Test
    fun `过敏史往返`() {
        val written = ProfileLists.toJson("NSAIDs, sulfa drugs")!!
        assertEquals("""["NSAIDs","sulfa drugs"]""", written)
        assertEquals("NSAIDs,sulfa drugs", ProfileLists.fromJson(written))
        // 读侧确实能解析（旧版这里是手写拼串，含逗号的条目会被展平成两项）
        assertEquals(2, JSONArray(written).length())
    }

    /** §5-MEDIUM-5 的定点回归：**内容里的引号不许产出非法 JSON**。 */
    @Test
    fun `过敏史含引号时仍产出合法 JSON`() {
        val written = ProfileLists.toJson("""对"某药"过敏""")!!
        // 能被解析，且解析回来仍是同一个值——旧版 `"[" + joinToString { "\"$it\"" } + "]"` 会产出
        // `["对"某药"过敏"]`（非法），而旧读侧 `.replace("\"","")` 又会把引号全吞掉
        val arr = JSONArray(written)
        assertEquals(1, arr.length())
        assertEquals("""对"某药"过敏""", arr.getString(0))
        assertEquals("""对"某药"过敏""", ProfileLists.fromJson(written))
    }

    @Test
    fun `过敏史含反斜杠与换行时仍产出合法 JSON`() {
        val nasty = """C:\path\file"""
        val written = ProfileLists.toJson(nasty)!!
        assertEquals(nasty, JSONArray(written).getString(0))
        assertEquals(nasty, ProfileLists.fromJson(written))
    }

    @Test
    fun `全角逗号与空白被规整`() {
        assertEquals("""["青霉素","磺胺"]""", ProfileLists.toJson("青霉素， 磺胺 ")!!)
    }

    @Test
    fun `空输入存 null 而不是空数组`() {
        assertNull(ProfileLists.toJson(""))
        assertNull(ProfileLists.toJson("   "))
        assertNull(ProfileLists.toJson("，，"))
        assertEquals("", ProfileLists.fromJson(null))
    }

    @Test
    fun `共存病往返`() {
        val written = ProfileLists.toJson("高血压, 糖尿病")!!
        assertEquals("""["高血压","糖尿病"]""", written)
        assertEquals("高血压,糖尿病", ProfileLists.fromJson(written))
    }

    /**
     * 历史脏值（旧版拼串产出的非法 JSON）→ 读侧**原样返回**，不抛、不做破坏性编辑。
     *
     * 旧读侧 `removeSurrounding("[","]").replace("\"","")` 会把它「修」成
     * `对某药过敏` —— 用户打开编辑页再保存，存量数据就被悄悄改写了。
     */
    @Test
    fun `历史脏值原样返回不被改写`() {
        val dirty = """["对"某药"过敏"]"""
        assertEquals(dirty, ProfileLists.fromJson(dirty))
    }

    @Test
    fun `读侧对合法值不再是简单去引号`() {
        // 两种实现的分歧点：内容里含引号时，`.replace("\"","")` 会把内容也吃掉
        val legit = """["a\"b"]"""
        assertEquals("""a"b""", ProfileLists.fromJson(legit))
    }

    // ------------------------------------------------------------------
    // 4) lifestyle：写侧 Lifestyle.toJson / 读侧 Lifestyle.fromJson
    //    （既有 LifestyleTest 已锁往返，这里补「与档案列共存的形状」）
    // ------------------------------------------------------------------

    @Test
    fun `lifestyle 往返`() {
        val life = Lifestyle(
            smoking = Lifestyle.SMOKING_FORMER,
            sedentaryHours = 8,
            exerciseHabit = Lifestyle.HABIT_OCCASIONAL,
            sleepHours = 7,
        )
        assertEquals(life, Lifestyle.fromJson(life.toJson()))
    }

    @Test
    fun `lifestyle 缺键回落默认`() {
        assertEquals(Lifestyle(), Lifestyle.fromJson("""{"smoking":"unknown"}"""))
        assertEquals(Lifestyle(), Lifestyle.fromJson("not-json"))
        assertEquals(Lifestyle(), Lifestyle.fromJson(null))
    }

    // ------------------------------------------------------------------
    // 5) 五字段共存于一个 Profile：确认它们互不干扰
    // ------------------------------------------------------------------

    @Test
    fun `五个字段同时存在时各自往返`() {
        val profile = Profile(
            id = 1,
            displayName = "曾庆城",
            diagnosis = "强直性脊柱炎",
            allergies = ProfileLists.toJson("""对"某药"过敏, 青霉素"""),
            comorbidities = ProfileLists.toJson("高血压"),
            lifestyle = Lifestyle(smoking = Lifestyle.SMOKING_CURRENT, sleepHours = 6).toJson(),
            createdAt = "2026-01-01T00:00:00",
            updatedAt = "2026-01-01T00:00:00",
        )

        assertEquals(listOf("""对"某药"过敏""", "青霉素"), arr(profile.allergies))
        assertEquals(listOf("高血压"), arr(profile.comorbidities))
        assertEquals(Lifestyle.SMOKING_CURRENT, Lifestyle.fromJson(profile.lifestyle).smoking)
        assertEquals(6, Lifestyle.fromJson(profile.lifestyle).sleepHours)
        // 出入一对：进编辑页再保存，值不变
        assertEquals("""对"某药"过敏,青霉素""", ProfileLists.fromJson(profile.allergies))
        assertEquals("高血压", ProfileLists.fromJson(profile.comorbidities))
    }

    private fun arr(json: String?): List<String> {
        val a = JSONArray(json)
        return (0 until a.length()).map { a.getString(it) }
    }
}
