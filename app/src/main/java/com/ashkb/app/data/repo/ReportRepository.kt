package com.ashkb.app.data.repo

import android.content.Context
import com.ashkb.app.data.db.AppDatabase
import com.ashkb.app.data.entity.BasdaiRecord
import com.ashkb.app.data.entity.CheckupRecord
import com.ashkb.app.data.entity.EmergencyContact
import com.ashkb.app.data.entity.LabResult
import com.ashkb.app.data.entity.Medication
import com.ashkb.app.data.entity.Profile
import com.ashkb.app.data.entity.SymptomDaily
import com.ashkb.app.data.entity.VaccineRecord
import com.ashkb.app.data.entity.Vitals
import com.ashkb.app.data.entity.WeightLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * P4 报表仓库（M9）：30 天依从统计 / 趋势序列 / 复诊报告快照。
 * 统计口径与 P2 红线一致：打卡状态 done/partial/skipped；症状字段 null=未记录不计入均值。
 */
class ReportRepository(private val context: Context) {
    private val db = AppDatabase.get(context)

    data class Adherence(
        val days: Int,
        val medDone: Int, val medPartial: Int, val medSkipped: Int,
        val medTotal: Int, val medRatePct: Int,
    )

    data class ExerciseStat(val doneCount: Int, val skippedCount: Int, val totalMinutes: Int)

    data class SymptomStat(
        val daysRecorded: Int,
        val avgPain: Double?, val avgStiffnessMin: Double?, val nightPainDays: Int,
        val avgFatigue: Double?, val eyeDays: Int, val feverDays: Int,
    )

    data class Overview(
        val adherence: Adherence,
        val exercise: ExerciseStat,
        val symptom: SymptomStat,
        val flareCount: Int, val flareActive: Boolean,
        val basdaiLatest: BasdaiRecord?, val basdaiDelta: Double?, val basdaiCount30: Int,
        val weightLatest: WeightLog?, val weightDelta: Double?,
    )

    data class Trends(
        val basdai: List<BasdaiRecord>,          // 升序
        val symptom: List<SymptomDaily>,         // 升序 90 天
        val weight: List<WeightLog>,             // 升序
        val vitals: List<Vitals>,                // 升序 90 天
    )

    data class CheckupReport(
        val profile: Profile?,
        val meds: List<Medication>,
        val checkups: List<CheckupRecord>,       // 近 180 天
        val labs: List<LabResult>,               // 近 180 天
        val vaccines: List<VaccineRecord>,
        val overview: Overview,
        val basdaiHistory: List<BasdaiRecord>,   // 近 90 天升序
        val nextCheckups: List<CheckupRecord>,   // next_date 未来项
    )

    data class EmergencyCard(
        val profile: Profile?,
        val contacts: List<EmergencyContact>,
        val cards: List<com.ashkb.app.data.entity.KbEntry>,
    )

    suspend fun overview(days: Int = 30): Overview = withContext(Dispatchers.IO) {
        val to = LocalDate.now()
        val from = to.minusDays((days - 1).toLong())
        val f = from.toString(); val t = to.toString()

        val logDao = db.medicationLogDao()
        val done = logDao.countBetweenStatus(f, t, "done")
        val partial = logDao.countBetweenStatus(f, t, "partial")
        val skipped = logDao.countBetweenStatus(f, t, "skipped")
        val total = done + partial + skipped
        val rate = if (total == 0) 0 else ((done + partial * 0.5) / total * 100).toInt()

        val exLogs = db.exerciseLogDao().between(f, t)
        val exDone = exLogs.count { it.status == "done" }
        val exSkipped = exLogs.count { it.status != "done" }
        val exMin = exLogs.filter { it.status == "done" }.sumOf { it.durationMin ?: 0 }

        val symptoms = db.symptomDailyDao().between(f, t)
        val pains = symptoms.mapNotNull { it.painScore }
        val stiff = symptoms.mapNotNull { it.morningStiffnessMin }
        val nights = symptoms.count { (it.nightPain ?: 0) > 0 }
        val fatigue = symptoms.mapNotNull { it.fatigue }
        val eye = symptoms.count { it.eyeSymptom }
        val fever = symptoms.count { it.feverish }

        val flares = db.flareDao().between(f, t)
        val active = db.flareDao().activeFlare() != null

        val basdai = db.basdaiDao().between(f, t).sortedBy { it.date }
        val basdaiLatest = basdai.lastOrNull()
        val basdaiDelta = if (basdai.size >= 2) basdai.last().total - basdai[basdai.size - 2].total else null

        val weights = db.weightLogDao().recent(10)
        val wLatest = weights.firstOrNull()
        val wDelta = if (weights.size >= 2) weights[0].weightKg - weights[1].weightKg else null

        Overview(
            adherence = Adherence(days, done, partial, skipped, total, rate),
            exercise = ExerciseStat(exDone, exSkipped, exMin),
            symptom = SymptomStat(
                daysRecorded = symptoms.size,
                avgPain = pains.takeIf { it.isNotEmpty() }?.average(),
                avgStiffnessMin = stiff.takeIf { it.isNotEmpty() }?.average(),
                nightPainDays = nights,
                avgFatigue = fatigue.takeIf { it.isNotEmpty() }?.average(),
                eyeDays = eye, feverDays = fever,
            ),
            flareCount = flares.size, flareActive = active,
            basdaiLatest = basdaiLatest, basdaiDelta = basdaiDelta, basdaiCount30 = basdai.size,
            weightLatest = wLatest, weightDelta = wDelta,
        )
    }

    suspend fun trends(): Trends = withContext(Dispatchers.IO) {
        val to = LocalDate.now()
        val from90 = to.minusDays(90).toString()
        Trends(
            basdai = db.basdaiDao().between(from90, to.toString()).sortedBy { it.date }.takeLast(12),
            symptom = db.symptomDailyDao().between(from90, to.toString()).sortedBy { it.date },
            weight = db.weightLogDao().recent(60).reversed(),
            vitals = vitalsBetween(from90, to.toString()),
        )
    }

    private suspend fun vitalsBetween(from: String, to: String): List<Vitals> =
        db.vitalsDao().observeBetween(from, to).first()

    suspend fun checkupReport(): CheckupReport = withContext(Dispatchers.IO) {
        val to = LocalDate.now()
        val from180 = to.minusDays(180).toString()
        val from90 = to.minusDays(90).toString()
        CheckupReport(
            profile = db.profileDao().get(),
            meds = db.medicationDao().listActive(),
            checkups = db.checkupRecordDao().between(from180, to.toString()).sortedByDescending { it.date },
            labs = db.labResultDao().between(from180, to.toString()).sortedByDescending { it.date },
            vaccines = vaccinesAll(),
            overview = overview(),
            basdaiHistory = db.basdaiDao().between(from90, to.toString()).sortedBy { it.date },
            nextCheckups = db.checkupRecordDao().between(from180, to.plusDays(90).toString())
                .filter { !it.nextDate.isNullOrBlank() && it.nextDate!! >= to.toString() }
                .sortedBy { it.nextDate },
        )
    }

    private suspend fun vaccinesAll(): List<VaccineRecord> = db.vaccineRecordDao().observeAll().first()

    suspend fun emergencyCard(): EmergencyCard = withContext(Dispatchers.IO) {
        EmergencyCard(
            profile = db.profileDao().get(),
            contacts = contactsAll(),
            cards = db.kbEntryDao().listByCategory("emergency"),
        )
    }

    private suspend fun contactsAll(): List<EmergencyContact> = db.contactDao().observeAll().first()
}
