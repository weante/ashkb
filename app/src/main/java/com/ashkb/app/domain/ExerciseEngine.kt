package com.ashkb.app.domain

import androidx.annotation.StringRes
import com.ashkb.app.R
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

    /**
     * 提示片段：资源 id + 参数。
     *
     * v1.2.6（i18n）：domain 层不再持有患者可见文案，由 UI 层按当前语言
     * （`stringResource(part.res, …)`，见 `ExerciseScreen.hintText()`）渲染。
     */
    data class TextPart(@StringRes val res: Int, val args: List<String> = emptyList())

    data class ExerciseCard(
        val entry: KbEntry,
        /** matrix 判定：recommend / allow / downgrade / conditional / pause / block / advise_against */
        val verdict: String,
        /** 给用户看的行动提示片段（主提示恒为首项，颈椎提示次之，替代方案末位） */
        val hintParts: List<TextPart>,
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

    fun parse(entry: KbEntry): JSONObject = parseOrNull(entry) ?: JSONObject()

    /**
     * v1.0.77（批次 4）：解析失败返回 **null**——调用方必须能区分「JSON 坏了」与「JSON 正常但缺键」。
     *
     * 此前两者都退化成空 `JSONObject`：红榜拿到默认 `allow`（**禁忌动作反而被推荐**）、
     * 黑榜虽默认 block 但提示语为空。第三份审查报告把这条列为 fail-open（P1-14）。
     */
    private fun parseOrNull(entry: KbEntry): JSONObject? =
        runCatching { JSONObject(entry.payload) }.getOrNull()

    /**
     * 矩阵判定核心。stage 取 profile.disease_stage（R1 三态：stable / controlled / flare；
     * unknown 及一切未识别值按 flare 处理——保守侧，矩阵 §4「用户标记 + 症状趋势提示复核」）。
     */
    fun evaluate(
        entry: KbEntry,
        diseaseStage: String?,
        spineMobility: String?,
    ): ExerciseCard {
        val p = parseOrNull(entry)
        if (p == null) {
            // v1.0.77（批次 4）：**fail-closed**。数据读不出来就不给处方，且让问题**可见**
            // （进黑榜拦截区，而不是静默消失）——「拼错种子即把禁忌动作变推荐」正是从这里来的。
            return ExerciseCard(
                entry = entry,
                verdict = "block",
                hintParts = listOf(TextPart(R.string.ui_exercise_hint_unparsed)),
                grade = "L3",
                listType = "black",
                movements = emptyList(),
                dose = null,
            )
        }
        val listType = p.optStr("list_type") ?: "red"
        val grade = p.optStr("grade") ?: "L2"
        val matrix = p.optJSONObject("grade_matrix")
        val stage = when (diseaseStage) {
            "stable", "controlled" -> diseaseStage
            else -> "flare" // flare / unknown / null / 残留旧值 → 发作期保守
        }
        // R1 兼容：v8 之前的种子矩阵只有 stable/active 两键——controlled/flare 回退到旧 active 行为
        val raw = matrix?.optStr(stage)
            ?: matrix?.optStr("active")
            ?: when (listType) {
                // v1.0.77（批次 4）：矩阵缺失时红榜**不再默认 allow**。
                // 种子里 15 条运动条目**全部**带 grade_matrix，缺失只可能是数据损坏或非预期格式；
                // 此时按「降级执行」而非「可以做」——安全默认值方向（维护者 2026-09-29 裁决）。
                "black" -> "block"
                else -> "downgrade"
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

        // v1.0.77（批次 4）：种子里用到、而引擎此前**静默忽略**的两个键（第三份审查报告 P1-15）：
        //  · "always"        —— 颈椎受累者**一律**拦截（与 stage 无关）；种子把它挂在黑榜高强度动作上
        //  · "amplitude_half"—— 颈椎受累者至多**幅度减半**执行（不得 recommend / allow）
        // 忽略它们的后果：本该拦截的高风险动作被放行、本该减半的动作按原幅度给出。
        val cervicalAlways = cervical && cervicalCondition == "always"
        val cervicalHalf = cervical && cervicalCondition == "amplitude_half"

        val verdict = when {
            blackIntercepted || cervicalBlock -> "block"
            cervicalAlways -> if (listType == "black") "block" else "pause"
            // R1 发作期：红榜 L2/L3 一律 pause（当日只出 L1 轻柔项）——矩阵之外的引擎级兜底
            stage == "flare" && listType == "red" && grade != "L1" -> "pause"
            cervicalHalf && (raw == "recommend" || raw == "allow") -> "downgrade"
            else -> raw
        }

        val hintParts = hintPartsOf(verdict, dose, p.optStr("risk"), cervical, cervicalCondition, alt)

        return ExerciseCard(
            entry = entry,
            verdict = verdict,
            hintParts = hintParts,
            grade = grade,
            listType = listType,
            movements = movements(p),
            dose = dose,
        )
    }

    /**
     * 提示片段拼装（v1.2.6 i18n）：主提示恒为首项、颈椎提示次之、替代方案末位。
     * 资源里不再带前导换行，行与行由 UI 层用 `\n` 连接。
     */
    private fun hintPartsOf(
        verdict: String,
        dose: String?,
        risk: String?,
        cervical: Boolean,
        cervicalCondition: String,
        alt: String?,
    ): List<TextPart> = buildList {
        add(mainHintPart(verdict, dose, risk))
        if (cervical && cervicalCondition in CERVICAL_HINT_CONDITIONS) {
            add(cervicalHintPart(risk))
        }
        if (verdict == "block" || verdict == "advise_against") {
            alt?.let { add(TextPart(R.string.ui_exercise_hint_alternative, listOf(it))) }
        }
    }

    /** 主提示（判定 → 行动）：`block`（含未知判定）走「已拦截」，风险原因可选。 */
    private fun mainHintPart(verdict: String, dose: String?, risk: String?): TextPart = when (verdict) {
        "recommend" -> TextPart(R.string.ui_exercise_hint_recommend, listOf(dose.orEmpty()))
        "allow" -> TextPart(R.string.ui_exercise_hint_allow, listOf(dose.orEmpty()))
        "downgrade" -> TextPart(R.string.ui_exercise_hint_downgrade, listOf(dose.orEmpty()))
        "conditional" -> TextPart(R.string.ui_exercise_hint_conditional, listOf(dose.orEmpty()))
        "pause" -> TextPart(R.string.ui_exercise_hint_pause)
        "advise_against" -> TextPart(R.string.ui_exercise_hint_advise_against)
        else -> risk?.let { TextPart(R.string.ui_exercise_hint_blocked, listOf(it)) }
            ?: TextPart(R.string.ui_exercise_hint_blocked_default)
    }

    /** 颈椎受累追加提示（风险原因可选）。 */
    private fun cervicalHintPart(risk: String?): TextPart =
        risk?.let { TextPart(R.string.ui_exercise_hint_cervical, listOf(it)) }
            ?: TextPart(R.string.ui_exercise_hint_cervical_default)

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

    /**
     * 需要在提示里点明「颈椎受累」的条件键。
     *
     * v1.0.77（批次 4）：补上种子实际在用的 `always` / `amplitude_half`（此前被静默忽略）；
     * 写成集合而不是一串 `||`，是为了让判定可读、也避免触发 detekt 的 ComplexCondition。
     */
    private val CERVICAL_HINT_CONDITIONS = setOf(
        "cervical_only", "breaststroke_block", "pose_filter",
        "always", "amplitude_half", // v1.0.77：种子在用、此前被静默忽略的两个键
    )

    /** R21 / exc-010 判读：worse → 建议下次减量；区分肌肉酸痛与炎症加重。v1.2.6：返回资源 id。 */
    @StringRes
    fun interpretFeedback(
        painChange: String?,
        stiffnessChange: String?,
        isMuscleSoreness: Boolean?,
    ): Int = when {
        painChange == "worse" && isMuscleSoreness == true -> R.string.ui_exercise_feedback_soreness
        painChange == "worse" -> R.string.ui_exercise_feedback_worse
        stiffnessChange == "worse" && isMuscleSoreness != true -> R.string.ui_exercise_feedback_stiffness
        painChange == "better" || stiffnessChange == "better" -> R.string.ui_exercise_feedback_better
        else -> R.string.ui_exercise_feedback_stable
    }
}
