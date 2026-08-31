package com.ashkb.app.data.repo

import android.content.Context
import android.content.SharedPreferences
import com.ashkb.app.data.backup.BackupEngine
import com.ashkb.app.data.backup.VaultCipher
import com.ashkb.app.data.backup.WebDavClient
import com.ashkb.app.data.db.AppDatabase
import com.ashkb.app.data.db.Ids
import com.ashkb.app.data.entity.BackupLedger
import com.ashkb.app.data.entity.LedgerStatus
import com.ashkb.app.data.entity.LedgerType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * P4 备份仓库（R20）：编排 BackupEngine + VaultCipher + WebDavClient + 备份台账。
 * 全量备份文件名遵循协议：本机导出 ashkb-YYYYMMDD.ashkb，WebDAV ashkb-backup-YYYYMMDD.ashkb。
 */
class BackupRepository(private val context: Context) {
    private val db = AppDatabase.get(context)
    private val ledgerDao = db.backupLedgerDao()

    private val prefs: SharedPreferences =
        context.getSharedPreferences("webdav_config", Context.MODE_PRIVATE)

    private fun nowIso(): String = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

    fun observeLedger(): Flow<List<BackupLedger>> = ledgerDao.observeRecent()

    // ---- WebDAV 配置 ----
    fun webdavConfig(): Triple<String, String, String> = Triple(
        prefs.getString("url", "") ?: "",
        prefs.getString("user", "") ?: "",
        prefs.getString("pass", "") ?: "",
    )

    fun saveWebdavConfig(url: String, user: String, pass: String) {
        prefs.edit().putString("url", url).putString("user", user).putString("pass", pass).apply()
    }

    fun webdavConfigured(): Boolean = webdavConfig().first.isNotBlank()

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

    // ======================= 本机备份 =======================

    class BackupOutcome(val file: File, val rowTotal: Int, val tableCount: Int, val sha256: String)

