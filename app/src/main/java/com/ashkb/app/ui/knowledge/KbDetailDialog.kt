package com.ashkb.app.ui.knowledge

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import com.ashkb.app.data.entity.KbEntry
import com.ashkb.app.ui.components.StatusChip
import com.ashkb.app.ui.theme.Spacing
import com.ashkb.app.ui.theme.StatusTone
import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject

/** K 模块条目详情：summary / 处置建议 / 证据 / 来源与复核（payload 按 category 动态展开） */
@Composable
fun KbDetailDialog(entry: KbEntry, onDismiss: () -> Unit) {
    val payload = runCatching { JSONObject(entry.payload) }.getOrDefault(JSONObject())
    val uriHandler = LocalUriHandler.current
    val overdue = entry.reviewDue < LocalDate.now().toString()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(entry.title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (overdue) {
                    StatusChip(text = "已过复核日，以医嘱为准", tone = StatusTone.Warning, icon = Icons.Rounded.Schedule)
                    Spacer(Modifier.height(Spacing.sm))
                }
                Text(entry.summary, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(Spacing.md))
                PayloadSection("处置与建议", payload.optArr("action"))
                PayloadSection("证据原文", payload.optArr("evidence"))
                LabeledText("要点", payload.optStr("content"))
                LabeledText("剂量锚点", payload.optStr("dose"))
                LabeledText("获益", payload.optStr("benefit"))
                LabeledText("风险", payload.optStr("risk"))
                LabeledText("替代方案", payload.optStr("alternative_hint"))
                LabeledText("阈值说明", payload.optStr("note"))
                LabeledText("触发条件", payload.optStr("condition"))
                payload.optStr("value")?.let {
                    LabeledText("阈值", "$it ${payload.optStr("unit") ?: ""}")
                }
                Spacer(Modifier.height(Spacing.md))
                Surface(
                    Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                        Text(
                            "来源：${entry.sourceName}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "证据层级 ${entry.sourceTier} · 适配 ${entry.adaptedAt} · 复核 ${entry.reviewDue} · v${entry.version}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (entry.sourceUrl.isNotBlank()) {
                            TextButton(
                                onClick = { runCatching { uriHandler.openUri(entry.sourceUrl) } },
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                    start = Spacing.xxs, end = Spacing.xxs, top = Spacing.xxs, bottom = Spacing.xxs,
                                ),
                            ) { Text("查看原文") }
                        }
                    }
                }
                Text(
                    "本条目为患者教育参考，不替代医嘱。内容如与医生意见冲突，以医嘱为准。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.sm),
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

private fun JSONObject.optArr(key: String): JSONArray? =
    if (has(key) && !isNull(key)) optJSONArray(key) else null

private fun JSONObject.optStr(key: String): String? =
    if (has(key) && !isNull(key)) optString(key).takeIf { it.isNotBlank() } else null

@Composable
private fun PayloadSection(title: String, arr: JSONArray?) {
    if (arr == null || arr.length() == 0) return
    Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.height(Spacing.xs))
    (0 until arr.length()).forEach { i ->
        Text(
            "${i + 1}. ${arr.optString(i)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = Spacing.xs),
        )
    }
    Spacer(Modifier.height(Spacing.xs))
}

@Composable
private fun LabeledText(label: String, content: String?) {
    if (content == null) return
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = Spacing.xxs),
    )
    Text(content, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(Spacing.sm))
}
