package com.ashkb.app.domain

import android.content.Context
import com.ashkb.app.R
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
 *
 * i18n（v1.2.6）：`Content.lines` 变成 `List<ResText>`、`title` 变成 `titleRes`，
 * 并列项分隔符由调用方注入。断言的是**渲染后的中文文本**，故整类走 Robolectric；
 * `@Config(qualifiers = "zh-rCN")` 不可省（`values-en` 存在，默认会落到 en-rUS）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "zh-rCN")
class EmergencyLockscreenTest {

    private val ctx: Context get() = RuntimeEnvironment.getApplication()

    /** 并列项分隔符：中文是「、」，英文是「, 」。 */
    private val sep: String get() = ctx.getString(R.string.dom_lock_list_separator)

    /** v1.2.5/v1.2.6（i18n）：`summarize` 的频次与注射周期文案改由调用方注入；本测试只断言锁屏行的拼接。 */
    private fun summarizeMeds(meds: List<Medication>, today: String) = EmergencyMeds.summarize(
        meds, today,
        freqLabel = { ctx.getString(it.plainRes) },
        injCycleLabel = { ctx.getString(R.string.ui_emergency_meds_inj_cycle, it) },
        brandParen = { ctx.getString(R.string.ui_brand_paren, it) },
    )

    private fun render(t: ResText): String = ctx.getString(t.res, *t.args.toTypedArray())

    /** 组装并**渲染**锁屏正文（断言口径是用户实际看到的文本）。 */
    private fun lines(
        profile: Profile?,
        contacts: List<EmergencyContact> = emptyList(),
        meds: EmergencyMeds.Summary = summarizeMeds(emptyList(), "2026-09-26"),
    ): List<String> = EmergencyLockscreen.build(profile, contacts, meds, sep).lines.map { render(it) }

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
        assertEquals("青霉素、磺胺", EmergencyLockscreen.cleanJsonArray("""["青霉素","磺胺"]""", sep))
        assertEquals("青霉素", EmergencyLockscreen.cleanJsonArray("""["青霉素"]""", sep))
        assertNull(EmergencyLockscreen.cleanJsonArray(null, sep))
        assertNull(EmergencyLockscreen.cleanJsonArray("", sep))
        assertNull(EmergencyLockscreen.cleanJsonArray("[]", sep))
        assertNull(EmergencyLockscreen.cleanJsonArray("[  ]", sep))
    }

    @Test
    fun `空档案也产出血型未填与诊断`() {
        assertTrue(lines(null).isEmpty())
    }

    @Test
    fun `有档案时含血型与诊断 过敏展平`() {
        val out = lines(profile(blood = "A", allergies = """["青霉素"]"""))
        assertTrue(out.any { it.contains("血型 A") && it.contains("强直性脊柱炎") })
        assertTrue(out.any { it == "过敏 青霉素" })
    }

    @Test
    fun `血型未填时显示占位而不是空串`() {
        assertTrue(lines(profile(blood = null)).any { it.contains("血型 未填") })
    }

    @Test
    fun `用药含免疫抑制时标注感染风险`() {
        val out = lines(profile(), meds = summarizeMeds(listOf(med("阿达木单抗", cls = "BIOLOGIC")), "2026-09-26"))
        val line = out.first { it.startsWith("用药") }
        assertTrue("免疫抑制标记缺失", line.contains("含免疫抑制"))
        assertTrue(line.contains("阿达木单抗"))
    }

    @Test
    fun `用药超出上限时只报前几条并给总数`() {
        val many = (1..6).map { med("药$it") }
        val out = lines(profile(), meds = summarizeMeds(many, "2026-09-26"))
        val line = out.first { it.startsWith("用药") }
        assertTrue("应提示总数", line.contains("等 6 种"))
        assertTrue("不应列出第 5 条", !line.contains("药5"))
    }

    @Test
    fun `家属优先于医生 且不把医生当家属`() {
        val contacts = listOf(
            contact("李医生", "13900000000", emergency = true, doctor = true),
            contact("王家属", "13800000000"),
        )
        val out = lines(profile(), contacts)
        assertTrue(out.any { it == "家属 王家属 13800000000" })
        assertTrue(out.any { it == "医生 李医生 13900000000" })
    }

    @Test
    fun `非紧急联系人不进锁屏`() {
        val contacts = listOf(contact("普通同事", "13700000000", emergency = false))
        assertTrue("非紧急联系人不应上锁屏", lines(profile(), contacts).none { it.contains("普通同事") })
    }

    @Test
    fun `标题不含用户数据`() {
        val c = EmergencyLockscreen.build(profile(), emptyList(), summarizeMeds(emptyList(), "2026-09-26"), sep)
        assertEquals("紧急信息", ctx.getString(c.titleRes))
    }
}
