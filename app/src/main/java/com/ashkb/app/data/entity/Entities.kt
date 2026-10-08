package com.ashkb.app.data.entity

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ashkb.app.R

/**
 * P1 实体集：profile / medications / medication_logs / kb_entries。
 * 列名与《数据字典 D-2 v1.0》字段六要素一致（snake_case）；
 * V3 裁剪已生效：medication_logs 无 operator / source_cmd_id。
 */

/** 药物大类。v1.2.5（i18n）：展示文案改为字符串资源 `labelRes`（原先硬编码中文）。 */
enum class MedClass(@StringRes val labelRes: Int) {
    NSAID(R.string.enum_med_class_nsaid),
    CSDMARD(R.string.enum_med_class_csdmard),
    BIOLOGIC(R.string.enum_med_class_biologic),
    JAK(R.string.enum_med_class_jak),
    GLUCOCORTICOID(R.string.enum_med_class_glucocorticoid),
    OTHER(R.string.enum_med_class_other);

    companion object {
        fun fromKey(k: String?) = entries.firstOrNull { it.name.equals(k, true) } ?: OTHER
    }
}

/**
 * 用药频次。
 *
 * **v1.2.1**：`EmergencyMeds` 原先读 `label`，于是急救卡上会印出
 * 「25mg · 每周两次（如依那西普，选两个星期）· 每 14 天」（维护者截图发现）——
 * 括号里那半句是**给患者看的表单选择提示**，急救卡是给医生/急救人员看的，不该出现它。
 * 于是拆出 `plain`（纯名称，供急救卡），`label` 保持原样。
 *
 * **v1.2.4**：维护者要求把括号说明从**表单选择项**里也删掉（「将（）内容删除」），
 * 于是 `label` 与 `plain` 现在字面相同。**两个字段仍然并存**：`plain` 是急救卡的
 * 唯一来源，将来谁再往 `label` 加提示也不会漏进急救卡——v1.2.1 那个缺陷不会复发。
 *
 * **v1.2.5（i18n）**：两个字段都改成字符串资源（`labelRes` / `plainRes`）。
 * 「两条资源」这个结构必须保留——上面那条「字面相同但语义不同」的约束在 i18n 下更硬：
 * `plainRes` 是急救卡唯一来源，将来谁往 `labelRes` 加表单提示，也不该跟着进急救卡。
 *
 * [hidden] = 不出现在频次选择列表里，但**枚举项必须保留**：
 * 历史数据里已落库的 key（如 "Q8H"）仍要能解析出正确频次，
 * 删掉枚举项会让 `fromKey` 退回 DAILY，把「每 8 小时」静默改成「每日」。
 */
enum class MedFrequency(@StringRes val labelRes: Int, @StringRes val plainRes: Int, val hidden: Boolean = false) {
    DAILY(R.string.enum_med_frequency_daily, R.string.enum_med_frequency_daily_plain),
    BID(R.string.enum_med_frequency_bid, R.string.enum_med_frequency_bid_plain),
    Q8H(R.string.enum_med_frequency_q8h, R.string.enum_med_frequency_q8h_plain, hidden = true),
    WEEKLY(R.string.enum_med_frequency_weekly, R.string.enum_med_frequency_weekly_plain),
    BIW(R.string.enum_med_frequency_biw, R.string.enum_med_frequency_biw_plain),
    Q2W(R.string.enum_med_frequency_q2w, R.string.enum_med_frequency_q2w_plain),
    MONTHLY(R.string.enum_med_frequency_monthly, R.string.enum_med_frequency_monthly_plain),
    PRN(R.string.enum_med_frequency_prn, R.string.enum_med_frequency_prn_plain),
    CUSTOM(R.string.enum_med_frequency_custom, R.string.enum_med_frequency_custom_plain);

    companion object {
        fun fromKey(k: String?) = entries.firstOrNull { it.name.equals(k, true) } ?: DAILY
    }
}

/** 打卡状态（完成 / 部分完成 / 跳过）。v1.2.5（i18n）：文案改为字符串资源 `labelRes`。 */
enum class CheckStatus(@StringRes val labelRes: Int) {
    DONE(R.string.enum_check_status_done),
    PARTIAL(R.string.enum_check_status_partial),
    SKIPPED(R.string.enum_check_status_skipped),
}

/** 漏服 / 跳过原因。v1.2.5（i18n）：文案改为字符串资源 `labelRes`。 */
enum class SkipReason(@StringRes val labelRes: Int) {
    TOO_BUSY(R.string.enum_skip_reason_too_busy),
    UNWELL(R.string.enum_skip_reason_unwell),
    HOSPITALIZED(R.string.enum_skip_reason_hospitalized),
    SIDE_EFFECT(R.string.enum_skip_reason_side_effect),
    // C5（v1.0.37）：对齐规划口径的漏服原因（遗忘 / 外出 / 药物用完）。
    // 新增项追加在 OTHER 之前——枚举 key 以 name 存库，追加不影响历史数据。
    FORGOT(R.string.enum_skip_reason_forgot),
    OUTING(R.string.enum_skip_reason_outing),
    RUN_OUT(R.string.enum_skip_reason_run_out),
    OTHER(R.string.enum_skip_reason_other);

    companion object {
        /**
         * v1.0.48：按存库值（枚举 name，大小写不敏感）取枚举。
         *
         * **未知值返回 null**——不像 [StopReason.fromKey] 兜底成 OTHER：用药记录里
         * 宁可直接显示原始字符串，也不要静默把它伪装成「其他」。
         */
        fun fromKey(k: String?): SkipReason? = entries.firstOrNull { it.name.equals(k, true) }
    }
}

/** 注射 / 用药后不良反应程度。v1.2.5（i18n）：文案改为字符串资源 `labelRes`。 */
enum class Reaction(@StringRes val labelRes: Int) {
    NONE(R.string.enum_reaction_none),
    MILD(R.string.enum_reaction_mild),
    MODERATE(R.string.enum_reaction_moderate),
    SEVERE(R.string.enum_reaction_severe),
}

/**
 * v1.0.49：注射部位。
 *
 * `key` 即 `medication_logs.inj_site` 的**存库值**（勿改——改了历史记录就与中文标签对不上），
 * `label` 为展示用中文。
 *
 * v1.2.5（i18n）：`label` → `labelRes`（字符串资源）。`key` **仍是英文存库值、保持不动**：
 * 它是数据不是文案，展示层按 `labelRes` 出中文/英文，落库值永远是 `thigh_l` 这类键。
 *
 * 此前只有「今日打卡」的选择器（`TodayScreen` 里的私有 `injSites()`）知道这层映射，
 * 药单的「用药记录」直接打印存库值，于是记录里显示的是 `thigh_l` 这种英文键。
 * 收拢到这里，选择侧与展示侧共用一份映射。
 */
enum class InjSite(val key: String, @StringRes val labelRes: Int) {
    LEFT_THIGH("thigh_l", R.string.enum_inj_site_left_thigh),
    RIGHT_THIGH("thigh_r", R.string.enum_inj_site_right_thigh),
    LEFT_ABDOMEN("abdomen_l", R.string.enum_inj_site_left_abdomen),
    RIGHT_ABDOMEN("abdomen_r", R.string.enum_inj_site_right_abdomen),
    LEFT_ARM("arm_l", R.string.enum_inj_site_left_arm),
    RIGHT_ARM("arm_r", R.string.enum_inj_site_right_arm);

