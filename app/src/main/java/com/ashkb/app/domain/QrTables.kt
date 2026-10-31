package com.ashkb.app.domain

/**
 * 二维码「版本 / 纠错等级」相关的规范常量表（ISO/IEC 18004 表 9 与附录 E）。
 *
 * ### 为什么这些表是硬编码的
 * 两个表都只有 4 × 41 个字节，却决定了每种版本下「每块放多少纠错码字」「分成多少块」。
 * 用公式推导要写一堆版本区间判断（标准里就是从表来的，没有闭式解），表更不容易写错，
 * 也便于与规范逐格对照。表值本身来自 ISO/IEC 18004，不是任何实现的私有数据。
 *
 * 下标 0 位是占位（标 `-1`），这样 `表[version]` 可以直观读，不必到处 `-1`。
 *
 * 纯 Kotlin、无 Android 依赖，可单测。
 */
internal object QrTables {

    /** 表下标 0 的占位值（版本从 1 开始，没有「版本 0」）。 */
    private const val VERSION_SLOT_UNUSED = -1

    /** 数据码字之外的「每块纠错码字数」——`[ecc.ordinal][version]`。 */
    private val ECC_CODEWORDS_PER_BLOCK = arrayOf(
        // LOW
        intArrayOf(
            VERSION_SLOT_UNUSED, 7, 10, 15, 20, 26, 18, 20, 24, 30, 18,
            20, 24, 26, 30, 22, 24, 28, 30, 28, 28, 28,
            28, 30, 30, 26, 28, 30, 30, 30, 30, 30,
            30, 30, 30, 30, 30, 30, 30, 30, 30, 30,
        ),
        // MEDIUM
        intArrayOf(
            VERSION_SLOT_UNUSED, 10, 16, 26, 18, 24, 16, 18, 22, 22, 26,
            30, 22, 22, 24, 24, 28, 28, 26, 26, 26, 26,
            28, 28, 28, 28, 28, 28, 28, 28, 28, 28,
            28, 28, 28, 28, 28, 28, 28, 28, 28, 28,
        ),
        // QUARTILE
        intArrayOf(
            VERSION_SLOT_UNUSED, 13, 22, 18, 26, 18, 24, 18, 22, 20, 24,
            28, 26, 24, 20, 30, 24, 28, 28, 26, 30, 28,
            30, 30, 30, 30, 28, 30, 30, 30, 30, 30,
            30, 30, 30, 30, 30, 30, 30, 30, 30, 30,
        ),
        // HIGH
        intArrayOf(
            VERSION_SLOT_UNUSED, 17, 28, 22, 16, 22, 28, 26, 26, 24, 28,
            24, 28, 22, 24, 24, 30, 28, 28, 26, 28, 30,
            24, 30, 30, 30, 30, 30, 30, 30, 30, 30,
            30, 30, 30, 30, 30, 30, 30, 30, 30, 30,
        ),
    )

    /** 纠错分块数——`[ecc.ordinal][version]`。 */
    private val NUM_ERROR_CORRECTION_BLOCKS = arrayOf(
        // LOW
        intArrayOf(
            VERSION_SLOT_UNUSED, 1, 1, 1, 1, 1, 2, 2, 2, 2, 4,
            4, 4, 4, 4, 6, 6, 6, 6, 7, 8, 8,
            9, 9, 10, 12, 12, 12, 13, 14, 15, 16,
            17, 18, 19, 19, 20, 21, 22, 24, 25,
        ),
        // MEDIUM
        intArrayOf(
            VERSION_SLOT_UNUSED, 1, 1, 1, 2, 2, 4, 4, 4, 5, 5,
            5, 8, 9, 9, 10, 10, 11, 13, 14, 16, 17,
            17, 18, 20, 21, 23, 25, 26, 28, 29, 31,
            33, 35, 37, 38, 40, 43, 45, 47, 49,
        ),
        // QUARTILE
        intArrayOf(
            VERSION_SLOT_UNUSED, 1, 1, 2, 2, 4, 4, 6, 6, 8, 8,
            8, 10, 12, 16, 12, 17, 16, 18, 21, 20, 23,
            23, 25, 27, 29, 34, 34, 35, 38, 40, 43,
            45, 48, 51, 53, 56, 59, 62, 65, 68,
        ),
        // HIGH
        intArrayOf(
            VERSION_SLOT_UNUSED, 1, 1, 2, 4, 4, 4, 5, 6, 8, 8,
            11, 11, 16, 16, 18, 16, 19, 21, 25, 25, 25,
            34, 30, 32, 35, 37, 40, 42, 45, 48, 51,
            54, 57, 60, 63, 66, 70, 74, 77, 81,
        ),
    )

    /** 版本 1..40 —— 见 [QrCode.MIN_VERSION] / [QrCode.MAX_VERSION]。 */
    fun eccCodewordsPerBlock(ecc: QrEcc, version: Int): Int = ECC_CODEWORDS_PER_BLOCK[ecc.ordinal][version]

    fun numEccBlocks(ecc: QrEcc, version: Int): Int = NUM_ERROR_CORRECTION_BLOCKS[ecc.ordinal][version]

