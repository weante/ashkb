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