    companion object {
        /**
         * 按存库值取枚举，**未知值返回 null**——不兜底成某个部位：
         * 猜错等于替用户改了注射部位，宁可显示原始字符串。
         */
        fun fromKey(k: String?): InjSite? = entries.firstOrNull { it.key == k }
    }
}

@Entity(tableName = "profile")
data class Profile(
    @PrimaryKey val id: Int = 1,
    @ColumnInfo(name = "display_name") val displayName: String,
    @ColumnInfo(name = "diagnosis") val diagnosis: String,
    @ColumnInfo(name = "diagnose_year") val diagnoseYear: Int? = null,
    @ColumnInfo(name = "hla_b27") val hlaB27: String = "unknown", // positive / negative / unknown
    /** R1 三态：stable（缓解期）/ controlled（控制中）/ flare（发作期）——驱动 M4 运动过滤与 M5 预警灵敏度；unknown 由引擎按 flare 保守处理 */
    @ColumnInfo(name = "disease_stage") val diseaseStage: String = "unknown", // stable / controlled / flare / unknown
    @ColumnInfo(name = "spine_mobility") val spineMobility: String? = null, // none / mild / moderate / severe（颈椎受累=moderate+）
    /**
     * v1.0.67 C1：骶髂关节影像分期（改良纽约标准 mNY，0–IV）——规划 M0「诊断信息全量」缺此项。
     * 存 "0".."4"；null/"unknown" = 未评估。Room v16→v17 迁移新增列。
     */
    @ColumnInfo(name = "sacroiliitis_grade") val sacroiliitisGrade: String? = null,
    @ColumnInfo(name = "comorbidities") val comorbidities: String? = null, // JSON 数组
    @ColumnInfo(name = "allergies") val allergies: String? = null, // JSON 数组
    @ColumnInfo(name = "lifestyle") val lifestyle: String? = null, // JSON
    @ColumnInfo(name = "emergency_blood_type") val emergencyBloodType: String? = null,
    @ColumnInfo(name = "emergency_med_summary") val emergencyMedSummary: String? = null,
    @ColumnInfo(name = "emergency_note") val emergencyNote: String? = null,
    // v10：体重目标区间（kg）——由医生给定或自我管理目标，用于体重卡「在区间内 / 偏高 / 偏低」提示。
    // 可空以兼容旧备份（旧备份无此列，恢复后为 NULL = 未设目标）。
    @ColumnInfo(name = "weight_target_low") val weightTargetLow: Double? = null,
    @ColumnInfo(name = "weight_target_high") val weightTargetHigh: Double? = null,
    /** 极简模式（红线三状态机 e2：发作期输入减负） */
    @ColumnInfo(name = "ui_mode") val uiMode: String = "normal", // normal / minimal
    /**
     * v1.0.65 B12：进入极简模式的时刻（null = 非极简）。
     * 与 [uiMode] 成对维护——`uiMode == minimal` 时必须有值，退出时清空。
     * Room v15→v16 迁移新增列。
     */
    @ColumnInfo(name = "minimal_since") val minimalSince: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
)

@Entity(
    tableName = "medications",
    indices = [Index("name_key"), Index("is_archived")]
)
data class Medication(
    @PrimaryKey val id: String, // med-xxxx
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "brand_name") val brandName: String? = null,
    @ColumnInfo(name = "name_key") val nameKey: String, // 小写通用名/类别键——R03 相互作用查询键
    @ColumnInfo(name = "med_class") val medClass: String, // MedClass.name
    @ColumnInfo(name = "risk_tags") val riskTags: String? = null, // JSON 数组
    @ColumnInfo(name = "route") val route: String, // oral / injection
    @ColumnInfo(name = "dose") val dose: String,
    @ColumnInfo(name = "frequency") val frequency: String, // MedFrequency.name
    /**
     * C6（v1.0.37）：服药三态显式标记（DoseState.name）。null = 未设置，按 frequency 推断。
     * 可空以兼容旧备份（旧备份无此列，恢复后为 NULL）。
     */
    @ColumnInfo(name = "dose_state") val doseState: String? = null,
    /** C6：减量方案备注（如「泼尼松 10mg→5mg，每周减 1mg，医生 9/15 医嘱」） */
    @ColumnInfo(name = "taper_note") val taperNote: String? = null,
    @ColumnInfo(name = "prn_reason") val prnReason: String? = null,
    /** 实现层增补 D-1：口服各槽位时刻 JSON ["08:00","20:00"]；frequency=prn 时为空 */
    @ColumnInfo(name = "take_times") val takeTimes: String? = null,
    /** 实现层增补 D-1：WEEKLY 时的星期（1=周一 … 7=周日） */
    @ColumnInfo(name = "weekly_weekday") val weeklyWeekday: Int? = null,
    /** P5 修订 R7：BIW（每周两次）第二针星期（1=周一 … 7=周日），如恩利 周一/周四 */
    @ColumnInfo(name = "weekly_weekday2") val weeklyWeekday2: Int? = null,
    @ColumnInfo(name = "duration") val duration: String? = null,
    @ColumnInfo(name = "start_date") val startDate: String, // YYYY-MM-DD，注射周期锚点
    @ColumnInfo(name = "end_date") val endDate: String? = null,
    @ColumnInfo(name = "inj_cycle_days") val injCycleDays: Int? = null,
    @ColumnInfo(name = "inj_sites") val injSites: String? = null, // JSON 数组
    @ColumnInfo(name = "inj_last_site") val injLastSite: String? = null,
    @ColumnInfo(name = "storage") val storage: String? = null,
    @ColumnInfo(name = "take_with_food") val takeWithFood: String? = null, // with_food / empty_stomach / any
    @ColumnInfo(name = "check_doctor_told") val checkDoctorTold: Boolean = false, // R03 待办
    @ColumnInfo(name = "check_leaflet_read") val checkLeafletRead: Boolean = false, // R03 待办
    @ColumnInfo(name = "interaction_check_date") val interactionCheckDate: String? = null,
    @ColumnInfo(name = "is_archived") val isArchived: Boolean = false,
    @ColumnInfo(name = "notes") val notes: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
)

@Entity(
    tableName = "medication_logs",
    indices = [Index("date"), Index("med_id"), Index(value = ["date", "med_id", "slot_key"], unique = true)]
)
data class MedicationLog(
    @PrimaryKey val id: String, // mlog-xxxx
    @ColumnInfo(name = "date") val date: String, // YYYY-MM-DD 归属日
    @ColumnInfo(name = "recorded_at") val recordedAt: String, // ISO8601 实际录入时刻
    @ColumnInfo(name = "backfill") val backfill: Boolean = false,
    @ColumnInfo(name = "med_id") val medId: String? = null, // 弱引用，可空自持
    @ColumnInfo(name = "med_key") val medKey: String, // 快照组
    @ColumnInfo(name = "med_name") val medName: String,
    @ColumnInfo(name = "dose_snapshot") val doseSnapshot: String,
    @ColumnInfo(name = "scheduled_time") val scheduledTime: String? = null, // 当日计划时刻快照 HH:mm
    /** 实现层增补 D-2：槽位键（PRN 无），幂等键成分 */
    @ColumnInfo(name = "slot_key") val slotKey: String? = null,
    @ColumnInfo(name = "status") val status: String, // done / partial / skipped
    @ColumnInfo(name = "reason") val reason: String? = null, // skipped / partial 必填
    @ColumnInfo(name = "taken_at") val takenAt: String? = null, // done 时必填，late 判定依据
    @ColumnInfo(name = "inj_site") val injSite: String? = null, // 注射类必填
    @ColumnInfo(name = "batch_no") val batchNo: String? = null,
    @ColumnInfo(name = "reaction") val reaction: String = Reaction.NONE.name,
    @ColumnInfo(name = "prn_flag") val prnFlag: Boolean = false,
    @ColumnInfo(name = "notes") val notes: String? = null,
)

