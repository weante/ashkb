package com.ashkb.app.data.repo

import android.content.Context
import androidx.room.withTransaction
import com.ashkb.app.data.db.AppDatabase
import com.ashkb.app.data.db.Ids
import com.ashkb.app.data.entity.Alert
import com.ashkb.app.data.entity.BasdaiRecord
import com.ashkb.app.data.entity.BodyMeasure
import com.ashkb.app.data.entity.CheckupItem
import com.ashkb.app.data.entity.CheckupRecord
import com.ashkb.app.data.entity.DietProfile
import com.ashkb.app.data.entity.EmergencyContact
import com.ashkb.app.data.entity.EmergencyEvent
import com.ashkb.app.data.entity.ExerciseLog
import com.ashkb.app.data.entity.FlareAction
import com.ashkb.app.data.entity.FlareEvent
import com.ashkb.app.data.entity.FlareTrigger
import com.ashkb.app.data.entity.FoodAvoidItem
import com.ashkb.app.data.entity.ImagingRecord
import com.ashkb.app.data.entity.KbEntry
import com.ashkb.app.data.entity.LabResult
import com.ashkb.app.data.entity.Profile
import com.ashkb.app.data.entity.Supplement
import com.ashkb.app.data.entity.SupplementLog
import com.ashkb.app.data.entity.SymptomDaily
import com.ashkb.app.data.entity.VaccineRecord
import com.ashkb.app.data.entity.Vitals
import com.ashkb.app.data.entity.WeightLog
import com.ashkb.app.domain.ImagingImport
import com.ashkb.app.domain.KbSearch
import com.ashkb.app.domain.LabImport
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray

/**
 * P2 健康仓库：M5 症状 / BASDAI / 发作，M4 运动打卡，系统 alerts。
 * 阈值依据：edu-th-001（发热 38.5℃）、edu-th-002（BASDAI ≥4.0×2/14 天）、edu-002（发作第 7 天）。
 */
class HealthRepository(private val context: Context) {
    private val db = AppDatabase.get(context)
    private val symptomDao = db.symptomDailyDao()
    private val basdaiDao = db.basdaiDao()
    private val flareDao = db.flareDao()
    private val exerciseDao = db.exerciseLogDao()
    private val exercisePlanDao = db.exercisePlanDao()
    private val alertDao = db.alertDao()
    private val kbDao = db.kbEntryDao()
    private val profileDao = db.profileDao()
    // P3 DAO
    private val supplementDao = db.supplementDao()
    private val supplementLogDao = db.supplementLogDao()
    private val vitalsDao = db.vitalsDao()
    private val weightDao = db.weightLogDao()
    private val bodyMeasureDao = db.bodyMeasureDao()
    private val dietProfileDao = db.dietProfileDao()
    private val foodAvoidDao = db.foodAvoidItemDao()
    private val checkupItemDao = db.checkupItemDao()
    private val checkupRecordDao = db.checkupRecordDao()
    private val labResultDao = db.labResultDao()
    private val imagingDao = db.imagingDao()
    private val vaccineDao = db.vaccineRecordDao()
    private val emergencyDao = db.emergencyEventDao()
    private val contactDao = db.contactDao()

    companion object {
        const val FEVER_THRESHOLD = 38.5
        const val BASDAI_THRESHOLD = 4.0
        const val BASDAI_WINDOW_DAYS = 14L
        const val FLARE_ALERT_DAY = 7L
    }

    // ---- 观察 ----
    fun observeProfile(): Flow<Profile?> = profileDao.observe()
    fun observeSymptom(date: String): Flow<SymptomDaily?> = symptomDao.observeByDate(date)
    fun observeBasdai(): Flow<List<BasdaiRecord>> = basdaiDao.observeRecent()
    fun observeActiveFlare(): Flow<FlareEvent?> = flareDao.observeActive()
    fun observeFlareRecent(): Flow<List<FlareEvent>> = flareDao.observeRecent()
    fun observeExerciseLogs(date: String): Flow<List<ExerciseLog>> = exerciseDao.observeByDate(date)
    fun observeUnackedAlerts(): Flow<List<Alert>> = alertDao.observeUnacked()

