package com.ashkb.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 指标名称归一与单位换算（趋势页方案 C）。
 *
 * 这一层的价值全在「口径」上，所以测试也针对口径：
 * 该认出来的写法要认（化验单写法五花八门），不该认的一个都不能认（配错＝静默画错指标）。
 */
class LabIndicatorTest {

    // ======================= 名称匹配：该认的认 =======================

    @Test
    fun `血沉的各种写法都能匹配`() {
        val forms = listOf(
            "血沉", "血沉(ESR)", "ESR", "esr", "Esr",
            "血沉（ESR）",          // 全角括号
            " 血沉 ",               // 前后空格
            "血 沉 ( ESR )",        // 内部空格（归一后等价）
            "红细胞沉降率", "红细胞沉降率(ESR)",
            "血沉测定", "血沉定量",
        )
        forms.forEach { assertTrue("应匹配：$it", LabIndicator.ESR.matches(it)) }
    }

    @Test
    fun `C 反应蛋白的各种写法都能匹配`() {
        val forms = listOf(
            "C反应蛋白", "C-反应蛋白", "CRP", "crp", "C反应蛋白(CRP)", "C-反应蛋白（CRP）",
            "c反应蛋白测定", "crp测定",
        )
        forms.forEach { assertTrue("应匹配 CRP：$it", LabIndicator.CRP.matches(it)) }
    }

    /**
     * 回归锁（v1.0.55 实测事故）：**超敏 CRP 必须与常规 CRP 分开**。
     *
     * 同一份化验单常同时报这两项，量级差一个数量级（炎症期 CRP 36.33 mg/L，
     * 而 hs-CRP 0.4 mg/L 仍属正常）。v1.0.54 把 hs-CRP 并进了 CRP，
     * 按日期去重时常选中 hs-CRP 的小数值 → **图上 CRP 是 0.4、化验单上却是 36.33**。
     */
    @Test
    fun `超敏C反应蛋白自成一项 绝不再并入常规 CRP`() {
        val hsForms = listOf(
            "超敏C反应蛋白", "超敏C反应蛋白(hs-CRP)", "hs-CRP", "Hs-Crp", "超敏CRP",
            "超敏C反应蛋白(CRP)", "超敏C反应蛋白测定",
        )
        hsForms.forEach {
            assertTrue("应匹配 HSCRP：$it", LabIndicator.HSCRP.matches(it))
            assertFalse("**绝不能**匹配常规 CRP：$it", LabIndicator.CRP.matches(it))
        }
        // 反向：常规 CRP 的写法不能被 HSCRP 认领（否则同一行会被画两遍）
        listOf("C反应蛋白", "CRP", "C-反应蛋白(CRP)").forEach {
            assertFalse("HSCRP 不应匹配常规写法：$it", LabIndicator.HSCRP.matches(it))
        }
    }

    @Test
    fun `超敏 CRP 是二级指标 无数据时不占格子`() {
        assertTrue("ESR 恒占格", LabIndicator.ESR.alwaysShow)
        assertTrue("CRP 恒占格", LabIndicator.CRP.alwaysShow)
        assertFalse("hs-CRP 仅在有数据时占格", LabIndicator.HSCRP.alwaysShow)
        // 单项测试里更要紧的：三项互不重复，项数就是 3
        assertEquals(3, LabIndicator.entries.size)
    }

    // ======================= 名称匹配：不该认的绝不认 =======================

    @Test
    fun `其它指标不会被误配`() {
        val others = listOf(
            "白细胞计数(WBC)", "血常规", "血小板计数", "血清淀粉样蛋白A(SAA)",
            "谷丙转氨酶(ALT)", "C肽", "血红蛋白(HGB)", "红细胞计数(RBC)",
        )
        for (name in others) {
            assertFalse("不应匹配 ESR：$name", LabIndicator.ESR.matches(name))
            assertFalse("不应匹配 CRP：$name", LabIndicator.CRP.matches(name))
        }
    }

    @Test
    fun `刻意不做模糊匹配：近似但不同的名字不匹配`() {
        // 若改成 contains，下面这些会被错误地卷进来。必须保持精确匹配。
        assertFalse(LabIndicator.CRP.matches("C反应蛋白前体"))
        assertFalse(LabIndicator.ESR.matches("血沉速率"))
        assertFalse(LabIndicator.CRP.matches("非C反应蛋白"))
    }

    // ======================= 尾限定词 / 括号内容的有限变形（v1.0.55） =======================

    /**
     * 回归锁：用户真实数据里存的名字是**「红细胞沉降率测定」**
     * （复诊管理 → 化验里显示的就是它），v1.0.54 的别名表只有「红细胞沉降率」，
     * 于是趋势页**一个点都认不出来**——用户实测发现。
     * 靠枚举写法永远会漏，故改为有界变形（剥尾限定词 / 去括号）后再比对别名表。
     */
    @Test
    fun `尾限定词与括号的有限变形都能匹配`() {
        val shouldMatch = listOf(
            "红细胞沉降率测定", "红细胞沉降率(ESR)测定", "红细胞沉降率检测", "红细胞沉降率定量",
            "血沉检测", "血沉检验", "血沉(ESR)测定",
            "C反应蛋白定量", "C反应蛋白检测", "C-反应蛋白(crp)测定", "超敏C反应蛋白(hs-CRP)测定",
        )
        shouldMatch.forEach {
            val hit = LabIndicator.entries.any { ind -> ind.matches(it) }
            assertTrue("应被某个炎症指标认领：$it", hit)
        }
    }

