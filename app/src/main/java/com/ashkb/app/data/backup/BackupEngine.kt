package com.ashkb.app.data.backup

import android.content.Context
import android.database.Cursor
import androidx.annotation.StringRes
import androidx.sqlite.db.SupportSQLiteDatabase
import org.json.JSONArray
import org.json.JSONObject
import com.ashkb.app.R
import java.security.MessageDigest

/**
 * 备份引擎：全库逐表导出（JSON 行阵列）+ manifest（行数 + 逐表 SHA-256），
 * 恢复走「pre-restore 快照（由调用方先行完成）→ 事务覆盖写入 → 双校验自证」
 * ——协议 §4 备份内容 / §5 checksum 双校验 / §6 恢复五步的实现层。
 *
 * 泛型 cursor 直读：不依赖 Room 实体类，未来加表无需改本引擎（表清单动态发现）。
 */
object BackupEngine {

    /** R2：恢复语义二选一——完整回滚 / 按表合并。 */
    enum class RestoreMode {
        /** 完整回滚：先清空全部用户表（含备份里没有的表），再按备份重建——真正回到备份时点。 */
        FULL_ROLLBACK,
        /** 按表合并：只覆盖备份里包含的表，其余表保持现状（旧行为）。 */
        MERGE_TABLES,
    }

    /**
     * v1.2.5 文案资源化：中文 [msg] 原样保留（日志 / 无资源时的回落）；
     * data 层没有 Context，[resId] / [resArgs] 由 UI 侧解析成当前语言（见 BackupViewModel.errText）。
     * [resArgs] 里若含 `List<*>`，UI 侧会用本地化的枚举分隔符重新拼接。
     */
    class BackupException(
        msg: String,
        cause: Throwable? = null,
        @StringRes val resId: Int = 0,
        val resArgs: List<Any> = emptyList(),
    ) : Exception(msg, cause)

    /**
     * R7：备份 schema 标签直接取 Room DB 版本（PRAGMA user_version），随迁移自动演进——
     * 此前是独立常量（停在 6），Room 升到 7 后未同步，标签失真。
     */
    fun schemaVersion(db: SupportSQLiteDatabase): Int =
        db.query("PRAGMA user_version").use { c -> c.moveToFirst(); c.getInt(0) }

    /** 表清单：用户表（排除 Room 元数据 / 系统表），固定字典序保证备份文件确定性。 */
    fun tableNames(db: SupportSQLiteDatabase): List<String> =
        db.query("SELECT name FROM sqlite_master WHERE type='table' " +
            "AND name NOT LIKE 'sqlite_%' AND name != 'room_master_table' AND name != 'android_metadata' " +
            "ORDER BY name").use { c ->
            buildList {
                while (c.moveToNext()) add(c.getString(0))
            }
        }

    /**
     * R8：恢复表名白名单校验（纯函数）——返回备份 tables 里不在当前库表清单中的表名。
     * 调用方据此整体拒绝恢复：构造恶意备份携带任意表名（含反引号逃逸）写入的攻击面就此封死。
     */
    fun unknownTables(tablesJson: JSONObject, knownTables: Set<String>): List<String> =
        tablesJson.keys().asSequence().filter { it !in knownTables }.sorted().toList()