    // ---- M5 每日症状：同日重记复用主键（upsert 幂等）----
    suspend fun saveSymptom(input: SymptomDaily) = db.withTransaction {
        val existing = symptomDao.byDate(input.date)
        val log = if (existing != null) input.copy(id = existing.id)
        else input.copy(id = Ids.new("sym"))
        symptomDao.upsert(log)
        evaluateSymptomAlerts(log)
    }

    /** 红旗三通道：发热→emr-002 / 眼→emr-001 / 神经→emr-004（edu-th-001 阈值联动） */
    private suspend fun evaluateSymptomAlerts(log: SymptomDaily) {
        if (log.feverish && (log.feverTemp ?: 0.0) >= FEVER_THRESHOLD) {
            insertAlertOnce(
                type = "symptom_abnormal", severity = "high", refDate = log.date,
                message = "今日体温 ${log.feverTemp}℃ ≥ 阈值 38.5℃。感染发热需先评估再注射生物制剂——请查看应急处理卡。",
                kbRef = "emr-002",
            )
        }
        if (log.eyeSymptom) {
            insertAlertOnce(
                type = "symptom_abnormal", severity = "high", refDate = log.date,
                message = "记录到眼部症状（眼痛 / 发红 / 畏光 / 视物模糊）——可能是葡萄膜炎，建议尽快眼科就诊。",
                kbRef = "emr-001",
            )
        }
        if (log.neuroRedFlag) {
            insertAlertOnce(
                type = "neuro_red_flag", severity = "high", refDate = log.date,
                message = "记录到神经症状（麻木 / 无力 / 大小便控制变化）——请立即联系医生评估。",
                kbRef = "emr-004",
            )
        }
    }

    // ---- M5 BASDAI ----
    /** 同日多次提交为覆盖更新（复用主键），backfill 标记补写日期 */
    suspend fun saveBasdai(
        date: String,
        q1: Int, q2: Int, q3: Int, q4: Int, q5: Int, q6: Int,
        notes: String?,
        backfill: Boolean = false,
    ) = db.withTransaction {
        val existing = basdaiDao.byDate(date)
        val id = existing?.id ?: Ids.new("bas")
        val record = BasdaiRecord(
            id = id, date = date, recordedAt = nowIso(), backfill = backfill,
            q1Fatigue = q1, q2SpinePain = q2, q3PeripheralPain = q3, q4TenderPoints = q4,
            q5StiffnessDegree = q5, q6StiffnessDuration = q6,
            total = BasdaiRecord.total(q1, q2, q3, q4, q5, q6), notes = notes,
        )
        basdaiDao.upsert(record)
        if (existing != null) basdaiDao.deleteOtherRowsForDate(date, id)
        evaluateBasdaiAlert(record)
    }

    /** edu-th-002：14 天内 ≥2 次记录均 ≥4.0 → basdai_high 警报（提示性非诊断性） */
    private suspend fun evaluateBasdaiAlert(record: BasdaiRecord) {
        if (record.total < BASDAI_THRESHOLD) return
        val from = LocalDate.parse(record.date).minusDays(BASDAI_WINDOW_DAYS).toString()
        val recent = basdaiDao.between(from, record.date).filter { it.total >= BASDAI_THRESHOLD }
        if (recent.size >= 2) {
            insertAlertOnce(
                type = "basdai_high", severity = "medium", refDate = record.date,
                message = "14 天内 BASDAI 已 ${recent.size} 次 ≥4.0（本次 ${"%.1f".format(record.total)} 分）。自评活动度持续偏高，建议预约风湿科复诊评估。",
                kbRef = "edu-th-002",
            )
        }
    }

