package com.ashkb.app.data.db

import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 批次 2（测试安全网）：**迁移清单与 schema 导出的机器可判校验**。
 *
 * 背景（第三份审查报告 P0-4，已复核）：库版本已到 17，但此前既没有 `room.schemaLocation`，
 * 也没有任何迁移测试——「加了版本号却忘了写/忘了注册迁移」只能等**用户升级时崩**才发现，
 * 而那正是最不可挽回的一类故障（用户数据在库里）。
 *
 * 本测试锁四件事：
 *  ① 迁移链从 1 到 [ASHKB_DB_VERSION] **逐级无缺口**（不能跳版本，也不能重复版本段）；
 *  ② 每一条迁移都是 `start + 1 == end`（Room 的逐级升级前提）；
 *  ③ 当前版本号的 schema 文件**已导出并入库**（改实体后必须提交 `app/schemas` 下的 diff）；
 *  ④ schema 文件里记录的版本号与代码常量一致（防止两者各说各话）。
 *
 * ⚠️ 本测试**不执行**迁移 SQL（那需要各历史版本的 schema，见 HANDOFF §9 的补录方案）；
 * 它保证的是「迁移链完整且与版本号对齐」这一层。
 */
class MigrationCoverageTest {

    private val schemaDir = "schemas/com.ashkb.app.data.db.AppDatabase"

    @Test
    fun `迁移链从 1 到当前版本逐级无缺口`() {
        val sorted = AppDatabase.ALL_MIGRATIONS.sortedBy { it.startVersion }
        assertTrue("迁移清单为空", sorted.isNotEmpty())
        assertEquals("第一条迁移必须从版本 1 开始", 1, sorted.first().startVersion)

        var expected = 1
        sorted.forEach { m ->
            assertEquals("迁移必须逐级递增（start+1 == end）", m.startVersion + 1, m.endVersion)
            assertEquals("版本 $expected 之后缺少迁移——用户从该版本升级会崩", expected, m.startVersion)
            expected = m.endVersion
        }
        assertEquals("最后一条迁移必须正好到达当前版本", ASHKB_DB_VERSION, expected)
    }

    @Test
    fun `没有重复或乱序的版本段`() {
        val starts = AppDatabase.ALL_MIGRATIONS.map { it.startVersion }
        assertEquals("同一版本段出现了多条迁移", starts.size, starts.toSet().size)
        assertEquals(
            "迁移清单应按版本升序书写（便于人工核对）",
            starts.sorted(),
            starts,
        )
    }

    @Test
    fun `当前版本的 schema 文件已导出并入库`() {
        val f = File(schemaDir, "$ASHKB_DB_VERSION.json")
        assertTrue(
            "缺少 schema 导出文件：${f.absolutePath}。" +
                "改实体后必须重新构建并提交 app/schemas 下的 diff（CI 有 `git diff --exit-code app/schemas` 门）。",
            f.exists(),
        )
        assertTrue("schema 文件为空", f.length() > 1_000)
    }

    @Test
    fun `schema 文件记录的版本号与代码常量一致`() {
        val f = File(schemaDir, "$ASHKB_DB_VERSION.json")
        // Room 导出的结构是 { "formatVersion": 1, "database": { "version": 17, ... } }——
        // 版本号在 database 里，不在顶层（写错会拿到 null 而不是静默通过）
        val version = JSONObject(f.readText()).getJSONObject("database").getInt("version")
        assertEquals("schema 文件里的 version 与 ASHKB_DB_VERSION 不一致", ASHKB_DB_VERSION, version)
    }
}
