package com.ashkb.app.data.entity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.2.10（审查 §6.9）：`fromKey` 必须区分「null」与「无法识别」。
 *
 * ### 为什么必须有这条测试
 * 全仓 15 个 `fromKey` 里 12 个是 fail-open（读不出来就退回某个默认值）。那个默认值
 * 在**排程侧**是刻意的——退回 DAILY 会变成天天提醒，但至少**看得见、改得动**；
 * 若改成 fail-closed，这支药会从今日页彻底消失，用户以为药没了。
 *
 * 但在**显示侧**，fail-open 会撒谎：急救卡把「读不出的频次」印成「每日」，
 * 而它可能是一支每两周一次的药——医生读到的是错的。
 *
 * 于是判据层要能同时回答两个问题：`fromKey`（要一个能用的值）与 `fromKeyOrNull`
 * （这个值到底读没读出来）。本测试锁的是后者，以及「脏值不会跨过显示侧」。
 */
class EnumFromKeyTest {

    // ======================= MedFrequency =======================

    @Test
    fun `MedFrequency fromKeyOrNull 对 null 与脏值都返回 null`() {
        assertNull(MedFrequency.fromKeyOrNull(null))
        assertNull(MedFrequency.fromKeyOrNull(""))
        assertNull(MedFrequency.fromKeyOrNull("Q7D"))
        assertNull(MedFrequency.fromKeyOrNull("每日"))
    }

    @Test
    fun `MedFrequency fromKeyOrNull 大小写不敏感且认得全部枚举项`() {
        MedFrequency.entries.forEach { f ->
            assertEquals(f, MedFrequency.fromKeyOrNull(f.name))
            assertEquals(f, MedFrequency.fromKeyOrNull(f.name.lowercase()))
        }
    }

    @Test
    fun `MedFrequency fromKey 保留 fail-open 退回每日`() {
        // 排程侧刻意如此：读不出就按每日出卡（看得见、能改），而不是让药消失。
        assertEquals(MedFrequency.DAILY, MedFrequency.fromKey(null))
        assertEquals(MedFrequency.DAILY, MedFrequency.fromKey("Q7D"))
        assertEquals(MedFrequency.DAILY, MedFrequency.fromKey(""))
    }

    @Test
    fun `labelResOrNull 只认能解析的值`() {
        assertNull(MedFrequency.labelResOrNull("Q7D"))
        assertNull(MedFrequency.labelResOrNull(null))
        MedFrequency.entries.forEach { f ->
            assertEquals(f.labelRes, MedFrequency.labelResOrNull(f.name))
        }
    }

    // ======================= EmergencyScene =======================

    @Test
    fun `EmergencyScene fromKeyOrNull 对 null 与脏值都返回 null`() {
        assertNull(EmergencyScene.fromKeyOrNull(null))
        assertNull(EmergencyScene.fromKeyOrNull(""))
        assertNull(EmergencyScene.fromKeyOrNull("NOT_A_SCENE"))
    }

    @Test
    fun `EmergencyScene fromKey 保留 fail-open 退回感染发热`() {
        // 新增事件时的默认场景，刻意如此（见 EmergencyEventSheet 的注释）。
        assertEquals(EmergencyScene.INFECTION_FEVER, EmergencyScene.fromKey(null))
        assertEquals(EmergencyScene.INFECTION_FEVER, EmergencyScene.fromKey("NOT_A_SCENE"))
    }

    @Test
    fun `EmergencyScene fromKeyOrNull 认得全部枚举项`() {
        EmergencyScene.entries.forEach { s ->
            assertEquals(s, EmergencyScene.fromKeyOrNull(s.name))
            assertEquals(s, EmergencyScene.fromKeyOrNull(s.name.lowercase()))
        }
    }

    // ======================= 三态说明 =======================

    @Test
    fun `可空返回的三个 fromKey 本来就区分 null`() {
        // 这三个是既有先例（判据本来就返回 null），本测试锁住它们不被"顺手"改成 fail-open。
        assertNull(SkipReason.fromKey("NOT_A_REASON"))
        assertNull(InjSite.fromKey("NOT_A_SITE"))
        assertNull(DoseState.fromKey("NOT_A_STATE"))
        assertNotNull(SkipReason.fromKey(SkipReason.entries.first().name))
        // InjSite 按 `key` 匹配（存库值是 key 不是 name），与本测试同批核对。
        assertNotNull(InjSite.fromKey(InjSite.entries.first().key))
        assertNull(InjSite.fromKey(InjSite.entries.first().name))
        assertNotNull(DoseState.fromKey(DoseState.entries.first().name))
    }

    @Test
    fun `急救卡用的频次文案不是患者表单文案`() {
        // v1.2.1 的教训：labelRes 含面向患者的表单提示（如「如甲氨蝶呤」），
        // 印到给医生看的急救卡上就是一份写错对象的说明书。
        MedFrequency.entries.forEach { f ->
            assertFalse(
                "急救卡不得使用 labelRes 文案：${f.name}",
                f.plainRes == f.labelRes,
            )
        }
        assertTrue(MedFrequency.entries.isNotEmpty())
    }
}
