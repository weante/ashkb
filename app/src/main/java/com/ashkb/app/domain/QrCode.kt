package com.ashkb.app.domain

/**
 * 二维码编码器（ISO/IEC 18004 Model 2），纯 Kotlin、无 Android 依赖。
 *
 * ### 为什么自己写
 * 项目硬约束是零第三方依赖（`app/build.gradle.kts` 里只有 Jetpack 与 Room），
 * 而钥匙扣急救卡只需要「把一小段中文编成二维码」，引入一个完整 QR 库来换 300 行代码不划算。
 *
 * ### 验证方式（这是上一轮返工的原因）
 * 上一版 B6 用「自己编、自己解」回读自测，全绿但真机扫不出来——因为编解码犯的是同一个错。
 * 现在**不提供解码器**，验证一律拿独立参考实现（Nayuki QR-Code-generator，MIT）生成的
 * 黄金向量逐格比对，向量见 `app/src/test/resources/qr/vectors.txt`。
 *
 * ### 分层
 * · [QrTables] 版本/纠错表与几何公式
 * · [QrReedSolomon] 纠错码
 * · [QrSegment] / [QrBitBuffer] 位流组装
 * · 本文件 图案绘制与掩码选择
 *
 * 每个步骤的方法名与注释都刻意对照规范的条款顺序排列，便于逐条核对。
 */
