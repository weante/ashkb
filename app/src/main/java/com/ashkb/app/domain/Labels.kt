package com.ashkb.app.domain

/**
 * 枚举 key → 中文标签的统一映射（此前在 MeScreen / EmergencyScreen / ReportPdfWriter
 * / WellnessScreen 各写一份）。枚举 key 绝不出现在 UI（改版方案 §11）。
 */
object Labels {

    fun hlaB27(key: String?): String = when (key) {
        "positive" -> "阳性"; "negative" -> "阴性"; else -> "未知"
    }

    fun diseaseStage(key: String?): String = when (key) {
        "stable" -> "缓解期"; "controlled" -> "控制中"; "flare" -> "发作期"; else -> "未评估"
    }

    /**
     * v1.0.67 C1：骶髂关节影像分期的可选值（改良纽约标准 mNY，X 线 0–IV）。
     * 单独暴露 key 清单，UI 不必自己写一遍字面量。
     */
    val SACROILIITIS_KEYS = listOf("0", "1", "2", "3", "4")

    /** 骶髂关节影像分期 → 展示文本（含罗马数字，key 不出现在 UI）。 */
    fun sacroiliitisGrade(key: String?): String = when (key) {
        "0" -> "0 正常"
        "1" -> "I 可疑"
        "2" -> "II 轻度"
        "3" -> "III 中度"
        "4" -> "IV 重度"
        else -> "未评估"
    }

    fun dietPattern(key: String?): String = when (key) {
        "mediterranean" -> "地中海式"; "paleo" -> "旧石器式"
        "vegan" -> "纯素"; "vegetarian" -> "素食"
        "low_starch" -> "低淀粉"; else -> "杂食"
    }

    fun seafoodFreq(key: String?): String = when (key) {
        "never" -> "不吃"; "rare" -> "偶尔"
        "weekly" -> "每周"; "frequent" -> "经常"; else -> "未设置"
    }

    fun dairyTolerance(key: String?): String = when (key) {
        "yes" -> "耐受"; "no" -> "不耐受"
        "lactose_free_only" -> "仅无乳糖"; else -> "未设置"
    }

    fun foodAvoidCategory(key: String?): String = when (key) {
        "allergy" -> "过敏"; "intolerance" -> "不耐受"
        "doctor_advice" -> "医嘱"; "personal_experience" -> "个人体验"
        "drug_interaction" -> "药效冲突"; else -> key ?: ""
    }

    fun foodAvoidSeverity(key: String?): String = when (key) {
        "high" -> "高风险"; "medium" -> "中"; "low" -> "低"; else -> key ?: ""
    }
}