    // ---------- 模块数公式里的规范常量（ISO/IEC 18004 §7.1 与附录 E） ----------

    /** 模块边长 = 版本 × 4 + 17（规范 §7.1）；这两个值与 [QrCode] 的同一公式必须一致。 */
    private const val SIZE_PER_VERSION = 4
    private const val SIZE_OFFSET = 17

    /** 每个定位图案连同分隔符占 8×8 模块，共三个。 */
    private const val FINDER_AREA_MODULES = 8 * 8 * 3

    /** 两份格式信息各 15 位，加一个恒深模块。 */
    private const val FORMAT_AREA_MODULES = 15 * 2 + 1

    /** 时序图案的长度上界扣减：三个定位图案两侧各占 8 模块。 */
    private const val TIMING_AREA_TRIM = 16

    /** 对齐图案外框 5×5 = 25 模块。 */
    private const val ALIGN_BLOCK_MODULES = 25

    /** 对齐图案落在时序图案上的那两条十字带，各 20 模块。 */
    private const val ALIGN_OVERLAP_MODULES = 20

    /** 版本信息块：两块，每块 6×3 模块。 */
    private const val VERSION_INFO_MODULES = 6 * 3 * 2

    /** 对齐图案坐标递推的规范参数（附录 E）：`step = (version×8 + numAlign×3 + 5) / (numAlign×4 − 4) × 2`。 */
    private const val ALIGN_NUM_DIVISOR = 7
    private const val ALIGN_NUM_BASE = 2
    private const val ALIGN_STEP_VERSION_WEIGHT = 8
    private const val ALIGN_STEP_NUM_WEIGHT = 3
    private const val ALIGN_STEP_BIAS = 5
    private const val ALIGN_STEP_DIVISOR_SLOPE = 4
    private const val ALIGN_STEP_DIVISOR_BIAS = 4
    private const val ALIGN_STEP_EVEN_FACTOR = 2
    private const val ALIGN_FIRST_POSITION = 6
    private const val ALIGN_LAST_OFFSET = 7

    /** 版本信息只在版本 7 及以上存在。 */
    private const val VERSION_INFO_MIN = 7

    /** 模块数 → 码字数：一个码字 8 位，余位丢弃。 */
    private const val BITS_PER_CODEWORD = 8

    /**
     * 该版本除掉全部功能图案后剩下的模块数（**含** 0..7 个余位，故不一定是 8 的倍数）。
     *
     * 规范给的是查表值；这里按领域公式算，五步分别减去三个定位图案（含分隔符）、
     * 两份格式信息 + 深色模块、时序图案（去掉定位图案那一段）、对齐图案、版本信息。
     */
    fun numRawDataModules(version: Int): Int {
        require(version in QrCode.MIN_VERSION..QrCode.MAX_VERSION) { "version out of range: $version" }
        val size = version * SIZE_PER_VERSION + SIZE_OFFSET
        var result = size * size
        result -= FINDER_AREA_MODULES
        result -= FORMAT_AREA_MODULES
        result -= (size - TIMING_AREA_TRIM) * 2
        if (version >= ALIGN_NUM_BASE) {
            val numAlign = version / ALIGN_NUM_DIVISOR + ALIGN_NUM_BASE
            result -= (numAlign - 1) * (numAlign - 1) * ALIGN_BLOCK_MODULES
            result -= (numAlign - ALIGN_NUM_BASE) * 2 * ALIGN_OVERLAP_MODULES
            if (version >= VERSION_INFO_MIN) result -= VERSION_INFO_MODULES
        }
        return result
    }

    /** 该版本 + 纠错等级下可用于「数据 + 终止符 + 填充」的码字数。 */
    fun numDataCodewords(version: Int, ecc: QrEcc): Int =
        numRawDataModules(version) / BITS_PER_CODEWORD -
            eccCodewordsPerBlock(ecc, version) * numEccBlocks(ecc, version)

    /**
     * 对齐图案的中心坐标列表（升序，行列共用）。
     *
     * 版本 1 没有对齐图案。其余版本按规范附录 E 的递推：首个恒为 6、末个恒为 `size - 7`，
     * 步长取偶数。
     */
    fun alignmentPatternPositions(version: Int): IntArray {
        if (version == 1) return IntArray(0)
        val size = version * SIZE_PER_VERSION + SIZE_OFFSET
        val numAlign = version / ALIGN_NUM_DIVISOR + ALIGN_NUM_BASE
        val numerator = version * ALIGN_STEP_VERSION_WEIGHT + numAlign * ALIGN_STEP_NUM_WEIGHT + ALIGN_STEP_BIAS
        val denominator = numAlign * ALIGN_STEP_DIVISOR_SLOPE - ALIGN_STEP_DIVISOR_BIAS
        val step = numerator / denominator * ALIGN_STEP_EVEN_FACTOR
        val result = IntArray(numAlign)
        result[0] = ALIGN_FIRST_POSITION
        var pos = size - ALIGN_LAST_OFFSET
        for (i in numAlign - 1 downTo 1) {
            result[i] = pos
            pos -= step
        }
        return result
    }
}