@Entity(tableName = "kb_entries", indices = [Index("category"), Index("review_due")])
data class KbEntry(
    @PrimaryKey val id: String, // itx-001 等
    @ColumnInfo(name = "category") val category: String, // interaction / food_drug / exercise / emergency / edu
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "summary") val summary: String,
    @ColumnInfo(name = "severity_level") val severityLevel: String, // high / medium / low
    @ColumnInfo(name = "applicable_scene") val applicableScene: String,
    @ColumnInfo(name = "source_name") val sourceName: String,
    @ColumnInfo(name = "source_url") val sourceUrl: String,
    @ColumnInfo(name = "source_tier") val sourceTier: String, // S1–S4
    @ColumnInfo(name = "adapted_at") val adaptedAt: String,
    @ColumnInfo(name = "review_due") val reviewDue: String,
    @ColumnInfo(name = "version") val version: Int,
    @ColumnInfo(name = "payload") val payload: String, // JSON，按 category 五种 schema
    // v9：检索专用拼接列（title + summary + payload，口径见 domain/KbSearch）。
    // 可空——旧版本备份不含此列，恢复时按名列表 INSERT 会留空，由恢复后回填补齐。
    @ColumnInfo(name = "search_text") val searchText: String? = null,
    // v10：个人备注层（v3 规划「只读种子层 + 个人备注层」双层结构的第二层）。
    // 用户对该条目的私人记录（如「我吃了会胃痛」），与种子内容分离、不随种子更新被覆盖。
    // 可空——NULL = 未写备注。
    @ColumnInfo(name = "user_note") val userNote: String? = null,
)

/**
 * v10：M6 复诊附件归档（checkup_attachments）——化验单 / 影像报告的拍照或 PDF 存档。
 *
 * ⚠️ **附件文件不随数据库备份**：文件落在 `filesDir/checkup_attachments/`，本表只记元数据。
 * 备份导出的是数据库各表 JSON（见 BackupEngine），不含二进制文件；换机恢复后附件需重新导入。
 * 这是为保持备份轻量（WebDAV 上传 120s 超时）而做的取舍。
 */
@Entity(tableName = "checkup_attachments", indices = [Index("checkup_id"), Index("created_at"), Index("remote_path")])
data class CheckupAttachment(
    @PrimaryKey val id: String, // catt-xxxx
    /** 归属复诊记录（弱引用，可空自持——记录删除后附件仍可追溯） */
    @ColumnInfo(name = "checkup_id") val checkupId: String? = null,
    /** PHOTO（拍照 / 相册图片）/ PDF */
    @ColumnInfo(name = "kind") val kind: String,
    /** 应用内部存储的相对文件名（不含目录），绝对路径由 AttachmentRepository 拼接 */
    @ColumnInfo(name = "file_name") val fileName: String,
    @ColumnInfo(name = "mime") val mime: String? = null,
    @ColumnInfo(name = "size_bytes") val sizeBytes: Long = 0,
    /** 原始文件名（PDF 来自选择器时保留，便于用户辨认） */
    @ColumnInfo(name = "display_name") val displayName: String? = null,
    @ColumnInfo(name = "note") val note: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: String,
    // ---- v12（v1.0.35）：WebDAV 附件同步 ----
    /** 远端相对路径（`YYYY-MM-DD/<附件id>.enc`，口径见 domain/AttachmentPath）；null = 未上传。 */
    @ColumnInfo(name = "remote_path") val remotePath: String? = null,
    /** 上传密文的 SHA-256，用于完整性校验与重传判断 */
    @ColumnInfo(name = "remote_sha256") val remoteSha256: String? = null,
    @ColumnInfo(name = "synced_at") val syncedAt: String? = null,
    /**
     * 软删除墓碑：非空 = 本地已删、待远端同步清理。
     * 直接物理删会留下「远端有、本地无」的孤儿且无从追溯——网络失败时靠它重试。
     * 远端 DELETE 成功后由同步流程物理删行。
     */
    @ColumnInfo(name = "deleted_at") val deletedAt: String? = null,
)

/**
 * R17 停药原因分类分级（D-2 §7：自行停药触发警示）。
 *
 * v1.2.5（i18n）：`label` → `labelRes`、`warning` → `noteRes`（可空的咨询性提示文案）。
 * `noteRes` **保持可空**：「这条原因有没有提示」是业务事实（判读逻辑见 domain/StopWarning），
 * 不能为统一类型塞一个占位资源进去。
 */
enum class StopReason(@StringRes val labelRes: Int, @StringRes val noteRes: Int?) {
    DOCTOR_SCHEDULED(R.string.enum_stop_reason_doctor_scheduled, null),
    DOCTOR_ADJUST(R.string.enum_stop_reason_doctor_adjust, null),
    SELF_STOPPED(R.string.enum_stop_reason_self_stopped, R.string.enum_stop_reason_self_stopped_note),
    SIDE_EFFECT(R.string.enum_stop_reason_side_effect, R.string.enum_stop_reason_side_effect_note),
    EXAM_RESULT(R.string.enum_stop_reason_exam_result, null),
    // C5（v1.0.37）：对齐规划口径的停药原因（感染发热 / 准备手术 / 经济原因）
    INFECTION(R.string.enum_stop_reason_infection, R.string.enum_stop_reason_infection_note),
    SURGERY(R.string.enum_stop_reason_surgery, R.string.enum_stop_reason_surgery_note),
    FINANCIAL(R.string.enum_stop_reason_financial, R.string.enum_stop_reason_financial_note),
    OTHER(R.string.enum_stop_reason_other, null);

    companion object {
        fun fromKey(k: String?) = entries.firstOrNull { it.name.equals(k, true) } ?: OTHER
    }
}

/**
 * C6（v1.0.37）：服药三态——固定 / 按需 / 减量中。
 *
 * 「减量中」= 医生批准的减量方案进行中：此态下**不触发停药警示**（见 domain/StopWarning），
 * 避免把医嘱减量误报成自行停药风险。
 */
enum class DoseState(@StringRes val labelRes: Int) {
    FIXED(R.string.enum_dose_state_fixed), PRN(R.string.enum_dose_state_prn), TAPERING(R.string.enum_dose_state_tapering);

    companion object {
        fun fromKey(k: String?): DoseState? = entries.firstOrNull { it.name.equals(k, true) }

        /** 未显式设置时按 frequency 推断：PRN → 按需，其余 → 固定。 */
        fun of(med: Medication): DoseState =
            fromKey(med.doseState) ?: if (MedFrequency.fromKey(med.frequency) == MedFrequency.PRN) PRN else FIXED
    }
}

