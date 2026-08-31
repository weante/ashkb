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
import com.ashkb.app.domain.ScheduleCalc
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
        it.status == "done" && ScheduleCalc.isLate(slotTime, it.takenAt, LocalDate.now())
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
