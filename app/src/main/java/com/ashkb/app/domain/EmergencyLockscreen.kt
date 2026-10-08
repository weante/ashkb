package com.ashkb.app.domain

import androidx.annotation.StringRes
import com.ashkb.app.R
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
 * i18n：本对象只给**资源 id + 参数**（[ResText]）。两处并列项的分隔符（中文「、」/
 * 英文「, 」）由调用方从资源取出后**注入**，本对象因此仍是纯函数、不碰 Context。
 * 用药行按「是否含免疫抑制」×「是否截断」四种组合各有一条资源——把标志位拼进正文
 * 会让两种语言各自的括号与语序无处安放。
 */
object EmergencyLockscreen {

    /** 锁屏通知最多列出的用药条数——锁屏可读长度有限，只保关键项。 */
    const val MAX_MED_LINES = 4

    /** 锁屏通知的标题资源（不含用户数据，避免标题栏泄露）。 */
    @StringRes
    val TITLE_RES: Int = R.string.dom_lock_title

    data class Content(
        /** 通知标题资源 */
        @StringRes val titleRes: Int,
        /** 正文各行（资源 id + 参数） */
        val lines: List<ResText>,
    )

    /**
     * 组装锁屏内容。
     *
     * @param listSeparator 并列项的分隔符——由调用方从资源取（`dom_lock_list_separator`），
     *   故本对象保持纯函数、不依赖 Context。
     */
    fun build(
        profile: Profile?,
        contacts: List<EmergencyContact>,
        meds: EmergencyMeds.Summary,
        listSeparator: String,
    ): Content {
        val lines = buildList {
            profile?.let { p ->
                val blood = p.emergencyBloodType?.takeIf { it.isNotBlank() }
                add(
                    if (blood == null) ResText(R.string.dom_lock_blood_unfilled, listOf(p.diagnosis))
                    else ResText(R.string.dom_lock_blood, listOf(blood, p.diagnosis))
                )
                cleanJsonArray(p.allergies, listSeparator)?.let { add(ResText(R.string.dom_lock_allergy, listOf(it))) }
            }

            if (!meds.isEmpty) {
                val head = meds.ordered.map { it.name }
                val shown = head.take(MAX_MED_LINES).joinToString(listSeparator)
                val truncated = head.size > MAX_MED_LINES
                val immuno = meds.hasImmunosuppressant
                add(
                    ResText(
                        res = when {
                            immuno && truncated -> R.string.dom_lock_meds_immuno_more
                            immuno -> R.string.dom_lock_meds_immuno
                            truncated -> R.string.dom_lock_meds_more
                            else -> R.string.dom_lock_meds
                        },
                        // 截断时多一个「共 N 种」参数；两条 _more 资源的占位符顺序都是「名单, 总数」
                        args = if (truncated) listOf(shown, head.size) else listOf(shown),
                    )
                )
            }

            // 家属优先，医生次之——急救时先找能到场的人
            contacts.firstOrNull { it.isEmergency && !it.isDoctor }
                ?.let { add(ResText(R.string.dom_lock_family, listOf(it.name, it.phone))) }
            contacts.firstOrNull { it.isDoctor }
                ?.let { add(ResText(R.string.dom_lock_doctor, listOf(it.name, it.phone))) }
        }
        return Content(TITLE_RES, lines)
    }

    /**
     * `["青霉素","磺胺"]` → `青霉素、磺胺`。
     *
     * 这几列在库里是 JSON 数组字符串，但紧急卡此前各处都直接原样展示（含方括号引号）。
     * 锁屏上可读性更重要，故在此做一次「展平为分隔符连接」；解析失败返回 null（宁可不显示）。
     *
     * @param listSeparator 连接符由调用方从资源注入（同 [build]）——中文是「、」、英文是「, 」。
     */
    fun cleanJsonArray(raw: String?, listSeparator: String): String? {
        val s = raw?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val inner = s.removeSurrounding("[", "]")
        val items = inner.split(",").map { it.trim().removeSurrounding("\"") }.filter { it.isNotBlank() }
        return items.takeIf { it.isNotEmpty() }?.joinToString(listSeparator)
    }
}