@Entity(tableName = "medication_changes", indices = [Index("med_id"), Index("effective_date")])
data class MedicationChange(
    @PrimaryKey val id: String, // mchg-xxxx
    @ColumnInfo(name = "recorded_at") val recordedAt: String,
    @ColumnInfo(name = "backfill") val backfill: Boolean = false,
    @ColumnInfo(name = "med_id") val medId: String? = null, // 弱引用，可空自持
    @ColumnInfo(name = "med_key") val medKey: String,
    @ColumnInfo(name = "change_type") val changeType: String, // start / dose_adjust / schedule_change / stop / pause / resume
    @ColumnInfo(name = "old_snapshot") val oldSnapshot: String, // 裁决 A 快照 JSON
    @ColumnInfo(name = "new_snapshot") val newSnapshot: String,
    @ColumnInfo(name = "effective_date") val effectiveDate: String,
    @ColumnInfo(name = "reason") val reason: String, // StopReason.name 小写
    @ColumnInfo(name = "reason_note") val reasonNote: String? = null, // other 时必填
    @ColumnInfo(name = "source") val source: String, // hospital / clinic / self
    @ColumnInfo(name = "notes") val notes: String? = null,
)

// ---------------------------------------------------------------------------
// P2 实体：M5 症状 / BASDAI / 发作，M4 运动，系统 alerts
// ---------------------------------------------------------------------------

/**
 * M5 每日症状（symptom_daily）。数值一律不预填；「未记录=无行/字段null」与「实际为0」严格区分。
 * 红旗布尔组驱动应急卡：feverish→emr-002，eye→emr-001，neuro→emr-004。
 */
@Entity(tableName = "symptom_daily", indices = [Index(value = ["date"], unique = true)])
data class SymptomDaily(
    @PrimaryKey val id: String, // sym-xxxx
    @ColumnInfo(name = "date") val date: String, // YYYY-MM-DD
    @ColumnInfo(name = "recorded_at") val recordedAt: String,
    @ColumnInfo(name = "backfill") val backfill: Boolean = false,
    @ColumnInfo(name = "morning_stiffness_min") val morningStiffnessMin: Int? = null, // 晨僵分钟
    @ColumnInfo(name = "night_pain") val nightPain: Int? = null, // 0–10
    @ColumnInfo(name = "pain_score") val painScore: Int? = null, // 0–10 整体疼痛
    @ColumnInfo(name = "feverish") val feverish: Boolean = false,
    @ColumnInfo(name = "fever_temp") val feverTemp: Double? = null, // ℃，配合 edu-th-001 阈值
    @ColumnInfo(name = "eye_symptom") val eyeSymptom: Boolean = false, // emr-001 触发
    @ColumnInfo(name = "neuro_red_flag") val neuroRedFlag: Boolean = false, // emr-004 触发
    @ColumnInfo(name = "mood") val mood: Int? = null, // 0–10 可选
    @ColumnInfo(name = "sleep") val sleep: Int? = null, // 0–10 可选
    @ColumnInfo(name = "fatigue") val fatigue: Int? = null, // 0–10 可选
    @ColumnInfo(name = "notes") val notes: String? = null,
)

/** M5 BASDAI 周期自评（basdai_records）。总分公式：(Q1+Q2+Q3+Q4+(Q5+Q6)/2)/5，0–10。 */
@Entity(tableName = "basdai_records", indices = [Index("date")])
data class BasdaiRecord(
    @PrimaryKey val id: String, // bas-xxxx
    @ColumnInfo(name = "date") val date: String,
    @ColumnInfo(name = "recorded_at") val recordedAt: String,
    @ColumnInfo(name = "backfill") val backfill: Boolean = false,
    @ColumnInfo(name = "q1_fatigue") val q1Fatigue: Int, // 疲乏程度 0–10
    @ColumnInfo(name = "q2_spine_pain") val q2SpinePain: Int, // 脊柱痛 0–10
    @ColumnInfo(name = "q3_peripheral_pain") val q3PeripheralPain: Int, // 外周关节痛 0–10
    @ColumnInfo(name = "q4_tender_points") val q4TenderPoints: Int, // 触痛部位程度 0–10
    @ColumnInfo(name = "q5_stiffness_degree") val q5StiffnessDegree: Int, // 晨僵程度 0–10
    @ColumnInfo(name = "q6_stiffness_duration") val q6StiffnessDuration: Int, // 晨僵时长 0–10
    @ColumnInfo(name = "total") val total: Double, // 0–10
    @ColumnInfo(name = "notes") val notes: String? = null,
) {
    companion object {
        /** (Q1+Q2+Q3+Q4+(Q5+Q6)/2)/5 —— 标准 BASDAI 公式 */
        fun total(q1: Int, q2: Int, q3: Int, q4: Int, q5: Int, q6: Int): Double =
            (q1 + q2 + q3 + q4 + (q5 + q6) / 2.0) / 5.0
    }
}

/**
 * M5 发作登记（R18 flare_events）：开始 / 诱因 / 处理 / 缓解，联动极简模式。
 * v1.2.5（i18n）：`label` → `labelRes`。
 */
enum class FlareTrigger(@StringRes val labelRes: Int) {
    INFECTION(R.string.enum_flare_trigger_infection), COLD(R.string.enum_flare_trigger_cold),
    OVERWORK(R.string.enum_flare_trigger_overwork), STRESS(R.string.enum_flare_trigger_stress),
    WEATHER(R.string.enum_flare_trigger_weather), DIET(R.string.enum_flare_trigger_diet),
    UNKNOWN(R.string.enum_flare_trigger_unknown);

    companion object { fun fromKey(k: String?) = entries.firstOrNull { it.name.equals(k, true) } ?: UNKNOWN }
}

/** 发作期自行处理措施。v1.2.5（i18n）：`label` → `labelRes`。 */
enum class FlareAction(@StringRes val labelRes: Int) {
    REST(R.string.enum_flare_action_rest), HEAT(R.string.enum_flare_action_heat),
    GENTLE_MOVE(R.string.enum_flare_action_gentle_move), EXTRA_MED(R.string.enum_flare_action_extra_med),
    DOCTOR(R.string.enum_flare_action_doctor), NONE(R.string.enum_flare_action_none);

    companion object { fun fromKey(k: String?) = entries.firstOrNull { it.name.equals(k, true) } ?: NONE }
}

@Entity(tableName = "flare_events", indices = [Index("start_date"), Index("status")])
data class FlareEvent(
    @PrimaryKey val id: String, // flr-xxxx
    @ColumnInfo(name = "recorded_at") val recordedAt: String,
    @ColumnInfo(name = "start_date") val startDate: String,
    @ColumnInfo(name = "end_date") val endDate: String? = null, // null = 进行中
    @ColumnInfo(name = "status") val status: String = "active", // active / resolved
    @ColumnInfo(name = "trigger") val trigger: String = FlareTrigger.UNKNOWN.name,
    @ColumnInfo(name = "actions_taken") val actionsTaken: String? = null, // JSON 数组 FlareAction.name
    @ColumnInfo(name = "severity_peak") val severityPeak: Int? = null, // 发作峰值疼痛 0–10
    @ColumnInfo(name = "notes") val notes: String? = null,
)

