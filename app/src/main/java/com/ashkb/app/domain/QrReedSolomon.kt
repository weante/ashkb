package com.ashkb.app.domain

/**
 * 二维码用的 Reed-Solomon 纠错码（GF(2^8)，本原多项式 x^8+x^4+x^3+x^2+1 = 0x11D）。
 *
 * ### 为什么单独一个文件
 * 这是整个编码器里唯一「不能靠读图案看出对错」的部分：算错了二维码照样画得出来，
 * 只是扫不动。单独隔离成纯函数，就可以用规范附录里的已知向量直接断言，
 * 不必先把整个图案画出来再反推。
 *
 * ### 坐标系
 * 多项式系数按**最高次在前**存放，且省略恒为 1 的最高次项。例如 `x^3 + 255x^2 + 8x + 93`
 * 存成 `[255, 8, 93]`。
 *
 * 纯 Kotlin、无 Android 依赖，可单测。
 */
internal object QrReedSolomon {

    /** GF(2^8) 的本原多项式，见规范附录 A。 */
    private const val FIELD_POLYNOMIAL = 0x11D

    /** 域元素是 8 位，乘法循环要走满 8 轮。 */
    private const val FIELD_BITS = 8

    private const val BYTE_MASK = 0xFF

    /** 域元素最高位（第 7 位）——乘法里判断是否要按本原多项式约简。 */
    private const val FIELD_TOP_BIT = 7

    /** 生成多项式的本原元，规范固定取 2。 */
    private const val GENERATOR_ROOT = 0x02

    /**
     * 求「生成多项式」`(x - r^0)(x - r^1)…(x - r^(degree-1))` 的系数（省略最高次项 1）。
     *
     * @param degree 纠错码字数，范围 `1..255`
     */
    fun computeDivisor(degree: Int): ByteArray {
        require(degree in 1..BYTE_MASK) { "纠错码字数超出范围: $degree" }
        val result = ByteArray(degree)
        result[degree - 1] = 1 // 从 x^0 起步，逐步乘上 (x - r^i)

        var root = 1
        repeat(degree) {
            for (j in result.indices) {
                result[j] = multiply(result[j].toInt() and BYTE_MASK, root).toByte()
                // 减等同于加（特征 2），把高次项搬到低位
                if (j + 1 < result.size) result[j] = (result[j].toInt() xor result[j + 1].toInt()).toByte()
            }
            root = multiply(root, GENERATOR_ROOT)
        }
        return result
    }

    /**
     * 用 [divisor] 对 [data] 做多项式除法，返回余数——也就是要写进图案的纠错码字。
     */
    fun computeRemainder(data: ByteArray, divisor: ByteArray): ByteArray {
        require(divisor.isNotEmpty()) { "生成多项式不能为空" }
        val result = ByteArray(divisor.size)
        for (b in data) {
            val factor = (b.toInt() xor result[0].toInt()) and BYTE_MASK
            // 整体左移一位（次数升一），末尾补 0
            for (i in 0 until result.size - 1) result[i] = result[i + 1]
            result[result.size - 1] = 0
            for (i in result.indices) {
                result[i] = (result[i].toInt() xor multiply(divisor[i].toInt() and BYTE_MASK, factor)).toByte()
            }
        }
        return result
    }

    /**
     * GF(2^8) 乘法（俄罗斯农民乘法），结果按 [FIELD_POLYNOMIAL] 约简。
     *
     * 不用 256×256 查表：一次乘法只有 8 轮移位异或，而二维码最多算几千次，
     * 换一张 64 KiB 的表不值得（这个类还要在单元测试里跑几百遍）。
     */
    fun multiply(x: Int, y: Int): Int {
        require(x in 0..BYTE_MASK && y in 0..BYTE_MASK) { "乘数超出字节范围: $x * $y" }
        var z = 0
        for (i in FIELD_BITS - 1 downTo 0) {
            // 先翻倍（溢出 8 位就按本原多项式约简），再按 y 的第 i 位决定是否 XOR 上 x
            z = (z shl 1) xor (((z ushr FIELD_TOP_BIT) and 1) * FIELD_POLYNOMIAL)
            z = z xor (((y ushr i) and 1) * x)
        }
        return z and BYTE_MASK
    }
}
