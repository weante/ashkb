package com.ashkb.app.domain

import com.ashkb.app.R
import com.ashkb.app.data.entity.SupplementLog
import com.ashkb.app.ui.theme.StatusTone

/**
 * v1.0.87（批次 12）：补剂卡片的**今日打卡态**判据与状态文案映射——唯一实现。
 *
 * ### 为什么这也要有一个「唯一实现」
 * 补剂卡片（`ui/wellness` 的档案列表行）与补剂详情弹层的历史列表都要显示同一个词
 * （「已服」/「跳过」）。若各写一处 `if (status == done) … else …`，两处迟早对不上：
 * 用户刚在卡片上看到「跳过」，翻历史却是「已服」——这正是「同一指标写在多处必然漂移」
 * 的老问题（参见 [AdherenceCalc] 顶部注释里费率被内联 4 遍的教训）。
 *
 * ### 为什么状态判据认 [AdherenceCalc.SETTLED_STATUSES]
 * 依从率（`ReportRepository`）与历史流（`SupplementLogDao.observeHistoryFor`）都以这一份定义为准：
 * done / partial / skipped 都算「已经有了交代」。本判据跟着它走，于是「卡片上有胶囊」
 * 「历史里有这行」「依从率把它算进分母」三者永远同时成立——三处任何一处单独改口径，
 * 用户数出来的条数就会对不上。
 *
 * 纯 Kotlin（无 Compose 运行时依赖，只引用资源 id），可直接单测。
 */
internal object SupplementLogStatus {

    /**
     * 今天这条补剂的状态；`null` = 今天还没记录（卡片上给「打卡 / 跳过」两个入口）。
     *
     * 同一天可能出现多行（补剂打卡的 `slot_key` 恒为 NULL，SQLite 里 NULL 互不相等；
     * 先跳过、后补记已服就会留下两行）。此时取 `recordedAt` 最大的那一条：**用户最后一次
     * 表态才算数**。撤销入口会清掉当天的全部行，不存在删不干净的残留。
     */
    fun loggedToday(logs: List<SupplementLog>, supId: String?): String? {
        if (supId == null) return null
        return logs.asSequence()
            .filter { it.supId == supId && it.status in AdherenceCalc.SETTLED_STATUSES }
            .maxByOrNull { it.recordedAt }
            ?.status
    }

    /**
     * v1.1.1：**按「天 × 补剂」去重**——同一天同一补剂只保留 `recordedAt` 最大的那一行。
     *
     * 为什么报表侧需要它（HIGH-1 的后半段）：
     *  · 旧版本的补剂打卡不带 `slot_key`，而 `SupplementLogDao.find` 当时用 `slot_key = :slotKey`
     *    比较（`NULL = NULL` 不为真）→ 幂等守卫恒失效，**连点几次就留下几行**。写侧已在本批次
     *    改成 `IS`（不再新增重复行），但**用户库里已经写进去的重复行不会自己消失**；
     *  · 同一天"先跳过、后补记已服"（或反过来）本来就会留下两行，而卡片只显示最后一条。
     * 依从率若继续按行计数，同一天会被算两次（一行 done + 一行 skipped → 报表显示约 50%），
     * 与卡片上那一个胶囊互相矛盾——同一个指标两处口径，必然漂移。
     *
     * 去重规则与 [loggedToday] **逐字一致**（`recordedAt` 最大者胜；`recordedAt` 相同则取先遇到的，
     * 此时两行本就是同一秒内的重复点击）。身份键用 `supId`，为空（手工导入的快照行）时退回
     * `supName`——快照行没有 sup_id，不退回会把同一天不同补剂的快照行错误合并成一条。
     *
     * 返回顺序未定义（只用于计数），调用方不要依赖它。
     */
    fun latestPerDay(logs: List<SupplementLog>): List<SupplementLog> =
        logs.groupBy { it.date to (it.supId ?: it.supName) }
            .values
            .mapNotNull { rows -> rows.maxByOrNull { it.recordedAt } }

    /**
     * 状态 → 文案资源。未知状态按「已服」兜底，与药品历史（`MedsScreen.statusLabel`）**逐字一致**：
     * 存库值只产生三态，未知值只可能来自手工导入的老数据，把它显示成空白或「未知」对用户毫无帮助。
     */
    fun labelRes(status: String): Int = when (status) {
        AdherenceCalc.SKIPPED -> R.string.med_history_status_skipped
        AdherenceCalc.PARTIAL -> R.string.med_history_status_partial
        else -> R.string.med_history_status_done
    }

    /**
     * 状态 → 胶囊色调。与药品侧同一套约定：
     *  · 已服 = Success（做成了的事）；
     *  · 部分 = Warning（没做全，值得看一眼）；
     *  · 跳过 = **Neutral 而不是 Danger**——用户明确决定不吃，不是错误，不该报警。
     */
    fun tone(status: String): StatusTone = when (status) {
        AdherenceCalc.SKIPPED -> StatusTone.Neutral
        AdherenceCalc.PARTIAL -> StatusTone.Warning
        else -> StatusTone.Success
    }
}
