package com.ashkb.app.domain

/**
 * v1.2.10（审查 §6.5）：运动打卡状态的**唯一判据**。
 *
 * ### 为什么这也要有一个「唯一实现」
 * 抽这个对象之前，「这条运动记录算不算完成」在全仓有四处各写一遍：
 * `ReportRepository` 的 `count { it.status == "done" }` 与 `count { it.status != "done" }`、
 * `ExercisePlanProgress` 的 `filter { it.status == "done" }`、以及 [com.ashkb.app.data.db.ExerciseLogDao.pendingFeedback]
 * 写死在 SQL 里的 `status = 'done'`。同一份语义写在四处，与 `AdherenceCalc` 顶部记的
 * 「费率此前在本文件内联 4 遍，必然漂移」是同一个病。
 *
 * 其中 `status != "done"` 尤其危险：它不是「跳过」的判据，而是**「不是完成」的判据**——
 * 认不出的状态（旧备份 / 未来新增 / 手工导入的脏值）会被当成用户「主动跳过」报进报表，
 * 「跳过 N 次」于是会在用户什么都没做的情况下自己长大。
 *
 * ### 判据
 * · [COMPLETED]：算作「练了」的状态。今天写入侧只有 `ExerciseViewModel.checkIn` 写 `done`
 *   （[com.ashkb.app.data.entity.ExerciseLog.status] 的注释声明了三态，但 partial / skipped
 *   目前没有任何写入路径）；将来真出现 partial 的写入路径时，**改这一处**就能让报表、
 *   计划进度、待反馈查询三边同时跟上。
 * · [SKIPPED]：用户明确跳过（已有交代）的状态——报表的「跳过」计数只认它。
 * · 其余取值（含 partial 与认不出的值）：两边都不算，与报表补剂侧「先过滤到已结算再计数」
 *   同构；它们仍会按原样显示在打卡流里，不会被抹掉。
 *
 * 纯 Kotlin（无 Android / Compose 依赖），可直接单测。
 */
internal object ExerciseLogStatus {

    /** 算作「练了」的状态（唯一来源）。 */
    val COMPLETED: Set<String> = setOf(AdherenceCalc.DONE)

    /** 用户明确跳过的状态（唯一来源）——报表的「跳过」计数只认它。 */
    val SKIPPED: Set<String> = setOf(AdherenceCalc.SKIPPED)

    /** 该状态是否算「练了」。 */
    fun isDone(status: String): Boolean = status in COMPLETED

    /** 该状态是否是用户明确跳过。 */
    fun isSkipped(status: String): Boolean = status in SKIPPED
}
