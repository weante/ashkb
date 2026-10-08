package com.ashkb.app.domain

import androidx.annotation.StringRes
import com.ashkb.app.R

/**
 * B7（v1.0.39）：4–12 周周期康复计划**模板**与 `week_structure` 编解码。
 *
 * 模板只给「每周强度级别 + 每周目标天数 + 一句提示」，**不写死具体动作**——动作仍由
 * `ExerciseEngine` 按当日分期从运动库过滤生成，避免与 R27 矩阵产生第二份真相。
 *
 * 编解码为自实现（不依赖 org.json，保持 domain 纯 JVM 可单测）。
 *
 * v1.0.42：**解析改为逐字符扫描，彻底移除正则**。原因见 `parse` 的注释——旧版 `Regex` 里有一个
 * 未转义的 `}`，Java 的 `Pattern` 视其为普通字符而 **Android（ICU）会抛 `PatternSyntaxException`**，
 * 导致类初始化失败（真机 `ExceptionInInitializerError → PatternSyntaxException: near index 83`，
 * 继而表现为 `NoClassDefFoundError: K1.n`）。
 *
 * i18n（v1.2.6）：模板定义用 [WeekSpecRes]（文案是资源 id），落库用 [WeekSpec]（文案是文本）——
 * `week_structure` 是一段 JSON，资源 id 在解析回来时无从还原，所以**展开必须在种入时做一次**，
 * 见 [materialize]。`grade`（`L1`/`L2`/`L1+L2`）与 `days` 是代码与数值，两种形态都不翻译。
 */
object ExercisePlanTemplates {

    /** 落库形态：`note` 是**已按种入时语言展开**的文本（`parse` 读回来的就是它）。 */
    data class WeekSpec(val week: Int, val grade: String, val days: Int, val note: String)

    /** 模板定义形态：`noteRes` 是资源 id，`grade` / `days` 与 [WeekSpec] 同义。 */
    data class WeekSpecRes(
        val week: Int,
        val grade: String,
        val days: Int,
        @StringRes val noteRes: Int,
    )

    data class Template(
        val id: String,
        @StringRes val titleRes: Int,
        val weeks: Int,
        val stageMode: String,
        val spec: List<WeekSpecRes>,
    )

    val ALL: List<Template> = listOf(
        Template(
            id = "eplan-seed-4w",
            titleRes = R.string.eplan_seed_4w_title,
            weeks = 4,
            stageMode = "any",
            spec = listOf(
                WeekSpecRes(1, "L1", 3, R.string.eplan_note_01),
                WeekSpecRes(2, "L1", 4, R.string.eplan_note_02),
                WeekSpecRes(3, "L1+L2", 4, R.string.eplan_note_03),
                WeekSpecRes(4, "L2", 5, R.string.eplan_note_04),
            ),
        ),
        Template(
            id = "eplan-seed-8w",
            titleRes = R.string.eplan_seed_8w_title,
            weeks = 8,
            stageMode = "stable",
            spec = listOf(
                WeekSpecRes(1, "L1", 3, R.string.eplan_note_05),
                WeekSpecRes(2, "L1", 4, R.string.eplan_note_06),
                WeekSpecRes(3, "L1+L2", 4, R.string.eplan_note_07),
                WeekSpecRes(4, "L2", 5, R.string.eplan_note_08),
                WeekSpecRes(5, "L2", 5, R.string.eplan_note_09),
                WeekSpecRes(6, "L2", 5, R.string.eplan_note_10),
                WeekSpecRes(7, "L2", 5, R.string.eplan_note_11),
                WeekSpecRes(8, "L1+L2", 4, R.string.eplan_note_12),
            ),
        ),
        Template(
            id = "eplan-seed-12w",
            titleRes = R.string.eplan_seed_12w_title,
            weeks = 12,
            stageMode = "stable",
            spec = buildList {
                add(WeekSpecRes(1, "L1", 3, R.string.eplan_note_13))
                add(WeekSpecRes(2, "L1", 4, R.string.eplan_note_14))
                (3..4).forEach { add(WeekSpecRes(it, "L1+L2", 4, R.string.eplan_note_15)) }
                (5..8).forEach { add(WeekSpecRes(it, "L2", 5, R.string.eplan_note_16)) }
                (9..11).forEach { add(WeekSpecRes(it, "L2", 5, R.string.eplan_note_17)) }
                add(WeekSpecRes(12, "L1+L2", 4, R.string.eplan_note_18))
            },
        ),
    )

