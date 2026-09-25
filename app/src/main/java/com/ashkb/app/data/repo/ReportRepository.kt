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
import com.ashkb.app.domain.AdherenceCalc
import com.ashkb.app.domain.EmergencyMeds
import com.ashkb.app.domain.LabTrend
import com.ashkb.app.domain.LabTrends
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

    /** C2（v1.0.37）：补剂（营养）依从统计——与用药同口径（部分完成计 0.5）。 */
    data class SupplementAdherence(
        val done: Int, val partial: Int, val skipped: Int, val total: Int, val ratePct: Int,
    )

    data class ExerciseStat(val doneCount: Int, val skippedCount: Int, val totalMinutes: Int)

    data class SymptomStat(
        val daysRecorded: Int,
        val avgPain: Double?, val avgStiffnessMin: Double?, val nightPainDays: Int,
        val avgFatigue: Double?, val eyeDays: Int, val feverDays: Int,
    )

    data class Overview(
        val adherence: Adherence,
        val supplement: SupplementAdherence,
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
        /** 客观炎症指标（ESR / CRP）序列，数据来自 `lab_results`（v1.0.54 方案 C 新增）。 */
        val labs: List<LabTrend> = emptyList(),
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
        val meds: EmergencyMeds.Summary,
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
        // v1.0.48：公式收拢到 AdherenceCalc（此前本文件内联 4 遍，必然漂移）
        val rate = AdherenceCalc.ratePct(done, partial, total)

        // C2（v1.0.37）：补剂（营养）依从——与用药同口径（部分完成计 0.5）
        val supDao = db.supplementLogDao()
        val supDone = supDao.countBetweenStatus(f, t, "done")
        val supPartial = supDao.countBetweenStatus(f, t, "partial")
        val supSkipped = supDao.countBetweenStatus(f, t, "skipped")
        val supTotal = supDone + supPartial + supSkipped
        val supRate = AdherenceCalc.ratePct(supDone, supPartial, supTotal)

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
            supplement = SupplementAdherence(supDone, supPartial, supSkipped, supTotal, supRate),
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

    /**
     * B4（v1.0.38）：周报 / 月报的结构化小结。
     *
     * 只给数据，**文案由 UI 侧组装**（与项目「VM 持状态、UI 持文案」惯例一致）。
     * 口径与 overview() 相同：部分完成计 0.5；症状字段 null 不计入均值。
     */
    data class PeriodicReport(
        val days: Int,
        val medDone: Int, val medPartial: Int, val medSkipped: Int, val medTotal: Int, val medRatePct: Int,
        val suppDone: Int, val suppPartial: Int, val suppSkipped: Int, val suppTotal: Int, val suppRatePct: Int,
        val exDoneDays: Int, val exMinutes: Int,
        val symptomDays: Int, val avgPain: Double?, val avgStiffnessMin: Double?,
        val basdaiCount: Int, val basdaiAvg: Double?, val basdaiDelta: Double?,
        val weightLatest: Double?, val weightDelta: Double?,
        val flareCount: Int,
        val checkupCount: Int, val nextCheckupDate: String?,
    )

    suspend fun periodicReport(days: Int): PeriodicReport = withContext(Dispatchers.IO) {
        val to = LocalDate.now()
        val from = to.minusDays((days - 1).toLong())
        val f = from.toString(); val t = to.toString()

        val logDao = db.medicationLogDao()
        val md = logDao.countBetweenStatus(f, t, "done")
        val mp = logDao.countBetweenStatus(f, t, "partial")
        val ms = logDao.countBetweenStatus(f, t, "skipped")
        val mt = md + mp + ms
        val mr = AdherenceCalc.ratePct(md, mp, mt)

        val supDao = db.supplementLogDao()
        val sd = supDao.countBetweenStatus(f, t, "done")
        val sp = supDao.countBetweenStatus(f, t, "partial")
        val ss = supDao.countBetweenStatus(f, t, "skipped")
        val st = sd + sp + ss
        val sr = AdherenceCalc.ratePct(sd, sp, st)

        val exLogs = db.exerciseLogDao().between(f, t)
        val exDone = exLogs.filter { it.status == "done" }

        val symptoms = db.symptomDailyDao().between(f, t)
        val pains = symptoms.mapNotNull { it.painScore }
        val stiff = symptoms.mapNotNull { it.morningStiffnessMin }

        val basdai = db.basdaiDao().between(f, t).sortedBy { it.date }

        val weights = db.weightLogDao().recent(30).filter { it.date >= f }.sortedByDescending { it.date }

        val checkups = db.checkupRecordDao().between(f, t)
        val nextCheckup = db.checkupRecordDao().between(t, to.plusDays(120).toString())
            .filter { !it.nextDate.isNullOrBlank() && it.nextDate!! >= t }
            .minByOrNull { it.nextDate!! }?.nextDate

        PeriodicReport(
            days = days,
            medDone = md, medPartial = mp, medSkipped = ms, medTotal = mt, medRatePct = mr,
            suppDone = sd, suppPartial = sp, suppSkipped = ss, suppTotal = st, suppRatePct = sr,
            exDoneDays = exDone.map { it.date }.distinct().size,
            exMinutes = exDone.sumOf { it.durationMin ?: 0 },
            symptomDays = symptoms.size,
            avgPain = pains.takeIf { it.isNotEmpty() }?.average(),
            avgStiffnessMin = stiff.takeIf { it.isNotEmpty() }?.average(),
            basdaiCount = basdai.size,
            basdaiAvg = basdai.takeIf { it.isNotEmpty() }?.map { it.total }?.average(),
            basdaiDelta = if (basdai.size >= 2) basdai.last().total - basdai.first().total else null,
            weightLatest = weights.firstOrNull()?.weightKg,
            weightDelta = if (weights.size >= 2) weights.first().weightKg - weights.last().weightKg else null,
            flareCount = db.flareDao().between(f, t).size,
            checkupCount = checkups.size,
            nextCheckupDate = nextCheckup,
        )
    }

    /**
     * 趋势序列。
     *
     * v1.0.45：`rangeDays` 由趋势页的时间范围控件决定（7 / 30 / 90）。
     * 此前窗口写死 90 天，且体重用 `recent(60)` 取「最近 60 条」而与窗口无关——
     * 切到 7 天视图仍会带回更早的体重数据，与其它指标口径不一致。
     * 现在四个序列统一以 `[to - (rangeDays-1), to]` 为窗口，闭区间。
     */
    suspend fun trends(rangeDays: Int = 30): Trends = withContext(Dispatchers.IO) {
        val to = LocalDate.now()
        val from = to.minusDays((rangeDays.coerceIn(1, 365) - 1).toLong()).toString()
        val toStr = to.toString()
        // 炎症指标：lab_results 是按「指标名」自由文本存的，别名归一等口径全在 LabTrends 里。
        // **刻意不套 rangeDays 窗口**：化验几个月才一次，套 7/30/90 天几乎永远为空（v1.0.55 用户实测）。
        val labRows = db.labResultDao().allOrdered()
        Trends(
            // BASDAI 为手工填写，窗口内最多一天一条；不再另设 takeLast 上限（否则 90 天视图会被静默截断）
            basdai = db.basdaiDao().between(from, toStr).sortedBy { it.date },
            symptom = db.symptomDailyDao().between(from, toStr).sortedBy { it.date },
            weight = db.weightLogDao().between(from, toStr),
            vitals = vitalsBetween(from, toStr),
            labs = LabTrends.build(labRows),
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
            meds = EmergencyMeds.summarize(db.medicationDao().listActive(), LocalDate.now().toString()),
            cards = db.kbEntryDao().listByCategory("emergency"),
        )
    }

    private suspend fun contactsAll(): List<EmergencyContact> = db.contactDao().observeAll().first()
}
