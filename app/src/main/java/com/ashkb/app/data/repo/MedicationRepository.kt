package com.ashkb.app.data.repo

import android.content.Context
import androidx.room.withTransaction
import com.ashkb.app.data.db.AppDatabase
import com.ashkb.app.data.db.Ids
import com.ashkb.app.data.entity.KbEntry
import com.ashkb.app.data.entity.Medication
import com.ashkb.app.data.entity.MedicationChange
import com.ashkb.app.data.entity.MedicationLog
import com.ashkb.app.data.entity.PlannedSlot
import com.ashkb.app.data.entity.Profile
import com.ashkb.app.data.entity.Reaction
import com.ashkb.app.data.entity.StopReason
import com.ashkb.app.data.entity.Supplement
import com.ashkb.app.data.entity.SupplementCategory
import com.ashkb.app.domain.AdherenceCalc
import com.ashkb.app.domain.DrugInteractionKeys
import com.ashkb.app.domain.MedDeletion
import com.ashkb.app.domain.MedLogEdit
import com.ashkb.app.domain.ScheduleCalc
import com.ashkb.app.reminder.ReminderScheduler
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

val ISO: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
fun nowIso(): String = LocalDateTime.now().format(ISO)

/** 今日打卡卡：计划槽位 + 已有日志行（无行 = 未记录，三分法） */
data class TodayItem(
    val med: Medication,
    val slotKey: String?,
    val slotTime: String?,
    val slotLabel: String,
    val log: MedicationLog?,
) {
    val isPrn: Boolean get() = slotKey == null
    val isLate: Boolean get() = log?.let {
        // P5 修订：锚定日志归属日（log.date），次日补打卡才能正确判 late
        val ownDate = runCatching { LocalDate.parse(it.date) }.getOrDefault(LocalDate.now())
        it.status == "done" && ScheduleCalc.isLate(slotTime, it.takenAt, ownDate)
    } ?: false
    val done: Boolean get() = log?.status == "done"
    val skipped: Boolean get() = log?.status == "skipped"
}

class MedicationRepository(private val context: Context) {
    private val db = AppDatabase.get(context)
    private val medDao = db.medicationDao()
    private val logDao = db.medicationLogDao()
    private val profileDao = db.profileDao()
    private val kbDao = db.kbEntryDao()
    private val changeDao = db.medicationChangeDao()
    private val slotDao = db.plannedSlotDao()

    /**
     * v1.1.2（批次 18）：补剂也要进用药核对——**叶酸**（itx-006 / itx-007 的 `drug_b`）
     * 与钙剂 / 维生素 D3 都记在**补剂档案**里，不在 `medications` 表。
     * 只看药品表会把「服甲氨蝶呤需每周补充叶酸」这一整类医嘱配合条目漏掉。
     */
    private val supplementDao = db.supplementDao()

    // ---- 档案 ----
    fun observeProfile(): Flow<Profile?> = profileDao.observe()
    suspend fun saveProfile(p: Profile) = profileDao.upsert(p.copy(updatedAt = nowIso()))

    // ---- 药单 ----
    fun observeMedications(): Flow<List<Medication>> = medDao.observeActive()

    /**
     * v1.0.80（批次 6）：保存药品（新增 / **编辑**）。
     *
     * 编辑时**先清掉今天起的计划槽位快照**，再交给调用方的重排流程重新物化：
     * `planned_slots` 是 `INSERT OR IGNORE` 幂等写入的，滚动窗口（今天-1 .. 今天+7）里的行
     * **早就写好了**——把 08:00 改成 20:00 之后，那些行仍是 08:00，于是「提醒在 20:00 响、
     * 完成度却拿 08:00 算」，用户会看到一剂自己从没被告知过的漏服（幽灵计划）。
     *
     * 只删**今天起**的行：停药用的是同一个 DAO 方法（[PlannedSlotDao.deleteOfMedFrom]），
     * 口径一致——历史不该被今天的编辑改写，今天的计划必须跟着新用法走。
     */
    suspend fun saveMedication(med: Medication) = db.withTransaction {
        medDao.upsert(med.copy(updatedAt = nowIso()))
        slotDao.deleteOfMedFrom(med.id, LocalDate.now().toString())
    }

    suspend fun medicationById(id: String): Medication? = medDao.byId(id)

