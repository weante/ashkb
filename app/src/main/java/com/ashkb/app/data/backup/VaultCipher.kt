package com.ashkb.app.data.backup

import org.json.JSONArray
import org.json.JSONObject
import com.ashkb.app.domain.RecoveryCode
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * 备份加密器：AES-256-GCM 信封加密 + 多密钥槽（v1.0.27 起，规划缺口 A2「恢复码」）。
 *
 * **文件格式 v2**（v1.0.27 起新备份统一采用）：
 *   [8B magic "ASHKBAK2"] [4B headerLen] [header JSON 明文] [密文 = GCM(payload, key=DEK, AAD=header)]
 * header 持 fmt=2 / cipher / schema_version / created_at / payload iv / slots[]。
 * 信封结构：随机 256-bit 数据密钥 DEK 加密 payload；每个槽把 DEK 再包一层——
 *   wrapped = GCM(DEK, key=PBKDF2(槽秘密), iv=槽iv, AAD="ASHKBAK2:slot:"+id)
 * 槽 id 进 AAD 防槽交换攻击。设过恢复码时含 "pass" 与 "recovery" 两槽，
 * **口令或恢复码任一可解**（口令遗忘的退路，规划 A2）；未设时仅 "pass" 槽
 * （格式仍为 v2——之后设置恢复码零格式变更，只是多一个槽）。
 *
 * **文件格式 v1**（v1.0.5–v1.0.26，永久兼容读取）：
 *   [8B magic "ASHKBAK1"] [4B headerLen] [header JSON] [密文 = GCM(payload, key=PBKDF2(口令), AAD=header)]
 * 口令直接派生 payload 密钥，无恢复码槽。
 *
 * KDF 标识与迭代次数进每个槽的头（协议 §2 兜底切换条款）——后续 KDF 升级
 * （如 Argon2id）可在新槽内无破坏切换，旧文件按头内参数解。
 */
object VaultCipher {
    const val MAGIC_V1 = "ASHKBAK1"
    const val MAGIC_V2 = "ASHKBAK2"
    private const val KDF = "pbkdf2-sha256"
    private const val ITER = 120_000
    private const val KEY_BITS = 256
    private const val GCM_TAG_BITS = 128
    private const val SALT_LEN = 16
    private const val IV_LEN = 12
    private const val DEK_LEN = 32
    private const val SLOT_AAD_PREFIX = "ASHKBAK2:slot:"

    class VaultException(msg: String, cause: Throwable? = null) : Exception(msg, cause)

