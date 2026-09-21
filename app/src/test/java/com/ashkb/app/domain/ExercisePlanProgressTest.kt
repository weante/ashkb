package com.ashkb.app.domain

import com.ashkb.app.data.entity.ExerciseLog
import com.ashkb.app.data.entity.ExercisePlan
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** v1.0.39：B7 计划完成度反算（进度由 exercise_logs 去重日期推出，不另存）。 */
class ExercisePlanProgressTest {

    private fun plan(weeks: Int = 4, start: String? = "2026-09-01") = ExercisePlan(
        id = "eplan-1", title = "测试计划", weeks = weeks, stageMode = "any",
        weekStructure = "[]", isActive = true, isSeed = true, startDate = start,
        createdAt = "2026-09-01T00:00:00", updatedAt = "2026-09-01T00:00:00",
    )

    private fun log(date: String, status: String = "done") = ExerciseLog(
        id = "elog-$date", date = date, recordedAt = "${date}T08:00:00",
        excKey = "exc-001", excName = "静态拉伸", status = status,
    )

    private val spec = listOf(
        ExercisePlanTemplates.WeekSpec(1, "L1", 3, ""),
        ExercisePlanTemplates.WeekSpec(2, "L1", 4, ""),
        ExercisePlanTemplates.WeekSpec(3, "L1+L2", 4, ""),
        ExercisePlanTemplates.WeekSpec(4, "L2", 5, ""),
    )

    @Test
    fun `current week derived from start date`() {
        assertEquals(1, ExercisePlanProgress.of(plan(), spec, emptyList(), LocalDate.parse("2026-09-01")).currentWeek)
        assertEquals(1, ExercisePlanProgress.of(plan(), spec, emptyList(), LocalDate.parse("2026-09-07")).currentWeek)
        assertEquals(2, ExercisePlanProgress.of(plan(), spec, emptyList(), LocalDate.parse("2026-09-08")).currentWeek)
        assertEquals(3, ExercisePlanProgress.of(plan(), spec, emptyList(), LocalDate.parse("2026-09-15")).currentWeek)
    }

    /** 超出总周数：周次钳到末周，并标记已完成。 */
    @Test
    fun `week is clamped and finished flag set after total weeks`() {
        val p = ExercisePlanProgress.of(plan(weeks = 4), spec, emptyList(), LocalDate.parse("2026-12-01"))
        assertEquals(4, p.currentWeek)
        assertTrue(p.finished)
        assertFalse(ExercisePlanProgress.of(plan(), spec, emptyList(), LocalDate.parse("2026-09-10")).finished)
    }

    /** 同日多条只算一天；非 done 不计；周内与累计分别统计。 */
    @Test
    fun `counts distinct done days`() {
        val logs = listOf(
            log("2026-09-01"), log("2026-09-01"), // 同日重复
            log("2026-09-03"), log("2026-09-05"),
            log("2026-09-08", "skipped"),         // 非 done
        )
        val p = ExercisePlanProgress.of(plan(), spec, logs, LocalDate.parse("2026-09-01"))
        assertEquals(3, p.weekDoneDays)
        assertEquals(3, p.overallDoneDays)
        assertEquals(16, p.overallTargetDays)   // 3+4+4+5
        assertEquals(18, p.pct)                 // 3*100/16 = 18
        assertEquals(3, p.weekTargetDays)
    }

    /** 计划窗口外的日志不计入（换计划后旧记录不应污染新进度）。 */
    @Test
    fun `logs outside the window are ignored`() {
        val logs = listOf(log("2026-08-01"), log("2027-01-01"))
        val p = ExercisePlanProgress.of(plan(), spec, logs, LocalDate.parse("2026-09-01"))
        assertEquals(0, p.overallDoneDays)
        assertEquals(0, p.pct)
    }

    /** 未启用（startDate 为空）时按今天起算，不崩、不误判为已完成。 */
    @Test
    fun `no start date falls back to today`() {
        val p = ExercisePlanProgress.of(plan(start = null), spec, emptyList(), LocalDate.parse("2026-09-20"))
        assertEquals(1, p.currentWeek)
        assertFalse(p.finished)
        assertEquals(0, p.overallDoneDays)
    }

    /** 空 spec（脏数据）时目标为 0，百分比兜底为 0 而不是除零崩溃。 */
    @Test
    fun `empty spec yields zero target without crash`() {
        val p = ExercisePlanProgress.of(plan(), emptyList(), listOf(log("2026-09-01")), LocalDate.parse("2026-09-01"))
        assertEquals(0, p.overallTargetDays)
        assertEquals(0, p.pct)
        assertEquals(0, p.weekTargetDays)
    }
}
