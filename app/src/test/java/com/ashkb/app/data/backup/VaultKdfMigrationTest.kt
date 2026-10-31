package com.ashkb.app.data.backup

import com.ashkb.app.R
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * v1.2.13 S-1：备份槽的 KDF 从 PBKDF2 换成 Argon2id（审查报告 §十 第 3 条裁决
 * 「不信任 WebDAV 服务商」）。
 *
 * 这一层最怕的不是「新文件解不开」（本机往返测试覆盖得到），而是**旧文件突然打不开**
 * ——那等于把用户存在云端的全部历史备份废掉。所以这里的每条断言都围绕「读侧按槽头
 * 声明的算法分派」这个契约：
 *
 *  - 旧文件（槽头仍写 `pbkdf2-sha256`）必须照常解开，且口令错时仍报「口令错误」
 *    而不是被误判成「槽损坏」/「不支持的 KDF」；
 *  - 新文件（槽头写 `argon2id`）的密钥**必须真用 Argon2id 派生**——把槽头的 kdf
 *    改回 pbkdf2 后必须解不开，否则说明声明与实际不符，将来任何算法替换都是空转；
 *  - 未知 KDF 与越界参数一律拒绝，绝不回落到 PBKDF2 或落到未校验的内存分配。
 *
 * 手工装配信封（而不是调 `VaultCipher.encrypt`）是刻意的：要造的是当前生产者**不会**
 * 再产出的东西——旧的 PBKDF2 槽、伪造的 KDF 头、越界的 Argon2 参数。
 */
class VaultKdfMigrationTest {

    private val payload = """{"format":"ashkb-full","schema_version":21,"tables":{},"manifest":{}}"""
    private val pass = "correct-horse-battery".toCharArray()

    // ======================= 旧文件：必须照常打开 =======================

    @Test
    fun `旧版 PBKDF2 双槽文件仍能解开`() {
        val file = legacyPbkdf2V2File(pass, payload)
        val d = VaultCipher.decrypt(pass, file)
        assertEquals("升级 KDF 不能废掉历史备份", payload, d.payload)
    }

    @Test
    fun `旧版文件口令错时报口令错误而不是槽损坏`() {
        val file = legacyPbkdf2V2File(pass, payload)
        try {
            VaultCipher.decrypt("wrong-password".toCharArray(), file)
            fail("错误口令不应解密成功")
        } catch (e: VaultCipher.VaultException) {
            // 关键区分：报「槽损坏」会让用户以为文件废了，报「口令错误」他才会再试一次
            assertEquals(R.string.backup_err_vault_bad_pass_or_code, e.resId)
        }
    }

    // ======================= 新文件：声明的 KDF 必须真的在用 =======================

    @Test
    fun `新文件的槽头声明 argon2id 且参数齐全`() {
        val file = VaultCipher.encrypt(pass, "ABCD-EFGH-IJKL".toCharArray(), payload, 21, NOW)
        val slot = slotsOf(file).getJSONObject(0)
        assertEquals(KDF_ARGON2ID, slot.getString("kdf"))
        assertTrue("Argon2id 的遍数 t 必须进头", slot.getInt("iter") >= 1)
        assertTrue("内存参数 m 必须进头，否则读侧无从复现", slot.getInt("m") >= 8 * slot.getInt("p"))
        assertTrue("并行度 p 必须进头", slot.getInt("p") >= 1)
    }

    @Test
    fun `把新文件的槽头改回 pbkdf2 就解不开`() {
        // 若这条能解开，说明「声明 argon2id」只是装饰，实际仍在跑 PBKDF2——
        // 那样的算法替换等于没做，而用户以为自己拿到了内存硬 KDF。
        val file = VaultCipher.encrypt(pass, null, payload, 21, NOW)
        val forged = rewriteFirstSlot(file) { it.put("kdf", "pbkdf2-sha256") }
        try {
            VaultCipher.decrypt(pass, forged)
            fail("槽头声明 pbkdf2 时不可能还解得出 argon2id 派生的密钥")
        } catch (e: VaultCipher.VaultException) {
            assertEquals(R.string.backup_err_vault_bad_pass_or_code, e.resId)
        }
    }

    // ======================= 伪造槽头：一律拒绝 =======================

    @Test
    fun `未知 KDF 被拒绝而不是回落 pbkdf2`() {
        val file = forgedFileBeforeDerivation(kdf = "scrypt-n2", memory = null, lanes = null)
        try {
            VaultCipher.decrypt(pass, file)
            fail("未知 KDF 不应被尝试解密")
        } catch (e: VaultCipher.VaultException) {
            assertTrue("实际：${e.message}", e.message!!.contains("不支持的 KDF"))
            assertEquals(R.string.backup_err_vault_bad_kdf, e.resId)
        }
    }

