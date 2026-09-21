package com.ashkb.app.domain

import com.ashkb.app.data.entity.KbEntry

/**
 * N3（v1.0.44）：知识库种子的**增量刷新**判定。
 *
 * 背景：旧实现用 `if (dao.count() > 0) return` 决定「是否导入种子」——库里只要有任意一条就
 * 整体跳过，于是**早于首次导入的设备此后永远拿不到种子增补与内容修订**，且完全静默。
 * 这是结构性数据缺口（v1.0.20 以来知识条目多次扩充），不是偶发 bug。
 *
 * 现在的语义（由 [plan] 统一裁决，导入侧只负责执行）：
 *  - 本机缺失的种子 → [Plan.fresh]，补入（固定 id + INSERT IGNORE，幂等）；
 *  - 本机已有但内容已修订的 → [Plan.revised]，只更新**种子列**，
 *    并把 `userNote` 从旧行拷回——个人备注层（v10 双层结构的第二层）永不被种子更新覆盖。
 *
 * 放在 domain 层的原因：这是纯函数判定，必须可被 JVM 单测覆盖
 * （导入动作本身依赖 Android Context，测不到；判定逻辑必须测到）。
 */
object KbSeedRefresh {

    data class Plan(
        /** 本机没有、需要补入的种子条目 */
        val fresh: List<KbEntry>,
        /** 本机已有但内容已修订、需要更新种子列的条目（`userNote` 已从旧行拷回） */
        val revised: List<KbEntry>,
    ) {
        val isEmpty: Boolean get() = fresh.isEmpty() && revised.isEmpty()
    }

    /**
     * @param existing 本机现有条目，按 id 索引
     * @param seeds 种子包解析结果
     */
    fun plan(existing: Map<String, KbEntry>, seeds: List<KbEntry>): Plan {
        val fresh = mutableListOf<KbEntry>()
        val revised = mutableListOf<KbEntry>()
        for (seed in seeds) {
            val old = existing[seed.id]
            when {
                old == null -> fresh += seed
                isRevised(old, seed) -> revised += seed.copy(userNote = old.userNote)
            }
        }
        return Plan(fresh, revised)
    }

    /**
     * 种子内容是否已修订。
     *
     * 既看条目自带的 `version`，也做**字段级比对**——后者是安全网：改了种子文案却忘记 bump
     * `version` 时仍能刷新。这类遗漏靠纪律无法避免（v1.0.39–42 的崩溃链已经证明过），
     * 所以判定必须建立在「内容本身」而不是「版本号是否被记得改」上。
     *
     * 刻意**不比对 `userNote`**：那是用户数据，不是种子内容，种子无权据此判定「已修订」。
     */
    fun isRevised(old: KbEntry, new: KbEntry): Boolean =
        old.version < new.version ||
            old.category != new.category ||
            old.title != new.title ||
            old.summary != new.summary ||
            old.severityLevel != new.severityLevel ||
            old.applicableScene != new.applicableScene ||
            old.sourceName != new.sourceName ||
            old.sourceUrl != new.sourceUrl ||
            old.sourceTier != new.sourceTier ||
            old.adaptedAt != new.adaptedAt ||
            old.reviewDue != new.reviewDue ||
            old.payload != new.payload ||
            // 顺带修复「search_text 未回填」的历史行（旧备份恢复后可能为 NULL）
            old.searchText != new.searchText
}
