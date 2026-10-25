package com.ashkb.app.data.backup

import com.ashkb.app.R
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.Base64

/**
 * v1.2.11（缺陷工单 C02）：**密钥槽数量的上限必须在任何 PBKDF2 之前生效**。
 *
 * 缺陷本体：备份文件是外部输入，`iter` 早就有 [VaultCipher] 内的 MIN/MAX 区间保护，
 * 但**槽数量**从来没有约束。解密时是「候选秘密 × 槽」的双层循环，每个槽跑一次
 * PBKDF2（120k 迭代）；恢复码场景下候选秘密还会翻倍。一个塞进几百个槽的恶意文件
 * 因此能把恢复耗时线性放大到「看起来卡死」——与 iter DoS 是同一类问题，只是换了个维度。
 *
 * 这组用例的关键不是「有报错」，而是**报错发生在正确的位置**：
 * [槽数量超限时不应先跑 PBKDF2] 故意把所有槽的 kdf 写成非法值——如果数量闸被放到
 * 循环之后（或干脆没有），这里会先撞上「不支持的 KDF」；只有闸在循环之前，
 * 才会稳定地报出槽数量异常。
 */
class VaultSlotLimitTest {

    private val pass = "correct-horse-battery".toCharArray()
    private val payload = """{"format":"ashkb-full","schema_version":20,"tables":{},"manifest":{}}"""

    private fun b64(s: String): String = Base64.getEncoder().encodeToString(s.toByteArray(Charsets.UTF_8))

    /** 造一个假槽：kdf 可指定，salt/iv/wrapped 用无意义但合法的 base64。 */
    private fun fakeSlot(kdf: String): JSONObject = JSONObject().apply {
        put("id", "pass")
        put("kdf", kdf)
        put("iter", 120_000)
        put("salt", b64("salt"))
        put("iv", b64("iv-12-bytes!"))
        put("wrapped", b64("wrapped"))
    }

    /**
     * 手工装配 v2 信封：`magic(8) + headerLen(BE int32) + headerBytes + ct`。
     *
     * 不用 [VaultCipher] 的私有 pack：要造的正是它**不会**产出的东西（超量槽）。
     */
    private fun buildFile(slots: JSONArray): ByteArray {
        val header = JSONObject().apply {
            put("fmt", 2)
            put("cipher", "aes-256-gcm")
            put("schema_version", 20)
            put("created_at", "2026-10-25T00:00:00")
            put("iv", b64("payload-iv!!"))
            put("slots", slots)
        }
        val headerBytes = header.toString().toByteArray(Charsets.UTF_8)
        val ct = ByteArray(32) { 0x5A }
        val out = ByteArray(12 + headerBytes.size + ct.size)
        System.arraycopy("ASHKBAK2".toByteArray(Charsets.US_ASCII), 0, out, 0, 8)
        val n = headerBytes.size
        out[8] = ((n ushr 24) and 0xFF).toByte()
        out[9] = ((n ushr 16) and 0xFF).toByte()
        out[10] = ((n ushr 8) and 0xFF).toByte()
        out[11] = (n and 0xFF).toByte()
        System.arraycopy(headerBytes, 0, out, 12, headerBytes.size)
        System.arraycopy(ct, 0, out, 12 + headerBytes.size, ct.size)
        return out
    }

    private fun slots(count: Int, kdf: String): JSONArray = JSONArray().apply {
        repeat(count) { put(fakeSlot(kdf)) }
    }

    // ======================= 闸门本身 =======================

    @Test
    fun `槽数量超限时不应先跑 PBKDF2`() {
        // 17 个槽、且每个槽的 kdf 都是非法值：只要数量闸排在循环之前，
        // 就绝不可能先撞上「不支持的 KDF」。
        val file = buildFile(slots(VaultCipher.MAX_SLOTS + 1, "not-a-real-kdf"))
        try {
            VaultCipher.decrypt(pass, file)
            fail("超量密钥槽不应解密成功")
        } catch (e: VaultCipher.VaultException) {
            assertTrue(
                "期望在 PBKDF2 之前被数量闸拦下，实际报错：${e.message}",
                e.message!!.contains("密钥槽数量异常"),
            )
            assertEquals(R.string.backup_err_vault_too_many_slots, e.resId)
            assertEquals(listOf(VaultCipher.MAX_SLOTS + 1, VaultCipher.MAX_SLOTS), e.resArgs)
        }
    }

    @Test
    fun `恰好达到上限的槽数量不被数量闸拦下`() {
        // 边界不误伤：16 个合法 kdf 的槽应当**通过**数量闸，进到真正的逐槽解包，
        // 最终因「槽里的密文是假的」而报密钥槽损坏——而不是被数量闸拦下。
        val file = buildFile(slots(VaultCipher.MAX_SLOTS, "pbkdf2-sha256"))
        try {
            VaultCipher.decrypt(pass, file)
            fail("假槽不应解密成功")
        } catch (e: VaultCipher.VaultException) {
            assertTrue(
                "16 个槽不该被数量闸拦下，实际报错：${e.message}",
                !e.message!!.contains("密钥槽数量异常"),
            )
            assertEquals(
                "应当已经通过数量闸、走到逐槽解包并因假密文失败",
                R.string.backup_err_vault_slot_corrupt,
                e.resId,
            )
        }
    }

    // ======================= 回归：正常文件不受影响 =======================

    @Test
    fun `正常双槽文件仍可往返`() {
        val file = VaultCipher.encrypt(pass, "ABCD-EFGH-IJKL".toCharArray(), payload, 20, "2026-10-25T00:00:00")
        assertEquals(payload, VaultCipher.decrypt(pass, file).payload)
    }

    @Test
    fun `v3 单槽文件仍可往返`() {
        val vaultKey = ByteArray(VaultCipher.VAULT_KEY_LEN) { it.toByte() }
        val file = VaultCipher.encryptV3(vaultKey, pass, null, payload, 20, "2026-10-25T00:00:00")
        val d = VaultCipher.decrypt(pass, file)
        assertEquals(payload, d.payload)
        assertEquals(vaultKey.toList(), d.vaultKey!!.toList())
    }
}