    /** R17 停药：归档 + medication_changes 登记（裁决 A 快照，self_stopped 供警示联动） */
    suspend fun stopMedication(med: Medication, reason: String, note: String?) {
        val now = nowIso()
        medDao.archive(med.id, now)
        // v1.0.77（批次 3b）：清掉**今天起**的计划槽位快照（停药生效日 = 今天）。
        // 物化窗口是滚动的 8 天，此刻未来 7 天的行已经在表里；留着它们，此后每天的报表都会
        // 把「已经停掉的药」算成未记录——那是用户无法理解的假漏服。
        // 停药前的历史行**保留**：停药不是删除，那段时间确实有过这些计划剂量。
        slotDao.deleteOfMedFrom(med.id, LocalDate.now().toString())
        changeDao.insert(
            MedicationChange(
                id = Ids.new("mchg"),
                recordedAt = now,
                medId = med.id,
                medKey = med.nameKey,
                changeType = "stop",
                oldSnapshot = medSnapshot(med, archived = false),
                newSnapshot = medSnapshot(med, archived = true),
                effectiveDate = LocalDate.now().toString(),
                reason = reason,
                reasonNote = note,
                source = "self",
            )
        )
    }

    private fun medSnapshot(med: Medication, archived: Boolean): String = org.json.JSONObject()
        .put("name", med.name)
        .put("dose", med.dose)
        .put("frequency", med.frequency)
        .put("route", med.route)
        .put("med_class", med.medClass)
        .put("is_archived", archived)
        .toString()

    // ---- R03 核对清单：按**结构化药物键**查种子相互作用条目 ----
    /**
     * v1.1.2（批次 18）：R03 用药核对清单的取数。
     *
     * ### 改前（v1.1.1 及以前）是什么情况下看不到
     * 旧实现是「把 `nameKey` / `medClass` / 硬编码类别键逐个拿去 `payload LIKE '%key%'`」，
     * 三条实测复现的失效路径（审查报告 §2，本批次独立复现，命令与输出见批次回报）：
     *  ① `medClassKeys` 没有 JAK 分支 → 乌帕替尼 / 托法替布 / 巴瑞替尼**命中 0 条**；
     *  ② `MedClass.OTHER.name` = "OTHER" 撞上 itx-015 引文里的英文单词 `other` →
     *     唑来膦酸 / 骨化三醇 / 维生素 D3 / 钙剂收到**阿仑膦酸钠**的服药规则；
     *  ③ `medClassKeys("csdmard")` 硬编码 `["mtx","methotrexate","sulfasalazine"]` →
     *     来氟米特 / 艾拉莫德收到「甲氨蝶呤误当每日服用可致死」。
     *
     * ### 改后是什么情况下能看到
     * ① 药单上的每个词（`nameKey` / 药名 / 商品名 / `medClass`）经词汇表归一成规范键，
     *    键的取值域与种子 `drug_a`/`drug_b` 的取值域**同一套**（`DrugInteractionKeys`）；
     * ② **药品–药品的组合条目要求两侧都真的在药单上**（旧实现只查单侧，
     *    把「MTX + NSAIDs 合用需监测」错报成「吃 MTX 就有这条风险」）；
     * ③ 补剂与药品**一并进 token 集**（叶酸记在补剂档案里，而它正是 itx-006 / itx-007 的 `drug_b`）；
     * ④ 语境型 `drug_b`（活疫苗 / 钙与食物）**不作适用性前提**——它们是该药患者迟早要看到的
     *    用药规则，患者还没录疫苗 / 补剂就不显示等于把规则藏起来。
     *
     * 判据（可复现）：新增「来氟米特（爱若华 / CSDMARD）」后，第二步核对清单**不再出现 itx-001**；
     * 新增「乌帕替尼（瑞福 / JAK）」后**能出现** itx-002 / itx-010（原先一条都没有）；
     * 新增「阿仑膦酸钠（福善美 / OTHER）」后**出现且只出现** itx-015；
     * 新增「钙剂（补剂 / OTHER）」后**不再出现** itx-015。
     *
     * 逐条回归锁见 `MedicationInteractionChainTest`（载入真种子 `kb_seed_itx.json` 跑完整链路）。
     *
     * @param med 正在新增 / 编辑的那一支（它自己还没落库，故必须显式并进药单）
     */
    suspend fun interactionsFor(med: Medication): List<KbEntry> {
        val entries = kbDao.interactions()
        val existing = medDao.listActive()
        val supplements = supplementDao.listActive()
        val tokens = tokensOf(existing, supplements) + tokensOfMedication(med)
        return entries
            .filter { appliesTo(it, tokens) }
            .sortedByDescending { severityRank(it.severityLevel) }
    }

