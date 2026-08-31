package com.ashkb.app.domain

import com.ashkb.app.data.entity.KbEntry
import org.json.JSONObject

/**
 * R27 三级运动分级 × 分期矩阵执行引擎。
 * 输入：kb_entries(exercise) payload + profile 两字段（disease_stage / spine_mobility）；
 * 输出：当日可执行处方（recommend/allow/downgrade/conditional/pause）与黑榜拦截区（block/advise_against）。
 */
object ExerciseEngine {

    /** 颈椎受累判定：spine_mobility ≥ moderate（矩阵 §3：exb-004 / exb-005 条件） */
    fun cervicalInvolved(spineMobility: String?): Boolean =
        spineMobility == "moderate" || spineMobility == "severe"

    data class ExerciseCard(
        val entry: KbEntry,
        /** matrix 判定：recommend / allow / downgrade / conditional / pause / block / advise_against */
        val verdict: String,
        /** 给用户看的行动提示（剂量 / 减量 / 条件 / 拦截原因与替代） */
        val hint: String,
        val grade: String,
        val listType: String, // red / black
        val movements: List<String>,
        val dose: String?,
    )

    /** 解析 payload 关键字段 */
    private fun JSONObject.optStr(k: String): String? = if (has(k) && !isNull(k)) optString(k) else null

    private fun movements(o: JSONObject): List<String> =
        if (o.has("movements")) o.optJSONArray("movements")?.let { a ->
            (0 until a.length()).map { a.optString(it) }
        } ?: emptyList() else emptyList()

    fun parse(entry: KbEntry): JSONObject = runCatching { JSONObject(entry.payload) }.getOrDefault(JSONObject())

    /**
     * 矩阵判定核心。stage 取 profile.disease_stage（active/stable/unknown；
     * unknown 按 active 处理——保守侧，矩阵 §4「用户标记 + 症状趋势提示复核」）。
     */
    fun evaluate(
        entry: KbEntry,
        diseaseStage: String?,
        spineMobility: String?,
    ): ExerciseCard {
        val p = parse(entry)
        val listType = p.optStr("list_type") ?: "red"
        val grade = p.optStr("grade") ?: "L2"
        val matrix = p.optJSONObject("grade_matrix")
        val stage = if (diseaseStage == "stable") "stable" else "active"
        val raw = matrix?.optStr(stage) ?: when (listType) {
            "black" -> "block"
            else -> "allow"
        }
        val cervical = cervicalInvolved(spineMobility)
        val cervicalCondition = p.optStr("cervical_condition") ?: "none"
        val dose = p.optStr("dose")
        val alt = p.optStr("alternative_hint")
        val blockRule = p.optJSONObject("block_rule")

        // 黑榜条件化拦截（R27 §3）：stage 命中且（无条件项或颈椎条件满足）
        val stageBlocked = blockRule?.optJSONArray("stage")?.let { a ->
            (0 until a.length()).any { a.optString(it) == stage }
        } ?: false
        val cervicalGated = blockRule?.optBoolean("cervical", false) ?: false
        val blackIntercepted = listType == "black" && stageBlocked && (!cervicalGated || cervical)

        // R27 §3 蛙泳 / 倒立条目：颈椎受累（cervical_only）全期拦截，不看 stage
        val cervicalBlock = listType == "black" && cervical && cervicalCondition == "cervical_only"

        val verdict = when {
            blackIntercepted || cervicalBlock -> "block"
            else -> raw
        }

        val hint = buildString {
            when (verdict) {
                "recommend" -> append("今日推荐。${dose ?: ""}")
                "allow" -> append("可以做。${dose ?: ""}")
                "downgrade" -> append("今日减量执行（幅度减半 / 时长缩短）。${dose ?: ""}")
                "conditional" -> append("条件允许时做：仅轻柔体式，幅度以不痛为界。${dose ?: ""}")
                "pause" -> append("活动期暂停（降级为 L1 轻柔项替代）。")
                "advise_against" -> append("不建议：骨折与应力风险随强度上升。")
                else -> append("已拦截：${p.optStr("risk") ?: "高强度高风险动作"}")
            }
            if (cervical && (cervicalCondition == "cervical_only" || cervicalCondition == "breaststroke_block" || cervicalCondition == "pose_filter")) {
                append("\n颈椎受累提示：${p.optStr("risk") ?: "该类动作颈椎风险高"}")
            }
            if (verdict == "block" || verdict == "advise_against") {
                alt?.let { append("\n替代方案：$it") }
            }
        }.trim()

        return ExerciseCard(
            entry = entry,
            verdict = verdict,
            hint = hint,
            grade = grade,
            listType = listType,
            movements = movements(p),
            dose = dose,
        )
    }

    /** 当日处方：红榜按矩阵过滤（pause 不出现），黑榜全部进拦截区 */
    fun todayPlan(
        exercises: List<KbEntry>,
        diseaseStage: String?,
        spineMobility: String?,
    ): Pair<List<ExerciseCard>, List<ExerciseCard>> {
        val cards = exercises.map { evaluate(it, diseaseStage, spineMobility) }
        val plan = cards.filter { it.listType == "red" && it.verdict != "pause" }
            .sortedWith(compareBy({ gradeOrder(it.grade) }, { it.verdict != "recommend" }))
        val blocked = cards.filter { it.listType == "black" }
        return plan to blocked
    }

    private fun gradeOrder(g: String) = when (g) { "L1" -> 0; "L2" -> 1; else -> 2 }

    /** R21 / exc-010 判读：worse → 建议下次减量；区分肌肉酸痛与炎症加重 */
    fun interpretFeedback(
        painChange: String?,
        stiffnessChange: String?,
        isMuscleSoreness: Boolean?,
    ): String = when {
        painChange == "worse" && isMuscleSoreness == true ->
            "运动后疼痛加重，但表现符合肌肉酸痛（延迟性酸痛）。观察 1–2 天；下次时长可暂不加量（exc-010 进展原则）。"
        painChange == "worse" ->
            "运动后疼痛加重且不符合单纯肌肉酸痛——按「运动后 2 小时疼痛规则」建议下次减量：时长 −20% 或强度降一档（exc-010）。连续 2 次加重建议咨询康复科。"
        stiffnessChange == "worse" && isMuscleSoreness != true ->
            "晨僵加重——可能提示炎症活动而非运动过量。建议当周维持低强度（L1），若持续加重请记录症状趋势并联系医生。"
        painChange == "better" || stiffnessChange == "better" ->
            "反馈良好。可按进展原则加量：先时长（每 1–2 周 +5–10 min），再频率，最后强度。"
        else ->
            "反馈平稳。维持当前量，加量顺序：时长 → 频率 → 强度（exc-010）。"
    }
}