/** M4 运动打卡（exercise_logs）。exc_id 弱引用 kb_entries(exercise)；R21 次日反馈字段组。v1.2.5（i18n）：`label` → `labelRes`。 */
enum class FeedbackChange(@StringRes val labelRes: Int) {
    BETTER(R.string.enum_feedback_change_better),
    SAME(R.string.enum_feedback_change_same),
    WORSE(R.string.enum_feedback_change_worse),
}

@Entity(tableName = "exercise_logs", indices = [Index("date"), Index("exc_id")])
data class ExerciseLog(
    @PrimaryKey val id: String, // elog-xxxx
    @ColumnInfo(name = "date") val date: String,
    @ColumnInfo(name = "recorded_at") val recordedAt: String,
    @ColumnInfo(name = "backfill") val backfill: Boolean = false,
    @ColumnInfo(name = "exc_id") val excId: String? = null, // 弱引用 kb_entries，可空自持
    @ColumnInfo(name = "exc_key") val excKey: String, // exc-001 / custom
    @ColumnInfo(name = "exc_name") val excName: String, // 快照
    @ColumnInfo(name = "grade") val grade: String? = null, // L1 / L2 / L3 快照
    @ColumnInfo(name = "duration_min") val durationMin: Int? = null,
    @ColumnInfo(name = "intensity") val intensity: String? = null, // low / moderate / high
    @ColumnInfo(name = "status") val status: String = "done", // done / partial / skipped
    @ColumnInfo(name = "reason") val reason: String? = null, // skipped / partial 必填
    /** R21 次日反馈：运动后疼痛 / 晨僵变化（exc-010 判读：worse → 建议减量） */
    @ColumnInfo(name = "fb_pain_change") val fbPainChange: String? = null, // better / same / worse
    @ColumnInfo(name = "fb_stiffness_change") val fbStiffnessChange: String? = null,
    @ColumnInfo(name = "fb_is_muscle_soreness") val fbIsMuscleSoreness: Boolean? = null, // 区分运动酸痛 vs 炎症加重
    @ColumnInfo(name = "fb_note") val fbNote: String? = null,
    @ColumnInfo(name = "notes") val notes: String? = null,
)

/** 系统警报（alerts）：ack_method 仅本机（F2 裁剪）。 */
@Entity(tableName = "alerts", indices = [Index("alert_type"), Index("created_at")])
data class Alert(
    @PrimaryKey val id: String, // alt-xxxx
    @ColumnInfo(name = "alert_type") val alertType: String, // symptom_abnormal / basdai_high / flare_day7 / review_due / neuro_red_flag
    @ColumnInfo(name = "severity") val severity: String, // high / medium / low
    @ColumnInfo(name = "message") val message: String,
    @ColumnInfo(name = "kb_ref") val kbRef: String? = null, // 关联 kb_entries.id（应急卡 / 阈值条目）
    @ColumnInfo(name = "ref_date") val refDate: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "ack_at") val ackAt: String? = null, // 本机确认时刻；null = 未读
)

// ---------------------------------------------------------------------------
// P3 实体：M2/M3 营养与体征、M6 复诊、M7 紧急卡
// ---------------------------------------------------------------------------

// ===== M2/M3 骨健康抗炎与营养 =====

/**
 * M2 补剂档案（supplements）——与 medications 同构但独立域，避免混淆。
 *
 * v1.2.5（i18n）：展示文案改为 `labelRes`。**`matchToken` 是另一回事、必须留中文**：
 * 它参与 `MedicationRepository.medicationTokens()` 的相互作用匹配——拿补剂类目名去和
 * 知识库里写死的中文 token（「钙」「维生素 D」…）比对（见 domain/DrugInteractionKeys）。
 * 匹配键不能随界面语言变，否则英文界面下相互作用会静默漏检。
 */
enum class SupplementCategory(@StringRes val labelRes: Int, val matchToken: String) {
    CALCIUM(R.string.enum_supplement_category_calcium, "钙"),
    VITAMIN_D(R.string.enum_supplement_category_vitamin_d, "维生素 D"),
    OMEGA3(R.string.enum_supplement_category_omega3, "Omega-3 / 鱼油"),
    PROBIOTIC(R.string.enum_supplement_category_probiotic, "益生菌"),
    CURCUMIN(R.string.enum_supplement_category_curcumin, "姜黄素"),
    COLLAGEN(R.string.enum_supplement_category_collagen, "胶原蛋白"),
    VITAMIN_B(R.string.enum_supplement_category_vitamin_b, "B 族"),
    OTHER(R.string.enum_supplement_category_other, "其他");
    companion object { fun fromKey(k: String?) = entries.firstOrNull { it.name.equals(k, true) } ?: OTHER }
}

@Entity(tableName = "supplements", indices = [Index("is_archived")])
data class Supplement(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "brand") val brand: String? = null,
    @ColumnInfo(name = "category") val category: String,
    @ColumnInfo(name = "dose") val dose: String,
    /**
     * B11（v1.0.38）：单次剂量数值 + 单位（如 500 / "mg"）。
     * 用于「每日上限警示」的算术比较；`dose` 仍是给人看的自由文本。可空 = 未量化。
     */
    @ColumnInfo(name = "dose_amount") val doseAmount: Double? = null,
    @ColumnInfo(name = "dose_unit") val doseUnit: String? = null,
    /**
     * B11：每日参考上限（由用户 / 医生 / 营养师填写）。
     * ⚠️ **刻意不内置任何医学上限数值**——App 只做「当日累计 vs 上限」的比较，不代替专业判断。
     */
    @ColumnInfo(name = "daily_max") val dailyMax: Double? = null,
    @ColumnInfo(name = "frequency") val frequency: String = "daily",
    @ColumnInfo(name = "times") val times: String? = null,
    @ColumnInfo(name = "take_with_food") val takeWithFood: String? = null,
    @ColumnInfo(name = "prescribed") val prescribed: Boolean = false,
    @ColumnInfo(name = "is_archived") val isArchived: Boolean = false,
    @ColumnInfo(name = "notes") val notes: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
)

/** M2 补剂打卡（supplement_logs）——打卡五表之一，快照自持。 */
@Entity(
    tableName = "supplement_logs",
    indices = [Index("date"), Index("sup_id"), Index(value = ["date", "sup_id", "slot_key"], unique = true)]
)
data class SupplementLog(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "date") val date: String,
    @ColumnInfo(name = "recorded_at") val recordedAt: String,
    @ColumnInfo(name = "backfill") val backfill: Boolean = false,
    @ColumnInfo(name = "sup_id") val supId: String? = null,
    @ColumnInfo(name = "sup_key") val supKey: String,
    @ColumnInfo(name = "sup_name") val supName: String,
    @ColumnInfo(name = "dose_snapshot") val doseSnapshot: String,
    @ColumnInfo(name = "scheduled_time") val scheduledTime: String? = null,
    @ColumnInfo(name = "slot_key") val slotKey: String? = null,
    @ColumnInfo(name = "status") val status: String,
    @ColumnInfo(name = "reason") val reason: String? = null,
    @ColumnInfo(name = "taken_at") val takenAt: String? = null,
    @ColumnInfo(name = "notes") val notes: String? = null,
)

