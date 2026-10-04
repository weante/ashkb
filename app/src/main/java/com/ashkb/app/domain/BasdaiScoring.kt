package com.ashkb.app.domain

/**
 * v1.1.3（批次 19 · J-6）：**BASDAI 必须完整施测 6 题才计分**。
 *
 * ### 改前是什么样、为什么是错的
 * 旧门槛是「至少答 1 题」，未答的题按 `0` 计入总分（`SymptomDialogs` 里 `q1 ?: 0` …）。
 * 于是只答 Q1=8 的人会得到 **1.6 分**——一个**看起来合法、实际不成立**的 BASDAI：
 * 真实含义是「Q2–Q6 全无症状」，而患者只是没答。它照样进趋势图、进 PDF 报告，
 * 并且能触发 `edu-th-002` 的 `basdai_high` 复诊提示。
 *
 * ⚠️ 「0 = 无症状」在 BASDAI 里是真的，但**「没答」不等于「答了 0」**——
 * 量表施测规范要求六题齐全，这是**方法学**要求，不是本 App 的口味问题。
 * 两种语义在数据层被压成同一个 `0`，是这条缺陷的根因。
 *
 * ### 为什么放在 domain 而不是只改 UI
 * 「能否计分」是一条**纯判定**，不依赖 Context。把它收在这里的理由是：
 * 将来若再加入口（补录、历史导入、语音录入），判定只有一处，
 * 不会出现「UI 拦住了、另一个入口没拦住」——那正是这类缺陷最常见的复发方式。
 *
 * **不产出总分 ≠ 丢弃作答**：本对象只回答「能不能计分」，
 * 答了哪几题、每题多少仍由 [com.ashkb.app.data.entity.BasdaiRecord] 原样保存。
 */
object BasdaiScoring {

    /** BASDAI 的题目数。少一题即不完整施测。 */
    const val QUESTION_COUNT = 6

    /**
     * 已作答的题数（`null` = 未作答）。
     *
     * 单独暴露是因为弹窗要显示「已作答 n/6」，而判定与显示必须用同一个口径。
     */
    fun answeredCount(answers: List<Int?>): Int = answers.count { it != null }

    /**
     * 六题是否**全部**作答——只有全部作答才允许产出总分。
     *
     * ⚠️ 刻意不设「答满 N 题即可」这类折中：折中的阈值同样是替量表定规则，
     * 且患者不会知道自己拿到的是一个残缺分数。宁可不出分。
     */
    fun isComplete(answers: List<Int?>): Boolean =
        answers.size == QUESTION_COUNT && answers.all { it != null }
}