    /**
     * 药单「在用药品 + 在用补剂」→ token 集。
     *
     * 抽出来是为了让 [interactionsFor] 与将来的「药单体检」入口共用同一份口径：
     * 药单里到底算哪些东西、每个东西贡献哪些词，只能有一处定义。
     */
    suspend fun medicationTokens(): List<String> =
        tokensOf(medDao.listActive(), supplementDao.listActive())

    private fun tokensOf(meds: List<Medication>, supplements: List<Supplement>): List<String> =
        meds.flatMap { tokensOfMedication(it) } + supplements.flatMap { tokensOfSupplement(it) }

    private fun tokensOfMedication(m: Medication): List<String> =
        listOfNotNull(m.nameKey, m.name, m.brandName, m.medClass)

    private fun tokensOfSupplement(s: Supplement): List<String> =
        listOfNotNull(s.name, s.brand, SupplementCategory.fromKey(s.category).label)

    /**
     * 单条判定（纯函数在 `domain/DrugInteractionKeys.matches`，这里只负责把 payload 拆出两侧）。
     *
     * payload 解析失败时**返回 false 而不是 true**：一条读不懂的条目不该出现在患者的核对清单里
     * ——宁可少提示也不要递一条来源不明的警告（与 `ExerciseEngine` 的「失败即封闭」同向）。
     */
    private fun appliesTo(entry: KbEntry, tokens: List<String>): Boolean {
        val payload = runCatching { org.json.JSONObject(entry.payload) }.getOrNull() ?: return false
        val declared = DrugInteractionKeys.classify(
            drugA = payload.optString("drug_a").takeIf { it.isNotBlank() && it != "null" },
            drugB = payload.optString("drug_b").takeIf { it.isNotBlank() && it != "null" },
        )
        return DrugInteractionKeys.matches(declared, tokens)
    }

    private fun severityRank(s: String) = when (s.lowercase()) {
        "high" -> 3; "medium" -> 2; else -> 1
    }

    // ---- 今日视图 ----
    fun observeToday(date: LocalDate): Flow<List<TodayItem>> =
        combine(medDao.observeActive(), logDao.observeByDate(date.toString())) { meds, logs ->
            buildTodayItems(meds, logs, date)
        }

    fun buildTodayItems(meds: List<Medication>, logs: List<MedicationLog>, date: LocalDate): List<TodayItem> {
        val items = mutableListOf<TodayItem>()
        for (med in meds) {
            val slots = ScheduleCalc.slotsFor(med, date)
            if (slots.isEmpty()) {
                if (com.ashkb.app.data.entity.MedFrequency.fromKey(med.frequency) ==
                    com.ashkb.app.data.entity.MedFrequency.PRN
                ) {
                    items.add(TodayItem(med, null, null, "按需 · ${med.prnReason ?: "备用"}", null))
                }
                continue
            }
            for (slot in slots) {
                val log = logs.firstOrNull { it.medId == med.id && it.slotKey == slot.key }
                items.add(TodayItem(med, slot.key, slot.time, slot.label, log))
            }
        }
        return items.sortedWith(compareBy({ !it.done && !it.skipped }, { it.slotTime ?: "99:99" }))
    }

