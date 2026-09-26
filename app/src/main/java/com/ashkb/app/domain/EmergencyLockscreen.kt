package com.ashkb.app.domain

import com.ashkb.app.data.entity.EmergencyContact
import com.ashkb.app.data.entity.Profile

/**
 * v1.0.66 B6a：**锁屏紧急信息**的内容构建。
 *
 * 用途：把「急救现场最需要一眼看到」的信息组织成锁屏可见通知的几行文本——
 * 血型 / 诊断 / 过敏 / 关键用药（免疫抑制类优先）/ 家属与医生电话。
 *
 * **为什么只做「可见通知」而不是自定义锁屏页**：Android 没有面向普通应用的
 * 自定义锁屏控件（锁屏 Widget 早已废弃），官方唯一受支持的途径就是
 * 「`VISIBILITY_PUBLIC` 的常驻通知」——它在锁屏上直接显示完整内容、
 * 无需解锁，且各厂商 ROM 行为一致。
 *
 * **隐私**：健康信息上锁屏属敏感操作，故**默认关闭**，由用户在紧急卡页显式开启
 * （见 [com.ashkb.app.data.repo.EmergencyLockscreenStore]）。
 *
 * 纯函数、无 Android 依赖，可单测。
 */
object EmergencyLockscreen {

    /** 锁屏通知最多列出的用药条数——锁屏可读长度有限，只保关键项。 */
    const val MAX_MED_LINES = 4

    /** 锁屏通知的标题（不含用户数据，避免标题栏泄露）。 */
    const val TITLE = "紧急信息"

    data class Content(val title: String, val lines: List<String>)

    fun build(
        profile: Profile?,
        contacts: List<EmergencyContact>,
        meds: EmergencyMeds.Summary,
    ): Content {
        val lines = buildList {
            profile?.let { p ->
                val blood = p.emergencyBloodType?.takeIf { it.isNotBlank() } ?: "未填"
                add("血型 $blood ｜ 诊断 ${p.diagnosis}")
                cleanJsonArray(p.allergies)?.let { add("过敏 $it") }
            }

            if (!meds.isEmpty) {
                val head = meds.ordered.map { it.name }
                val shown = head.take(MAX_MED_LINES).joinToString("、")
                val suffix = if (head.size > MAX_MED_LINES) " 等 ${head.size} 种" else ""
                val flag = if (meds.hasImmunosuppressant) "（含免疫抑制，注意感染风险）" else ""
                add("用药$flag $shown$suffix")
            }

            // 家属优先，医生次之——急救时先找能到场的人
            contacts.firstOrNull { it.isEmergency && !it.isDoctor }
                ?.let { add("家属 ${it.name} ${it.phone}") }
            contacts.firstOrNull { it.isDoctor }
                ?.let { add("医生 ${it.name} ${it.phone}") }
        }
        return Content(TITLE, lines)
    }

    /**
     * `["青霉素","磺胺"]` → `青霉素、磺胺`。
     *
     * 这几列在库里是 JSON 数组字符串，但紧急卡此前各处都直接原样展示（含方括号引号）。
     * 锁屏上可读性更重要，故在此做一次「展平为顿号连接」；解析失败返回 null（宁可不显示）。
     */
    fun cleanJsonArray(raw: String?): String? {
        val s = raw?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val inner = s.removeSurrounding("[", "]")
        val items = inner.split(",").map { it.trim().removeSurrounding("\"") }.filter { it.isNotBlank() }
        return items.takeIf { it.isNotEmpty() }?.joinToString("、")
    }
}