/** M3 体征记录（vitals）——体温 / 血压 / 心率，联动感染发热与 itx-013 监测。 */
@Entity(tableName = "vitals", indices = [Index("date"), Index("recorded_at")])
data class Vitals(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "date") val date: String,
    @ColumnInfo(name = "recorded_at") val recordedAt: String,
    @ColumnInfo(name = "backfill") val backfill: Boolean = false,
    @ColumnInfo(name = "temperature") val temperature: Double? = null,
    @ColumnInfo(name = "bp_sys") val bpSys: Int? = null,
    @ColumnInfo(name = "bp_dia") val bpDia: Int? = null,
    @ColumnInfo(name = "heart_rate") val heartRate: Int? = null,
    @ColumnInfo(name = "notes") val notes: String? = null,
)

/** M3 体重记录（weight_logs）——打卡五表之一，体重趋势追踪。 */
@Entity(tableName = "weight_logs", indices = [Index("date")])
data class WeightLog(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "date") val date: String,
    @ColumnInfo(name = "recorded_at") val recordedAt: String,
    @ColumnInfo(name = "backfill") val backfill: Boolean = false,
    @ColumnInfo(name = "weight_kg") val weightKg: Double,
    @ColumnInfo(name = "notes") val notes: String? = null,
)

/** M3 身体指标（body_measures）——身高 / 腰围 / BMI 基线与定期复测。 */
@Entity(tableName = "body_measures", indices = [Index("date")])
data class BodyMeasure(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "date") val date: String,
    @ColumnInfo(name = "recorded_at") val recordedAt: String,
    @ColumnInfo(name = "backfill") val backfill: Boolean = false,
    @ColumnInfo(name = "height_cm") val heightCm: Double? = null,
    @ColumnInfo(name = "waist_cm") val waistCm: Double? = null,
    @ColumnInfo(name = "hip_cm") val hipCm: Double? = null,
    @ColumnInfo(name = "bmi") val bmi: Double? = null,
    @ColumnInfo(name = "notes") val notes: String? = null,
)

/** M3 饮食画像（diet_profile）——抗炎饮食基线，驱动食物药物建议展示。 */
@Entity(tableName = "diet_profile")
data class DietProfile(
    @PrimaryKey val id: Int = 1,
    @ColumnInfo(name = "diet_pattern") val dietPattern: String = "mixed",
    @ColumnInfo(name = "seafood_freq") val seafoodFreq: String? = null,
    @ColumnInfo(name = "dairy_tolerant") val dairyTolerant: String? = null,
    @ColumnInfo(name = "alcohol_freq") val alcoholFreq: String? = null,
    @ColumnInfo(name = "caffeine_freq") val caffeineFreq: String? = null,
    @ColumnInfo(name = "notes") val notes: String? = null,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
)

/** M3 忌口清单（food_avoid_items）——个人过敏 / 不耐受 / 医生建议，与知识库联动。 */
@Entity(tableName = "food_avoid_items", indices = [Index("category")])
data class FoodAvoidItem(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "category") val category: String,
    @ColumnInfo(name = "severity") val severity: String = "medium",
    @ColumnInfo(name = "kb_ref") val kbRef: String? = null,
    @ColumnInfo(name = "symptoms") val symptoms: String? = null,
    @ColumnInfo(name = "notes") val notes: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
)

// ===== M6 复诊管理 =====

/** M6 复诊项目（checkup_items）——周期自动排程依据。v1.2.5（i18n）：`label` → `labelRes`。 */
enum class CheckupType(@StringRes val labelRes: Int) {
    LAB(R.string.enum_checkup_type_lab), IMAGE(R.string.enum_checkup_type_image), EYE(R.string.enum_checkup_type_eye),
    DENTAL(R.string.enum_checkup_type_dental), VACCINE(R.string.enum_checkup_type_vaccine),
    PHYSICAL(R.string.enum_checkup_type_physical), CONSULT(R.string.enum_checkup_type_consult),
    OTHER(R.string.enum_checkup_type_other);
    companion object { fun fromKey(k: String?) = entries.firstOrNull { it.name.equals(k, true) } ?: OTHER }
}

@Entity(tableName = "checkup_items", indices = [Index("check_type"), Index("is_active")])
data class CheckupItem(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "check_type") val checkType: String,
    @ColumnInfo(name = "cycle_days") val cycleDays: Int? = null,
    @ColumnInfo(name = "linked_med_id") val linkedMedId: String? = null,
    @ColumnInfo(name = "kb_ref") val kbRef: String? = null,
    @ColumnInfo(name = "is_active") val isActive: Boolean = true,
    @ColumnInfo(name = "notes") val notes: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
)

/** M6 复诊记录（checkup_records）——打卡五表之一。 */
@Entity(tableName = "checkup_records", indices = [Index("date"), Index("item_id")])
data class CheckupRecord(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "date") val date: String,
    @ColumnInfo(name = "recorded_at") val recordedAt: String,
    @ColumnInfo(name = "backfill") val backfill: Boolean = false,
    @ColumnInfo(name = "item_id") val itemId: String? = null,
    @ColumnInfo(name = "item_name") val itemName: String,
    @ColumnInfo(name = "check_type") val checkType: String,
    @ColumnInfo(name = "status") val status: String = "done",
    @ColumnInfo(name = "hospital") val hospital: String? = null,
    @ColumnInfo(name = "doctor") val doctor: String? = null,
    @ColumnInfo(name = "next_date") val nextDate: String? = null,
    @ColumnInfo(name = "conclusion") val conclusion: String? = null,
    @ColumnInfo(name = "notes") val notes: String? = null,
)

/** M6 化验结果（lab_results）——单项目数值，支持单位与参考范围。 */
@Entity(tableName = "lab_results", indices = [Index("date"), Index("test_name"), Index("checkup_id")])
data class LabResult(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "date") val date: String,
    @ColumnInfo(name = "recorded_at") val recordedAt: String,
    @ColumnInfo(name = "backfill") val backfill: Boolean = false,
    @ColumnInfo(name = "checkup_id") val checkupId: String? = null,
    @ColumnInfo(name = "test_name") val testName: String,
    @ColumnInfo(name = "value") val value: Double? = null,
    @ColumnInfo(name = "value_text") val valueText: String? = null,
    @ColumnInfo(name = "unit") val unit: String? = null,
    @ColumnInfo(name = "ref_low") val refLow: Double? = null,
    @ColumnInfo(name = "ref_high") val refHigh: Double? = null,
    /**
     * v1.0.78（批次 4 收尾）：**本地参考范围判读结果**（high / low / normal）；
     * 没有参考范围、本地判不了时沿用 AI 导入的标记兜底（即「本地判读结果或兜底值」）。
     */
    @ColumnInfo(name = "abnormal") val abnormal: String? = null,
    /**
     * v1.0.78（批次 4 收尾）：**AI 导入时的原始异常标记**（high / low / normal），
     * 与本地判读结果 [abnormal] **并列展示**，本身**不参与任何判定**。
     *
     * 为什么必须单独一列：`abnormal` 自 v1.0.77 起由「本地参考范围判定优先」接管，而 AI 的原始标记
     * 原本写进**同一列**——本地判读一覆盖，「AI 说正常 / 本地判读偏高」这类分歧就永久丢失，
     * 而复诊时恰恰要把这种分歧摆给医生看（第三份审查报告 S-12：AI 幻觉不得遮盖真实异常值）。
     * 可空 = 非 AI 导入（手工录入）或 AI 未给标记，属合法业务态。
     */
    @ColumnInfo(name = "ai_abnormal") val aiAbnormal: String? = null,
    @ColumnInfo(name = "notes") val notes: String? = null,
)

