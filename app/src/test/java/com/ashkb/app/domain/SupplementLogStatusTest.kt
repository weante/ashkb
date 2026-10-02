package com.ashkb.app.domain

import com.ashkb.app.R
import com.ashkb.app.data.entity.SupplementLog
import com.ashkb.app.ui.theme.StatusTone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * v1.0.87（批次 12）：补剂卡片与详情弹层的**状态判据 / 文案映射**（[SupplementLogStatus]）。
 *
 * 为什么值得单独锁：批次 12 让补剂能写 `skipped`，于是「今天这条补剂算不算已经有交代」
 * 这个问题第一次有了两个可能的答案（已服 / 跳过），而它同时被三处依赖：
 *  ① 卡片右上角显示哪个胶囊；
 *  ② 卡片上给的是「打卡 / 跳过」两个入口还是「撤销」；
 *  ③ 历史流里那一行显示哪个词、什么颜色。
 * 三者任何一处单独改口径，用户就会看到「卡片说跳过、历史说已服」这类自相矛盾。
 *
 * 判据认的是 [AdherenceCalc.SETTLED_STATUSES]——与依从率（`ReportRepository`）和
 * 历史流（`SupplementLogDao.observeHistoryFor`）**同一份**定义，见对象注释。
 */
class SupplementLogStatusTest {

    // ---- loggedToday：卡片该显示什么 ----

    @Test
    fun `今天没有记录时返回 null`() {
        assertNull(SupplementLogStatus.loggedToday(emptyList(), "sup-1"))
    }

    @Test
    fun `按补剂 id 判定并返回状态词`() {
        val logs = listOf(log("slog-1", "sup-1", "done", "2026-10-02T08:00:00"))

        assertEquals("sup-1 今天已有交代，状态词要原样返回给卡片", "done", SupplementLogStatus.loggedToday(logs, "sup-1"))
        assertNull("别的补剂的记录不能挂到它头上", SupplementLogStatus.loggedToday(logs, "sup-2"))
    }

    /** `sup_id` 为空的快照行（补剂真删后按名称兜底的那些）不属于任何在档补剂。 */
    @Test
    fun `sup_id 为 null 时不判定`() {
        val logs = listOf(log("slog-1", null, "done", "2026-10-02T08:00:00"))

        assertNull(SupplementLogStatus.loggedToday(logs, null))
    }

    @Test
    fun `已服与跳过都算今天已有交代`() {
        assertEquals(
            "done",
            SupplementLogStatus.loggedToday(listOf(log("slog-1", "sup-1", "done", "2026-10-02T08:00:00")), "sup-1"),
        )
        assertEquals(
            "skipped",
            SupplementLogStatus.loggedToday(listOf(log("slog-1", "sup-1", "skipped", "2026-10-02T08:00:00")), "sup-1"),
        )
    }

    /** 未知状态既不是已服也不是跳过：卡片必须仍给两个入口，否则用户点不进任何记录。 */
    @Test
    fun `未知状态不视为已记录`() {
        val logs = listOf(log("slog-1", "sup-1", "mystery", "2026-10-02T08:00:00"))

        assertNull(SupplementLogStatus.loggedToday(logs, "sup-1"))
    }

    /**
     * 同一天多行时取**最近记录**。
     *
     * 这不是理论情形：补剂打卡的 `slot_key` 恒为 NULL，而 SQLite 里 NULL 互不相等，
     * 所以先点跳过、再点打卡就会留下两行。用户最后一次表态才算数——否则卡片会一直
     * 显示几分钟前的旧状态，他会以为刚才那一下没生效。
     */
    @Test
    fun `同一天多行时以最后一次记录为准`() {
        val logs = listOf(
            log("slog-old", "sup-1", "skipped", "2026-10-02T08:00:00"),
            log("slog-new", "sup-1", "done", "2026-10-02T20:00:00"),
        )

        assertEquals("done", SupplementLogStatus.loggedToday(logs, "sup-1"))
    }

    // ---- 文案与色调映射（与药品侧同一套词） ----

    @Test
    fun `已服映射到已服文案与成功色`() {
        assertEquals(R.string.med_history_status_done, SupplementLogStatus.labelRes("done"))
        assertEquals(StatusTone.Success, SupplementLogStatus.tone("done"))
    }

    @Test
    fun `跳过映射到跳过文案与中性色`() {
        assertEquals(R.string.med_history_status_skipped, SupplementLogStatus.labelRes("skipped"))
        // 中性色而非红色：用户明确决定不吃，不是错误，不该报警（与药品卡 medStatusOf 同款）
        assertEquals(StatusTone.Neutral, SupplementLogStatus.tone("skipped"))
    }

    @Test
    fun `部分映射到部分文案与警示色`() {
        assertEquals(R.string.med_history_status_partial, SupplementLogStatus.labelRes("partial"))
        assertEquals(StatusTone.Warning, SupplementLogStatus.tone("partial"))
    }

    /** 两个不同的状态词必须落在**不同**的文案上——写反了用户就看不出自己按的是哪个。 */
    @Test
    fun `已服与跳过不是同一句话`() {
        assertNotEquals(SupplementLogStatus.labelRes("done"), SupplementLogStatus.labelRes("skipped"))
    }

    /** 未知状态按「已服」兜底（与 `MedsScreen.statusLabel` 的既有兜底逐字一致）。 */
    @Test
    fun `未知状态按已服兜底`() {
        assertEquals(R.string.med_history_status_done, SupplementLogStatus.labelRes("mystery"))
        assertEquals(StatusTone.Success, SupplementLogStatus.tone("mystery"))
    }

    // ---- 夹具 ----

    private fun log(id: String, supId: String?, status: String, recordedAt: String) = SupplementLog(
        id = id, date = recordedAt.take(10), recordedAt = recordedAt,
        supId = supId, supKey = supId ?: "snapshot", supName = "测试补剂",
        doseSnapshot = "1 粒", status = status,
        // 与写入侧一致（WellnessViewModel.checkInSupplement）：只有 done 才有服用时刻
        takenAt = if (status == AdherenceCalc.DONE) recordedAt else null,
    )
}
