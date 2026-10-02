package com.ashkb.app.data.repo

import android.content.Context
import com.ashkb.app.data.db.AppDatabase
import com.ashkb.app.data.entity.Medication
import com.ashkb.app.data.entity.PlannedSlot
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * v1.1.1（HIGH-2）：**注射顺延必须清掉今天起的计划槽位快照**。
 *
 * 缺陷形态：`postponeInjection` 此前只改 `startDate`，而同文件的 `saveMedication` /
 * `stopMedication` 都会先 `slotDao.deleteOfMedFrom`。顺延把锚点从"今天"挪到"明天"之后，
 * **旧锚点已经物化在今天..+7 的行仍留在 `planned_slots`**，而物化例程是 `INSERT OR IGNORE`
 * （只加不删）→ 那些行永远不会被新锚点覆盖 → `AdherenceCalc.doseCompletion` 给它们配上一个
 * 永远不会存在的日志 → **永久假漏服**，并在其落入的每个报表窗口里都被计为漏服。
 *
 * 为什么必须过真库：这条缺陷的形状就是"表里那几行的存活情况"，纯函数证明不了。
 * 顺带锁住另一半：只删**今天起**的行，昨天的历史计划行必须原样保留（与停药/编辑同口径）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class PostponeInjectionSlotTest {

    private val ctx: Context get() = RuntimeEnvironment.getApplication()
    private val db get() = AppDatabase.get(ctx)
    private val repo by lazy { MedicationRepository(ctx) }

    @Before
    fun resetDatabaseSingleton() {
        val field = AppDatabase::class.java.getDeclaredField("instance")
        field.isAccessible = true
        field.set(null, null)
    }

    private fun <T> io(block: suspend () -> T): T = runBlocking(Dispatchers.IO) { block() }

    private val today: LocalDate get() = LocalDate.now()

    @Test
    fun `顺延清掉今天起的计划槽位并留下审计行`() {
        val med = medication("med-postpone-1")
        val yesterday = today.minusDays(1).toString()

        io {
            db.medicationDao().upsert(med)
            db.plannedSlotDao().insertAll(
                listOf(
                    slot("pslot-p1-y", yesterday, med),                  // 历史：必须保留
                    slot("pslot-p1-t", today.toString(), med),           // 今天起：必须清掉
                    slot("pslot-p1-1", today.plusDays(1).toString(), med),
                    slot("pslot-p1-3", today.plusDays(3).toString(), med),
                ),
            )
            repo.postponeInjection(med, today.plusDays(2))
        }

        val remaining = io {
            db.plannedSlotDao().betweenForMed(
                med.id, today.minusDays(30).toString(), today.plusDays(30).toString(),
            )
        }
        assertEquals(
            "顺延后只剩昨天那一行——旧锚点在今天起的行若留着，就会变成永久假漏服",
            listOf(yesterday),
            remaining.map { it.date },
        )

        val changes = io { db.medicationChangeDao().byMed(med.id) }
        assertEquals("顺延必须留一条药品变更审计行（此前是本文件唯一不留痕的变更）", 1, changes.size)
        assertEquals("schedule_change", changes.first().changeType)
        assertEquals(today.plusDays(2).toString(), changes.first().effectiveDate)
    }

    @Test
    fun `顺延更新锚点日期`() {
        val med = medication("med-postpone-2")
        val target = today.plusDays(3)

        io {
            db.medicationDao().upsert(med)
            repo.postponeInjection(med, target)
        }

        assertEquals(target.toString(), io { db.medicationDao().byId(med.id)?.startDate })
    }

    @Test
    fun `没有计划槽位时顺延不会凭空造行`() {
        val med = medication("med-postpone-3")

        io {
            db.medicationDao().upsert(med)
            repo.postponeInjection(med, today.plusDays(1))
        }

        assertTrue(
            "本方法只负责「清 + 改锚点」，重新物化由调用方（TodayViewModel.rescheduleInternal）负责",
            io { db.plannedSlotDao().betweenForMed(med.id, today.toString(), today.plusDays(30).toString()) }.isEmpty(),
        )
    }

    // ---- 夹具 ----

    private fun medication(id: String) = Medication(
        id = id, name = "恩利", nameKey = "adalimumab", medClass = "BIOLOGIC",
        route = "injection", dose = "25mg", frequency = "BIW",
        startDate = today.toString(), createdAt = nowIso(), updatedAt = nowIso(),
    )

    private fun slot(id: String, date: String, med: Medication) = PlannedSlot(
        id = id, date = date, medId = med.id, medKey = med.nameKey, medName = med.name,
        slotKey = "inj", slotTime = "08:00", doseSnapshot = med.dose, createdAt = nowIso(),
    )
}