/** M6 影像记录（imaging_records）——MRI/CT/X线检查报告，支持 AI 导入（v1.0.4）。 */
@Entity(tableName = "imaging_records", indices = [Index("exam_date"), Index("checkup_id")])
data class ImagingRecord(
    @PrimaryKey val id: String, // img-xxxx
    @ColumnInfo(name = "exam_date") val examDate: String,
    @ColumnInfo(name = "recorded_at") val recordedAt: String,
    @ColumnInfo(name = "backfill") val backfill: Boolean = false,
    @ColumnInfo(name = "modality") val modality: String, // MRI / CT / XRAY
    @ColumnInfo(name = "body_part") val bodyPart: String,
    @ColumnInfo(name = "hospital") val hospital: String? = null,
    @ColumnInfo(name = "findings") val findings: String? = null,
    @ColumnInfo(name = "conclusion") val conclusion: String? = null,
    @ColumnInfo(name = "notes") val notes: String? = null,
    // v11：归属复诊记录（与 lab_results.checkup_id 对称）。可空 = 尚未归属。
    // 由用户在影像列表里手动选择归属（不自动按日期猜），选定后「记录」Tab 的对应复诊可看到该影像。
    @ColumnInfo(name = "checkup_id") val checkupId: String? = null,
) {
    companion object {
        /**
         * v1.2.5（i18n）：影像模态展示名。MRI / CT 的展示名就是代码本身、不需要资源，
         * 只有 XRAY 要查资源（中文「X 线」/ 英文 "X-ray"），故改成 @Composable +
         * `stringResource`——全部调用点都在 Compose 内。
         */
        @Composable
        fun modalityLabel(m: String) = when (m) {
            "MRI" -> "MRI"
            "CT" -> "CT"
            "XRAY" -> stringResource(R.string.imaging_modality_xray)
            else -> m
        }
    }
}

/** M6 疫苗记录（vaccine_records）——活疫苗需医生确认。v1.2.5（i18n）：`label` → `labelRes`。 */
enum class VaccineType(@StringRes val labelRes: Int) {
    LIVE(R.string.enum_vaccine_type_live), INACTIVATED(R.string.enum_vaccine_type_inactivated),
    UNKNOWN(R.string.enum_vaccine_type_unknown);
    companion object { fun fromKey(k: String?) = entries.firstOrNull { it.name.equals(k, true) } ?: UNKNOWN }
}

/** 疫苗接种的医生确认状态。v1.2.5（i18n）：`label` → `labelRes`。 */
enum class DoctorConfirm(@StringRes val labelRes: Int) {
    PENDING(R.string.enum_doctor_confirm_pending), CONFIRMED(R.string.enum_doctor_confirm_confirmed),
    DECLINED(R.string.enum_doctor_confirm_declined);
    companion object { fun fromKey(k: String?) = entries.firstOrNull { it.name.equals(k, true) } ?: PENDING }
}

@Entity(tableName = "vaccine_records", indices = [Index("date"), Index("vaccine_type")])
data class VaccineRecord(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "date") val date: String,
    @ColumnInfo(name = "recorded_at") val recordedAt: String,
    @ColumnInfo(name = "backfill") val backfill: Boolean = false,
    @ColumnInfo(name = "vaccine_name") val vaccineName: String,
    @ColumnInfo(name = "vaccine_type") val vaccineType: String,
    @ColumnInfo(name = "dose") val dose: String? = null,
    @ColumnInfo(name = "hospital") val hospital: String? = null,
    // v1.0.73：默认值必须与 DoctorConfirm.PENDING.name 一致——原先写死小写 "pending"，
    // 而 HealthRepository 的活疫苗安全警报按 == "PENDING" 比较，导致「走默认值的路径」
    // 静默跳过 high 级安全警报（安全默认值反转）。直接引用枚举名，杜绝再次漂移。
    @ColumnInfo(name = "doctor_confirm") val doctorConfirm: String = DoctorConfirm.PENDING.name,
    @ColumnInfo(name = "reaction") val reaction: String? = null,
    @ColumnInfo(name = "next_due_date") val nextDueDate: String? = null,
    @ColumnInfo(name = "notes") val notes: String? = null,
)

// ===== M7 紧急卡 =====

/** M7 紧急事件（emergency_events）——五应急场景记录。v1.2.5（i18n）：`label` → `labelRes`；`kbId` 是知识库外键不是文案，保持不动。 */
enum class EmergencyScene(@StringRes val labelRes: Int, val kbId: String) {
    UVEITIS(R.string.enum_emergency_scene_uveitis, "emr-001"),
    INFECTION_FEVER(R.string.enum_emergency_scene_infection_fever, "emr-002"),
    FALL_FRACTURE(R.string.enum_emergency_scene_fall_fracture, "emr-003"),
    CAUDA_EQUINA(R.string.enum_emergency_scene_cauda_equina, "emr-004"),
    STEROID_ADRENAL(R.string.enum_emergency_scene_steroid_adrenal, "emr-005");
    companion object { fun fromKey(k: String?) = entries.firstOrNull { it.name.equals(k, true) } ?: INFECTION_FEVER }
}

@Entity(tableName = "emergency_events", indices = [Index("date"), Index("scene")])
data class EmergencyEvent(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "date") val date: String,
    @ColumnInfo(name = "recorded_at") val recordedAt: String,
    @ColumnInfo(name = "scene") val scene: String,
    @ColumnInfo(name = "severity") val severity: String = "high",
    @ColumnInfo(name = "onset_time") val onsetTime: String? = null,
    @ColumnInfo(name = "symptoms") val symptoms: String? = null,
    @ColumnInfo(name = "actions_taken") val actionsTaken: String? = null,
    @ColumnInfo(name = "hospital_visit") val hospitalVisit: Boolean = false,
    @ColumnInfo(name = "hospital_name") val hospitalName: String? = null,
    @ColumnInfo(name = "outcome") val outcome: String? = null,
    @ColumnInfo(name = "resolved_date") val resolvedDate: String? = null,
    @ColumnInfo(name = "notes") val notes: String? = null,
)

/** M7 紧急联系人（contacts）——紧急卡展示 + 一键拨打。 */
@Entity(tableName = "contacts", indices = [Index("is_emergency")])
data class EmergencyContact(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "relation") val relation: String? = null,
    @ColumnInfo(name = "phone") val phone: String,
    @ColumnInfo(name = "is_emergency") val isEmergency: Boolean = true,
    @ColumnInfo(name = "is_doctor") val isDoctor: Boolean = false,
    @ColumnInfo(name = "hospital") val hospital: String? = null,
    @ColumnInfo(name = "notes") val notes: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
)

// ===========================================================================
// P4：R20 备份台账（sync_state 裁剪语义：backup / restore / export）
// ===========================================================================

