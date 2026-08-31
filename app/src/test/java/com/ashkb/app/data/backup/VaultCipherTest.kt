package com.ashkb.app.data.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P5 备份加密层单测：加解密往返、口令错误、密文篡改、AAD 绑定、头格式校验。
 * 这些是 R20「数据可带走可恢复」的安全底线——任何一条失败都不应定版。
 */
class VaultCipherTest {

    private val payload = """{"format":"ashkb-full","schema_version":5,"tables":{},"manifest":{}}"""
    private val pass = "correct-horse-battery".toCharArray()

    private fun enc(pw: CharArray = pass): ByteArray =
        VaultCipher.encrypt(pw, payload, BackupEngine.SCHEMA_VERSION, "2026-08-31T10:00:00")

    @Test
    fun `加解密往返一致`() {
        val file = enc()
        val d = VaultCipher.decrypt(pass, file)
        assertEquals(payload, d.payload)
        assertEquals(BackupEngine.SCHEMA_VERSION, d.schemaVersion)
        assertEquals("2026-08-31T10:00:00", d.createdAt)
    }

    @Test
    fun `同口令重复加密输出不同密文（随机盐与 IV）`() {
        val a = enc()
        val b = enc()
        assertNotEquals(a.toList(), b.toList())
        // 但两者都能解回同一明文
        assertEquals(payload, VaultCipher.decrypt(pass, a).payload)
        assertEquals(payload, VaultCipher.decrypt(pass, b).payload)
    }

    @Test
    fun `错误口令解密失败`() {
        val file = enc()
        try {
            VaultCipher.decrypt("wrong-password".toCharArray(), file)
            throw AssertionError("错误口令不应解密成功")
        } catch (e: VaultCipher.VaultException) {
            assertTrue(e.message!!.contains("口令错误"))
        }
    }

    @Test
    fun `密文被篡改解密失败（GCM 认证标签）`() {
        val file = enc()
        file[file.size - 1] = (file[file.size - 1].toInt() xor 0x01).toByte()
        try {
            VaultCipher.decrypt(pass, file)
            throw AssertionError("篡改密文不应通过认证")
        } catch (_: VaultCipher.VaultException) { }
    }

    @Test
    fun `明文头被篡改解密失败（AAD 绑定防跨协议改头）`() {
        val file = enc()
        // 头长固定为第一个文件的头长——改头内一个字符（schema_version 数字位）
        val headerLen = ((file[8].toInt() and 0xFF) shl 24) or ((file[9].toInt() and 0xFF) shl 16) or
            ((file[10].toInt() and 0xFF) shl 8) or (file[11].toInt() and 0xFF)
        val off = 12 + headerLen - 3 // 头 JSON 尾部附近
        file[off] = (file[off].toInt() xor 0x01).toByte()
        try {
            VaultCipher.decrypt(pass, file)
            throw AssertionError("篡改明文头不应通过 AAD 校验")
        } catch (_: VaultCipher.VaultException) {
            // 头解析失败或 AAD 校验失败均视为正确拒绝
        }
    }

    @Test
    fun `过短文件被拒绝`() {
        try {
            VaultCipher.decrypt(pass, ByteArray(8))
            throw AssertionError("短文件不应进入解密")
        } catch (e: VaultCipher.VaultException) {
            assertTrue(e.message!!.contains("过短"))
        }
    }

    @Test
    fun `文件头 magic 不符被拒绝`() {
        val file = enc()
        file[0] = 'X'.code.toByte()
        try {
            VaultCipher.decrypt(pass, file)
            throw AssertionError("错误 magic 不应解密")
        } catch (e: VaultCipher.VaultException) {
            assertTrue(e.message!!.contains("标识不符"))
        }
    }

    @Test
    fun `非 ASCII 明文（中文档案）往返无损`() {
        val cnPayload = """{"profile":{"display_name":"张三","diagnosis":"强直性脊柱炎","allergies":"青霉素"}}"""
        val file = VaultCipher.encrypt(pass, cnPayload, 5, "2026-08-31T12:00:00")
        assertEquals(cnPayload, VaultCipher.decrypt(pass, file).payload)
    }

    @Test
    fun `密钥派生确定性（同口令同盐）`() {
        val salt = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16)
        val k1 = VaultCipher.deriveKey(pass, salt, 1000)
        val k2 = VaultCipher.deriveKey(pass, salt, 1000)
        assertArrayEquals(k1.encoded, k2.encoded)
        // 不同口令不同键
        val k3 = VaultCipher.deriveKey("another".toCharArray(), salt, 1000)
        assertFalse(k1.encoded.contentEquals(k3.encoded))
    }
}
