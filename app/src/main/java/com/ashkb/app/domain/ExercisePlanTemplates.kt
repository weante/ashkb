package com.ashkb.app.domain

/**
 * B7（v1.0.39）：4–12 周周期康复计划**模板**与 `week_structure` 编解码。
 *
 * 模板只给「每周强度级别 + 每周目标天数 + 一句提示」，**不写死具体动作**——动作仍由
 * `ExerciseEngine` 按当日分期从运动库过滤生成，避免与 R27 矩阵产生第二份真相。
 *
 * 编解码为自实现（不依赖 org.json，保持 domain 纯 JVM 可单测）；只处理本对象自己
 * 产出的 JSON 形状，脏数据不会抛异常（解析不到的周直接忽略）。
 */
object ExercisePlanTemplates {

    data class WeekSpec(val week: Int, val grade: String, val days: Int, val note: String)

    data class Template(
        val id: String,
        val title: String,
        val weeks: Int,
        val stageMode: String,
        val spec: List<WeekSpec>,
    )

    val ALL: List<Template> = listOf(
        Template(
            id = "eplan-seed-4w",
            title = "4 周起步计划（活动期 → 缓解期过渡）",
            weeks = 4,
            stageMode = "any",
            spec = listOf(
                WeekSpec(1, "L1", 3, "只做轻柔维持（呼吸 / 拉伸 / 活动度），不追求量"),
                WeekSpec(2, "L1", 4, "保持 L1，逐步增加天数"),
                WeekSpec(3, "L1+L2", 4, "加入低冲击有氧 / 自重力量，量从半量起"),
                WeekSpec(4, "L2", 5, "以 L2 为主；疼痛加重即退回 L1（exc-010 两小时规则）"),
            ),
        ),
        Template(
            id = "eplan-seed-8w",
            title = "8 周强化计划",
            weeks = 8,
            stageMode = "stable",
            spec = listOf(
                WeekSpec(1, "L1", 3, "适应期：轻柔维持为主"),
                WeekSpec(2, "L1", 4, "增加频次，仍限 L1"),
                WeekSpec(3, "L1+L2", 4, "引入 L2，留意次日反馈"),
                WeekSpec(4, "L2", 5, "L2 为主，力量间隔 48 小时"),
                WeekSpec(5, "L2", 5, "稳定周：保持上周处方"),
                WeekSpec(6, "L2", 5, "稳定周：按反馈微调动作"),
                WeekSpec(7, "L2", 5, "巩固周：关注晨僵与疼痛趋势"),
                WeekSpec(8, "L1+L2", 4, "回落周：降低总量，为下一周期恢复"),
            ),
        ),
        Template(
            id = "eplan-seed-12w",
            title = "12 周维持计划（缓解期长期）",
            weeks = 12,
            stageMode = "stable",
            spec = buildList {
                add(WeekSpec(1, "L1", 3, "重新起步：只做轻柔维持"))
                add(WeekSpec(2, "L1", 4, "增加频次"))
                (3..4).forEach { add(WeekSpec(it, "L1+L2", 4, "引入 L2，量从半量起")) }
                (5..8).forEach { add(WeekSpec(it, "L2", 5, "L2 维持：有氧 3–5 次/周，力量间隔 48 小时")) }
                (9..11).forEach { add(WeekSpec(it, "L2", 5, "维持周：按次日反馈微调")) }
                add(WeekSpec(12, "L1+L2", 4, "回落周：降量恢复，进入下一周期"))
            },
        ),
    )

    fun pending(existingIds: Set<String>): List<Template> = ALL.filter { it.id !in existingIds }

    /** 每周结构 → JSON（存入 `exercise_plans.week_structure`）。 */
    fun toJson(spec: List<WeekSpec>): String =
        spec.joinToString(",", "[", "]") { w ->
            "{\"week\":${w.week},\"grade\":\"${esc(w.grade)}\",\"days\":${w.days},\"note\":\"${esc(w.note)}\"}"
        }

    private val WEEK_RE = Regex(
        "\\{\"week\":(\\d+),\"grade\":\"((?:[^\"\\\\]|\\\\.)*)\",\"days\":(\\d+),\"note\":\"((?:[^\"\\\\]|\\\\.)*)\"}"
    )

    /** JSON → 每周结构；解析不到的周忽略（脏数据不致崩）。 */
    fun parse(json: String?): List<WeekSpec> {
        if (json.isNullOrBlank()) return emptyList()
        return WEEK_RE.findAll(json).map { m ->
            WeekSpec(
                week = m.groupValues[1].toIntOrNull() ?: return@map null,
                grade = unesc(m.groupValues[2]),
                days = m.groupValues[3].toIntOrNull() ?: 0,
                note = unesc(m.groupValues[4]),
            )
        }.filterNotNull().sortedBy { it.week }.toList()
    }

    /** 指定周的目标天数（超出范围返回 0）。 */
    fun targetDays(spec: List<WeekSpec>, week: Int): Int = spec.firstOrNull { it.week == week }?.days ?: 0

    private fun esc(s: String): String = s.replace("\\", "\\\\").replace("\"", "\\\"")

    private fun unesc(s: String): String = s.replace("\\\"", "\"").replace("\\\\", "\\")
}
