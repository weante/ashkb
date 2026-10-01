package com.ashkb.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.ashkb.app.R
import com.ashkb.app.domain.AdherenceCalc
import com.ashkb.app.domain.ClinicalThresholds
import com.ashkb.app.ui.theme.DataLarge
import com.ashkb.app.ui.theme.Spacing
import com.ashkb.app.ui.theme.StatusTone
import com.ashkb.app.ui.theme.accent

/**
 * v1.0.77（批次 3b）：**用药完成度（计划剂量口径）** + 次级「记录内完成度」的一体化展示块。
 * v1.0.82（批次 8）：按维护者反馈瘦身——块内重复标题只在调用方没印标题时出现（见 [showTitle]）、
 * 「覆盖起点」整行删除、口径说明压成一句。**口径逻辑与百分比一个字节都没动，只动展示。**
 *
 * 为什么两套口径要挤在同一个组件里、而不是各写一段：
 * 它们的分母不一样（计划剂量数 vs 已记录条数），**必须成对出现并解释差别**——
 * 单独看任何一个都会得出错误结论（计划口径漏记会掉分；记录口径漏记根本不掉分）。
 * 收在一处还保证药单弹层与报表卡片用的是同一套文案，不会一处改了另一处没改。
 *
 * 展示口径（每一条都是上面「不得给出无据结论」原则的落实）：
 *  · `rate == null`（无计划快照）→ 显示「—（暂无计划快照）」+ 原因，**不给百分比、不给达标判定**；
 *  · 有计划快照 → 百分比 + 「完成 / 部分 / 跳过 / 未记录」四段拆分（缺任何一段都会让用户对不上数）；
 *  · 最后一句说明两个口径的分母差别，避免用户以为两个百分比在打架。
 *
 * @param dose 计划剂量口径的汇总（来自 `AdherenceCalc.doseCompletion`，调用方须先滤掉未到点的槽位）
 * @param record 记录内完成度（来自 `AdherenceCalc.completion`）——**次级说明行**，它覆盖更早的历史
 * @param days 统计窗口（天）；两套口径必须是同一个窗口
 * @param showTitle 块内是否自印标题。药单弹层顶部只有「药名 + 剂量」，指标叫什么得由本块交代，
 *   故默认 `true`；报表卡片的外层 [com.ashkb.app.ui.components.SectionCard] 已经印了**同一句**标题，
 *   那边传 `false`——同一句标题只印一次，印两遍就是维护者说的「废话」。
 */
@Composable
fun DoseCompletionBlock(
    dose: AdherenceCalc.DoseCompletion,
    record: AdherenceCalc.Completion,
    days: Int,
    modifier: Modifier = Modifier,
    showTitle: Boolean = true,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        if (showTitle) {
            Text(
                stringResource(R.string.dose_completion_plan_title, days),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        val rate = dose.ratePct
        if (rate == null) {
            Text(
                stringResource(R.string.dose_completion_no_plan),
                style = DataLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(R.string.dose_completion_no_plan_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            val tone = completionTone(rate)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("$rate%", style = DataLarge, color = tone.accent())
                Spacer(Modifier.width(Spacing.lg))
                Text(
                    stringResource(
                        R.string.dose_completion_plan_breakdown,
                        dose.done, dose.partial, dose.skipped, dose.missed, dose.planned,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.weight(1f))
                // 有计划快照才有判定：completionLabel 对 null 恒为 null（无快照上面已拦掉）
                ClinicalThresholds.completionLabel(rate)?.let { StatusChip(it, tone) }
            }
            // 数字与进度条必须同色（同 tone）——曾出现过「数字红、进度条绿」的矛盾观感
            LinearProgressIndicator(
                progress = { rate / 100f },
                modifier = Modifier.fillMaxWidth(),
                color = tone.accent(),
                trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            )
        }
        RecordCompletionLine(record = record, days = days)
        // v1.0.82（批次 8）：口径说明从一段压成一句。它是两个百分比不打架的唯一解释，**不能删**。
        Text(
            stringResource(R.string.dose_completion_scope_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 次级说明行：记录内完成度（分母 = 已记录条数）。
 *
 * 沿用报表既有的两段式（标签行 + 数值行），不另造格式：同一指标在周月报里就是「记录内完成度（N 天）」
 * 紧跟「85%（完成 3 · 部分 1 · 跳过 1，共 5 条记录）」——同一份数据在两处显示成两种样子只会让人怀疑。
 */
@Composable
private fun RecordCompletionLine(record: AdherenceCalc.Completion, days: Int) {
    Text(
        stringResource(R.string.report_adherence_days, days),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    val rate = record.ratePct
    Text(
        if (rate == null) {
            // 零分母不给百分比（「—（暂无记录）」）——0% 会被读成「一条都没完成」
            stringResource(R.string.report_completion_empty)
        } else {
            stringResource(
                R.string.report_periodic_value_adherence,
                rate, record.done, record.partial, record.skipped, record.total,
            )
        },
        style = MaterialTheme.typography.bodyMedium,
    )
}

/**
 * 完成度档位配色（90 / 70，来自 [ClinicalThresholds] 的既有阈值）。
 *
 * 与报表、药单弹层原来的内联 `when` 是同一套判定——收到这里之后，两处的百分比与判定不可能再分叉。
 */
private fun completionTone(rate: Int): StatusTone = when {
    rate >= ClinicalThresholds.ADHERENCE_GOOD -> StatusTone.Success
    rate >= ClinicalThresholds.ADHERENCE_FAIR -> StatusTone.Warning
    else -> StatusTone.Danger
}
