package com.ashkb.app.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * P1 实体集：profile / medications / medication_logs / kb_entries。
 * 列名与《数据字典 D-2 v1.0》字段六要素一致（snake_case）；
 * V3 裁剪已生效：medication_logs 无 operator / source_cmd_id。
 */

enum class MedClass(val label: String) {
    NSAID("NSAIDs 消炎镇痛"),
    CSDMARD("传统 DMARD"),
    BIOLOGIC("生物制剂"),
    JAK("JAK 抑制剂"),
    GLUCOCORTICOID("糖皮质激素"),
    OTHER("其他");

    companion object {
        fun fromKey(k: String?) = entries.firstOrNull { it.name.equals(k, true) } ?: OTHER
    }
}

enum class MedFrequency(val label: String) {
    DAILY("每日"),
    BID("每日两次"),
    Q8H("每 8 小时"),
    WEEKLY("每周一次（如甲氨蝶呤）"),
    Q2W("每两周一次"),
    PRN("按需服用"),
    CUSTOM("自定义周期");

    companion object {
        fun fromKey(k: String?) = entries.firstOrNull { it.name.equals(k, true) } ?: DAILY
    }
}

enum class CheckStatus(val label: String) { DONE("完成"), PARTIAL("部分完成"), SKIPPED("跳过") }

enum class SkipReason(val label: String) {
    TOO_BUSY("太忙"), UNWELL("身体不适"), HOSPITALIZED("住院"),
    SIDE_EFFECT("疑似副作用"), OTHER("其他")
}

enum class Reaction(val label: String) {
    NONE("无"), MILD("轻微"), MODERATE("中等"), SEVERE("严重")
}

@Entity(tableName = "profile")
data class Profile(
    @PrimaryKey val id: Int = 1,
    @ColumnInfo(name = "display_name") val displayName: String,
    @ColumnInfo(name = "diagnosis") val diagnosis: String,
    @ColumnInfo(name = "diagnose_year") val diagnoseYear: Int? = null,
    @ColumnInfo(name = "hla_b27") val hlaB27: String = "unknown", // positive / negative / unknown
    /** R27 矩阵分期维：active（炎症活动期）/ stable（缓解期）——驱动 M4 运动过滤与 M5 预警灵敏度 */
    @ColumnInfo(name = "disease_stage") val diseaseStage: String = "unknown", // active / stable / unknown
    @ColumnInfo(name = "spine_mobility") val spineMobility: String? = null, // none / mild / moderate / severe（颈椎受累=moderate+）
    @ColumnInfo(name = "comorbidities") val comorbidities: String? = null, // JSON 数组
    @ColumnInfo(name = "allergies") val allergies: String? = null, // JSON 数组
    @ColumnInfo(name = "lifestyle") val lifestyle: String? = null, // JSON
    @ColumnInfo(name = "emergency_blood_type") val emergencyBloodType: String? = null,
    @ColumnInfo(name = "emergency_med_summary") val emergencyMedSummary: String? = null,
    @ColumnInfo(name = "emergency_note") val emergencyNote: String? = null,
    /** 极简模式（红线三状态机 e2：发作期输入减负） */
    @ColumnInfo(name = "ui_mode") val uiMode: String = "normal", // normal / minimal
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
    @ColumnInfo(name = "prn_reason") val prnReason: String? = null,
    /** 实现层增补 D-1：口服各槽位时刻 JSON ["08:00","20:00"]；frequency=prn 时为空 */
    @ColumnInfo(name = "take_times") val takeTimes: String? = null,
    /** 实现层增补 D-1：WEEKLY 时的星期（1=周一 … 7=周日） */
    @ColumnInfo(name = "weekly_weekday") val weeklyWeekday: Int? = null,
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
)

/** R17 停药原因分类分级（D-2 §7：自行停药触发警示） */
enum class StopReason(val label: String, val warning: String?) {
    DOCTOR_SCHEDULED("医嘱计划停（疗程结束）", null),
    DOCTOR_ADJUST("医嘱调整（换药 / 减量）", null),
    SELF_STOPPED("自行停药", "自行停药有病情反弹风险——生物制剂尤其不建议自行停用或减量，任何调整请与风湿科医生确认。"),
    SIDE_EFFECT("副作用", "建议联系医生说明副作用表现，由医生判断停药 / 换药或对症处理。"),
    EXAM_RESULT("检查结果调整", null),
    OTHER("其他", null);

