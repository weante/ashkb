package com.ashkb.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B6 钥匙扣二维码的**内容组装**（v1.2.13）。
 *
 * 逐格正确性由 [QrGoldenVectorsTest] 对着独立参考实现保证；这里只测「正文里写了什么」——
 * 因为二维码再准，内容不对（漏了过敏、印了空行）在急救现场同样是废纸。
 */
class EmergencyQrTest {

    private val zh = EmergencyQr.Labels(
        blood = "血型",
        diagnosis = "诊断",
        allergy = "过敏",
        call = "急救电话",
        allergyJoiner = "、",
    )

    private fun fields(
        bloodType: String? = null,
        diagnosis: String? = null,
        allergies: List<String> = emptyList(),
        emergencyPhone: String? = null,
    ) = EmergencyQr.Fields(bloodType, diagnosis, allergies, emergencyPhone)

    @Test
    fun `四项全缺时不生成二维码`() {
        assertNull(
            "一张只有 ASHKB 的贴纸对急救毫无价值，必须返回 null 让界面提示先补档案",
            EmergencyQr.payload(zh, fields(), null),
        )
    }

    @Test
    fun `空白字符串等同于没填`() {
        assertNull(EmergencyQr.payload(zh, fields(bloodType = "  ", diagnosis = "\t", emergencyPhone = ""), "  "))
    }

    @Test
    fun `首行是品牌行，其余为半角冒号键值`() {
        val text = EmergencyQr.payload(
            zh,
            fields(bloodType = "B", diagnosis = "强直性脊柱炎", emergencyPhone = "13800138000"),
            null,
        )
        assertEquals(
            listOf("ASHKB", "血型:B", "诊断:强直性脊柱炎", "急救电话:13800138000"),
            text!!.split("\n"),
        )
    }

    @Test
    fun `缺失的字段整行不出，而不是留一个空标签`() {
        val text = EmergencyQr.payload(zh, fields(diagnosis = "强直性脊柱炎"), null)!!
        assertEquals(listOf("ASHKB", "诊断:强直性脊柱炎"), text.split("\n"))
        assertFalse("没填血型就不该出现「血型:」", text.contains("血型"))
    }

    @Test
    fun `过敏按注入的分隔符并列，且去重`() {
        val text = EmergencyQr.payload(
            zh,
            fields(allergies = listOf("青霉素", "磺胺", "青霉素")),
            null,
        )!!
        // 必须整行相等：用 contains 的话，「青霉素、磺胺」作为前缀仍能命中，
        // 去掉 distinct() 也照样绿——这正是第一次负向对照没变红的原因。
        assertEquals(
            "并列必须用注入的分隔符（中文顿号），且重复条目要去掉",
            listOf("ASHKB", "过敏:青霉素、磺胺"),
            text.split("\n"),
        )
    }

    @Test
    fun `过敏条目超出上限时截断`() {
        val many = (1..EmergencyQr.MAX_ALLERGIES + 2).map { "药$it" }
        val text = EmergencyQr.payload(zh, fields(allergies = many), null)!!
        val line = text.split("\n").first { it.startsWith("过敏:") }
        assertEquals(
            "过敏条目超过上限会撑大二维码版本，钥匙扣尺寸印不下",
            EmergencyQr.MAX_ALLERGIES,
            line.removePrefix("过敏:").split("、").size,
        )
    }

    @Test
    fun `只有过敏也能生成二维码`() {
        val text = EmergencyQr.payload(zh, fields(allergies = listOf("青霉素")), null)!!
        assertTrue(text.contains("过敏:青霉素"))
    }

    @Test
    fun `过敏能从 profile 的 JSON 列解析出来`() {
        val text = EmergencyQr.payload(zh, fields(bloodType = "B"), """["青霉素","磺胺"]""")!!
        assertTrue("应复用 ProfileLists 的口径解析 JSON 列：$text", text.contains("过敏:青霉素、磺胺"))
    }

    @Test
    fun `调用方给的过敏列表优先于 JSON 列`() {
        val text = EmergencyQr.payload(
            zh,
            fields(allergies = listOf("只有这个")),
            """["不该出现"]""",
        )!!
        assertTrue(text.contains("过敏:只有这个"))
        assertFalse("已解析好的列表更权威，不该再去看 JSON 列", text.contains("不该出现"))
    }

    @Test
    fun `历史脏值（非法 JSON）不会被丢弃`() {
        val text = EmergencyQr.payload(zh, fields(bloodType = "B"), "对\"某药\"过敏")!!
        assertTrue("ProfileLists 对解析不出的历史值原样返回，这里必须把它印出来而不是吞掉：$text", text.contains("过敏:"))
    }

    @Test
    fun `英文界面用注入的分隔符，不出现中文顿号`() {
        val en = EmergencyQr.Labels("Blood type", "Diagnosis", "Allergies", "Emergency call", ", ")
        val text = EmergencyQr.payload(en, fields(allergies = listOf("Penicillin", "Sulfa")), null)!!
        assertTrue(text.contains("Allergies:Penicillin, Sulfa"))
        assertFalse(text.contains("、"))
    }
}
