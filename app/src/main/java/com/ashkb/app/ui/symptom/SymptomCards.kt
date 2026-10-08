package com.ashkb.app.ui.symptom

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource

import com.ashkb.app.domain.ClinicalThresholds
import com.ashkb.app.R
import com.ashkb.app.data.entity.Alert
import com.ashkb.app.data.entity.BasdaiRecord
import com.ashkb.app.data.entity.FlareAction
import com.ashkb.app.data.entity.FlareEvent
import com.ashkb.app.data.entity.FlareTrigger
import com.ashkb.app.ui.components.DestructiveAction
import com.ashkb.app.ui.components.DividerList
import com.ashkb.app.ui.components.SectionCard
import com.ashkb.app.ui.components.StatusChip
import com.ashkb.app.ui.theme.Size
import com.ashkb.app.ui.theme.Spacing
import com.ashkb.app.ui.theme.StatusTone
import com.ashkb.app.ui.theme.colors
import androidx.compose.ui.Alignment

// ---------------------------------------------------------------------------
// 警报 / 发作 / BASDAI 卡
// ---------------------------------------------------------------------------

@Composable
private fun alertLabel(alertType: String): String = when (alertType) {
    "symptom_abnormal" -> stringResource(R.string.symptom_alert_section)
    "basdai_high" -> stringResource(R.string.basdai_self_high)
    "flare_day7" -> stringResource(R.string.report_flare_tracking)
    "review_due" -> stringResource(R.string.backup_content_review)
    "neuro_red_flag" -> stringResource(R.string.emergency_neuro_flag)
    else -> stringResource(R.string.reminder_nav)
}

private fun alertIcon(alertType: String) = when (alertType) {
    "symptom_abnormal" -> Icons.Rounded.MonitorHeart
    "basdai_high" -> Icons.Rounded.Insights
    "flare_day7" -> Icons.Rounded.TrendingUp
    "review_due" -> Icons.Rounded.Schedule
    "neuro_red_flag" -> Icons.Rounded.WarningAmber
    else -> Icons.Rounded.ErrorOutline
}

private fun alertTone(severity: String): StatusTone = when (severity) {
    "high" -> StatusTone.Danger
    "medium" -> StatusTone.Warning
    else -> StatusTone.Neutral
}

@Composable
internal fun AlertCard(alert: Alert, onView: () -> Unit, onAck: () -> Unit) {
    val tone = alertTone(alert.severity)
    val (bg, fg) = tone.colors()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = bg,
    ) {
        Column(Modifier.padding(Spacing.lg)) {
            StatusChip(text = alertLabel(alert.alertType), tone = tone, icon = alertIcon(alert.alertType))
            Spacer(Modifier.height(Spacing.sm))
            Text(alert.message, style = MaterialTheme.typography.bodyMedium, color = fg)
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                modifier = Modifier.padding(top = Spacing.xs),
            ) {
                if (alert.kbRef != null) {
                    TextButton(onClick = onView) { Text(stringResource(R.string.knowledge_view_evidence)) }
                }
                TextButton(onClick = onAck) { Text(stringResource(R.string.common_got_it)) }
            }
        }
    }
}

/**
 * 发作登记卡（无活跃发作时给「登记发作」，有则显示天数 / 诱因 / 已采取措施 + 「标记缓解」）。
 *
 * v1.0.80（批次 6）：活跃发作也能**修改**——记错诱因 / 峰值是常事，此前只能标记缓解再重记
 * （而重记会丢掉真实开始日期，把「已第几天」算错）。
 */