    // ---- 打卡写入（幂等：date+medId+slotKey 唯一；PRN 的 slotKey 为 NULL，SQLite 中 NULL 互不相等，故按需用药可多次记录） ----
    /**
     * @param date v1.0.73（P1-4）：**槽位所属日期**。默认今天（应用内打卡），但从通知动作 /
     *   全屏页打卡时必须传入槽位那天的日期——跨零点时 `LocalDate.now()` 已是次日，会把日志记到
     *   次日，原槽位永远空缺（依从率虚低，且 23:50 的剂量看起来「没吃」）。
     */
    suspend fun checkIn(
        med: Medication, slotKey: String?, slotTime: String?,
        reaction: String = Reaction.NONE.name, injSite: String? = null, note: String? = null,
        date: LocalDate = LocalDate.now(),
    ) {
        val dateStr = date.toString()
        val existing = slotKey?.let { logDao.find(med.id, dateStr, it) }
        val now = nowIso()
        val log = (existing ?: MedicationLog(
            id = Ids.new("mlog"),
            date = dateStr,
            recordedAt = now,
            backfill = false,
            medId = med.id,
            medKey = med.nameKey,
            medName = med.name,
            doseSnapshot = med.dose,
            scheduledTime = slotTime,
            slotKey = slotKey,
            status = "done",
            reason = null,
            takenAt = now,
            injSite = injSite,
            batchNo = null,
            reaction = reaction,
            prnFlag = slotKey == null,
        )).copy(
            status = "done", takenAt = now, recordedAt = now,
            reason = null, reaction = reaction, injSite = injSite, notes = note,
            prnFlag = slotKey == null,
        )
        logDao.upsert(log)
        // 注射部位轮换：记住本次部位（下次打卡提示轮换）
        if (injSite != null && med.injLastSite != injSite) {
            medDao.upsert(med.copy(injLastSite = injSite, updatedAt = now))
        }
    }

    /** 补录 / 修正跳过：跳过必须有原因（红线三） */
    suspend fun skip(med: Medication, slotKey: String?, slotTime: String?, reason: String, note: String? = null) {
        val date = LocalDate.now().toString()
        val existing = slotKey?.let { logDao.find(med.id, date, it) }
        val now = nowIso()
        val log = (existing ?: MedicationLog(
            id = Ids.new("mlog"), date = date, recordedAt = now, backfill = false,
            medId = med.id, medKey = med.nameKey, medName = med.name, doseSnapshot = med.dose,
            scheduledTime = slotTime, slotKey = slotKey, status = "skipped", reason = reason,
        )).copy(status = "skipped", reason = reason, notes = note, recordedAt = now, takenAt = null)
        logDao.upsert(log)
    }

    /**
     * v1.0.49：手动修正某条用药记录（记错了改）。
     *
     * **只改「内容」，不改归属日与槽位键**——`(date, med_id, slot_key)` 是唯一索引，
     * 改日期/槽位等于换一条记录，会与相邻记录撞键（[logDao.upsert] 是 REPLACE 语义，
     * 撞键会**静默删掉**被撞的那条）。日期与计划时刻因此在 UI 上只读。
     *
     * 字段规范化交给 [MedLogEdit]（与写入侧同一套不变量），并保证「已服」有服用时刻。
     */
    suspend fun updateLog(log: MedicationLog, status: String, reason: String?, injSite: String?, notes: String?) {
        logDao.upsert(
            log.copy(
                status = status,
                reason = MedLogEdit.reasonFor(status, reason),
                injSite = MedLogEdit.injSiteFor(status, injSite),
                takenAt = if (status == AdherenceCalc.DONE) (log.takenAt ?: nowIso()) else null,
                notes = notes?.takeIf { it.isNotBlank() },
            )
        )
    }

    suspend fun logsForDate(date: LocalDate): List<MedicationLog> = logDao.byDate(date.toString())

    // ---- v1.0.48：用药记录与已停用药品（药单点开查看流水 / 折叠区） ----

    /**
     * 某条药的用药记录（近 [days] 天，倒序）。
     *
     * 闭区间与报表口径一致：`[今天-(days-1), 今天]`。停药的药也能查到——归档不改写历史日志。
     */
    fun observeLogsForMed(medId: String, days: Int = 90): Flow<List<MedicationLog>> =
        logDao.observeByMedSince(medId, LocalDate.now().minusDays((days - 1).toLong()).toString())