    // ---- M5 发作登记（R18）：开始 / 缓解，第 7 天警报（edu-002） ----
    suspend fun startFlare(
        startDate: String,
        trigger: FlareTrigger,
        actions: List<FlareAction>,
        severityPeak: Int?,
        notes: String?,
    ) = db.withTransaction {
        // 已有活跃发作则不重复开（幂等）
        if (flareDao.activeFlare() != null) return@withTransaction
        flareDao.insert(
            FlareEvent(
                id = Ids.new("flr"), recordedAt = nowIso(), startDate = startDate,
                status = "active", trigger = trigger.name,
                actionsTaken = JSONArray(actions.map { it.name }).toString(),
                severityPeak = severityPeak, notes = notes,
            )
        )
    }

    suspend fun resolveFlare(endDate: String, notes: String?) = db.withTransaction {
        val active = flareDao.activeFlare() ?: return@withTransaction
        flareDao.upsert(
            active.copy(endDate = endDate, status = "resolved", notes = notes ?: active.notes)
        )
    }

    /** 活跃发作第 7 天无缓解 → medium 警报（edu-002 双通道之一） */
    suspend fun checkFlareDayAlert(today: LocalDate) {
        val active = flareDao.activeFlare() ?: return
        val days = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.parse(active.startDate), today)
        if (days >= FLARE_ALERT_DAY) {
            insertAlertOnce(
                type = "flare_day7", severity = "medium", refDate = today.toString(),
                message = "本次发作已第 $days 天。若自我处理（休息 / 温和活动 / 热敷）后 7–10 天仍无改善，建议联系风湿科（频繁反复发作可能需调整用药）。",
                kbRef = "edu-002",
            )
        }
    }

    // ---- M4 运动打卡 ----
    suspend fun checkInExercise(log: ExerciseLog) = exerciseDao.upsert(log)

    /** R21 次日反馈：更新打卡行 + 返回 exc-010 判读建议 */
    suspend fun saveExerciseFeedback(
        logId: String,
        painChange: String?,
        stiffnessChange: String?,
        isMuscleSoreness: Boolean?,
        note: String?,
    ) = db.withTransaction {
        val rows = exerciseDao.byDate(LocalDate.now().minusDays(1).toString())
        val target = rows.firstOrNull { it.id == logId } ?: return@withTransaction
        exerciseDao.upsert(
            target.copy(
                fbPainChange = painChange, fbStiffnessChange = stiffnessChange,
                fbIsMuscleSoreness = isMuscleSoreness, fbNote = note,
            )
        )
    }

    suspend fun pendingFeedbackLogs(yesterday: String): List<ExerciseLog> =
        exerciseDao.pendingFeedback(yesterday)

    /** R27 矩阵输入：当日运动库（红榜 + 黑榜由引擎分层） */
    suspend fun exerciseLibrary(): List<KbEntry> = kbDao.exercises()

    // ---- B7（v1.0.39）周期康复计划 ----

    fun observeExercisePlans(): Flow<List<com.ashkb.app.data.entity.ExercisePlan>> = exercisePlanDao.observeAll()

    fun observeActiveExercisePlan(): Flow<com.ashkb.app.data.entity.ExercisePlan?> = exercisePlanDao.observeActive()

    /** 幂等种入 4 / 8 / 12 周模板。@return 本次新增条数 */
    suspend fun seedExercisePlans(): Int = db.withTransaction {
        val existing = exercisePlanDao.seedIds().toSet()
        val pending = com.ashkb.app.domain.ExercisePlanTemplates.pending(existing)
        val now = nowIso()
        pending.forEach { t ->
            exercisePlanDao.upsert(
                com.ashkb.app.data.entity.ExercisePlan(
                    id = t.id,
                    title = t.title,
                    weeks = t.weeks,
                    stageMode = t.stageMode,
                    weekStructure = com.ashkb.app.domain.ExercisePlanTemplates.toJson(t.spec),
                    isActive = false,
                    isSeed = true,
                    startDate = null,
                    notes = null,
                    createdAt = now,
                    updatedAt = now,
                )
            )
        }
        pending.size
    }

    /** 启用某计划（同一时刻只允许一个；重新启用即重新起算周次） */
    suspend fun activateExercisePlan(id: String, startDate: String) = db.withTransaction {
        val now = nowIso()
        exercisePlanDao.deactivateAll(now)
        val p = exercisePlanDao.listAll().firstOrNull { it.id == id } ?: return@withTransaction
        exercisePlanDao.upsert(p.copy(isActive = true, startDate = startDate, updatedAt = now))
    }

    suspend fun deactivateExercisePlan(id: String) = db.withTransaction {
        val now = nowIso()
        val p = exercisePlanDao.listAll().firstOrNull { it.id == id } ?: return@withTransaction
        exercisePlanDao.upsert(p.copy(isActive = false, updatedAt = now))
    }

    /** 计划窗口内的运动日志（完成度反算用） */
    suspend fun exerciseLogsBetween(from: String, to: String): List<ExerciseLog> = exerciseDao.between(from, to)

    // ---- K 知识库 ----
    fun observeKbAll(): Flow<List<KbEntry>> = kbDao.observeAll()
    fun observeKbByCategory(category: String): Flow<List<KbEntry>> = kbDao.observeByCategory(category)
    suspend fun kbByCategory(category: String): List<KbEntry> = kbDao.listByCategory(category)
    /** v9：走单列 search_text 检索 + 结果上限；输入侧防抖在 KnowledgeViewModel */
    fun searchKb(q: String): Flow<List<KbEntry>> = kbDao.search(q, KbSearch.MAX_RESULTS)
    suspend fun kbEntry(id: String): KbEntry? = kbDao.byId(id)

    /** v10（B2）：写入知识库个人备注层——只动 user_note，种子内容不受影响。空白即清除。 */
    suspend fun saveKbNote(id: String, note: String?) = kbDao.updateUserNote(id, note?.trim()?.ifBlank { null })

    /** v10（B2）：有个人备注的条目数 */
    fun observeKbNoteCount(): Flow<Int> = kbDao.observeNoteCount()

    /** 复核到期警报：按条目去重（refDate 存条目 id），启动与知识库入口各查一次 */
    suspend fun checkReviewDue(today: String) {
        val overdue = kbDao.overdueReview(today)
        overdue.forEach { e ->
            insertAlertOnce(
                type = "review_due", severity = "low", refDate = e.id,
                message = "知识条目「${e.title}」已过复核日（${e.reviewDue}）——内容可能过期，就医核对时请以医生意见为准。",
                kbRef = e.id,
            )
        }
    }

    // ---- alerts ----
    suspend fun ackAlert(id: String) = alertDao.ack(id, nowIso())

    /** 同 (type, refDate) 只报一次——避免每次保存症状刷屏 */
    private suspend fun insertAlertOnce(type: String, severity: String, refDate: String, message: String, kbRef: String?) {
        if (alertDao.countByRef(type, refDate) > 0) return
        alertDao.insert(
            Alert(
                id = Ids.new("alt"), alertType = type, severity = severity,
                message = message, kbRef = kbRef, refDate = refDate, createdAt = nowIso(),
            )
        )
    }

    // =========================================================================
    // P3：M2/M3 骨健康抗炎与营养
    // =========================================================================

    // ---- M2 补剂 ----
    fun observeSupplements(): Flow<List<Supplement>> = supplementDao.observeActive()
    fun observeSupplementLogs(date: String): Flow<List<SupplementLog>> = supplementLogDao.observeByDate(date)

    /** U3 单个补剂的服用历史（近 90 天，仅 done） */
    fun observeSupplementHistory(supId: String, name: String): Flow<List<SupplementLog>> =
        supplementLogDao.observeHistoryFor(supId, name, LocalDate.now().minusDays(90).toString())

    suspend fun saveSupplement(supp: Supplement) {
        val now = nowIso()
        val toSave = if (supp.id.isBlank()) supp.copy(id = Ids.new("sup"), createdAt = now, updatedAt = now)
        else supp.copy(updatedAt = now)
        supplementDao.upsert(toSave)
    }

    suspend fun archiveSupplement(id: String) = supplementDao.archive(id, nowIso())

    /** U1 真删（supplement_logs 快照自持，历史不受影响） */
    suspend fun deleteSupplement(id: String) = supplementDao.delete(id)

    suspend fun checkInSupplement(log: SupplementLog) = db.withTransaction {
        val existing = log.supId?.let { supplementLogDao.find(it, log.date, log.slotKey) }
        val toSave = if (existing != null) log.copy(id = existing.id)
        else log.copy(id = Ids.new("slog"))
        supplementLogDao.upsert(toSave)
    }

    // ---- M3 体征 ----
    fun observeVitals(date: String): Flow<Vitals?> = vitalsDao.observeLatestByDate(date)
    fun observeVitalsBetween(from: String, to: String): Flow<List<Vitals>> = vitalsDao.observeBetween(from, to)

    suspend fun saveVitals(input: Vitals) = db.withTransaction {
        val existing = vitalsDao.latestByDate(input.date)
        // 同日只保留最新一条（upsert 按 id 覆盖，先查再用同一 id）
        val log = if (existing != null) input.copy(id = existing.id)
        else input.copy(id = Ids.new("vit"))
        vitalsDao.upsert(log)
    }

    /** U4 误录删除 */
    suspend fun deleteVitals(id: String) = vitalsDao.delete(id)

    // ---- M3 体重 ----
    fun observeWeightRecent(limit: Int = 30): Flow<List<WeightLog>> = weightDao.observeRecent(limit)
    fun observeWeightToday(date: String): Flow<WeightLog?> = weightDao.observeByDate(date)

    suspend fun saveWeight(date: String, weightKg: Double, notes: String?) = db.withTransaction {
        val existing = weightDao.byDate(date)
        val log = if (existing != null) existing.copy(weightKg = weightKg, notes = notes, recordedAt = nowIso())
        else WeightLog(id = Ids.new("wgt"), date = date, recordedAt = nowIso(), weightKg = weightKg, notes = notes)
        weightDao.upsert(log)
    }

    /** U4 误录删除 */
    suspend fun deleteWeight(id: String) = weightDao.delete(id)

    // ---- M3 身体指标 ----
    fun observeBodyMeasureLatest(): Flow<BodyMeasure?> = bodyMeasureDao.observeLatest()
    fun observeBodyMeasureRecent(limit: Int = 10): Flow<List<BodyMeasure>> = bodyMeasureDao.observeRecent(limit)

    suspend fun saveBodyMeasure(input: BodyMeasure) {
        val toSave = if (input.id.isBlank()) input.copy(id = Ids.new("bm")) else input
        bodyMeasureDao.upsert(toSave)
    }

    /** U4 误录删除 */
    suspend fun deleteBodyMeasure(id: String) = bodyMeasureDao.delete(id)

    // ---- M3 饮食画像 + 忌口 ----
    fun observeDietProfile(): Flow<DietProfile?> = dietProfileDao.observe()
    suspend fun saveDietProfile(profile: DietProfile) =
        dietProfileDao.upsert(profile.copy(updatedAt = nowIso()))

    /** U4 清除画像（回到未设置态） */
    suspend fun deleteDietProfile() = dietProfileDao.delete()

    fun observeFoodAvoidAll(): Flow<List<FoodAvoidItem>> = foodAvoidDao.observeAll()
    fun observeFoodAvoidByCategory(category: String): Flow<List<FoodAvoidItem>> = foodAvoidDao.observeByCategory(category)

    suspend fun saveFoodAvoid(item: FoodAvoidItem) {
        val now = nowIso()
        val toSave = if (item.id.isBlank()) item.copy(id = Ids.new("fav"), createdAt = now, updatedAt = now)
        else item.copy(updatedAt = now)
        foodAvoidDao.upsert(toSave)
    }

    suspend fun deleteFoodAvoid(id: String) = foodAvoidDao.delete(id)

    // =========================================================================
    // P3：M6 复诊管理
    // =========================================================================

    // ---- 复诊项目 ----
    fun observeCheckupItems(): Flow<List<CheckupItem>> = checkupItemDao.observeActive()

    suspend fun saveCheckupItem(item: CheckupItem) {
        val now = nowIso()
        val toSave = if (item.id.isBlank()) item.copy(id = Ids.new("cki"), createdAt = now, updatedAt = now)
        else item.copy(updatedAt = now)
        checkupItemDao.upsert(toSave)
    }

    suspend fun deactivateCheckupItem(id: String) = checkupItemDao.deactivate(id, nowIso())

    /**
     * C10（v1.0.37）：一键种入生物制剂筛查 / 续方节点（结核 / 乙肝 / 丙肝筛查 + 续方随访）。
     * 幂等：按 name 去重，已存在的不重复建。
     * @return 本次新增条数
     */
    suspend fun seedBiologicScreeningItems(): Int = db.withTransaction {
        val existing = checkupItemDao.listActive().map { it.name }.toSet()
        val pending = com.ashkb.app.domain.ScreeningSeeds.pending(existing)
        val now = nowIso()
        pending.forEach { s ->
            checkupItemDao.upsert(
                CheckupItem(
                    id = Ids.new("cki"),
                    name = s.name,
                    checkType = s.checkType,
                    cycleDays = s.cycleDays,
                    linkedMedId = null,
                    kbRef = null,
                    isActive = true,
                    notes = s.notes,
                    createdAt = now,
                    updatedAt = now,
                )
            )
        }
        pending.size
    }

    // ---- 复诊记录 ----
    fun observeCheckupRecent(limit: Int = 20): Flow<List<CheckupRecord>> = checkupRecordDao.observeRecent(limit)
    fun observeCheckupByItem(itemId: String, limit: Int = 10): Flow<List<CheckupRecord>> =
        checkupRecordDao.observeByItem(itemId, limit)

    suspend fun saveCheckupRecord(record: CheckupRecord) {
        val toSave = if (record.id.isBlank()) record.copy(id = Ids.new("crec")) else record
        checkupRecordDao.upsert(toSave)
    }

    // ---- 化验结果 ----
    fun observeLabByCheckup(checkupId: String): Flow<List<LabResult>> = labResultDao.observeByCheckup(checkupId)
    fun observeLabTrend(testName: String, limit: Int = 20): Flow<List<LabResult>> =
        labResultDao.observeTrend(testName, limit)

    suspend fun saveLabResult(result: LabResult) {
        // 自动判读异常
        val withAbnormal = if (result.abnormal == null && result.value != null) {
            val abn = when {
                result.refHigh != null && result.value > result.refHigh -> "high"
                result.refLow != null && result.value < result.refLow -> "low"
                else -> "normal"
            }
            result.copy(abnormal = abn)
        } else result
        val toSave = if (withAbnormal.id.isBlank()) withAbnormal.copy(id = Ids.new("lab")) else withAbnormal
        labResultDao.upsert(toSave)
    }

    /** 化验结果总览（化验 Tab：按日期倒序，含 AI 导入的独立化验单） */
    fun observeLabRecent(limit: Int = 100): Flow<List<LabResult>> = labResultDao.observeRecent(limit)

    // ---- M6 影像记录（v1.0.4 AI 导入） ----
    fun observeImagingRecords(): Flow<List<ImagingRecord>> = imagingDao.observeAll()

    suspend fun saveImagingRecord(record: ImagingRecord) {
        val toSave = if (record.id.isBlank()) record.copy(id = Ids.new("img")) else record
        imagingDao.upsert(toSave)
    }

    // ---- M6 AI 导入：ReportImportParser 解析结果 → 实体入库 ----

    /** 化验单导入：逐行写入 lab_results（事务内原子完成；无关联复诊记录，checkupId = null；医院并入备注） */
    suspend fun importLabReport(import: LabImport) = db.withTransaction {
        val date = import.date ?: LocalDate.now().toString()
        val backfill = date != LocalDate.now().toString()
        val note = listOfNotNull(
            import.hospital?.let { "医院：$it" },
            import.note,
        ).joinToString(" · ").ifBlank { null }
        import.rows.forEach { row ->
            saveLabResult(
                LabResult(
                    id = "", date = date, recordedAt = nowIso(), backfill = backfill,
                    checkupId = null, testName = row.testName,
                    value = row.value, valueText = row.valueText,
                    unit = row.unit, refLow = row.refLow, refHigh = row.refHigh,
                    abnormal = row.abnormal, notes = note,
                )
            )
        }
    }

    /** 影像报告导入：AI 解析结果 → imaging_records，「对比」并入备注 */
    suspend fun importImagingReport(import: ImagingImport) {
        val examDate = import.date ?: LocalDate.now().toString()
        val notesParts = buildList {
            import.compare?.takeIf { it.isNotBlank() && it != "无" }?.let { add("对比：$it") }
        }
        imagingDao.upsert(
            ImagingRecord(
                id = Ids.new("img"), examDate = examDate, recordedAt = nowIso(),
                backfill = examDate != LocalDate.now().toString(),
                modality = import.modality, bodyPart = import.bodyPart,
                hospital = import.hospital, findings = import.findings,
                conclusion = import.conclusion,
                notes = notesParts.joinToString("\n").ifBlank { null },
            )
        )
    }

    // ---- v11：化验 / 影像归属复诊记录（B10 后续增强，手动选择） ----

    /** 把某一天的全部化验归属到指定复诊记录（null = 解除归属）。 */
    suspend fun linkLabsByDate(date: String, checkupId: String?) = labResultDao.linkByDate(date, checkupId)

    /** 把某条影像记录归属到指定复诊记录（null = 解除归属）。 */
    suspend fun linkImagingToCheckup(imagingId: String, checkupId: String?) =
        imagingDao.linkToCheckup(imagingId, checkupId)

    /** 某条复诊记录下的影像 */
    fun observeImagingByCheckup(checkupId: String): Flow<List<ImagingRecord>> =
        imagingDao.observeByCheckup(checkupId)

    // ---- 疫苗记录 ----
    fun observeVaccinesAll(): Flow<List<VaccineRecord>> = vaccineDao.observeAll()
    fun observeVaccinesByType(type: String): Flow<List<VaccineRecord>> = vaccineDao.observeByType(type)

    suspend fun saveVaccineRecord(record: VaccineRecord) = db.withTransaction {
        val toSave = if (record.id.isBlank()) record.copy(id = Ids.new("vac")) else record
        vaccineDao.upsert(toSave)
        // 活疫苗 + 未确认 → 疫苗安全警报（itx-010/012 联动）
        if (record.vaccineType == "LIVE" && record.doctorConfirm == "PENDING") {
            insertAlertOnce(
                type = "vaccine_live_pending", severity = "high", refDate = record.date,
                message = "记录了活疫苗（${record.vaccineName}）但医生确认状态为「待确认」——AS 患者使用生物制剂 / DMARD 期间接种活疫苗有严重感染风险，请务必先与风湿科医生确认（itx-010/012）。",
                kbRef = "itx-010",
            )
        }
    }

    // =========================================================================
    // P3：M7 紧急卡
    // =========================================================================

    // ---- 紧急事件 ----
    fun observeEmergencyRecent(limit: Int = 20): Flow<List<EmergencyEvent>> = emergencyDao.observeRecent(limit)
    fun observeEmergencyByScene(scene: String): Flow<List<EmergencyEvent>> = emergencyDao.observeByScene(scene)

    suspend fun saveEmergencyEvent(event: EmergencyEvent) {
        val toSave = if (event.id.isBlank()) event.copy(id = Ids.new("eev")) else event
        emergencyDao.upsert(toSave)
    }

    // ---- 紧急联系人 ----
    fun observeEmergencyContacts(): Flow<List<EmergencyContact>> = contactDao.observeEmergency()
    fun observeAllContacts(): Flow<List<EmergencyContact>> = contactDao.observeAll()

    suspend fun saveContact(contact: EmergencyContact) {
        val now = nowIso()
        val toSave = if (contact.id.isBlank()) contact.copy(id = Ids.new("ctc"), createdAt = now, updatedAt = now)
        else contact.copy(updatedAt = now)
        contactDao.upsert(toSave)
    }

    suspend fun deleteContact(id: String) = contactDao.delete(id)
}

// ---- 工具扩展（BodyMeasure 体重获取——weight 来自参数而非字段，此处占位） ----
private fun BodyMeasure.weightKg(): Double? = null
