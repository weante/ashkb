package com.ashkb.app.ui.knowledge

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.ashkb.app.R

/**
 * v1.1.2：知识库**证据层级**（`source_tier`，取值 `S1`–`S4`）的患者可读释义。
 *
 * ### 为什么需要它
 * 此前列表与详情都只显示**裸字母**（`S2 · Versus Arthritis…`）。层级是**内容可信度**的
 * 唯一线索，却需要患者自己去查一份规划文档才知道 `S2` 是什么——界面上等于没有这个信息。
 * 与其删掉层级（那会丢掉「这条来自哪一档证据」的关键信号），不如把定义写进产品。
 *
 * ### 定义（出自 `patient-health-plan/phase0-work/source-candidates-a-line.md` §1）
 *  · **S1** 国际 / 国内官方指南、官方药品标签、政府机构页面；
 *  · **S2** 同行评审期刊开放获取全文（PMC 等）；
 *  · **S3** 权威患者组织 / 医院 / NHS 患者教育页；
 *  · **S4** 商业平台（用药助手、drugs.com 等）——**仅备用**，条目里应标注「商业平台」性质。
 *
 * ### 两种长度
 *  · [tierShortLabel]——列表用「字母 + 短释义」，一行放得下，不占版面；
 *  · [tierFullLabel]——详情用完整说明（含 S4 的「仅备用」限定）。
 * 列表是多数人**唯一**会去的地方，所以短版必须存在；详情才展开得下完整定义。
 *
 * ⚠️ 未知层级（脏数据 / 将来新增 `S5`）一律**原样显示原值**，绝不显示空白——
 * 这里回落到空等于凭空抹掉一条「这条内容来自哪」的信息。
 */
@Composable
fun tierShortLabel(tier: String): String {
    // ⚠️ 块体而非表达式体：表达式体里不能写 `return`（Kotlin 语法），
    // 而"未知层级回落到原值"这条判据不能丢——见上方注释。
    val res = tierShortRes(tier) ?: return tier
    return stringResource(res)
}

/** 详情页用：层级 + 完整释义。未知层级同样回落到原值。 */
@Composable
fun tierFullLabel(tier: String): String {
    val res = tierFullRes(tier) ?: return tier
    return stringResource(res)
}

/** 层级 → 短释义的字符串资源；`null` = 未知层级（调用方回落到原值）。 */
private fun tierShortRes(tier: String): Int? = when (tier) {
    "S1" -> R.string.kb_tier_s1_short
    "S2" -> R.string.kb_tier_s2_short
    "S3" -> R.string.kb_tier_s3_short
    "S4" -> R.string.kb_tier_s4_short
    else -> null
}

/** 层级 → 完整释义的字符串资源；`null` = 未知层级（调用方回落到原值）。 */
@StringRes
private fun tierFullRes(tier: String): Int? = when (tier) {
    "S1" -> R.string.kb_tier_s1_full
    "S2" -> R.string.kb_tier_s2_full
    "S3" -> R.string.kb_tier_s3_full
    "S4" -> R.string.kb_tier_s4_full
    else -> null
}
