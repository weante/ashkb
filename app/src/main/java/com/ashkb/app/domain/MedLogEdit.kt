package com.ashkb.app.domain

/**
 * v1.0.49：手动修正一条用药记录时的不变量。
 *
 * 与写入侧（[com.ashkb.app.data.repo.MedicationRepository.checkIn] / `skip`）保持同一口径：
 *
 * - **已服（done）**：不保留原因（原因只对「没吃成」有意义），必须留有服用时刻；
 * - **部分 / 跳过（partial / skipped）**：必须有原因（红线三：跳过要如实记录），不保留服用时刻；
 * - **注射部位**只在「已服」时有意义——跳过的针次没有部位可言。
 *
 * 抽成纯函数而不是写在弹层里，是因为**编辑是最容易破坏数据不变量的入口**：
 * 用户可以把一条「跳过」改成「已服」，若此时不把原因清掉，报表里就会出现
 * 「已服 + 原因=遗忘」这种自相矛盾的行；反之若把「已服」改成「跳过」却不填原因，
 * 就会出现一条无原因的跳过。两者都会污染依从率口径。
 */
object MedLogEdit {

    /** 该状态是否必须填写原因 */
    fun needsReason(status: String): Boolean =
        status == AdherenceCalc.PARTIAL || status == AdherenceCalc.SKIPPED

    /** 规范化原因：需要原因时保留（空白视为未填），否则一律清空 */
    fun reasonFor(status: String, reason: String?): String? =
        if (needsReason(status)) reason?.takeIf { it.isNotBlank() } else null

    /** 规范化注射部位：仅「已服」保留（空白视为未填），其余状态清空 */
    fun injSiteFor(status: String, injSite: String?): String? =
        if (status == AdherenceCalc.DONE) injSite?.takeIf { it.isNotBlank() } else null

    /** 是否满足保存条件：需要原因的状态必须填了原因，否则会写出无原因的跳过/部分 */
    fun canSave(status: String, reason: String?): Boolean =
        !needsReason(status) || !reason.isNullOrBlank()
}
