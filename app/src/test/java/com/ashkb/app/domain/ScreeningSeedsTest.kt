package com.ashkb.app.domain

import android.content.Context
import com.ashkb.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * v1.0.37：C10 生物制剂筛查 / 续方节点种子（幂等去重）。
 *
 * v1.2.6：种子文案改成 `@StringRes`，所以断言的是**渲染后的中文文本**（Robolectric 读 `values/`）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "zh-rCN")
class ScreeningSeedsTest {

    private val ctx: Context get() = RuntimeEnvironment.getApplication()

    /** 当前语言下一条种子的名称。 */
    private fun name(s: ScreeningSeeds.Seed): String = ctx.getString(s.nameRes)

    /** 判重回调：只认当前语言（单测口径，与真机 `SeedLocales.ALL` 不同）。 */
    private fun here(s: ScreeningSeeds.Seed): List<String> = listOf(name(s))

    @Test
    fun `pending returns all seeds when none exist`() {
        assertEquals(ScreeningSeeds.BIOLOGIC.size, ScreeningSeeds.pending(emptySet(), ::here).size)
    }

    /** 幂等：已存在的项不再返回（重复点击「一键添加」不会建重复条目）。 */
    @Test
    fun `pending skips existing names`() {
        val first = name(ScreeningSeeds.BIOLOGIC.first())
        val pending = ScreeningSeeds.pending(setOf(first), ::here)
        assertEquals(ScreeningSeeds.BIOLOGIC.size - 1, pending.size)
        assertTrue(pending.none { name(it) == first })
    }

    @Test
    fun `pending is empty when all exist`() {
        val all = ScreeningSeeds.BIOLOGIC.map { name(it) }.toSet()
        assertTrue(ScreeningSeeds.pending(all, ::here).isEmpty())
    }

    /**
     * ⭐ 中文名不认识、但**英文名认识**时也必须判为「已存在」。
     *
     * 这是切语言后不被重复种入的那条防线：库里那行是**种入当时**的语言写的，
     * 只比当前语言就会认不出它。
     */
    @Test
    fun `pending recognises a seed written in another language`() {
        val en = ctx.createConfigurationContext(
            android.content.res.Configuration(ctx.resources.configuration).apply {
                setLocale(java.util.Locale.ENGLISH)
            }
        )
        val first = ScreeningSeeds.BIOLOGIC.first()
        val allKnown = { s: ScreeningSeeds.Seed ->
            listOf(ctx.getString(s.nameRes), en.getString(s.nameRes))
        }
        val pending = ScreeningSeeds.pending(setOf(en.getString(first.nameRes)), allKnown)
        assertEquals(ScreeningSeeds.BIOLOGIC.size - 1, pending.size)
    }

    /** 规划要求的三项筛查 + 续方提醒都在种子里，且都带说明。 */
    @Test
    fun `seeds cover tb hbv hcv and refill`() {
        val names = ScreeningSeeds.BIOLOGIC.map { name(it) }
        assertTrue(names.any { it.contains("结核") })
        assertTrue(names.any { it.contains("乙肝") })
        assertTrue(names.any { it.contains("丙肝") })
        assertTrue(names.any { it.contains("续方") })
        assertTrue(ScreeningSeeds.BIOLOGIC.all { ctx.getString(it.notesRes).isNotBlank() })
    }

    /** ⭐ 四条种子必须指向四条不同资源：误指向同一条编译器不报错，界面会静默退化成重复项。 */
    @Test
    fun `seeds point at distinct resources`() {
        val ids = ScreeningSeeds.BIOLOGIC.flatMap { listOf(it.nameRes, it.notesRes) }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.contains(R.string.screen_seed_1_name))
    }
}
