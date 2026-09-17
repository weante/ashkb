package com.ashkb.app.ui.symptom

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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

import com.ashkb.app.R
import com.ashkb.app.data.entity.Alert
import com.ashkb.app.data.entity.BasdaiRecord
import com.ashkb.app.data.entity.FlareAction
import com.ashkb.app.data.entity.FlareEvent
import com.ashkb.app.data.entity.FlareTrigger
import com.ashkb.app.ui.components.DividerList
import com.ashkb.app.ui.components.SectionCard
import com.ashkb.app.ui.components.StatusChip
import com.ashkb.app.ui.theme.Spacing
import com.ashkb.app.ui.theme.StatusTone
import com.ashkb.app.ui.theme.colors

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

@Composable
internal fun FlareStatusCard(flare: FlareEvent?, days: Long?, onResolve: () -> Unit, onStart: () -> Unit) {
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
                "开始：${flare.startDate} · 诱因：${FlareTrigger.fromKey(flare.trigger).label}" +
                    (flare.severityPeak?.let { stringResource(R.string.symptom_peak_pain_suffix, it) } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            flare.actionsTaken?.let { json ->
                val acts = runCatching {
                    org.json.JSONArray(json).let { a -> (0 until a.length()).map { a.optString(it) } }
                }.getOrDefault(emptyList<String>())
                if (acts.isNotEmpty()) {
                    Text(
                        "已采取：${acts.joinToString("、") { FlareAction.fromKey(it).label }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Spacing.xxs),
                    )
                }
            }
            Spacer(Modifier.height(Spacing.sm))
            Button(onClick = onResolve) { Text(stringResource(R.string.symptom_mark_remission)) }
            Text(
                stringResource(R.string.symptom_self_care_note),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
    }
}

@Composable
internal fun FlareHistoryList(events: List<FlareEvent>) {
    DividerList(items = events, key = { it.id }) { f ->
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Text(
                "${f.startDate} 至 ${f.endDate ?: stringResource(R.string.symptom_until_now)}",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "${FlareTrigger.fromKey(f.trigger).label}${f.severityPeak?.let { " · 峰值 $it/10" } ?: ""}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        StatusChip(
            text = if (f.status == "active") stringResource(R.string.symptom_flare_ongoing) else stringResource(R.string.symptom_status_remitted),
            tone = if (f.status == "active") StatusTone.Danger else StatusTone.Neutral,
        )
    }
}

@Composable
internal fun BasdaiList(records: List<BasdaiRecord>) {
    DividerList(items = records, key = { it.id }) { r ->
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Text(r.date + if (r.backfill) stringResource(R.string.report_supplement_tag) else "", style = MaterialTheme.typography.bodySmall)
            Text(
                "Q1 ${r.q1Fatigue} · Q2 ${r.q2SpinePain} · Q3 ${r.q3PeripheralPain} · Q4 ${r.q4TenderPoints} · Q5 ${r.q5StiffnessDegree} · Q6 ${r.q6StiffnessDuration}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            "%.1f".format(r.total),
            style = MaterialTheme.typography.titleMedium,
            color = if (r.total >= 4.0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        )
    }
}
