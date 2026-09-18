package com.ashkb.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.0.27 备份恢复码（规划缺口 A2）纯逻辑单测：
 * 码形（160-bit → 32 字符 Base32 → 8 组 × 4）、归一化（任意抄写形态）、
 * 合法性校验（字母表 / 长度）、Base32 编码已知向量。
 */
class RecoveryCodeTest {

    // ======================= 码形与生成 =======================

    @Test
    fun `生成形态为 8 组 4 字符连字符分组`() {
        val code = RecoveryCode.generate()
        val groups = code.split("-")
        assertEquals(8, groups.size)
        assertTrue(groups.all { it.length == 4 })
        assertEquals(RecoveryCode.RAW_LENGTH, code.replace("-", "").length)
    }

    @Test
    fun `两次生成互不相同（SecureRandom）`() {
        assertNotEquals(RecoveryCode.generate(), RecoveryCode.generate())
    }

    @Test
    fun `生成结果自身通过合法性校验`() {
        assertTrue(RecoveryCode.isValid(RecoveryCode.generate()))
    }

    // ======================= 归一化（任意抄写形态） =======================

    @Test
    fun `小写与连字符与空白被归一化消除`() {
        assertEquals("A2VB7KQZ4MXT", RecoveryCode.normalize("a2vb-7kqz-4mxt"))
        assertEquals("A2VB7KQZ", RecoveryCode.normalize(" a2vb   7KQZ "))
        assertEquals("A2VB", RecoveryCode.normalize("a2vB"))
    }

    @Test
    fun `合法性校验接受任意抄写形态`() {
        val code = RecoveryCode.generate()
        assertTrue(RecoveryCode.isValid(code))                    // 分组
        assertTrue(RecoveryCode.isValid(code.lowercase()))        // 小写分组
        assertTrue(RecoveryCode.isValid(code.replace("-", "")))   // 无连字符
        assertTrue(RecoveryCode.isValid(code.lowercase().replace("-", " "))) // 小写空格
    }

    @Test
    fun `长度不是 32 字符的输入不合法`() {
        assertFalse(RecoveryCode.isValid("A".repeat(31)))
        assertFalse(RecoveryCode.isValid("A".repeat(33)))
        assertFalse(RecoveryCode.isValid(""))
    }

    @Test
    fun `Base32 字母表外字符（0 1 8 9）不合法`() {
        // RFC 4648 Base32 只有 A-Z 与 2-7
        assertFalse(RecoveryCode.isValid("0".repeat(32)))
        assertFalse(RecoveryCode.isValid("1".repeat(32)))
        assertFalse(RecoveryCode.isValid("8".repeat(32)))
        assertFalse(RecoveryCode.isValid("9".repeat(32)))
        assertTrue(RecoveryCode.isValid("2".repeat(32))) // 边界：2 与 7 在字母表内
        assertTrue(RecoveryCode.isValid("7".repeat(32)))
    }

    // ======================= Base32 编码已知向量 =======================

    @Test
    fun `全零字节编码为全 A`() {
        val zeros = ByteArray(20)
        assertEquals("A".repeat(32), RecoveryCode.encodeBase32(zeros))
    }

    @Test
    fun `全 0xFF 字节编码为全 7`() {
        val ones = ByteArray(20) { 0xFF.toByte() }
        assertEquals("7".repeat(32), RecoveryCode.encodeBase32(ones))
    }

    @Test
    fun `分组展示与常量一致`() {
        assertEquals("AAAA-BBBB", RecoveryCode.formatGrouped("AAAABBBB"))
        assertEquals("AAAA-BBBB-CCCC", RecoveryCode.formatGrouped("AAAABBBBCCCC"))
        assertEquals(4, RecoveryCode.GROUP)
        assertEquals(32, RecoveryCode.RAW_LENGTH)
    }
}
