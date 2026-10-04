package com.ashkb.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
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
import com.ashkb.app.data.entity.ExercisePlan
import com.ashkb.app.data.entity.FlareEvent
import com.ashkb.app.data.entity.FoodAvoidItem
import com.ashkb.app.data.entity.ImagingRecord
import com.ashkb.app.data.entity.KbEntry
import com.ashkb.app.data.entity.LabResult
import com.ashkb.app.data.entity.Medication
import com.ashkb.app.data.entity.MedicationChange
import com.ashkb.app.data.entity.MedicationLog
import com.ashkb.app.data.entity.PlannedSlot
import com.ashkb.app.data.entity.Profile
import com.ashkb.app.data.entity.Recipe
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

    /** v1.0.48：已停用（归档）药品——药单「已停用药品」区用，按停用时间倒序 */
    @Query("SELECT * FROM medications WHERE is_archived = 1 ORDER BY updated_at DESC")
    fun observeArchived(): Flow<List<Medication>>

    /**
     * v1.0.71：物理删除已停用药品。
     *
     * SQL 里再加一道 `is_archived = 1` 门禁（调用方已校验，这里是第二道防线）：
     * 在用药品必须先走「停用」才可能被删——一条 DELETE 抹掉在服医嘱是不可接受的。
     */
    @Query("DELETE FROM medications WHERE id = :id AND is_archived = 1")
    suspend fun deleteArchived(id: String)
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

    /**
     * P4 M9「记录内完成度」（v1.0.76（批次 3a）改名，原「服药依从」）的取数：
     * 区间内**计划打卡**（`prn_flag = 0`）的指定状态条数。
     *
     * 为什么排除 PRN：按需药的打卡次数由疼痛 / 发作决定，既没有计划剂量也没有「漏服」，
     * 混进分母只会抬高或稀释完成度——它由 [countPrnBetween] 单独报数（原实现两者混算，指标虚高）。
     */
    @Query(
        "SELECT COUNT(*) FROM medication_logs WHERE date BETWEEN :from AND :to " +
            "AND status = :status AND prn_flag = 0",
    )
    suspend fun countScheduledBetweenStatus(from: String, to: String, status: String): Int

    /** v1.0.76（批次 3a）：区间内按需（PRN）打卡条数——单独统计，不参与完成度。 */
    @Query("SELECT COUNT(*) FROM medication_logs WHERE date BETWEEN :from AND :to AND prn_flag = 1")
    suspend fun countPrnBetween(from: String, to: String): Int

    /**
     * v1.0.48：某条药的用药记录（区间内倒序），供药单点开查看流水。
     *
     * 排序：`date DESC, scheduled_time DESC`——(date, med_id, slot_key) 有唯一索引，
     * 故同日内按计划时刻倒序即是稳定的时间线；PRN（scheduled_time 为 NULL）排在当日末尾。
     *
     * **按 med_id 查是有意的**：归档（停药）不改写历史日志，归档药的流水仍能查全。
     */
    @Query(
        "SELECT * FROM medication_logs WHERE med_id = :medId AND date >= :from " +
            "ORDER BY date DESC, scheduled_time DESC",
    )
    fun observeByMedSince(medId: String, from: String): Flow<List<MedicationLog>>

    /** v1.0.71：某条药名下的打卡记录条数——删除确认框要如实报出条数（不可逆操作不能含糊）。 */
    @Query("SELECT COUNT(*) FROM medication_logs WHERE med_id = :medId")
    suspend fun countOfMed(medId: String): Int

    /**
     * v1.0.77（批次 3b）：区间内的全部用药记录（含 PRN）。
     *
     * 为什么需要「整行」而不只是计数：计划剂量口径的完成度要按 `(date, med_id, slot_key)`
     * 与 `planned_slots` 里的计划槽位**逐条配对**——计数版本（[countScheduledBetweenStatus]）
     * 只有三态条数，无法知道「哪一剂没记录」，也就分不出「未记录（missed）」。
     */
    @Query("SELECT * FROM medication_logs WHERE date BETWEEN :from AND :to")
    suspend fun listBetween(from: String, to: String): List<MedicationLog>

    /**
     * v1.0.71：随药档一并**物理删除**该药的全部打卡记录。
     *
     * 为什么连带删：只删药档而留着记录，报表依从率仍会把它们算进去——用户要清掉
     * 「测试用药」的痕迹就清不干净（用户 2026-09-27 拍板）。代价是历史统计会变，
     * 故确认框必须报出条数（见 [com.ashkb.app.domain.MedDeletion]）。
     */
    @Query("DELETE FROM medication_logs WHERE med_id = :medId")
    suspend fun deleteOfMed(medId: String)
}

@Dao
interface MedicationChangeDao {
    @Insert
    suspend fun insert(change: MedicationChange)

