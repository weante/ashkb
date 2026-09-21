package com.ashkb.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.ashkb.app.data.entity.Alert
import com.ashkb.app.data.entity.BackupLedger
import com.ashkb.app.data.entity.BasdaiRecord
import com.ashkb.app.data.entity.BodyMeasure
import com.ashkb.app.data.entity.CheckupAttachment
import com.ashkb.app.data.entity.CheckupItem
import com.ashkb.app.data.entity.CheckupRecord
import com.ashkb.app.data.entity.DietProfile
import com.ashkb.app.data.entity.EmergencyContact
import com.ashkb.app.data.entity.EmergencyEvent
import com.ashkb.app.data.entity.ExerciseLog
import com.ashkb.app.data.entity.FlareEvent
import com.ashkb.app.data.entity.FoodAvoidItem
import com.ashkb.app.data.entity.ImagingRecord
import com.ashkb.app.data.entity.KbEntry
import com.ashkb.app.data.entity.LabResult
import com.ashkb.app.data.entity.Medication
import com.ashkb.app.data.entity.MedicationChange
import com.ashkb.app.data.entity.MedicationLog
import com.ashkb.app.data.entity.Profile
import com.ashkb.app.data.entity.Supplement
import com.ashkb.app.data.entity.SupplementLog
import com.ashkb.app.data.entity.SymptomDaily
import com.ashkb.app.data.entity.VaccineRecord
import com.ashkb.app.data.entity.Vitals
import com.ashkb.app.data.entity.WeightLog
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profile WHERE id = 1")
    fun observe(): Flow<Profile?>

    @Query("SELECT * FROM profile WHERE id = 1")
    suspend fun get(): Profile?

    @Upsert
    suspend fun upsert(profile: Profile)
}

@Dao
interface MedicationDao {
    @Query("SELECT * FROM medications WHERE is_archived = 0 ORDER BY created_at")
    fun observeActive(): Flow<List<Medication>>

    @Query("SELECT * FROM medications WHERE is_archived = 0")
    suspend fun listActive(): List<Medication>

    @Query("SELECT * FROM medications WHERE id = :id")
    suspend fun byId(id: String): Medication?

    @Upsert
    suspend fun upsert(medication: Medication)

    @Query("UPDATE medications SET is_archived = 1, updated_at = :now WHERE id = :id")
    suspend fun archive(id: String, now: String)
}

@Dao
interface MedicationLogDao {
    @Query("SELECT * FROM medication_logs WHERE date = :date")
    fun observeByDate(date: String): Flow<List<MedicationLog>>

    @Query("SELECT * FROM medication_logs WHERE date = :date")
    suspend fun byDate(date: String): List<MedicationLog>

    @Query("SELECT * FROM medication_logs WHERE med_id = :medId AND date = :date AND slot_key = :slotKey")
    suspend fun find(medId: String, date: String, slotKey: String?): MedicationLog?

    @Upsert
    suspend fun upsert(log: MedicationLog)

    @Query("SELECT COUNT(*) FROM medication_logs WHERE date BETWEEN :from AND :to")
    suspend fun countBetween(from: String, to: String): Int

    /** P4 M9 依从统计：区间内指定状态的打卡数（done / partial / skipped） */
    @Query("SELECT COUNT(*) FROM medication_logs WHERE date BETWEEN :from AND :to AND status = :status")
    suspend fun countBetweenStatus(from: String, to: String, status: String): Int
}

@Dao
interface MedicationChangeDao {
    @Insert
    suspend fun insert(change: MedicationChange)

    @Query("SELECT * FROM medication_changes WHERE med_id = :medId ORDER BY effective_date DESC, recorded_at DESC")
    suspend fun byMed(medId: String): List<MedicationChange>

    @Query("SELECT * FROM medication_changes ORDER BY effective_date DESC, recorded_at DESC LIMIT :limit")
    fun observeRecent(limit: Int = 50): Flow<List<MedicationChange>>
}

@Dao
interface KbEntryDao {
    @Query("SELECT COUNT(*) FROM kb_entries")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(entries: List<KbEntry>)

    @Query(
        "SELECT * FROM kb_entries WHERE category = 'interaction' AND " +
            "(payload LIKE '%' || :key || '%') ORDER BY severity_level DESC"
    )
    suspend fun interactionsFor(key: String): List<KbEntry>

