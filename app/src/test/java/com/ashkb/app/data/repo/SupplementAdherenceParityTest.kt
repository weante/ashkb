package com.ashkb.app.data.repo

import com.ashkb.app.data.entity.SupplementLog
import com.ashkb.app.domain.AdherenceCalc
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * v1.0.87（批次 12）：补剂依从率的**口径回归锁**。
 *
 * ### 本批改了什么、为什么必须锁
 * 「跳过」此前在补剂侧**根本写不进库**（唯一调用点写死 `status = "done"`），所以报表里
 * 补剂的三个计数里有意义的只有 done；本批让卡片能写 skipped 之后，这个指标第一次会
 * 因为用户按下按钮而**下降**。要锁的东西有两件：
 *
 * ① **老数据（只有 done）的数字一个都不能变。** 现网库里没有一行 skipped / partial，
 *    过滤与分母都不该因本次改动而动。下面用「同一条 SQL 谓词 + 同一个公式」重算一遍，
 *    与改动前的数字逐位对齐；任何把未知状态混进分母、或把 skipped 挪出分母的改动都会失败。
 *
 * ② **跳过 = 已结算但未服用。** 它进分母（那剂确实被计划过 / 被交代过），**不进分子**，
 *    也**不算漏服**。这与药品侧 `AdherenceCalc.doseCompletion` 对 SKIPPED 的既有语义
 *    （`SKIPPED -> Unit // 已交代，不计完成也不计漏服`）**逐字一致**——补剂不与药品分家。
 *
 * ### 为什么这里只测纯算术
 * `ReportRepository.overview()` 需要真库 + IO；而本次改动的风险点是**口径**（谁进分母、
 * 谁进分子），口径全部落在 `AdherenceCalc.ratePct` 这一处（`ReportRepository` 的用药与
 * 补剂两侧都调它，见该文件 overview / periodicReport）。因此这里锁算术 + 谓词，
 * 「补剂走的是同一份公式」由 `ReportRepository` 的调用点保证（同一行就是 done/partial/
 * skipped 三个计数 + 一次 ratePct 调用）。
 */
class SupplementAdherenceParityTest {

    /**
     * 依从率 = (done + partial × 0.5) / (done + partial + skipped)。
     *
     * 分母里**含** skipped 是刻意的（与药品一致）：跳过是一剂被明确交代过的计划剂量，
     * 把它从分母里挖掉会让「天天跳过」显示成 100%。它不进分子，所以跳过会如实拉低完成度。
     */
    @Test
    fun `跳过进分母不进分子`() {
        // 8 已服 + 2 跳过 = 80%
        assertEquals(80, AdherenceCalc.ratePct(done = 8, partial = 0, total = 8 + 2))
        // 全部跳过 = 0%，而不是「无数据」
        assertEquals(0, AdherenceCalc.ratePct(done = 0, partial = 0, total = 5))
    }

    /**
     * **回归锁**：老数据（只有 done）的数字与改动前逐位一致。
     *
     * 改动前 `ReportRepository.overview()` 对补剂做的是：
     * ```
     * supDone = countBetweenStatus(f, t, "done"); supPartial = ...; supSkipped = ...  // 恒 0
     * supTotal = supDone + supPartial + supSkipped
     * supRate  = AdherenceCalc.ratePct(supDone, supPartial, supTotal)
     * ```
     * 老数据里 partial 与 skipped 都是 0，于是 `supRate == 100`（只要有记录）。
     * 本用例把这段旧代码**原样**跑一遍，和「新代码在只有 done 的数据上的结果」比对——
     * 两者必须相等。这样做而不是写死 `100`，是为了让断言本身也随旧公式走：
     * 若有人日后改了公式，这里会先暴露「老数据的数字变了」。
     */
    @Test
    fun `只有 done 的老数据结果与改动前一致`() {
        // 老数据：全库只有 done（条数取几个不同值，避免只覆盖 100% 这一个点）
        for (doneCount in intArrayOf(1, 3, 7, 30)) {
            val legacyLogs = List(doneCount) { log("slog-legacy-$it", AdherenceCalc.DONE) }

            // —— 改动前的实现（照抄 ReportRepository 的旧补剂分支）——
            val done = legacyLogs.count { it.status == "done" }
            val partial = legacyLogs.count { it.status == "partial" }
            val skipped = legacyLogs.count { it.status == "skipped" }
            val oldTotal = done + partial + skipped
            val oldRate = AdherenceCalc.ratePct(done, partial, oldTotal)

            // —— 改动后：状态过滤放宽为「已结算」，公式不变 ——
            val settled = legacyLogs.filter { it.status in AdherenceCalc.SETTLED_STATUSES }
            val summary = AdherenceCalc.completionByStatus(settled.map { it.status })

            assertEquals("done=$doneCount 时分母不能变", oldTotal, summary.total)
            assertEquals("done=$doneCount 时已完成数不能变", done, summary.done)
            assertEquals("done=$doneCount 时百分比不能变", oldRate, summary.ratePct)
            assertEquals("老数据里不该凭空冒出跳过", 0, summary.skipped)
        }
    }

