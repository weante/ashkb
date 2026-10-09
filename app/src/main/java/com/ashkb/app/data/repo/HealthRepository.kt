package com.ashkb.app.data.repo

import android.content.Context
import androidx.room.withTransaction
import com.ashkb.app.R
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
import com.ashkb.app.domain.AdherenceCalc
import com.ashkb.app.domain.CheckupDeletion
import com.ashkb.app.domain.ClinicalThresholds
import com.ashkb.app.domain.DerivedAlerts
import com.ashkb.app.domain.ImagingImport
import com.ashkb.app.domain.KbSearch
import com.ashkb.app.domain.LabImport
import com.ashkb.app.domain.MinimalMode
import com.ashkb.app.domain.ScreeningSeeds
import com.ashkb.app.domain.SupplementHistory
import com.ashkb.app.domain.VaccineSafety
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray

/**
 * P2 健康仓库：M5 症状 / BASDAI / 发作，M4 运动打卡，系统 alerts。
 * 阈值依据：edu-th-001（发热 38.5℃）、edu-th-002（BASDAI ≥4.0×2/14 天）、edu-002（发作第 7 天）。
 */
class HealthRepository(private val context: Context) {
    private val db = AppDatabase.get(context)
    /**
     * v1.0.80（批次 6）：删除复诊记录要连带清掉它名下附件的**磁盘文件**，
     * 故这里复用附件仓储（同一个库、同一份同步开关）——文件在哪、远端怎么收尾只有它知道。
     */
    private val attachmentRepo = AttachmentRepository(context)
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
        // v1.0.77（批次 4）：原 `BASDAI_THRESHOLD = 4.0` 已删除——与 `ClinicalThresholds.BASDAI_HIGH` 是同一个值，
    // 两个常量并存会让「改了一处忘了另一处」成为必然（第三份审查报告 §六）。一律走 `ClinicalThresholds.basdaiHigh()`。
        //
        // v1.1.2（批次 18）：原 `BASDAI_WINDOW_DAYS = 14L` 同样删除——它是「持续 2 周」这句话的代码化，
        // 但比默认自评周期（28 天）还短，使 `basdai_high` 在默认配置下不可达（第四份审查报告 §五）。
        // 判据改用「最近两次自评」（见 `evaluateBasdaiAlert`），不再需要任何窗口常数。
        const val FLARE_ALERT_DAY = 7L
    }

    // ---- 观察 ----
    fun observeProfile(): Flow<Profile?> = profileDao.observe()

    /**
     * v1.0.65 B12：区间内**有症状记录**的日期集合——极简模式「连续缺失」判定用。
     * 只取 date 一列即可，避免把整行症状带出来。
     */
    suspend fun symptomDatesBetween(from: String, to: String): Set<String> =
        symptomDao.between(from, to).mapTo(mutableSetOf()) { it.date }

    /**
     * v1.0.65 B12：切换极简模式。
     * `uiMode` 与 `minimalSince` **成对维护**（极简必须有进入时刻，退出必须清空）——
     * 任何别的写法都会让 [com.ashkb.app.domain.MinimalMode.isConsistent] 不成立。
     */
    suspend fun setMinimalMode(minimal: Boolean, nowIso: String) = db.withTransaction {
        val cur = profileDao.get() ?: return@withTransaction
        profileDao.upsert(
            cur.copy(
                uiMode = if (minimal) MinimalMode.MODE_MINIMAL else MinimalMode.MODE_NORMAL,
                minimalSince = if (minimal) nowIso else null,
                updatedAt = nowIso,
            )
        )
    }
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

    /**
     * v1.0.80（批次 6）：删除某天的症状记录（误录）。
     *
     * **为什么必须连带清警报**：症状记录是红旗警报（发热 / 眼 / 神经）的唯一依据，
     * 警报又按 `(type, ref_date=记录日)` 去重、写记录时自动生成。只删记录不清警报，
     * 症状页顶部就会一直挂着一条 high 级提示，点进去指向一个已经不存在的记录——
     * 用户既消不掉它（重存一条同样的记录也只会被去重逻辑忽略），也无从判断它是否还成立。
     *
     * 按「记录日」清是有意的：`ref_date` 就是派生它的那条记录的日期，一一对应，不会误伤别的日期。
     * 已被用户确认（ack）的警报保留——那是「他确实看过」的留痕，且不出现在未读列表里。
     *
     * v1.1.2（批次 18）：**发热警报有两个来源**（症状自评的「发热 + 体温」与体征录入的体温），
     * 共用同一个 `(type='symptom_abnormal', ref_date)` 去重键。故删除症状记录时，
     * 只有当**体征侧也不再发热**才清这条警报——体征还挂着 38.7 ℃ 时把它删掉，
     * 等于凭空抹掉一条仍然成立的急症提示。
     */
    suspend fun deleteSymptom(date: String) = db.withTransaction {
        val row = symptomDao.byDate(date) ?: return@withTransaction
        symptomDao.delete(row.id)
        alertDao.deleteUnackedByRef(DerivedAlerts.NEURO_RED_FLAG, row.date)
        if (!vitalsStillFever(row.date)) {
            alertDao.deleteUnackedByRef(DerivedAlerts.SYMPTOM_ABNORMAL, row.date)
        }
    }

    /** 红旗三通道：发热→emr-002 / 眼→emr-001 / 神经→emr-004（edu-th-001 阈值联动） */
    private suspend fun evaluateSymptomAlerts(log: SymptomDaily) {
        if (log.feverish && (log.feverTemp ?: 0.0) >= FEVER_THRESHOLD) {
            insertAlertOnce(
                type = "symptom_abnormal", severity = "high", refDate = log.date,
                message = context.getString(
                    R.string.ui_alert_fever,
                    log.feverTemp.toString(),
                    FEVER_THRESHOLD.toString(),
                ),
                kbRef = "emr-002",
            )
        }
        if (log.eyeSymptom) {
            insertAlertOnce(
                type = "symptom_abnormal", severity = "high", refDate = log.date,
                message = context.getString(R.string.ui_alert_eye),
                kbRef = "emr-001",
            )
        }
        if (log.neuroRedFlag) {
            insertAlertOnce(
                type = "neuro_red_flag", severity = "high", refDate = log.date,
                message = context.getString(R.string.ui_alert_neuro),
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

    /**
     * `edu-th-002`：BASDAI 活动度**持续**偏高 → `basdai_high` 警报（提示性非诊断性）。
     *
     * v1.1.2（批次 18）把判据从「14 天窗口内 ≥2 次 ≥4.0」换成「**最近两次自评均 ≥4.0**」
     * （审查报告 §五）。旧判据在默认配置下**不可达**：BASDAI 提醒的默认周期是 28 天
     * （`ReminderConfigRepository.DEFAULT_BASDAI_CYCLE`），按默认节奏自评的人在 14 天内
     * 根本攒不出第二条记录，`basdai_high` 成了死分支——除非他中途手动自评。
     * 窗口是**自评节奏**的函数，不该在代码里另写一个比节奏还短的常数。
     *
     * 新判据与任何周期对齐（7 / 14 / 28 / 56 / 84 天都可触发），且仍然**不会**在只有
     * 单次高分时触发——单次偏高由报表页的即时提示承担（`ReportScreen` 的
     * `basdai_high_alert_note`），警报留给「持续」这件事。
     */
    private suspend fun evaluateBasdaiAlert(record: BasdaiRecord) {
        if (!ClinicalThresholds.basdaiHigh(record.total)) return
        val lastTwo = basdaiDao.latestTwo()
        if (lastTwo.size < 2) return
        if (lastTwo.all { ClinicalThresholds.basdaiHigh(it.total) }) {
            insertAlertOnce(
                type = "basdai_high", severity = "medium", refDate = record.date,
                message = context.getString(
                    R.string.ui_alert_basdai_high,
                    "%.1f".format(ClinicalThresholds.BASDAI_HIGH),
                    "%.1f".format(record.total),
                ),
                kbRef = "edu-th-002",
            )
        }
    }

    /**
     * v1.0.80（批次 6）：删除一条 BASDAI 自评（误录）。
     *
     * 三个连带动作，一个都不能少：
     * ① 按 **id** 删——`deleteOtherRowsForDate` 是「同日只留一条」的覆盖语义，
     *    拿它当删除入口会顺手抹掉同日的其它行；同日不变量由写入侧维护，删除侧不碰它。
     * ② 清掉**该记录日**派生出来的 `basdai_high` 警报（未确认的）——警报按 ref_date 一一对应，
     *    删掉记录后它就成了无依据的幽灵，而且重存一条也不会再生成（去重键相同）。
     * ③ 调用方（SymptomViewModel）还要重排 BASDAI 提醒——`dueDate` 由 `latest()` 推算，
     *    删掉最近一条会让下次评估日期整体前移（提醒层的事，不在数据层做）。
     */
    suspend fun deleteBasdai(id: String) = db.withTransaction {
        val row = basdaiDao.byId(id) ?: return@withTransaction
        basdaiDao.delete(id)
        alertDao.deleteUnackedByRef(DerivedAlerts.BASDAI_HIGH, row.date)
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
                message = context.getString(R.string.ui_alert_flare_days, days),
                kbRef = "edu-002",
            )
        }
    }

    /**
     * v1.0.80（批次 6）：**编辑**一次发作登记（记错了改：诱因 / 采取的措施 / 峰值 / 备注 / 起止日期）。
     *
     * 为什么改日期也要重算警报：「第 7 天」警报只与 `startDate` 有关，而它的 `ref_date` 是**报警当日**。
     * 把开始日往后挪 5 天，那条已经报出来的「已第 8 天」就凭空多算了——所以先把**旧窗口内**、
     * 且**新窗口覆盖不到**的未确认警报清掉，再按新窗口重判一次（`insertAlertOnce` 幂等，仍成立会补回来）。
     *
     * [FlareEvent.endDate] / `status` 也可在此改：把窗口右端一起纳入判定，
     * 否则「提前结束发作」后，那条针对「仍在发作」的警报会继续挂着。
     */
    suspend fun saveFlare(event: FlareEvent, today: LocalDate = LocalDate.now()) = db.withTransaction {
        val before = flareDao.byId(event.id)
        flareDao.upsert(event)
        if (before != null) {
            dropUncoveredFlareAlerts(
                orphan = DerivedAlerts.FlareWindow(before.startDate, before.endDate),
                today = today,
            )
        }
        // 窗口变了就重判：仍满足「≥7 天未缓解」时补回一条（按 (type, today) 去重，不会重复）
        checkFlareDayAlert(today)
    }

    /**
     * v1.0.80（批次 6）：删除一次发作登记。
     *
     * 与编辑同理——「第 7 天」警报的 `ref_date` 落在这次发作的窗口内就说明它由这次发作派生，
     * 删掉发作后必须把它清掉（否则症状页挂着一条指向不存在发作的 medium 警报）。
     * 若别的发作窗口也覆盖同一个报警日（历史数据里可能重叠），则保留。
     */
    suspend fun deleteFlare(id: String, today: LocalDate = LocalDate.now()) = db.withTransaction {
        val row = flareDao.byId(id) ?: return@withTransaction
        flareDao.delete(id)
        dropUncoveredFlareAlerts(DerivedAlerts.FlareWindow(row.startDate, row.endDate), today)
    }

    /**
     * 清掉「已不在任何现存发作窗口内」的未确认 `flare_day7` 警报。
     *
     * [orphan] 是刚被删 / 刚被改窗口的那次发作的**旧**窗口：只有落在这个窗口里的报警日才可能是它派生的，
     * 再去掉仍被现存发作覆盖的那些——剩下的才是真孤儿。范围收窄到这一步是必要的：
     * 无差别清空该类型警报会顺手删掉别次发作的、用户还没看到的提醒。
     */
    private suspend fun dropUncoveredFlareAlerts(orphan: DerivedAlerts.FlareWindow, today: LocalDate) {
        val todayStr = today.toString()
        val candidates = alertDao.listUnackedByType(DerivedAlerts.FLARE_DAY7)
            .filter { DerivedAlerts.flareWindowCovers(orphan.startDate, orphan.endDate, it.refDate, todayStr) }
        if (candidates.isEmpty()) return
        val remaining = flareDao.listAll().map { DerivedAlerts.FlareWindow(it.startDate, it.endDate) }
        val stillCovered = DerivedAlerts.stillCoveredByOthers(candidates.map { it.refDate }, remaining, todayStr)
        candidates.filter { it.refDate !in stillCovered }.forEach {
            alertDao.deleteUnackedByRef(DerivedAlerts.FLARE_DAY7, it.refDate ?: return@forEach)
        }
    }

    // ---- M4 运动打卡 ----
    suspend fun checkInExercise(log: ExerciseLog) = exerciseDao.upsert(log)

    /**
     * v1.0.80（批次 6）：删除一条运动打卡（误录）。
     *
     * ⚠️ 调用方删完必须重排运动提醒：打卡状态直接决定提醒排程（「今天已打卡」时今日的升级重查不再重建），
     * 删掉今天唯一一条打卡后，今日提醒应当重新出现——这是删除的**派生重算**，不在数据层做。
     */
    suspend fun deleteExerciseLog(id: String) = exerciseDao.delete(id)

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

    /**
     * 幂等种入 4 / 8 / 12 周模板，并把**未被用户编辑过**的种子计划刷新到当前语言。
     *
     * 与 `RecipeRepository.seedIfMissing` 同一个道理：`week_structure` 里存的是展开后的
     * **文本**（JSON），语言在种入那一刻就定死了。判定「没被编辑过」同样是内容比对。
     *
     * @return 本次新增条数（刷新不算新增）
     */
    suspend fun seedExercisePlans(): Int = db.withTransaction {
        val pending = com.ashkb.app.domain.ExercisePlanTemplates.pending(
            exercisePlanDao.seedIds().toSet()
        )
        val now = nowIso()
        pending.forEach { t ->
            exercisePlanDao.upsert(seedPlanRow(t, now))
        }
        refreshExercisePlanLanguage(now)
        pending.size
    }

    /** 把一个模板展开成可落库的种子行（文案按当前语言取词）。 */
    private fun seedPlanRow(
        t: com.ashkb.app.domain.ExercisePlanTemplates.Template,
        now: String,
    ): com.ashkb.app.data.entity.ExercisePlan {
        val templates = com.ashkb.app.domain.ExercisePlanTemplates
        return com.ashkb.app.data.entity.ExercisePlan(
            id = t.id,
            title = context.getString(t.titleRes),
            weeks = t.weeks,
            stageMode = t.stageMode,
            weekStructure = templates.toJson(
                templates.materialize(t) { res -> context.getString(res) }
            ),
            isActive = false,
            isSeed = true,
            startDate = null,
            notes = null,
            createdAt = now,
            updatedAt = now,
        )
    }

    /** 把仍是「原封不动的种子」的计划改写成当前语言；用户编辑过的原样保留。 */
    private suspend fun refreshExercisePlanLanguage(now: String) {
        val rows = exercisePlanDao.listAll().filter { it.isSeed }
        val byId = rows.associateBy { it.id }
        com.ashkb.app.domain.ExercisePlanTemplates.ALL.forEach { t ->
            val row = byId[t.id] ?: return@forEach
            val title = context.getString(t.titleRes)
            val structure = com.ashkb.app.domain.ExercisePlanTemplates.toJson(
                com.ashkb.app.domain.ExercisePlanTemplates.materialize(t) { context.getString(it) }
            )
            if (title == row.title && structure == row.weekStructure) return@forEach
            if (!isUntouchedSeedPlan(t, row)) return@forEach
            exercisePlanDao.upsert(row.copy(title = title, weekStructure = structure, updatedAt = now))
        }
    }

    /**
     * 该计划是否仍是「原封不动的种子」：标题与周结构都与**某一已知语言**的种子逐字相同。
     *
     * 必须比对所有已知语言——库里那行是**种入当时**的语言写的。
     */
    private fun isUntouchedSeedPlan(
        t: com.ashkb.app.domain.ExercisePlanTemplates.Template,
        row: com.ashkb.app.data.entity.ExercisePlan,
    ): Boolean {
        val templates = com.ashkb.app.domain.ExercisePlanTemplates
        return SeedLocales.ALL.any { locale ->
            val c = SeedLocales.contextIn(context, locale)
            c.getString(t.titleRes) == row.title &&
                templates.toJson(templates.materialize(t) { c.getString(it) }) == row.weekStructure
        }
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
                message = context.getString(R.string.ui_alert_kb_review_due, e.title, e.reviewDue),
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

    /**
     * v1.2.4：补剂提醒重排要的是**当下这一刻的库内状态**，不是一条 Flow。
     *
     * 为什么不能读 `WellnessViewModel.supplements.value`：那是 `observeActive()` 经 Room 派发的
     * 状态流，写入之后**要等下一次派发**才更新。重排紧跟在写入之后调用，读 `.value` 会拿到
     * 保存前的旧列表——新加的那支补剂当场排不出闹钟，用户看到「设了时刻却没提醒」。
     */
    suspend fun listActiveSupplements(): List<Supplement> = supplementDao.listActive()

    /** v1.2.4：某日已打卡的补剂 id 集合（`slotKey` 恒 NULL，故粒度就是「整支补剂」）。 */
    suspend fun loggedSupplementIdsOn(date: String): Set<String> =
        supplementLogDao.byDate(date).mapNotNull { it.supId }.toSet()

    /**
     * U3 单个补剂的服用历史（近 90 天，**已结算**状态：done / partial / skipped）。
     * v1.0.81（批次 7）：窗口长度改为引用 `domain/SupplementHistory` 的常量——
     * 弹层的行数上限与这里的窗口是同一条规则，散在两个文件里迟早只改一处。
     * v1.0.87（批次 12）：状态集改为引用 [AdherenceCalc.SETTLED_STATUSES] 的**同一份**定义——
     * 此前 SQL 里写死 `status = 'done'`，跳过被记进库却在历史里查无此条（详见 Dao 注释）。
     */
    fun observeSupplementHistory(supId: String, name: String): Flow<List<SupplementLog>> =
        supplementLogDao.observeHistoryFor(
            supId, name, AdherenceCalc.SETTLED_STATUSES, SupplementHistory.fromDate(LocalDate.now()),
        )

    suspend fun saveSupplement(supp: Supplement) {
        val now = nowIso()
        val toSave = if (supp.id.isBlank()) supp.copy(id = Ids.new("sup"), createdAt = now, updatedAt = now)
        else supp.copy(updatedAt = now)
        supplementDao.upsert(toSave)
    }

    suspend fun archiveSupplement(id: String) = supplementDao.archive(id, nowIso())

    /** v1.0.81（批次 7）：某补剂名下的记录条数——删除确认框报数用（只读，无副作用）。 */
    suspend fun countSupplementLogs(supId: String): Int = supplementLogDao.countBySupId(supId)

    /**
     * v1.0.81（批次 7）：删除整个补剂条目，**连带删除它名下的全部服用记录**。
     *
     * 为什么要级联、为什么必须报数：见 `domain/SupplementDeletion`（纯函数，有单测）。
     * 为什么两条语句必须同事务：先删记录、再删档案，中途失败会留下「档案还在、记录没了」的半截状态；
     * 同事务提交则要么都生效、要么都不生效。
     *
     * @return 实际连带删掉的记录条数（供确认框报数与测试断言；档案不存在时为 0）
     */
    suspend fun deleteSupplement(id: String): Int = db.withTransaction {
        val removed = supplementLogDao.countBySupId(id)
        // 不限日期：详情只列最近 90 天，但删档案要删掉它的**全部**记录，否则 90 天前的记录永远无人可见
        supplementLogDao.deleteBySupId(id)
        supplementDao.delete(id)
        removed
    }

    suspend fun checkInSupplement(log: SupplementLog) = db.withTransaction {
        val existing = log.supId?.let { supplementLogDao.find(it, log.date, log.slotKey) }
        val toSave = if (existing != null) log.copy(id = existing.id)
        else log.copy(id = Ids.new("slog"))
        supplementLogDao.upsert(toSave)
    }

    /**
     * v1.0.80（批次 6）：撤销一次补剂打卡（误点）。
     *
     * 无派生数据：补剂打卡不参与任何提醒排程，也不产生警报（与用药打卡不同——
     * 那边会牵动「今天这剂吃没吃」的提醒重建，见 `ExerciseViewModel.deleteLog` 的同款说明）。
     */
    suspend fun deleteSupplementLog(id: String) = supplementLogDao.delete(id)

    // ---- M3 体征 ----
    fun observeVitals(date: String): Flow<Vitals?> = vitalsDao.observeLatestByDate(date)
    fun observeVitalsBetween(from: String, to: String): Flow<List<Vitals>> = vitalsDao.observeBetween(from, to)

    suspend fun saveVitals(input: Vitals) = db.withTransaction {
        val existing = vitalsDao.latestByDate(input.date)
        // 同日只保留最新一条（upsert 按 id 覆盖，先查再用同一 id）
        val log = if (existing != null) input.copy(id = existing.id)
        else input.copy(id = Ids.new("vit"))
        vitalsDao.upsert(log)
        evaluateVitalsAlerts(log)
    }

    /**
     * v1.1.2（批次 18）：**体征录入也触发发热红旗**（审查报告 §六）。
     *
     * 为什么以前不触发：红旗判定只挂在 `saveSymptom` 上，条件是「勾了发热 **且** 填了体温」。
     * 而患者量体温的正常路径是「体征」页——在那里录 38.6 ℃ 什么都不会发生。
     * 于是 `edu-th-001` 里写的 `vitals.temperature >= value 触发 alert`、
     * 以及 `emr-002` 的「≥38.5 自动弹本卡」都是空头承诺。
     * 对免疫抑制（生物制剂 / JAK / 激素）的患者，发热是急症级信号：
     * 触发源必须是**体温本身被记下来**，而不是「今天顺手做了症状自评并且勾对了框」。
     *
     * 与症状侧同用 `symptom_abnormal` 类型 + `insertAlertOnce` 的 `(type, refDate)` 去重：
     * 两条来源同一天只会留下一条警报，不刷屏。
     */
    private suspend fun evaluateVitalsAlerts(log: Vitals) {
        val t = log.temperature ?: return
        if (t < FEVER_THRESHOLD) return
        insertAlertOnce(
            type = "symptom_abnormal", severity = "high", refDate = log.date,
            message = context.getString(R.string.ui_alert_vitals_fever, t.toString(), FEVER_THRESHOLD.toString()),
            kbRef = "emr-002",
        )
    }

    /** 当天体征是否仍构成发热红旗——发热警报有**两个来源**，删除时按它决定留不留。 */
    private suspend fun vitalsStillFever(date: String): Boolean {
        val t = vitalsDao.latestByDate(date)?.temperature ?: return false
        return t >= FEVER_THRESHOLD
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

    suspend fun saveBodyMeasure(input: BodyMeasure) = db.withTransaction {
        // v1.0.80（批次 6）：「改」必须**覆盖当天那一行**，而不是再插一行。
        // 旧实现无论有无 id 都 upsert 传入值（表单新增时 id 恒为空），于是同一天改一次就多一行——
        // 「最近记录」里堆着一串同一天的重复值，用户只能一条条删；而体征 / 体重早就是同日覆盖语义。
        // 表单带 id 回来（真编辑）时按 id 覆盖；否则复用当天已有行的主键。
        val existing = if (input.id.isBlank()) bodyMeasureDao.byDate(input.date) else null
        val toSave = when {
            !input.id.isBlank() -> input
            existing != null -> input.copy(id = existing.id)
            else -> input.copy(id = Ids.new("bm"))
        }
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
     * 幂等：按 name 去重（**所有已知语言**，见 [ScreeningSeeds.pending]），已存在的不重复建。
     * @return 本次新增条数（刷新不算新增）
     */
    suspend fun seedBiologicScreeningItems(): Int = db.withTransaction {
        val items = checkupItemDao.listActive()
        val pending = ScreeningSeeds.pending(items.map { it.name }.toSet(), ::knownSeedNames)
        val now = nowIso()
        pending.forEach { s ->
            checkupItemDao.upsert(
                CheckupItem(
                    id = Ids.new("cki"),
                    name = context.getString(s.nameRes),
                    checkType = s.checkType,
                    cycleDays = s.cycleDays,
                    linkedMedId = null,
                    kbRef = null,
                    isActive = true,
                    notes = context.getString(s.notesRes),
                    createdAt = now,
                    updatedAt = now,
                )
            )
        }
        refreshScreeningLanguage(items, now)
        pending.size
    }

    /** 一条筛查种子在**所有已知语言**下的名称（判重要用它，不能只比当前语言）。 */
    private fun knownSeedNames(s: ScreeningSeeds.Seed): List<String> =
        SeedLocales.ALL.map { SeedLocales.contextIn(context, it).getString(s.nameRes) }

    /**
     * 把仍是「原封不动的种子」的筛查项改写成当前语言；用户改过名或备注的一律不碰。
     *
     * ⚠️ `checkup_items` **没有 `is_seed` 列**（`name` 既是展示文案又是幂等键，用户可改名），
     * 所以「没被编辑过」只能按「名称 + 备注与某一已知语言的种子逐字相同」认定。
     */
    private suspend fun refreshScreeningLanguage(items: List<CheckupItem>, now: String) {
        ScreeningSeeds.BIOLOGIC.forEach { s ->
            val row = items.firstOrNull { it.name in knownSeedNames(s) } ?: return@forEach
            val name = context.getString(s.nameRes)
            val notes = context.getString(s.notesRes)
            if (name == row.name && notes == row.notes) return@forEach
            if (!isUntouchedSeedItem(s, row)) return@forEach
            checkupItemDao.upsert(row.copy(name = name, notes = notes, updatedAt = now))
        }
    }

    private fun isUntouchedSeedItem(s: ScreeningSeeds.Seed, row: CheckupItem): Boolean =
        SeedLocales.ALL.any { locale ->
            val c = SeedLocales.contextIn(context, locale)
            c.getString(s.nameRes) == row.name && c.getString(s.notesRes) == row.notes
        }

    // ---- 复诊记录 ----
    fun observeCheckupRecent(limit: Int = 20): Flow<List<CheckupRecord>> = checkupRecordDao.observeRecent(limit)

    /**
     * v1.0.87（批次 13）：某个复诊项目名下的记录（「项目」→「记录」的项目筛选）。
     * `itemId` 认的是记录与项目的**显式关联**，`itemName` 认的是名字快照——现存记录的
     * `item_id` 一律是 NULL（该列至今没有写入路径），只认前者会筛出空列表。
     */
    fun observeCheckupByItem(itemId: String, itemName: String, limit: Int = 10): Flow<List<CheckupRecord>> =
        checkupRecordDao.observeByItem(itemId, itemName, limit)

    suspend fun saveCheckupRecord(record: CheckupRecord) {
        val toSave = if (record.id.isBlank()) record.copy(id = Ids.new("crec")) else record
        checkupRecordDao.upsert(toSave)
    }

    // ---- v1.0.80（批次 6）：复诊记录 / 化验 / 影像的修改与删除 ----

    /**
     * 删除前的**级联条数**（打开确认框时取一次，不再写库）。
     *
     * 为什么单独一个方法：计数要跑三条查询，而记录列表里可能有几十条卡片——
     * 在列表里为每条都挂一次计数查询是白烧 IO（用户点删除的只是其中一条）。
     */
    suspend fun checkupDeletionCounts(id: String): CheckupDeletion.Counts = CheckupDeletion.Counts(
        labs = labResultDao.countByCheckup(id),
        imaging = imagingDao.countByCheckup(id),
        attachments = attachmentRepo.listByCheckup(id).size,
    )

    /**
     * v1.0.80（批次 6）：**级联删除**一条复诊记录。
     *
     * 化验 / 影像 / 附件都靠 `checkup_id` 归属到这次就诊，只删记录本身会留下三张表里的孤儿行
     * （界面上还在，却再也看不出属于哪次就诊）。故一并删除，且附件要连**磁盘文件**一起删——
     * 否则内部存储里会永久留着一份用户以为已经删掉的化验单照片（隐私问题，不只是空间问题）。
     *
     * **顺序是有意的**：先把「附件的行 / 化验行 / 影像行 / 记录本身」在**一个事务里**处理干净，
     * 事务提交之后再删附件的磁盘字节。
     *
     * v1.2.7（批次 14 / R7）**改过一次**，理由值得留着：
     * 旧顺序是「先逐个 `attachmentRepo.delete()`（删文件 + 删/标行，各自独立提交），再开事务删三张表」。
     * 那样在两步之间崩溃会留下**悬空附件行**（文件已经没了、行还在），用户点开就失败，
     * 而且没有任何入口能把它清掉——级联删除那一次已经失败，记录还在，但附件已经烂了。
     * 现在反过来：事务里 `deleteRowsForCascade` 只动行、不碰磁盘；提交后才 `deleteLocalFiles`。
     * 崩溃只可能落在两种可接受的状态上：
     *  ① 事务之前——什么都没变，用户再点一次删除即可；
     *  ② 事务提交后、删字节前——留下无人引用的孤儿字节（占空间，不影响任何界面）。
     * 两种都比「行在、文件没了」好：后者是用户看得见且无法自救的坏状态。
     *
     * 附件删除里「有远端副本则留墓碑」那一分支保持不变（见 [AttachmentRepository.deleteRowsForCascade]）。
     *
     * @return 实际连带删除的条数；`null` = 该记录不存在（**零写入**）
     */
    suspend fun deleteCheckupRecord(id: String): CheckupDeletion.Counts? {
        checkupRecordDao.byId(id) ?: return null
        val attachments = attachmentRepo.listByCheckup(id)
        val counts = db.withTransaction {
            val c = CheckupDeletion.Counts(
                labs = labResultDao.countByCheckup(id),
                imaging = imagingDao.countByCheckup(id),
                attachments = attachments.size,
            )
            attachmentRepo.deleteRowsForCascade(attachments)
            labResultDao.deleteByCheckup(id)
            imagingDao.deleteByCheckup(id)
            checkupRecordDao.delete(id)
            c
        }
        // 事务已提交：行已经不可能再引用这些文件，此时删字节不会造成悬空行
        attachmentRepo.deleteLocalFiles(attachments)
        return counts
    }

    /**
     * v1.0.80（批次 6）：删除单条化验结果。
     *
     * 无派生数据——`abnormal` 是这一行**自己的字段**（无参考范围时才是 AI 标记的兜底值），
     * 化验不产生任何警报、也不被别的表引用，故只删行。
     */
    suspend fun deleteLabResult(id: String) = labResultDao.delete(id)

    /**
     * v1.0.80（批次 6）：删除单条影像记录。
     *
     * 无派生数据：附件挂的是**复诊记录**（`checkup_attachments.checkup_id`），从不挂影像 id，
     * 故删影像不会产生孤儿附件（用户从影像卡片归档的附件仍归属同一次复诊，行为与预期一致）。
     */
    suspend fun deleteImagingRecord(id: String) = imagingDao.delete(id)

    // ---- 化验结果 ----
    fun observeLabByCheckup(checkupId: String): Flow<List<LabResult>> = labResultDao.observeByCheckup(checkupId)
    fun observeLabTrend(testName: String, limit: Int = 20): Flow<List<LabResult>> =
        labResultDao.observeTrend(testName, limit)

    suspend fun saveLabResult(result: LabResult) {
        // v1.0.77（批次 4）：**本地参考范围判定优先**（维护者 2026-09-29 裁决）。
        //
        // 旧行为：只有 `abnormal == null` 才做本地判读——而 AI 导入路径会把 AI 的标记写进**同一列**
        // （包括「正常」）。于是 **AI 说正常，本地就再也不判读**：真实超出参考范围的数值被静默标成
        // 正常，从趋势图与 PDF 里消失（第三份审查报告 S-12「AI 幻觉能遮盖真实异常值」）。
        // 现在：只要**有参考范围**就一律本地判读；AI 标记仅在本地**无法判读**（没有参考范围）时兜底。
        // v1.0.78（批次 4 收尾）：AI 的原始标记已另存 `ai_abnormal` 一列（库 v19），
        // 故本地判读**只覆盖 `abnormal`**——下面这行刻意不碰 `aiAbnormal`，两列谁也别覆盖谁，
        // 否则「并列展示」就无从谈起（`LabRow` 只在两列不一致时才多显示一行说明）。
        val localAbnormal = if (result.value != null) {
            when {
                result.refHigh != null && result.value > result.refHigh -> "high"
                result.refLow != null && result.value < result.refLow -> "low"
                result.refHigh != null || result.refLow != null -> "normal"
                else -> null // 没有参考范围：本地判不了，保留 AI 标记兜底
            }
        } else null
        val withAbnormal = if (localAbnormal != null) result.copy(abnormal = localAbnormal) else result
        val toSave = if (withAbnormal.id.isBlank()) withAbnormal.copy(id = Ids.new("lab")) else withAbnormal
        labResultDao.upsert(toSave)
    }

    /** 化验结果总览（化验 Tab：按日期倒序，含 AI 导入的独立化验单） */
    fun observeLabRecent(limit: Int = 100): Flow<List<LabResult>> = labResultDao.observeRecent(limit)

    /**
     * v1.0.87（批次 13）：化验**总条数**（健康页摘要用）。
     *
     * 摘要此前拿的是 [observeLabRecent] 的长度——那是分页窗口（初值 100），窗口装满时
     * 它恒等于窗口值，删几条也不动。总数口径见 `LabResultDao.observeCount` 的注释。
     */
    fun observeLabCount(): Flow<Int> = labResultDao.observeCount()

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
            import.hospital?.let { context.getString(R.string.ui_import_hospital_prefix, it) },
            import.note,
        ).joinToString(" · ").ifBlank { null }
        import.rows.forEach { row ->
            saveLabResult(
                LabResult(
                    id = "", date = date, recordedAt = nowIso(), backfill = backfill,
                    checkupId = null, testName = row.testName,
                    value = row.value, valueText = row.valueText,
                    unit = row.unit, refLow = row.refLow, refHigh = row.refHigh,
                    // v1.0.78（批次 4 收尾）：两列都写——`aiAbnormal` 留 AI 原值（并列展示），
                    // `abnormal` 交给 saveLabResult 的本地判读覆盖 / 无参考范围时兜底。
                    abnormal = row.abnormal, aiAbnormal = row.aiAbnormal, notes = note,
                )
            )
        }
    }

    /** 影像报告导入：AI 解析结果 → imaging_records，「对比」并入备注 */
    suspend fun importImagingReport(import: ImagingImport) {
        val examDate = import.date ?: LocalDate.now().toString()
        val notesParts = buildList {
            import.compare?.takeIf { it.isNotBlank() && it != "无" }
                ?.let { add(context.getString(R.string.ui_import_compare_prefix, it)) }
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
        // v1.0.73：判定抽到 domain/VaccineSafety（纯函数 + 枚举比较 + 未知值保守兜底），
        // 修掉「实体默认值小写 "pending" vs 此处大写比较」+「表单默认 CONFIRMED」两处安全默认值反转。
        if (VaccineSafety.needsLiveVaccineAlert(record.vaccineType, record.doctorConfirm)) {
            insertAlertOnce(
                type = DerivedAlerts.VACCINE_LIVE_PENDING, severity = "high", refDate = record.date,
                message = context.getString(R.string.ui_alert_live_vaccine, record.vaccineName),
                kbRef = "itx-010",
            )
        }
        // v1.0.80（批次 6）：**编辑的反向情形**——把「活疫苗 + 待确认」改成灭活或「医生同意」后，
        // 原来那条 high 级警报就失去了依据（记录已经不再声称「有一针活疫苗待确认」）。
        // 与新增走同一条重算路径，避免「新增会报警、修改却不会消警」这种单向逻辑。
        refreshVaccineLiveAlert(record.date)
    }

    /**
     * v1.0.80（批次 6）：删除一条疫苗记录，并重算它派生出来的活疫苗安全警报。
     *
     * **重算而不是直接删**：警报按 `(type, ref_date=接种日)` 去重，而同一天可能记了多针
     * （例如同时打了流感 + 带状疱疹）。删掉其中「活疫苗待确认」那一针时警报该消；
     * 但若当天还有**另一针**同样满足活疫苗 × 未确认，警报必须留下——一刀切删掉就是漏报。
     */
    suspend fun deleteVaccineRecord(id: String) = db.withTransaction {
        val row = vaccineDao.byId(id) ?: return@withTransaction
        vaccineDao.delete(id)
        refreshVaccineLiveAlert(row.date)
    }

    /**
     * 按**库里当天的实际记录**重算活疫苗警报：已无任何一条满足「活疫苗 × 未确认」时，
     * 清掉该日未被确认的警报（已确认的保留——那是用户看过的留痕，见 [AlertDao.deleteUnackedByRef]）。
     */
    private suspend fun refreshVaccineLiveAlert(date: String) {
        val stillNeeded = vaccineDao.byDate(date)
            .any { VaccineSafety.needsLiveVaccineAlert(it.vaccineType, it.doctorConfirm) }
        if (!stillNeeded) alertDao.deleteUnackedByRef(DerivedAlerts.VACCINE_LIVE_PENDING, date)
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

    /**
     * v1.0.80（批次 6）：删除一条紧急事件记录（误录）。
     * 无派生数据：事件记录不产生警报、不参与任何提醒排程，也不被别的表引用。
     */
    suspend fun deleteEmergencyEvent(id: String) = emergencyDao.delete(id)

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