internal class QrCode private constructor(
    /** 版本 1..40，决定边长 `version * 4 + 17`。 */
    val version: Int,
    /** 实际使用的纠错等级（自动提升后可能高于请求值）。 */
    val ecc: QrEcc,
    requestedMask: Int,
    dataCodewords: ByteArray,
) {

    /** 边长（模块数），奇数，21..177。 */
    val size: Int = version * SIZE_PER_VERSION + SIZE_OFFSET

    /** 实际使用的掩码 0..7。 */
    val mask: Int

    private val modules = Array(size) { BooleanArray(size) }
    private val isFunction = Array(size) { BooleanArray(size) }

    init {
        require(dataCodewords.size == QrTables.numDataCodewords(version, ecc)) {
            "数据码字数不对：需要 ${QrTables.numDataCodewords(version, ecc)}，实际 ${dataCodewords.size}"
        }
        drawFunctionPatterns()
        drawCodewords(addEccAndInterleave(dataCodewords))
        mask = if (requestedMask == MASK_AUTO) chooseBestMask() else requestedMask
        applyMask(mask)
        drawFormatBits(mask)
    }

    /** `x` 向右、`y` 向下，左上角为 (0, 0)。越界一律返回 `false`（浅色）。 */
    fun isDark(x: Int, y: Int): Boolean = x in 0 until size && y in 0 until size && modules[y][x]

    // ---------- 一、功能图案 ----------

    /**
     * 画出所有功能图案（时序、定位、对齐、格式信息占位、版本信息）。
     *
     * 顺序照规范 §7.3：先时序、再定位（会盖住部分时序）、再对齐（避开三个定位角）、
     * 最后写格式信息（此处先用掩码 0 占位，选定掩码后重写）与版本信息。
     */
    private fun drawFunctionPatterns() {
        for (i in 0 until size) {
            // 时序图案：第 6 行与第 6 列，深浅交替
            setFunctionModule(TIMING_INDEX, i, i % 2 == 0)
            setFunctionModule(i, TIMING_INDEX, i % 2 == 0)
        }
        // 三个定位图案（含各自分隔符），中心分别在左上、右上、左下
        drawFinderPattern(FINDER_CENTER, FINDER_CENTER)
        drawFinderPattern(size - FINDER_CENTER - 1, FINDER_CENTER)
        drawFinderPattern(FINDER_CENTER, size - FINDER_CENTER - 1)

        val positions = QrTables.alignmentPatternPositions(version)
        val last = positions.size - 1
        for (i in positions.indices) {
            for (j in positions.indices) {
                val onFinderCorner = (i == 0 && j == 0) || (i == 0 && j == last) || (i == last && j == 0)
                if (!onFinderCorner) drawAlignmentPattern(positions[i], positions[j])
            }
        }

        drawFormatBits(0) // 占位，选定掩码后重写
        drawVersion()
    }

    /**
     * 9×9 定位图案（3×3 深色核心 + 一圈浅色 + 一圈深色 + 分隔符），中心在 (x, y)。
     *
     * 用切比雪夫距离判定环层：距离 0/1 深、2 浅、3 深、4 浅（最外圈即分隔符）。
     * 越界的部分直接跳过，这样三个角落可以用同一套代码。
     */
    private fun drawFinderPattern(x: Int, y: Int) {
        for (dy in -FINDER_RADIUS..FINDER_RADIUS) {
            for (dx in -FINDER_RADIUS..FINDER_RADIUS) {
                val xx = x + dx
                val yy = y + dy
                if (xx !in 0 until size || yy !in 0 until size) continue
                val ring = maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy))
                setFunctionModule(xx, yy, ring != FINDER_LIGHT_RING && ring != FINDER_SEPARATOR_RING)
            }
        }
    }

    /** 5×5 对齐图案，中心在 (x, y)：中心深、中间一圈浅、外圈深。 */
    private fun drawAlignmentPattern(x: Int, y: Int) {
        for (dy in -ALIGN_RADIUS..ALIGN_RADIUS) {
            for (dx in -ALIGN_RADIUS..ALIGN_RADIUS) {
                val ring = maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy))
                setFunctionModule(x + dx, y + dy, ring != ALIGN_LIGHT_RING)
            }
        }
    }

    /**
     * 两份格式信息（15 位 = 2 位纠错等级 + 3 位掩码 + 10 位 BCH(15,5)），再与 `0x5412` 异或。
     *
     * 位序容易搞错：**低位在前**写入，(8,0) 是 bit0、(8,5) 是 bit5、然后是 (8,7)、(8,8)、(7,8)……
     * 这正是上一版 B6 的缺陷②（读回 `decMask=1` 而编码 `encMask=3`）的根源。
     */
    private fun drawFormatBits(msk: Int) {
        val data = (ecc.formatBits shl FORMAT_MSK_BITS) or msk
        var rem = data
        repeat(FORMAT_EC_BITS) {
            rem = (rem shl 1) xor (((rem ushr (FORMAT_EC_BITS - 1)) and 1) * FORMAT_POLYNOMIAL)
        }
        val bits = ((data shl FORMAT_EC_BITS) or rem) xor FORMAT_MASK

        // 第一份：bit0..bit5 沿左上定位图案左侧竖边（自上而下），跳过第 6 行时序
        for (i in 0..FORMAT_FIRST_VERTICAL_END) setFunctionModule(FORMAT_ROW, i, bitAt(bits, i))
        // bit6 在 (8,7)、bit7 在 (8,8)、bit8 在 (7,8) —— 时序行/列的两个交叉缺口
        setFunctionModule(FORMAT_ROW, FORMAT_SKIP_INDEX, bitAt(bits, FORMAT_BIT_AT_SKIP_GAP))
        setFunctionModule(FORMAT_ROW, FORMAT_ROW, bitAt(bits, FORMAT_BIT_AT_SKIP_GAP + 1))
        setFunctionModule(FORMAT_SKIP_INDEX, FORMAT_ROW, bitAt(bits, FORMAT_BIT_AT_SKIP_GAP + 2))
        // bit9..bit14 沿第 8 行自右向左
        for (i in FORMAT_SECOND_HALF_START until FORMAT_TOTAL_BITS) {
            setFunctionModule(FORMAT_TOTAL_BITS - 1 - i, FORMAT_ROW, bitAt(bits, i))
        }

        // 第二份：bit0..bit7 右上横排自右向左，bit8..bit14 左下竖排自上而下
        for (i in 0..FORMAT_BIT_SHORT_END) setFunctionModule(size - 1 - i, FORMAT_ROW, bitAt(bits, i))
        for (i in FORMAT_BIT_SHORT_END + 1 until FORMAT_TOTAL_BITS) {
            setFunctionModule(FORMAT_ROW, size - FORMAT_TOTAL_BITS + i, bitAt(bits, i))
        }
        setFunctionModule(FORMAT_ROW, size - FORMAT_ROW, true)
    }

    /** 版本 7 起才有的两份版本信息（18 位 = 6 位版本号 + 12 位 BCH(18,6)）。 */
    private fun drawVersion() {
        if (version < VERSION_INFO_MIN) return
        var rem = version
        repeat(VERSION_EC_BITS) {
            rem = (rem shl 1) xor (((rem ushr (VERSION_EC_BITS - 1)) and 1) * VERSION_POLYNOMIAL)
        }
        val bits = (version shl VERSION_EC_BITS) or rem
        for (i in 0 until VERSION_TOTAL_BITS) {
            val dark = bitAt(bits, i)
            val a = size - VERSION_BLOCK_SPAN + i % VERSION_BLOCK_WIDTH
            val b = i / VERSION_BLOCK_WIDTH
            setFunctionModule(a, b, dark)
            setFunctionModule(b, a, dark)
        }
    }

    private fun setFunctionModule(x: Int, y: Int, dark: Boolean) {
        modules[y][x] = dark
        isFunction[y][x] = true
    }

    // ---------- 二、纠错与交织 ----------

    /**
     * 把数据码字按块切分、逐块算纠错码字，再**交织**（不是简单拼接）。
     *
     * 交织是规范的抗损设计：整块丢失时受损码字会分散到所有块，每块都还在纠错能力内。
     * 短块在这里补一个填充字节，交织时跳过它（`i == 数据区长度 && j < 短块数`）。
     */
    private fun addEccAndInterleave(data: ByteArray): ByteArray {
        val numBlocks = QrTables.numEccBlocks(ecc, version)
        val blockEccLen = QrTables.eccCodewordsPerBlock(ecc, version)
        val rawCodewords = QrTables.numRawDataModules(version) / BITS_PER_BYTE
        val numShortBlocks = numBlocks - rawCodewords % numBlocks
        val shortBlockLen = rawCodewords / numBlocks
        val dataLenOfShortBlock = shortBlockLen - blockEccLen

        val divisor = QrReedSolomon.computeDivisor(blockEccLen)
        val blocks = ArrayList<ByteArray>(numBlocks)
        var offset = 0
        for (i in 0 until numBlocks) {
            val len = dataLenOfShortBlock + if (i < numShortBlocks) 0 else 1
            val chunk = data.copyOfRange(offset, offset + len)
            offset += len
            val block = ByteArray(shortBlockLen + 1)
            chunk.copyInto(block, 0)
            QrReedSolomon.computeRemainder(chunk, divisor).copyInto(block, block.size - blockEccLen)
            blocks.add(block)
        }

        val result = ByteArray(rawCodewords)
        var k = 0
        for (i in 0..blocks[0].size - 1) {
            for (j in blocks.indices) {
                // 短块在数据区末尾多出来的那一位是填充，交织时跳过
                if (i == dataLenOfShortBlock && j < numShortBlocks) continue
                result[k++] = blocks[j][i]
            }
        }
        return result
    }

    // ---------- 三、铺码字与掩码 ----------

    /**
     * 按规范 §7.7.3 的「之字形」把码字铺进数据区：从右下开始两列一组，上下交替，跳过功能图案
     * 与第 6 列（时序图案）。余位（0..7 个）保持浅色。
     */
    private fun drawCodewords(data: ByteArray) {
        var bitIndex = 0
        var right = size - 1
        while (right >= 1) {
            if (right == TIMING_INDEX) right = TIMING_INDEX - 1
            bitIndex = drawColumnPair(data, right, bitIndex)
            right -= 2
        }
    }

    /** 铺一组两列（自下而上或自上而下交替），返回铺完之后的位游标。 */
    private fun drawColumnPair(data: ByteArray, right: Int, startBit: Int): Int {
        val totalBits = data.size * BITS_PER_BYTE
        val upward = ((right + 1) and 2) == 0
        var bitIndex = startBit
        for (vert in 0 until size) {
            val y = if (upward) size - 1 - vert else vert
            bitIndex = drawPairRow(data, right, y, bitIndex, totalBits)
        }
        return bitIndex
    }

    /** 铺一行里的左右两个模块（跳过功能图案与已填满的余位）。 */
    private fun drawPairRow(data: ByteArray, right: Int, y: Int, startBit: Int, totalBits: Int): Int {
        var bitIndex = startBit
        for (j in 0..1) {
            val x = right - j
            if (!isFunction[y][x] && bitIndex < totalBits) {
                modules[y][x] = nextDataBit(data, bitIndex)
                bitIndex++
            }
        }
        return bitIndex
    }

    /** 取第 [bitIndex] 位的码字比特（高位在前）；之字形铺放与掩码试算都走这一处。 */
    private fun nextDataBit(data: ByteArray, bitIndex: Int): Boolean =
        bitAt(data[bitIndex ushr BYTE_SHIFT].toInt(), BITS_PER_BYTE - 1 - (bitIndex and BYTE_MASK))

    /** 八条掩码公式（规范表 10）。XOR 两次即还原，所以选掩码时可以先试后撤。 */
    private fun applyMask(msk: Int) {
        for (y in 0 until size) {
            for (x in 0 until size) {
                if (!isFunction[y][x] && maskCondition(msk, x, y)) modules[y][x] = !modules[y][x]
            }
        }
    }

    private fun maskCondition(msk: Int, x: Int, y: Int): Boolean = when (msk) {
        MASK_0 -> (x + y) % 2 == 0
        MASK_1 -> y % 2 == 0
        MASK_2 -> x % 3 == 0
        MASK_3 -> (x + y) % 3 == 0
        MASK_4 -> (x / 3 + y / 2) % 2 == 0
        MASK_5 -> x * y % 2 + x * y % 3 == 0
        MASK_6 -> (x * y % 2 + x * y % 3) % 2 == 0
        MASK_7 -> ((x + y) % 2 + x * y % 3) % 2 == 0
        else -> throw IllegalArgumentException("掩码超出范围: $msk")
    }

    /** 依次试 8 个掩码，取罚分最低者（规范 §7.8.3）。试完立刻 XOR 还原。 */
    private fun chooseBestMask(): Int {
        var best = 0
        var bestPenalty = Int.MAX_VALUE
        for (candidate in 0 until MASK_COUNT) {
            applyMask(candidate)
            drawFormatBits(candidate)
            val penalty = penaltyScore()
            if (penalty < bestPenalty) {
                best = candidate
                bestPenalty = penalty
            }
            applyMask(candidate)
        }
        return best
    }

    // ---------- 四、罚分（四条规则） ----------

    private fun penaltyScore(): Int = penaltyRuns() + penaltyBlocks() + penaltyBalance()

    /** N1（连续 5 个同色 +3，之后每多 1 个 +1）与 N3（疑似定位图案 +40），行列各扫一遍。 */
    private fun penaltyRuns(): Int {
        var result = 0
        for (i in 0 until size) {
            result += scanLine(runsRow = true, index = i)
            result += scanLine(runsRow = false, index = i)
        }
        return result
    }

    private fun scanLine(runsRow: Boolean, index: Int): Int {
        var result = 0
        var runColor = false
        var runLength = 0
        val history = IntArray(RUN_HISTORY_SIZE)
        for (step in 0 until size) {
            val dark = if (runsRow) modules[index][step] else modules[step][index]
            if (dark == runColor) {
                runLength++
                if (runLength == PENALTY_N1_THRESHOLD) result += PENALTY_N1
                else if (runLength > PENALTY_N1_THRESHOLD) result++
            } else {
                addRunHistory(runLength, history)
                if (!runColor) result += countFinderLikePatterns(history) * PENALTY_N3
                runColor = dark
                runLength = 1
            }
        }
        if (runColor) {
            addRunHistory(runLength, history)
            runLength = 0
        }
        // 行末当成浅色边框
        addRunHistory(runLength + size, history)
        result += countFinderLikePatterns(history) * PENALTY_N3
        return result
    }

    /** 历史只看最近 7 段（1:1:3:1:1 核心 + 两侧各留 1 段用于判断是否贴边）。 */
    private fun addRunHistory(runLength: Int, history: IntArray) {
        // 首个 run 视作左边界外全是浅色，额外加上边长
        val value = if (history[0] == 0) runLength + size else runLength
        for (i in history.size - 1 downTo 1) history[i] = history[i - 1]
        history[0] = value
    }

    /**
     * N3：判定 `1:1:3:1:1`（即 `1011101`）两侧是否贴边。
     *
     * 上一版 B6 的缺陷③是「按 11 模块完整模式匹配」，那样既漏报了贴边的真实图案，
     * 又把中间那段可变宽度的合法图案（规范允许任意倍数宽度）判错。
     */
    private fun countFinderLikePatterns(history: IntArray): Int {
        val n = history[1]
        if (n <= 0) return 0
        val core = history[2] == n &&
            history[3] == n * N3_DARK_CORE_LENGTH &&
            history[4] == n &&
            history[5] == n
        if (!core) return 0
        return countQuietSides(history, n)
    }

    /** `1011101` 两侧的静区只要够宽就算一次：前缀 ≥4n 或后缀 ≥4n，两个方向分别计分。 */
    private fun countQuietSides(history: IntArray, n: Int): Int {
        val head = history[RUN_HISTORY_HEAD]
        val tail = history[RUN_HISTORY_TAIL]
        val quietAtStart = head >= n * N3_QUIET_RUNS
        val quietAtEnd = tail >= n * N3_QUIET_RUNS
        var count = 0
        if (quietAtStart && tail >= n) count++
        if (quietAtEnd && head >= n) count++
        return count
    }

    /** N2：每出现一个 2×2 同色块 +3。 */
    private fun penaltyBlocks(): Int {
        var result = 0
        for (y in 0 until size - 1) {
            for (x in 0 until size - 1) {
                val color = modules[y][x]
                if (color == modules[y][x + 1] && color == modules[y + 1][x] && color == modules[y + 1][x + 1]) {
                    result += PENALTY_N2
                }
            }
        }
        return result
    }

    /**
     * N4：深浅比例每偏离 50% 一个 5% 档 +10。
     *
     * `k = floor(|深色占比 - 50%| / 5%)`，等价于 `(|dark*20 - total*10| + total - 1) / total - 1`。
     * 上一版 B6 的缺陷④用了「相邻 5% 档取小」，即少了公式末尾的 `-1`，整体差一档。
     */
    private fun penaltyBalance(): Int {
        var dark = 0
        for (row in modules) for (cell in row) if (cell) dark++
        val total = size * size
        val k = (kotlin.math.abs(dark * PERCENT_SCALE - total * HALF_PERCENT_SCALE) + total - 1) / total - 1
        return k * PENALTY_N4
    }

    companion object {

        const val MIN_VERSION = 1
        const val MAX_VERSION = 40

        /** 传这个值表示「自动挑最优掩码」。 */
        const val MASK_AUTO = -1

        private const val SIZE_PER_VERSION = 4
        private const val SIZE_OFFSET = 17

        private const val BITS_PER_BYTE = 8

        /** 位运算里「一字节 = 右移 3 位」「字节内掩码 = 低 3 位」。 */
        private const val BYTE_SHIFT = 3
        private const val BYTE_MASK = 7
        private const val MASK_COUNT = 8
        private const val MASK_0 = 0
        private const val MASK_1 = 1
        private const val MASK_2 = 2
        private const val MASK_3 = 3
        private const val MASK_4 = 4
        private const val MASK_5 = 5
        private const val MASK_6 = 6
        private const val MASK_7 = 7

        private const val TIMING_INDEX = 6
        private const val FINDER_CENTER = 3
        private const val FINDER_RADIUS = 4
        private const val FINDER_LIGHT_RING = 2
        private const val FINDER_SEPARATOR_RING = 4

        private const val ALIGN_RADIUS = 2
        private const val ALIGN_LIGHT_RING = 1

        private const val FORMAT_MSK_BITS = 3
        private const val FORMAT_EC_BITS = 10
        private const val FORMAT_TOTAL_BITS = 15
        private const val FORMAT_POLYNOMIAL = 0x537
        private const val FORMAT_MASK = 0x5412
        private const val FORMAT_FIRST_VERTICAL_END = 5
        private const val FORMAT_SKIP_INDEX = 7
        private const val FORMAT_ROW = 8
        private const val FORMAT_SECOND_HALF_START = 9

        /** 时序缺口处的三位（bit6/bit7/bit8）：(8,7)、(8,8)、(7,8)。 */
        private const val FORMAT_BIT_AT_SKIP_GAP = 6

        /** 第二份格式信息的横排是 bit0..bit7，共 8 位。 */
        private const val FORMAT_BIT_SHORT_END = 7

        private const val VERSION_INFO_MIN = 7
        private const val VERSION_EC_BITS = 12
        private const val VERSION_TOTAL_BITS = 18
        private const val VERSION_BLOCK_WIDTH = 3
        private const val VERSION_BLOCK_SPAN = 11
        private const val VERSION_POLYNOMIAL = 0x1F25

        private const val PENALTY_N1 = 3
        private const val PENALTY_N1_THRESHOLD = 5
        private const val PENALTY_N2 = 3
        private const val PENALTY_N3 = 40
        private const val PENALTY_N4 = 10
        private const val RUN_HISTORY_SIZE = 7

        /** N3 的 `1:1:3:1:1` 核心：中间那条深色 run 是两侧窄 run 的 3 倍宽。 */
        private const val N3_DARK_CORE_LENGTH = 3

        /** 静区判定：`1011101` 一侧的浅色 run 至少要有 4 倍窄 run 宽才算静区。 */
        private const val N3_QUIET_RUNS = 4

        /** run 栈里「最旧」与「最新」两端的下标（栈长 7：前导静区 + 1011101 + 后缀静区）。 */
        private const val RUN_HISTORY_HEAD = 0
        private const val RUN_HISTORY_TAIL = RUN_HISTORY_SIZE - 1

        private const val PERCENT_SCALE = 20
        private const val HALF_PERCENT_SCALE = 10

        /** 终止符最多 4 位（规范 §7.4.9）。 */
        private const val TERMINATOR_MAX_BITS = 4

        private const val PAD_BYTE_A = 0xEC
        private const val PAD_BYTE_B = 0x11

        /**
         * 把一段文字编成二维码，自动选版本与掩码。
         *
         * @param boostEcl 容量有余时是否自动提升纠错等级。
         *   生产代码传 `true`（扫得动比画得小更重要）；与黄金向量比对时传 `false`，
         *   因为向量是用 `boostEcl=false` 生成的，否则版本不同会逐格对不上。
         * @throws IllegalArgumentException 内容超出 40 版容量
         */
        fun encodeText(text: String, ecc: QrEcc, boostEcl: Boolean = true): QrCode =
            encodeSegments(QrSegment.of(text), ecc, MASK_AUTO, boostEcl)

        /**
         * 低层入口：给定分段、纠错等级与掩码，编出二维码。
         *
         * @param mask 0..7 固定掩码，或 [MASK_AUTO] 自动选择
         */
        fun encodeSegments(
            segments: List<QrSegment>,
            ecc: QrEcc,
            mask: Int = MASK_AUTO,
            boostEcl: Boolean = true,
        ): QrCode {
            require(mask == MASK_AUTO || mask in 0 until MASK_COUNT) { "掩码超出范围: $mask" }
            var version = MIN_VERSION
            var usedBits = 0
            while (true) {
                val capacityBits = QrTables.numDataCodewords(version, ecc) * BITS_PER_BYTE
                usedBits = QrSegment.totalBits(segments, version)
                if (usedBits <= capacityBits) break
                require(version < MAX_VERSION) {
                    "内容过长：需要 $usedBits 位，版本 $MAX_VERSION 也只有 $capacityBits 位"
                }
                version++
            }

            var finalEcc = ecc
            if (boostEcl) {
                for (candidate in QrEcc.entries) {
                    if (usedBits <= QrTables.numDataCodewords(version, candidate) * BITS_PER_BYTE) {
                        finalEcc = candidate
                    }
                }
            }

            return QrCode(version, finalEcc, mask, buildDataCodewords(segments, version, finalEcc))
        }

        /** 组装数据码字：各段位流 + 终止符 + 补齐到字节 + 交替填充字节。 */
        private fun buildDataCodewords(segments: List<QrSegment>, version: Int, ecc: QrEcc): ByteArray {
            val capacityBits = QrTables.numDataCodewords(version, ecc) * BITS_PER_BYTE
            val buffer = QrBitBuffer()
            for (segment in segments) segment.appendTo(buffer, version)
            require(buffer.bitLength <= capacityBits) { "数据位超出版本容量" }

            buffer.appendBits(0, minOf(TERMINATOR_MAX_BITS, capacityBits - buffer.bitLength))
            buffer.appendBits(0, (BITS_PER_BYTE - buffer.bitLength % BITS_PER_BYTE) % BITS_PER_BYTE)

            var padByte = PAD_BYTE_A
            while (buffer.bitLength < capacityBits) {
                buffer.appendBits(padByte, BITS_PER_BYTE)
                padByte = padByte xor (PAD_BYTE_A xor PAD_BYTE_B)
            }
            return buffer.toByteArray()
        }

        private fun bitAt(value: Int, i: Int): Boolean = ((value ushr i) and 1) != 0
    }
}
