package com.ashkb.app.domain

import java.time.LocalDate

/**
 * R2：顺延（[com.ashkb.app.data.repo.MedicationRepository.postponeInjection]）留下的
 * **存量幽灵计划槽位**——判定区间的那一小块纯逻辑。
 *
 * ## 幽灵是怎么产生的
 *
 * 顺延把注射锚点（`medications.start_date`）从「今天」挪到 [effectiveDate]。旧锚点在
 * 物化窗口（今天-1 .. 今天+7）里已经写过若干行 `planned_slots`，而物化例程是
 * `INSERT OR IGNORE`——**只加不删**。v1.1.1 之前顺延只改 `startDate`、不清行，于是那些
 * 旧锚点的行永久留在表里：新锚点算不出它们（`isInjectionDay` 要求 `days >= 0`），
 * 它们也不会被覆盖，却会被 `AdherenceCalc.doseCompletion` 配上一个永远不会存在的日志 →
 * **该剂量在其落入的每个报表窗口里都被永久计为漏服**，`MissedDoses.unsettled` 还会每天提示。
 *
 * v1.1.1 补上了 `deleteOfMedFrom(medId, today)`（只删今天起），所以**新增**已经防住；
 * 但升级前就已顺延过的用户，那些行日期如今都成了过去，`deleteOfMedFrom` 永远碰不到它们。
 *
 * ## 判据：为什么是 `[R, D)` 半开区间
 *
 * 对每条顺延审计行，取操作发生日 `R = recordedAt 的日期`、生效日 `D = effectiveDate`：
 *
 * - `date < R`：顺延**之前**的真实历史计划，当时确实该打 → 保留；
 * - `date >= D`：新锚点的领地，物化会（重新）写 → 不该由清理动；
 * - `[R, D)`：**用户明确决定「这一剂往后推」**的那些日期，按新锚点算 `days < 0`
 *   根本不该有槽位 → 正是幽灵。
 *
 * 典型情形「今天 → 明天」时 `D = R + 1`，区间只有 `R` 当天，窗口极窄、误删风险最低。
 *
 * ## 为什么不按「当前锚点重算对不上」判
 *
 * 那个判据会连**正常改时刻 / 改频次**留下的历史行一起误杀——它们与当前锚点同样对不上，
 * 但都是当时真实发生过的计划。限定在「有顺延审计行」的药、且限定在 `[R, D)`，
 * 才既覆盖幽灵、又不碰真实历史。
 *
 * 纯函数、无 Android 依赖，可直接单测（见 `PostponeGhostSlotsTest`）。
 */
internal object PostponeGhostSlots {

    /**
     * 一条顺延审计行对应的幽灵区间 `[from, to)`；两条日期串。
     *
     * 返回 `null` 的情形（都表示「这条审计行不该触发清理」）：
     * - `recordedAt` 或 `effectiveDate` 解析不出日期（脏数据，宁可不动）；
     * - `D <= R`（顺延到当天或更早：区间为空，删了只会伤到真实行）。
     *
     * 取日期用 `take(10)` 而不是 `LocalDateTime.parse(...).toLocalDate()`：写入侧
     * [com.ashkb.app.data.repo.nowIso] 用的是 `ISO_LOCAL_DATE_TIME`，**纳秒非零时会追加
     * 小数秒**，但 ISO 8601 的日期永远在前 10 个字符——从头部截取是安全的（同文件里
     * `hhmm` 那条注释警告的是「从串尾截取」，方向相反）。
     */
    fun range(recordedAt: String, effectiveDate: String): Pair<String, String>? {
        val from = dateOf(recordedAt) ?: return null
        val to = dateOf(effectiveDate) ?: return null
        if (!to.isAfter(from)) return null
        return from.toString() to to.toString()
    }

    /** ISO 日期或日期时刻串 → `LocalDate`；解析不出返回 `null`。 */
    fun dateOf(iso: String): LocalDate? =
        if (iso.length < 10) null else runCatching { LocalDate.parse(iso.take(10)) }.getOrNull()
}
