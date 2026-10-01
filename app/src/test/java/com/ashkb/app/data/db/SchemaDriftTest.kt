package com.ashkb.app.data.db

import android.content.Context
import androidx.room.Room
import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 批次 2（测试安全网）：**schema 漂移校验（JVM / Robolectric，进 CI）**。
 *
 * 干什么：新建一个库，把它的**真实建表语句**与仓库里导出的
 * `app/schemas/…/[ASHKB_DB_VERSION].json`（Room 依据当前实体生成）逐表比对。
 *
 * 为什么放在单测而不是 androidTest：Robolectric 提供真实 SQLite，Room 能照常建库，
 * 于是这条检查**每次 CI 都会跑**；放在 androidTest 里则只有人手动跑真机时才生效。
 * （真机侧另有 `RealDatabaseSchemaTest` 验证**生产工厂 + 真实库文件**。）
 *
 * 它抓的是「实体改了、迁移没跟上」这类事故——Room 的一致性校验只在**用户真的升级**时触发，
 * 那时已经晚了；这里提前到 CI。
 *
 * 与 [MigrationCoverageTest] 互补：一个管「迁移链完整」，一个管「结构与实体对得上」。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class SchemaDriftTest {

    private val schemaFile = File("schemas/com.ashkb.app.data.db.AppDatabase/$ASHKB_DB_VERSION.json")

    @Test
    fun 新建库的建表语句与导出的schema逐表一致() {
        val ctx: Context = RuntimeEnvironment.getApplication()
        val dbName = "schema-drift-test.db"
        ctx.deleteDatabase(dbName)

        val db = Room.databaseBuilder(ctx, AppDatabase::class.java, dbName)
            .addMigrations(*AppDatabase.ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            // 触发建库
            db.openHelper.writableDatabase

            val entities = JSONObject(schemaFile.readText())
                .getJSONObject("database")
                .getJSONArray("entities")

            val liveSql = mutableMapOf<String, String>()
            db.openHelper.readableDatabase
                .query("SELECT name, sql FROM sqlite_master WHERE type = 'table'")
                .use { c ->
                    while (c.moveToNext()) liveSql[c.getString(0)] = c.getString(1) ?: ""
                }

            for (i in 0 until entities.length()) {
                val e = entities.getJSONObject(i)
                val table = e.getString("tableName")
                val expected = e.getString("createSql").replace("\${TABLE_NAME}", table)
                val actual = liveSql[table]
                assertNotNull("表 `$table` 在真实库里不存在（schema 与实体不一致）", actual)
                assertEquals(
                    "表 `$table` 的建表语句与导出的 schema 不一致——迁移/实体改动没对齐",
                    normalize(expected),
                    normalize(actual!!),
                )
            }
            // 反向：库里不应存在 schema 未声明的业务表
            // （排除框架/系统的表：sqlite_* 内部表、room_* 记账表、android_metadata 区域表）
            val extra = liveSql.keys.filter { k ->
                !k.startsWith("sqlite_") && !k.startsWith("room_") && k != "android_metadata" &&
                    (0 until entities.length()).none { entities.getJSONObject(it).getString("tableName") == k }
            }
            assertEquals("库里存在 schema 未声明的表（可能是漏提交 schema 或多余的手工建表）", emptyList<String>(), extra)
        } finally {
            db.close()
            ctx.deleteDatabase(dbName)
        }
    }

    /**
     * 抹平**语义等价**的书写差异后比对：
     *  · 空白（SQLite 存原文，Room 的 createSql 可能换行/多空格）
     *  · `IF NOT EXISTS`——Room 导出的 createSql 带它，而实际建表执行后 sqlite_master 里不带
     *  · 末尾分号
     * 其余任何差异都是真差异（列名/类型/默认值/NOT NULL 等）。
     */
    private fun normalize(sql: String): String = sql.trim()
        .replace(Regex("\\s+"), " ")
        .replace("CREATE TABLE IF NOT EXISTS ", "CREATE TABLE ")
        .trimEnd(';')
        .trim()
}
