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

private fun alertLabel(alertType: String): String = when (alertType) {
    "symptom_abnormal" -> "症状警报"
    "basdai_high" -> "自评偏高"
    "flare_day7" -> "发作追踪"
    "review_due" -> "内容复核"
    "neuro_red_flag" -> "神经红旗"
    else -> "提醒"
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
                    TextButton(onClick = onView) { Text("查看依据") }
                }
                TextButton(onClick = onAck) { Text("知道了") }
            }
        }
    }
}

@Composable
internal fun FlareStatusCard(flare: FlareEvent?, days: Long?, onResolve: () -> Unit, onStart: () -> Unit) {
    SectionCard(title = "发作登记") {
        if (flare == null) {
            Text(
                "当前无活跃发作。症状明显加重（疼痛 / 晨僵突然变重）时在此登记，系统将追踪天数并在第 7 天提醒就医指征。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.sm))
            OutlinedButton(onClick = onStart) { Text("登记发作") }
        } else {
            StatusChip(
                text = if (days != null) "发作进行中 · 第 $days 天" else "发作进行中",
                tone = StatusTone.Danger,
                icon = Icons.Rounded.WarningAmber,
            )
            Spacer(Modifier.height(Spacing.sm))
            Text(
                "开始：${flare.startDate} · 诱因：${FlareTrigger.fromKey(flare.trigger).label}" +
                    (flare.severityPeak?.let { " · 峰值疼痛 $it/10" } ?: ""),
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
            Button(onClick = onResolve) { Text("标记缓解") }
            Text(
                "自我处理（休息 / 温和活动 / 热敷）7–10 天无改善应联系风湿科",
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
                "${f.startDate} → ${f.endDate ?: "进行中"}",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "${FlareTrigger.fromKey(f.trigger).label}${f.severityPeak?.let { " · 峰值 $it/10" } ?: ""}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        StatusChip(
            text = if (f.status == "active") "进行中" else "已缓解",
            tone = if (f.status == "active") StatusTone.Danger else StatusTone.Neutral,
        )
    }
}

@Composable
internal fun BasdaiList(records: List<BasdaiRecord>) {
    DividerList(items = records, key = { it.id }) { r ->
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Text(r.date + if (r.backfill) "（补）" else "", style = MaterialTheme.typography.bodySmall)
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
