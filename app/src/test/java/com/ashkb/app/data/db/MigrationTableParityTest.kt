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

    private val schemaFile = File("$SCHEMA_DIR/$ASHKB_DB_VERSION.json")

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

    /**
     * v1.0.78（批次 4 收尾）：**加列型迁移**的逐列比对（`lab_results.ai_abnormal`）。
     *
     * 与新表那条同理：老用户升级走 `ALTER TABLE ... ADD COLUMN`，新装用户走 Room 按实体建表，
     * 两条路径必须得到**同样的列定义**。做法：
     *  ① 按**上一版导出的 schema**（`18.json` 的 createSql）建出「老库」——不手抄建表语句，
     *     免得抄错反而把真问题掩盖过去；
     *  ② 跑 18→19 迁移；
     *  ③ 把 `PRAGMA table_info` 的真实列与 `19.json` 声明的列逐列比对（列名 → 类型 + 是否非空）。
     *
     * 只比列定义、不比 createSql 原文：`ADD COLUMN` 只能把新列**追加到末尾**，而 Room 新建库时
     * 按实体声明序排在 `abnormal` 之后——列位置差异是 SQLite 层面的书写差异，按列名取值不受影响。
     */
    @Test
    fun `化验表 ai_abnormal 加列迁移与导出 schema 逐列一致`() {
        val ctx: Context = RuntimeEnvironment.getApplication()
        val dbName = "migration-parity-lab-column-test.db"
        ctx.deleteDatabase(dbName)

        val migration = AppDatabase.ALL_MIGRATIONS.first { it.startVersion == 18 }
        assertEquals("迁移清单里应当有 18→19", 19, migration.endVersion)
        // 老库的表结构取自上一版导出的 schema（版本号同样由迁移自身给出，不写死）
        val oldSchema = JSONObject(File("$SCHEMA_DIR/${migration.startVersion}.json").readText())
        val oldSql = entityOf(oldSchema, LAB_TABLE).getString("createSql").replace("\${TABLE_NAME}", LAB_TABLE)

        val config = SupportSQLiteOpenHelper.Configuration.builder(ctx)
            .name(dbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(migration.startVersion) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(oldSql)
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                    // 空库直接落到老版本，不需要升级逻辑
                }
            })
            .build()
        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        try {
            val sdb = helper.writableDatabase
            migration.migrate(sdb)

            val entity = entityOf(JSONObject(schemaFile.readText()), LAB_TABLE)
            assertEquals(
                "迁移后的列定义与导出 schema 不一致——升级上来的库会与新装的不一样",
                schemaColumns(entity),
                liveColumns(sdb, LAB_TABLE),
            )
            assertTrue(
                "新列在真实库里的书写应与 schema 的 createSql 逐字一致（含反引号）",
                liveCreateSql(sdb, LAB_TABLE).contains("`$NEW_COLUMN` TEXT"),
            )
        } finally {
            helper.close()
            ctx.deleteDatabase(dbName)
        }
    }

    /** 真实库里的列定义（列名 → `类型|是否非空`），与 [schemaColumns] 同一口径，可直接 assertEquals。 */
    private fun liveColumns(db: SupportSQLiteDatabase, table: String): Map<String, String> {
        val out = mutableMapOf<String, String>()
        db.query("PRAGMA table_info(`$table`)").use { c ->
            val nameIdx = c.getColumnIndex("name")
            val typeIdx = c.getColumnIndex("type")
            val notNullIdx = c.getColumnIndex("notnull")
            while (c.moveToNext()) {
                out[c.getString(nameIdx)] = "${c.getString(typeIdx)}|${c.getInt(notNullIdx) != 0}"
            }
        }
        return out
    }

    /** 导出 schema 声明的列定义（列名 → `affinity|notNull`）。 */
    private fun schemaColumns(entity: JSONObject): Map<String, String> {
        val fields = entity.getJSONArray("fields")
        return (0 until fields.length()).associate {
            val f = fields.getJSONObject(it)
            f.getString("columnName") to "${f.getString("affinity")}|${f.getBoolean("notNull")}"
        }
    }

    private fun liveCreateSql(db: SupportSQLiteDatabase, table: String): String {
        db.query("SELECT sql FROM sqlite_master WHERE type = 'table' AND name = ?", arrayOf(table)).use { c ->
            return if (c.moveToFirst()) c.getString(0) ?: "" else ""
        }
    }

    private fun entityOf(schema: JSONObject, table: String): JSONObject {
        val entities = schema.getJSONObject("database").getJSONArray("entities")
        return (0 until entities.length())
            .map { entities.getJSONObject(it) }
            .first { it.getString("tableName") == table }
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
        const val LAB_TABLE = "lab_results"
        const val NEW_COLUMN = "ai_abnormal"
        const val SCHEMA_DIR = "schemas/com.ashkb.app.data.db.AppDatabase"
    }
}