    /**
     * 把模板展开成可落库的 [WeekSpec]（文案按传入的取词函数展开）。
     *
     * `note` 必须由调用方注入（`context.getString(it)`）：domain 层没有 `Context`，
     * 而这里要的正是**当前语言**的文本。
     */
    fun materialize(template: Template, note: (Int) -> String): List<WeekSpec> =
        template.spec.map { WeekSpec(it.week, it.grade, it.days, note(it.noteRes)) }

    fun pending(existingIds: Set<String>): List<Template> = ALL.filter { it.id !in existingIds }

    /** 每周结构 → JSON（存入 `exercise_plans.week_structure`）。 */
    fun toJson(spec: List<WeekSpec>): String =
        spec.joinToString(",", "[", "]") { w ->
            "{\"week\":${w.week},\"grade\":\"${esc(w.grade)}\",\"days\":${w.days},\"note\":\"${esc(w.note)}\"}"
        }

    /**
     * JSON → 每周结构；解析不到的周忽略（脏数据不致崩）。
     *
     * 该 JSON 只由本对象的 `toJson` 产出，格式固定，故**逐字符解析**即可：
     * 先按深度配平找出每个对象的边界（字符串内的转义与花括号不参与配平），再按字段名取值。
     * 这样既不依赖 org.json，也**不依赖正则引擎的方言差异**（见类注释）。
     */
    fun parse(json: String?): List<WeekSpec> {
        if (json.isNullOrBlank()) return emptyList()
        val body = json
        val out = ArrayList<WeekSpec>()
        var i = body.indexOf('{')
        while (i >= 0) {
            val end = indexOfObjectEnd(body, i)
            if (end < 0) break
            parseOne(body.substring(i + 1, end))?.let { out.add(it) }
            i = body.indexOf('{', end + 1)
        }
        return out.sortedBy { it.week }
    }

    /** 与 `open` 处的 `{` 配对的 `}` 下标；字符串内（含转义）不参与配平。找不到返回 -1。 */
    private fun indexOfObjectEnd(s: String, open: Int): Int {
        var depth = 0
        var inStr = false
        var i = open
        while (i < s.length) {
            val c = s[i]
            if (inStr) {
                when {
                    c == '\\' -> i++          // 跳过被转义的下一个字符
                    c == '"' -> inStr = false
                }
            } else {
                when (c) {
                    '"' -> inStr = true
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) return i
                    }
                }
            }
            i++
        }
        return -1
    }

    /** 解析单个对象体（不含首尾花括号）。缺 `week` 视为脏数据，返回 null。 */
    private fun parseOne(obj: String): WeekSpec? {
        val week = intField(obj, "week") ?: return null
        return WeekSpec(
            week = week,
            grade = strField(obj, "grade"),
            days = intField(obj, "days") ?: 0,
            note = strField(obj, "note"),
        )
    }

    private fun intField(obj: String, key: String): Int? {
        val at = obj.indexOf("\"$key\":")
        if (at < 0) return null
        var j = at + key.length + 3
        val sb = StringBuilder()
        while (j < obj.length && obj[j].isDigit()) {
            sb.append(obj[j])
            j++
        }
        return sb.toString().toIntOrNull()
    }

    private fun strField(obj: String, key: String): String {
        val at = obj.indexOf("\"$key\":\"")
        if (at < 0) return ""
        var j = at + key.length + 4
        val sb = StringBuilder()
        while (j < obj.length) {
            val c = obj[j]
            if (c == '\\' && j + 1 < obj.length) {
                sb.append(unescChar(obj[j + 1]))
                j += 2
                continue
            }
            if (c == '"') break
            sb.append(c)
            j++
        }
        return sb.toString()
    }

    /** 指定周的目标天数（超出范围返回 0）。 */
    fun targetDays(spec: List<WeekSpec>, week: Int): Int = spec.firstOrNull { it.week == week }?.days ?: 0

    private fun esc(s: String): String = buildString {
        for (c in s) when (c) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> append(c)
        }
    }

    private fun unescChar(c: Char): Char = when (c) {
        'n' -> '\n'
        'r' -> '\r'
        't' -> '\t'
        else -> c
    }
}