    @Test
    fun `变形是有界的：不相关指标带限定词也不会被误配`() {
        val unrelated = listOf("白细胞计数(WBC)测定", "丙氨酸氨基转移酶测定", "肌酐定量", "血红蛋白检测")
        for (name in unrelated) {
            assertFalse("不应匹配 ESR：$name", LabIndicator.ESR.matches(name))
            assertFalse("不应匹配 CRP：$name", LabIndicator.CRP.matches(name))
        }
    }

    @Test
    fun `变形集合的边界行为`() {
        assertTrue("空值不产生任何变形候选", LabIndicator.nameVariants(null).isEmpty())
        assertTrue(LabIndicator.nameVariants("   ").isEmpty())
        // 变形只有两种：剥尾限定词、去括号及其内容（含其后的限定词）
        val v = LabIndicator.nameVariants("红细胞沉降率(ESR)测定")
        assertTrue("原形", v.contains("红细胞沉降率(esr)测定"))
        assertTrue("剥尾限定词", v.contains("红细胞沉降率(esr)"))
        assertTrue("去括号", v.contains("红细胞沉降率"))
        // 去括号形与剥限定词可叠加：带限定词的那个写法必须仍能命中别名表
        assertTrue(LabIndicator.ESR.matches("红细胞沉降率(ESR)测定"))
        assertTrue(LabIndicator.ESR.matches("红细胞沉降率测定"))
    }

    @Test
    fun `空值与空格不匹配任何指标`() {
        for (name in listOf(null, "", "   ")) {
            assertFalse(LabIndicator.ESR.matches(name))
            assertFalse(LabIndicator.CRP.matches(name))
        }
    }

    // ======================= 单位换算 =======================

    @Test
    fun `血沉接受常见 mm 每小时写法`() {
        for (u in listOf("mm/h", "mm/hr", "mm/1h", "MM/H", "mm/h.", "mm/小时")) {
            assertEquals("单位 $u 应视为 1:1", 1.0, LabIndicator.ESR.unitFactor(u)!!, 1e-9)
        }
    }

    @Test
    fun `C 反应蛋白的 mg 每分升按 10 倍换算到 mg 每升`() {
        assertEquals(1.0, LabIndicator.CRP.unitFactor("mg/L")!!, 1e-9)
        assertEquals(1.0, LabIndicator.CRP.unitFactor("MG/L")!!, 1e-9)
        assertEquals(10.0, LabIndicator.CRP.unitFactor("mg/dL")!!, 1e-9)
        // 大小写 + 结尾的点都要容忍（化验单上很常见）
        assertEquals(10.0, LabIndicator.CRP.unitFactor(" mg/DL. ")!!, 1e-9)
    }

    @Test
    fun `认不出的单位与缺失的单位都返回 null（而不是当作 1）`() {
        assertNull(LabIndicator.CRP.unitFactor("g/L"))
        assertNull(LabIndicator.CRP.unitFactor("IU/mL"))
        assertNull(LabIndicator.CRP.unitFactor(null))
        assertNull(LabIndicator.CRP.unitFactor(""))
        assertNull(LabIndicator.CRP.unitFactor("  "))
        // 血沉拿到 CRP 的单位也算认不出
        assertNull(LabIndicator.ESR.unitFactor("mg/L"))
    }

    // ======================= 归一函数本身 =======================

    @Test
    fun `归一化处理大小写 全角括号与所有空白`() {
        assertEquals("血沉(esr)", LabIndicator.normalizeName(" 血沉 （ESR） "))
        assertEquals("c-反应蛋白(crp)", LabIndicator.normalizeName("C-反应蛋白 (CRP)"))
        assertEquals("血沉(esr)", LabIndicator.normalizeName("血\u3000沉\t(ESR)")) // 全角空格与制表符
    }

    @Test
    fun `byCode 可反查且忽略大小写`() {
        assertEquals(LabIndicator.ESR, LabIndicator.byCode("esr"))
        assertEquals(LabIndicator.CRP, LabIndicator.byCode("CRP"))
        assertNull(LabIndicator.byCode("xx"))
        assertNull(LabIndicator.byCode(null))
    }

    @Test
    fun `兜底参考上限来自 ClinicalThresholds（单一来源）`() {
        assertEquals(ClinicalThresholds.ESR_HIGH, LabIndicator.ESR.defaultRefHigh)
        assertEquals(ClinicalThresholds.CRP_HIGH, LabIndicator.CRP.defaultRefHigh)
    }
}