    companion object {
        fun fromKey(k: String?) = entries.firstOrNull { it.name.equals(k, true) } ?: OTHER
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

/** M5 发作登记（R18 flare_events）：开始 / 诱因 / 处理 / 缓解，联动极简模式。 */
enum class FlareTrigger(val label: String) {
    INFECTION("感染 / 感冒"), COLD("着凉"), OVERWORK("劳累"), STRESS("情绪 / 压力"),
    WEATHER("天气变化"), DIET("饮食"), UNKNOWN("不明");

    companion object { fun fromKey(k: String?) = entries.firstOrNull { it.name.equals(k, true) } ?: UNKNOWN }
}

enum class FlareAction(val label: String) {
    REST("休息调整"), HEAT("热敷"), GENTLE_MOVE("温和活动"), EXTRA_MED("临时加药（遵医嘱）"),
    DOCTOR("就医"), NONE("未处理");

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

/** M4 运动打卡（exercise_logs）。exc_id 弱引用 kb_entries(exercise)；R21 次日反馈字段组。 */
enum class FeedbackChange(val label: String) { BETTER("好转"), SAME("不变"), WORSE("加重") }

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

/** M2 补剂档案（supplements）——与 medications 同构但独立域，避免混淆。 */
enum class SupplementCategory(val label: String) {
    CALCIUM("钙"), VITAMIN_D("维生素 D"), OMEGA3("Omega-3 / 鱼油"),
    PROBIOTIC("益生菌"), CURCUMIN("姜黄素"), COLLAGEN("胶原蛋白"),
    VITAMIN_B("B 族"), OTHER("其他");
    companion object { fun fromKey(k: String?) = entries.firstOrNull { it.name.equals(k, true) } ?: OTHER }
}

@Entity(tableName = "supplements", indices = [Index("is_archived")])
data class Supplement(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "brand") val brand: String? = null,
    @ColumnInfo(name = "category") val category: String,
    @ColumnInfo(name = "dose") val dose: String,
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

/** M6 复诊项目（checkup_items）——周期自动排程依据。 */
enum class CheckupType(val label: String) {
    LAB("化验"), IMAGE("影像"), EYE("眼科"), DENTAL("牙科"),
    VACCINE("疫苗"), PHYSICAL("体检"), CONSULT("门诊复诊"), OTHER("其他");
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
    @ColumnInfo(name = "abnormal") val abnormal: String? = null,
    @ColumnInfo(name = "notes") val notes: String? = null,
)

/** M6 疫苗记录（vaccine_records）——活疫苗需医生确认。 */
enum class VaccineType(val label: String) {
    LIVE("活疫苗 / 减毒"), INACTIVATED("灭活 / 重组"), UNKNOWN("不详");
    companion object { fun fromKey(k: String?) = entries.firstOrNull { it.name.equals(k, true) } ?: UNKNOWN }
}

enum class DoctorConfirm(val label: String) {
    PENDING("待确认"), CONFIRMED("医生同意"), DECLINED("医生不建议");
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
    @ColumnInfo(name = "doctor_confirm") val doctorConfirm: String = "pending",
    @ColumnInfo(name = "reaction") val reaction: String? = null,
    @ColumnInfo(name = "next_due_date") val nextDueDate: String? = null,
    @ColumnInfo(name = "notes") val notes: String? = null,
)

// ===== M7 紧急卡 =====

/** M7 紧急事件（emergency_events）——五应急场景记录。 */
enum class EmergencyScene(val label: String, val kbId: String) {
    UVEITIS("葡萄膜炎急性发作", "emr-001"),
    INFECTION_FEVER("感染发热（生物制剂期间）", "emr-002"),
    FALL_FRACTURE("跌倒 / 骨折", "emr-003"),
    CAUDA_EQUINA("马尾综合征（神经急症）", "emr-004"),
    STEROID_ADRENAL("糖皮质激素停药 / 肾上腺危象", "emr-005");
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