    fun sha256Hex(data: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it) }

    // ======================= 导出 =======================

    data class ExportResult(val payload: String, val rowTotal: Int, val tableCount: Int)

    fun export(db: SupportSQLiteDatabase, nowIso: String): ExportResult {
        // 读事务：全程读到同一快照，避免导出期间被并发写（打卡 receiver 等）污染
        db.beginTransaction()
        try {
            val tables = tableNames(db)
            val tablesJson = JSONObject()
            val manifest = JSONObject()
            var rowTotal = 0
            for (t in tables) {
                val rows = readTable(db, t)
                val arr = JSONArray()
                for (r in rows) arr.put(r)
                tablesJson.put(t, arr)
                rowTotal += rows.size
                manifest.put(t, JSONObject().apply {
                    put("rows", rows.size)
                    put("sha256", tableSha(rows))
                })
            }
            val payload = JSONObject().apply {
                put("format", "ashkb-full")
                put("schema_version", schemaVersion(db))
                put("exported_at", nowIso)
                put("tables", tablesJson)
                put("manifest", manifest)
            }
            db.setTransactionSuccessful()
            return ExportResult(payload.toString(), rowTotal, tables.size)
        } finally {
            db.endTransaction()
        }
    }

    /** 逐行 JSON：列顺序 = 建表列序（确定性）；值类型保留（INTEGER→long / REAL→double / TEXT→string / NULL→null）。 */
    private fun readTable(db: SupportSQLiteDatabase, table: String): List<JSONObject> {
        val rows = mutableListOf<JSONObject>()
        db.query("SELECT * FROM `$table`").use { c: Cursor ->
            while (c.moveToNext()) {
                val o = JSONObject()
                for (i in 0 until c.columnCount) {
                    if (c.isNull(i)) { o.put(c.getColumnName(i), JSONObject.NULL); continue }
                    when (c.getType(i)) {
                        Cursor.FIELD_TYPE_INTEGER -> o.put(c.getColumnName(i), c.getLong(i))
                        Cursor.FIELD_TYPE_FLOAT -> o.put(c.getColumnName(i), c.getDouble(i))
                        Cursor.FIELD_TYPE_BLOB -> o.put(c.getColumnName(i),
                            java.util.Base64.getEncoder().encodeToString(c.getBlob(i)))
                        else -> o.put(c.getColumnName(i), c.getString(i))
                    }
                }
                rows.add(o)
            }
        }
        return rows
    }

    /** 行序无关的表摘要：每行 JSON 串按字典序排序后拼接取 SHA-256（协议 §5 逐表排序序列化）。 */
    private fun tableSha(rows: List<JSONObject>): String {
        val lines = rows.map { it.toString() }.sorted()
        return sha256Hex(lines.joinToString("\n").toByteArray(Charsets.UTF_8))
    }

    // ======================= 恢复 =======================

    /** 单条校验差异的资源化形式（[args] 同 [BackupException.resArgs] 约定）。 */
    data class RowIssue(@StringRes val resId: Int, val args: List<Any>) {
        /**
         * 当前语言文案。参数最多 3 个（见 `strings_backup.xml` 的 `backup_verify_*`）：data 层拿不到
         * Context，故在此按元数显式分派，避免 vararg 展开数组的额外拷贝（detekt SpreadOperator）。
         */
        fun text(context: Context): String = when (args.size) {
            0 -> context.getString(resId)
            1 -> context.getString(resId, args[0])
            2 -> context.getString(resId, args[0], args[1])
            else -> context.getString(resId, args[0], args[1], args[2])
        }
    }

    data class VerifyResult(
        val rowsOk: Boolean, val shaOk: Boolean,
        val rowDetails: List<String>, val totalRows: Int,
        val rowIssues: List<RowIssue> = emptyList(),
    )

    /**
     * 恢复：事务内逐表 DELETE + INSERT（按 PRAGMA 列类型绑定），随后双校验。
     * 调用方必须在调用前完成 pre-restore 快照（协议 §6 退路）。
     * R2：mode=FULL_ROLLBACK 时先清空全部用户表（含备份里没有的表）再重建，
     * 真正回到备份时点；MERGE_TABLES 保持旧行为（备份里没有的表保留现状）。
     * 双校验在事务内进行（审查 P2「事务后无回滚」）：任一表行数或 SHA-256 不匹配
     * 即不 setTransactionSuccessful——endTransaction 整体回滚，库保持恢复前状态。
     * 校验针对备份原文；R1 归一化（active→controlled，与迁移 v7→v8 同义）与 v9 检索列回填
     * （kb_entries.search_text，与迁移 v8→v9 同义）都在校验通过后、提交前执行
     * （否则改值必致对应表 SHA 误报不匹配）。
     */
    fun restore(
        db: SupportSQLiteDatabase, payload: String,
        mode: RestoreMode = RestoreMode.MERGE_TABLES,
    ): VerifyResult {
        val root = JSONObject(payload)
        val schema = root.optInt("schema_version", 0)
        if (schema <= 0) throw BackupException("备份缺少 schema_version", resId = R.string.backup_err_engine_no_schema)
        val current = schemaVersion(db)
        if (schema > current) throw BackupException(
            "备份来自更高版本（schema $schema > 当前 $current），请先升级 APP",
            resId = R.string.backup_err_engine_schema_newer, resArgs = listOf(schema, current))
        val expectedFormat = root.optString("format")
        if (expectedFormat != "ashkb-full") throw BackupException(
            "备份格式不正确：$expectedFormat",
            resId = R.string.backup_err_engine_bad_format, resArgs = listOf(expectedFormat))

        val tables = root.getJSONObject("tables")
        // R8：表名白名单——备份里的每个表必须存在于当前库（sqlite_master 动态发现），
        // 任一未知表名即整体拒绝（事务开始前校验，保证拒绝时零写入）
        val unknown = unknownTables(tables, tableNames(db).toHashSet())
        if (unknown.isNotEmpty()) throw BackupException(
            "备份包含当前数据库不存在的表，已整体拒绝：${unknown.joinToString("、")}",
            resId = R.string.backup_err_engine_unknown_tables, resArgs = listOf(unknown))
        db.beginTransaction()
        try {
            if (mode == RestoreMode.FULL_ROLLBACK) {
                for (t in tableNames(db)) db.execSQL("DELETE FROM `$t`")
            }
            val names = tables.keys().asSequence().toList()
            for (t in names) {
                insertTable(db, t, tables.getJSONArray(t))
            }
            // 双校验（事务内，针对备份原文——归一化在其后）：失败则不提交，整体回滚
            val result = verifyAgainst(db, root.getJSONObject("manifest"), tables)
            if (result.rowsOk) {
                // R1：旧备份归一化——v8 前导出的 disease_stage='active' 落库时并入 controlled；
                // 校验已按备份原文通过，此处等价于恢复旧备份后补跑 v7→v8 的数据迁移
                if (tables.has("profile")) {
                    db.execSQL("UPDATE `profile` SET `disease_stage` = 'controlled' WHERE `disease_stage` = 'active'")
                }
                // v9：旧备份不含 kb_entries.search_text 列（insertTable 按名列表 INSERT，缺列即 NULL）——
                // 按当前口径回填，等价于恢复旧备份后补跑 v8→v9；不回填则知识库搜索在恢复后整库失配。
                // 表达式与 MIGRATION_8_9 / KbSearch.searchText 完全一致。
                if (tables.has("kb_entries")) {
                    db.execSQL(
                        "UPDATE `kb_entries` SET `search_text` = " +
                            "`title` || char(10) || `summary` || char(10) || `payload` " +
                            "WHERE `search_text` IS NULL"
                    )
                }
                db.setTransactionSuccessful()
            }
            return result
        } catch (e: Exception) {
            throw BackupException(
                "恢复写入失败：${e.message}", e,
                resId = R.string.backup_err_engine_write_failed, resArgs = listOf(e.message.orEmpty()))
        } finally {
            db.endTransaction()
        }
    }

    /**
     * 双校验：行数快速核对 + 逐表排序序列化 SHA-256 精确比对（协议 §5）。
     * P5 修订 R9：跨 schema 恢复兼容——低版本备份缺少新列（如 v6 的 weekly_weekday2），
     * 按「备份自身的列集」重算摘要而非当前库全列，避免列增迁移后旧备份被误判损坏。
     *
     * v1.1.1（S-1 / M6）：**遍历的是 `tables` 对象的键，不再遍历 `manifest` 的键。**
     *
     * 为什么必须换：`manifest` 的键同样来自备份文件，而 `readTable` 会把表名拼进
     * `SELECT * FROM \`$t\``——含反引号的键可以闭合转义。威胁模型正是本项目自己写下的
     * 「诱导用户导入攻击者提供、口令已知的备份」（见 `insertTable` 的列名白名单注释）：
     * 表名白名单只作用于 `tables`（唯一调用点 `:154`），manifest 这条镜像缺口此前无人守。
     * 遍历 `tables` 的键（它已整体过白名单）后，备份文件里的任意键都不可能到达动态 SQL。
     *
     * 顺带修掉 M6：`tables` 里有、`manifest` 里没有的表，旧实现**既不校验也照样报
     * `rowsOk=true`**（"已校验"是假的）。现在该表的期望值取不到（`m_rows` 给 -1、`m_sha` 给空串），
     * 一律判为不匹配 → 恢复被拒绝并回滚，`rowDetails` 里能看见是哪张表。
     */
    fun verifyAgainst(db: SupportSQLiteDatabase, manifest: JSONObject, tablesJson: JSONObject? = null): VerifyResult {
        val rowsBad = mutableListOf<String>()
        val shaBad = mutableListOf<String>()
        val issues = mutableListOf<RowIssue>()
        var total = 0
        // 有 tablesJson（正常恢复路径）时只信它；没有时（自生成负载）退回 manifest 键，
        // 但仍然先过一遍当前库的表白名单——两条路径都不允许备份文件自带的键直达 SQL。
        val keys = tablesJson?.keys()?.asSequence()?.toList()
            ?: manifest.keys().asSequence().filter { it in tableNames(db).toHashSet() }.toList()
        for (t in keys) {
            val rows = readTable(db, t)
            // 备份列集（按行内出现顺序——与导出时 readTable 的列序一致，保证 toString 字节一致）
            val backupCols = tablesJson?.optJSONArray(t)?.let { arr ->
                val seen = linkedSetOf<String>()
                for (i in 0 until arr.length()) arr.getJSONObject(i).keys().forEach { seen.add(it) }
                seen
            }
            val compare = if (backupCols != null && backupCols.isNotEmpty() && rows.isNotEmpty() &&
                rows.first().keys().asSequence().toList() != backupCols.toList()
            ) {
                rows.map { r -> JSONObject().apply { backupCols.forEach { c -> put(c, r.opt(c) ?: JSONObject.NULL) } } }
            } else rows
            total += rows.size
            if (rows.size != m_rows(manifest, t)) {
                rowsBad.add("$t: 期望 ${m_rows(manifest, t)} 实际 ${rows.size}")
                issues.add(RowIssue(R.string.backup_verify_rows_mismatch, listOf(t, m_rows(manifest, t), rows.size)))
            }
            if (tableSha(compare) != m_sha(manifest, t)) {
                shaBad.add(t)
                issues.add(RowIssue(R.string.backup_verify_sha_mismatch, listOf(t)))
            }
        }
        return VerifyResult(
            rowsOk = rowsBad.isEmpty() && shaBad.isEmpty(),
            shaOk = shaBad.isEmpty(),
            rowDetails = rowsBad + shaBad.map { "$it: SHA-256 不匹配" },
            totalRows = total,
            rowIssues = issues,
        )
    }

    // v1.1.1（M6）：取不到期望值时给"必然不匹配"的哨兵，而不是抛 JSONException——
    // 缺 manifest 条目的备份应当被**判为损坏并回滚**（可诊断），而不是以一句
    // "恢复写入失败：No value for xxx" 收场。
    private fun m_rows(manifest: JSONObject, t: String): Int =
        manifest.optJSONObject(t)?.optInt("rows", -1) ?: -1

    private fun m_sha(manifest: JSONObject, t: String): String =
        manifest.optJSONObject(t)?.optString("sha256").orEmpty()

    /**
     * v1.0.44（S4）：把备份里的值当数值取。
     *
     * INT / REAL 列收到非数值（损坏或被构造过的备份）时，原先的 `(v as Number)` 会抛裸
     * `ClassCastException`，被外层包成「恢复写入失败」——用户无从知道是哪张表哪一列。
     * 安全性不受影响（仍在事务内，会整体回滚），但可定位性差很多。
     */
    private fun asNumber(table: String, col: String, v: Any): Number =
        v as? Number
            ?: throw BackupException(
                "备份数据类型不符：$table.$col 期望数值，实际为 ${v::class.simpleName}",
                resId = R.string.backup_err_engine_bad_type,
                resArgs = listOf(table, col, v::class.simpleName.orEmpty()))

    private fun insertTable(db: SupportSQLiteDatabase, table: String, rows: JSONArray) {
        db.execSQL("DELETE FROM `$table`")
        if (rows.length() == 0) return
        // 列类型：PRAGMA table_info 决定绑定类型（INTEGER→long / REAL→double / 其他→string）
        val colTypes = mutableMapOf<String, String>()
        db.query("PRAGMA table_info(`$table`)").use { c ->
            while (c.moveToNext()) colTypes[c.getString(1)] = c.getString(2).uppercase()
        }
        for (i in 0 until rows.length()) {
            val row = rows.getJSONObject(i)
            val cols = mutableListOf<String>()
            val marks = mutableListOf<String>()
            row.keys().asSequence().forEach { cols.add(it); marks.add("?") }
            if (cols.isEmpty()) continue
            // v1.0.43：**列名也必须有白名单**。表名此前已过 unknownTables 校验，但列名直接取自
            // 备份 JSON 的键并拼进 SQL（`INSERT INTO t (`a`, `b`) ...`）——含反引号 / `)` 的键可
            // 破坏语句结构。威胁模型正是「诱导用户导入攻击者提供、口令已知的备份」。
            val unknown = cols.firstOrNull { it !in colTypes }
            if (unknown != null) throw BackupException(
                "备份含未知列：$table.$unknown",
                resId = R.string.backup_err_engine_unknown_column, resArgs = listOf(table, unknown))
            val stmt = db.compileStatement(
                "INSERT OR REPLACE INTO `$table` (${cols.joinToString(separator = "`, `", prefix = "`", postfix = "`")}) " +
                    "VALUES (${marks.joinToString()})")
            try {
                cols.forEachIndexed { idx, col ->
                    val type = colTypes.getValue(col) // 已校验存在（见上方 unknown 检查）
                    val v = row.opt(col)
                    when {
                        v == null || v == JSONObject.NULL -> stmt.bindNull(idx + 1)
                        type.contains("INT") -> stmt.bindLong(idx + 1, asNumber(table, col, v).toLong())
                        type.contains("REAL") || type.contains("FLOA") || type.contains("DOUB") || type.contains("NUM") ->
                            stmt.bindDouble(idx + 1, asNumber(table, col, v).toDouble())
                        v is Number -> stmt.bindString(idx + 1, v.toString())
                        v is Boolean -> stmt.bindLong(idx + 1, if (v) 1L else 0L)
                        else -> stmt.bindString(idx + 1, v.toString())
                    }
                }
                stmt.executeInsert()
            } finally {
                stmt.close()
            }
        }
    }
}