/** R20 备份台账条目类型。v1.2.5（i18n）：`label` → `labelRes`。 */
enum class LedgerType(@StringRes val labelRes: Int) {
    BACKUP(R.string.enum_ledger_type_backup), RESTORE(R.string.enum_ledger_type_restore),
    EXPORT(R.string.enum_ledger_type_export), DRILL(R.string.enum_ledger_type_drill),
    ATTACH(R.string.enum_ledger_type_attach);

    companion object { fun fromKey(k: String?) = entries.firstOrNull { it.name.equals(k, true) } ?: BACKUP }
}

/** R20 备份台账结果。v1.2.5（i18n）：`label` → `labelRes`。 */
enum class LedgerStatus(@StringRes val labelRes: Int) {
    SUCCESS(R.string.enum_ledger_status_success), FAILED(R.string.enum_ledger_status_failed);

    companion object { fun fromKey(k: String?) = entries.firstOrNull { it.name.equals(k, true) } ?: FAILED }
}

/** 备份台账：每次备份 / 恢复 / 演练 / 导出的登记记录（协议 §4/§6 台账要求）。 */
@Entity(tableName = "backup_ledger", indices = [Index("created_at")])
data class BackupLedger(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "ledger_type") val ledgerType: String,   // backup / restore / export / drill
    @ColumnInfo(name = "status") val status: String,            // success / failed
    @ColumnInfo(name = "target") val target: String,            // local / webdav / restore
    @ColumnInfo(name = "file_name") val fileName: String? = null,
    @ColumnInfo(name = "row_total") val rowTotal: Int? = null,
    @ColumnInfo(name = "verify_ok") val verifyOk: Boolean? = null, // checksum 双校验结论
    @ColumnInfo(name = "detail") val detail: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: String,
)

// ===========================================================================
// v1.0.39：B3 推荐食谱库 + B7 周期康复计划模板
// ===========================================================================

/**
 * B3（v1.0.39）：推荐食谱（recipes）。
 *
 * 双层结构同知识库：**种子层**（`is_seed=true`，随版本更新）+ **自建层**（用户自己添加）。
 * 标签用于筛选（抗炎 / 胃肠友好 / 控热量）；`sources` 只存编号（`R1`…），完整题录见
 * `domain/RecipeSources`——列表页不占版面，**点开某条食谱才在详情里展开出处**。
 * v1.1.2：编号前缀由 `S` 改为 `R`（与知识库的 `S1`–`S4` 证据层级区分开）；**老库里
 * 仍是 `S1` 这类旧值**（种子按 id 幂等，不会重种），由 `RecipeSources` 归一后再查。
 */
@Entity(tableName = "recipes", indices = [Index("is_favorite"), Index("is_seed")])
data class Recipe(
    @PrimaryKey val id: String, // rec-xxxx
    @ColumnInfo(name = "title") val title: String,
    /** 标签 JSON 数组：anti_inflammatory / gut_friendly / calorie_control */
    @ColumnInfo(name = "tags") val tags: String,
    /** 配料（每行一条） */
    @ColumnInfo(name = "ingredients") val ingredients: String,
    /** 做法（每行一步） */
    @ColumnInfo(name = "steps") val steps: String,
    /** 出处编号 JSON 数组：["R1","R4"]（完整题录见 domain/RecipeSources） */
    @ColumnInfo(name = "sources") val sources: String? = null,
    @ColumnInfo(name = "is_favorite") val isFavorite: Boolean = false,
    @ColumnInfo(name = "is_seed") val isSeed: Boolean = false,
    @ColumnInfo(name = "notes") val notes: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
)

/**
 * B7（v1.0.39）：4–12 周周期康复计划（exercise_plans）。
 *
 * `week_structure` 存每周结构 JSON（口径见 `domain/ExercisePlanTemplates`）：
 * `[{"week":1,"grade":"L1","days":3,"note":"…"}]`——知识库种子 `kb_seed_exc.json` 里引用的
 * `exercise_plans.week_structure` / `exercise_plans(stage_mode=flare)` 挂点由此补齐。
 * **完成度由 `exercise_logs` 反算**（不另存进度，避免双份真相）。
 */
@Entity(tableName = "exercise_plans", indices = [Index("is_active")])
data class ExercisePlan(
    @PrimaryKey val id: String, // eplan-xxxx
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "weeks") val weeks: Int,
    /** 适用分期：any / stable / flare */
    @ColumnInfo(name = "stage_mode") val stageMode: String = "any",
    @ColumnInfo(name = "week_structure") val weekStructure: String,
    @ColumnInfo(name = "is_active") val isActive: Boolean = false,
    @ColumnInfo(name = "is_seed") val isSeed: Boolean = false,
    /** 启用日期（周次从该日起算）；null = 未启用 */
    @ColumnInfo(name = "start_date") val startDate: String? = null,
    @ColumnInfo(name = "notes") val notes: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
)

// ===========================================================================
// v1.0.77（批次 3b）：计划槽位快照（planned_slots）
// ===========================================================================

/**
 * v1.0.77（批次 3b）：**计划槽位快照**（planned_slots）——把「某天该用哪几剂」落库留痕。
 *
 * 为什么必须落库、而不是每次从 `medications` 现算：
 *  · 「用药完成度（计划剂量口径）」的分母是**当时的计划剂量数**。药档随时会改（改时刻 / 改频次 /
 *    改剂量 / 停药），现算等于**用今天的方案评判过去一个月的用药**——同一段历史会随药档变动而变，
 *    报表数字跟着编辑操作漂移，用户无从解释；
 *  · 漏服补发要在**应用启动 / 开机广播**里判断「昨天该用几剂」。那是提醒层，不该为了历史计划
 *    反向依赖药品表单的当前状态。
 * 因此本表是**当时计划**的留痕：同一药事后改了时刻，已写入的行不随之改变（有意为之）。
 *
 * 幂等：`(date, med_id, slot_key)` 唯一索引 + `INSERT OR IGNORE`，物化例程可反复调用而不重复。
 * 按需（PRN）天然没有槽位（`ScheduleCalc.slotsFor` 返回空），本表不会有它的行。
 */
@Entity(
    tableName = "planned_slots",
    indices = [Index("date"), Index(value = ["date", "med_id", "slot_key"], unique = true)]
)
data class PlannedSlot(
    @PrimaryKey val id: String, // pslot-xxxx
    /** 槽位**所属日**（YYYY-MM-DD）——统计与补记都以它为准，不是「现在」那天 */
    @ColumnInfo(name = "date") val date: String,
    @ColumnInfo(name = "med_id") val medId: String,
    /** 药品名键快照（与 `medication_logs.med_key` 同口径） */
    @ColumnInfo(name = "med_key") val medKey: String,
    @ColumnInfo(name = "med_name") val medName: String,
    /** 槽位键：口服 = "HH:mm"，注射日 = "inj"（口径见 `domain/ScheduleCalc`）；PRN 无 */
    @ColumnInfo(name = "slot_key") val slotKey: String?,
    /** 计划时刻 "HH:mm"——「是否已到点」的判定依据（还没到点的剂量不算漏服） */
    @ColumnInfo(name = "slot_time") val slotTime: String,
    /** 计划当时的剂量快照（药档后续改剂量不影响已成行的历史） */
    @ColumnInfo(name = "dose_snapshot") val doseSnapshot: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: String,
)
