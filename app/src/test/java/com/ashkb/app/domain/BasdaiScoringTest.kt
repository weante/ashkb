package com.ashkb.app.domain

import com.ashkb.app.data.entity.BasdaiRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.1.3（批次 19 · J-6）：**BASDAI 不足 6 题不产分**的回归锁。
 *
 * ### 改前的失效是可复现的，而且不报错
 * 旧门槛是「至少答 1 题」，未答的题按 `0` 计入总分。只答 Q1=8 会得到
 * `(8 + 0 + 0 + 0 + 0/2.0) / 5 = 1.6` 分——一个看起来完全合法的 BASDAI。
 * 它会进趋势图、进 PDF 报告，并能触发 `edu-th-002` 的复诊提示，而**没有任何一处报错**。
 *
 * ⚠️ 这条缺陷的危险不在于「算错了一个数」，在于「算出了一个不该存在的数」：
 * 量表方法学要求六题齐全，「没答」与「答 0」在数据层被压成同一个 `0`，无法事后分辨。
 */
class BasdaiScoringTest {

    @Test
    fun `六题全答才允许计分`() {
        assertTrue("6 题全答", BasdaiScoring.isComplete(listOf(0, 0, 0, 0, 0, 0)))
        assertTrue("含非零分同样是完整施测", BasdaiScoring.isComplete(listOf(8, 7, 6, 5, 4, 3)))
        // ⚠️ 全 0 必须是「完整」：BASDAI 里 0 是有意义的作答（无症状），不是「没答」
        assertTrue("全 0 是有效作答而不是未作答", BasdaiScoring.isComplete(listOf(0, 0, 0, 0, 0, 0)))
    }

    @Test
    fun `缺任意一题都不计分`() {
        for (missing in 0 until 6) {
            val answers = MutableList<Int?>(6) { 3 }
            answers[missing] = null
            assertFalse(
                "缺第 ${missing + 1} 题时不应计分",
                BasdaiScoring.isComplete(answers),
            )
        }
    }

    @Test
    fun `题目数不足六也算不完整（防将来加题或传错长度）`() {
        assertFalse("只传 5 题", BasdaiScoring.isComplete(listOf(1, 2, 3, 4, 5)))
        assertFalse("空列表", BasdaiScoring.isComplete(emptyList()))
        assertFalse("题数多于 6 同样不算标准施测", BasdaiScoring.isComplete(listOf(1, 1, 1, 1, 1, 1, 1)))
    }

    @Test
    fun `已作答题数与判定用同一口径`() {
        assertEquals(0, BasdaiScoring.answeredCount(listOf(null, null, null, null, null, null)))
        assertEquals(1, BasdaiScoring.answeredCount(listOf(8, null, null, null, null, null)))
        assertEquals(5, BasdaiScoring.answeredCount(listOf(1, 1, 1, 1, 1, null)))
        assertEquals(6, BasdaiScoring.answeredCount(listOf(0, 0, 0, 0, 0, 0)))
    }

    /**
     * 判据的正例：改前那个「只答 Q1=8 = 1.6 分」的场景，现在必须被拦住；
     * 同时确认公式本身没被顺手改掉（`(Q1+Q2+Q3+Q4+(Q5+Q6)/2)/5`）。
     */
    @Test
    fun `只答一题的场景被拦住且公式未变`() {
        val partial = listOf<Int?>(8, null, null, null, null, null)
        assertFalse("只答 Q1=8 必须被拦住（旧实现会算出 1.6 分）", BasdaiScoring.isComplete(partial))
        // 同一组答案补齐后，总分才是有意义的那个数
        val complete = listOf<Int?>(8, 0, 0, 0, 0, 0)
        assertTrue(BasdaiScoring.isComplete(complete))
        assertEquals(1.6, BasdaiRecord.total(8, 0, 0, 0, 0, 0), 1e-9)
    }
}
