package com.ashkb.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B6 钥匙扣二维码：**逐格比对独立参考实现**的黄金向量。
 *
 * ### 为什么这么测
 * 上一版 B6 的四条缺陷（分隔符覆盖不全 / 格式信息位映射错 / N3 判据错 / N4 少一档）
 * 都是靠「自己编、自己解」的回读自测发现不了的——编解码犯同一个错时回读必然一致。
 * 唯一的办法是拿**另一份实现**生成的模块矩阵逐格比：向量由 Nayuki QR-Code-generator（MIT）
 * 产出，见 [QrGoldenVectors]。
 *
 * ### 参数必须与生成器一致
 * 生成器用 `boostEcl = false` + 自动掩码（`mask = -1`）。若这里开 `boostEcl`，
 * 版本会被自动提升，逐格比对必然全红——这是刻意的「同一口径」约定，不是实现缺陷。
 */
class QrGoldenVectorsTest {

    private val vectors: List<QrGoldenVectors.Vector> by lazy { QrGoldenVectors.load() }

    @Test
    fun `向量文件覆盖了多个版本与四种纠错等级`() {
        val versions = vectors.map { it.version }.toSet()
        val eccs = vectors.map { it.ecc }.toSet()
        assertTrue("向量版本覆盖不足：$versions", versions.size >= MIN_DISTINCT_VERSIONS)
        assertEquals("四种纠错等级都应被覆盖", QrEcc.entries.toSet(), eccs)
        assertTrue("应覆盖版本 7 以上（含版本信息块）", versions.any { it >= VERSION_INFO_MIN })
    }

    @Test
    fun `每一组向量都与独立参考实现逐格一致`() {
        val failures = ArrayList<String>()
        for (vector in vectors) {
            val label = "text=${vector.text.take(LABEL_PREVIEW)}… ecc=${vector.ecc} version=${vector.version}"
            val actual = runCatching {
                QrCode.encodeText(vector.text, vector.ecc, boostEcl = false)
            }.getOrNull()
            if (actual == null) {
                failures.add("$label 编码抛异常")
            } else {
                vector.firstDifference(actual)?.let { failures.add("$label 不一致：$it") }
            }
        }
        assertTrue("与参考实现不一致的向量：\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun `单组向量的尺寸与版本自洽`() {
        for (vector in vectors) {
            assertEquals("版本 ${vector.version} 的边长应为 4×版本+17", vector.version * 4 + 17, vector.size)
            assertEquals("行数应等于边长", vector.size, vector.rows.size)
            assertTrue("每个掩码都应在 0..7", vector.mask in 0..7)
        }
    }

    @Test
    fun `自动选掩码的结果与参考实现选出的掩码相同`() {
        // 掩码选择依赖罚分函数的每一个细节（N1–N4）。若 N4 少算一档、或 N3 判据写成 11 模块完整匹配，
        // 选出的掩码就会与参考实现不同——这一条是四条罚分规则的整体回归。
        for (vector in vectors) {
            val actual = QrCode.encodeText(vector.text, vector.ecc, boostEcl = false)
            assertEquals(
                "text=${vector.text.take(LABEL_PREVIEW)}… ecc=${vector.ecc} 的掩码与参考实现不同",
                vector.mask,
                actual.mask,
            )
        }
    }

    @Test
    fun `空内容也能编出合法的版本 1 二维码`() {
        val vector = vectors.firstOrNull { it.text.isEmpty() } ?: error("向量里缺少空串用例")
        val actual = QrCode.encodeText("", vector.ecc, boostEcl = false)
        assertEquals(1, actual.version)
        assertEquals(VERSION_ONE_SIZE, actual.size)
        assertNull("空串用例应与参考实现逐格一致", vector.firstDifference(actual))
    }

    @Test
    fun `开启纠错等级提升后版本不变而等级不低于请求值`() {
        // 生产代码默认 boostEcl = true：容量有余时自动升级纠错等级（更耐脏）。
        // 要求是「只升不降、且不因为升级而换版本」。
        val text = "ASHKB\n血型:B\n过敏:青霉素"
        for (ecc in QrEcc.entries) {
            val boosted = QrCode.encodeText(text, ecc, boostEcl = true)
            val plain = QrCode.encodeText(text, ecc, boostEcl = false)
            assertTrue(
                "提升后的纠错等级不应低于请求值：请求 $ecc，实际 ${boosted.ecc}",
                boosted.ecc.ordinal >= ecc.ordinal,
            )
            assertEquals("升级纠错等级不应改变版本", plain.version, boosted.version)
        }
    }

    private companion object {
        const val MIN_DISTINCT_VERSIONS = 6
        const val VERSION_INFO_MIN = 7
        const val VERSION_ONE_SIZE = 21
        const val LABEL_PREVIEW = 24
    }
}
