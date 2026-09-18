package com.ashkb.app.domain

import java.security.SecureRandom

/**
 * 备份恢复码（v1.0.27，规划缺口 A2）。
 *
 * 背景：备份加密口令遗忘 = 数据永久不可恢复（v1 格式口令直接派生密钥，别无退路）。
 * 恢复码是独立于口令的第二把钥匙：v2 备份文件以信封加密（数据密钥 DEK 分别被
 * 口令与恢复码包装进两个密钥槽），任一可解——类似 LUKS 的 keyslot。
 *
 * 码形：160-bit（20 字节）SecureRandom → RFC 4648 Base32（无 padding）恰 32 字符，
 * 按 4 字符分组展示：XXXX-XXXX-…（8 组）。熵 160-bit 对离线暴力破解免疫，
 * 且不依赖用户选择强度（与口令不同）。
 *
 * 纯函数、无 Android 依赖，可单测。
 */
object RecoveryCode {

    /** RFC 4648 Base32 字母表（无 padding）。 */
    const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    /** 归一化后的码长（160-bit / 5）。 */
    const val RAW_LENGTH = 32

    /** 展示分组大小。 */
    const val GROUP = 4

    /** 生成并返回分组展示形态（XXXX-XXXX-…，8 组）。 */
    fun generate(random: SecureRandom = SecureRandom()): String {
        val bytes = ByteArray(RAW_LENGTH / 8 * 5).also { random.nextBytes(it) }
        return formatGrouped(encodeBase32(bytes))
    }

    /** 20 字节 → 32 字符 Base32（无 padding）：高位在前，每 5 bit 一字符。 */
    fun encodeBase32(bytes: ByteArray): String {
        require(bytes.size == RAW_LENGTH / 8 * 5) { "恢复码源须为 ${RAW_LENGTH / 8 * 5} 字节" }
        val sb = StringBuilder(RAW_LENGTH)
        var buffer = 0
        var bits = 0
        for (b in bytes) {
            buffer = (buffer shl 8) or (b.toInt() and 0xFF)
            bits += 8
            while (bits >= 5) {
                bits -= 5
                sb.append(ALPHABET[(buffer ushr bits) and 0x1F])
            }
        }
        // 160 bits 整除 5，此处 bits 必为 0
        return sb.toString()
    }

    /** 去连字符与空白并大写——用户无论怎么抄写（小写、漏连字符、多空格）都能解。 */
    fun normalize(input: String): String =
        input.filterNot { it == '-' || it.isWhitespace() }.uppercase()

    /** 归一化后恰 32 字符且全部在 Base32 字母表内。 */
    fun isValid(input: String): Boolean {
        val n = normalize(input)
        return n.length == RAW_LENGTH && n.all { it in ALPHABET }
    }

    /** 32 字符原始码 → 8 组 × 4 字符展示形态。 */
    fun formatGrouped(raw: String): String =
        raw.chunked(GROUP).joinToString("-")
}