    /** 全量加密备份到本机（协议 §3 全量导出：ashkb-YYYYMMDD.ashkb）。 */
    suspend fun backupLocal(password: CharArray): BackupOutcome = withContext(Dispatchers.IO) {
        val exported = BackupEngine.export(supportDb(), nowIso())
        val bytes = VaultCipher.encrypt(password, exported.payload, BackupEngine.SCHEMA_VERSION, nowIso())
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

    /** WebDAV 立即备份：导出 → 加密 → 上传 → 回读校验 → 轮换（协议 §4 全链路）。 */
    suspend fun backupWebdav(password: CharArray): String = withContext(Dispatchers.IO) {
        val (url, user, pass) = webdavConfig()
        if (url.isBlank()) throw WebDavClient.DavException("未配置 WebDAV 服务器")
        val client = WebDavClient(url, user, pass)
        val exported = BackupEngine.export(supportDb(), nowIso())
        val bytes = VaultCipher.encrypt(password, exported.payload, BackupEngine.SCHEMA_VERSION, nowIso())
        val name = "ashkb-backup-${LocalDate.now()}.ashkb"
        try {
            val upMsg = client.upload(name, bytes)
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

    suspend fun probeWebdav(url: String, user: String, pass: String): String =
        withContext(Dispatchers.IO) { WebDavClient(url, user, pass).probe() }

    // ======================= 恢复 =======================

    class DecryptedFile(val payload: String, val schemaVersion: Int, val createdAt: String)

    /** 旁路解密 + 文件自校验（协议 §6 第 1/3 步：不动主库）。 */
    suspend fun decryptAndSelfCheck(bytes: ByteArray, password: CharArray): DecryptedFile =
        withContext(Dispatchers.IO) {
            val d = VaultCipher.decrypt(password, bytes)
            val root = JSONObject(d.payload)
            if (root.optString("format") != "ashkb-full") throw BackupEngine.BackupException("备份格式不正确")
            if (root.optInt("schema_version", 0) > BackupEngine.SCHEMA_VERSION)
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
     */
    suspend fun restore(decrypted: DecryptedFile): BackupEngine.VerifyResult =
        withContext(Dispatchers.IO) {
            // 1. pre-restore 快照（退路）
            val snapshot = BackupEngine.export(supportDb(), nowIso())
            val snapBytes = VaultCipher.encrypt(
                PRE_RESTORE_SNAPSHOT_PASSWORD.toCharArray(),
                snapshot.payload, BackupEngine.SCHEMA_VERSION, nowIso())
            val dir = File(context.filesDir, "backups").apply { mkdirs() }
            val snapFile = dir.resolve("pre-restore-${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))}.ashkb")
            snapFile.writeBytes(snapBytes)
            try {
                // 2. 覆盖写入 + 3. 双校验
                val verify = BackupEngine.restore(supportDb(), decrypted.payload)
                log(LedgerType.RESTORE, true, "restore", snapFile.name, verify.totalRows,
                    verify.rowsOk, "pre-restore 快照已留存；行数+SHA 双校验${if (verify.rowsOk) "通过" else "失败"}")
                verify
            } catch (e: Exception) {
                log(LedgerType.RESTORE, false, "restore", snapFile.name, null, false, e.message)
                throw e
            }
        }

    // ======================= 恢复演练（协议 §7 首次恢复演练） =======================

    data class DrillReport(
        val rowTotal: Int, val tableCount: Int,
        val roundtripOk: Boolean,   // 演练后库与演练前逐字节一致
        val detail: String,
    )

    /**
     * 一键恢复演练：导出 → 加密 → 解密 → 覆盖恢复 → 双校验 → 复核导出一致。
     * 全程内存态、可逆（恢复写入的正是刚导出的当前数据）；
     * 演练口令随机即弃，不落盘——证明的是加密链路 + 恢复链路本身可用。
     * 台账登记为 DRILL 类型。
     */
    suspend fun drill(): DrillReport = withContext(Dispatchers.IO) {
        val now = nowIso()
        // 1. 导出当前全库
        val before = BackupEngine.export(supportDb(), now)
        // 2. 加密 → 解密（证明加密链路）
        val drillPass = "drill-${java.util.UUID.randomUUID()}"
        val bytes = VaultCipher.encrypt(drillPass.toCharArray(), before.payload, BackupEngine.SCHEMA_VERSION, now)
        val decrypted = VaultCipher.decrypt(drillPass.toCharArray(), bytes)
        // 3. 恢复写入（写入的即刚导出的当前数据——可逆）+ 双校验
        val verify = BackupEngine.restore(supportDb(), decrypted.payload)
        // 4. 复核：重新导出与演练前逐字节比对（此时尚未写台账行，故应严格一致）
        val after = BackupEngine.export(supportDb(), now)
        val roundtripOk = before.payload == after.payload
        val detail = buildString {
            append("加密链路✓ 解密✓ 恢复双校验${if (verify.rowsOk) "✓" else "✗"}" +
                " 复核一致${if (roundtripOk) "✓" else "✗"}；" +
                "${before.tableCount} 表 ${before.rowTotal} 行")
            if (!verify.rowsOk) append("；异常：${verify.rowDetails.take(3).joinToString("；")}")
        }
        // 5. 台账登记（放在比对之后——台账行本身会改变库内容）
        log(LedgerType.DRILL, verify.rowsOk && roundtripOk, "drill", null,
            before.rowTotal, verify.rowsOk, detail)
        DrillReport(before.rowTotal, before.tableCount, roundtripOk, detail)
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
            diseaseStage = po.optString("disease_stage", existing?.diseaseStage ?: "unknown"),
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

    companion object {
        /** pre-restore 快照固定口令（本机退路文件，仅恢复误操作用）。 */
        const val PRE_RESTORE_SNAPSHOT_PASSWORD = "ashkb-pre-restore"
    }
}
