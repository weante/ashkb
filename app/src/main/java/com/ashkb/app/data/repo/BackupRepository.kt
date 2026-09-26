package com.ashkb.app.data.repo

import android.content.Context
import android.content.SharedPreferences
import com.ashkb.app.data.backup.BackupEngine
import com.ashkb.app.data.backup.KeystoreCipher
import com.ashkb.app.data.backup.VaultCipher
import com.ashkb.app.data.backup.VaultKeyStore
import com.ashkb.app.data.backup.WebDavClient
import com.ashkb.app.data.db.AppDatabase
import com.ashkb.app.data.db.AttachmentSyncCounts
import com.ashkb.app.data.db.Ids
import com.ashkb.app.data.entity.BackupLedger
import com.ashkb.app.data.entity.CheckupAttachment
import com.ashkb.app.data.entity.LedgerStatus
import com.ashkb.app.data.entity.LedgerType
import com.ashkb.app.domain.AttachmentPath
import com.ashkb.app.domain.RecoveryCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * P4 备份仓库（R20）：编排 BackupEngine + VaultCipher + WebDavClient + 备份台账。
 * 全量备份文件名遵循协议：本机导出 ashkb-YYYYMMDD.ashkb，WebDAV ashkb-backup-YYYY-MM-DD-HHmmss.ashkb（X2 带时间戳，同天多份不覆盖）。
 */
class BackupRepository(private val context: Context) {
    private val db = AppDatabase.get(context)
    private val ledgerDao = db.backupLedgerDao()

    /** v1.0.35：稳定 vault key（备份 payload 与附件共用）与附件同步所需的本地读写。 */
    private val vaultKeys = VaultKeyStore(context)
    private val attachments = AttachmentRepository(context)

    private val prefs: SharedPreferences =
        context.getSharedPreferences("webdav_config", Context.MODE_PRIVATE)

    private fun nowIso(): String = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

    fun observeLedger(): Flow<List<BackupLedger>> = ledgerDao.observeRecent()

    // ---- WebDAV 配置（凭据经 Android Keystore AES-256-GCM 加密落盘，审查 P0） ----
    fun webdavConfig(): Triple<String, String, String> {
        // 一次性迁移：v1.0.5 及以前明文存储的凭据 → Keystore 加密后删除旧键
        if (prefs.contains("url") || prefs.contains("user") || prefs.contains("pass")) {
            val old = Triple(
                prefs.getString("url", "") ?: "",
                prefs.getString("user", "") ?: "",
                prefs.getString("pass", "") ?: "",
            )
            // R3：只迁移 https:// 的旧明文配置；其余（http:// 或畸形 scheme）一律删除即失效
            if (old.first.trim().startsWith("https://", ignoreCase = true)) {
                saveWebdavConfig(old.first, old.second, old.third)
            }
            prefs.edit().remove("url").remove("user").remove("pass").apply()
        }
        fun readEnc(key: String): String {
            val v = prefs.getString(key, null) ?: return ""
            return runCatching { KeystoreCipher.decryptFromB64(v) }.getOrDefault("")
        }
        return Triple(readEnc("url_v2"), readEnc("user_v2"), readEnc("pass_v2"))
    }

    /**
     * R3：强制 HTTPS。v1.0.43 改为**白名单**——原先只拦 `http://`，于是 `ftp://`、畸形 URL
     * 全部放行（Basic 凭据会照发）。
     */
    private fun requireHttps(url: String) {
        if (!url.trim().startsWith("https://", ignoreCase = true))
            throw WebDavClient.DavException("仅支持 https:// 地址——明文 http 会把账号密码暴露给链路上任何人")
    }

    fun saveWebdavConfig(url: String, user: String, pass: String) {
        requireHttps(url) // R3：http:// 配置不入库
        prefs.edit()
            .putString("url_v2", KeystoreCipher.encryptToB64(url))
            .putString("user_v2", KeystoreCipher.encryptToB64(user))
            .putString("pass_v2", KeystoreCipher.encryptToB64(pass))
            .apply()
    }

    fun webdavConfigured(): Boolean = webdavConfig().first.isNotBlank()

    // ---- 备份恢复码（规划 A2，v1.0.27）：Keystore AES-256-GCM 加密落盘，同 WebDAV 凭据模式 ----
    private val vaultPrefs: SharedPreferences =
        context.getSharedPreferences("vault_config", Context.MODE_PRIVATE)

