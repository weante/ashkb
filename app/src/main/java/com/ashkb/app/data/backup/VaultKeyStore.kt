package com.ashkb.app.data.backup

import android.content.Context
import android.content.SharedPreferences
import java.security.SecureRandom
import java.util.Base64

/**
 * 稳定 vault key（v1.0.35）：备份 payload 与**所有附件**共用的长期数据密钥。
 *
 * 为什么要稳定：附件要长期存在 WebDAV 上。若用「每次备份新生成的随机 DEK」加密附件，
 * 用户改口令 / 重新生成恢复码后旧附件全部解不开；且同一附件可能被不同密钥加密而无法管理。
 *
 * 存法：32 字节随机密钥 → Base64 → [KeystoreCipher]（AndroidKeyStore AES-256-GCM）加密 →
 * 落 `vault_config` prefs（与恢复码同一文件、同一保护方式）。Keystore 密钥不可导出，
 * 拿到 prefs 文件也无法离线解出。
 *
 * 随备份携带：v3 备份文件的密钥槽包装的就是这把 key（口令槽 / 恢复码槽各一份），
 * 换机恢复时 [adoptIfAbsent] 把它收回本机。
 */
class VaultKeyStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("vault_config", Context.MODE_PRIVATE)

    companion object {
        private const val PREF_KEY = "vault_key_v1"
        private const val KEY_LEN = 32
    }

    /** 读本机密钥；未生成或 Keystore 解密失败返回 null。 */
    fun current(): ByteArray? =
        prefs.getString(PREF_KEY, null)
            ?.let { runCatching { KeystoreCipher.decryptFromB64(it) }.getOrNull() }
            ?.let { runCatching { Base64.getDecoder().decode(it) }.getOrNull() }
            ?.takeIf { it.size == KEY_LEN }

    /** 取密钥；不存在则生成一次并落盘。**永不更换**——换了云端附件就全废。 */
    fun getOrCreate(): ByteArray = current() ?: generate()

    /** 生成新密钥并覆盖落盘（仅测试 / 极端重置场景使用，正常路径不要调用）。 */
    fun generate(): ByteArray {
        val key = ByteArray(KEY_LEN).also { SecureRandom().nextBytes(it) }
        prefs.edit()
            .putString(PREF_KEY, KeystoreCipher.encryptToB64(Base64.getEncoder().encodeToString(key)))
            .apply()
        return key
    }

    /**
     * 恢复时采纳备份里的密钥——**仅当本机没有**。
     *
     * 刻意不覆盖已有密钥：一次旧备份恢复若把本机密钥冲掉，当前设备上已上传的附件会立刻解不开。
     * 两者不一致说明是两台设备的密钥（如先在 A 备份、又在 B 上建了新附件），
     * 此时以本机为准并返回 false，由调用方决定是否提示用户。
     *
     * @return true = 已采纳（本机此前没有密钥）；false = 本机已有且（可能）不同，未改动
     */
    fun adoptIfAbsent(key: ByteArray): Boolean {
        if (key.size != KEY_LEN) return false
        if (current() != null) return false
        prefs.edit()
            .putString(PREF_KEY, KeystoreCipher.encryptToB64(Base64.getEncoder().encodeToString(key)))
            .apply()
        return true
    }
}
