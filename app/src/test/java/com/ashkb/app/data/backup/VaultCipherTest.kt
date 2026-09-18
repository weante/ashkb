package com.ashkb.app.data.backup

import com.ashkb.app.domain.RecoveryCode
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

/**
 * P5 备份加密层单测：加解密往返、口令错误、密文篡改、AAD 绑定、头格式校验。
 * v1.0.27 起覆盖信封加密双密钥槽（口令 / 恢复码）与 v1 历史格式永久兼容。
 * 这些是 R20「数据可带走可恢复」的安全底线——任何一条失败都不应定版。
 */
class VaultCipherTest {

    private val payload = """{"format":"ashkb-full","schema_version":5,"tables":{},"manifest":{}}"""
    private val pass = "correct-horse-battery".toCharArray()
    // R7 后 schemaVersion 从 DB 派生（SupportSQLiteDatabase，纯 JVM 不可构造），此处为任意往返值
    private val schema = 7

    private fun enc(pw: CharArray = pass, recovery: CharArray? = null): ByteArray =
        VaultCipher.encrypt(pw, recovery, payload, schema, "2026-08-31T10:00:00")

    // ======================= v2 基本往返（单口令槽） =======================

    @Test
    fun `加解密往返一致`() {
        val file = enc()
        val d = VaultCipher.decrypt(pass, file)
        assertEquals(payload, d.payload)
        assertEquals(schema, d.schemaVersion)
        assertEquals("2026-08-31T10:00:00", d.createdAt)
    }

    @Test
    fun `同口令重复加密输出不同密文（随机 DEK、盐与 IV）`() {
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
            assertTrue(e.message!!.contains("口令"))
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
            // 头解析失败、密钥槽损坏或 AAD 校验失败均视为正确拒绝
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
        val file = VaultCipher.encrypt(pass, null, cnPayload, 5, "2026-08-31T12:00:00")
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

    // ======================= v2 双密钥槽（A2 恢复码） =======================

    @Test
    fun `双槽：口令可解`() {
        val file = enc(recovery = RecoveryCode.generate().toCharArray())
        assertEquals(payload, VaultCipher.decrypt(pass, file).payload)
    }

    @Test
    fun `双槽：恢复码可解（分组形态原样输入）`() {
        val rc = RecoveryCode.generate()
        val file = enc(recovery = rc.toCharArray())
        assertEquals(payload, VaultCipher.decrypt(rc.toCharArray(), file).payload)
    }

    @Test
    fun `双槽：恢复码任意抄写形态可解（小写、无连字符、多空格）`() {
        val rc = RecoveryCode.generate()
        // 加密槽用归一化形态（与 BackupRepository.recoverySlot 一致）
        val file = enc(recovery = RecoveryCode.normalize(rc).toCharArray())
        val sloppy = rc.lowercase().replace("-", "  ")
        assertEquals(payload, VaultCipher.decrypt(sloppy.toCharArray(), file).payload)
    }

    @Test
    fun `双槽：口令与恢复码均错误才报错`() {
        val file = enc(recovery = RecoveryCode.generate().toCharArray())
        try {
            VaultCipher.decrypt("wrong-password".toCharArray(), file)
            throw AssertionError("错误口令不应解密成功")
        } catch (e: VaultCipher.VaultException) {
            assertTrue(e.message!!.contains("口令或恢复码"))
        }
    }

    @Test
    fun `单槽：形似恢复码的错误输入不可解（归一化兜底不误放行）`() {
        val file = enc() // 仅口令槽
        // 构造 32 字符 Base32 形状但并非本备份恢复码的输入
        val fake = "A".repeat(RecoveryCode.RAW_LENGTH)
        try {
            VaultCipher.decrypt(fake.toCharArray(), file)
            throw AssertionError("伪恢复码不应解密成功")
        } catch (_: VaultCipher.VaultException) { }
    }

    @Test
    fun `槽交换攻击被拒（槽 id 进 AAD）`() {
        val rc = RecoveryCode.generate()
        val file = enc(recovery = RecoveryCode.normalize(rc).toCharArray())
        // 读出 header，把 recovery 槽的 id 改成 pass——重打包后恢复码应解不开（AAD 不匹配）
        val headerLen = ((file[8].toInt() and 0xFF) shl 24) or ((file[9].toInt() and 0xFF) shl 16) or
            ((file[10].toInt() and 0xFF) shl 8) or (file[11].toInt() and 0xFF)
        val header = JSONObject(String(file, 12, headerLen, Charsets.UTF_8))
        val slots = header.getJSONArray("slots")
        for (i in 0 until slots.length()) {
            if (slots.getJSONObject(i).getString("id") == "recovery")
                slots.getJSONObject(i).put("id", "pass")
        }
        val newHeader = header.toString().toByteArray(Charsets.UTF_8)
        val out = ByteArray(12 + newHeader.size + (file.size - 12 - headerLen))
        System.arraycopy(file, 0, out, 0, 12)
        System.arraycopy(newHeader, 0, out, 12, newHeader.size)
        System.arraycopy(file, 12 + headerLen, out, 12 + newHeader.size, file.size - 12 - headerLen)
        try {
            VaultCipher.decrypt(rc.toCharArray(), out)
            throw AssertionError("槽交换后恢复码不应解密成功")
        } catch (_: VaultCipher.VaultException) { }
    }

    // ======================= v1 历史格式永久兼容 =======================

    @Test
    fun `v1 旧格式仍可解（ASHKBAK1 只读兼容）`() {
        val legacy = VaultCipher.encryptLegacy(pass, payload, schema, "2026-08-15T08:00:00")
        val d = VaultCipher.decrypt(pass, legacy)
        assertEquals(payload, d.payload)
        assertEquals(schema, d.schemaVersion)
        assertEquals("2026-08-15T08:00:00", d.createdAt)
    }

    @Test
    fun `v1 旧格式口令错误报错`() {
        val legacy = VaultCipher.encryptLegacy(pass, payload, schema, "2026-08-15T08:00:00")
        try {
            VaultCipher.decrypt("wrong".toCharArray(), legacy)
            throw AssertionError("v1 错误口令不应解密成功")
        } catch (e: VaultCipher.VaultException) {
            assertTrue(e.message!!.contains("口令错误"))
        }
    }

    @Test
    fun `v2 文件 magic 为 ASHKBAK2 且槽结构完整`() {
        val file = enc(recovery = RecoveryCode.generate().toCharArray())
        assertEquals("ASHKBAK2", String(file, 0, 8, Charsets.US_ASCII))
        val headerLen = ((file[8].toInt() and 0xFF) shl 24) or ((file[9].toInt() and 0xFF) shl 16) or
            ((file[10].toInt() and 0xFF) shl 8) or (file[11].toInt() and 0xFF)
        val header = JSONObject(String(file, 12, headerLen, Charsets.UTF_8))
        assertEquals(2, header.getInt("fmt"))
        val slots = header.getJSONArray("slots")
        assertEquals(2, slots.length())
        assertEquals("pass", slots.getJSONObject(0).getString("id"))
        assertEquals("recovery", slots.getJSONObject(1).getString("id"))
        // 槽内 KDF 参数进头（后续 Argon2id 升级不破坏旧文件的格式基础）
        assertEquals("pbkdf2-sha256", slots.getJSONObject(0).getString("kdf"))
        assertTrue(slots.getJSONObject(0).getInt("iter") > 0)
        // Base64 可解码（salt/iv/wrapped 均为合法 Base64）
        Base64.getDecoder().decode(slots.getJSONObject(0).getString("salt"))
    }
}
