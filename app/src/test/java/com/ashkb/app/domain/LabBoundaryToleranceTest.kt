package com.ashkb.app.domain

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.2.13（医学缺口③）：按化验项配置「临界」容差的**存储与查表**守卫。
 *
 * 判据本身（越界优先、量程为 0 不判）由 `LabNearBoundaryTest` 覆盖，这里只测
 * [LabBoundaryTolerance] 这一层——它决定的不是「某项临界不临界」，而是
 * 「**这个配置存进去还能不能原样读回来**」以及「查表时会不会配了却不生效」。
 *
 * 不用 Robolectric 的原因：`parse` / `serialize` / `ratioFor` / `ratioOfPercent`
 * 全是纯函数，只有 `load` / `save` 碰 SharedPreferences——那两个方法的语义
 * （脏值丢行、越界丢项）在纯函数里已经完整表达，起一套 Android 运行时只为了
 * 验证 `getString` 返回同一个字符串，收益不成比例。
 */
class LabBoundaryToleranceTest {

    @After
    fun tearDown() {
        LabBoundaryTolerance.resetForTest()
    }

    @Test
    fun `序列化出来的文本能被原样解析回来`() {
        val config = linkedMapOf("ESR" to 0.02f, "CRP" to 0.1f, "血红蛋白" to 0.005f)
        val roundTrip = LabBoundaryTolerance.parse(LabBoundaryTolerance.serialize(config))
        // 百分比只留一位小数（2 / 10 / 0.5 都是干净值），往返必须逐项值相等
        assertEquals("存进去的配置必须能原样读出来，否则用户改了等于没改", config.keys, roundTrip.keys)
        config.forEach { (name, ratio) ->
            assertEquals("$name 往返后不一致", ratio, roundTrip[name]!!, TOLERANCE)
        }
    }

    @Test
    fun `脏行只丢那一行 不把整份配置作废`() {
        val raw = listOf(
            "ESR=2",        // 正常
            "=5",           // 名字为空 → 丢
            "CRP=abc",      // 不是数字 → 丢
            "血糖=0.1",     // 0.1% 小于下限 → 丢
            "白细胞=99",    // 99% 大于上限 → 丢
            "血红蛋白=10",  // 正常
        ).joinToString("\n")
        val parsed = LabBoundaryTolerance.parse(raw)
        assertEquals(
            "坏行只能丢自己，好行必须保留——否则一行脏配置会静默清空用户所有设置",
            listOf("ESR", "血红蛋白"),
            parsed.keys.toList(),
        )
        assertEquals(0.02f, parsed.getValue("ESR"), TOLERANCE)
        assertEquals(0.1f, parsed.getValue("血红蛋白"), TOLERANCE)
    }

    @Test
    fun `空配置与 null 都解析成空表而不是抛异常`() {
        assertTrue(LabBoundaryTolerance.parse(null).isEmpty())
        assertTrue(LabBoundaryTolerance.parse("").isEmpty())
        assertTrue(LabBoundaryTolerance.parse("   \n  \n").isEmpty())
    }

    @Test
    fun `越界或非有限的值在序列化时就被丢掉`() {
        val text = LabBoundaryTolerance.serialize(
            mapOf(
                "ESR" to 0.02f,
                "太低" to 0.001f,
                "太高" to 0.9f,
                "NaN" to Float.NaN,
                "   " to 0.05f,
            ),
        )
        val parsed = LabBoundaryTolerance.parse(text)
        assertEquals("只有合法项该留下", listOf("ESR"), parsed.keys.toList())
        assertEquals(0.02f, parsed.getValue("ESR"), TOLERANCE)
    }

    @Test
    fun `查表忽略首尾空白与大小写`() {
        val config = mapOf("ESR" to 0.02f)
        assertEquals(
            "同一项会被写成 ESR / Esr / esr（不同医院模板、AI 导入、手工录入），按字面查会导致配了不生效",
            0.02f,
            LabBoundaryTolerance.ratioFor(config, " esr "),
            TOLERANCE,
        )
        assertEquals(0.02f, LabBoundaryTolerance.ratioFor(config, "Esr"), TOLERANCE)
        assertEquals(0.02f, LabBoundaryTolerance.ratioFor(config, "ESR"), TOLERANCE)
    }

    @Test
    fun `没配过的项回落到默认容差而不是 0`() {
        assertEquals(
            "没配过 ≠ 容差为 0——返回 0 会让所有项永远不报临界",
            LabBoundaryTolerance.DEFAULT_RATIO,
            LabBoundaryTolerance.ratioFor(mapOf("ESR" to 0.02f), "CRP"),
            TOLERANCE,
        )
        assertEquals(
            LabBoundaryTolerance.DEFAULT_RATIO,
            LabBoundaryTolerance.ratioFor(emptyMap(), ""),
            TOLERANCE,
        )
    }

    @Test
    fun `默认容差与全局常量同源`() {
        assertEquals(
            "默认值必须跟着 ClinicalThresholds 走，否则「全局默认」会有两份真值",
            0.05f,
            LabBoundaryTolerance.DEFAULT_RATIO,
            TOLERANCE,
        )
    }

    @Test
    fun `百分数与比例互转 越界返回 null 而不是夹到边界`() {
        assertEquals(0.05f, LabBoundaryTolerance.ratioOfPercent(5f)!!, TOLERANCE)
        assertEquals(0.005f, LabBoundaryTolerance.ratioOfPercent(0.5f)!!, TOLERANCE)
        assertEquals(0.2f, LabBoundaryTolerance.ratioOfPercent(20f)!!, TOLERANCE)
        // 越界必须明确失败：悄悄夹到 0.5% 会让人以为自己填的 0.1% 生效了
        assertNull(LabBoundaryTolerance.ratioOfPercent(0.4f))
        assertNull(LabBoundaryTolerance.ratioOfPercent(20.1f))
        assertNull(LabBoundaryTolerance.ratioOfPercent(null))
        assertNull(LabBoundaryTolerance.ratioOfPercent(Float.NaN))
        assertEquals(5f, LabBoundaryTolerance.percentOf(0.05f), TOLERANCE)
    }

    @Test
    fun `百分比展示不留浮点尾巴`() {
        val text = LabBoundaryTolerance.serialize(mapOf("ESR" to 0.05f, "CRP" to 0.055f))
        assertEquals("5 要写成 5 而不是 5.0；5.5 保留一位小数", "ESR=5\nCRP=5.5", text)
    }

    @Test
    fun `按项容差真的会改变临界判定`() {
        // 参考范围 0–20，值 19：距边界 1，量程 20 → 占 5%
        val value = 19.0
        val refLow = 0.0
        val refHigh = 20.0
        assertTrue(
            "默认 5% 时 19 恰在边界上，应算临界",
            ClinicalThresholds.labNearBoundary(value, refLow, refHigh, ratio = 0.05f),
        )
        assertTrue(
            "容差调到 2% 后同一数值不该再报临界——这就是本功能的全部意义",
            !ClinicalThresholds.labNearBoundary(value, refLow, refHigh, ratio = 0.02f),
        )
    }

    @Test
    fun `非法容差让判定直接返回 false 而不是静默抹掉`() {
        // NaN ≤ x 恒为 false：若不放行早退，浮点 NaN 静默把整类临界抹掉且没有任何提示
        assertTrue(!ClinicalThresholds.labNearBoundary(19.0, 0.0, 20.0, ratio = Float.NaN))
        assertTrue(!ClinicalThresholds.labNearBoundary(19.0, 0.0, 20.0, ratio = 0f))
    }

    private companion object {
        const val TOLERANCE = 1e-6f
    }
}
