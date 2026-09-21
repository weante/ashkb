package com.ashkb.app.ui.knowledge

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource

import com.ashkb.app.R
import com.ashkb.app.data.entity.KbEntry
import com.ashkb.app.ui.components.StatusChip
import com.ashkb.app.ui.theme.Spacing
import com.ashkb.app.ui.theme.StatusTone
import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject

/**
 * K 模块条目详情：summary / 我的备注 / 处置建议 / 证据 / 来源与复核（payload 按 category 动态展开）
 * 备注回调由外层注入（而不是拿 vm）：弹窗只负责交互，写入路径统一走 ViewModel。
 */
@Composable
fun KbDetailDialog(
    entry: KbEntry,
    onSaveNote: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val payload = runCatching { JSONObject(entry.payload) }.getOrDefault(JSONObject())
    val uriHandler = LocalUriHandler.current
    val overdue = entry.reviewDue < LocalDate.now().toString()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(entry.title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (overdue) {
                    StatusChip(text = stringResource(R.string.checkup_overdue_note), tone = StatusTone.Warning, icon = Icons.Rounded.Schedule)
                    Spacer(Modifier.height(Spacing.sm))
                }
                Text(entry.summary, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(Spacing.md))

                // B2 个人备注层：插在摘要之后、处置建议之前——患者自己写下的感受比通用种子内容更需要先被看到
                var editing by remember(entry.id) { mutableStateOf(false) }
                // draft 的 key 带上 entry.userNote：保存后 entry 会换新实例，草稿必须跟着重同步，否则残留旧文本
                var draft by remember(entry.id, entry.userNote) { mutableStateOf(entry.userNote ?: "") }
                val savedNote = entry.userNote?.takeIf { it.isNotBlank() }

                Text(
                    stringResource(R.string.kb_note_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = Spacing.xxs),
                )
                if (editing) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        label = { Text(stringResource(R.string.kb_note_title)) },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        TextButton(onClick = {
                            onSaveNote(draft)
                            editing = false
                        }) { Text(stringResource(R.string.common_save)) }
                        TextButton(onClick = { editing = false }) { Text(stringResource(R.string.common_cancel)) }
                        Spacer(Modifier.weight(1f))
                        // 清除是破坏性动作：用 error 色拉开距离，避免与保存并排时误触
                        TextButton(onClick = {
                            onSaveNote(null)
                            editing = false
                        }) { Text(stringResource(R.string.kb_note_clear), color = MaterialTheme.colorScheme.error) }
                    }
                } else if (savedNote == null) {
                    Text(
                        stringResource(R.string.kb_note_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = { editing = true }) { Text(stringResource(R.string.kb_note_edit)) }
                } else {
                    Text(savedNote, style = MaterialTheme.typography.bodyMedium)
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        TextButton(onClick = { editing = true }) { Text(stringResource(R.string.kb_note_edit_action)) }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { onSaveNote(null) }) {
                            Text(stringResource(R.string.kb_note_clear), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                // 编辑态与空态都给提示：说明备注与条目内容分开存储，打消「会不会覆盖条目」的顾虑
                if (editing || savedNote == null) {
                    Text(
                        stringResource(R.string.kb_note_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(Spacing.md))

                PayloadSection(stringResource(R.string.checkup_disposition), payload.optArr("action"))
                PayloadSection(stringResource(R.string.knowledge_evidence_source), payload.optArr("evidence"))
                LabeledText(stringResource(R.string.knowledge_key_points), payload.optStr("content"))
                LabeledText(stringResource(R.string.med_dose_anchor), payload.optStr("dose"))
                LabeledText(stringResource(R.string.knowledge_benefit), payload.optStr("benefit"))
                LabeledText(stringResource(R.string.knowledge_risk), payload.optStr("risk"))
                LabeledText(stringResource(R.string.knowledge_alternative), payload.optStr("alternative_hint"))
                LabeledText(stringResource(R.string.report_threshold_note), payload.optStr("note"))
                LabeledText(stringResource(R.string.knowledge_trigger_condition), payload.optStr("condition"))
                payload.optStr("value")?.let {
                    LabeledText(stringResource(R.string.report_threshold), "$it ${payload.optStr("unit") ?: ""}")
                }
                Spacer(Modifier.height(Spacing.md))
                Surface(
                    Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                        Text(
                            stringResource(R.string.knowledge_source_line, entry.sourceName),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            stringResource(R.string.knowledge_evidence_meta, entry.sourceTier, entry.adaptedAt, entry.reviewDue, entry.version),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (entry.sourceUrl.isNotBlank()) {
                            TextButton(
                                onClick = { runCatching { uriHandler.openUri(entry.sourceUrl) } },
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                    start = Spacing.xxs, end = Spacing.xxs, top = Spacing.xxs, bottom = Spacing.xxs,
                                ),
                            ) { Text(stringResource(R.string.knowledge_view_source)) }
                        }
                    }
                }
                Text(
                    stringResource(R.string.knowledge_disclaimer),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.sm),
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) } },
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
