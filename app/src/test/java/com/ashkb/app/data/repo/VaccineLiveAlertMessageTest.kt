package com.ashkb.app.data.repo

import android.content.Context
import com.ashkb.app.R
import com.ashkb.app.data.db.AppDatabase
import com.ashkb.app.data.entity.MedClass
import com.ashkb.app.data.entity.MedFrequency
import com.ashkb.app.data.entity.Medication
import com.ashkb.app.data.entity.VaccineRecord
import com.ashkb.app.domain.DerivedAlerts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * v1.2.13（审查报告 §十 第 2 条 / P0-2 第 14 条）：活疫苗警报的**文案分档**走真实保存路径。
 *
 * 背景：`ui_alert_live_vaccine` 的文案一直写着「AS 患者使用生物制剂 / DMARD 期间接种活疫苗有
 * 严重感染风险」，但代码从未核过「用户到底在不在用」这个前提——文案在断言一件没查过的事。
 * 本次把药单读进来做分档，本文件证明三件事走的是**真实** `saveVaccineRecord` → 告警落库链路：
 *  1. 药单里没有免疫抑制药物时，落库的是通用文案（不点名，也不暗示「你没在用」）；
 *  2. 有生物制剂 / JAK 在用时，落库的文案点名该药；
 *  3. 已停用（`is_archived = 1`）的药不算在用——`listActive()` 只取在用。
 *
 * 断言一律用「落库的 message 文本」，不是 `VaccineSafety` 的返回值：判定对了但仓储没接上，
 * 前一类测试全绿而用户看到的仍是通用文案，这正是要防的那种「只测了纯函数」。
 *
 * 读库用 `listUnackedByType` 按 `refDate` 过滤，且每个用例用**互不相同的日期**与前缀 id，
 * 因为 Robolectric 里 [AppDatabase] 是进程内单例、用例之间表内容是看得见的
 * （同 [LabCountScopeTest] 的约定）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class VaccineLiveAlertMessageTest {

    private val ctx: Context get() = RuntimeEnvironment.getApplication()
    private val repo by lazy { HealthRepository(ctx) }
    private val db get() = AppDatabase.get(ctx)

    @Before
    fun resetDatabaseSingleton() {
        val field = AppDatabase::class.java.getDeclaredField("instance")
        field.isAccessible = true
        field.set(null, null)
    }

    private fun <T> io(block: suspend () -> T): T = runBlocking(Dispatchers.IO) { block() }

    private fun medication(id: String, name: String, cls: MedClass) = Medication(
        id = id,
        name = name,
        nameKey = name.lowercase(),
        medClass = cls.name,
        route = "oral",
        dose = "1 片",
        frequency = MedFrequency.DAILY.name,
        startDate = "2026-10-01",
        createdAt = "2026-10-01T08:00:00",
        updatedAt = "2026-10-01T08:00:00",
    )

    private fun vaccine(id: String, date: String, name: String) = VaccineRecord(
        id = id,
        date = date,
        recordedAt = "${date}T09:00:00",
        vaccineName = name,
        vaccineType = "LIVE",
    )

    /** 保存一针活疫苗后，回读这条记录的告警正文（`null` = 没落告警）。 */
    private fun savedAlertMessage(record: VaccineRecord): String? = io {
        repo.saveVaccineRecord(record)
        db.alertDao()
            .listUnackedByType(DerivedAlerts.VACCINE_LIVE_PENDING)
            .firstOrNull { it.refDate == record.date }
            ?.message
    }

    @Test
    fun `药单里没有免疫抑制药物时落库的是通用文案`() {
        io {
            db.medicationDao().upsert(medication("med-nsaid-generic", "塞来昔布", MedClass.NSAID))
        }
        val record = vaccine("vac-generic", "2026-09-01", "麻腮风疫苗")
        val message = savedAlertMessage(record)

        assertEquals(
            "没在用药单里时不能声称「你没在用」——只发通用版",
            ctx.getString(R.string.ui_alert_live_vaccine, record.vaccineName),
            message,
        )
        assertFalse(
            "通用文案里不该出现点名版才有的过渡语",
            message!!.contains(ctx.getString(R.string.dom_lock_list_separator)),
        )
    }

    @Test
    fun `生物制剂在用时落库的文案点名该药`() {
        io {
            db.medicationDao().upsert(medication("med-biologic-ecy", "依那西普", MedClass.BIOLOGIC))
        }
        val record = vaccine("vac-biologic", "2026-09-02", "麻腮风疫苗")
        val message = savedAlertMessage(record)

        assertEquals(
            "分档文案必须带疫苗名与药名两个占位符",
            ctx.getString(
                R.string.ui_alert_live_vaccine_on_immunosuppressant,
                record.vaccineName,
                "依那西普",
            ),
            message,
        )
        assertTrue("点名的药必须真在正文里", message!!.contains("依那西普"))
    }

    @Test
    fun `已停用的生物制剂不算在用（警报回到通用文案）`() {
        io {
            db.medicationDao().upsert(
                medication("med-biologic-archived", "阿达木单抗", MedClass.BIOLOGIC)
                    .copy(isArchived = true),
            )
        }
        val record = vaccine("vac-archived", "2026-09-03", "麻腮风疫苗")
        val message = savedAlertMessage(record)

        assertEquals(
            "归档药不得参与文案分档——`listActive()` 的 is_archived = 0 就是这条边界",
            ctx.getString(R.string.ui_alert_live_vaccine, record.vaccineName),
            message,
        )
    }
}
