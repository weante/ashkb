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
class VaultKeyStore(
    context: Context,
    /**
     * v1.2.21（独立审查 F4）：加解密函数可注入，**仅为单测**。
     *
     * [KeystoreCipher] 依赖 AndroidKeyStore，JVM 上直接调用会抛 `KeyStoreException:
     * AndroidKeyStore not found`，于是 adopt / rollback 这两个真正需要验证的语义此前零覆盖。
     * 生产调用点（`BackupRepository`、`AshkbApplication`）都不传参，行为完全不变。
     */
    private val encrypt: (String) -> String = KeystoreCipher::encryptToB64,
    private val decrypt: (String) -> String = KeystoreCipher::decryptFromB64,
) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("vault_config", Context.MODE_PRIVATE)

    companion object {
        private const val PREF_KEY = "vault_key_v1"
        private const val KEY_LEN = 32
    }

    /** 本机密钥状态（v1.0.43）：区分「本机从未生成」与「有密文但读不出」。 */
    enum class KeyState {
        /** 本机没有密钥——首次使用，可以安全生成 */
        ABSENT,

        /** 本机有且可解出 */
        PRESENT,

        /** 本机有密文但解不开（Keystore 密钥丢失 / 损坏）——**绝不能覆盖** */
        UNREADABLE,
    }

    private fun decode(raw: String): ByteArray? =
        runCatching { decrypt(raw) }.getOrNull()
            ?.let { runCatching { Base64.getDecoder().decode(it) }.getOrNull() }
            ?.takeIf { it.size == KEY_LEN }

    /** 读本机密钥；未生成**或** Keystore 解密失败均返回 null（如需区分请用 [state]）。 */
    fun current(): ByteArray? = prefs.getString(PREF_KEY, null)?.let { decode(it) }

    fun state(): KeyState {
        val raw = prefs.getString(PREF_KEY, null) ?: return KeyState.ABSENT
        return if (decode(raw) != null) KeyState.PRESENT else KeyState.UNREADABLE
    }

    /**
     * 取密钥用于加密；返回 null 表示**本机有密钥但读不出**，调用方必须明确报错。
     *
     * v1.0.43 修复：原先 `current() ?: generate()` 把「本机没有」与「Keystore 解密失败」都折叠成
     * 生成新密钥并**覆盖落盘**——于是系统密钥库一旦失效，云端已上传的附件会全部变成解不开的密文，
     * 而用户毫无察觉（与「永不更换」的设计承诺相悖）。
     * 现在的处置：`UNREADABLE` 时返回 null，由调用方报错；用户恢复一份 v3 备份即可修复
     * （[adoptIfAbsent] 在本机读不出时会采纳备份里的密钥）。
     */
    fun getOrCreate(): ByteArray? = when (state()) {
        KeyState.PRESENT -> current()
        KeyState.ABSENT -> generate()
        KeyState.UNREADABLE -> null
    }

    /**
     * 生成新密钥并覆盖落盘。**仅在本机从未有过密钥时可用**（正常路径只经 [getOrCreate] 的
     * `ABSENT` 分支到达）。
     *
     * v1.0.44（S3）：把「不要覆盖已有密钥」从注释升级为**机器约束**。本方法是「覆盖落盘」语义，
     * 与 B5 修掉的是同一类威胁——静默覆盖会让云端已上传的附件永久不可解；同一文件里留一个
     * 无防护的覆盖入口，逻辑上自相矛盾。密钥已存在或读不出时一律拒绝（读不出时也拒绝：
     * 覆盖会毁掉唯一可恢复的密文）。
     */
    fun generate(): ByteArray {
        require(state() == KeyState.ABSENT) {
            "本机已存在 vault key（或存在但读不出），拒绝覆盖：覆盖后云端已上传的附件将永久不可解。" +
                "如需取回密钥，请恢复一份本机或云端的 v3 备份（见 adoptIfAbsent）。"
        }
        val key = ByteArray(KEY_LEN).also { SecureRandom().nextBytes(it) }
        prefs.edit()
            .putString(PREF_KEY, encrypt(Base64.getEncoder().encodeToString(key)))
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
     * v1.0.43：本机为 [KeyState.UNREADABLE]（读不出）时 `current()` 为 null，此处会采纳备份里的
     * 密钥——这正是 Keystore 失效后的**唯一修复路径**，属预期行为。
     *
     * @return true = 已采纳（本机此前没有可用密钥）；false = 本机已有可用密钥，未改动
     */
    fun adoptIfAbsent(key: ByteArray): Boolean {
        if (key.size != KEY_LEN) return false
        if (current() != null) return false
        prefs.edit()
            .putString(PREF_KEY, encrypt(Base64.getEncoder().encodeToString(key)))
            .apply()
        return true
    }

    /**
     * v1.2.21（独立审查 F4）：撤销一次**本次恢复采纳的**密钥。
     *
     * 为什么需要：换机恢复时 [adoptIfAbsent] 会在写库**之前**把备份的密钥收进本机（顺序不能反，
     * 否则 pre-restore 快照没密钥可加密）。可一旦随后的写库失败，数据库整体回滚、密钥却留下了：
     * 本机从此被锁进「备份那把 key」的密钥域，此后本机自己备的份、本机附件全用新 key，
     * 而原设备的备份进来一律判 [VaultKeyDomain.Decision.MISMATCH] 被拒，[generate] 又拒绝覆盖，
     * App 内没有任何清除出口 —— 用户只能清应用数据。
     *
     * 所以「采纳」必须与「恢复成功」同生共死：失败时把这次采纳的密钥退回去。
     *
     * @param key 本次采纳的那把密钥（必须与调用 [adoptIfAbsent] 时传入的同一实例）
     * @return true = 已撤销；false = 未撤销（本机当前密钥不是它，说明期间被别处改过，不能动）
     */
    fun rollbackAdoptionIfCurrent(key: ByteArray): Boolean {
        val now = current() ?: return false
        if (!now.contentEquals(key)) return false
        prefs.edit().remove(PREF_KEY).apply()
        return true
    }
}