    fun deriveKey(password: CharArray, salt: ByteArray, iter: Int): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, iter, KEY_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val key = factory.generateSecret(spec).encoded
        spec.clearPassword()
        return SecretKeySpec(key, "AES")
    }

    private fun b64(b: ByteArray): String = Base64.getEncoder().encodeToString(b)
    private fun b64d(s: String): ByteArray =
        runCatching { Base64.getDecoder().decode(s) }.getOrDefault(ByteArray(0))

    // ======================= v2：信封加密（当前生产路径） =======================

    /**
     * v2 加密。recoveryCode 非空 → 双槽（口令 / 恢复码任一可解）；空 → 仅口令槽。
     * 秘密错配的槽在解密时逐槽尝试失败即跳过，互不干扰。
     */
    fun encrypt(pass: CharArray, recoveryCode: CharArray?, payload: String,
                schemaVersion: Int, nowIso: String): ByteArray {
        val rnd = SecureRandom()
        val dek = ByteArray(DEK_LEN).also { rnd.nextBytes(it) }
        val payloadIv = ByteArray(IV_LEN).also { rnd.nextBytes(it) }

        val slots = JSONArray()
        wrapSlot(slots, rnd, dek, "pass", pass)
        recoveryCode?.let { wrapSlot(slots, rnd, dek, "recovery", it) }

        val header = JSONObject().apply {
            put("fmt", 2)
            put("cipher", "aes-256-gcm")
            put("schema_version", schemaVersion)
            put("created_at", nowIso)
            put("iv", b64(payloadIv))
            put("slots", slots)
        }
        val headerBytes = header.toString().toByteArray(Charsets.UTF_8)
        val ct = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(dek, "AES"), GCMParameterSpec(GCM_TAG_BITS, payloadIv))
            updateAAD(headerBytes) // AAD 绑定明文头防跨协议改头（协议 §2，与 v1 一致）
        }.doFinal(payload.toByteArray(Charsets.UTF_8))
        return pack(MAGIC_V2, headerBytes, ct)
    }

    /** 用槽秘密（口令或恢复码）包 DEK，追加进 slots 数组。 */
    private fun wrapSlot(slots: JSONArray, rnd: SecureRandom, dek: ByteArray, id: String, secret: CharArray) {
        val salt = ByteArray(SALT_LEN).also { rnd.nextBytes(it) }
        val iv = ByteArray(IV_LEN).also { rnd.nextBytes(it) }
        val wrapped = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, deriveKey(secret, salt, ITER), GCMParameterSpec(GCM_TAG_BITS, iv))
            updateAAD((SLOT_AAD_PREFIX + id).toByteArray(Charsets.UTF_8)) // 槽 id 进 AAD 防交换
        }.doFinal(dek)
        slots.put(JSONObject().apply {
            put("id", id)
            put("kdf", KDF)
            put("iter", ITER)
            put("salt", b64(salt))
            put("iv", b64(iv))
            put("wrapped", b64(wrapped))
        })
    }

    // ======================= 解密（v1/v2 统一入口，永久兼容） =======================

    /** 解密并返回明文 payload；格式错误 / 口令与恢复码均不匹配 / 篡改均抛 VaultException。 */
    fun decrypt(secret: CharArray, file: ByteArray): Decrypted {
        if (file.size < 12) throw VaultException("文件过短，不是有效的 ASHKB 备份")
        val magic = String(file, 0, 8, Charsets.US_ASCII)
        if (magic != MAGIC_V1 && magic != MAGIC_V2)
            throw VaultException("文件头标识不符（期望 $MAGIC_V1/$MAGIC_V2）")
        val headerLen = readIntBE(file, 8)
        if (headerLen <= 0 || 12 + headerLen >= file.size) throw VaultException("文件头长度非法")
        val headerBytes = file.copyOfRange(12, 12 + headerLen)
        val ct = file.copyOfRange(12 + headerLen, file.size)
        val header = try {
            JSONObject(String(headerBytes, Charsets.UTF_8))
        } catch (e: Exception) {
            throw VaultException("文件头损坏", e)
        }
        if (header.optString("cipher") != "aes-256-gcm")
            throw VaultException("不支持的加密算法 ${header.optString("cipher")}")
        return if (magic == MAGIC_V1) decryptV1(secret, header, headerBytes, ct)
        else decryptV2(secret, header, headerBytes, ct)
    }

    /** v1：口令直接派生 payload 密钥（历史文件，只读兼容）。 */
    private fun decryptV1(secret: CharArray, header: JSONObject, headerBytes: ByteArray, ct: ByteArray): Decrypted {
        if (header.optString("kdf") != KDF) throw VaultException("不支持的 KDF：${header.optString("kdf")}")
        val salt: ByteArray; val iv: ByteArray; val iter: Int
        try {
            salt = Base64.getDecoder().decode(header.getString("salt"))
            iv = Base64.getDecoder().decode(header.getString("iv"))
            iter = header.getInt("iter")
        } catch (e: Exception) {
            throw VaultException("文件头损坏", e)
        }
        val key = deriveKey(secret, salt, iter)
        val plain = try {
            Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
                updateAAD(headerBytes)
            }.doFinal(ct)
        } catch (e: Exception) {
            throw VaultException("解密失败：口令错误或文件已损坏", e)
        }
        return Decrypted(
            payload = String(plain, Charsets.UTF_8),
            schemaVersion = header.optInt("schema_version", 0),
            createdAt = header.optString("created_at"),
        )
    }

    /**
     * v2 解密的候选秘密：用户输入的可能是口令，也可能是恢复码的任意抄写形态
     * （分组 / 无连字符 / 小写）。先按原样试全部槽；输入形似恢复码时再按归一化
     * 形态补一轮。口令若第一轮已命中则不受影响——归一化只在原样全失败后兜底。
     */
    private fun candidates(secret: CharArray): List<CharArray> =
        if (RecoveryCode.isValid(String(secret)))
            listOf(secret, RecoveryCode.normalize(String(secret)).toCharArray())
        else listOf(secret)

    /** v2：逐槽尝试解包 DEK（任一槽秘密匹配即通过），再用 DEK 解 payload。 */
    private fun decryptV2(secret: CharArray, header: JSONObject, headerBytes: ByteArray, ct: ByteArray): Decrypted {
        val slots = header.optJSONArray("slots") ?: throw VaultException("文件头缺少密钥槽")
        var dek: ByteArray? = null
        outer@ for (cand in candidates(secret)) {
            for (i in 0 until slots.length()) {
                val slot = slots.optJSONObject(i) ?: continue
                if (slot.optString("kdf") != KDF) throw VaultException("不支持的 KDF：${slot.optString("kdf")}")
                val salt = b64d(slot.optString("salt"))
                val iv = b64d(slot.optString("iv"))
                val wrapped = b64d(slot.optString("wrapped"))
                val iter = slot.optInt("iter", -1)
                if (salt.size != SALT_LEN || iv.size != IV_LEN || wrapped.isEmpty() || iter <= 0)
                    throw VaultException("密钥槽损坏")
                val unwrapped = try {
                    Cipher.getInstance("AES/GCM/NoPadding").apply {
                        init(Cipher.DECRYPT_MODE, deriveKey(cand, salt, iter), GCMParameterSpec(GCM_TAG_BITS, iv))
                        updateAAD((SLOT_AAD_PREFIX + slot.optString("id")).toByteArray(Charsets.UTF_8))
                    }.doFinal(wrapped)
                } catch (_: Exception) {
                    null // 本槽秘密不匹配——继续试下一槽
                }
                if (unwrapped != null && unwrapped.size == DEK_LEN) { dek = unwrapped; break@outer }
            }
        }
        val key = dek ?: throw VaultException("解密失败：口令或恢复码错误，或文件已损坏")
        val payloadIv = b64d(header.optString("iv"))
        if (payloadIv.size != IV_LEN) throw VaultException("文件头损坏")
        val plain = try {
            Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, payloadIv))
                updateAAD(headerBytes)
            }.doFinal(ct)
        } catch (e: Exception) {
            throw VaultException("解密失败：文件已损坏（密钥槽通过但内容认证失败）", e)
        }
        return Decrypted(
            payload = String(plain, Charsets.UTF_8),
            schemaVersion = header.optInt("schema_version", 0),
            createdAt = header.optString("created_at"),
        )
    }

    data class Decrypted(val payload: String, val schemaVersion: Int, val createdAt: String)

    // ======================= v1 加密（仅回归测试生成历史样本） =======================

    /**
     * v1 格式加密——v1.0.26 及以前的历史格式。生产路径已统一 v2（未设恢复码也用 v2 单槽，
     * 格式不再分裂）；此函数仅为单测生成 v1 样本、验证「永久兼容读取」而保留。
     */
    internal fun encryptLegacy(password: CharArray, payload: String,
                               schemaVersion: Int, nowIso: String): ByteArray {
        val rnd = SecureRandom()
        val salt = ByteArray(SALT_LEN).also { rnd.nextBytes(it) }
        val iv = ByteArray(IV_LEN).also { rnd.nextBytes(it) }
        val header = JSONObject().apply {
            put("kdf", KDF)
            put("iter", ITER)
            put("salt", b64(salt))
            put("iv", b64(iv))
            put("schema_version", schemaVersion)
            put("created_at", nowIso)
            put("cipher", "aes-256-gcm")
        }
        val headerBytes = header.toString().toByteArray(Charsets.UTF_8)
        val key = deriveKey(password, salt, ITER)
        val ct = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            updateAAD(headerBytes)
        }.doFinal(payload.toByteArray(Charsets.UTF_8))
        return pack(MAGIC_V1, headerBytes, ct)
    }

    // ======================= 布局原语 =======================

    private fun pack(magic: String, headerBytes: ByteArray, ct: ByteArray): ByteArray {
        val out = ByteArray(8 + 4 + headerBytes.size + ct.size)
        System.arraycopy(magic.toByteArray(Charsets.US_ASCII), 0, out, 0, 8)
        writeIntBE(out, 8, headerBytes.size)
        System.arraycopy(headerBytes, 0, out, 12, headerBytes.size)
        System.arraycopy(ct, 0, out, 12 + headerBytes.size, ct.size)
        return out
    }

    private fun writeIntBE(buf: ByteArray, off: Int, v: Int) {
        buf[off] = (v ushr 24).toByte(); buf[off + 1] = (v ushr 16).toByte()
        buf[off + 2] = (v ushr 8).toByte(); buf[off + 3] = v.toByte()
    }

    private fun readIntBE(buf: ByteArray, off: Int): Int =
        ((buf[off].toInt() and 0xFF) shl 24) or ((buf[off + 1].toInt() and 0xFF) shl 16) or
            ((buf[off + 2].toInt() and 0xFF) shl 8) or (buf[off + 3].toInt() and 0xFF)
}
