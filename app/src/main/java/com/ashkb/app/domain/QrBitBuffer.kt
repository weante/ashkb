package com.ashkb.app.domain

/**
 * 二维码编码用的比特缓冲（大端写入、按位索引）。
 *
 * 规范把每一段数据都描述成「若干个定长字段依次拼接」，先在比特层面拼好、最后才按 8 位切字节，
 * 所以需要一个能按位追加的容器。写成可变长数组而不是 `java.util.BitSet`，是为了让
 * 「第 i 位是 0 还是 1」这件事在测试里可以直接断言（`operator fun get`）。
 *
 * 纯 Kotlin、无 Android 依赖，可单测。
 */
internal class QrBitBuffer {

    private var bits = BooleanArray(INITIAL_CAPACITY)
    private var length = 0

    val bitLength: Int get() = length

    /**
     * 追加 [count] 个比特，取 [value] 的低 [count] 位（高位在前）。
     *
     * @throws IllegalArgumentException [count] 超出 `0..16`（二维码任一字段都不超过 16 位）
     */
    fun appendBits(value: Int, count: Int) {
        require(count in 0..MAX_APPEND_BITS) { "比特数超出范围: $count" }
        ensure(length + count)
        for (i in count - 1 downTo 0) {
            bits[length] = ((value ushr i) and 1) != 0
            length++
        }
    }

    /** 把另一个缓冲的比特原样接到尾部（用于「段的数据位」拼进总位流）。 */
    fun appendBuffer(other: QrBitBuffer) {
        ensure(length + other.length)
        for (i in 0 until other.length) {
            bits[length] = other.bits[i]
            length++
        }
    }

    /** 按字节追加（字节模式下 `numChars` 记的是字节数，位流也是逐字节）。 */
    fun appendBytes(data: ByteArray, start: Int = 0, endExclusive: Int = data.size) {
        require(start in 0..data.size) { "起点越界: $start" }
        require(endExclusive in start..data.size) { "终点越界: $endExclusive" }
        ensure(length + (endExclusive - start) * BITS_PER_BYTE)
        for (i in start until endExclusive) {
            appendBits(data[i].toInt() and BYTE_MASK, BITS_PER_BYTE)
        }
    }

    operator fun get(index: Int): Boolean = bits[index]

    /**
     * 按 8 位切字节（大端）。
     *
     * @throws IllegalStateException 比特数不是 8 的整数倍（说明填充步骤漏了）
     */
    fun toByteArray(): ByteArray {
        check(length % BITS_PER_BYTE == 0) { "比特数 $length 不是字节的整数倍" }
        val out = ByteArray(length / BITS_PER_BYTE)
        for (i in 0 until length) {
            if (bits[i]) {
                val index = i / BITS_PER_BYTE
                val shift = BITS_PER_BYTE - 1 - i % BITS_PER_BYTE
                out[index] = (out[index].toInt() or (1 shl shift)).toByte()
            }
        }
        return out
    }

    private fun ensure(capacity: Int) {
        if (capacity <= bits.size) return
        var next = bits.size * 2
        while (next < capacity) next *= 2
        bits = bits.copyOf(next)
    }

    private companion object {
        const val INITIAL_CAPACITY = 128
        const val MAX_APPEND_BITS = 16
        const val BITS_PER_BYTE = 8
        const val BYTE_MASK = 0xFF
    }
}