    @Query("SELECT * FROM medication_changes WHERE med_id = :medId ORDER BY effective_date DESC, recorded_at DESC")
    suspend fun byMed(medId: String): List<MedicationChange>

    @Query("SELECT * FROM medication_changes ORDER BY effective_date DESC, recorded_at DESC LIMIT :limit")
    fun observeRecent(limit: Int = 50): Flow<List<MedicationChange>>

    /**
     * v1.0.48：全部停药变更——「已停用药品」区按 med_id 取最近一条以显示停药原因 / 日期 / 备注。
     *
     * 停药原因不落在 `medications` 上（那里只有 `is_archived` 一个布尔），而在本表
     * `change_type='stop'` 的记录里（见 MedicationRepository.stopMedication）。
     */
    @Query("SELECT * FROM medication_changes WHERE change_type = 'stop' ORDER BY recorded_at DESC")
    fun observeStops(): Flow<List<MedicationChange>>

    /** v1.0.71：随药档一并删除该药的变更记录，避免留下指向已删药档的孤儿行。 */
    @Query("DELETE FROM medication_changes WHERE med_id = :medId")
    suspend fun deleteOfMed(medId: String)
}

@Dao
interface KbEntryDao {
    @Query("SELECT COUNT(*) FROM kb_entries")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(entries: List<KbEntry>)

    /** v1.0.44（N3）：种子增量刷新用——一次取回全表（固定 47+ 条，量级可忽略） */
    @Query("SELECT * FROM kb_entries")
    suspend fun listAll(): List<KbEntry>

    /**
     * v1.0.44（N3）：按主键整行更新（种子内容修订时用）。
     * 调用方必须先把 `userNote` 从旧行拷回，否则会抹掉用户的个人备注。
     */
    @Update
    suspend fun updateAll(entries: List<KbEntry>)

    /**
     * v1.1.2（批次 18）：**取全部相互作用条目，匹配交给 Kotlin 侧的结构化判定**
     * （`domain/DrugInteractionKeys` + `MedicationRepository.interactionsFor`）。
     *
     * 为什么删掉旧的 `interactionsFor(key)`（`payload LIKE '%'||:key||'%'`）：
     * 它把「条目关心哪个药」这件事交给**子串巧合**决定，实测两个方向都错——
     *   · 漏：JAK 一类的键压根没有对应分支，乌帕替尼 / 托法替布 / 巴瑞替尼药单零提示；
     *   · 错：`MedClass.OTHER.name` = "OTHER" 命中了 itx-015 引文里的英文单词 `other`，
     *     于是钙剂 / 维生素 D3 / 骨化三醇药单收到「阿仑膦酸钠服用规则」。
     * 正确性无法靠「把键拼得更细」解决——子串匹配本身不是「药物身份」的表达方式。
     *
     * 不新增索引列的取舍：交互条目固定 15 条（`kb_seed_itx.json`），一次全取在量级上可忽略；
     * 而加 `drug_a_key`/`drug_b_key` 索引列要牵动 Room 迁移、schema 导出、备份恢复回填
     * 与 `KB_SEED_VERSION` 闸门四处（见 `MIGRATION_8_9` 为 `search_text` 付过的代价），
     * 换来的只是把同一次判定从 Kotlin 挪回 SQL。本次选择**把改动收敛在代码层**。
     *
     * 排序与 [FoodAvoidItemDao.observeAll] 同一个坑：`severity_level` 是 TEXT，
     * `ORDER BY severity_level DESC` 得到的是 medium → low → high。
     * 这里改成 `CASE` 序数排序，让「DAO 自己给出的顺序」就是对的——
     * 过去它被 `MedicationRepository.severityRank` 的重排**掩盖**了，
     * 于是同一个 bug 看起来「已经被处理过」，下一个调用者直接吃到错序。
     */
    @Query(
        "SELECT * FROM kb_entries WHERE category = 'interaction' " +
            "ORDER BY CASE severity_level WHEN 'high' THEN 3 WHEN 'medium' THEN 2 ELSE 1 END DESC, id",
    )
    suspend fun interactions(): List<KbEntry>

    @Query(
        "SELECT * FROM kb_entries WHERE category = 'interaction' " +
            "ORDER BY CASE severity_level WHEN 'high' THEN 3 WHEN 'medium' THEN 2 ELSE 1 END DESC, id",
    )
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

