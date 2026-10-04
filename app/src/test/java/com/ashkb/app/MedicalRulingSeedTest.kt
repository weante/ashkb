package com.ashkb.app

import com.ashkb.app.domain.DrugInteractionKeys
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.1.3（批次 19）：**维护者 5 项医学内容裁决在种子里的落地锁**。
 *
 * ### 为什么内容也要有回归测试
 * 医学内容改错的代价与代码不同：代码写错会崩，内容写错**什么都不发生**——
 * 一句措辞过头的话会安安静静地显示给患者，而 CI 全绿、测试全过。
 * 这个 App 的知识库有 48 条种子、逐条人工改写，**没有任何机制能发现「这句话说过头了」**。
 * 故把本批次的裁决结论逐条钉成断言：这些句子要么在，要么测试红。
 *
 * ### 边界
 * 本测试**不**判断医学结论对错——那由维护者（患者本人）与其医生决定。
 * 它锁的是「维护者裁决过的那句话，在种子里仍然是那一句」，
 * 以及「不再出现被裁决掉的旧说法」。
 */
class MedicalRulingSeedTest {

    /** 单测工作目录是模块目录（`app/`），与 `KbSeedVersionGateTest` 同一约定。 */
    private fun entries(file: String): List<JSONObject> {
        val f = listOf(File("src/main/assets/$file"), File("app/src/main/assets/$file"))
            .firstOrNull { it.isFile }
            ?: error("找不到种子文件 $file")
        val arr = JSONArray(f.readText(Charsets.UTF_8))
        return (0 until arr.length()).map { arr.getJSONObject(it) }
    }

    private fun one(file: String, id: String): JSONObject =
        entries(file).first { it.getString("id") == id }

    private fun payload(file: String, id: String): JSONObject =
        one(file, id).getJSONObject("payload")

    /** payload 里所有字符串值拼成一段可检索文本（数组元素也展开）。 */
    private fun JSONObject.flattenText(): String {
        val sb = StringBuilder()
        fun walk(v: Any?) {
            when (v) {
                is String -> sb.append(v).append('\n')
                is JSONObject -> v.keys().forEach { walk(v.get(it)) }
                is JSONArray -> (0 until v.length()).forEach { walk(v.get(it)) }
                null -> Unit
                else -> sb.append(v.toString()).append('\n')
            }
        }
        walk(this)
        return sb.toString()
    }

    // ------------------------------------------------------------------
    // J-2：itx-002 收窄为 TNF 类级 + 品牌具体性收进 source_name
    // ------------------------------------------------------------------

    @Test
    fun `J-2 itx-002 标题不再以品牌打头且注明 PI 来源`() {
        val e = one("kb_seed_itx.json", "itx-002")
        val title = e.getString("title")
        assertTrue("标题应写明是 TNF 抑制剂类，实际：$title", title.contains("TNF 抑制剂"))
        assertTrue(
            "标题应把品牌具体性收进括注「以阿达木单抗 PI 为例」，实际：$title",
            title.contains("以阿达木单抗 PI 为例"),
        )
        // 品牌仍出现在标题里只能是「以…为例」的括注形式，不能是「阿达木单抗：…」这种主语
        assertFalse("标题不应以品牌名开头", title.startsWith("阿达木单抗"))
    }

    @Test
    fun `J-2 itx-002 的 source_name 承载品牌具体性`() {
        val e = one("kb_seed_itx.json", "itx-002")
        val src = e.getString("source_name")
        assertTrue("source_name 应指明引的是修美乐（阿达木单抗）PI，实际：$src", src.contains("修美乐"))
        assertTrue("source_name 应指明引的是中文处方信息，实际：$src", src.contains("中文处方信息"))
    }

    @Test
    fun `J-2 itx-002 已改挂 TNF 类级键且不再挂具体品牌键`() {
        val p = payload("kb_seed_itx.json", "itx-002")
        assertEquals("itx-002 的 drug_a 应为类级键", "tnf_inhibitor_strict", p.getString("drug_a"))
    }

    // ------------------------------------------------------------------
    // J-1：itx-012 按 ACR 2022 原文拆成两条事实
    // ------------------------------------------------------------------

    @Test
    fun `J-1 itx-012 把剂量阈值挂回非活疫苗（除流感）`() {
        val text = payload("kb_seed_itx.json", "itx-012").flattenText()
        assertTrue("应写明除流感外的非活疫苗要推迟", text.contains("除流感"))
        assertTrue("应写明非活疫苗", text.contains("非活疫苗"))
        assertTrue("应写明推迟至减量至 <20 mg/天", text.contains("<20 mg/天"))
    }

    @Test
    fun `J-1 itx-012 活疫苗写成不分剂量的类别级建议`() {
        val text = payload("kb_seed_itx.json", "itx-012").flattenText()
        assertTrue("活疫苗应作为独立的一条事实出现", text.contains("活疫苗"))
        assertTrue("应写明该建议不分激素剂量", text.contains("不分激素剂量"))
    }