    @Query("SELECT * FROM kb_entries WHERE category = 'interaction' ORDER BY severity_level DESC, id")
    fun observeInteractions(): Flow<List<KbEntry>>

    @Query("SELECT * FROM kb_entries WHERE id = :id")
    suspend fun byId(id: String): KbEntry?

    @Query("SELECT * FROM kb_entries WHERE category = :category ORDER BY id")
    fun observeByCategory(category: String): Flow<List<KbEntry>>

    @Query("SELECT * FROM kb_entries WHERE category = :category ORDER BY id")
    suspend fun listByCategory(category: String): List<KbEntry>

    @Query("SELECT * FROM kb_entries ORDER BY id")
    fun observeAll(): Flow<List<KbEntry>>

    /**
     * v9 检索：单列 `search_text` LIKE 替代原「title OR summary OR payload」三列 OR——
     * 每行谓词求值由 3 次降为 1 次，并加结果上限（KbSearch.MAX_RESULTS）。
     * 列由迁移 v8→v9 回填、种子导入时写入；旧备份恢复后由 BackupEngine 统一回填
     * （未回填的行 search_text 为 NULL，LIKE 结果为 NULL，不参与匹配）。
     * 不迁 FTS4 的原因见 domain/KbSearch 注释（FTS4 分词器对中文子串零命中）。
     */
    @Query("SELECT * FROM kb_entries WHERE `search_text` LIKE '%' || :q || '%' ORDER BY id LIMIT :limit")
    fun search(q: String, limit: Int): Flow<List<KbEntry>>

    @Query("SELECT * FROM kb_entries WHERE review_due < :today ORDER BY review_due")
    suspend fun overdueReview(today: String): List<KbEntry>

    /** R27 矩阵输入：取运动类条目按 grade_matrix / block_rule 过滤 */
    @Query("SELECT * FROM kb_entries WHERE category = 'exercise' ORDER BY id")
    suspend fun exercises(): List<KbEntry>

    @Query("SELECT * FROM kb_entries WHERE category = 'emergency' ORDER BY id")
    suspend fun emergencies(): List<KbEntry>

    /**
     * v10：写入个人备注层（B2）。传 null / 空白即清除备注。
     * 只更新 user_note 一列——不触碰种子层内容（title/summary/payload 等），
     * 保证「种子可随版本更新、个人备注自持」的双层语义。
     */
    @Query("UPDATE kb_entries SET user_note = :note WHERE id = :id")
    suspend fun updateUserNote(id: String, note: String?)

    /** v10：有个人备注的条目数（知识库页提示用） */
    @Query("SELECT COUNT(*) FROM kb_entries WHERE user_note IS NOT NULL AND user_note != ''")
    fun observeNoteCount(): Flow<Int>
}

@Dao
interface SymptomDailyDao {
    @Query("SELECT * FROM symptom_daily WHERE date = :date")
    suspend fun byDate(date: String): SymptomDaily?

    @Query("SELECT * FROM symptom_daily WHERE date = :date")
    fun observeByDate(date: String): Flow<SymptomDaily?>

    @Query("SELECT * FROM symptom_daily WHERE date BETWEEN :from AND :to ORDER BY date")
    suspend fun between(from: String, to: String): List<SymptomDaily>

    @Upsert
    suspend fun upsert(log: SymptomDaily)
}

@Dao
interface BasdaiDao {
    @Query("SELECT * FROM basdai_records ORDER BY date DESC LIMIT :limit")
    fun observeRecent(limit: Int = 20): Flow<List<BasdaiRecord>>

    @Query("SELECT * FROM basdai_records WHERE date BETWEEN :from AND :to ORDER BY date")
    suspend fun between(from: String, to: String): List<BasdaiRecord>

    @Query("SELECT * FROM basdai_records WHERE date = :date ORDER BY recorded_at DESC LIMIT 1")
    suspend fun byDate(date: String): BasdaiRecord?

    @Upsert
    suspend fun upsert(record: BasdaiRecord)

    /** 同日仅保留一条（覆盖语义下清理历史遗留的重复行） */
    @Query("DELETE FROM basdai_records WHERE date = :date AND id != :keepId")
    suspend fun deleteOtherRowsForDate(date: String, keepId: String)
}

