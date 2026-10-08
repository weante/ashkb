package com.ashkb.app.data.repo

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * 种子内容**曾经以哪些语言写入过数据库**。
 *
 * 为什么需要它：种子文案现在是 `@StringRes`，但落库的是**文本**
 * （`recipes.title` / `checkup_items.name` / `exercise_plans.week_structure`——用户可编辑、
 * 要能导出备份，存资源 id 没有意义），所以库里那一行的语言 = **种入那一刻**的语言。
 * 于是判断「这行还是原封不动的种子」与「这行是不是同一条种子」都必须拿**所有已知语言**去比，
 * 只比当前语言会把中文旧行当成陌生数据（轻则重复种入，重则用户切语言后永远看不到新语言）。
 *
 * ⚠️ 将来新增发布语言时必须在这里追加一条，否则**该语言之前种下的行不会被刷新**。
 */
internal object SeedLocales {

    val ALL: List<Locale> = listOf(Locale.SIMPLIFIED_CHINESE, Locale.ENGLISH)

    /**
     * 用指定语言取词的 `Context`。
     *
     * 用 `createConfigurationContext` 而不是改进程 `Locale.setDefault`：后者会影响全局
     * 数字 / 日期格式化（本项目多处刻意锁 `Locale.US` 防止小数点变逗号），
     * 为了读一条种子文案去动它是得不偿失的。
     */
    fun contextIn(base: Context, locale: Locale): Context {
        val cfg = Configuration(base.resources.configuration)
        cfg.setLocale(locale)
        return base.createConfigurationContext(cfg)
    }
}
