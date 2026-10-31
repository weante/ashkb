package com.ashkb.app.data.backup

import androidx.annotation.StringRes
import org.json.JSONArray
import org.json.JSONObject
import com.ashkb.app.R
import com.ashkb.app.domain.Argon2id
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
    /** v3（v1.0.35）：密钥槽包装的是**稳定 vault key** 本身（而非每次随机 DEK）——见 encryptV3。 */
    const val MAGIC_V3 = "ASHKBAK3"
    /** 附件密文标识（与备份文件区分，防止误喂）。 */
    const val BLOB_MAGIC = "ASHKBATT"
    /**
     * 旧槽的 KDF 标识（v1.0.5–v1.2.12 写出的所有备份）。**永久保留**：
     * 那些文件只能用 PBKDF2 解，删掉这个分支等于让历史备份再也打不开。
     */
    private const val KDF = "pbkdf2-sha256"
    private const val ITER = 120_000
    /**
     * v1.2.13（审查报告 §十 第 3 条「备份威胁模型 = 不信任 WebDAV 服务商」）：新槽改用 Argon2id。
     *
     * 为什么换：PBKDF2-HMAC-SHA256 只吃 CPU，攻击者用 GPU/ASIC 摊薄成本的优势极大；
     * Argon2id 是 RFC 9106 的推荐算法，**内存硬**——每次猜测都要占一块内存，显卡的
     * 并行优势被内存带宽卡住，而用户自己只付一次（备份 / 恢复各一次）。
     *
     * 参数取 RFC 9106 §4 的**第二推荐档**（`m=19 MiB, t=2, p=1`，即「内存受限环境」推荐值）：
     * 本机 JVM 实测 30 ms（见 `Argon2idTest` 的性能实测用例），比原来的 PBKDF2 120k
     * （本机约 46 ms）还快，内存代价 19 MiB 对现代手机无压力。OWASP 的 `m=64 MiB, t=3, p=4`
     * 在同一台机器上 177 ms，放到中低端机可能到秒级，备份/恢复各一次尚可但没必要。
     *
     * 兼容性（**发布时必须在说明里写明**）：新备份的槽头写 `kdf=argon2id`，v1.2.12 及更早的
     * App 会报「不支持的 KDF」而**打不开新备份**——这是加密强度升级的固有代价，与 v1→v2→v3
     * 历次格式升级同一性质（新读旧永远可以，旧读新不行）。旧备份不受影响，仍按槽头声明的
     * `pbkdf2-sha256` 解。
     */
    private const val KDF_ARGON2ID = "argon2id"
    private const val ARGON2_MEMORY_KIB = 19_456
    private const val ARGON2_ITERATIONS = 2
    private const val ARGON2_PARALLELISM = 1
    /**
     * Argon2id 参数的**可接受区间**。与 [MIN_ITER]/[MAX_ITER] 同理：备份是外部输入，
     * 槽头的 `m` 直接决定要分配多少内存、跑多少轮——一个 `m=4 GiB` 的恶意备份能让恢复
     * 过程 OOM，`m=8` 又能把算法降级成几乎不设防。下界取 RFC 9106 对 p=1 的最低要求再留余量，
     * 上界取 256 MiB（远超生产者写的 19 MiB，但不至于把手机拖死）。
     */
    private const val ARGON2_MIN_MEMORY_KIB = 8_192
    private const val ARGON2_MAX_MEMORY_KIB = 262_144
    private const val ARGON2_MAX_ITERATIONS = 16
    private const val ARGON2_MAX_PARALLELISM = 8
    /**
     * v1.0.43：KDF 迭代次数的可接受区间。备份文件是**外部输入**，其头部的 `iter` 直接进 PBKDF2——
     * 一个 `iter = Int.MAX_VALUE` 的（口令已知的）恶意备份可让恢复过程长时间挂起（DoS）。
     * 下界防「iter=1 快速暴力」，上界防 DoS。
     */
    private const val MIN_ITER = 1_000
    private const val MAX_ITER = 10_000_000
    /**
     * v1.2.11（缺陷工单 C02）：密钥槽数量上限。
     *
     * 上面的 [MAX_ITER] 只约束了**单个槽**的 PBKDF2 代价，槽数量此前没有任何约束：
     * 每个槽都要跑一次 PBKDF2，而恢复码场景下候选秘密还会翻倍。一个塞进几百个槽的
     * 恶意备份因此能把恢复耗时线性放大到「看起来卡死」，与 `iter` DoS 是同一类问题。
     *
     * 生产格式只会写入「口令槽 + 恢复码槽」两个；取 16 是为了给未来可能的新槽类型
     * 留足兼容余量，同时把最坏情况钉在 16 × 2 = 32 次 PBKDF2。
     */
    internal const val MAX_SLOTS = 16
    private const val KEY_BITS = 256
    private const val BITS_PER_BYTE = 8
    /** Argon2id 输出长度必须与 PBKDF2 分支一致（32 字节）——两者都包成 AES-256 密钥。 */
    private const val KEY_BYTES = KEY_BITS / BITS_PER_BYTE
    private const val GCM_TAG_BITS = 128
    private const val SALT_LEN = 16
    private const val IV_LEN = 12
    private const val DEK_LEN = 32
    /** 稳定数据密钥长度（v3 / 附件共用） */
    const val VAULT_KEY_LEN = 32
    private const val SLOT_AAD_PREFIX = "ASHKBAK2:slot:"
    private const val SLOT_AAD_PREFIX_V3 = "ASHKBAK3:slot:"

    /**
     * v1.2.5 文案资源化：中文 [msg] **原样保留**——单测（VaultCipherTest）与日志按它断言/排查。
     * data 层没有 Context，故 [resId] / [resArgs] 交给 UI 侧 `getString` 解析成当前语言；
     * [resId] == 0 表示无对应资源，调用方回落 [msg]。
     */
    class VaultException(
        msg: String,
        cause: Throwable? = null,
        @StringRes val resId: Int = 0,
        val resArgs: List<Any> = emptyList(),
    ) : Exception(msg, cause)

    /**
     * 旧槽派生：PBKDF2-HMAC-SHA256。[kdf] 必须是 [KDF] 对应的算法。
     *
     * v1.2.13 起**只用于读取旧文件与生成回归样本**——生产写入路径改走 [deriveKeyFor]，
     * 它会按槽头的 `kdf` 选算法。保留本函数的签名是为了 `VaultCipherTest` 里那条
     * `deriveKey 可重复` 的断言继续成立（它验证的是 PBKDF2 分支本身仍工作）。
     */
    fun deriveKey(password: CharArray, salt: ByteArray, iter: Int): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, iter, KEY_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val key = factory.generateSecret(spec).encoded
        spec.clearPassword()
        return SecretKeySpec(key, "AES")
    }

    /**
     * Argon2id 派生：口令先按 UTF-8 编码成字节（Argon2 吃字节不吃 char），
     * 输出 [KEY_BYTES] 字节后包成 AES 密钥。
     *
     * ⚠️ **口令的 UTF-8 编码必须是稳定的**：它一旦随版本变化（例如某次改动把
     * `Charsets.UTF_8` 换掉、或对字符串先做了归一化），所有已写出的 Argon2 备份
     * 就会「口令正确也解不开」。所以这里不做 NFC/NFD 归一化、不 trim——用户
     * 输入什么就是什么，与 PBKDF2 分支的口径一致（`PBEKeySpec` 按 UTF-8 编码
     * char[]，见 JDK 文档「PBEKeySpec 使用的编码是 UTF-8」）。
     */
    private fun deriveKeyArgon2id(
        password: CharArray,
        salt: ByteArray,
        memoryKiB: Int,
        iterations: Int,
        parallelism: Int,
    ): SecretKeySpec {
        val tag = Argon2id.hash(
            password = String(password).toByteArray(Charsets.UTF_8),
            salt = salt,
            memoryKiB = memoryKiB,
            iterations = iterations,
            parallelism = parallelism,
            tagLength = KEY_BYTES,
        )
        return SecretKeySpec(tag, "AES")
    }

    /**
     * 按槽头的 `kdf` 标识派生密钥——**这是读侧唯一的派发点**。
     *
     * 两个分支各自套用自己那套参数的区间校验（PBKDF2 用 [MIN_ITER]..[MAX_ITER]，
     * Argon2id 用 ARGON2_* 三组区间），并且**未知 KDF 一律拒绝而不是回落 PBKDF2**：
     * 静默回落会让一个声称 argon2id 的文件被弱算法去解，攻击者只要伪造槽头就能
     * 把强度降回去。
     */
    private fun deriveKeyFor(slot: JSONObject, password: CharArray, salt: ByteArray, iter: Int): SecretKeySpec =
        when (val kdf = slot.optString("kdf")) {
            KDF -> deriveKey(password, salt, iter)
            KDF_ARGON2ID -> {
                if (iter !in 1..ARGON2_MAX_ITERATIONS) {
                    throw VaultException(
                        "文件头损坏：Argon2id 迭代次数越界",
                        resId = R.string.backup_err_vault_kdf_iter,
                    )
                }
                // m 与 p 必须在**分配内存之前**校验：m 直接决定要申请多少内存，
                // 一个 m=4 GiB 的伪造槽头足以让恢复过程 OOM。
                val (memory, lanes) = argon2ParametersOrThrow(slot)
                deriveKeyArgon2id(password, salt, memory, iter, lanes)
            }
            else -> throw VaultException(
                "不支持的 KDF：$kdf",
                resId = R.string.backup_err_vault_bad_kdf,
                resArgs = listOf(kdf),
            )
        }

    /** 槽头里的 Argon2id 内存（KiB）与并行度——两者都在区间内才放行。 */
    private fun argon2ParametersOrThrow(slot: JSONObject): Pair<Int, Int> {
        val memory = slot.optInt("m", -1)
        val lanes = slot.optInt("p", -1)
        if (memory !in ARGON2_MIN_MEMORY_KIB..ARGON2_MAX_MEMORY_KIB ||
            lanes !in 1..ARGON2_MAX_PARALLELISM
        ) {
            throw VaultException("密钥槽损坏", resId = R.string.backup_err_vault_slot_corrupt)
        }
        return memory to lanes
    }

    private fun b64(b: ByteArray): String = Base64.getEncoder().encodeToString(b)
    private fun b64d(s: String): ByteArray =
        runCatching { Base64.getDecoder().decode(s) }.getOrDefault(ByteArray(0))

    // ======================= v2：信封加密（当前生产路径） =======================

    /**
     * v2 加密。recoveryCode 非空 → 双槽（口令 / 恢复码任一可解）；空 → 仅口令槽。
     * 秘密错配的槽在解密时逐槽尝试失败即跳过，互不干扰。
     * ⚠️ 每次生成**随机 DEK**——附件不能用它（改口令即失效），附件走 [encryptV3] 的稳定密钥。
     */
    fun encrypt(pass: CharArray, recoveryCode: CharArray?, payload: String,
                schemaVersion: Int, nowIso: String): ByteArray {
        val dek = ByteArray(DEK_LEN).also { SecureRandom().nextBytes(it) }
        return envelope(MAGIC_V2, dek, pass, recoveryCode, payload, schemaVersion, nowIso)
    }

    /**
     * v3 加密（v1.0.35）：用**稳定的 vault key** 加密 payload，密钥槽包装的也是 vault key 本身。
     *
     * 为什么不让 DEK 每次随机：附件要上传到 WebDAV 长期保存，若用「某次备份的随机 DEK」加密，
     * 用户改口令 / 重新生成恢复码后旧附件全部解不开。改为稳定密钥后：
     *  - 改口令 = 只重包装密钥槽，所有密文（含云端附件）不用重传
     *  - 换机恢复 = 从槽里解出 vault key，DB 与附件一起可读
     * 安全性不降：槽仍由 PBKDF2(口令/恢复码) 保护，与 v2 同构。
     */
    fun encryptV3(vaultKey: ByteArray, pass: CharArray, recoveryCode: CharArray?, payload: String,
                  schemaVersion: Int, nowIso: String): ByteArray {
        require(vaultKey.size == VAULT_KEY_LEN) { "vault key 长度必须为 $VAULT_KEY_LEN 字节" }
        return envelope(MAGIC_V3, vaultKey, pass, recoveryCode, payload, schemaVersion, nowIso)
    }

    /** v2 / v3 共用的信封装配：key 被逐槽包装，payload 用 key 加密。 */
    private fun envelope(magic: String, key: ByteArray, pass: CharArray, recoveryCode: CharArray?,
                         payload: String, schemaVersion: Int, nowIso: String): ByteArray {
        val rnd = SecureRandom()
        val payloadIv = ByteArray(IV_LEN).also { rnd.nextBytes(it) }
        val prefix = slotAadPrefix(magic)

        val slots = JSONArray()
        wrapSlot(slots, rnd, key, "pass", pass, prefix)
        recoveryCode?.let { wrapSlot(slots, rnd, key, "recovery", it, prefix) }

        val header = JSONObject().apply {
            put("fmt", if (magic == MAGIC_V3) 3 else 2)
            put("cipher", "aes-256-gcm")
            put("schema_version", schemaVersion)
            put("created_at", nowIso)
            put("iv", b64(payloadIv))
            put("slots", slots)
        }
        val headerBytes = header.toString().toByteArray(Charsets.UTF_8)
        val ct = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, payloadIv))
            updateAAD(headerBytes) // AAD 绑定明文头防跨协议改头（协议 §2，与 v1 一致）
        }.doFinal(payload.toByteArray(Charsets.UTF_8))
        return pack(magic, headerBytes, ct)
    }

    /** 槽 AAD 前缀随 magic 变：v2 文件必须仍用 "ASHKBAK2:slot:" 才能解开（历史文件不可回改）。 */
    private fun slotAadPrefix(magic: String): String =
        if (magic == MAGIC_V3) SLOT_AAD_PREFIX_V3 else SLOT_AAD_PREFIX

    /**
     * 用槽秘密（口令或恢复码）包 key，追加进 slots 数组。
     *
     * v1.2.13 起新槽写 `kdf=argon2id` 并把 Argon2 的三个参数一并进头（`iter` / `m` / `p`）——
     * 与 PBKDF2 时代「参数进头」的协议约定一致（见类注释：KDF 升级可在新槽内无破坏切换，
     * 旧文件按头内参数解）。**注意 `iter` 在 Argon2id 下是遍数 t，不是 PBKDF2 的迭代次数**，
     * 同一个键复用是因为两者的语义都是「重复跑多少轮」，读侧按 `kdf` 分派才能区分。
     */
    private fun wrapSlot(slots: JSONArray, rnd: SecureRandom, key: ByteArray, id: String,
                         secret: CharArray, aadPrefix: String) {
        val salt = ByteArray(SALT_LEN).also { rnd.nextBytes(it) }
        val iv = ByteArray(IV_LEN).also { rnd.nextBytes(it) }
        val wrapped = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(
                Cipher.ENCRYPT_MODE,
                deriveKeyArgon2id(secret, salt, ARGON2_MEMORY_KIB, ARGON2_ITERATIONS, ARGON2_PARALLELISM),
                GCMParameterSpec(GCM_TAG_BITS, iv),
            )
            updateAAD((aadPrefix + id).toByteArray(Charsets.UTF_8)) // 槽 id 进 AAD 防交换
        }.doFinal(key)
        slots.put(JSONObject().apply {
            put("id", id)
            put("kdf", KDF_ARGON2ID)
            put("iter", ARGON2_ITERATIONS)
            put("m", ARGON2_MEMORY_KIB)
            put("p", ARGON2_PARALLELISM)
            put("salt", b64(salt))
            put("iv", b64(iv))
            put("wrapped", b64(wrapped))
        })
    }

    // ======================= 解密（v1/v2 统一入口，永久兼容） =======================

    /** 解密并返回明文 payload；格式错误 / 口令与恢复码均不匹配 / 篡改均抛 VaultException。 */
    fun decrypt(secret: CharArray, file: ByteArray): Decrypted {
        if (file.size < 12) throw VaultException("文件过短，不是有效的 ASHKB 备份", resId = R.string.backup_err_vault_too_short)
        val magic = String(file, 0, 8, Charsets.US_ASCII)
        if (magic != MAGIC_V1 && magic != MAGIC_V2 && magic != MAGIC_V3)
            throw VaultException(
                "文件头标识不符（期望 $MAGIC_V1/$MAGIC_V2/$MAGIC_V3）",
                resId = R.string.backup_err_vault_magic,
                resArgs = listOf(MAGIC_V1, MAGIC_V2, MAGIC_V3),
            )
        val headerLen = readIntBE(file, 8)
        if (headerLen <= 0 || 12 + headerLen >= file.size)
            throw VaultException("文件头长度非法", resId = R.string.backup_err_vault_header_len)
        val headerBytes = file.copyOfRange(12, 12 + headerLen)
        val ct = file.copyOfRange(12 + headerLen, file.size)
        val header = try {
            JSONObject(String(headerBytes, Charsets.UTF_8))
        } catch (e: Exception) {
            throw VaultException("文件头损坏", e, resId = R.string.backup_err_vault_header_corrupt)
        }
        if (header.optString("cipher") != "aes-256-gcm")
            throw VaultException(
                "不支持的加密算法 ${header.optString("cipher")}",
                resId = R.string.backup_err_vault_bad_cipher,
                resArgs = listOf(header.optString("cipher")),
            )
        return if (magic == MAGIC_V1) decryptV1(secret, header, headerBytes, ct)
        else decryptSlotted(magic, secret, header, headerBytes, ct)
    }

    /** v1：口令直接派生 payload 密钥（历史文件，只读兼容）。 */
    private fun decryptV1(secret: CharArray, header: JSONObject, headerBytes: ByteArray, ct: ByteArray): Decrypted {
        if (header.optString("kdf") != KDF)
            throw VaultException(
                "不支持的 KDF：${header.optString("kdf")}",
                resId = R.string.backup_err_vault_bad_kdf,
                resArgs = listOf(header.optString("kdf")),
            )
        val salt: ByteArray; val iv: ByteArray; val iter: Int
        try {
            salt = Base64.getDecoder().decode(header.getString("salt"))
            iv = Base64.getDecoder().decode(header.getString("iv"))
            iter = header.getInt("iter")
        } catch (e: Exception) {
            throw VaultException("文件头损坏", e, resId = R.string.backup_err_vault_header_corrupt)
        }
        // v1.0.43：v1 分支此前完全不校验 salt / iv 长度与 iter 范围（v2/v3 槽已校验）
        if (salt.size != SALT_LEN || iv.size != IV_LEN)
            throw VaultException("文件头损坏", resId = R.string.backup_err_vault_header_corrupt)
        if (iter !in MIN_ITER..MAX_ITER)
            throw VaultException("文件头损坏：KDF 迭代次数越界", resId = R.string.backup_err_vault_kdf_iter)
        val key = try {
            deriveKey(secret, salt, iter)
        } catch (e: Exception) {
            throw VaultException("文件头损坏", e, resId = R.string.backup_err_vault_header_corrupt)
        }
        val plain = try {
            Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
                updateAAD(headerBytes)
            }.doFinal(ct)
        } catch (e: Exception) {
            throw VaultException("解密失败：口令错误或文件已损坏", e, resId = R.string.backup_err_vault_bad_pass)
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

    /**
     * v2 / v3 解密：逐槽尝试解包密钥（任一槽秘密匹配即通过），再用该密钥解 payload。
     * v2 解出的是该文件的随机 DEK；v3 解出的是稳定 vault key（随 Decrypted.key 一并返回，
     * 供恢复后写入 Keystore——附件与后续备份都用它）。
     */
    private fun decryptSlotted(magic: String, secret: CharArray, header: JSONObject,
                               headerBytes: ByteArray, ct: ByteArray): Decrypted {
        val slots = header.optJSONArray("slots")
            ?: throw VaultException("文件头缺少密钥槽", resId = R.string.backup_err_vault_slot_missing)
        // v1.2.11（缺陷工单 C02）：**在任何 PBKDF2 之前**校验槽数量——这道闸必须在
        // 下面 `for (cand in candidates(secret))` 的双层循环之前，否则恶意文件已经把
        // 机器拖住才报错，等于没设防。
        if (slots.length() > MAX_SLOTS)
            throw VaultException(
                "密钥槽数量异常：${slots.length()} > $MAX_SLOTS",
                resId = R.string.backup_err_vault_too_many_slots,
                resArgs = listOf(slots.length(), MAX_SLOTS),
            )
        val prefix = slotAadPrefix(magic)
        var dek: ByteArray? = null
        outer@ for (cand in candidates(secret)) {
            for (i in 0 until slots.length()) {
                val slot = slots.optJSONObject(i) ?: continue
                val salt = b64d(slot.optString("salt"))
                val iv = b64d(slot.optString("iv"))
                val wrapped = b64d(slot.optString("wrapped"))
                val iter = slot.optInt("iter", -1)
                if (salt.size != SALT_LEN || iv.size != IV_LEN || wrapped.isEmpty()) {
                    throw VaultException("密钥槽损坏", resId = R.string.backup_err_vault_slot_corrupt)
                }
                // v1.2.13：KDF 与参数区间校验都在 deriveKeyFor 内按槽头声明的算法分派——
                // 不能在这里统一套 MIN_ITER..MAX_ITER，Argon2id 的 t 只有 2，会被误判成损坏。
                val slotKey = deriveKeyFor(slot, cand, salt, iter)
                val unwrapped = try {
                    Cipher.getInstance("AES/GCM/NoPadding").apply {
                        init(Cipher.DECRYPT_MODE, slotKey, GCMParameterSpec(GCM_TAG_BITS, iv))
                        updateAAD((prefix + slot.optString("id")).toByteArray(Charsets.UTF_8))
                    }.doFinal(wrapped)
                } catch (_: Exception) {
                    null // 本槽秘密不匹配——继续试下一槽
                }
                if (unwrapped != null && unwrapped.size == DEK_LEN) { dek = unwrapped; break@outer }
            }
        }
        val key = dek ?: throw VaultException(
            "解密失败：口令或恢复码错误，或文件已损坏",
            resId = R.string.backup_err_vault_bad_pass_or_code,
        )
        val payloadIv = b64d(header.optString("iv"))
        if (payloadIv.size != IV_LEN) throw VaultException("文件头损坏", resId = R.string.backup_err_vault_header_corrupt)
        val plain = try {
            Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, payloadIv))
                updateAAD(headerBytes)
            }.doFinal(ct)
        } catch (e: Exception) {
            throw VaultException("解密失败：文件已损坏（密钥槽通过但内容认证失败）", e, resId = R.string.backup_err_vault_auth)
        }
        return Decrypted(
            payload = String(plain, Charsets.UTF_8),
            schemaVersion = header.optInt("schema_version", 0),
            createdAt = header.optString("created_at"),
            // v3 的密钥就是 vault key；v2 的是该文件私有 DEK（不应当作 vault key 采纳）
            vaultKey = if (magic == MAGIC_V3) key else null,
        )
    }

    // ======================= 附件密文（v1.0.35） =======================

    /**
     * 附件密文：[8B "ASHKBATT"][12B iv][ct+tag]，AES-256-GCM(key=vault key, AAD=附件 id)。
     * AAD 绑定附件 id —— 即便有人能写服务器，也无法把 A 的密文冒充成 B。
     */
    fun encryptBlob(vaultKey: ByteArray, plain: ByteArray, attachmentId: String): ByteArray {
        require(vaultKey.size == VAULT_KEY_LEN) { "vault key 长度必须为 $VAULT_KEY_LEN 字节" }
        val iv = ByteArray(IV_LEN).also { SecureRandom().nextBytes(it) }
        val ct = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(vaultKey, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
            updateAAD(attachmentId.toByteArray(Charsets.UTF_8))
        }.doFinal(plain)
        val out = ByteArray(8 + IV_LEN + ct.size)
        System.arraycopy(BLOB_MAGIC.toByteArray(Charsets.US_ASCII), 0, out, 0, 8)
        System.arraycopy(iv, 0, out, 8, IV_LEN)
        System.arraycopy(ct, 0, out, 8 + IV_LEN, ct.size)
        return out
    }

    /** 解密附件密文；标识不符 / 密钥错 / 篡改 / id 不匹配均抛 VaultException。 */
    fun decryptBlob(vaultKey: ByteArray, blob: ByteArray, attachmentId: String): ByteArray {
        if (blob.size <= 8 + IV_LEN) throw VaultException("附件密文过短", resId = R.string.backup_err_vault_blob_short)
        val magic = String(blob, 0, 8, Charsets.US_ASCII)
        if (magic != BLOB_MAGIC)
            throw VaultException(
                "附件密文标识不符（期望 $BLOB_MAGIC）",
                resId = R.string.backup_err_vault_blob_magic,
                resArgs = listOf(BLOB_MAGIC),
            )
        val iv = blob.copyOfRange(8, 8 + IV_LEN)
        val ct = blob.copyOfRange(8 + IV_LEN, blob.size)
        return try {
            Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, SecretKeySpec(vaultKey, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
                updateAAD(attachmentId.toByteArray(Charsets.UTF_8))
            }.doFinal(ct)
        } catch (e: Exception) {
            throw VaultException("附件解密失败：密钥不匹配或文件已损坏", e, resId = R.string.backup_err_vault_blob_decrypt)
        }
    }

    data class Decrypted(
        val payload: String,
        val schemaVersion: Int,
        val createdAt: String,
        /** 仅 v3 非空：解出的稳定 vault key（恢复时应采纳到 Keystore）。 */
        val vaultKey: ByteArray? = null,
    )

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
