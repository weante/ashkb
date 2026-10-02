package com.ashkb.app.data.backup

import android.content.Context
import com.ashkb.app.data.db.AppDatabase
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * v1.1.1（S-1 / M6）：`verifyAgainst` 只许遍历**已过白名单的表对象**的键。
 *
 * ### 缺陷形态（S-1，安全）
 * `insertTable` 早已用 `PRAGMA table_info` 封死列名，`unknownTables` 也把 `tables` 的表名过了
 * 白名单——但**校验阶段遍历的是 `manifest` 的键**，而 manifest 同样来自备份文件。
 * `readTable` 会把表名拼进 ``SELECT * FROM `$t` ``，于是一个含反引号的键就能闭合转义，
 * 直达动态 SQL（威胁模型正是本项目自己写下的「诱导用户导入攻击者提供、口令已知的备份」）。
 *
 * ### 缺陷形态（M6，正确性）
 * `tables` 里有、`manifest` 里没有的表，旧实现**既不校验，也照样报 `rowsOk = true`**——
 * "已校验"是假的。
 *
 * 两条都在真库上验：注入键若真的被拿去拼 SQL，SQLite 会抛语法错（本用例因此变红）；
 * 缺 manifest 条目则必须判为不通过。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class BackupVerifyWhitelistTest {

    private val ctx: Context get() = RuntimeEnvironment.getApplication()
    private val db get() = AppDatabase.get(ctx)

    @Before
    fun resetDatabaseSingleton() {
        val field = AppDatabase::class.java.getDeclaredField("instance")
        field.isAccessible = true
        field.set(null, null)
    }

    /** 空表在导出侧算出的摘要：`tableSha(emptyList())` = sha256("")。 */
    private val emptySha: String get() = BackupEngine.sha256Hex(ByteArray(0))

    private fun emptyManifestFor(tables: List<String>): JSONObject = JSONObject().apply {
        tables.forEach { put(it, JSONObject().put("rows", 0).put("sha256", emptySha)) }
    }

    @Test
    fun `manifest 里的注入键不会到达动态 SQL`() {
        val sdb = db.openHelper.writableDatabase
        val real = BackupEngine.tableNames(sdb)
        val tables = JSONObject().apply { real.forEach { put(it, JSONArray()) } }
        val manifest = emptyManifestFor(real).apply {
            // 闭合转义 + UNION 的经典形态：旧实现会把它交给 readTable 拼进 SELECT
            put(
                "`a` WHERE 1=1 UNION SELECT 1,2 FROM `sqlite_master",
                JSONObject().put("rows", 1).put("sha256", "deadbeef"),
            )
        }

        val result = try {
            BackupEngine.verifyAgainst(sdb, manifest, tables)
        } catch (e: Exception) {
            throw AssertionError(
                "manifest 的键被拿去拼 SQL 了——校验阶段必须只遍历 tables（已过白名单）的键",
                e,
            )
        }

        assertTrue(
            "注入键不得出现在校验结果里：${result.rowDetails}",
            result.rowDetails.none { it.contains("UNION") || it.contains("sqlite_master") },
        )
        assertTrue("空库 + 空期望 → 校验通过", result.rowsOk)
    }

    @Test
    fun `tables 里有而 manifest 里没有的表判为未通过`() {
        val sdb = db.openHelper.writableDatabase
        val target = BackupEngine.tableNames(sdb).first()
        val tables = JSONObject().apply { put(target, JSONArray()) }

        val result = BackupEngine.verifyAgainst(sdb, JSONObject(), tables)

        assertFalse(
            "缺 manifest 条目必须判为不通过——旧实现会报 rowsOk=true（「已校验」是假的）",
            result.rowsOk,
        )
        assertTrue("并且要能看出是哪张表", result.rowDetails.any { it.contains(target) })
    }

    @Test
    fun `正常备份形态仍然通过校验`() {
        val sdb = db.openHelper.writableDatabase
        val real = BackupEngine.tableNames(sdb)
        val tables = JSONObject().apply { real.forEach { put(it, JSONArray()) } }

        val result = BackupEngine.verifyAgainst(sdb, emptyManifestFor(real), tables)

        assertTrue("表与 manifest 一一对应时不该误伤：${result.rowDetails}", result.rowsOk)
        assertTrue(result.shaOk)
    }
}
