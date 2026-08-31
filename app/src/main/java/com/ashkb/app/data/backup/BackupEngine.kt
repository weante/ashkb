package com.ashkb.app.data.backup

import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/**
 * 备份引擎：全库逐表导出（JSON 行阵列）+ manifest（行数 + 逐表 SHA-256），
 * 恢复走「pre-restore 快照（由调用方先行完成）→ 事务覆盖写入 → 双校验自证」
 * ——协议 §4 备份内容 / §5 checksum 双校验 / §6 恢复五步的实现层。
 *
 * 泛型 cursor 直读：不依赖 Room 实体类，未来加表无需改本引擎（表清单动态发现）。
 */
object BackupEngine {

    class BackupException(msg: String, cause: Throwable? = null) : Exception(msg, cause)

    const val SCHEMA_VERSION = 6

    /** 表清单：用户表（排除 Room 元数据 / 系统表），固定字典序保证备份文件确定性。 */
    fun tableNames(db: SupportSQLiteDatabase): List<String> =
        db.query("SELECT name FROM sqlite_master WHERE type='table' " +
            "AND name NOT LIKE 'sqlite_%' AND name != 'room_master_table' AND name != 'android_metadata' " +
            "ORDER BY name").use { c ->
            buildList {
                while (c.moveToNext()) add(c.getString(0))
            }
        }

    fun sha256Hex(data: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it) }

    // ======================= 导出 =======================

    data class ExportResult(val payload: String, val rowTotal: Int, val tableCount: Int)

    fun export(db: SupportSQLiteDatabase, nowIso: String): ExportResult {
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
            put("schema_version", SCHEMA_VERSION)
            put("exported_at", nowIso)
            put("tables", tablesJson)
            put("manifest", manifest)
        }
        return ExportResult(payload.toString(), rowTotal, tables.size)
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

    data class VerifyResult(
        val rowsOk: Boolean, val shaOk: Boolean,
        val rowDetails: List<String>, val totalRows: Int,
    )

    /**
     * 恢复：事务内逐表 DELETE + INSERT（按 PRAGMA 列类型绑定），随后双校验。
     * 调用方必须在调用前完成 pre-restore 快照（协议 §6 退路）。
     */
    fun restore(db: SupportSQLiteDatabase, payload: String): VerifyResult {
        val root = JSONObject(payload)
        val schema = root.optInt("schema_version", 0)
        if (schema <= 0) throw BackupException("备份缺少 schema_version")
        if (schema > SCHEMA_VERSION) throw BackupException(
            "备份来自更高版本（schema $schema > 当前 $SCHEMA_VERSION），请先升级 APP")
        val expectedFormat = root.optString("format")
        if (expectedFormat != "ashkb-full") throw BackupException("备份格式不正确：$expectedFormat")

        val tables = root.getJSONObject("tables")
        db.beginTransaction()
        try {
            val names = tables.keys().asSequence().toList()
            for (t in names) {
                insertTable(db, t, tables.getJSONArray(t))
            }
            db.setTransactionSuccessful()
        } catch (e: Exception) {
            throw BackupException("恢复写入失败：${e.message}", e)
        } finally {
            db.endTransaction()
        }
        return verifyAgainst(db, root.getJSONObject("manifest"), root.optJSONObject("tables"))
    }

    /**
     * 双校验：行数快速核对 + 逐表排序序列化 SHA-256 精确比对（协议 §5）。
     * P5 修订 R9：跨 schema 恢复兼容——低版本备份缺少新列（如 v6 的 weekly_weekday2），
     * 按「备份自身的列集」重算摘要而非当前库全列，避免列增迁移后旧备份被误判损坏。
     */
    fun verifyAgainst(db: SupportSQLiteDatabase, manifest: JSONObject, tablesJson: JSONObject? = null): VerifyResult {
        val rowsBad = mutableListOf<String>()
        val shaBad = mutableListOf<String>()
        var total = 0
        val keys = manifest.keys().asSequence().toList()
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
            if (rows.size != m_rows(manifest, t)) rowsBad.add("$t: 期望 ${m_rows(manifest, t)} 实际 ${rows.size}")
            if (tableSha(compare) != m_sha(manifest, t)) shaBad.add(t)
        }
        return VerifyResult(
            rowsOk = rowsBad.isEmpty() && shaBad.isEmpty(),
            shaOk = shaBad.isEmpty(),
            rowDetails = rowsBad + shaBad.map { "$it: SHA-256 不匹配" },
            totalRows = total,
        )
    }

    private fun m_rows(manifest: JSONObject, t: String): Int = manifest.getJSONObject(t).getInt("rows")
    private fun m_sha(manifest: JSONObject, t: String): String = manifest.getJSONObject(t).getString("sha256")

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
            val stmt = db.compileStatement(
                "INSERT OR REPLACE INTO `$table` (${cols.joinToString(separator = "`, `", prefix = "`", postfix = "`")}) " +
                    "VALUES (${marks.joinToString()})")
            try {
                cols.forEachIndexed { idx, col ->
                    val type = colTypes[col] ?: "TEXT"
                    val v = row.opt(col)
                    when {
                        v == null || v == JSONObject.NULL -> stmt.bindNull(idx + 1)
                        type.contains("INT") -> stmt.bindLong(idx + 1, (v as Number).toLong())
                        type.contains("REAL") || type.contains("FLOA") || type.contains("DOUB") || type.contains("NUM") ->
                            stmt.bindDouble(idx + 1, (v as Number).toDouble())
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
