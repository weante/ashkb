package com.ashkb.app.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import com.ashkb.app.domain.KbSearch
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 迁移路径的证明集，三件事：
 *
 * 1. 历史 schema 文件（4.json … 16.json）足以驱动真实升级链：
 *    以每个历史版本建库 → 跑真实 ALL_MIGRATIONS → Room 再按 21.json 校验结构。
 * 2. v8 → v9 的 search_text 回填是**有内容语义的**，不只是加一列：
 *    先在 v8 库里写一行真实数据，升级后该行 search_text 必须等于
 *    `KbSearch.searchText(title, summary, payload)`（title \n summary \n payload）。
 * 3. 回填 SQL 一旦被删掉，本测试立刻变红（源码守卫）——
 *    这条是「改坏必红」的那一半：上一条证明它现在对，这一条保证它以后错不了。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class MigrationPathProofTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    private fun upgradeFrom(startVersion: Int) {
        val name = "backfill-proof-$startVersion.db"
        helper.createDatabase(name, startVersion).close()
        helper.runMigrationsAndValidate(
            name,
            ASHKB_DB_VERSION,
            true,
            *AppDatabase.ALL_MIGRATIONS,
        ).close()
    }

    @Test fun `upgrade from v4`() = upgradeFrom(4)
    @Test fun `upgrade from v5`() = upgradeFrom(5)
    @Test fun `upgrade from v6`() = upgradeFrom(6)
    @Test fun `upgrade from v7`() = upgradeFrom(7)
    @Test fun `upgrade from v8`() = upgradeFrom(8)
    @Test fun `upgrade from v9`() = upgradeFrom(9)
    @Test fun `upgrade from v10`() = upgradeFrom(10)
    @Test fun `upgrade from v11`() = upgradeFrom(11)
    @Test fun `upgrade from v12`() = upgradeFrom(12)
    @Test fun `upgrade from v13`() = upgradeFrom(13)
    @Test fun `upgrade from v14`() = upgradeFrom(14)
    @Test fun `upgrade from v15`() = upgradeFrom(15)
    @Test fun `upgrade from v16`() = upgradeFrom(16)

    /**
     * 行为面：v8 时代就存在的行，升级后必须被回填成与 KbSearch.searchText 完全相同的字符串。
     * 若 MIGRATION_8_9 只 ADD COLUMN 而不 UPDATE，这里会读到 null 而失败。
     */
    @Test
    fun `upgrade from v8 backfills search_text for pre-existing rows`() {
        val name = "backfill-content-8.db"
        val id = "itx-legacy-row"
        val title = "旧条目标题"
        val summary = "旧条目摘要"
        val payload = "{\"content\":\"旧正文\"}"

        helper.createDatabase(name, 8).use { db ->
            db.execSQL(
                "INSERT INTO `kb_entries` (`id`,`category`,`title`,`summary`,`severity_level`," +
                    "`applicable_scene`,`source_name`,`source_url`,`source_tier`,`adapted_at`," +
                    "`review_due`,`version`,`payload`) VALUES (?,?,?,?,'high','','种子来源','','S1'," +
                    "'2024-01-01','2025-01-01',1,?)",
                arrayOf(id, "interaction", title, summary, payload),
            )
        }

        helper.runMigrationsAndValidate(
            name,
            ASHKB_DB_VERSION,
            true,
            *AppDatabase.ALL_MIGRATIONS,
        ).use { db ->
            db.query("SELECT `search_text` FROM `kb_entries` WHERE `id` = ?", arrayOf(id)).use { c ->
                assertTrue("迁移后应仍能查到 v8 时代写入的行", c.moveToFirst())
                assertEquals(
                    "search_text 必须等于 KbSearch.searchText(title, summary, payload)",
                    KbSearch.searchText(title, summary, payload),
                    c.getString(0),
                )
            }
        }
    }

    /**
     * 源码守卫：MIGRATION_8_9 里那段回填 SQL 是承重的。
     * 删掉 `UPDATE kb_entries SET search_text = ...` 或改掉拼接表达式，本测试即失败。
     */
    @Test
    fun `MIGRATION_8_9 still backfills search_text`() {
        val source = listOf(
            File("src/main/java/com/ashkb/app/data/db/AppDatabase.kt"),
            File("app/src/main/java/com/ashkb/app/data/db/AppDatabase.kt"),
        ).firstOrNull { it.isFile }
            ?: throw AssertionError("找不到 AppDatabase.kt（工作目录=${File(".").absolutePath}）")

        val text = source.readText()
        val start = text.indexOf("MIGRATION_8_9")
        val end = text.indexOf("MIGRATION_9_10")
        assertTrue("AppDatabase.kt 里找不到 MIGRATION_8_9", start >= 0)
        assertTrue("MIGRATION_9_10 应出现在 MIGRATION_8_9 之后", end > start)

        val body = text.substring(start, end).replace(Regex("\\s+"), "")
        assertTrue(
            "MIGRATION_8_9 必须新增 search_text 列",
            body.contains("ADDCOLUMN`search_text`TEXT"),
        )
        assertTrue(
            "MIGRATION_8_9 必须回填 search_text —— 删掉这段 SQL 本测试即失败",
            body.contains("UPDATE`kb_entries`SET`search_text`="),
        )
        assertTrue(
            "回填表达式必须与 KbSearch.searchText 同构：title || char(10) || summary || char(10) || payload",
            body.contains("`title`||char(10)||`summary`||char(10)||`payload`"),
        )
    }
}