    /**
     * v1.0.43 修复：原先只看 `prefs.contains(...)`，于是 Keystore 异常（能读到一个解不开的密文）时
     * 界面显示「已设置恢复码」，但 [recoverySlot] 返回 null → 备份**实际只写了口令槽**，
     * 用户以为有退路，直到忘掉口令那一刻才发现没有。改为以「能真正解出非空码」为准。
     */
    fun hasRecoveryCode(): Boolean = recoveryCode() != null

    /** 读出恢复码（分组形态）；未设置或 Keystore 解密失败返回 null。 */
    fun recoveryCode(): String? =
        vaultPrefs.getString("recovery_code_v1", null)
            ?.let { runCatching { KeystoreCipher.decryptFromB64(it) }.getOrNull() }
            ?.takeIf { it.isNotBlank() }

    /** 生成并落盘新恢复码（覆盖旧码：旧码对既有旧备份仍有效，但不再用于新备份）。 */
    fun generateRecoveryCode(): String {
        val code = RecoveryCode.generate()
        vaultPrefs.edit()
            .putString("recovery_code_v1", KeystoreCipher.encryptToB64(code))
            .apply()
        return code
    }

    /** 备份加密用：已设恢复码则带恢复码槽（口令 / 恢复码任一可解），否则仅口令槽。
     *  槽秘密统一用归一化形态（去连字符大写 32 字符）——解密侧对用户任意抄写形态归一化后即可命中。 */
    private fun recoverySlot(): CharArray? =
        recoveryCode()?.let { RecoveryCode.normalize(it).toCharArray() }

    // ---- 台账 ----
    private suspend fun log(type: LedgerType, ok: Boolean, target: String,
                            fileName: String? = null, rows: Int? = null,
                            verifyOk: Boolean? = null, detail: String? = null) {
        ledgerDao.insert(
            BackupLedger(
                id = Ids.new("led"), ledgerType = type.name,
                status = if (ok) LedgerStatus.SUCCESS.name else LedgerStatus.FAILED.name,
                target = target, fileName = fileName, rowTotal = rows,
                verifyOk = verifyOk, detail = detail?.take(500), createdAt = nowIso(),
            )
        )
    }

    private fun supportDb() = db.openHelper.writableDatabase

    /**
     * 取稳定 vault key 用于加密。
     *
     * v1.0.43：本机有密钥但**读不出**（Keystore 失效）时明确报错，而不是生成新密钥覆盖——
     * 后者会让云端已上传的附件永久不可解且用户毫无察觉。恢复一份 v3 备份即可取回密钥。
     */
    private fun vaultKeyOrThrow(): ByteArray = vaultKeys.getOrCreate()
        ?: throw BackupEngine.BackupException(
            "本机附件密钥无法解密（系统密钥库异常）。为避免云端已上传的附件永久不可解，已停止生成新密钥。" +
                "请先恢复一份本机或云端的 v3 备份以取回密钥。"
        )

    // ======================= 本机备份 =======================

    class BackupOutcome(val file: File, val rowTotal: Int, val tableCount: Int, val sha256: String)

    /** 全量加密备份到本机（协议 §3 全量导出：ashkb-YYYYMMDD.ashkb）。 */
    suspend fun backupLocal(password: CharArray): BackupOutcome = withContext(Dispatchers.IO) {
        val exported = BackupEngine.export(supportDb(), nowIso())
        // v3：用稳定 vault key 加密（附件与备份同密钥域，换机后附件才解得开）
        val bytes = VaultCipher.encryptV3(vaultKeyOrThrow(), password, recoverySlot(), exported.payload,
            BackupEngine.schemaVersion(supportDb()), nowIso())
        val dir = File(context.filesDir, "backups").apply { mkdirs() }
        // 同日多次备份保留时分秒后缀
        val name = "ashkb-${LocalDate.now()}" +
            if (dir.resolve("ashkb-${LocalDate.now()}.ashkb").exists())
                "-${LocalDateTime.now().format(DateTimeFormatter.ofPattern("HHmmss"))}" else ""
        val f = dir.resolve("$name.ashkb")
        f.writeBytes(bytes)
        log(LedgerType.BACKUP, true, "local", f.name, exported.rowTotal, null,
            "${exported.tableCount} 表 ${exported.rowTotal} 行，AES-256-GCM")
        BackupOutcome(f, exported.rowTotal, exported.tableCount, BackupEngine.sha256Hex(bytes))
    }

    // ======================= WebDAV =======================