@Dao
interface FlareDao {
    @Query("SELECT * FROM flare_events WHERE status = 'active' ORDER BY start_date DESC LIMIT 1")
    fun observeActive(): Flow<FlareEvent?>

    @Query("SELECT * FROM flare_events ORDER BY start_date DESC LIMIT :limit")
    fun observeRecent(limit: Int = 30): Flow<List<FlareEvent>>

    @Query("SELECT * FROM flare_events WHERE status = 'active' ORDER BY start_date DESC LIMIT 1")
    suspend fun activeFlare(): FlareEvent?

    /** P4 M9：区间发作记录 */
    @Query("SELECT * FROM flare_events WHERE start_date BETWEEN :from AND :to ORDER BY start_date")
    suspend fun between(from: String, to: String): List<FlareEvent>

    @Insert
    suspend fun insert(event: FlareEvent)

    @Upsert
    suspend fun upsert(event: FlareEvent)
}

@Dao
interface ExerciseLogDao {
    @Query("SELECT * FROM exercise_logs WHERE date = :date ORDER BY recorded_at")
    fun observeByDate(date: String): Flow<List<ExerciseLog>>

    @Query("SELECT * FROM exercise_logs WHERE date = :date")
    suspend fun byDate(date: String): List<ExerciseLog>

    /** R21：待反馈的昨日运动打卡（fb 为空且已完成） */
    @Query(
        "SELECT * FROM exercise_logs WHERE date = :date AND status = 'done' " +
            "AND fb_pain_change IS NULL ORDER BY recorded_at"
    )
    suspend fun pendingFeedback(date: String): List<ExerciseLog>

    @Upsert
    suspend fun upsert(log: ExerciseLog)

    /** P4 M9：区间运动打卡（依从统计） */
    @Query("SELECT * FROM exercise_logs WHERE date BETWEEN :from AND :to ORDER BY date")
    suspend fun between(from: String, to: String): List<ExerciseLog>
}

@Dao
interface AlertDao {
    @Query("SELECT * FROM alerts WHERE ack_at IS NULL ORDER BY created_at DESC")
    fun observeUnacked(): Flow<List<Alert>>

    @Insert
    suspend fun insert(alert: Alert)

    @Query("UPDATE alerts SET ack_at = :now WHERE id = :id")
    suspend fun ack(id: String, now: String)

    @Query("SELECT COUNT(*) FROM alerts WHERE alert_type = :type AND ref_date = :refDate")
    suspend fun countByRef(type: String, refDate: String): Int
}

// ===========================================================================
// P3 DAO：M2/M3 营养与体征、M6 复诊、M7 紧急卡
// ===========================================================================

@Dao
interface SupplementDao {
    @Query("SELECT * FROM supplements WHERE is_archived = 0 ORDER BY created_at")
    fun observeActive(): Flow<List<Supplement>>

    @Query("SELECT * FROM supplements WHERE is_archived = 0")
    suspend fun listActive(): List<Supplement>

    @Query("SELECT * FROM supplements WHERE id = :id")
    suspend fun byId(id: String): Supplement?

    @Upsert
    suspend fun upsert(supplement: Supplement)

    @Query("UPDATE supplements SET is_archived = 1, updated_at = :now WHERE id = :id")
    suspend fun archive(id: String, now: String)

    /** U1 真删（误录/停用后清理档案）；历史 supplement_logs 快照自持，不受影响 */
    @Query("DELETE FROM supplements WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface SupplementLogDao {
    @Query("SELECT * FROM supplement_logs WHERE date = :date ORDER BY scheduled_time")
    fun observeByDate(date: String): Flow<List<SupplementLog>>

    @Query("SELECT * FROM supplement_logs WHERE date = :date")
    suspend fun byDate(date: String): List<SupplementLog>

    @Query("SELECT * FROM supplement_logs WHERE sup_id = :supId AND date = :date AND slot_key = :slotKey")
    suspend fun find(supId: String, date: String, slotKey: String?): SupplementLog?

    /** U3 服用历史：按 sup_id 弱引用或名称快照匹配（补剂真删后仍可按快照追历史），日期窗口近 90 天 */
    @Query("SELECT * FROM supplement_logs WHERE (sup_id = :supId OR (sup_id IS NULL AND sup_name = :name)) " +
        "AND status = 'done' AND date >= :fromDate ORDER BY date DESC, recorded_at DESC")
    fun observeHistoryFor(supId: String, name: String, fromDate: String): Flow<List<SupplementLog>>

    @Upsert
    suspend fun upsert(log: SupplementLog)
}

@Dao
interface VitalsDao {
    @Query("SELECT * FROM vitals WHERE date = :date ORDER BY recorded_at DESC LIMIT 1")
    fun observeLatestByDate(date: String): Flow<Vitals?>

