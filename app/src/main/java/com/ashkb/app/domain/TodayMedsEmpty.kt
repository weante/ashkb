package com.ashkb.app.domain

/**
 * 「今日用药」卡的空态判据（v1.2.19）。
 *
 * 背景——真机走查（2026-11-18，非用药日）：首页这张卡写「当前没有在用药品」，而
 * 「我的 → 药单管理」明明显示「在用 1 种 · 依那西普」。两句话互相矛盾：卡面把
 * **今天没有该服的药**说成了**根本没有药**。依那西普是 BIW（隔日给药），周三本来就没有
 * 槽位，`TodayViewModel.today` 因此为空，旧实现就统一走了「无在用药」那一条文案。
 *
 * 三种情形在用户眼里是三件事，必须分开说：
 *  · [Kind.PROFILE_MISSING]——还没建档，先引导建档（沿用旧文案与旧跳转）；
 *  · [Kind.NO_ACTIVE_MEDS]——确实没有任何在用药品，引导去添加；
 *  · [Kind.NOTHING_SCHEDULED_TODAY]——有在用药品，但今天不排（隔日用药 / 疗程间歇），
 *    应明确告诉用户「今天不用吃」，不能再劝人加药。
 *
 * 抽成纯函数是为了能在 JVM 单测里钉住判据（`TodayMedsEmptyTest`），文案与图标留在 UI 层。
 */
object TodayMedsEmpty {

    /** 空态三种情形，映射关系见类注释。 */
    enum class Kind { PROFILE_MISSING, NO_ACTIVE_MEDS, NOTHING_SCHEDULED_TODAY }

    /**
     * 判据。
     *
     * @param profileMissing 档案是否缺失（缺档案时先建档，谈不上有没有药）
     * @param activeMedCount 在用药品数量（`observeMedications()` 的长度）
     */
    fun kindOf(profileMissing: Boolean, activeMedCount: Int): Kind = when {
        profileMissing -> Kind.PROFILE_MISSING
        activeMedCount <= 0 -> Kind.NO_ACTIVE_MEDS
        else -> Kind.NOTHING_SCHEDULED_TODAY
    }
}
