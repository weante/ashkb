package com.ashkb.app.domain

import com.ashkb.app.data.entity.MedicationLog

/**
 * v1.2.10（审查第二批 M8）：药单详情弹层里「用药记录」的取数规则（纯函数、可纯单测）。
 *
 * **为什么要有行数上限**：弹层走 `Column` + `verticalScroll`（`SheetColumn`，**没有虚拟化**，
 * 所有行都会被组合出来）。窗口是 90 天（[MED_HISTORY_WINDOW_DAYS]），一天可以有多剂
 * （`BID` / `Q8H` 各 2 / 3 次），所以最坏情况一次组合近 270 行——每一行还都带状态胶囊、
 * 途径标签与修正入口。用户打开这一层的目的是「看看最近怎么样、顺手改一条记错的」，
 * 把 270 行全铺出来既不必要，也让弹层明显卡顿。
 *
 * **上限只影响「列出多少行」，不影响头部的完成度与次数**：`Completion` / `prnCount` / 计划口径
 * 全部仍按**窗口内全量记录**计算（调用方传原始 `logs` 给 `AdherenceCalc`，只把
 * [visible] 的结果交给 `DividerList`）。这不是实现细节而是产品要求——把被截断的
 * 样本喂给依从率，用户会看到一个凭空变好的百分比，那比不显示更糟。
 *
 * 与 [SupplementHistory] 的关系：同一条思路的两份实现，**刻意不合并**。两者的窗口与
 * 上限并不相同（补剂按天吃、一天可能连点出多行；药物有固定时刻数），合并意味着
 * 将来为其中一边调参就会顺手改掉另一边。两处都是纯函数，各自的常量由各自的单测钉住。
 */
object MedHistory {

    /**
     * 窗口长度（天）。与 [com.ashkb.app.data.repo.MedicationRepository.observeLogsForMed]
     * 的默认窗口、以及 `ui/me/MedsScreen.kt` 里 `MED_HISTORY_DAYS` 必须一致——
     * 那个常量是本文件的别名，改一处要同时改另一处（由单测钉住）。
     */
    const val MED_HISTORY_WINDOW_DAYS = 90

    /**
     * 详情里最多列出的行数。
     *
     * 60 行约两屏：按一天 3 剂算是 20 天，足够回答「最近这段有没有漏、哪条记错了」；
     * 再往前的内容由头部的真实计数与截断说明承担。
     */
    const val MAX_ROWS = 60

    /**
     * 列表实际渲染的行。
     *
     * 仓库层已按「日期倒序、同日按计划顺序」返回，这里**只截断、不重排**——
     * 再排一次就会让「最近」出现第二份定义，两份迟早不一致。
     */
    fun visible(logs: List<MedicationLog>): List<MedicationLog> = logs.take(MAX_ROWS)

    /**
     * 窗口内的记录是否多到列不下。
     *
     * 判据是**真实条数**（不是截断后的行数）：只有真实条数才能回答「是不是还有更早的没显示」。
     */
    fun isTruncated(total: Int): Boolean = total > MAX_ROWS
}
