package com.ashkb.app.data.backup

import android.util.Base64
import org.json.JSONObject
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * 备份加密器：AES-256-GCM + PBKDF2WithHmacSHA256 口令派生。
 * 文件格式（协议 §2 密文头 schema 的 P4 实现层）：
 *   [8B magic "ASHKBAK1"] [4B headerLen] [header JSON 明文] [密文 = GCM(payload, AAD=header)]
 * header 明文持 KDF 标识 / 迭代次数 / salt / iv / schema 版本——算法与 KDF 进头，
 * 后续 Argon2id 升级或 KDF 参数调整不破坏旧文件（协议 §2 兜底切换条款）。
 */
object VaultCipher {
    const val MAGIC = "ASHKBAK1"
    private const val KDF = "pbkdf2-sha256"
    private const val ITER = 120_000
    private const val KEY_BITS = 256
    private const val GCM_TAG_BITS = 128
    private const val SALT_LEN = 16
    private const val IV_LEN = 12

    class VaultException(msg: String, cause: Throwable? = null) : Exception(msg, cause)

    fun deriveKey(password: CharArray, salt: ByteArray, iter: Int): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, iter, KEY_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val key = factory.generateSecret(spec).encoded
        spec.clearPassword()
        return SecretKeySpec(key, "AES")
    }

    fun encrypt(password: CharArray, payload: String, schemaVersion: Int, nowIso: String): ByteArray {
        val rnd = SecureRandom()
        val salt = ByteArray(SALT_LEN).also { rnd.nextBytes(it) }
        val iv = ByteArray(IV_LEN).also { rnd.nextBytes(it) }
        val header = JSONObject().apply {
            put("kdf", KDF)
            put("iter", ITER)
            put("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            put("iv", Base64.encodeToString(iv, Base64.NO_WRAP))
            put("schema_version", schemaVersion)
            put("created_at", nowIso)
            put("cipher", "aes-256-gcm")
        }
        val headerBytes = header.toString().toByteArray(Charsets.UTF_8)
        val key = deriveKey(password, salt, ITER)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        cipher.updateAAD(headerBytes)
        val ct = cipher.doFinal(payload.toByteArray(Charsets.UTF_8))

        val out = ByteArray(8 + 4 + headerBytes.size + ct.size)
        val magicBytes = MAGIC.toByteArray(Charsets.US_ASCII)
        System.arraycopy(magicBytes, 0, out, 0, 8)
        writeIntBE(out, 8, headerBytes.size)
        System.arraycopy(headerBytes, 0, out, 12, headerBytes.size)
        System.arraycopy(ct, 0, out, 12 + headerBytes.size, ct.size)
        return out
    }

    /** 解密并返回明文 payload；格式错误 / 口令错误 / 篡改均抛 VaultException。 */
    fun decrypt(password: CharArray, file: ByteArray): Decrypted {
        if (file.size < 12) throw VaultException("文件过短，不是有效的 ASHKB 备份")
        val magic = String(file, 0, 8, Charsets.US_ASCII)
        if (magic != MAGIC) throw VaultException("文件头标识不符（期望 $MAGIC）")
        val headerLen = readIntBE(file, 8)
        if (headerLen <= 0 || 12 + headerLen >= file.size) throw VaultException("文件头长度非法")
        val headerBytes = file.copyOfRange(12, 12 + headerLen)
        val ct = file.copyOfRange(12 + headerLen, file.size)
        val header = JSONObject(String(headerBytes, Charsets.UTF_8))
        if (header.optString("cipher") != "aes-256-gcm") throw VaultException("不支持的加密算法 ${header.optString("cipher")}")
        val kdf = header.optString("kdf")
        if (kdf != KDF) throw VaultException("不支持的 KDF：$kdf")
        val salt = Base64.decode(header.getString("salt"), Base64.NO_WRAP)
        val iv = Base64.decode(header.getString("iv"), Base64.NO_WRAP)
        val iter = header.getInt("iter")
        val key = deriveKey(password, salt, iter)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        cipher.updateAAD(headerBytes) // AAD 绑定明文头防跨协议改后缀（协议 §2）
        val plain = try {
            cipher.doFinal(ct)
        } catch (e: Exception) {
            throw VaultException("解密失败：口令错误或文件已损坏", e)
        }
        return Decrypted(
            payload = String(plain, Charsets.UTF_8),
            schemaVersion = header.optInt("schema_version", 0),
            createdAt = header.optString("created_at"),
        )
    }

    data class Decrypted(val payload: String, val schemaVersion: Int, val createdAt: String)

    private fun writeIntBE(buf: ByteArray, off: Int, v: Int) {
        buf[off] = (v ushr 24).toByte(); buf[off + 1] = (v ushr 16).toByte()
        buf[off + 2] = (v ushr 8).toByte(); buf[off + 3] = v.toByte()
    }

    private fun readIntBE(buf: ByteArray, off: Int): Int =
        ((buf[off].toInt() and 0xFF) shl 24) or ((buf[off + 1].toInt() and 0xFF) shl 16) or
            ((buf[off + 2].toInt() and 0xFF) shl 8) or (buf[off + 3].toInt() and 0xFF)
}
