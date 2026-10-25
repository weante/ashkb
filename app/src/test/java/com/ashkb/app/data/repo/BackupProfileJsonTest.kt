package com.ashkb.app.data.repo

import android.content.Context
import com.ashkb.app.data.db.AppDatabase
import com.ashkb.app.data.entity.Medication
import com.ashkb.app.data.entity.Profile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * v1.2.11（缺陷工单 A04）：**档案 JSON 的全量保真 + 导入原子性**。
 *
 * 工单给的缺陷是两件事，这里各有用例：
 * 1. **导出丢字段**——只导 14 个药品键，`riskTags` / `doseState` / `taperNote` / `prnReason` /
 *    `duration` / 注射部位状态 / `storage` / `takeWithFood` / 核对状态全都不在。
 *    于是「导出再导入」这个动作**本身会丢数据**，而用户以为只是备份了一下。
 * 2. **导入不在一个事务**——档案与药品逐条写，中途失败会留下「档案已换、药单只写一半」，
 *    用户重试一次就出现重复药。
 *
 * 必须过真库：这两条都是「表里到底写了什么」的事实，纯函数证明不了 upsert 真的落对了行，
 * 也证明不了事务真的把前一半回滚了。
 *
 * 沿用 [RecordDeletionCascadeTest] 的两条基建约定：每个用例前清掉 [AppDatabase] 进程内单例；
 * 所有库操作走 IO 线程（Room 禁止主线程访问，而 Robolectric 的测试线程就是主线程）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class BackupProfileJsonTest {

    private val ctx: Context get() = RuntimeEnvironment.getApplication()
    private val db get() = AppDatabase.get(ctx)
    private val repo by lazy { BackupRepository(ctx) }

    @Before
    fun resetDatabaseSingleton() {
        val field = AppDatabase::class.java.getDeclaredField("instance")
        field.isAccessible = true
        field.set(null, null)
    }

    private fun <T> io(block: suspend () -> T): T = runBlocking(Dispatchers.IO) { block() }

    private fun seedRichMedication(id: String) = Medication(
        id = id,
        name = "柳氮磺吡啶",
        brandName = "维柳芬",
        nameKey = "sulfasalazine",
        medClass = "DMARD",
        riskTags = """["immunosuppressant","photosensitivity"]""",
        route = "oral",
        dose = "500 mg",
        frequency = "DAILY",
        doseState = "taper",
        taperNote = "两周后减到 250 mg",
        prnReason = null,
        takeTimes = """["08:00","20:00"]""",
        weeklyWeekday = null,
        weeklyWeekday2 = null,
        duration = "3 个月",
        startDate = "2026-10-01",
        endDate = null,
        injCycleDays = null,
        injSites = """["left_glute","right_glute"]""",
        injLastSite = "left_glute",
        storage = "room_temp",
        takeWithFood = "with_food",
        checkDoctorTold = true,
        checkLeafletRead = true,
        interactionCheckDate = "2026-10-02",
        isArchived = false,
        notes = "饭后服用",
        createdAt = "2026-10-01T08:00:00",
        updatedAt = "2026-10-01T08:00:00",
    )

    // ---- 1. 导出必须覆盖全部持久化字段 ----

    @Test
    fun `导出再导入后药品的每一个持久化字段都还在`() {
        val id = "med-a04-roundtrip"
        io {
            db.medicationDao().upsert(seedRichMedication(id))
            db.profileDao().upsert(
                Profile(
                    id = 1, displayName = "曾庆城", diagnosis = "强直性脊柱炎",
                    uiMode = "minimal", minimalSince = "2026-10-03T10:00:00",
                    createdAt = "2026-09-01T00:00:00", updatedAt = "2026-09-01T00:00:00",
                ),
            )
        }

        val json = io { repo.exportProfileJson() }

        // 先把库清空，再导入同一份 JSON——这才是「换机」的真实路径。
        io {
            db.medicationDao().upsert(seedRichMedication(id).copy(
                riskTags = null, doseState = null, taperNote = null, duration = null,
                injSites = null, injLastSite = null, storage = null, takeWithFood = null,
                checkDoctorTold = false, checkLeafletRead = false, interactionCheckDate = null,
            ))
        }
        io { repo.importProfileJson(json) }

        val m = io { db.medicationDao().activeByNameKey("sulfasalazine") }
        assertNotNull("同名药没被复用 id，导入后查不到", m)
        assertEquals("风险标签丢了——免疫抑制提示会消失", """["immunosuppressant","photosensitivity"]""", m!!.riskTags)
        assertEquals("减量状态丢了", "taper", m.doseState)
        assertEquals("减量计划说明丢了", "两周后减到 250 mg", m.taperNote)
        assertEquals("疗程时长丢了", "3 个月", m.duration)
        assertEquals("注射部位轮换表丢了——下一次注射不知道该换哪边", """["left_glute","right_glute"]""", m.injSites)
        assertEquals("上一次注射部位丢了", "left_glute", m.injLastSite)
        assertEquals("储存条件丢了", "room_temp", m.storage)
        assertEquals("饭前饭后提示丢了", "with_food", m.takeWithFood)
        assertTrue("「已告知医生」勾选被清掉，R03 待办会重新亮起", m.checkDoctorTold)
        assertTrue("「已读说明书」勾选被清掉", m.checkLeafletRead)
        assertEquals("相互作用核对日期丢了", "2026-10-02", m.interactionCheckDate)
        assertEquals("备注丢了", "饭后服用", m.notes)
    }

    @Test
    fun `导出再导入后档案的极简模式字段也还在`() {
        io {
            db.profileDao().upsert(
                Profile(
                    id = 1, displayName = "曾庆城", diagnosis = "强直性脊柱炎",
                    uiMode = "minimal", minimalSince = "2026-10-03T10:00:00",
                    createdAt = "2026-09-01T00:00:00", updatedAt = "2026-09-01T00:00:00",
                ),
            )
        }

        val json = io { repo.exportProfileJson() }
        io {
            db.profileDao().upsert(
                Profile(
                    id = 1, displayName = "曾庆城", diagnosis = "强直性脊柱炎",
                    uiMode = "normal", minimalSince = null,
                    createdAt = "2026-09-01T00:00:00", updatedAt = "2026-09-01T00:00:00",
                ),
            )
        }
        io { repo.importProfileJson(json) }

        val p = io { db.profileDao().get() }
        assertEquals("导入把用户从极简模式踢回完整模式了", "minimal", p!!.uiMode)
        assertEquals("极简模式进入时刻丢了", "2026-10-03T10:00:00", p.minimalSince)
    }

    @Test
    fun `导出文件带显式格式版本`() {
        val json = io { repo.exportProfileJson() }

        assertTrue("缺 format_version 时导入侧只能靠猜年代，两种缺键语义无法区分", json.contains("\"format_version\": 2"))
    }

    // ---- 2. 导入必须整体原子 ----

    @Test
    fun `药品非法时档案与已写药品一起回滚`() {
        io {
            db.profileDao().upsert(
                Profile(
                    id = 1, displayName = "原名", diagnosis = "原诊断",
                    createdAt = "2026-09-01T00:00:00", updatedAt = "2026-09-01T00:00:00",
                ),
            )
        }
        // 第二支药缺 `name`（`getString` 必抛），第一支是合法的。
        val json = """
            {
              "format": "ashkb-profile",
              "format_version": 2,
              "profile": {"display_name": "新名", "diagnosis": "新诊断"},
              "medications": [
                {"name": "甲氨蝶呤", "name_key": "methotrexate", "med_class": "DMARD", "route": "oral",
                 "dose": "10 mg", "frequency": "WEEKLY", "start_date": "2026-10-01"},
                {"name_key": "broken", "med_class": "OTHER"}
              ]
            }
        """.trimIndent()

        try {
            io { repo.importProfileJson(json) }
            fail("非法药品必须让整次导入失败")
        } catch (_: Exception) {
            // 预期
        }

        assertEquals("档案被写进了半完成状态", "原名", io { db.profileDao().get() }!!.displayName)
        assertNull(
            "第一支合法药也留下了——事务没兜住，用户重试一次就会出现重复药",
            io { db.medicationDao().activeByNameKey("methotrexate") },
        )
    }

    // ---- 3. 缺键语义按版本号区分 ----

    @Test
    fun `v1 旧文件缺键时保留本机原值`() {
        val id = "med-a04-v1-keep"
        io {
            db.medicationDao().upsert(seedRichMedication(id))
            db.profileDao().upsert(
                Profile(
                    id = 1, displayName = "曾庆城", diagnosis = "强直性脊柱炎",
                    comorbidities = """["高血压"]""",
                    createdAt = "2026-09-01T00:00:00", updatedAt = "2026-09-01T00:00:00",
                ),
            )
        }
        // v1 文件：没有 format_version，也没有 risk_tags / comorbidities。
        val v1 = """
            {
              "format": "ashkb-profile",
              "profile": {"display_name": "曾庆城", "diagnosis": "强直性脊柱炎"},
              "medications": [
                {"name": "柳氮磺吡啶", "name_key": "sulfasalazine", "med_class": "DMARD",
                 "route": "oral", "dose": "500 mg", "frequency": "DAILY", "start_date": "2026-10-01"}
              ]
            }
        """.trimIndent()

        io { repo.importProfileJson(v1) }

        assertEquals(
            "v1 文件缺键被当成「清空」了——导入一份三个月前的档案会丢掉后来补的合并症",
            """["高血压"]""",
            io { db.profileDao().get() }!!.comorbidities,
        )
        assertEquals(
            "v1 文件缺 risk_tags 时把本机已有的风险标签清掉了",
            """["immunosuppressant","photosensitivity"]""",
            io { db.medicationDao().activeByNameKey("sulfasalazine") }!!.riskTags,
        )
    }

    @Test
    fun `v2 文件缺键时按空值写入`() {
        val id = "med-a04-v2-clear"
        io {
            db.medicationDao().upsert(seedRichMedication(id))
            db.profileDao().upsert(
                Profile(
                    id = 1, displayName = "曾庆城", diagnosis = "强直性脊柱炎",
                    comorbidities = """["高血压"]""",
                    createdAt = "2026-09-01T00:00:00", updatedAt = "2026-09-01T00:00:00",
                ),
            )
        }
        // v2 文件：用户刻意把风险标签与合并症清空后再导出，导入必须真的清空。
        val v2 = """
            {
              "format": "ashkb-profile",
              "format_version": 2,
              "profile": {"display_name": "曾庆城", "diagnosis": "强直性脊柱炎"},
              "medications": [
                {"name": "柳氮磺吡啶", "name_key": "sulfasalazine", "med_class": "DMARD",
                 "route": "oral", "dose": "500 mg", "frequency": "DAILY", "start_date": "2026-10-01"}
              ]
            }
        """.trimIndent()

        io { repo.importProfileJson(v2) }

        assertNull(
            "v2 文件缺键仍按「保原值」处理——用户刻意清空的合并症被复活了",
            io { db.profileDao().get() }!!.comorbidities,
        )
        assertNull(
            "v2 文件缺 risk_tags 仍按「保原值」处理——用户清掉的风险标签被复活了",
            io { db.medicationDao().activeByNameKey("sulfasalazine") }!!.riskTags,
        )
    }
}