    /**
     * 已停用药品 + 停药信息（原因 / 生效日 / 备注）。
     *
     * 停药原因不在 `medications` 表上（那里只有 `is_archived` 一个布尔），而在
     * `medication_changes` 的 `change_type='stop'` 记录里——本方法按 `med_id` 关联最近一条。
     *
     * `stopReason` 为 null = 查不到对应的停药变更（老数据 / 恢复的旧备份），
     * 此时 UI 不应臆测原因，只显示「无记录」。
     */
    fun observeArchivedMedications(): Flow<List<ArchivedMedication>> =
        combine(medDao.observeArchived(), changeDao.observeStops()) { meds, changes ->
            meds.map { med ->
                val c = changes.firstOrNull { it.medId == med.id }
                ArchivedMedication(
                    med = med,
                    stopDate = c?.effectiveDate,
                    stopReason = c?.reason?.let { StopReason.fromKey(it) },
                    stopNote = c?.reasonNote,
                    // v1.0.71：删除入口的确认框必须说出「连带删掉几条打卡记录」
                    logCount = logDao.countOfMed(med.id),
                )
            }
        }

    /**
     * v1.0.71：**删除**已停用药品——连带其打卡记录与变更记录（用户 2026-09-27 拍板）。
     *
     * 为什么连带删打卡记录：只删药档而留着记录，报表依从率仍会把它们算进去，「清掉测试用药的
     * 痕迹」就清不干净。代价是历史统计会变，故确认框如实报条数（[MedDeletion]）。
     *
     * 只有「已停用」的药可删（[MedDeletion.canDelete] + DAO 里的 SQL 门禁）；
     * 事务内四步（计数 → 删日志 → 删变更 → 删药档），任一失败整体回滚。
     *
     * @return 实际删除的打卡记录条数；`null` = 该药不存在或仍在用（未归档）→ **未做任何改动**
     */
    suspend fun deleteArchivedMedication(medId: String): Int? = db.withTransaction {
        val med = medDao.byId(medId) ?: return@withTransaction null
        if (!MedDeletion.canDelete(med.isArchived)) return@withTransaction null
        val logs = logDao.countOfMed(medId)
        logDao.deleteOfMed(medId)
        changeDao.deleteOfMed(medId)
        // v1.0.77（批次 3b）：计划快照一并物理删除——否则删掉「测试用药」后，
        // 计划剂量口径的完成度仍会把它的计划算进分母 / 漏服，用户的痕迹清不干净（与日志同理）。
        slotDao.deleteOfMed(medId)
        medDao.deleteArchived(medId)
        logs
    }

    /** 已停用药品视图项（[stopReason] null = 无停药变更记录，见 [observeArchivedMedications]） */
    data class ArchivedMedication(
        val med: Medication,
        val stopDate: String?,
        val stopReason: StopReason?,
        val stopNote: String?,
        /** v1.0.71：该药名下打卡记录条数（删除确认框用，0 = 无记录） */
        val logCount: Int = 0,
    )

    /**
     * v1.0.44（N1）：今日**已打卡**槽位集合（[ReminderScheduler.slotRef] 形态），
     * 供 [ReminderScheduler.rescheduleAll] 判断「该槽位无需重建升级重查」。
     *
     * 抽到一处的原因：这个集合此前在 Application / TodayViewModel / MeViewModel 各写一遍，
     * 于是开机广播（BootReceiver）那条路径漏传就成了必然。现在只有一个实现，
     * 且 `rescheduleAll` 的该参数**无默认值**——任何新增调用点都必须显式取一次。
     */
    suspend fun doneSlotRefs(date: LocalDate): Set<String> =
        logsForDate(date)
            .filter { it.status == "done" }
            .map { ReminderScheduler.slotRef(it.medId, it.slotKey) }
            .toSet()

    /**
     * v1.0.74：某日**已结算**的槽位（done 或 skipped）。
     *
     * 与 [doneSlotRefs] 的区别：那个只认 `done`（用于「重排时不再重建升级重查」——
     * 用户主动跳过的槽位在 v1.0.73 之后也不该重建，故新代码一律用本方法）。
     * 今日页的「昨天还有 N 剂未记录」卡必须用**已结算**口径，否则用户昨天明确跳过（写了原因）的
     * 剂量会天天挂在卡片上催他补记。
     *
     * v1.0.77（批次 3b）：状态集合改为 [AdherenceCalc.SETTLED_STATUSES]（done / partial / skipped），
     * 与漏服补发通知（`MissedDoses.unsettled`）**共用同一份定义**。
     * 此前这里只认 done / skipped，于是「部分完成」的剂量会同时出现在补记卡上（说漏了）
     * 与漏服判定里（说没漏）——同一剂药两个结论。改为一处定义后两者不可能再分叉。
     */
    suspend fun settledSlotRefs(date: LocalDate): Set<String> =
        logsForDate(date)
            .filter { it.status in AdherenceCalc.SETTLED_STATUSES }
            .map { ReminderScheduler.slotRef(it.medId, it.slotKey) }
            .toSet()

