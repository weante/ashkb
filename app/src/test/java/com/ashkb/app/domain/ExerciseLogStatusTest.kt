package com.ashkb.app.domain

import com.ashkb.app.data.entity.ExerciseLog
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.2.10（审查 §6.5）：运动打卡状态判据的**唯一实现**回归锁。
 *
 * ### 本批改了什么、为什么必须锁
 * 改前「这条运动记录算不算完成」在全仓有四处各写一遍：`ReportRepository` 的
 * `count { it.status == "done" }` 与 `count { it.status != "done" }`、`ExercisePlanProgress`
 * 的 `filter { it.status == "done" }`、`ExerciseLogDao.pendingFeedback` 写死在 SQL 里的
 * `status = 'done'`。本批全部改走 [ExerciseLogStatus]。
 *
 * 要锁的是三件事：
 * ① **`status != "done"` 不是「跳过」判据**。它把认不出的状态报成「用户主动跳过」，
 *    「跳过 N 次」会在用户什么都没做的情况下自己长大。报表的跳过数只认 [ExerciseLogStatus.SKIPPED]。
 * ② **完成天数 / 时长口径不能变**：老数据（只有 done）的数字必须与改动前逐位一致。
 * ③ **认不出的状态两边都不算**（既不算完成也不算跳过），但仍如实留在记录流里。
 *
 * ### 为什么这里只测纯函数
 * 「报表调的是这个判据」由调用点保证（`ReportRepository` overview / periodicReport 两处
 * 各一行 `ExerciseLogStatus.isDone(…)` / `.isSkipped(…)`）；`ExerciseLogStatus` 本身无
 * Android 依赖，所以判据能在这里逐条钉死。
 */
class ExerciseLogStatusTest {

    // ---- 判据本身 ----

    /** 今天写入侧只产生 done，所以「算练了」的集合就是它一个。 */
    @Test
    fun `完成集合只认 done`() {
        assertEquals(setOf("done"), ExerciseLogStatus.COMPLETED)
        assertTrue(ExerciseLogStatus.isDone(AdherenceCalc.DONE))
    }

    /** 跳过是用户明确交代过的状态，报表的「跳过」计数只认它。 */
    @Test
    fun `跳过集合只认 skipped`() {
        assertEquals(setOf("skipped"), ExerciseLogStatus.SKIPPED)
        assertTrue(ExerciseLogStatus.isSkipped(AdherenceCalc.SKIPPED))
    }

    /**
     * **回归锁**：`status != "done"` 的旧写法会把下面这些值全算成「跳过」。
     * 新判据下它们两边都不算——报表的跳过数不再被脏值 / 未来状态顶大。
     */
    @Test
    fun `认不出的状态既不算完成也不算跳过`() {
        for (mystery in listOf("partial", "mystery", "", "DONE", "Done", " done", "done ")) {
            assertFalse("$mystery 不该算练了", ExerciseLogStatus.isDone(mystery))
            assertFalse("$mystery 不该算跳过", ExerciseLogStatus.isSkipped(mystery))
        }
    }

    /** 两个集合不相交：一条记录不可能既算完成又算跳过。 */
    @Test
    fun `完成与跳过互斥`() {
        assertTrue((ExerciseLogStatus.COMPLETED intersect ExerciseLogStatus.SKIPPED).isEmpty())
        for (s in ExerciseLogStatus.COMPLETED) assertFalse(ExerciseLogStatus.isSkipped(s))
        for (s in ExerciseLogStatus.SKIPPED) assertFalse(ExerciseLogStatus.isDone(s))
    }

    // ---- 与消费方口径的一致性 ----

    /**
     * 报表 overview 的三段计数（改后的写法照抄一遍）：done / skipped / 时长。
     * 旧写法 `exSkipped = count { it.status != "done" }` 会给出 3（把 partial 与 mystery 算进去），
     * 新写法给 1。
     */
    @Test
    fun `报表跳过计数不再吞掉非 skipped 的取值`() {
        val logs = listOf(
            log("2026-10-01", AdherenceCalc.DONE),
            log("2026-10-02", AdherenceCalc.SKIPPED),
            log("2026-10-03", AdherenceCalc.PARTIAL),
            log("2026-10-04", "mystery"),
        )

        val done = logs.filter { ExerciseLogStatus.isDone(it.status) }
        val skipped = logs.count { ExerciseLogStatus.isSkipped(it.status) }
        val minutes = done.sumOf { it.durationMin ?: 0 }

        assertEquals("完成条数只认 done", 1, done.size)
        assertEquals("跳过条数只认 skipped（旧写法会给 3）", 1, skipped)
        assertEquals("时长只累加完成的行", 30, minutes)
    }

    /** 计划进度（`ExercisePlanProgress`）的完成日期判据同样是 isDone，partial 那天不算练过。 */
    @Test
    fun `计划进度只把 done 的日期算进完成`() {
        val spec = listOf(ExercisePlanTemplates.WeekSpec(1, "L1", 3, ""))
        val plan = com.ashkb.app.data.entity.ExercisePlan(
            id = "eplan-1", title = "测试计划", weeks = 1, stageMode = "any",
            weekStructure = "[]", isActive = true, isSeed = true, startDate = "2026-10-01",
            createdAt = "2026-10-01T00:00:00", updatedAt = "2026-10-01T00:00:00",
        )
        val logs = listOf(
            log("2026-10-01", AdherenceCalc.DONE),
            log("2026-10-02", AdherenceCalc.PARTIAL),
            log("2026-10-03", AdherenceCalc.SKIPPED),
            log("2026-10-04", "mystery"),
        )

        val p = ExercisePlanProgress.of(plan, spec, logs, LocalDate.parse("2026-10-04"))
        assertEquals("只有 done 那天算完成", 1, p.overallDoneDays)
    }

    // ---- 夹具 ----

    private fun log(date: String, status: String) = ExerciseLog(
        id = "elog-$date", date = date, recordedAt = "${date}T08:00:00",
        excKey = "exc-001", excName = "静态拉伸", status = status, durationMin = 30,
    )
}
