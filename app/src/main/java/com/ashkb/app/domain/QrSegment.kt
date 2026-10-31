package com.ashkb.app.domain

/**
 * 二维码编码用到的「分段」——把一段文字切成规范定义的段，并算出每段占多少位。
 *
 * ### 为什么要有分段
 * 二维码的容量不是按字符数算的，而是按**比特数**算的，且不同模式每个字符的比特代价差很多：
 * 同一串 `0123456789` 用数字模式只要 34 位，用普通字节模式要 80 位。分段做对了，
 * 同样的版本能放更多内容（或者反过来说，屏幕上的码更小、更容易扫）。
 *
 * ### 本实现的取舍
 * 只实现数字 / 字母数字 / 字节三种模式（规范里的第 4 种是汉字节模式 = Shift-JIS 双字节，
 * 中文走 UTF-8 字节模式即可，引入 Shift-JIS 表得不偿失）。**不做**「一段文字拆成多段混合模式」的
 * 最优搜索——急救卡这种短内容里，混排优化省下的位还不足以跨过一个版本等级，却要多一大块搜索代码。
 *
 * 纯 Kotlin、无 Android 依赖，可单测。
 */
internal sealed interface QrSegment {

    /** 本段的模式指示符（规范表 4）。 */
    val modeBits: Int

    /** 本段的数据「字符数」——数字/字母数字模式是字符数，字节模式是**字节数**。 */
    val characterCount: Int

    /** 段在数据位流里占的比特数（含模式指示符与字符计数指示符）。 */
    fun bitLength(version: Int): Int = headerBits(version) + dataBitLength()

    /** 把本段的模式指示符、字符计数、数据位依次追加到 [buffer]。 */
    fun appendTo(buffer: QrBitBuffer, version: Int) {
        buffer.appendBits(modeBits, MODE_INDICATOR_BITS)
        buffer.appendBits(characterCount, charCountBits(version, modeBits))
        appendData(buffer)
    }

    /** 只追加数据位（不含头部）。 */
    fun appendData(buffer: QrBitBuffer)

    /** 只算数据位的长度（不含头部）。 */
    fun dataBitLength(): Int

    /** 本段头部的位数（模式指示符 4 位 + 字符计数指示符）。 */
    fun headerBits(version: Int): Int = MODE_INDICATOR_BITS + charCountBits(version, modeBits)

    /** 数字模式：只用 `0-9`，每 3 位压成 10 位。 */
    data class Numeric(val digits: String) : QrSegment {

        init {
            require(digits.isNotEmpty()) { "数字段不能为空" }
            require(digits.all { it in '0'..'9' }) { "数字段只能含 0-9：$digits" }
        }

        override val modeBits: Int get() = MODE_NUMERIC
        override val characterCount: Int get() = digits.length

        override fun dataBitLength(): Int {
            val full = digits.length / NUMERIC_GROUP_LENGTH
            return full * NUMERIC_GROUP_BITS_3 + when (digits.length % NUMERIC_GROUP_LENGTH) {
                2 -> NUMERIC_GROUP_BITS_2
                1 -> NUMERIC_GROUP_BITS_1
                else -> 0
            }
        }

        override fun appendData(buffer: QrBitBuffer) {
            var i = 0
            while (digits.length - i >= NUMERIC_GROUP_LENGTH) {
                buffer.appendBits(digits.substring(i, i + NUMERIC_GROUP_LENGTH).toInt(), NUMERIC_GROUP_BITS_3)
                i += NUMERIC_GROUP_LENGTH
            }
            when (digits.length - i) {
                2 -> buffer.appendBits(digits.substring(i).toInt(), NUMERIC_GROUP_BITS_2)
                1 -> buffer.appendBits(digits.substring(i).toInt(), NUMERIC_GROUP_BITS_1)
            }
        }
    }

    /** 字母数字模式：`0-9 A-Z 空格 $ % * + - . / :`，两字符合并成 11 位。 */
    data class Alphanumeric(val text: String) : QrSegment {

        init {
            require(text.isNotEmpty()) { "字母数字段不能为空" }
            require(text.all { ALPHANUMERIC_CHARSET.indexOf(it) >= 0 }) {
                "字母数字段含不可编码字符：$text"
            }
        }

        override val modeBits: Int get() = MODE_ALPHANUMERIC
        override val characterCount: Int get() = text.length

        override fun dataBitLength(): Int {
            val pairs = text.length / ALPHANUMERIC_GROUP_LENGTH
            val rest = if (text.length % ALPHANUMERIC_GROUP_LENGTH == 1) ALPHANUMERIC_SINGLE_BITS else 0
            return pairs * ALPHANUMERIC_PAIR_BITS + rest
        }

        override fun appendData(buffer: QrBitBuffer) {
            var i = 0
            while (text.length - i >= ALPHANUMERIC_GROUP_LENGTH) {
                val pair = ALPHANUMERIC_CHARSET.indexOf(text[i]) * ALPHANUMERIC_CHARSET_SIZE +
                    ALPHANUMERIC_CHARSET.indexOf(text[i + 1])
                buffer.appendBits(pair, ALPHANUMERIC_PAIR_BITS)
                i += ALPHANUMERIC_GROUP_LENGTH
            }
            if (text.length - i == 1) {
                buffer.appendBits(ALPHANUMERIC_CHARSET.indexOf(text[i]), ALPHANUMERIC_SINGLE_BITS)
            }
        }
    }

