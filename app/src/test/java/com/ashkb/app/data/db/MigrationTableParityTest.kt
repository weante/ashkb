package com.ashkb.app.data.db

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * v1.0.77（批次 3b）：**迁移 SQL 与导出 schema 的逐字比对**。
 *
 * 为什么非要单独测这一条：全新安装走的是 Room 按实体生成的建表语句，而**老用户升级走迁移 SQL**——
 * 两条路径必须产出**完全相同**的表结构，否则「升级上来的库」与「新装出来的库」会长得不一样，
 * 且只在升级用户身上出问题（[SchemaDriftTest] 建的是新库，抓不到这种偏差）。
 *
 * 做法：在空库（user_version = 17）上执行 [AppDatabase.ALL_MIGRATIONS] 里那条 17→18，
 * 再把 `sqlite_master` 里真实落下的 `planned_slots` 建表语句与两个索引，与
 * `app/schemas/.../[ASHKB_DB_VERSION].json` 中 Room 依据实体导出的 createSql 逐字比对
 * （比对前只抹平空白 / `IF NOT EXISTS` / 末尾分号这类**语义等价**的书写差异）。
 *
 * 本批新增表时才需要这样一条；历史迁移缺同款校验已记在 HANDOFF（迁移测试补录方案）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class MigrationTableParityTest {

    private val schemaFile = File("schemas/com.ashkb.app.data.db.AppDatabase/$ASHKB_DB_VERSION.json")

    @Test
    fun 计划槽位表与索引的迁移SQL与导出schema逐字一致() {
        val ctx: Context = RuntimeEnvironment.getApplication()
        val dbName = "migration-parity-test.db"
        ctx.deleteDatabase(dbName)

        val config = SupportSQLiteOpenHelper.Configuration.builder(ctx)
            .name(dbName)
            // 空库 + user_version = 17：让 17→18 的迁移成为**唯一**建这张表的地方
            .callback(object : SupportSQLiteOpenHelper.Callback(17) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    // 刻意不建任何表：本测试只关心迁移自己建了什么
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                    // 同上：空库直接落到 17 版，不需要升级逻辑
                }
            })
            .build()
        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        try {
            val sdb = helper.writableDatabase
            val migration = AppDatabase.ALL_MIGRATIONS.first { it.startVersion == 17 }
            assertEquals("迁移清单里应当有 17→18", 18, migration.endVersion)
            migration.migrate(sdb)

            val live = mutableMapOf<String, String>()
            sdb.query("SELECT name, sql FROM sqlite_master WHERE type IN ('table', 'index')").use { c ->
                while (c.moveToNext()) live[c.getString(0)] = c.getString(1) ?: ""
            }

            val entities = JSONObject(schemaFile.readText()).getJSONObject("database").getJSONArray("entities")
            val entity = (0 until entities.length())
                .map { entities.getJSONObject(it) }
                .first { it.getString("tableName") == TABLE }

            assertNotNull("迁移没有建出 `$TABLE` 表", live[TABLE])
            assertEquals(
                "`$TABLE` 的迁移 SQL 与实体导出的 schema 不一致——升级上来的库会与新装的不一样",
                normalize(entity.getString("createSql").replace("\${TABLE_NAME}", TABLE)),
                normalize(live.getValue(TABLE)),
            )

            val indices = entity.getJSONArray("indices")
            assertTrue("实体至少应有 date 与唯一索引两个索引", indices.length() >= 2)
            for (i in 0 until indices.length()) {
                val idx = indices.getJSONObject(i)
                val name = idx.getString("name")
                assertNotNull("迁移没有建出索引 `$name`", live[name])
                assertEquals(
                    "索引 `$name` 的迁移 SQL 与实体导出的 schema 不一致",
                    normalize(idx.getString("createSql").replace("\${TABLE_NAME}", TABLE)),
                    normalize(live.getValue(name)),
                )
            }
        } finally {
            helper.close()
            ctx.deleteDatabase(dbName)
        }
    }

    /** 抹平语义等价的书写差异后比对（与 [SchemaDriftTest] 同款）。 */
    private fun normalize(sql: String): String = sql.trim()
        .replace(Regex("\\s+"), " ")
        .replace("CREATE TABLE IF NOT EXISTS ", "CREATE TABLE ")
        .replace("CREATE UNIQUE INDEX IF NOT EXISTS ", "CREATE UNIQUE INDEX ")
        .replace("CREATE INDEX IF NOT EXISTS ", "CREATE INDEX ")
        .trimEnd(';')
        .trim()

    private companion object {
        const val TABLE = "planned_slots"
    }
}