    /**
     * **边界（刻意单列，不当成回归）**：老数据为**空**（一条记录都没有）时，旧实现给 `0`，
     * 而改动后的补剂侧在报表里仍走 `AdherenceCalc.ratePct`（同样给 0）——两者一致。
     *
     * 这里用 `completionByStatus` 的 `Completion.ratePct`（无记录时是 `null`，v1.0.76 批次 3a 的
     * 「无数据不编结论」口径）作对照：它**不是**补剂侧在用的那个入口，写在这里是为了说清
     * 「空数据这件事有两套表达」，避免下一个人误以为补剂口径也变成了 null。
     */
    @Test
    fun `空数据时补剂仍走数值口径给 0 而不是 null`() {
        val empty = emptyList<SupplementLog>()
        val settled = empty.filter { it.status in AdherenceCalc.SETTLED_STATUSES }

        // 报表里补剂用的就是这个（见 ReportRepository 的 supRate）
        assertEquals(0, AdherenceCalc.ratePct(done = 0, partial = 0, total = settled.size))
        // 而「记录内完成度」那一套对空数据给 null——两者是不同指标，别混用
        assertNull(AdherenceCalc.completionByStatus(settled.map { it.status }).ratePct)
    }

    /**
     * 老数据与新增的跳过混在一起时，跳过的效果**只是把分母撑大**——
     * 既不改动已有那些 done 行的完成数，也不会变成一条「漏服」。
     */
    @Test
    fun `新增跳过只稀释分母不动已完成数`() {
        val legacy = listOf(log("slog-1", AdherenceCalc.DONE), log("slog-2", AdherenceCalc.DONE))
        val withSkip = legacy + log("slog-3", AdherenceCalc.SKIPPED)

        val before = AdherenceCalc.completionByStatus(legacy.map { it.status })
        val after = AdherenceCalc.completionByStatus(withSkip.map { it.status })

        assertEquals("已完成数不该因为多了一条跳过而变化", before.done, after.done)
        assertEquals(2, before.total)
        assertEquals(3, after.total)
        assertEquals(100, before.ratePct)
        assertEquals(66, after.ratePct) // (2 + 0) / 3 = 66.67 → 取整 66
    }

    /**
     * 跳过**不算漏服**：漏服判定（`MissedDoses` / `PendingDoses`）认的就是这份已结算集合。
     * 这里直接锁集合本身——补剂侧与本用例共用同一个 `AdherenceCalc`，本批没有为补剂另造口径。
     */
    @Test
    fun `跳过属于已结算因而不算漏服`() {
        assertEquals(setOf("done", "partial", "skipped"), AdherenceCalc.SETTLED_STATUSES)
        assertEquals(true, AdherenceCalc.SKIPPED in AdherenceCalc.SETTLED_STATUSES)
        // 反例：未知状态**不在**已结算集合里 → 它才该被当成漏服（保守取值）
        assertEquals(false, "mystery" in AdherenceCalc.SETTLED_STATUSES)
    }

    // ---- 夹具 ----

    private fun log(id: String, status: String) = SupplementLog(
        id = id, date = "2026-10-01", recordedAt = "2026-10-01T08:00:00",
        supId = "sup-a", supKey = "sup-a", supName = "测试补剂",
        doseSnapshot = "1 粒", status = status,
        takenAt = if (status == AdherenceCalc.DONE) "2026-10-01T08:00:00" else null,
    )
}