    /** 字节模式：任意 UTF-8 字节串（中文走这里）。 */
    data class Bytes(val data: ByteArray) : QrSegment {

        init {
            require(data.isNotEmpty()) { "字节段不能为空" }
        }

        override val modeBits: Int get() = MODE_BYTE
        override val characterCount: Int get() = data.size

        override fun dataBitLength(): Int = data.size * BITS_PER_BYTE

        override fun appendData(buffer: QrBitBuffer) = buffer.appendBytes(data)

        override fun equals(other: Any?): Boolean =
            this === other || (other is Bytes && data.contentEquals(other.data))

        override fun hashCode(): Int = data.contentHashCode()
    }

    companion object {

        /** 模式指示符宽度（所有模式都是 4 位）。 */
        const val MODE_INDICATOR_BITS = 4

        /** 模式指示符取值（规范表 4）。 */
        const val MODE_NUMERIC = 0x1
        const val MODE_ALPHANUMERIC = 0x2
        const val MODE_BYTE = 0x4

        /** 规范里的字母数字字符表，下标即该字符的编码值。 */
        const val ALPHANUMERIC_CHARSET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ $%*+-./:"

        private const val NUMERIC_GROUP_LENGTH = 3
        private const val NUMERIC_GROUP_BITS_3 = 10
        private const val NUMERIC_GROUP_BITS_2 = 7
        private const val NUMERIC_GROUP_BITS_1 = 4

        private const val ALPHANUMERIC_GROUP_LENGTH = 2
        private const val ALPHANUMERIC_PAIR_BITS = 11
        private const val ALPHANUMERIC_SINGLE_BITS = 6
        private const val ALPHANUMERIC_CHARSET_SIZE = 45

        private const val BITS_PER_BYTE = 8

        /** 字符计数指示符的三个版本档位分界（规范表 3）：版本 1–9 / 10–26 / 27–40。 */
        private const val VERSION_TIER_SMALL_END = 9
        private const val VERSION_TIER_MEDIUM_END = 26

        /** 数字模式的字符计数位数（小 / 中 / 大三档）。 */
        private const val NUMERIC_BITS_SMALL = 10
        private const val NUMERIC_BITS_MEDIUM = 12
        private const val NUMERIC_BITS_LARGE = 14

        /** 字母数字模式的字符计数位数（小 / 中 / 大三档）。 */
        private const val ALPHANUMERIC_BITS_SMALL = 9
        private const val ALPHANUMERIC_BITS_MEDIUM = 11
        private const val ALPHANUMERIC_BITS_LARGE = 13

        /** 字节模式只有两档：版本 1–9 用 8 位，10 以上统一 16 位。 */
        private const val BYTE_BITS_SMALL = 8
        private const val BYTE_BITS_MEDIUM = 16

        /**
         * 字符计数指示符的位数随版本变化（小版本省位，大版本才够放）。
         * 分档：版本 1–9 / 10–26 / 27–40（规范表 3）。
         */
        fun charCountBits(version: Int, mode: Int): Int = when (mode) {
            MODE_NUMERIC -> versionedBits(version, NUMERIC_BITS_SMALL, NUMERIC_BITS_MEDIUM, NUMERIC_BITS_LARGE)
            MODE_ALPHANUMERIC ->
                versionedBits(version, ALPHANUMERIC_BITS_SMALL, ALPHANUMERIC_BITS_MEDIUM, ALPHANUMERIC_BITS_LARGE)
            MODE_BYTE -> versionedBits(version, BYTE_BITS_SMALL, BYTE_BITS_MEDIUM, BYTE_BITS_MEDIUM)
            else -> throw IllegalArgumentException("未知模式: $mode")
        }

        /** 三个版本档位里取对应那一位：≤9 用 small、≤26 用 medium、其余用 large。 */
        private fun versionedBits(version: Int, small: Int, medium: Int, large: Int): Int = when {
            version <= VERSION_TIER_SMALL_END -> small
            version <= VERSION_TIER_MEDIUM_END -> medium
            else -> large
        }

        /**
         * 把整段文字切成**单个**段：纯数字走数字模式、全在字母数字表里走字母数字模式，
         * 其余一律 UTF-8 字节模式。
         */
        fun of(text: String): List<QrSegment> {
            if (text.isEmpty()) return emptyList()
            return when {
                text.all { it in '0'..'9' } -> listOf(Numeric(text))
                text.all { ALPHANUMERIC_CHARSET.indexOf(it) >= 0 } -> listOf(Alphanumeric(text))
                else -> listOf(Bytes(text.toByteArray(Charsets.UTF_8)))
            }
        }

        /** 全部段在指定版本下的总比特数。 */
        fun totalBits(segments: List<QrSegment>, version: Int): Int =
            segments.sumOf { it.bitLength(version) }
    }
}