    /**
     * v1.0.80（批次 6）：删除某天的症状记录（误录）。
     *
     * 按 **id** 删而不是按 date：`date` 上有唯一索引，按 id 删把「哪天」这件事交给调用方，
     * DAO 不承担日期解析（仓库层已经从行里拿到 id 了）。
     * 它派生出来的红旗警报（发热 / 眼 / 神经）由仓库层一并清理，见 `HealthRepository.deleteSymptom`。
     */
    @Query("DELETE FROM symptom_daily WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface BasdaiDao {
    @Query("SELECT * FROM basdai_records ORDER BY date DESC LIMIT :limit")
    fun observeRecent(limit: Int = 20): Flow<List<BasdaiRecord>>

    @Query("SELECT * FROM basdai_records WHERE date BETWEEN :from AND :to ORDER BY date")
    suspend fun between(from: String, to: String): List<BasdaiRecord>

    @Query("SELECT * FROM basdai_records WHERE date = :date ORDER BY recorded_at DESC LIMIT 1")
    suspend fun byDate(date: String): BasdaiRecord?

    /** v1.0.59 B5：最近一次评估记录（BasdaiReminderScheduler 推算 dueDate / Receiver 验真用）。 */
    @Query("SELECT * FROM basdai_records ORDER BY date DESC, recorded_at DESC LIMIT 1")
    suspend fun latest(): BasdaiRecord?

    /**
     * v1.1.2（批次 18）：最近两次评估记录（`edu-th-002` 触发判定用）。
     *
     * 取「最近两次」而不是「某个固定窗口内的两次」：窗口宽度取决于**自评周期**
     * （`ReminderConfigRepository.DEFAULT_BASDAI_CYCLE = 28` 天），写死一个比周期短的窗口
     * 会让按默认节奏自评的患者永远攒不出第二条记录——`basdai_high` 于是成了一条死分支
     * （审查报告 §五）。这里只取行，判定在 `HealthRepository.evaluateBasdaiAlert`。
     */
    @Query("SELECT * FROM basdai_records ORDER BY date DESC, recorded_at DESC LIMIT 2")
    suspend fun latestTwo(): List<BasdaiRecord>

    @Upsert
    suspend fun upsert(record: BasdaiRecord)

    /** 同日仅保留一条（覆盖语义下清理历史遗留的重复行） */
    @Query("DELETE FROM basdai_records WHERE date = :date AND id != :keepId")
    suspend fun deleteOtherRowsForDate(date: String, keepId: String)

    /** v1.0.80（批次 6）：按主键取一条（编辑 / 删除前取原值）。 */
    @Query("SELECT * FROM basdai_records WHERE id = :id")
    suspend fun byId(id: String): BasdaiRecord?

    /**
     * v1.0.80（批次 6）：删除一条自评记录（误录）。
     *
     * 按 id 删是**必须**的：`deleteOtherRowsForDate` 是「同日只留一条」的覆盖语义，
     * 拿它当删除入口会把同日的其它行也抹掉——而自评记录的价值恰恰在于逐条留痕。
     */
    @Query("DELETE FROM basdai_records WHERE id = :id")
    suspend fun delete(id: String)
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

    /** v1.0.80（批次 6）：按主键取一条（编辑预填 / 删除前取原窗口算派生警报）。 */
    @Query("SELECT * FROM flare_events WHERE id = :id")
    suspend fun byId(id: String): FlareEvent?

    /** v1.0.80（批次 6）：全部发作记录——删除 / 编辑后重算「第 7 天」警报时，要看**其余**发作的窗口。 */
    @Query("SELECT * FROM flare_events ORDER BY start_date DESC")
    suspend fun listAll(): List<FlareEvent>

    /**
     * v1.0.80（批次 6）：删除一次发作登记（误录）。
     *
     * 它派生出来的 `flare_day7` 警报由仓库层按窗口重算后清理——那个警报的 refDate 是**报警当日**
     * （不是发作开始日），是否属于这次发作只能靠窗口判定，故不放在这里做。
     */
    @Query("DELETE FROM flare_events WHERE id = :id")
    suspend fun delete(id: String)
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

    /**
     * v1.0.80（批次 6）：删除一条运动打卡（误录）。
     *
     * ⚠️ 打卡会**改变运动提醒的排程**（「今天已打卡」时今日升级重查不再重建，见 ExerciseViewModel），
     * 故调用方删完必须重排提醒——仓库侧做不到（提醒层依赖 AlarmManager，不是数据层的事）。
     */
    @Query("DELETE FROM exercise_logs WHERE id = :id")
    suspend fun delete(id: String)
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

    /**
     * v1.0.80（批次 6）：清掉某条记录**派生**出来的警报（删除记录时的「不留幽灵」）。
     *
     * 只删 `ack_at IS NULL` 的：用户已经点过「知道了」的警报是「他确实看过这条提醒」的留痕，
     * 不在未读列表里出现，也就不构成幽灵；把它删掉反而是销毁用户已确认过的信息。
     */
    @Query("DELETE FROM alerts WHERE alert_type = :type AND ref_date = :refDate AND ack_at IS NULL")
    suspend fun deleteUnackedByRef(type: String, refDate: String)

    /**
     * v1.0.80（批次 6）：按类型取全部**未确认**警报——发作第 7 天警报的 refDate 是报警当日，
     * 要判断它是否由某次发作派生，只能拿它与各次发作的日期窗口比对（见 `DerivedAlerts`）。
     */
    @Query("SELECT * FROM alerts WHERE alert_type = :type AND ack_at IS NULL")
    suspend fun listUnackedByType(type: String): List<Alert>
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

    /**
     * U1 真删（误录/停用后清理档案）。
     *
     * v1.0.81（批次 7）更正：**必须与 [SupplementLogDao.deleteBySupId] 同事务使用**
     * （走 `HealthRepository.deleteSupplement`）。上一版注释写的「历史 supplement_logs 快照自持，不受影响」
     * 说的是「只删这一行」，却漏了后果：记录会变成 `sup_id` 指向不存在补剂的孤儿行，
     * 用户再也打不开、也删不掉它们。本方法本身仍是单表删除，级联由仓库层负责。
     */
    @Query("DELETE FROM supplements WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface SupplementLogDao {
    @Query("SELECT * FROM supplement_logs WHERE date = :date ORDER BY scheduled_time")
    fun observeByDate(date: String): Flow<List<SupplementLog>>

    @Query("SELECT * FROM supplement_logs WHERE date = :date")
    suspend fun byDate(date: String): List<SupplementLog>

    /**
     * v1.1.1：**判等用 `IS` 而不是 `=`**——这是"补剂连点会写多行"的根因。
     *
     * `slot_key` 在本表恒为 NULL（补剂打卡没有槽位，见 `WellnessViewModel.checkInSupplement`），
     * 而 SQL 里 `NULL = NULL` 的结果是 NULL（不为真）→ 这个幂等守卫**永远返回 null**，
     * `HealthRepository.checkInSupplement` 于是每次点击都当作新记录插入一行。
     * 后果不止"历史里多几行"：报表的补剂完成度按**行**计数，连点三次 = 分母 +3，
     * 用户看到的是被自己手速抬高的依从率（本批次修的 HIGH-1）。
     *
     * `IS` 是 SQLite 的 NULL 安全判等（`NULL IS NULL` 为真），语义正是这里要的
     * 「同一天、同一补剂、都没有槽位」= 同一条记录。药品侧不需要改：那边只在 `slotKey` 非空时
     * 查库（PRN 刻意允许一天多行），调用点已用 `slotKey?.let { }` 挡住 NULL。
     */
    @Query("SELECT * FROM supplement_logs WHERE sup_id = :supId AND date = :date AND slot_key IS :slotKey")
    suspend fun find(supId: String, date: String, slotKey: String?): SupplementLog?

    /**
     * v1.1.1：窗口内**整行**（不过滤状态、不去重）——报表的补剂依从在 Kotlin 侧按
     * 「天 × 补剂」去重后再计数（见 `SupplementLogStatus.latestPerDay` 的 KDoc）。
     *
     * 为什么不再直接 `COUNT(*)`：连点留下的重复行、以及"同一天先跳过再补记已服"的两行，
     * 都会让分母虚增。去重键必须与卡片上的「今日打卡态」同源（那边也是取 `recordedAt` 最大的
     * 那一条），否则卡片显示一个状态、报表按另一个状态计数。
     */
    @Query("SELECT * FROM supplement_logs WHERE date BETWEEN :from AND :to")
    suspend fun between(from: String, to: String): List<SupplementLog>

    /**
     * U3 服用历史：按 sup_id 弱引用或名称快照匹配（补剂真删后仍可按快照追历史），日期窗口近 90 天。
     *
     * v1.0.87（批次 12）：状态过滤由**写死的 `status = 'done'`** 改为入参 [statuses]，
     * 调用方传 [com.ashkb.app.domain.AdherenceCalc.SETTLED_STATUSES]（done / partial / skipped）。
     * 补剂卡片新增「跳过」后，跳过也是一次交代：它必须出现在历史流里（否则用户按了跳过、
     * 卡片上的胶囊也变了，翻历史却找不到那条记录，只会以为没记上）。
     * 过滤值不从 SQL 里再抄一份，是为了让「已结算」这套定义全应用只有 [AdherenceCalc.SETTLED_STATUSES] 一处。
     */
    @Query("SELECT * FROM supplement_logs WHERE (sup_id = :supId OR (sup_id IS NULL AND sup_name = :name)) " +
        "AND status IN (:statuses) AND date >= :fromDate ORDER BY date DESC, recorded_at DESC")
    fun observeHistoryFor(
        supId: String,
        name: String,
        statuses: Collection<String>,
        fromDate: String,
    ): Flow<List<SupplementLog>>

    @Upsert
    suspend fun upsert(log: SupplementLog)

    /**
     * v1.0.81（批次 7）：某补剂名下的记录条数（删除整个补剂时的确认框要如实报数）。
     *
     * 与 [deleteBySupId] **同一谓词**（`sup_id = :supId`）——报出的条数与实际删掉的条数必须是同一个数，
     * 否则确认框就是在骗用户。刻意不按 `sup_name` 兜底匹配：`sup_id IS NULL` 的同名行不是这条档案的记录
     * （App 自身写入的打卡恒带 sup_id，见 `WellnessViewModel.checkInSupplement`），按名字删会误伤导入的快照行。
     */
    @Query("SELECT COUNT(*) FROM supplement_logs WHERE sup_id = :supId")
    suspend fun countBySupId(supId: String): Int

    /**
     * v1.0.81（批次 7）：随补剂档案一并删除它名下的全部记录（避免 `sup_id` 悬空的孤儿行）。
     *
     * 不限日期：详情弹层只列最近 90 天，但「删掉这个补剂」是删掉它的全部记录——
     * 只删窗口内的会让 90 天前的记录永远留在库里且无人可见。
     */
    @Query("DELETE FROM supplement_logs WHERE sup_id = :supId")
    suspend fun deleteBySupId(supId: String)

    // v1.1.1（HIGH-1）：原 `countBetweenStatus`（按行 `COUNT(*)`）已删除——它是"补剂连点虚增
    // 依从率分母"的另一半：即便写侧不再产生重复行，用户库里已有的重复行、以及"同日先跳过再补记"
    // 的两行，也会被它按行数计进分母。报表改用 `between(...)` 取整行后在 Kotlin 侧按
    // 「天 × 补剂」去重（`SupplementLogStatus.latestPerDay`），口径与卡片上的打卡态同源。
    // 留着那个查询只会给下一个人一条"看起来能直接用"的错路。

    /**
     * v1.0.80（批次 6）：撤销一次补剂打卡（误点）。
     *
     * 删掉行 = 回到「今天还没服用」的**未记录**态——这正是误点后想要的结果；
     * 改成 `skipped` 是另一回事（那是在声称「我今天决定不吃」，用户并没有这个意思）。
     */
    @Query("DELETE FROM supplement_logs WHERE id = :id")
    suspend fun delete(id: String)
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

    /**
     * v1.0.45：按日期区间取体重（升序），供趋势页 7 / 30 / 90 天切换。
     * 原先趋势页用 `recent(60)` 取「最近 60 条」，与所选窗口无关——切到 7 天视图仍会带回更早的数据。
     */
    @Query("SELECT * FROM weight_logs WHERE date >= :from AND date <= :to ORDER BY date")
    suspend fun between(from: String, to: String): List<WeightLog>

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

    /**
     * v1.0.80（批次 6）：某日的身体围度记录。
     *
     * 为「改」而加：此前 `saveBodyMeasure` 对空白 id 一律新建行，于是**同一天改一次就多一行**，
     * 「最近记录」列表里堆着一串同一天的重复值，用户只能靠删除键一条条清。体征 / 体重早就是
     * 「同日覆盖」语义（见 `saveVitals` / `saveWeight`），围度没有理由不一样。
     */
    @Query("SELECT * FROM body_measures WHERE date = :date ORDER BY recorded_at DESC LIMIT 1")
    suspend fun byDate(date: String): BodyMeasure?

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
    /**
     * v1.1.2（批次 18）：忌口清单按**危险度**排序，**不能**直接 `ORDER BY severity DESC`。
     *
     * `severity` 是 TEXT，取值 `high` / `medium` / `low`。SQLite 的 TEXT 比较是**字典序**，
     * 而字典序里 `'high' < 'low' < 'medium'`（h < l < m）——`DESC` 得到的是
     * **medium → low → high**，最该忌口的那条恰好沉到列表最后。
     * 这条查询是**活的用户可见路径**（`WellnessScreen` 的忌口清单直接消费它），
     * 且此前**没有任何 Kotlin 侧纠正**——排序错在这里等于把最危险的信息埋掉。
     *
     * 用 `CASE` 把词汇映射成序数再排：与 `MedicationRepository.severityRank`
     * （high=3 / medium=2 / 其余=1）同一口径，不新造第二套等级定义。
     */
    @Query(
        "SELECT * FROM food_avoid_items " +
            "ORDER BY CASE severity WHEN 'high' THEN 3 WHEN 'medium' THEN 2 ELSE 1 END DESC, created_at",
    )
    fun observeAll(): Flow<List<FoodAvoidItem>>

    @Query(
        "SELECT * FROM food_avoid_items WHERE category = :category " +
            "ORDER BY CASE severity WHEN 'high' THEN 3 WHEN 'medium' THEN 2 ELSE 1 END DESC",
    )
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

    /**
     * v1.0.87（批次 13）：某个复诊项目名下的记录（「项目」→「记录」的项目筛选）。
     *
     * 为什么条件是 `item_id OR item_name`：`item_id` 列 v11 就建好了（还带索引），但
     * **至今没有任何写入路径**——复诊记录表单只填一个自由文本 `item_name`
     * （见 `CheckupForms.kt` 的 `CheckupRecordDraft.toRecord`），故现存记录的 `item_id` 全是 NULL。
     * 只按 `item_id` 过滤，维护者点「MRI」看到的会是空列表——正是这一批要消灭的「找不到东西」。
     * 名字快照是记录落库时抄下来的，项目后来改名它不跟着变，故两条都要认；改名后旧记录按新名字
     * 搜不到，这是快照口径的已知代价（与「历史记录靠 item_name 快照自持」的既定口径一致）。
     */
    @Query(
        "SELECT * FROM checkup_records WHERE item_id = :itemId OR item_name = :itemName " +
            "ORDER BY date DESC LIMIT :limit",
    )
    fun observeByItem(itemId: String, itemName: String, limit: Int = 10): Flow<List<CheckupRecord>>

    @Upsert
    suspend fun upsert(record: CheckupRecord)

    /** v1.0.59 B5：列出全部复诊记录（CheckupReminderScheduler.rescheduleAll / Receiver 验真用）。 */
    @Query("SELECT * FROM checkup_records ORDER BY date DESC")
    suspend fun listAll(): List<CheckupRecord>

    /** v1.0.80（批次 6）：按主键取一条（编辑预填 / 删除前确认存在）。 */
    @Query("SELECT * FROM checkup_records WHERE id = :id")
    suspend fun byId(id: String): CheckupRecord?

    /**
     * v1.0.80（批次 6）：删除一条复诊记录。
     *
     * ⚠️ 只删记录本身——它名下的化验 / 影像 / 附件由 `HealthRepository.deleteCheckupRecord`
     * **在一个事务里级联处理**。任何绕过仓库直接调本方法的写法都会留下孤儿行。
     */
    @Query("DELETE FROM checkup_records WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface LabResultDao {
    @Query("SELECT * FROM lab_results WHERE checkup_id = :checkupId ORDER BY test_name")
    fun observeByCheckup(checkupId: String): Flow<List<LabResult>>

    @Query("SELECT * FROM lab_results WHERE test_name = :testName ORDER BY date DESC LIMIT :limit")
    fun observeTrend(testName: String, limit: Int = 20): Flow<List<LabResult>>

    @Query("SELECT * FROM lab_results WHERE date BETWEEN :from AND :to ORDER BY date DESC, test_name")
    suspend fun between(from: String, to: String): List<LabResult>

    /**
     * 全部化验行（按日期升序）。
     *
     * 趋势页的炎症指标**刻意不设日期界**（v1.0.55）：化验是几个月一次的稀疏采样，
     * 套上「近 7/30/90 天」几乎永远是空的（用户实测正是如此）。行数天然很少（每年几次）。
     */
    @Query("SELECT * FROM lab_results ORDER BY date, recorded_at, id")
    suspend fun allOrdered(): List<LabResult>

    @Query("SELECT * FROM lab_results ORDER BY date DESC LIMIT :limit")
    fun observeRecent(limit: Int = 100): Flow<List<LabResult>>

    /**
     * v1.0.87（批次 13）：**全部**化验行数（不分页）。
     *
     * 为什么不能拿 `observeRecent(limit).size` 当总数：那是一个**分页窗口**的长度，
     * 库里行数 ≥ 窗口时它恒等于窗口值（初值 100）——健康页摘要「化验 100 条」正是这么来的，
     * 而且删掉几行也不会变（维护者真机反馈：删掉一条 4 项化验单后数字没动）。
     * 总数只能由 COUNT(*) 给出：它随化验表的任何增删改自动失效并重查。
     */
    @Query("SELECT COUNT(*) FROM lab_results")
    fun observeCount(): Flow<Int>

    /**
     * v11：把某一天的全部化验归属到指定复诊记录（或解除归属传 null）。
     * 覆盖式而非只补 NULL——用户改主意时要能重新归属；同一天的多行化验视作同一次就诊。
     */
    @Query("UPDATE lab_results SET checkup_id = :checkupId WHERE date = :date")
    suspend fun linkByDate(date: String, checkupId: String?)

    @Upsert
    suspend fun upsert(result: LabResult)

    /**
     * v1.0.80（批次 6）：某条复诊记录名下的化验条数——级联删除的确认框要**如实报数**
     * （用户看不见「这次就诊挂了多少项化验」，不报就是在让他盲删一份化验单）。
     */
    @Query("SELECT COUNT(*) FROM lab_results WHERE checkup_id = :checkupId")
    suspend fun countByCheckup(checkupId: String): Int

    /** v1.0.80（批次 6）：随复诊记录一并级联删除其名下化验（避免 checkup_id 指向已删记录的孤儿行）。 */
    @Query("DELETE FROM lab_results WHERE checkup_id = :checkupId")
    suspend fun deleteByCheckup(checkupId: String)

    /** v1.0.80（批次 6）：单条化验删除（误录 / 重复导入）。化验的 `abnormal` 是自身字段，无派生数据。 */
    @Query("DELETE FROM lab_results WHERE id = :id")
    suspend fun delete(id: String)
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

    /** v1.0.80（批次 6）：某条复诊记录名下的影像条数（级联删除确认框报数用）。 */
    @Query("SELECT COUNT(*) FROM imaging_records WHERE checkup_id = :checkupId")
    suspend fun countByCheckup(checkupId: String): Int

    /** v1.0.80（批次 6）：随复诊记录一并级联删除其名下影像。 */
    @Query("DELETE FROM imaging_records WHERE checkup_id = :checkupId")
    suspend fun deleteByCheckup(checkupId: String)

    /** v1.0.80（批次 6）：单条影像删除。附件按复诊记录归属（不挂影像 id），故此处无级联。 */
    @Query("DELETE FROM imaging_records WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface VaccineRecordDao {
    @Query("SELECT * FROM vaccine_records ORDER BY date DESC")
    fun observeAll(): Flow<List<VaccineRecord>>

    @Query("SELECT * FROM vaccine_records WHERE vaccine_type = :type ORDER BY date DESC")
    fun observeByType(type: String): Flow<List<VaccineRecord>>

    @Upsert
    suspend fun upsert(record: VaccineRecord)

    @Query("SELECT * FROM vaccine_records WHERE id = :id")
    suspend fun byId(id: String): VaccineRecord?

    /**
     * v1.0.80（批次 6）：某一天的全部接种记录。
     *
     * 用于**重算活疫苗安全警报**：警报按 `(type, ref_date=接种日)` 去重（见 HealthRepository），
     * 同一天可能记了多针，删掉 / 改掉其中一针时不能想当然地把警报一起删——必须看当天**其余**
     * 记录是否仍然成立。
     */
    @Query("SELECT * FROM vaccine_records WHERE date = :date")
    suspend fun byDate(date: String): List<VaccineRecord>

    /** v1.0.80（批次 6）：单条疫苗记录删除（误录）。派生警报由仓库层重算，不在这里处理。 */
    @Query("DELETE FROM vaccine_records WHERE id = :id")
    suspend fun delete(id: String)
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

    @Query("SELECT * FROM emergency_events WHERE id = :id")
    suspend fun byId(id: String): EmergencyEvent?

    /** v1.0.80（批次 6）：删除一条紧急事件记录（误录）。无派生数据——它不产生任何警报或提醒。 */
    @Query("DELETE FROM emergency_events WHERE id = :id")
    suspend fun delete(id: String)
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

    /**
     * v1.0.80（批次 6）：某条复诊记录名下的可见附件。
     *
     * 级联删除时**必须先拿到整行**（不只是条数）：删除要把磁盘文件一起清掉，
     * 而文件名只在这一行里（`file_name`），先 SQL 删行就再也找不到那个文件了。
     * 墓碑行（`deleted_at` 非空）不在其中：它们对用户不可见，且远端清理流程还在用。
     */
    @Query("SELECT * FROM checkup_attachments WHERE checkup_id = :checkupId AND deleted_at IS NULL ORDER BY created_at DESC")
    suspend fun listByCheckup(checkupId: String): List<CheckupAttachment>

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

// ===========================================================================
// v1.0.39：B3 食谱库 + B7 周期康复计划
// ===========================================================================

@Dao
interface RecipeDao {
    /** 收藏置顶，其余按创建时间——列表默认顺序。 */
    @Query("SELECT * FROM recipes ORDER BY is_favorite DESC, created_at")
    fun observeAll(): Flow<List<Recipe>>

    @Query("SELECT * FROM recipes ORDER BY is_favorite DESC, created_at")
    suspend fun listAll(): List<Recipe>

    @Query("SELECT * FROM recipes WHERE id = :id")
    suspend fun byId(id: String): Recipe?

    /** 已种入的种子 id（幂等种子的判重依据） */
    @Query("SELECT id FROM recipes WHERE is_seed = 1")
    suspend fun seedIds(): List<String>

    @Upsert
    suspend fun upsert(recipe: Recipe)

    @Query("UPDATE recipes SET is_favorite = :favorite, updated_at = :now WHERE id = :id")
    suspend fun setFavorite(id: String, favorite: Boolean, now: String)

    @Query("DELETE FROM recipes WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface ExercisePlanDao {
    /** 按周数升序（4 周 → 12 周），列表顺序稳定 */
    @Query("SELECT * FROM exercise_plans ORDER BY weeks, created_at")
    fun observeAll(): Flow<List<ExercisePlan>>

    @Query("SELECT * FROM exercise_plans ORDER BY weeks, created_at")
    suspend fun listAll(): List<ExercisePlan>

    @Query("SELECT * FROM exercise_plans WHERE is_active = 1 LIMIT 1")
    fun observeActive(): Flow<ExercisePlan?>

    @Query("SELECT * FROM exercise_plans WHERE is_active = 1 LIMIT 1")
    suspend fun active(): ExercisePlan?

    @Query("SELECT id FROM exercise_plans WHERE is_seed = 1")
    suspend fun seedIds(): List<String>

    @Upsert
    suspend fun upsert(plan: ExercisePlan)

    /** 同一时刻只允许一个在用计划（切换前先全部停用） */
    @Query("UPDATE exercise_plans SET is_active = 0, updated_at = :now")
    suspend fun deactivateAll(now: String)

    @Query("DELETE FROM exercise_plans WHERE id = :id")
    suspend fun delete(id: String)
}

// ===========================================================================
// v1.0.77（批次 3b）：计划槽位快照（planned_slots）
// ===========================================================================

/**
 * 计划槽位快照的读写。
 *
 * 只做四件事：**幂等批量写入**、**按日期区间取**、**判定某槽位是否已存在**、**随药档清理**。
 * 这里刻意**不**提供任何「更新计划」的方法——快照是当时计划的留痕，
 * 药档改了应该由物化例程写入**新日期**的行，而不是回头改写历史。
 */
@Dao
interface PlannedSlotDao {
    /**
     * 幂等批量写入（与其它「快照表」同策略）。
     *
     * `IGNORE` + `(date, med_id, slot_key)` 唯一索引 = 物化例程**可以每天反复跑**：
     * 已写过的槽位被忽略，不会重复插入、也不会覆盖已有行。
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(slots: List<PlannedSlot>)

    /** 某药在区间内的计划槽位（药单「用药记录」弹层的计划口径完成度用） */
    @Query(
        "SELECT * FROM planned_slots WHERE med_id = :medId AND date BETWEEN :from AND :to " +
            "ORDER BY date, slot_time",
    )
    suspend fun betweenForMed(medId: String, from: String, to: String): List<PlannedSlot>

    /**
     * 日期区间内的全部计划槽位（闭区间，报表用）。
     * 排序固定为「日期 + 计划时刻」，与日志列表的时间线口径一致。
     */
    @Query("SELECT * FROM planned_slots WHERE date BETWEEN :from AND :to ORDER BY date, slot_time")
    suspend fun between(from: String, to: String): List<PlannedSlot>

    /**
     * 该槽位是否已存在（幂等判定 / 自检用）。
     *
     * ⚠️ `slot_key` 用 `=` 比较，**NULL 永不相等**——与 `medication_logs` 的
     * `find(...)` 同款语义。计划槽位的 slotKey 恒来自 `ScheduleCalc`（"HH:mm" 或 "inj"，
     * 从不为 null），故这一限制不影响实际使用；真要按 NULL 查请自行用 `IS NULL` 的查询。
     */
    @Query("SELECT COUNT(*) FROM planned_slots WHERE date = :date AND med_id = :medId AND slot_key = :slotKey")
    suspend fun countAt(date: String, medId: String, slotKey: String?): Int

    /** [countAt] 的布尔形态：某日某槽位是否已物化。 */
    suspend fun exists(date: String, medId: String, slotKey: String?): Boolean =
        countAt(date, medId, slotKey) > 0

    /**
     * 删除某药的全部计划快照。
     *
     * 用在两处（语义不同，都是「这些行不该再参与统计」）：
     *  · **物理删除药档**（`MedicationRepository.deleteArchivedMedication`）——与日志 / 变更一起清；
     *    否则删掉「测试用药」后，计划口径的完成度仍会把它的计划剂量算进分母（用户清不干净痕迹）。
     *  · **停药**（`MedicationRepository.stopMedication`）——只删**今天起**的行（见 [deleteOfMedFrom]），
     *    保留停药前的历史：停药不是删除，历史计划剂量仍该留在统计里。
     */
    @Query("DELETE FROM planned_slots WHERE med_id = :medId")
    suspend fun deleteOfMed(medId: String)

    /**
     * 删除某药从 [fromDate]（含）起的计划快照——停药专用。
     *
     * 为什么必须删「停药后」的行：物化窗口是滚动 8 天，用户今天停药时，
     * **未来 7 天的行已经写进表里**了。若留着，此后每天的报表窗口都会把它们算成「未记录」，
     * 用户会看到一个自己已经停掉的药在不断产生漏服——比不删更糟。
     * 而 `date < fromDate` 的行必须保留：那是真实发生过的计划（停药前确实该吃）。
     */
    @Query("DELETE FROM planned_slots WHERE med_id = :medId AND date >= :fromDate")
    suspend fun deleteOfMedFrom(medId: String, fromDate: String)
}