    /** WebDAV 立即备份：导出 → 加密 → 上传 → 回读校验 → 轮换（协议 §4 全链路）。
     *  W1：onStage 阶段回调驱动 UI 实时反馈；withTimeout 总兜底防任何未预期挂起。 */
    suspend fun backupWebdav(
        password: CharArray,
        onStage: (String) -> Unit = {},
    ): String = withContext(Dispatchers.IO) {
        withTimeout(120_000) {
        val (url, user, pass) = webdavConfig()
        if (url.isBlank()) throw WebDavClient.DavException("未配置 WebDAV 服务器")
        requireHttps(url) // R3：旧版本存的 http:// 配置给出明确报错，而非网络层异常
        val client = WebDavClient(url, user, pass)
        onStage("正在导出数据库快照…")
        val exported = BackupEngine.export(supportDb(), nowIso())
        onStage("正在加密（AES-256-GCM）…")
        val bytes = VaultCipher.encryptV3(vaultKeyOrThrow(), password, recoverySlot(), exported.payload,
            BackupEngine.schemaVersion(supportDb()), nowIso())
        // X2：文件名带时间戳——同一天多次备份不再互相覆盖（旧按日命名 PUT 同名即覆盖）
        val name = "ashkb-backup-" +
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss")) +
            ".ashkb"
        try {
            onStage("正在上传到 WebDAV…")
            val upMsg = client.upload(name, bytes)
            onStage("正在清理过期备份…")
            val removed = client.rotate()
            log(LedgerType.BACKUP, true, "webdav", name, exported.rowTotal, true,
                "$upMsg；轮换清理 ${removed.size} 份过期备份")
            "WebDAV 备份成功：$name（${exported.rowTotal} 行）。$upMsg" +
                if (removed.isEmpty()) "" else "；清理过期 ${removed.size} 份"
        } catch (e: Exception) {
            log(LedgerType.BACKUP, false, "webdav", name, null, false, e.message)
            throw e
        }
        }
    }

    suspend fun probeWebdav(url: String, user: String, pass: String): String =
        withContext(Dispatchers.IO) {
            requireHttps(url) // R3：先于任何网络请求拦截明文 http
            WebDavClient(url, user, pass).probe()
        }

    // ---- W4：WebDAV 远程恢复 ----

    /** 列出服务器 /ashkb/backup/ 下的备份；失败直接抛出——恢复场景用户须知道原因。 */
    suspend fun listWebdavBackups(): List<WebDavClient.DavBackupFile> =
        withContext(Dispatchers.IO) {
            val (url, user, pass) = webdavConfig()
            if (url.isBlank()) throw WebDavClient.DavException("未配置 WebDAV 服务器")
            requireHttps(url)
            WebDavClient(url, user, pass).listBackupFiles()
        }

    /** 下载指定远程备份（随后走既有五步恢复：旁路解密 → pre-restore 快照 → 覆盖写入）。 */
    suspend fun downloadWebdavBackup(name: String): ByteArray =
        withContext(Dispatchers.IO) {
            // name 来自刚拉取的远程列表，拼 URL 前再守一道：只放行本应用备份文件名形状
            if (!name.startsWith("ashkb-backup-") || !name.endsWith(".ashkb") ||
                name.contains('/') || name.contains('?')
            ) throw WebDavClient.DavException("非法备份文件名：$name")
            val (url, user, pass) = webdavConfig()
            if (url.isBlank()) throw WebDavClient.DavException("未配置 WebDAV 服务器")
            requireHttps(url)
            WebDavClient(url, user, pass).download(name)
        }

    // ======================= 恢复 =======================

    class DecryptedFile(val payload: String, val schemaVersion: Int, val createdAt: String)