    // ---- v1.0.77（批次 3b）：计划槽位快照 ----

    /**
     * 物化计划槽位快照：把 [from]..[to] 每天的计划用 `ScheduleCalc.slotsFor` 展开成行写库。
     *
     * **幂等**：`(date, med_id, slot_key)` 唯一索引 + `INSERT OR IGNORE`，同一天反复调用只会
     * 补上缺的行。因此这个例程挂在每次「重排提醒」的同一批调用点上（启动 / 开机 / 打卡后 /
     * 改药单后）——窗口滚过去一天，就补进来一天，不需要额外的定时任务。
     *
     * 按需（PRN）天然没有槽位（`slotsFor` 返回空），无需特判。
     *
     * **只物化未开始的药**：`startDate` 晚于某天的药在该日没有计划——否则今天新建一支药，
     * 昨天会被凭空算出一剂未记录（`PendingDoses` 的 startDate 判定同款理由，假阳性回归锁见
     * `PendingDosesTest`）。日期解析不出来时按「不过滤」处理（宁可多记一剂，也不静默藏掉计划）。
     *
     * ⚠️ 快照是**当时计划**的留痕：同一药事后改了时刻 / 剂量，已写入的历史行不会跟着变。
     * v1.0.80（批次 6）起，「今天起」的行由 [saveMedication] 主动清掉后按新定义重物化——
     * 改完时刻若还留着旧行，提醒与完成度就会按两个不同的时刻各算一套（见那里的注释）。
     */
    suspend fun materializePlannedSlots(meds: List<Medication>, from: LocalDate, to: LocalDate) {
        if (meds.isEmpty()) return
        val createdAt = nowIso()
        val rows = mutableListOf<PlannedSlot>()
        // 本批已用过的主键。为什么要自己去重：[Ids.new] 是「毫秒时间基 + 3 位随机尾」，
        // 而一次物化会一口气生成几十行（8 天 × 每天几剂，全在同一两毫秒内）——
        // 同毫秒撞尾的概率到百分之几量级，撞上会被 `INSERT OR IGNORE` **静默丢掉一行计划**
        // （那天分母少一剂，还没有任何报错）。故同一批内保证主键互不相同。
        val usedIds = mutableSetOf<String>()
        var date = from
        while (!date.isAfter(to)) {
            for (med in meds) {
                rows.addAll(slotRowsFor(med, date, usedIds, createdAt))
            }
            date = date.plusDays(1)
        }
        if (rows.isNotEmpty()) slotDao.insertAll(rows)
    }

    /**
     * 某药在某一天的应物化槽位行（空列表 = 这一天不该有计划）。
     *
     * 抽成独立函数有两个理由：① 物化主循环里的 `continue` 一多（startDate / updatedAt / 空时刻三处）
     * 就触发 detekt 的 `LoopWithTooManyJumpStatements`；② 「这一天该不该有计划」本身是一条业务规则，
     * 值得单独读、单独改。
     */
    private fun slotRowsFor(
        med: Medication,
        date: LocalDate,
        usedIds: MutableSet<String>,
        createdAt: String,
    ): List<PlannedSlot> {
        // **只物化未开始的药**：`startDate` 晚于某天的药在该日没有计划——否则今天新建一支药，
        // 昨天会被凭空算出一剂未记录（`PendingDoses` 的 startDate 判定同款理由，
        // 假阳性回归锁见 `PendingDosesTest`）。日期解析不出来时按「不过滤」处理。
        val started = runCatching { LocalDate.parse(med.startDate) }.getOrNull()
        if (started != null && date.isBefore(started)) return emptyList()
        // v1.0.80（批次 6）：**不物化「药档最后一次修改」之前的日期**。
        //
        // 为什么必须有这一条：窗口的回看端是「昨天」，而改过服药时刻之后，昨天会被按**新**定义
        // 补出一行——`INSERT OR IGNORE` 只挡同键，挡不住 08:00 → 20:00 这种换键。
        // 于是昨天凭空多出一剂「20:00 的计划」，当晚的完成度把它算成未记录，
        // 今早还会收到「昨天还有 1 剂未记录」的补发通知：用户从没被告知过那一剂。
        //
        // 代价：若某台设备昨天没开过应用、当天又改了药档，昨天那一剂不会补进计划
        // （补发通知少算一剂）。宁可少催一次，也不要凭空多出一剂——误报会直接摧毁
        // 用户对「漏服提醒」的信任。药档没改过的药不受影响（updatedAt 仍是创建/上次修改日）。
        val editedOn = runCatching { LocalDate.parse(med.updatedAt.take(10)) }.getOrNull()
        if (editedOn != null && date.isBefore(editedOn)) return emptyList()
        // 时刻为空的槽位不物化：没有时刻就算不出「是否已到点」，也无法用于判定漏服
        return ScheduleCalc.slotsFor(med, date).mapNotNull { slot ->
            val time = slot.time ?: return@mapNotNull null
            PlannedSlot(
                id = freshSlotId(usedIds),
                date = date.toString(),
                medId = med.id,
                medKey = med.nameKey,
                medName = med.name,
                slotKey = slot.key,
                slotTime = time,
                doseSnapshot = med.dose,
                createdAt = createdAt,
            )
        }
    }