    @Query("SELECT * FROM vitals WHERE date = :date ORDER BY recorded_at DESC LIMIT 1")
    suspend fun latestByDate(date: String): Vitals?

    @Query("SELECT * FROM vitals WHERE date BETWEEN :from AND :to ORDER BY date, recorded_at")
    fun observeBetween(from: String, to: String): Flow<List<Vitals>>

    /** U4 误录删除 */
    @Query("DELETE FROM vitals WHERE id = :id")
    suspend fun delete(id: String)

    @Upsert
    suspend fun upsert(vitals: Vitals)
}

@Dao
interface WeightLogDao {
    @Query("SELECT * FROM weight_logs WHERE date = :date LIMIT 1")
    fun observeByDate(date: String): Flow<WeightLog?>

    @Query("SELECT * FROM weight_logs WHERE date = :date LIMIT 1")
    suspend fun byDate(date: String): WeightLog?

    @Query("SELECT * FROM weight_logs ORDER BY date DESC LIMIT :limit")
    fun observeRecent(limit: Int = 30): Flow<List<WeightLog>>

    /** P4 M9：近期体重（趋势图） */
    @Query("SELECT * FROM weight_logs ORDER BY date DESC LIMIT :limit")
    suspend fun recent(limit: Int = 90): List<WeightLog>

    /** U4 误录删除 */
    @Query("DELETE FROM weight_logs WHERE id = :id")
    suspend fun delete(id: String)

    @Upsert
    suspend fun upsert(log: WeightLog)
}

@Dao
interface BodyMeasureDao {
    @Query("SELECT * FROM body_measures ORDER BY date DESC LIMIT 1")
    fun observeLatest(): Flow<BodyMeasure?>

    @Query("SELECT * FROM body_measures ORDER BY date DESC LIMIT :limit")
    fun observeRecent(limit: Int = 10): Flow<List<BodyMeasure>>

    /** U4 误录删除 */
    @Query("DELETE FROM body_measures WHERE id = :id")
    suspend fun delete(id: String)

    @Upsert
    suspend fun upsert(measure: BodyMeasure)
}

@Dao
interface DietProfileDao {
    @Query("SELECT * FROM diet_profile WHERE id = 1")
    fun observe(): Flow<DietProfile?>

    @Query("SELECT * FROM diet_profile WHERE id = 1")
    suspend fun get(): DietProfile?

    /** U4 清除画像（回到未设置态） */
    @Query("DELETE FROM diet_profile WHERE id = 1")
    suspend fun delete()

    @Upsert
    suspend fun upsert(profile: DietProfile)
}

@Dao
interface FoodAvoidItemDao {
    @Query("SELECT * FROM food_avoid_items ORDER BY severity DESC, created_at")
    fun observeAll(): Flow<List<FoodAvoidItem>>

    @Query("SELECT * FROM food_avoid_items WHERE category = :category ORDER BY severity DESC")
    fun observeByCategory(category: String): Flow<List<FoodAvoidItem>>

    @Upsert
    suspend fun upsert(item: FoodAvoidItem)

    @Query("DELETE FROM food_avoid_items WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface CheckupItemDao {
    @Query("SELECT * FROM checkup_items WHERE is_active = 1 ORDER BY check_type, created_at")
    fun observeActive(): Flow<List<CheckupItem>>

    @Query("SELECT * FROM checkup_items WHERE is_active = 1")
    suspend fun listActive(): List<CheckupItem>

    @Query("SELECT * FROM checkup_items WHERE id = :id")
    suspend fun byId(id: String): CheckupItem?

    @Upsert
    suspend fun upsert(item: CheckupItem)

    @Query("UPDATE checkup_items SET is_active = 0, updated_at = :now WHERE id = :id")
    suspend fun deactivate(id: String, now: String)
}

@Dao
interface CheckupRecordDao {
    @Query("SELECT * FROM checkup_records ORDER BY date DESC LIMIT :limit")
    fun observeRecent(limit: Int = 20): Flow<List<CheckupRecord>>

