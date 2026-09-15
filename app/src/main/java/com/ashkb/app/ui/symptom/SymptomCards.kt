package com.ashkb.app.ui.symptom

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ashkb.app.data.entity.Alert
import com.ashkb.app.data.entity.BasdaiRecord
import com.ashkb.app.data.entity.FlareAction
import com.ashkb.app.data.entity.FlareEvent
import com.ashkb.app.data.entity.FlareTrigger

// ---------------------------------------------------------------------------
// 警报 / 发作 / BASDAI 卡
// ---------------------------------------------------------------------------

@Composable
internal fun AlertCard(alert: Alert, onView: () -> Unit, onAck: () -> Unit) {
    val color = when (alert.severity) {
        "high" -> MaterialTheme.colorScheme.error
        "medium" -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.10f)),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when (alert.alertType) {
                        "symptom_abnormal" -> "症状警报"
                        "basdai_high" -> "自评偏高"
                        "flare_day7" -> "发作追踪"
                        "review_due" -> "内容复核"
                        "neuro_red_flag" -> "神经红旗"
                        else -> "提醒"
                    },
                    style = MaterialTheme.typography.labelMedium, color = color,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(alert.message, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                if (alert.kbRef != null) {
                    TextButton(onClick = onView, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 0.dp)) { Text("查看依据") }
                }
                TextButton(onClick = onAck, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 0.dp)) { Text("知道了") }
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
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onStart) { Text("登记发作") }
        } else {
            Text(
                if (days != null) "发作进行中 · 第 $days 天" else "发作进行中",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error,
            )
            Text(
                "开始：${flare.startDate} · 诱因：${FlareTrigger.fromKey(flare.trigger).label}" +
                    (flare.severityPeak?.let { " · 峰值疼痛 $it/10" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
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
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = onResolve) { Text("标记缓解") }
            Text(
                "自我处理（休息 / 温和活动 / 热敷）7–10 天无改善应联系风湿科",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
internal fun FlareHistoryRow(f: FlareEvent) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
        Text(
            if (f.status == "active") "进行中" else "已缓解",
            style = MaterialTheme.typography.labelMedium,
            color = if (f.status == "active") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
internal fun BasdaiRow(r: BasdaiRecord) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
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

@Composable
internal fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}