    /** 旁路解密 + 文件自校验（协议 §6 第 1/3 步：不动主库）。 */
    suspend fun decryptAndSelfCheck(bytes: ByteArray, password: CharArray): DecryptedFile =
        withContext(Dispatchers.IO) {
            val d = VaultCipher.decrypt(password, bytes)
            // v1.0.35：v3 备份携带稳定 vault key——本机没有才采纳（不覆盖本机已有密钥，
            // 否则一次旧备份恢复会把本机附件密钥冲掉，已上传的附件立刻解不开）
            d.vaultKey?.let { vaultKeys.adoptIfAbsent(it) }
            val root = JSONObject(d.payload)
            if (root.optString("format") != "ashkb-full") throw BackupEngine.BackupException("备份格式不正确")
            if (root.optInt("schema_version", 0) > BackupEngine.schemaVersion(supportDb()))
                throw BackupEngine.BackupException("备份 schema 高于当前 APP 版本，请先升级")
            // 文件内自校验：payload 行重新序列化后与 manifest 比对
            val manifest = root.getJSONObject("manifest")
            val tables = root.getJSONObject("tables")
            for (t in manifest.keys().asSequence().toList()) {
                val rows = mutableListOf<String>()
                val arr = tables.getJSONArray(t)
                for (i in 0 until arr.length()) rows.add(arr.getJSONObject(i).toString())
                val m = manifest.getJSONObject(t)
                if (rows.size != m.getInt("rows"))
                    throw BackupEngine.BackupException("文件自校验失败：$t 行数 ${rows.size} ≠ manifest ${m.getInt("rows")}")
                val sha = BackupEngine.sha256Hex(rows.sorted().joinToString("\n").toByteArray())
                if (sha != m.getString("sha256"))
                    throw BackupEngine.BackupException("文件自校验失败：$t SHA-256 不匹配（文件可能损坏）")
            }
            DecryptedFile(d.payload, d.schemaVersion, d.createdAt)
        }

    /**
     * 恢复五步（协议 §6）：pre-restore 快照 → 解密校验（已由 decryptAndSelfCheck 完成）→
     * 覆盖写入 → 双校验 → 台账登记。
     *
     * @param snapshotPassword pre-restore 快照口令——与主备份口令一致（审查 P0：
     *   不再硬编码，退路文件用户自己可解，反编译 APK 也拿不到口令）。
     */
    suspend fun restore(
        decrypted: DecryptedFile, snapshotPassword: CharArray,
        mode: BackupEngine.RestoreMode = BackupEngine.RestoreMode.FULL_ROLLBACK,
    ): BackupEngine.VerifyResult =
        withContext(Dispatchers.IO) {
            // 1. pre-restore 快照（退路）
            val snapshot = BackupEngine.export(supportDb(), nowIso())
            // 快照同样带恢复码槽——用户用恢复码完成恢复时，快照仍可用同一恢复码解开
            val snapBytes = VaultCipher.encryptV3(
                vaultKeyOrThrow(), snapshotPassword, recoverySlot(), snapshot.payload,
                BackupEngine.schemaVersion(supportDb()), nowIso())
            val dir = File(context.filesDir, "backups").apply { mkdirs() }
            val snapFile = dir.resolve("pre-restore-${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))}.ashkb")
            snapFile.writeBytes(snapBytes)
            val modeNote = if (mode == BackupEngine.RestoreMode.FULL_ROLLBACK) "完整回滚" else "按表合并"
            try {
                // 2. 覆盖写入 + 3. 双校验（事务内：失败即整体回滚，库保持恢复前状态）
                val verify = BackupEngine.restore(supportDb(), decrypted.payload, mode)
                log(LedgerType.RESTORE, verify.rowsOk, "restore", snapFile.name, verify.totalRows,
                    verify.rowsOk, "pre-restore 快照已留存；$modeNote；行数+SHA 双校验" +
                        if (verify.rowsOk) "通过" else "失败——已整体回滚，库保持恢复前状态")
                verify
            } catch (e: Exception) {
                log(LedgerType.RESTORE, false, "restore", snapFile.name, null, false, e.message)
                throw e
            }
        }

    // ======================= 恢复演练（协议 §7 首次恢复演练） =======================

    data class DrillReport(
        val rowTotal: Int, val tableCount: Int,
        val roundtripOk: Boolean,   // 副本演练前后逐字节一致（生产库全程零写入）
        val detail: String,
    )