    @Test
    fun `argon2id 的内存参数越界时拒绝且在分配内存之前`() {
        // m=4 GiB 的伪造头足以让恢复过程 OOM；必须在申请内存之前就拒掉
        val file = forgedFileBeforeDerivation(kdf = KDF_ARGON2ID, memory = HUGE_MEMORY_KIB, lanes = 1)
        try {
            VaultCipher.decrypt(pass, file)
            fail("越界的内存参数不应进入派生")
        } catch (e: VaultCipher.VaultException) {
            assertEquals(R.string.backup_err_vault_slot_corrupt, e.resId)
        }
    }

    @Test
    fun `argon2id 的并行度越界时拒绝`() {
        val file = forgedFileBeforeDerivation(kdf = KDF_ARGON2ID, memory = DEFAULT_MEMORY_KIB, lanes = HUGE_LANES)
        try {
            VaultCipher.decrypt(pass, file)
            fail("越界的并行度不应进入派生")
        } catch (e: VaultCipher.VaultException) {
            assertEquals(R.string.backup_err_vault_slot_corrupt, e.resId)
        }
    }

    @Test
    fun `argon2id 的遍数越界时拒绝`() {
        // iter 在 Argon2id 下是遍数 t；一个 t=99999 的伪造头同样是 DoS
        val file = forgedFileBeforeDerivation(
            kdf = KDF_ARGON2ID,
            memory = DEFAULT_MEMORY_KIB,
            lanes = 1,
            iterations = HUGE_ITERATIONS,
        )
        try {
            VaultCipher.decrypt(pass, file)
            fail("越界的遍数不应进入派生")
        } catch (e: VaultCipher.VaultException) {
            assertEquals(R.string.backup_err_vault_kdf_iter, e.resId)
        }
    }

    // ======================= 信封装配helper =======================

