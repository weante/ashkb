package com.ashkb.app.domain

import com.ashkb.app.data.entity.SupplementLog
import java.time.LocalDate

/**
 * v1.0.81（批次 7）：补剂详情弹层里「最近服用记录」的取数规则（纯函数、可纯单测）。
 *
 * **为什么是「近 90 天」+「最多 30 行」两条一起卡**：
 *  · 90 天：与既有的 U3 历史窗口、列表行提示语「点击补剂可查看近 90 天服用记录」保持一致。
 *    补剂是按天吃的，一个季度足够回答「我最近到底吃没吃、哪天漏了」。
 *  · 30 行：弹层走 Column + verticalScroll（**没有虚拟化**，所有行都会被组合出来），
 *    而补剂打卡在连点时会于同一天留下多行（`slot_key` 为 NULL，SQLite 里 NULL 互不相等）。
 *    不设上限时，一个吃了半年的补剂会一次性组合出上百行。30 行约一屏半，够看也够删。
 *
 * **上限只影响「列出多少行」，不影响头部「已服 N 次」的 N**：N 始终是窗口内的真实条数，
 * 被截断时另给一行说明（[isTruncated]）——不能让用户以为「我一共只吃了 30 次」。
 */
object SupplementHistory {

    /** 窗口长度（天），与 SQL 的 `date >= :fromDate` 配套。 */
    const val WINDOW_DAYS = 90L

    /** 详情里最多列出的行数；超出部分只在头部计数与截断说明里体现。 */
    const val MAX_ROWS = 30

    /** 窗口起点（含）：`today - 90 天`。 */
    fun fromDate(today: LocalDate): String = today.minusDays(WINDOW_DAYS).toString()

    /**
     * 详情里实际列出的行。
     * 列表已由 SQL 按「日期倒序、同日按记录时间倒序」返回，这里只截断、**不再排序**——
     * 再排一次会让「最近」的定义出现第二份实现，两份迟早不一致。
     */
    fun visible(logs: List<SupplementLog>): List<SupplementLog> = logs.take(MAX_ROWS)

    /** 窗口内的记录是否多到列不下（判据是真实条数，不是截断后的行数）。 */
    fun isTruncated(total: Int): Boolean = total > MAX_ROWS
}