    /**
     * 取一个本批内唯一的主键（理由见 [materializePlannedSlots]）。
     *
     * 撞尾时加计数后缀而不是重摇随机数：重摇仍是随机过程（理论上可以一直撞），
     * 加后缀是确定性的——前缀保持 `pslot-` 的标准形态，主键只要求本地唯一。
     */
    private fun freshSlotId(used: MutableSet<String>): String = uniqueSlotId(Ids.new("pslot"), used)

    /** 某药在区间内的计划槽位（药单「用药记录」弹层的计划口径完成度用） */
    suspend fun plannedSlotsForMed(medId: String, from: LocalDate, to: LocalDate): List<PlannedSlot> =
        slotDao.betweenForMed(medId, from.toString(), to.toString())

    /** 区间内全部计划槽位（报表用） */
    suspend fun plannedSlotsBetween(from: LocalDate, to: LocalDate): List<PlannedSlot> =
        slotDao.between(from.toString(), to.toString())

    /** 区间内全部用药记录（计划口径完成度要按槽位逐条配对，计数不够用） */
    suspend fun logsBetween(from: LocalDate, to: LocalDate): List<MedicationLog> =
        logDao.listBetween(from.toString(), to.toString())

    /**
     * 计划槽位快照的**滚动物化窗口**（今天-1 .. 今天+7）。
     *
     * 为什么是这一头一尾：起点取**昨天**（跨零点补记与「昨天漏了几剂」的补发都要看昨天），
     * 长度与提醒的 [ReminderScheduler.HORIZON_DAYS] **对齐**——提醒排到哪天，计划快照就记到哪天，
     * 这样永远不会出现「有提醒可发、却没有计划可评判」的错位。
     *
     * 收成一个方法（而不是在四个调用点各写一遍 `minusDays(1) / plusDays(7)`）的理由与
     * [doneSlotRefs] 同款：多写几遍必然会漏改一处，而窗口错一天没有任何症状——
     * 只是某天的完成度悄悄算不出来。
     */
    fun plannedSlotWindow(today: LocalDate = LocalDate.now()): PlannedSlotWindow =
        PlannedSlotWindow(
            from = today.minusDays(PLANNED_SLOT_BACK_DAYS),
            to = today.plusDays(ReminderScheduler.HORIZON_DAYS),
        )

