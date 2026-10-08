package com.ashkb.app.domain

import android.content.Context
import com.ashkb.app.data.entity.EmergencyContact
import com.ashkb.app.data.entity.Medication
import com.ashkb.app.data.entity.Profile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * v1.0.66 B6a：锁屏紧急信息内容构建回归。
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "zh-rCN")
class EmergencyLockscreenTest {

    private val ctx: Context get() = RuntimeEnvironment.getApplication()

    /** v1.2.5（i18n）：`summarize` 的频次文案改由调用方注入；本测试只断言锁屏行的拼接。 */
    private fun summarizeMeds(meds: List<Medication>, today: String) =
        EmergencyMeds.summarize(meds, today, freqLabel = { ctx.getString(it.plainRes) })

    private fun profile(blood: String? = "O", allergies: String? = null) = Profile(
        displayName = "张三", diagnosis = "强直性脊柱炎",
        emergencyBloodType = blood, allergies = allergies,
        createdAt = "2026-01-01T00:00:00", updatedAt = "2026-01-01T00:00:00",
    )

    private fun contact(name: String, phone: String, emergency: Boolean = true, doctor: Boolean = false) =
        EmergencyContact(
            id = "c-$name", name = name, phone = phone,
            isEmergency = emergency, isDoctor = doctor,
            createdAt = "2026-01-01T00:00:00", updatedAt = "2026-01-01T00:00:00",
        )

    private fun med(name: String, cls: String = "OTHER") = Medication(
        id = "m-$name", name = name, nameKey = name.lowercase(), medClass = cls,
        route = "oral", dose = "1 片", frequency = "DAILY", takeTimes = """["08:00"]""",
        startDate = "2026-01-01",
        createdAt = "2026-01-01T00:00:00", updatedAt = "2026-01-01T00:00:00",
    )

    @Test
    fun `JSON 数组展平为顿号连接`() {
        assertEquals("青霉素、磺胺", EmergencyLockscreen.cleanJsonArray("""["青霉素","磺胺"]"""))
        assertEquals("青霉素", EmergencyLockscreen.cleanJsonArray("""["青霉素"]"""))
        assertNull(EmergencyLockscreen.cleanJsonArray(null))
        assertNull(EmergencyLockscreen.cleanJsonArray(""))
        assertNull(EmergencyLockscreen.cleanJsonArray("[]"))
        assertNull(EmergencyLockscreen.cleanJsonArray("[  ]"))
    }

    @Test
    fun `空档案也产出血型未填与诊断`() {
        val c = EmergencyLockscreen.build(null, emptyList(), summarizeMeds(emptyList(), "2026-09-26"))
        assertTrue(c.lines.isEmpty())
    }

    @Test
    fun `有档案时含血型与诊断 过敏展平`() {
        val c = EmergencyLockscreen.build(
            profile(blood = "A", allergies = """["青霉素"]"""),
            emptyList(),
            summarizeMeds(emptyList(), "2026-09-26"),
        )
        assertTrue(c.lines.any { it.contains("血型 A") && it.contains("强直性脊柱炎") })
        assertTrue(c.lines.any { it == "过敏 青霉素" })
    }

    @Test
    fun `血型未填时显示占位而不是空串`() {
        val c = EmergencyLockscreen.build(
            profile(blood = null), emptyList(), summarizeMeds(emptyList(), "2026-09-26"),
        )
        assertTrue(c.lines.any { it.contains("血型 未填") })
    }

    @Test
    fun `用药含免疫抑制时标注感染风险`() {
        val meds = summarizeMeds(
            listOf(med("阿达木单抗", cls = "BIOLOGIC")), "2026-09-26",
        )
        val c = EmergencyLockscreen.build(profile(), emptyList(), meds)
        val line = c.lines.first { it.startsWith("用药") }
        assertTrue("免疫抑制标记缺失", line.contains("含免疫抑制"))
        assertTrue(line.contains("阿达木单抗"))
    }

    @Test
    fun `用药超出上限时只报前几条并给总数`() {
        val many = (1..6).map { med("药$it") }
        val meds = summarizeMeds(many, "2026-09-26")
        val c = EmergencyLockscreen.build(profile(), emptyList(), meds)
        val line = c.lines.first { it.startsWith("用药") }
        assertTrue("应提示总数", line.contains("等 6 种"))
        assertTrue("不应列出第 5 条", !line.contains("药5"))
    }

    @Test
    fun `家属优先于医生 且不把医生当家属`() {
        val contacts = listOf(
            contact("李医生", "13900000000", emergency = true, doctor = true),
            contact("王家属", "13800000000"),
        )
        val c = EmergencyLockscreen.build(profile(), contacts, summarizeMeds(emptyList(), "2026-09-26"))
        assertTrue(c.lines.any { it == "家属 王家属 13800000000" })
        assertTrue(c.lines.any { it == "医生 李医生 13900000000" })
    }

    @Test
    fun `非紧急联系人不进锁屏`() {
        val contacts = listOf(contact("普通同事", "13700000000", emergency = false))
        val c = EmergencyLockscreen.build(profile(), contacts, summarizeMeds(emptyList(), "2026-09-26"))
        assertTrue("非紧急联系人不应上锁屏", c.lines.none { it.contains("普通同事") })
    }

    @Test
    fun `标题不含用户数据`() {
        val c = EmergencyLockscreen.build(profile(), emptyList(), summarizeMeds(emptyList(), "2026-09-26"))
        assertEquals("紧急信息", c.title)
    }
}