    @Query("SELECT * FROM checkup_records WHERE date BETWEEN :from AND :to ORDER BY date DESC")
    suspend fun between(from: String, to: String): List<CheckupRecord>

    @Query("SELECT * FROM checkup_records WHERE item_id = :itemId ORDER BY date DESC LIMIT :limit")
    fun observeByItem(itemId: String, limit: Int = 10): Flow<List<CheckupRecord>>

    @Upsert
    suspend fun upsert(record: CheckupRecord)
}

@Dao
interface LabResultDao {
    @Query("SELECT * FROM lab_results WHERE checkup_id = :checkupId ORDER BY test_name")
    fun observeByCheckup(checkupId: String): Flow<List<LabResult>>

    @Query("SELECT * FROM lab_results WHERE test_name = :testName ORDER BY date DESC LIMIT :limit")
    fun observeTrend(testName: String, limit: Int = 20): Flow<List<LabResult>>

    @Query("SELECT * FROM lab_results WHERE date BETWEEN :from AND :to ORDER BY date DESC, test_name")
    suspend fun between(from: String, to: String): List<LabResult>

    @Query("SELECT * FROM lab_results ORDER BY date DESC LIMIT :limit")
    fun observeRecent(limit: Int = 100): Flow<List<LabResult>>

    /**
     * v11：把某一天的全部化验归属到指定复诊记录（或解除归属传 null）。
     * 覆盖式而非只补 NULL——用户改主意时要能重新归属；同一天的多行化验视作同一次就诊。
     */
    @Query("UPDATE lab_results SET checkup_id = :checkupId WHERE date = :date")
    suspend fun linkByDate(date: String, checkupId: String?)

    @Upsert
    suspend fun upsert(result: LabResult)
}

@Dao
interface ImagingDao {
    @Query("SELECT * FROM imaging_records ORDER BY exam_date DESC LIMIT :limit")
    fun observeAll(limit: Int = 50): Flow<List<ImagingRecord>>

    /** v11：某条复诊记录下的影像（「记录」Tab 详情用） */
    @Query("SELECT * FROM imaging_records WHERE checkup_id = :checkupId ORDER BY exam_date DESC")
    fun observeByCheckup(checkupId: String): Flow<List<ImagingRecord>>

    /** v11：归属到指定复诊记录（或解除归属传 null） */
    @Query("UPDATE imaging_records SET checkup_id = :checkupId WHERE id = :id")
    suspend fun linkToCheckup(id: String, checkupId: String?)

    @Upsert
    suspend fun upsert(record: ImagingRecord)
}

@Dao
interface VaccineRecordDao {
    @Query("SELECT * FROM vaccine_records ORDER BY date DESC")
    fun observeAll(): Flow<List<VaccineRecord>>

    @Query("SELECT * FROM vaccine_records WHERE vaccine_type = :type ORDER BY date DESC")
    fun observeByType(type: String): Flow<List<VaccineRecord>>

    @Upsert
    suspend fun upsert(record: VaccineRecord)
}

@Dao
interface EmergencyEventDao {
    @Query("SELECT * FROM emergency_events ORDER BY date DESC LIMIT :limit")
    fun observeRecent(limit: Int = 20): Flow<List<EmergencyEvent>>

    @Query("SELECT * FROM emergency_events WHERE scene = :scene ORDER BY date DESC")
    fun observeByScene(scene: String): Flow<List<EmergencyEvent>>

    @Insert
    suspend fun insert(event: EmergencyEvent)

    @Upsert
    suspend fun upsert(event: EmergencyEvent)
}

@Dao
interface ContactDao {
    @Query("SELECT * FROM contacts WHERE is_emergency = 1 ORDER BY created_at")
    fun observeEmergency(): Flow<List<EmergencyContact>>

    @Query("SELECT * FROM contacts ORDER BY is_emergency DESC, created_at")
    fun observeAll(): Flow<List<EmergencyContact>>

    @Upsert
    suspend fun upsert(contact: EmergencyContact)

    @Query("DELETE FROM contacts WHERE id = :id")
    suspend fun delete(id: String)
}

// ===========================================================================
// P4 DAO：备份台账
// ===========================================================================

@Dao
interface BackupLedgerDao {
    @Query("SELECT * FROM backup_ledger ORDER BY created_at DESC LIMIT :limit")
    fun observeRecent(limit: Int = 30): Flow<List<BackupLedger>>

