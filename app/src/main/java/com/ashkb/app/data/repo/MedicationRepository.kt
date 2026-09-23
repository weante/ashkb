package com.ashkb.app.data.repo

import android.content.Context
import com.ashkb.app.data.db.AppDatabase
import com.ashkb.app.data.db.Ids
import com.ashkb.app.data.entity.KbEntry
import com.ashkb.app.data.entity.Medication
import com.ashkb.app.data.entity.MedicationChange
import com.ashkb.app.data.entity.MedicationLog
import com.ashkb.app.data.entity.Profile
import com.ashkb.app.data.entity.Reaction
import com.ashkb.app.data.entity.StopReason
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

    // ---- 档案 ----
    fun observeProfile(): Flow<Profile?> = profileDao.observe()
    suspend fun saveProfile(p: Profile) = profileDao.upsert(p.copy(updatedAt = nowIso()))

    // ---- 药单 ----
    fun observeMedications(): Flow<List<Medication>> = medDao.observeActive()
    suspend fun saveMedication(med: Medication) = medDao.upsert(med.copy(updatedAt = nowIso()))
    suspend fun medicationById(id: String): Medication? = medDao.byId(id)

    /** R17 停药：归档 + medication_changes 登记（裁决 A 快照，self_stopped 供警示联动） */
    suspend fun stopMedication(med: Medication, reason: String, note: String?) {
        val now = nowIso()
        medDao.archive(med.id, now)
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

    // ---- R03 核对清单：按 name_key 与类别查种子相互作用条目 ----
    suspend fun interactionsFor(med: Medication): List<KbEntry> =
        (listOf(med.nameKey, med.medClass) + medClassKeys(med))
            .filter { it.isNotBlank() }
            .distinct()
            .flatMap { kbDao.interactionsFor(it) }
            .distinctBy { it.id }
            .sortedByDescending { severityRank(it.severityLevel) }

    /** 类别键覆盖（如 nsaid 命中所有 NSAID 条目的 drug_a="nsaid"） */
    private fun medClassKeys(med: Medication): List<String> = when (med.medClass.lowercase()) {
        "nsaid" -> listOf("nsaid")
        "glucocorticoid" -> listOf("glucocorticoid", "steroid")
        "csdmard" -> listOf("mtx", "methotrexate", "sulfasalazine")
        "biologic" -> listOf("biologic", "adalimumab", "tnf")
        else -> emptyList()
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

    // ---- 打卡写入（幂等：date+medId+slotKey 唯一） ----
    suspend fun checkIn(med: Medication, slotKey: String?, slotTime: String?, reaction: String = Reaction.NONE.name, injSite: String? = null, note: String? = null) {
        val date = LocalDate.now().toString()
        val existing = slotKey?.let { logDao.find(med.id, date, it) }
        val now = nowIso()
        val log = (existing ?: MedicationLog(
            id = Ids.new("mlog"),
            date = date,
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
                )
            }
        }

    /** 已停用药品视图项（[stopReason] null = 无停药变更记录，见 [observeArchivedMedications]） */
    data class ArchivedMedication(
        val med: Medication,
        val stopDate: String?,
        val stopReason: StopReason?,
        val stopNote: String?,
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

    /** R17 注射顺延：锚点移至新日期，周期从新日期起算重排；实际注射发生时才写日志（未记录=无行） */
    suspend fun postponeInjection(med: Medication, toDate: LocalDate) {
        medDao.upsert(med.copy(startDate = toDate.toString(), updatedAt = nowIso()))
    }

    // ---- 通知动作写入（ReminderReceiver / CheckInActionReceiver 共用） ----
    suspend fun checkInByMedId(medId: String, slotKey: String?, slotTime: String?): Boolean {
        val med = medDao.byId(medId) ?: return false
        checkIn(med, slotKey, slotTime)
        return true
    }
}
