package com.ashkb.app.data.backup

import android.content.Context
import androidx.room.Room
import com.ashkb.app.data.db.AppDatabase
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 恢复事务的**失败注入**：证明「失败 = 整体回滚 = 零写入」，而不是「抛了个异常」。
 *
 * 为什么需要这一组：`restoreGate` 只覆盖了互斥（见 `RestoreGateTest`），
 * `BackupEngine.restore` 的业务五步（校验 → 清表 → 重插 → 双校验 → 提交）此前
 * 只有 happy path 的设备实测，**没有任何一条断言能证明它失败时不写库**。
 *
 * 判据（三条一起看才算数）：
 *  1. `VerifyResult.rowsOk == false`（校验真的发现了差异）；
 *  2. 逐表行数与恢复前**完全相等**（事务未 setSuccessful ⇒ endTransaction 整体回滚）；
 *  3. 恢复前埋的 canary 行内容**一字未变**（不是「行数碰巧相等」）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class RestoreRollbackTest {

    private val stamp = "2026-11-19T00:00:00Z"

    @Test
    fun `未篡改的备份对照组装恢复成功（证明下面的红不是环境问题）`() {
        withDb("restore-control.db") { db ->
            insertCanary(db, "原始标题")
            val raw = db.openHelper.writableDatabase
            val exported = BackupEngine.export(raw, stamp)

            val result = BackupEngine.restore(raw, exported.payload, BackupEngine.RestoreMode.FULL_ROLLBACK)

            assertTrue("对照组的 rowsOk 必须为 true", result.rowsOk)
            assertTrue("对照组的 shaOk 必须为 true", result.shaOk)
            assertEquals("恢复后 canary 行仍在", "原始标题", titleOf(db, CANARY_ID))
        }
    }

    @Test
    fun `SHA 被篡改的备份必须整体回滚且零写入`() {
        withDb("restore-rollback.db") { db ->
            insertCanary(db, "原始标题")
            val raw = db.openHelper.writableDatabase
            val before = counts(db)
            val tampered = tamperOneRow(BackupEngine.export(raw, stamp).payload)

            val result = BackupEngine.restore(raw, tampered, BackupEngine.RestoreMode.FULL_ROLLBACK)

            assertFalse("篡改后 rowsOk 必须为 false", result.rowsOk)
            assertFalse("篡改后 shaOk 必须为 false", result.shaOk)
            assertEquals("失败恢复必须零写入：逐表行数与恢复前完全一致", before, counts(db))
            assertEquals("canary 行必须仍在且内容未被改写", "原始标题", titleOf(db, CANARY_ID))
        }
    }

    @Test
    fun `备份来自更高 schema 时整体拒绝且零写入`() {
        withDb("restore-schema-newer.db") { db ->
            insertCanary(db, "原始标题")
            val raw = db.openHelper.writableDatabase
            val before = counts(db)
            val payload = JSONObject(BackupEngine.export(raw, stamp).payload)
                .put("schema_version", BackupEngine.schemaVersion(raw) + 1)
                .toString()

            val error = runCatching {
                BackupEngine.restore(raw, payload, BackupEngine.RestoreMode.FULL_ROLLBACK)
            }.exceptionOrNull()

            assertTrue("必须是 BackupException", error is BackupEngine.BackupException)
            assertEquals(
                "保留可解释的 resId",
                com.ashkb.app.R.string.backup_err_engine_schema_newer,
                (error as BackupEngine.BackupException).resId,
            )
            assertEquals("拒绝发生在事务之前：零写入", before, counts(db))
            assertEquals("canary 行必须仍在", "原始标题", titleOf(db, CANARY_ID))
        }
    }

    @Test
    fun `备份携带未知表名时整体拒绝且零写入`() {
        withDb("restore-unknown-table.db") { db ->
            insertCanary(db, "原始标题")
            val raw = db.openHelper.writableDatabase
            val before = counts(db)
            val payload = JSONObject(BackupEngine.export(raw, stamp).payload).apply {
                getJSONObject("tables").put("evil_table", org.json.JSONArray())
            }.toString()

            val error = runCatching {
                BackupEngine.restore(raw, payload, BackupEngine.RestoreMode.FULL_ROLLBACK)
            }.exceptionOrNull()

            assertTrue("必须是 BackupException", error is BackupEngine.BackupException)
            assertEquals("拒绝发生在事务之前：零写入", before, counts(db))
            assertEquals("canary 行必须仍在", "原始标题", titleOf(db, CANARY_ID))
        }
    }

    /**
     * v1.2.21（独立审查 F1）：`checkup_attachments.file_name` 来自备份（外部输入），
     * 恢复侧必须做形状白名单——越界的名字会让 `fileOf()` 拼出附件目录之外的路径。
     */
    @Test
    fun `备份携带越界附件文件名时整体拒绝且零写入`() {
        withDb("restore-bad-attachment-name.db") { db ->
            insertCanary(db, "原始标题")
            val raw = db.openHelper.writableDatabase
            val before = counts(db)
            val payload = JSONObject(BackupEngine.export(raw, stamp).payload).apply {
                getJSONObject("tables").put(
                    "checkup_attachments",
                    org.json.JSONArray().put(
                        JSONObject().put("id", "catt-evil").put("file_name", "../../databases/app.db"),
                    ),
                )
            }.toString()

            val error = runCatching {
                BackupEngine.restore(raw, payload, BackupEngine.RestoreMode.FULL_ROLLBACK)
            }.exceptionOrNull()

            assertTrue("必须是 BackupException", error is BackupEngine.BackupException)
            assertEquals(
                "保留可解释的 resId",
                com.ashkb.app.R.string.backup_err_engine_bad_attachment_name,
                (error as BackupEngine.BackupException).resId,
            )
            assertEquals("拒绝发生在事务之前：零写入", before, counts(db))
            assertEquals("canary 行必须仍在", "原始标题", titleOf(db, CANARY_ID))
        }
    }

    /** 合法文件名不能被误杀——这是上一条的对照组，避免把白名单写成过严的「一律拒绝」。 */
    @Test
    fun `合法附件文件名不会因附件名校验被拒绝`() {
        withDb("restore-good-attachment-name.db") { db ->
            val raw = db.openHelper.writableDatabase
            val payload = JSONObject(BackupEngine.export(raw, stamp).payload).apply {
                getJSONObject("tables").put(
                    "checkup_attachments",
                    org.json.JSONArray().put(
                        JSONObject().put("id", "catt-1").put("file_name", "catt-1732180800000.jpg"),
                    ),
                )
            }.toString()

            val error = runCatching {
                BackupEngine.restore(raw, payload, BackupEngine.RestoreMode.FULL_ROLLBACK)
            }.exceptionOrNull()
            val resId = (error as? BackupEngine.BackupException)?.resId
            assertTrue(
                "合法文件名不应被附件名校验拒绝（实际 resId=$resId）",
                resId != com.ashkb.app.R.string.backup_err_engine_bad_attachment_name,
            )
        }
    }

    // ---------------- 测试基建 ----------------

    private fun withDb(name: String, body: (AppDatabase) -> Unit) {
        val ctx: Context = RuntimeEnvironment.getApplication()
        ctx.deleteDatabase(name)
        val db = Room.databaseBuilder(ctx, AppDatabase::class.java, name)
            .addMigrations(*AppDatabase.ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            db.openHelper.writableDatabase
            body(db)
        } finally {
            db.close()
        }
    }

    private fun insertCanary(db: AppDatabase, title: String) {
        db.openHelper.writableDatabase.execSQL(
            "INSERT INTO `kb_entries` (`id`,`category`,`title`,`summary`,`severity_level`," +
                "`applicable_scene`,`source_name`,`source_url`,`source_tier`,`adapted_at`," +
                "`review_due`,`version`,`payload`,`search_text`,`user_note`) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
            arrayOf(
                CANARY_ID, "interaction", title, "注入用 canary 行", "low",
                "", "", "", "USR", "2026-01-01",
                "9999-12-31", 1, """{"content":"canary"}""", "canary search", null,
            ),
        )
    }

    private fun counts(db: AppDatabase): Map<String, Int> {
        val raw = db.openHelper.writableDatabase
        return BackupEngine.tableNames(raw).associateWith { table ->
            raw.query("SELECT COUNT(*) FROM `$table`").use { c ->
                c.moveToFirst()
                c.getInt(0)
            }
        }
    }

    private fun titleOf(db: AppDatabase, id: String): String =
        db.openHelper.readableDatabase
            .query("SELECT `title` FROM `kb_entries` WHERE `id` = ?", arrayOf(id))
            .use { c ->
                c.moveToFirst()
                c.getString(0)
            }

    /** 改掉某一行的某个非空字符串字段，manifest 的 SHA 随即失配（列集不变，insertTable 照常能写）。 */
    private fun tamperOneRow(payload: String): String {
        val root = JSONObject(payload)
        val tables = root.getJSONObject("tables")
        val key = tables.keys().asSequence().first { tables.getJSONArray(it).length() > 0 }
        val row = tables.getJSONArray(key).getJSONObject(0)
        val field = row.keys().asSequence()
            .first { row.opt(it) is String && row.optString(it).isNotEmpty() }
        row.put(field, row.getString(field) + "-TAMPERED")
        return root.toString()
    }

    private companion object {
        const val CANARY_ID = "usr-canary-0001"
    }
}
