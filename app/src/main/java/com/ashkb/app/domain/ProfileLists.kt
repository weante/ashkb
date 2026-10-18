package com.ashkb.app.domain

import org.json.JSONArray

/**
 * v1.2.10（审查 §5-MEDIUM-5 与裁决项 7）：档案里两个「列表型」字段
 * （`profile.allergies` / `profile.comorbidities`）的**唯一编解码口径**。
 *
 * 此前这两个函数是 `ui/me/ProfileEditScreen.kt` 里的两个私有函数，各写一遍、且不互逆：
 * - 写侧手写拼串：`"[" + items.joinToString(",") { "\"$it\"" } + "]"` —— **不转义 `"` 与 `\`**，
 *   用户填 `对"某药"过敏` 就产出非法 JSON；
 * - 读侧 `removeSurrounding("[","]").replace("\"","")` —— 「删掉所有引号」不是逆运算，
 *   存量值里含引号的条目**打开编辑再保存就会被改写**。
 *
 * 抽进 domain（`org.json` 在 JVM 单测里有 `libs.json` 桥接，可见 [ScheduleCalc] 的先例）
 * 后，写侧产物与读侧解析可以放进同一个往返测试里对着跑。
 */
object ProfileLists {

    /**
     * 用户输入（半角或全角逗号分隔）→ JSON 数组字符串；无有效条目返回 `null`
     * （存 `null` 而不是 `"[]"`，与既有数据的「没填」语义一致）。
     */
    fun toJson(csv: String): String? {
        val items = csv.split("，", ",").map { it.trim() }.filter { it.isNotBlank() }
        if (items.isEmpty()) return null
        return JSONArray(items).toString()
    }

    /**
     * JSON 数组字符串 → 逗号分隔的可编辑文本（[toJson] 的逆）。
     *
     * 解析不出来（历史脏值，例如旧版拼串产出的非法 JSON）**原样返回**——
     * 至少不再对用户数据做破坏性编辑，也不再假装自己能读懂它。
     */
    fun fromJson(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return raw
        return (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() }.joinToString(",")
    }
}
