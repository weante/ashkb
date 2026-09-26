package com.ashkb.app.domain

/**
 * v1.0.64 B13：生活方式画像（吸烟 / 职业久坐 / 运动习惯 / 睡眠）。
 *
 * 存 `profile.lifestyle` 列——**扁平 JSON**（全部标量、无嵌套），手写编解码，
 * 不引入任何 JSON 库（本仓库零第三方依赖，且 domain 是纯 JVM，拿不到 `org.json`）。
 *
 * 为什么手写而不是复用 `csvToJson`：这里是**键值**而非数组，且必须能被稳定解析回来
 * （`csvToJson` 只往一个方向写）。编解码各自只有十几行，且由 [LifestyleTest] 锁往返一致性。
 *
 * **医学口径**：四项都只用于「给通用建议」和「排序」，
 * **不参与**任何诊断或用药决策（见 [LifestylePrescription] 与 [Disclaimer]）。
 */
data class Lifestyle(
    /** never / former / current / unknown */
    val smoking: String = SMOKING_UNKNOWN,
    /** 每日职业久坐时长（小时）；null = 未填。 */
    val sedentaryHours: Int? = null,
    /** none / occasional / regular / unknown */
    val exerciseHabit: String = HABIT_UNKNOWN,
    /** 平均每日睡眠时长（小时）；null = 未填。 */
    val sleepHours: Int? = null,
) {

    /** 是否登记过吸烟（现吸或已戒）——风险因素对两者都成立。 */
    val hasSmokingHistory: Boolean
        get() = smoking == SMOKING_CURRENT || smoking == SMOKING_FORMER

    /**
     * 按画像需要**置顶**的知识库条目 id。
     *
     * 修掉 `kb_seed_edu.json`「edu-003 吸烟条目」的悬空挂点：
     * 其 `applicable_scene` 写着「profile 吸烟状态登记后知识库置顶」，但此前 `lifestyle`
     * 从未被采集，该联动永远不触发。
     */
    fun pinnedKbIds(): List<String> = if (hasSmokingHistory) listOf(KB_SMOKING) else emptyList()

    /** 序列化为扁平 JSON；数值为 null 时整键省略（`fromJson` 对应回落 null）。 */
    fun toJson(): String {
        val parts = buildList {
            add("\"smoking\":\"$smoking\"")
            sedentaryHours?.let { add("\"sedentary_hours\":$it") }
            add("\"exercise_habit\":\"$exerciseHabit\"")
            sleepHours?.let { add("\"sleep_hours\":$it") }
        }
        return "{" + parts.joinToString(",") + "}"
    }

    companion object {
        const val SMOKING_NEVER = "never"
        const val SMOKING_FORMER = "former"
        const val SMOKING_CURRENT = "current"
        const val SMOKING_UNKNOWN = "unknown"

        const val HABIT_NONE = "none"
        const val HABIT_OCCASIONAL = "occasional"
        const val HABIT_REGULAR = "regular"
        const val HABIT_UNKNOWN = "unknown"

        /** 吸烟条目的知识库 id（见 kb_seed_edu.json）。 */
        const val KB_SMOKING = "edu-003"

        private val SMOKING_VALUES =
            setOf(SMOKING_NEVER, SMOKING_FORMER, SMOKING_CURRENT, SMOKING_UNKNOWN)
        private val HABIT_VALUES =
            setOf(HABIT_NONE, HABIT_OCCASIONAL, HABIT_REGULAR, HABIT_UNKNOWN)

        /**
         * 解析 [toJson] 的产物。**刻意宽松**：空串 / 残缺 / 手改过的输入一律退化为默认值，
         * 不抛异常——建档数据损坏不该让「我的」页崩掉。
         */
        fun fromJson(raw: String?): Lifestyle {
            if (raw.isNullOrBlank()) return Lifestyle()
            val map = raw.trim().removeSurrounding("{", "}")
                .split(",")
                .mapNotNull { pair ->
                    val i = pair.indexOf(':')
                    if (i <= 0) null
                    else pair.substring(0, i).trim().removeSurrounding("\"") to
                        pair.substring(i + 1).trim().removeSurrounding("\"")
                }
                .toMap()
            val smoking = map["smoking"].orEmpty()
            val habit = map["exercise_habit"].orEmpty()
            return Lifestyle(
                smoking = if (smoking in SMOKING_VALUES) smoking else SMOKING_UNKNOWN,
                sedentaryHours = map["sedentary_hours"]?.toIntOrNull()?.takeIf { it in 0..24 },
                exerciseHabit = if (habit in HABIT_VALUES) habit else HABIT_UNKNOWN,
                sleepHours = map["sleep_hours"]?.toIntOrNull()?.takeIf { it in 0..24 },
            )
        }
    }
}
