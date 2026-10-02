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
import com.ashkb.app.domain.SupplementLogStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * P4 报表仓库（M9）：30 天依从统计 / 趋势序列 / 复诊报告快照。
 * 统计口径与 P2 红线一致：打卡状态 done/partial/skipped；症状字段 null=未记录不计入均值。
 *
 * v1.0.76（批次 3a）：用药指标改称「记录内完成度」——分母是**已记录条数**（计划打卡），
 * 按需（PRN）记录全部移出并单独计数（[Adherence.medPrnCount]）。补剂口径不变。
 */
class ReportRepository(private val context: Context) {
    private val db = AppDatabase.get(context)

    data class Adherence(
        val days: Int,
        val medDone: Int, val medPartial: Int, val medSkipped: Int,
        val medTotal: Int, val medRatePct: Int,
        /**
         * v1.0.76（批次 3a）：区间内**按需（PRN）**打卡条数——单独统计，不进 [medTotal] 分母。
         * 界面据此说明「另有 N 次按需用药」，否则 PRN 用户的记录会在这张卡片上凭空消失。
         */
        val medPrnCount: Int,
        /** v1.0.77（批次 3b）：记录内完成度对象（次级说明行用；与上面四个扁平字段同一份数据）。 */
        val record: AdherenceCalc.Completion,
        /**
         * v1.0.77（批次 3b）：**计划剂量口径**的完成度（主指标）——分母是计划剂量数，
         * 完全漏记会算进「未记录」。计划快照缺失时 `plan.hasPlan == false`，
         * 界面必须显示「—（暂无计划快照）」而不给百分比。
         */
        val plan: AdherenceCalc.DoseCompletion,
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
        // v1.0.76（批次 3a）：完成度的分母只算计划打卡；按需（PRN）另计，不混进这个比率
        val done = logDao.countScheduledBetweenStatus(f, t, "done")
        val partial = logDao.countScheduledBetweenStatus(f, t, "partial")
        val skipped = logDao.countScheduledBetweenStatus(f, t, "skipped")
        val total = done + partial + skipped
        val prnCount = logDao.countPrnBetween(f, t)
        // v1.0.48：公式收拢到 AdherenceCalc（此前本文件内联 4 遍，必然漂移）
        val rate = AdherenceCalc.ratePct(done, partial, total)

        // v1.0.77（批次 3b）：**计划剂量口径**——与计划槽位快照逐条配对（键 date+med_id+slot_key）。
        // 先滤掉「今天还没到点」的槽位：否则每天早上的完成度都会因为今日未到点的剂量假性掉一截。
        val planNow = LocalDateTime.now()
        val planned = db.plannedSlotDao().between(f, t).filter { AdherenceCalc.isDue(it, planNow) }
        val planDose = AdherenceCalc.doseCompletion(planned, logDao.listBetween(f, t))

        // C2（v1.0.37）：补剂（营养）依从——与用药同口径（部分完成计 0.5）
        // v1.1.1（HIGH-1）：**先按「天 × 补剂」去重再计数**。旧实现是三条 `COUNT(*)`（按行数），
        // 连点留下的重复行、以及"同日先跳过再补记已服"的两行都会虚增分母——用户手速能改指标。
        // 去重键与卡片上的今日打卡态同源（SupplementLogStatus），两处不会再各算各的。
        val supDao = db.supplementLogDao()
        val supSettled = SupplementLogStatus.latestPerDay(supDao.between(f, t))
            .filter { it.status in AdherenceCalc.SETTLED_STATUSES }
        val supCount = AdherenceCalc.completionByStatus(supSettled.map { it.status })
        val supDone = supCount.done
        val supPartial = supCount.partial
        val supSkipped = supCount.skipped
        val supTotal = supCount.total
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
            adherence = Adherence(
                days = days,
                medDone = done, medPartial = partial, medSkipped = skipped,
                medTotal = total, medRatePct = rate,
                medPrnCount = prnCount,
                record = AdherenceCalc.Completion(done, partial, skipped, total),
                plan = planDose,
            ),
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
        /** v1.0.76（批次 3a）：本期按需（PRN）打卡条数——单独报，不进 [medTotal] 分母。 */
        val medPrnCount: Int,
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
        // v1.0.76（批次 3a）：同 overview()——完成度只含计划打卡，按需（PRN）单独报数
        val md = logDao.countScheduledBetweenStatus(f, t, "done")
        val mp = logDao.countScheduledBetweenStatus(f, t, "partial")
        val ms = logDao.countScheduledBetweenStatus(f, t, "skipped")
        val mt = md + mp + ms
        val mPrn = logDao.countPrnBetween(f, t)
        val mr = AdherenceCalc.ratePct(md, mp, mt)

        val supDao = db.supplementLogDao()
        // v1.1.1（HIGH-1）：与 overview() 同一份去重口径（按「天 × 补剂」取最后一次表态）
        val supCount = AdherenceCalc.completionByStatus(
            SupplementLogStatus.latestPerDay(supDao.between(f, t))
                .filter { it.status in AdherenceCalc.SETTLED_STATUSES }
                .map { it.status },
        )
        val sd = supCount.done
        val sp = supCount.partial
        val ss = supCount.skipped
        val st = supCount.total
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
            medPrnCount = mPrn,
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
