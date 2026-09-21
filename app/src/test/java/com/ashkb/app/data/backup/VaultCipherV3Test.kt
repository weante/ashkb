package com.ashkb.app.data.backup

import com.ashkb.app.domain.RecoveryCode
import java.util.Base64
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.0.35：v3 信封（稳定 vault key）+ 附件密文原语 + 历史格式兼容。
 * 与 VaultCipherTest 分开：这里只覆盖 v3 / blob，v1/v2 回归仍在原文件。
 */
class VaultCipherV3Test {

    private fun key(seed: Int = 7): ByteArray = ByteArray(32) { (it + seed).toByte() }
    private val pass = "correct-horse".toCharArray()
    private val payload = """{"format":"ashkb-full","tables":{}}"""

    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }

    // ---------------- v3 信封 ----------------

    @Test
    fun `v3 roundtrip exposes the same vault key`() {
        val k = key()
        val file = VaultCipher.encryptV3(k, pass, null, payload, 12, "2026-09-21T10:00:00")
        assertEquals("ASHKBAK3", String(file, 0, 8, Charsets.US_ASCII))

        val d = VaultCipher.decrypt(pass, file)
        assertEquals(payload, d.payload)
        assertEquals(12, d.schemaVersion)
        assertArrayEquals(k, d.vaultKey) // v3 的密钥就是 vault key，恢复时要采纳到 Keystore
    }

    @Test
    fun `v3 recovery slot also unwraps the same key`() {
        val k = key(3)
        val code = RecoveryCode.generate()
        val file = VaultCipher.encryptV3(k, pass, RecoveryCode.normalize(code).toCharArray(), payload, 12, "t")

        // 用户抄写形态（分组 / 小写）也应命中——归一化兜底
        val d = VaultCipher.decrypt(code.lowercase().replace("-", "").toCharArray(), file)
        assertEquals(payload, d.payload)
        assertArrayEquals(k, d.vaultKey)
    }

    @Test(expected = VaultCipher.VaultException::class)
    fun `v3 wrong secret is rejected`() {
        val file = VaultCipher.encryptV3(key(), pass, null, payload, 12, "t")
        VaultCipher.decrypt("wrong-password".toCharArray(), file)
    }

    @Test(expected = VaultCipher.VaultException::class)
    fun `v3 tampered ciphertext is rejected`() {
        val file = VaultCipher.encryptV3(key(), pass, null, payload, 12, "t")
        file[file.size - 1] = (file[file.size - 1] + 1).toByte()
        VaultCipher.decrypt(pass, file)
    }

    @Test(expected = VaultCipher.VaultException::class)
    fun `v3 tampered header is rejected by AAD`() {
        val file = VaultCipher.encryptV3(key(), pass, null, payload, 12, "t")
        // header 明文在 [12, 12+len)；改一个字节 → AAD 不匹配
        val headerLen = ((file[8].toInt() and 0xFF) shl 24) or ((file[9].toInt() and 0xFF) shl 16) or
            ((file[10].toInt() and 0xFF) shl 8) or (file[11].toInt() and 0xFF)
        assertTrue(headerLen > 0)
        file[13] = (file[13] + 1).toByte()
        VaultCipher.decrypt(pass, file)
    }

    @Test
    fun `v3 rejects wrong key length`() {
        var threw = false
        try {
            VaultCipher.encryptV3(ByteArray(16), pass, null, payload, 12, "t")
        } catch (_: IllegalArgumentException) {
            threw = true
        }
        assertTrue(threw)
    }

    // ---------------- 历史格式兼容（v3 不得破坏老文件读取） ----------------

    @Test
    fun `v2 file still decrypts and yields no vault key`() {
        val file = VaultCipher.encrypt(pass, null, payload, 11, "t")
        assertEquals("ASHKBAK2", String(file, 0, 8, Charsets.US_ASCII))
        val d = VaultCipher.decrypt(pass, file)
        assertEquals(payload, d.payload)
        // v2 解出的是该文件私有随机 DEK，**不能**被当成 vault key 采纳
        assertNull(d.vaultKey)
    }

    @Test
    fun `v1 legacy file still decrypts`() {
        val file = VaultCipher.encryptLegacy(pass, payload, 10, "t")
        assertEquals("ASHKBAK1", String(file, 0, 8, Charsets.US_ASCII))
        val d = VaultCipher.decrypt(pass, file)
        assertEquals(payload, d.payload)
        assertNull(d.vaultKey)
    }

    @Test
    fun `unknown magic is rejected`() {
        var threw = false
        try {
            VaultCipher.decrypt(pass, "NOTABAK9".toByteArray() + ByteArray(40))
        } catch (_: VaultCipher.VaultException) {
            threw = true
        }
        assertTrue(threw)
    }

    // ---------------- 附件密文 ----------------

    @Test
    fun `blob roundtrip preserves binary content`() {
        val k = key()
        val plain = ByteArray(4096) { (it % 251).toByte() }
        val blob = VaultCipher.encryptBlob(k, plain, "catt-abc123")
        assertEquals("ASHKBATT", String(blob, 0, 8, Charsets.US_ASCII))
        // 8B magic + 12B iv + 密文(+16B tag)
        assertEquals(8 + 12 + plain.size + 16, blob.size)
        assertArrayEquals(plain, VaultCipher.decryptBlob(k, blob, "catt-abc123"))
    }

    @Test
    fun `blob ciphertext differs each time (random iv)`() {
        val k = key()
        val plain = "same content".toByteArray()
        val a = VaultCipher.encryptBlob(k, plain, "id1")
        val b = VaultCipher.encryptBlob(k, plain, "id1")
        assertFalse(hex(a) == hex(b))
    }

    @Test(expected = VaultCipher.VaultException::class)
    fun `blob with wrong key is rejected`() {
        val blob = VaultCipher.encryptBlob(key(1), "secret".toByteArray(), "id1")
        VaultCipher.decryptBlob(key(2), blob, "id1")
    }

    @Test(expected = VaultCipher.VaultException::class)
    fun `blob bound to its attachment id (AAD prevents swap)`() {
        val k = key()
        val blob = VaultCipher.encryptBlob(k, "report A".toByteArray(), "catt-A")
        // 把 A 的密文冒充成 B —— AAD 不匹配必须失败
        VaultCipher.decryptBlob(k, blob, "catt-B")
    }

    @Test(expected = VaultCipher.VaultException::class)
    fun `tampered blob is rejected`() {
        val k = key()
        val blob = VaultCipher.encryptBlob(k, "content".toByteArray(), "id1")
        blob[blob.size - 1] = (blob[blob.size - 1] + 1).toByte()
        VaultCipher.decryptBlob(k, blob, "id1")
    }

    @Test(expected = VaultCipher.VaultException::class)
    fun `non-blob bytes are rejected`() {
        VaultCipher.decryptBlob(key(), ByteArray(64), "id1")
    }

    @Test(expected = VaultCipher.VaultException::class)
    fun `too short blob is rejected`() {
        VaultCipher.decryptBlob(key(), ByteArray(10), "id1")
    }

    /** 空文件也要能加密（相机取消后留下的空占位文件不会走到这里，但边界要稳）。 */
    @Test
    fun `empty plaintext roundtrips`() {
        val k = key()
        val blob = VaultCipher.encryptBlob(k, ByteArray(0), "id-empty")
        assertArrayEquals(ByteArray(0), VaultCipher.decryptBlob(k, blob, "id-empty"))
    }

    /** 附件密钥走 Base64 落 prefs 的往返（VaultKeyStore 的存储形态）。 */
    @Test
    fun `vault key survives base64 roundtrip`() {
        val k = key(9)
        val b64 = Base64.getEncoder().encodeToString(k)
        assertArrayEquals(k, Base64.getDecoder().decode(b64))
    }
}