    /**
     * 造一个「旧生产者」（v1.2.12 及更早）写出的 v2 文件：单口令槽、`kdf=pbkdf2-sha256`。
     * 每一步都手工做，好让这条测试**不依赖**被测代码当前选择的算法。
     */
    private fun legacyPbkdf2V2File(password: CharArray, plain: String): ByteArray {
        val rnd = SecureRandom()
        val dek = ByteArray(DEK_LEN).also { rnd.nextBytes(it) }
        val salt = ByteArray(SALT_LEN).also { rnd.nextBytes(it) }
        val slotIv = ByteArray(IV_LEN).also { rnd.nextBytes(it) }
        val slotKey = VaultCipher.deriveKey(password, salt, LEGACY_ITER)
        val wrapped = Cipher.getInstance(JCE_CIPHER).apply {
            init(Cipher.ENCRYPT_MODE, slotKey, GCMParameterSpec(GCM_TAG_BITS, slotIv))
            updateAAD((SLOT_AAD_PREFIX + "pass").toByteArray(Charsets.UTF_8))
        }.doFinal(dek)
        val slot = JSONObject().apply {
            put("id", "pass")
            put("kdf", "pbkdf2-sha256")
            put("iter", LEGACY_ITER)
            put("salt", b64(salt))
            put("iv", b64(slotIv))
            put("wrapped", b64(wrapped))
        }
        val payloadIv = ByteArray(IV_LEN).also { rnd.nextBytes(it) }
        val header = headerOf(JSONArray().put(slot), payloadIv)
        val headerBytes = header.toString().toByteArray(Charsets.UTF_8)
        val ct = Cipher.getInstance(JCE_CIPHER).apply {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(dek, "AES"), GCMParameterSpec(GCM_TAG_BITS, payloadIv))
            updateAAD(headerBytes)
        }.doFinal(plain.toByteArray(Charsets.UTF_8))
        return assemble(headerBytes, ct)
    }

    /**
     * 造一个「能在派生阶段就被拦下」的文件：槽的 salt/iv/wrapped 长度都合法（先过结构
     * 校验），但 kdf / m / p / iter 是我们要测的那个值；payload 密文是垃圾字节，
     * 因为断言全部发生在槽解包之前。
     */
    private fun forgedFileBeforeDerivation(
        kdf: String,
        memory: Int?,
        lanes: Int?,
        iterations: Int = DEFAULT_ITERATIONS,
    ): ByteArray {
        val slot = JSONObject().apply {
            put("id", "pass")
            put("kdf", kdf)
            put("iter", iterations)
            memory?.let { put("m", it) }
            lanes?.let { put("p", it) }
            put("salt", b64(ByteArray(SALT_LEN) { it.toByte() }))
            put("iv", b64(ByteArray(IV_LEN) { it.toByte() }))
            put("wrapped", b64(ByteArray(DEK_LEN + GCM_TAG_BYTES) { 0x5A.toByte() }))
        }
        val header = headerOf(JSONArray().put(slot), ByteArray(IV_LEN) { it.toByte() })
        return assemble(header.toString().toByteArray(Charsets.UTF_8), ByteArray(PAYLOAD_CT_LEN) { 0x33 })
    }

    private fun headerOf(slots: JSONArray, payloadIv: ByteArray): JSONObject = JSONObject().apply {
        put("fmt", 2)
        put("cipher", HEADER_CIPHER)
        put("schema_version", 21)
        put("created_at", NOW)
        put("iv", b64(payloadIv))
        put("slots", slots)
    }

    /** `magic(8) + headerLen(BE int32) + headerBytes + ct`——与 [VaultCipher] 的私有 pack 同构。 */
    private fun assemble(headerBytes: ByteArray, ct: ByteArray): ByteArray {
        val out = ByteArray(MAGIC_LEN + LEN_FIELD_LEN + headerBytes.size + ct.size)
        System.arraycopy(VaultCipher.MAGIC_V2.toByteArray(Charsets.US_ASCII), 0, out, 0, MAGIC_LEN)
        val n = headerBytes.size
        out[8] = ((n ushr BITS_PER_INT_SHIFT_3) and BYTE_MASK).toByte()
        out[9] = ((n ushr BITS_PER_INT_SHIFT_2) and BYTE_MASK).toByte()
        out[10] = ((n ushr BITS_PER_INT_SHIFT_1) and BYTE_MASK).toByte()
        out[11] = (n and BYTE_MASK).toByte()
        System.arraycopy(headerBytes, 0, out, MAGIC_LEN + LEN_FIELD_LEN, headerBytes.size)
        System.arraycopy(ct, 0, out, MAGIC_LEN + LEN_FIELD_LEN + headerBytes.size, ct.size)
        return out
    }

    private fun slotsOf(file: ByteArray): JSONArray {
        val headerLen = ((file[8].toInt() and BYTE_MASK) shl BITS_PER_INT_SHIFT_3) or
            ((file[9].toInt() and BYTE_MASK) shl BITS_PER_INT_SHIFT_2) or
            ((file[10].toInt() and BYTE_MASK) shl BITS_PER_INT_SHIFT_1) or
            (file[11].toInt() and BYTE_MASK)
        val header = JSONObject(String(file, MAGIC_LEN + LEN_FIELD_LEN, headerLen, Charsets.UTF_8))
        return header.getJSONArray("slots")
    }

    /** 只改第一个槽的字段，其余字节原样保留（含 payload 密文）。 */
    private fun rewriteFirstSlot(file: ByteArray, edit: (JSONObject) -> Unit): ByteArray {
        val headerLen = ((file[8].toInt() and BYTE_MASK) shl BITS_PER_INT_SHIFT_3) or
            ((file[9].toInt() and BYTE_MASK) shl BITS_PER_INT_SHIFT_2) or
            ((file[10].toInt() and BYTE_MASK) shl BITS_PER_INT_SHIFT_1) or
            (file[11].toInt() and BYTE_MASK)
        val header = JSONObject(String(file, MAGIC_LEN + LEN_FIELD_LEN, headerLen, Charsets.UTF_8))
        edit(header.getJSONArray("slots").getJSONObject(0))
        val ct = file.copyOfRange(MAGIC_LEN + LEN_FIELD_LEN + headerLen, file.size)
        return assemble(header.toString().toByteArray(Charsets.UTF_8), ct)
    }

    private fun b64(b: ByteArray): String = Base64.getEncoder().encodeToString(b)

    private companion object {
        /** 槽头 `cipher` 字段的值（协议标识，不是 JCE 算法名）。 */
        const val HEADER_CIPHER = "aes-256-gcm"
        /** JCE 实际算法名——`Cipher.getInstance` 不认槽头里那个写法。 */
        const val JCE_CIPHER = "AES/GCM/NoPadding"
        const val NOW = "2026-10-31T12:00:00"
        const val KDF_ARGON2ID = "argon2id"
        const val SLOT_AAD_PREFIX = "ASHKBAK2:slot:"
        const val LEGACY_ITER = 120_000
        const val DEFAULT_ITERATIONS = 2
        const val DEFAULT_MEMORY_KIB = 19_456
        const val HUGE_MEMORY_KIB = 4_194_304
        const val HUGE_LANES = 64
        const val HUGE_ITERATIONS = 99_999
        const val KEY_BITS = 256
        const val BITS_PER_BYTE = 8
        const val BYTE_MASK = 0xFF
        const val BITS_PER_INT_SHIFT_3 = 24
        const val BITS_PER_INT_SHIFT_2 = 16
        const val BITS_PER_INT_SHIFT_1 = 8
        const val MAGIC_LEN = 8
        const val LEN_FIELD_LEN = 4
        const val SALT_LEN = 16
        const val IV_LEN = 12
        const val DEK_LEN = KEY_BITS / BITS_PER_BYTE
        const val GCM_TAG_BITS = 128
        const val GCM_TAG_BYTES = GCM_TAG_BITS / BITS_PER_BYTE
        const val PAYLOAD_CT_LEN = 64
    }
}
