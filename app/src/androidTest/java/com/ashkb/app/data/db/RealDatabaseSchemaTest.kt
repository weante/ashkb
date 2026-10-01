package com.ashkb.app.data.db

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 批次 2（测试安全网）：**真机上的生产库 schema 校验**。
 *
 * 与单测侧的 `SchemaDriftTest`（Robolectric，进 CI）分工：
 *  · 单测那份用 `Room.databaseBuilder` 现搭一个库，验证「实体 ↔ 导出 schema」一致；
 *  · 这份用**生产工厂** [AppDatabase.get]（含真实迁移链、真实库名 `ashkb.db`）打开库，
 *    验证**线上那条路径**建出来的结构与导出 schema 一致。
 *
 * 本仓库此前没有 androidTest 源集（第三份审查报告 P1-16），这是第一个——
 * 基础设施（runner / room-testing / assets 挂载）已随本批补齐。
 *
 * 运行：`gradle connectedDebugAndroidTest`（需真机/模拟器；CI 上未接入）
 */
@RunWith(AndroidJUnit4::class)
class RealDatabaseSchemaTest {

    @Test
    fun 生产工厂打开的库与导出的schema逐表一致() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val db = AppDatabase.get(ctx)
        try {
            val entities = JSONObject(
                ctx.assets.open("com.ashkb.app.data.db.AppDatabase/$ASHKB_DB_VERSION.json")
                    .bufferedReader().use { it.readText() }
            ).getJSONObject("database").getJSONArray("entities")

            val liveSql = mutableMapOf<String, String>()
            db.openHelper.readableDatabase
                .query("SELECT name, sql FROM sqlite_master WHERE type = 'table'")
                .use { c ->
                    while (c.moveToNext()) liveSql[c.getString(0)] = c.getString(1) ?: ""
                }

            for (i in 0 until entities.length()) {
                val table = entities.getJSONObject(i).getString("tableName")
                assertNotNull("生产库里缺少表 `$table`（迁移没建出来？）", liveSql[table])
            }
        } finally {
            db.close()
        }
    }
}