    /**
     * 一键恢复演练（R5 方案A，协议 §6 旁路模式）：生产库先 WAL checkpoint 再整库复制副本，
     * 导出 → 加密 → 解密 → 恢复 → 双校验 → 复核导出一致，全程在副本上跑，生产库零写入。
     * 旧实现对生产库真实 DELETE+重插——导出→写回窗口内通知栏打卡 / 闹钟 / BootReceiver
     * 的任何写入都会被抹掉，且双快照都不含该行（丢数据、演练还报成功）。
     * 演练口令随机即弃，不落盘；副本用后即删；台账照旧登记 DRILL。
     */
    suspend fun drill(): DrillReport = withContext(Dispatchers.IO) {
        val now = nowIso()
        // WAL checkpoint 让主库文件自包含（-wal 日志并回主文件），副本才是完整快照；
        // checkpoint 属 SQLite 例行整理，不改库的逻辑内容
        runCatching { supportDb().query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() } }
        val src = context.getDatabasePath("ashkb.db")
        val name = "ashkb-drill-${java.util.UUID.randomUUID()}"
        val drillDb = androidx.room.Room.databaseBuilder(context, AppDatabase::class.java, name).build()
        try {
            src.copyTo(context.getDatabasePath(name), overwrite = true)
            val copy = drillDb.openHelper.writableDatabase
            // 1. 副本导出
            val before = BackupEngine.export(copy, now)
            // 2. 加密 → 解密（证明加密链路）
            val drillPass = "drill-${java.util.UUID.randomUUID()}"
            // 演练口令随机即弃，无恢复码槽（单口令槽）；v3 格式——演练走的就是生产加密路径
            val bytes = VaultCipher.encryptV3(vaultKeyOrThrow(), drillPass.toCharArray(), null, before.payload,
                BackupEngine.schemaVersion(copy), now)
            val decrypted = VaultCipher.decrypt(drillPass.toCharArray(), bytes)
            // 3. 副本上恢复写入 + 双校验
            val verify = BackupEngine.restore(copy, decrypted.payload)
            // 4. 复核：副本重新导出与首次导出逐字节比对
            val after = BackupEngine.export(copy, now)
            val roundtripOk = before.payload == after.payload
            val detail = buildString {
                append("加密链路✓ 解密✓ 恢复双校验${if (verify.rowsOk) "✓" else "✗"}" +
                    " 复核一致${if (roundtripOk) "✓" else "✗"}；" +
                    "${before.tableCount} 表 ${before.rowTotal} 行（旁路副本，生产库零写入）")
                if (!verify.rowsOk) append("；异常：${verify.rowDetails.take(3).joinToString("；")}")
            }
            // 5. 台账照旧登记 DRILL（生产库唯一写入，放在比对之后）
            log(LedgerType.DRILL, verify.rowsOk && roundtripOk, "drill", null,
                before.rowTotal, verify.rowsOk, detail)
            DrillReport(before.rowTotal, before.tableCount, roundtripOk, detail)
        } finally {
            runCatching { drillDb.close() }
            listOf(name, "$name-wal", "$name-shm").forEach {
                runCatching { context.getDatabasePath(it).delete() }
            }
        }
    }

    // ======================= 档案 JSON（模块导出） =======================

    /** 健康档案明文 JSON（换机建档导入用，协议 §3 模块导出）。 */
    suspend fun exportProfileJson(): String = withContext(Dispatchers.IO) {
        val o = JSONObject()
        o.put("format", "ashkb-profile")
        o.put("exported_at", nowIso())
        db.profileDao().get()?.let { p ->
            o.put("profile", JSONObject().apply {
                put("display_name", p.displayName)
                put("diagnosis", p.diagnosis)
                p.diagnoseYear?.let { put("diagnose_year", it) }
                put("hla_b27", p.hlaB27)
                put("disease_stage", p.diseaseStage)
                p.sacroiliitisGrade?.let { put("sacroiliitis_grade", it) }
                p.allergies?.let { put("allergies", it) }
                p.emergencyBloodType?.let { put("emergency_blood_type", it) }
            })
        }
        val meds = db.medicationDao().listActive()
        val arr = org.json.JSONArray()
        meds.forEach { m ->
            arr.put(JSONObject().apply {
                put("name", m.name)
                m.brandName?.let { put("brand_name", it) }
                put("name_key", m.nameKey)
                put("med_class", m.medClass)
                put("route", m.route)
                put("dose", m.dose)
                put("frequency", m.frequency)
                m.takeTimes?.let { put("take_times", it) }
                m.weeklyWeekday?.let { put("weekly_weekday", it) }
                m.weeklyWeekday2?.let { put("weekly_weekday2", it) }
                put("start_date", m.startDate)
                m.endDate?.let { put("end_date", it) }
                m.injCycleDays?.let { put("inj_cycle_days", it) }
                m.notes?.let { put("notes", it) }
            })
        }
        o.put("medications", arr)
        o.toString(2)
    }

    suspend fun importProfileJson(json: String): Pair<String, Int> = withContext(Dispatchers.IO) {
        val root = JSONObject(json)
        if (root.optString("format") != "ashkb-profile")
            throw BackupEngine.BackupException("不是有效的档案 JSON（缺 format=ashkb-profile）")
        val existing = db.profileDao().get()
        val po = root.optJSONObject("profile") ?: throw BackupEngine.BackupException("JSON 中无 profile 段")
        val profile = (existing ?: com.ashkb.app.data.entity.Profile(
            id = 1, displayName = po.getString("display_name"), diagnosis = po.getString("diagnosis"),
            createdAt = nowIso(), updatedAt = nowIso(),
        )).copy(
            displayName = po.getString("display_name"),
            diagnosis = po.getString("diagnosis"),
            diagnoseYear = if (po.has("diagnose_year")) po.getInt("diagnose_year") else existing?.diagnoseYear,
            hlaB27 = po.optString("hla_b27", existing?.hlaB27 ?: "unknown"),
            // R1：旧档案 JSON 的 active 归一化为 controlled（与迁移 v7→v8 同义；其余未识别值原样透传，引擎按 flare 保守处理）
            diseaseStage = po.optString("disease_stage", existing?.diseaseStage ?: "unknown")
                .let { if (it == "active") "controlled" else it },
            sacroiliitisGrade = po.optString("sacroiliitis_grade").ifBlank { existing?.sacroiliitisGrade },
            allergies = po.optString("allergies").ifBlank { existing?.allergies },
            emergencyBloodType = po.optString("emergency_blood_type").ifBlank { existing?.emergencyBloodType },
        )
        db.profileDao().upsert(profile)
        var medCount = 0
        root.optJSONArray("medications")?.let { arr ->
            val now = nowIso()
            for (i in 0 until arr.length()) {
                val m = arr.getJSONObject(i)
                db.medicationDao().upsert(
                    com.ashkb.app.data.entity.Medication(
                        id = Ids.new("med"), name = m.getString("name"),
                        brandName = m.optString("brand_name").ifBlank { null },
                        nameKey = m.optString("name_key", m.getString("name").lowercase()),
                        medClass = m.optString("med_class", "OTHER"),
                        route = m.optString("route", "oral"),
                        dose = m.optString("dose", ""),
                        frequency = m.optString("frequency", "DAILY"),
                        takeTimes = m.optString("take_times").ifBlank { null },
                        weeklyWeekday = if (m.has("weekly_weekday")) m.getInt("weekly_weekday") else null,
                        weeklyWeekday2 = if (m.has("weekly_weekday2")) m.getInt("weekly_weekday2") else null,
                        startDate = m.optString("start_date", LocalDate.now().toString()),
                        endDate = m.optString("end_date").ifBlank { null },
                        injCycleDays = if (m.has("inj_cycle_days")) m.getInt("inj_cycle_days") else null,
                        notes = m.optString("notes").ifBlank { null },
                        createdAt = now, updatedAt = now,
                    )
                )
                medCount++
            }
        }
        log(LedgerType.EXPORT, true, "import-profile", null, medCount, null, "档案 JSON 导入")
        Pair(profile.displayName, medCount)
    }

    suspend fun logExportJson(fileName: String, rows: Int) {
        log(LedgerType.EXPORT, true, "local", fileName, rows, null, "档案 JSON 导出")
    }

    suspend fun logFailed(type: LedgerType, target: String, msg: String) {
        log(type, false, target, null, null, null, msg)
    }

    // ======================= 附件同步（v1.0.35） =======================
    //
    // 逐个附件加密上传到 ashkb/attachments/<日期>/<附件id>.enc（不打包）。
    // 与 DB 备份**分开操作**：DB 备份有 120s 超时，附件几百 MB 远超，混在一起必然失败。

    /** UI 透传：同步开关（存 prefs，非密钥） */
    fun attachmentSyncEnabled(): Boolean = attachments.isSyncEnabled()

    fun setAttachmentSyncEnabled(on: Boolean) = attachments.setSyncEnabled(on)

    fun observeAttachmentSyncCounts(): Flow<AttachmentSyncCounts> = attachments.observeSyncCounts()

    suspend fun attachmentLocalBytes(): Long = attachments.totalBytes()

    data class AttachmentSyncProgress(val phase: String, val done: Int, val total: Int, val failed: Int)

    data class AttachmentSyncResult(
        val uploaded: Int,
        val deletedRemote: Int,
        /** 本地文件已丢失且无远端副本——无从上传（如从旧备份恢复后的空壳行） */
        val noLocal: Int,
        val failed: Int,
        val detail: String,
    )

    /**
     * 批量补传 + 清理待删远端。
     *
     * 幂等：已上传的天然不在队列里；中途失败保留已完成部分，重跑继续。
     * 刻意不做上传后回读（逐个回读流量翻倍），改为记录本地密文 SHA-256 供后续校验。
     */
    suspend fun syncAttachments(
        onProgress: (AttachmentSyncProgress) -> Unit = {},
    ): AttachmentSyncResult = withContext(Dispatchers.IO) {
        if (!attachments.isSyncEnabled()) throw WebDavClient.DavException("附件同步已关闭")
        val (url, user, pass) = webdavConfig()
        if (url.isBlank()) throw WebDavClient.DavException("未配置 WebDAV 服务器")
        requireHttps(url)
        val client = WebDavClient(url, user, pass)
        val vaultKey = vaultKeyOrThrow()

        // 1. 先清墓碑：释放服务器空间，也让「删除」尽快闭环
        val tc = clearTombstones(client)
        var failed = tc.failed

        // 2. 补传
        val pending = attachments.pendingUpload()
        var uploaded = 0
        var noLocal = 0
        val ensured = mutableSetOf<String>()
        onProgress(AttachmentSyncProgress("准备上传附件", 0, pending.size, failed))
        pending.forEachIndexed { idx, a ->
            onProgress(AttachmentSyncProgress("正在上传附件", idx, pending.size, failed))
            val plain = attachments.readLocal(a)
            if (plain == null) { noLocal++; return@forEachIndexed }
            val folder = AttachmentPath.folderOf(a.createdAt)
            // 每个日期目录只确保一次（MKCOL 幂等，但没必要重复往返）
            if (ensured.add(folder)) runCatching { client.ensureAttachmentDir(folder) }
            val blob = VaultCipher.encryptBlob(vaultKey, plain, a.id)
            val remote = AttachmentPath.remotePathOf(a.createdAt, a.id)
            runCatching { client.uploadAttachment(remote, blob) }
                .onSuccess { attachments.markUploaded(a.id, remote, BackupEngine.sha256Hex(blob)); uploaded++ }
                .onFailure { failed++ }
        }
        onProgress(AttachmentSyncProgress("附件同步完成", pending.size, pending.size, failed))

        val detail = "上传 $uploaded / 待传 ${pending.size}；远端清理 ${tc.deleted} / 待删 ${tc.total}" +
            if (noLocal > 0) "；$noLocal 个本地文件缺失（无法上传）" else "" +
            if (failed > 0) "；失败 $failed" else ""
        log(LedgerType.ATTACH, failed == 0, "webdav", null, uploaded, failed == 0, detail)
        AttachmentSyncResult(uploaded, tc.deleted, noLocal, failed, detail)
    }

    /** 清理墓碑（本地已删、待远端清理）；远端 404 视为已不存在（幂等）。 */
    private suspend fun clearTombstones(client: WebDavClient): TombstoneClear {
        val tombstones = attachments.pendingRemoteDelete()
        var deleted = 0
        var failed = 0
        tombstones.forEach { a ->
            val p = a.remotePath
            if (p == null) {
                attachments.hardDelete(a.id) // 从未上传过，无需远端清理
                return@forEach
            }
            runCatching { client.deleteAttachment(p) }
                .onSuccess { attachments.hardDelete(a.id); deleted++ }
                .onFailure { failed++ }
        }
        return TombstoneClear(deleted, tombstones.size, failed)
    }

    private data class TombstoneClear(val deleted: Int, val total: Int, val failed: Int)

    // ======================= 附件远端校验（v1.0.36） =======================

    data class AttachmentVerifyResult(
        /** 服务器上扫描到的附件文件数 */
        val remoteCount: Int,
        /** 本次补传成功数 */
        val uploaded: Int,
        /** 本地有记录但远端缺失的应补传总数 */
        val missingTotal: Int,
        /** 本次清理的远端多余文件数 */
        val orphanDeleted: Int,
        /** 远端多余文件总数（本地无记录） */
        val orphanTotal: Int,
        /** 本地文件已丢失、无从补传的数量 */
        val noLocal: Int,
        val failed: Int,
        val detail: String,
    )

    /**
     * v1.0.36：远端校验与补传。
     *
     * 比对服务器上 `ashkb/attachments/` 的实际文件集与本地记录的 `remote_path`：
     *  ① 本地有记录、远端缺失 → 重新上传（远端被手动删除的情形）
     *  ② 远端有文件、本地无记录 → 清理（换机残留 / 本地已删；坚果云回收站可找回）
     * 顺带收尾墓碑行。**只处理 `AttachmentPath.isManagedRemotePath` 认可的形状**，
     * 目录下其它文件（非日期目录、非 `.enc`）一律不碰。
     */
    suspend fun verifyAttachments(
        onProgress: (AttachmentSyncProgress) -> Unit = {},
    ): AttachmentVerifyResult = withContext(Dispatchers.IO) {
        if (!attachments.isSyncEnabled()) throw WebDavClient.DavException("附件同步已关闭")
        val (url, user, pass) = webdavConfig()
        if (url.isBlank()) throw WebDavClient.DavException("未配置 WebDAV 服务器")
        requireHttps(url)
        val client = WebDavClient(url, user, pass)
        val vaultKey = vaultKeyOrThrow()

        onProgress(AttachmentSyncProgress("正在列出远端附件", 0, 0, 0))
        val remote = client.listAttachmentRemotePaths()

        val rows = attachments.withRemotePath()
        val plan = AttachmentPath.reconcile(rows.mapNotNull { it.remotePath }.toSet(), remote)

        var failed = 0

        // ① 补传：本地有记录、远端缺失（远端被手动删掉 / 换机后未传全）
        val byPath = rows.associateBy { it.remotePath }
        val missing = plan.missing.toList()
        var uploaded = 0
        var noLocal = 0
        val ensured = mutableSetOf<String>()
        missing.forEachIndexed { idx, path ->
            onProgress(AttachmentSyncProgress("正在补传附件", idx, missing.size, failed))
            val a = byPath[path] ?: return@forEachIndexed
            val plain = attachments.readLocal(a)
            if (plain == null) { noLocal++; return@forEachIndexed }
            val folder = AttachmentPath.folderOf(a.createdAt)
            if (ensured.add(folder)) runCatching { client.ensureAttachmentDir(folder) }
            val blob = VaultCipher.encryptBlob(vaultKey, plain, a.id)
            runCatching { client.uploadAttachment(path, blob) }
                .onSuccess { attachments.markUploaded(a.id, path, BackupEngine.sha256Hex(blob)); uploaded++ }
                .onFailure { failed++ }
        }

        // ② 清孤儿：远端有文件、本地无记录
        val orphans = plan.orphans.toList()
        var orphanDeleted = 0
        orphans.forEachIndexed { idx, path ->
            onProgress(AttachmentSyncProgress("正在清理远端多余文件", idx, orphans.size, failed))
            runCatching { client.deleteAttachment(path) }
                .onSuccess { orphanDeleted++ }
                .onFailure { failed++ }
        }

        // ③ 收尾墓碑（远端已确认不在 → 物理删行）
        failed += clearTombstones(client).failed

        onProgress(AttachmentSyncProgress("远端校验完成", 0, 0, failed))
        val detail = "远端 ${remote.size} 个文件；补传 $uploaded / 缺 ${missing.size}；" +
            "清理多余 $orphanDeleted / 共 ${orphans.size}" +
            if (noLocal > 0) "；$noLocal 个本地文件缺失（无法补传）" else "" +
            if (failed > 0) "；失败 $failed" else ""
        log(LedgerType.ATTACH, failed == 0, "webdav", null, uploaded, failed == 0, "远端校验：$detail")
        AttachmentVerifyResult(remote.size, uploaded, missing.size, orphanDeleted, orphans.size, noLocal, failed, detail)
    }

    /**
     * 懒下载：本地文件缺失但已有远端副本时，按需拉回并解密。
     *
     * @return true = 已恢复本地文件；false = 远端没有 / 解密失败 / 未配置
     */
    suspend fun downloadAttachmentIfMissing(a: CheckupAttachment): Boolean = withContext(Dispatchers.IO) {
        if (attachments.hasLocalFile(a)) return@withContext true
        val remote = a.remotePath ?: return@withContext false
        val (url, user, pass) = webdavConfig()
        if (url.isBlank()) return@withContext false
        requireHttps(url)
        val vaultKey = vaultKeys.current() ?: return@withContext false
        runCatching {
            val blob = WebDavClient(url, user, pass).downloadAttachment(remote)
            val plain = VaultCipher.decryptBlob(vaultKey, blob, a.id)
            attachments.writeLocal(a, plain)
        }.getOrDefault(false)
    }
}