    @Test
    fun `J-1 itx-012 删除了无出处的两周时程条件`() {
        val e = one("kb_seed_itx.json", "itx-012")
        val text = e.getString("title") + "\n" + e.getString("summary") + "\n" +
            payload("kb_seed_itx.json", "itx-012").getString("effect")
        // ACR 2022 全文 "14 days" 0 命中，Table 4 的 † 脚注是纯剂量条件
        assertFalse("结论性文案里不该再出现「14 天」", text.contains("14 天"))
        assertFalse("结论性文案里不该再出现「超过 2 周」", text.contains("超过 2 周"))
        assertFalse("结论性文案里不该再出现「≥14 天」", text.contains("≥14 天"))
    }

    // ------------------------------------------------------------------
    // J-3：exc-001 / exc-003 补「诚实口径」，矩阵不动
    // ------------------------------------------------------------------

    @Test
    fun `J-3 exc-001 与 exc-003 都补了诚实口径说明`() {
        for (id in listOf("exc-001", "exc-003")) {
            val text = payload("kb_seed_exc.json", id).flattenText()
            assertTrue("$id 应有「诚实口径」说明", text.contains("诚实口径"))
        }
    }

    @Test
    fun `J-3 运动矩阵保持原样（维护者只裁决补说明）`() {
        val m1 = payload("kb_seed_exc.json", "exc-001").getJSONObject("grade_matrix")
        assertEquals("exc-001 controlled 保持 pause", "pause", m1.getString("controlled"))
        assertEquals("exc-001 flare 保持 pause", "pause", m1.getString("flare"))
        assertEquals("exc-001 stable 保持 allow", "allow", m1.getString("stable"))

        val m3 = payload("kb_seed_exc.json", "exc-003").getJSONObject("grade_matrix")
        assertEquals("exc-003 controlled 保持 downgrade", "downgrade", m3.getString("controlled"))
        assertEquals("exc-003 flare 保持 pause", "pause", m3.getString("flare"))
        assertEquals("exc-003 stable 保持 recommend", "recommend", m3.getString("stable"))
    }

    // ------------------------------------------------------------------
    // J-8 + P-25：edu-th-002 文案对齐实现；两条阈值条目不再冒充 S2
    // ------------------------------------------------------------------

    @Test
    fun `J-8 edu-th-002 文案与已实现的行为一致`() {
        val e = one("kb_seed_edu.json", "edu-th-002")
        val text = e.getString("title") + e.getString("summary") +
            payload("kb_seed_edu.json", "edu-th-002").getString("condition")
        assertTrue("应写明最近两次，实际：$text", text.contains("最近两次"))
        assertTrue("应写明间隔不限定，实际：$text", text.contains("间隔不限定"))
        // 旧判据「连续 14 日内 ≥2 次」在默认 28 天自评周期下不可达，已废止
        assertFalse("不该再出现旧判据「14 日内」", text.contains("14 日"))
        assertFalse("不该再出现「持续 2 周」", text.contains("持续 2 周"))
    }

    @Test
    fun `P-25 两条无来源阈值条目改标 SYS 而非 S2`() {
        for (id in listOf("edu-th-001", "edu-th-002")) {
            val e = one("kb_seed_edu.json", id)
            assertEquals("$id 无外部来源，不得标 S2", "SYS", e.getString("source_tier"))
            assertEquals("$id 确实没有来源链接", "", e.getString("source_url"))
        }
    }

    // ------------------------------------------------------------------
    // P-28：商业来源降 S4 并标注商业性质
    // ------------------------------------------------------------------

    @Test
    fun `P-28 商业平台来源降为 S4 并在 source_name 标注性质`() {
        val exb = one("kb_seed_exc.json", "exb-004")
        assertEquals("exb-004（myHealthcare，商业平台）应为 S4", "S4", exb.getString("source_tier"))
        assertTrue(
            "exb-004 的 source_name 应标注商业性质",
            exb.getString("source_name").contains("商业"),
        )

        val fdg = one("kb_seed_fdg.json", "fdg-004")
        assertEquals("fdg-004（药智数据，商业平台）应为 S4", "S4", fdg.getString("source_tier"))
        assertTrue(
            "fdg-004 的 source_name 应标注商业性质",
            fdg.getString("source_name").contains("商业"),
        )
    }

    // ------------------------------------------------------------------
    // 门槛：J-2 不得靠「删掉 JAK 的词汇登记」实现
    // ------------------------------------------------------------------

    /**
     * JAK **必须**仍然留在 `biologic` 家族里。
     *
     * 理由：`biologic` 家族是 JAK 唯一一条「同属靶向治疗」的通路；把它摘掉固然能让
     * JAK 收不到 itx-002，但也会让 JAK **一条提示都收不到**——那是收窄过头，
     * 比误报更危险（`MedicationInteractionChainTest` 里 itx-010 的断言就是守这一条）。
     */
    @Test
    fun `biologic 家族仍登记 JAK（不得用删成员的方式实现收窄）`() {
        assertTrue(
            "JAK 必须在 biologic 家族里（否则 JAK 药单零提示）",
            DrugInteractionKeys.drugOf("upadacitinib").contains("biologic"),
        )
    }
}
