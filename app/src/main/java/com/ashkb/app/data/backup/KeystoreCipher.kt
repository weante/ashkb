package com.ashkb.app.data.backup

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Android Keystore AES-256-GCM：本机敏感凭据（WebDAV 账号密码）加密落盘。
 * 密钥生成并保存在 AndroidKeyStore 硬件安全模块中，不可导出——
 * 拿到备份的 SharedPreferences 文件也无法离线解密（对应审查报告 P0）。
 */
object KeystoreCipher {
    private const val KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "ashkb_prefs_key"
    private const val IV_LEN = 12
    private const val TAG_BITS = 128

    /** 取密钥；不存在则生成（首次使用时落 Keystore）。 */
    private fun key(): SecretKey {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (ks.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        kg.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return kg.generateKey()
    }

    /** 加密：输出 iv(12B) || ciphertext+tag（Base64 NO_WRAP）。 */
    fun encryptToB64(plain: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val blob = cipher.iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(blob, Base64.NO_WRAP)
    }

    /** 解密 Base64(iv || ct)；口令或密文错误抛 AEADBadTag 异常由调用方兜底。 */
    fun decryptFromB64(b64: String): String {
        val blob = Base64.decode(b64, Base64.NO_WRAP)
        require(blob.size > IV_LEN) { "密文长度不合法" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE, key(),
            GCMParameterSpec(TAG_BITS, blob.copyOfRange(0, IV_LEN)),
        )
        return String(cipher.doFinal(blob.copyOfRange(IV_LEN, blob.size)), Charsets.UTF_8)
    }
}