    @Query("SELECT * FROM backup_ledger WHERE ledger_type = :type ORDER BY created_at DESC LIMIT 1")
    suspend fun latestByType(type: String): BackupLedger?

    @Insert
    suspend fun insert(ledger: BackupLedger)
}

// ===========================================================================
// v10 DAO：复诊附件归档（B10）
// ===========================================================================

@Dao
interface CheckupAttachmentDao {
    /** 全量可见（按时间倒序）——附件中心列表。墓碑行（待远端清理）对用户不可见。 */
    @Query("SELECT * FROM checkup_attachments WHERE deleted_at IS NULL ORDER BY created_at DESC")
    fun observeAll(): Flow<List<CheckupAttachment>>

    /** 某条复诊记录下的附件 */
    @Query("SELECT * FROM checkup_attachments WHERE checkup_id = :checkupId AND deleted_at IS NULL ORDER BY created_at DESC")
    fun observeByCheckup(checkupId: String): Flow<List<CheckupAttachment>>

    @Query("SELECT * FROM checkup_attachments WHERE deleted_at IS NULL ORDER BY created_at DESC")
    suspend fun listAll(): List<CheckupAttachment>

    @Query("SELECT * FROM checkup_attachments WHERE id = :id")
    suspend fun byId(id: String): CheckupAttachment?

    @Upsert
    suspend fun upsert(attachment: CheckupAttachment)

    /** v11：改归属（或解除归属传 null）——用户在弹层里重新选择复诊记录时用 */
    @Query("UPDATE checkup_attachments SET checkup_id = :checkupId WHERE id = :id")
    suspend fun linkToCheckup(id: String, checkupId: String?)

    @Query("DELETE FROM checkup_attachments WHERE id = :id")
    suspend fun delete(id: String)

    // ---- v12（v1.0.35）WebDAV 同步 ----

    /** 待上传：可见且尚无远端副本 */
    @Query("SELECT * FROM checkup_attachments WHERE remote_path IS NULL AND deleted_at IS NULL ORDER BY created_at")
    suspend fun pendingUpload(): List<CheckupAttachment>

    /** 待清理远端：软删除墓碑行 */
    @Query("SELECT * FROM checkup_attachments WHERE deleted_at IS NOT NULL ORDER BY deleted_at")
    suspend fun pendingRemoteDelete(): List<CheckupAttachment>

    /** 远端校验的本地基准：已上传（有远端路径）的可见行 */
    @Query("SELECT * FROM checkup_attachments WHERE remote_path IS NOT NULL AND deleted_at IS NULL ORDER BY created_at")
    suspend fun withRemotePath(): List<CheckupAttachment>

    @Query("UPDATE checkup_attachments SET remote_path = :remotePath, remote_sha256 = :sha256, synced_at = :syncedAt WHERE id = :id")
    suspend fun markUploaded(id: String, remotePath: String, sha256: String, syncedAt: String)

    /** 软删除：置墓碑，等远端 DELETE 成功后再物理删行 */
    @Query("UPDATE checkup_attachments SET deleted_at = :deletedAt WHERE id = :id")
    suspend fun markDeleted(id: String, deletedAt: String)

    /** 同步状态计数（用于备份页摘要）：[全部可见, 已同步, 待上传, 待远端清理] */
    @Query(
        "SELECT (SELECT COUNT(*) FROM checkup_attachments WHERE deleted_at IS NULL) AS visible, " +
            "(SELECT COUNT(*) FROM checkup_attachments WHERE remote_path IS NOT NULL AND deleted_at IS NULL) AS synced, " +
            "(SELECT COUNT(*) FROM checkup_attachments WHERE remote_path IS NULL AND deleted_at IS NULL) AS pending, " +
            "(SELECT COUNT(*) FROM checkup_attachments WHERE deleted_at IS NOT NULL) AS toDelete"
    )
    fun observeSyncCounts(): Flow<AttachmentSyncCounts>
}

/** 附件同步摘要（Room 直接映射上面那条多子查询） */
data class AttachmentSyncCounts(
    val visible: Int = 0,
    val synced: Int = 0,
    val pending: Int = 0,
    val toDelete: Int = 0,
)
