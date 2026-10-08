package com.ashkb.app.domain

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * v1.0.67 C1：档案标签映射回归（骶髂关节影像分期）。
 *
 * i18n（v1.2.6）：`Labels` 的函数改为返回 `@StringRes Int`，断言的是**渲染后的中文文本**，
 * 故整类走 Robolectric；`@Config(qualifiers = "zh-rCN")` 不可省（`values-en` 存在）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "zh-rCN")
class LabelsTest {

    private val ctx: Context get() = RuntimeEnvironment.getApplication()

    private fun label(key: String?): String = ctx.getString(Labels.sacroiliitisGrade(key))

    @Test
    fun `骶髂关节分期 0 到 IV 各有中文标签`() {
        assertEquals("0 正常", label("0"))
        assertEquals("I 可疑", label("1"))
        assertEquals("II 轻度", label("2"))
        assertEquals("III 中度", label("3"))
        assertEquals("IV 重度", label("4"))
    }

    @Test
    fun `未填与未知值统一为未评估`() {
        assertEquals("未评估", label(null))
        assertEquals("未评估", label(""))
        assertEquals("未评估", label("unknown"))
        assertEquals("未评估", label("5"))
    }

    @Test
    fun `可选值清单与标签一一对应且无未评估`() {
        assertEquals(listOf("0", "1", "2", "3", "4"), Labels.SACROILIITIS_KEYS)
        Labels.SACROILIITIS_KEYS.forEach { k ->
            assertTrue("key=$k 应有非「未评估」标签", label(k) != "未评估")
        }
    }

    @Test
    fun `标签里不出现原始 key 之外的空值`() {
        // 防回归：key 本身不该出现在 UI（改版方案 §11）——这里只确保映射非空
        Labels.SACROILIITIS_KEYS.forEach { k ->
            assertTrue(label(k).isNotBlank())
        }
    }

    /**
     * i18n 新增守卫：五个分期 key 必须落在**五个不同**的资源上。
     *
     * 改成资源 id 后，把两个 key 误指向同一条资源（例如全写成 `dom_lbl_sacro_unknown`）
     * 编译器不会报错，界面却会静默退化成「每档都显示同一句话」——这条断言专门堵这个坏法。
     */
    @Test
    fun `五个分期 key 指向五条不同资源`() {
        val resIds = Labels.SACROILIITIS_KEYS.map { Labels.sacroiliitisGrade(it) }
        assertEquals("分期标签不得重复指向同一资源", resIds.size, resIds.toSet().size)
    }
}