@Composable
internal fun FlareStatusCard(
    flare: FlareEvent?,
    days: Long?,
    onResolve: () -> Unit,
    onStart: () -> Unit,
    onEdit: (FlareEvent) -> Unit,
) {
    SectionCard(title = stringResource(R.string.symptom_flare_register)) {
        if (flare == null) {
            Text(
                stringResource(R.string.symptom_no_flare_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.sm))
            OutlinedButton(onClick = onStart) { Text(stringResource(R.string.symptom_log_flare)) }
        } else {
            StatusChip(
                text = if (days != null) stringResource(R.string.symptom_flare_day_n, days) else stringResource(R.string.symptom_flare_active),
                tone = StatusTone.Danger,
                icon = Icons.Rounded.WarningAmber,
            )
            Spacer(Modifier.height(Spacing.sm))
            Text(
                "开始：${flare.startDate} · 诱因：${stringResource(FlareTrigger.fromKey(flare.trigger).labelRes)}" +
                    (flare.severityPeak?.let { stringResource(R.string.symptom_peak_pain_suffix, it) } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            flare.actionsTaken?.let { json ->
                val acts = runCatching {
                    org.json.JSONArray(json).let { a -> (0 until a.length()).map { a.optString(it) } }
                }.getOrDefault(emptyList<String>())
                if (acts.isNotEmpty()) {
                    // v1.2.5（i18n）：`joinToString` 的 transform **不是 inline 参数**，里面不能直接
                    // 调 `@Composable` 的 stringResource；先用 inline 的 map 解析成文案再拼接。
                    val actedLabels = acts.map { stringResource(FlareAction.fromKey(it).labelRes) }
                    Text(
                        "已采取：${actedLabels.joinToString("、")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Spacing.xxs),
                    )
                }
            }
            Spacer(Modifier.height(Spacing.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Button(onClick = onResolve) { Text(stringResource(R.string.symptom_mark_remission)) }
                OutlinedButton(onClick = { onEdit(flare) }) { Text(stringResource(R.string.common_edit)) }
            }
            Text(
                stringResource(R.string.symptom_self_care_note),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
    }
}

/**
 * 发作历史列表。
 *
 * v1.0.80（批次 6）：每行给「修改 / 删除」——历史发作此前只能看，记错一条就只能一直错着。
 * 删除会连带清掉该次发作派生出来的「已第 7 天」警报（仓库层按日期窗口判定归属）。
 */
@Composable
internal fun FlareHistoryList(
    events: List<FlareEvent>,
    onEdit: (FlareEvent) -> Unit,
    onDelete: (FlareEvent) -> Unit,
) {
    DividerList(items = events, key = { it.id }) { f ->
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Text(
                "${f.startDate} 至 ${f.endDate ?: stringResource(R.string.symptom_until_now)}",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "${stringResource(FlareTrigger.fromKey(f.trigger).labelRes)}${f.severityPeak?.let { " · 峰值 $it/10" } ?: ""}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                TextButton(
                    onClick = { onEdit(f) },
                    modifier = Modifier.heightIn(min = Size.touchMin),
                ) { Text(stringResource(R.string.common_edit)) }
                TextButton(
                    onClick = { onDelete(f) },
                    modifier = Modifier.heightIn(min = Size.touchMin),
                ) { Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error) }
            }
        }
        StatusChip(
            text = if (f.status == "active") stringResource(R.string.symptom_flare_ongoing) else stringResource(R.string.symptom_status_remitted),
            tone = if (f.status == "active") StatusTone.Danger else StatusTone.Neutral,
        )
    }
}

/**
 * BASDAI 历史列表。
 *
 * v1.0.80（批次 6）：每行给删除入口（改由「编辑今日 / 昨日自评」那条路径负责，见 SymptomScreen）。
 * 删除按 **id** 走，不碰「同日只保留一条」的既有语义——那条不变量由写入侧维护。
 */
@Composable
internal fun BasdaiList(records: List<BasdaiRecord>, onDelete: (BasdaiRecord) -> Unit) {
    DividerList(items = records, key = { it.id }) { r ->
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Text(r.date + if (r.backfill) stringResource(R.string.report_supplement_tag) else "", style = MaterialTheme.typography.bodySmall)
            Text(
                "Q1 ${r.q1Fatigue} · Q2 ${r.q2SpinePain} · Q3 ${r.q3PeripheralPain} · Q4 ${r.q4TenderPoints} · Q5 ${r.q5StiffnessDegree} · Q6 ${r.q6StiffnessDuration}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // v1.2.3：删除入口从「左侧 Column 内独占一行」移到**右侧、分数上方**。
        // 维护者反馈「每天的的自评记录间隔偏大」——实测每行约 330px（屏高 3051 时一屏只
        // 看得到 6 条），其中「删除」二字独占的一行占了近三分之一。移到右侧后行高由
        // 两行文本（日期 + Q 值）决定，删除按钮与分数同列、竖直排列，不再撑行。
        // v1.2.3：删除与总分**同一列、同右对齐**。
        // 维护者反馈截图里两者错位——「删除」在右上、总分在右下，读起来像两列。
        // 现在同处一个右对齐 Column，且删除在下（先读到分数，再决定是否删）。
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
        ) {
            Text(
                "%.1f".format(r.total),
                style = MaterialTheme.typography.titleMedium,
                color = if (ClinicalThresholds.basdaiHigh(r.total)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
            DestructiveAction(
                label = stringResource(R.string.common_delete),
                confirmTitle = stringResource(R.string.basdai_delete_confirm, r.date),
                confirmBody = stringResource(R.string.basdai_delete_note, "%.1f".format(r.total)) + "\n" +
                    stringResource(R.string.common_delete_irreversible),
                onConfirm = { onDelete(r) },
            )
        }
    }
}