    /**
     * R17 注射顺延：锚点移至新日期，周期从新日期起算重排；实际注射发生时才写日志（未记录=无行）。
     *
     * v1.1.1（本批次修的 HIGH-2）：**补上「清今天起的计划槽位」与 `medication_changes` 审计行**。
     *
     * 此前本方法只改 `startDate`，而同文件的 [saveMedication] / [stopMedication] 都会先调
     * `slotDao.deleteOfMedFrom`。顺延把锚点从"今天"挪到"明天"之后，**旧锚点已经物化在今天..+7
     * 的行仍留在 `planned_slots` 里**，而物化例程是 `INSERT OR IGNORE`（只加不删，见
     * [plannedSlotWindow] 上方注释）→ 那些行永远不会被新锚点覆盖：
     * `AdherenceCalc.doseCompletion` 会给它们配上一个永远不会存在的日志 → `missed++` →
     * **该剂量在其落入的每个报表窗口里都被永久计为漏服**，`MissedDoses.unsettled` 也会每天
     * 提示它。App 自己的漏服安全信号与用户在顺延对话框里刚做的决定互相矛盾。
     *
     * 这是本项目第三次踩「计划槽位只加不删」（前两次的教训就写在上面两处注释里）——所以这里
     * 不是"补一行"，而是与另两个入口对齐成同一口径：**改用法/停药/顺延都必须清掉今天起的快照**。
     * 只删"今天起"：历史计划行不该被今天的改动抹掉。
     *
     * 审计行一并补上：本方法此前是本文件里唯一不留痕的药品变更，用户事后无法回答
     * 「这药是什么时候改成隔天打的」。`change_type` 用既有词汇表里的 `schedule_change`
     * （见实体注释），UI 目前只读 `stop` 行，这条是留给台账与导出的。
     */
    suspend fun postponeInjection(med: Medication, toDate: LocalDate) = db.withTransaction {
        val now = nowIso()
        val today = LocalDate.now().toString()
        val before = medDao.byId(med.id) ?: med
        val after = med.copy(startDate = toDate.toString(), updatedAt = now)
        medDao.upsert(after)
        slotDao.deleteOfMedFrom(med.id, today)
        changeDao.insert(
            MedicationChange(
                id = Ids.new("mchg"),
                recordedAt = now,
                medId = med.id,
                medKey = med.nameKey,
                changeType = "schedule_change",
                oldSnapshot = medSnapshot(before, archived = false),
                newSnapshot = medSnapshot(after, archived = false),
                effectiveDate = toDate.toString(),
                // reason 一列按实体注释是停药原因（StopReason 小写）；非停药行填本条变更的类别，
                // 供台账/导出辨认，没有任何读取方按它分支（UI 只查 change_type='stop'）。
                reason = "postpone",
                source = "self",
            )
        )
    }

    // ---- 通知动作写入（ReminderReceiver / CheckInActionReceiver 共用） ----
    /**
     * @param slotDate v1.0.73（P1-4）：槽位所属日期；为 null 时按今天（应用内打卡）处理。
     */
    suspend fun checkInByMedId(medId: String, slotKey: String?, slotTime: String?, slotDate: LocalDate? = null): Boolean {
        val med = medDao.byId(medId) ?: return false
        checkIn(med, slotKey, slotTime, date = slotDate ?: LocalDate.now())
        return true
    }

    private companion object {
        /**
         * 计划槽位物化窗口往回几天（1 = 昨天）。
         *
         * 昨天必须在窗口里：跨零点补记卡与「昨天有几剂没记录」的补发通知都依赖它的行。
         */
        const val PLANNED_SLOT_BACK_DAYS = 1L
    }
}

/**
 * v1.0.77（批次 3b）：计划槽位物化窗口（闭区间）。
 *
 * 用数据类而不是 `Pair<LocalDate, LocalDate>`：`first / second` 在这种「两个都是日期」的
 * 场景里极易写反，而写反不会有编译错误——只会把昨天和下周的窗口调个头。
 */
data class PlannedSlotWindow(val from: LocalDate, val to: LocalDate)

/**
 * v1.0.77（批次 3b）：**一批插入内的主键去重**（纯函数，可单测）。
 *
 * [Ids.new] 的主键 = 毫秒时间基 + 3 位随机尾；一次物化在同一两毫秒内生成几十行时，
 * 撞尾概率到百分之几量级——而 `INSERT OR IGNORE` 遇到主键冲突是**静默丢弃**，
 * 表现为「某天的计划凭空少一剂」，没有任何日志。故同一批内自己去重。
 *
 * 撞了就加计数后缀（`xxx-2`、`xxx-3`…）而不是重摇随机数：重摇仍是随机过程，理论上可以一直撞；
 * 后缀是确定性的，且主键只要求本地唯一。
 */
internal fun uniqueSlotId(base: String, used: MutableSet<String>): String {
    if (used.add(base)) return base
    var n = 2
    while (!used.add("$base-$n")) n++
    return "$base-$n"
}
