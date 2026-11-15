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
import com.ashkb.app.ui.components.AshkbTextField
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
import com.ashkb.app.domain.KbUserEntry
import com.ashkb.app.ui.components.DestructiveAction
import com.ashkb.app.ui.components.StatusChip
import com.ashkb.app.ui.theme.Spacing
import com.ashkb.app.ui.theme.StatusTone
import org.json.JSONArray
import org.json.JSONObject

/**
 * K 模块条目详情：summary / 我的备注 / 处置建议 / 证据 / 来源与复核（payload 按 category 动态展开）
 * 备注回调由外层注入（而不是拿 vm）：弹窗只负责交互，写入路径统一走 ViewModel。
 *
 * v1.2.16：整块正文按「备注 / 载荷 / 来源」拆成三个私有 composable（`NoteEditor` /
 * `PayloadBody` / `SourceBlock`），本函数只负责组装与「编辑 / 删除」按钮。拆的理由是
 * detekt 的 `LongMethod`(80) 与 `CyclomaticComplexMethod`(15) 双阈值——加个人条目的
 * 编辑删除后本函数同时越了这两条线，而按项目铁律这两类问题的对策是**改代码拆函数**，
 * 不是往基线里塞豁免。
 */
@Composable
fun KbDetailDialog(
    entry: KbEntry,
    today: String,
    onSaveNote: (String?) -> Unit,
    onDismiss: () -> Unit,
    // v1.2.16：只有自己写的条目（`usr-` 前缀）才传这两个；种子条目为 null，
    // 详情里就不出现「编辑 / 删除」——见 KnowledgeScreen 的调用点。
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    val payload = runCatching { JSONObject(entry.payload) }.getOrDefault(JSONObject())
    val mine = KbUserEntry.isUserEntry(entry.id)
    // v1.2.7（批次 14 / R8）：**改用调用方注入的 `today`**，不再读系统时钟。
    // `today` 来自 `KnowledgeScreen` 的 `KnowledgeViewModel.date`（注入的 `DateProvider`，每零点推进），
    // 与列表行 `overdue = entry.reviewDue < today` 是**同一个值**：「同一屏上的同一件事只允许有一个日期源」。
    val overdue = entry.reviewDue < today

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

                NoteEditor(entry = entry, onSaveNote = onSaveNote)
                Spacer(Modifier.height(Spacing.md))

                PayloadBody(payload = payload)
                Spacer(Modifier.height(Spacing.md))

                SourceBlock(entry = entry, mine = mine)
                Text(
                    stringResource(R.string.knowledge_disclaimer),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.sm),
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) } },
        // v1.2.16：自己写的条目给「编辑 / 删除」；删除走统一的破坏性操作形态（先确认、说清不可撤销）。
        dismissButton = if (onEdit == null && onDelete == null) {
            null
        } else {
            { KbDialogDismissActions(entryTitle = entry.title, onEdit = onEdit, onDelete = onDelete) }
        },
    )
}

/**
 * B2 个人备注层：插在摘要之后、处置建议之前——患者自己写下的感受比通用种子内容更需要先被看到。
 *
 * 备注与条目正文是**两层独立存储**（`user_note` 列 vs `payload`），编辑备注永远不会动条目内容；
 * 编辑态与空态都给一句提示，就是为了打消「会不会覆盖条目」的顾虑。
 */
@Composable
private fun NoteEditor(entry: KbEntry, onSaveNote: (String?) -> Unit) {
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
        AshkbTextField(
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
}

/**
 * payload 正文展开：只读既有键（`content` / `action` / `evidence` 与各分类的补充字段）。
 *
 * v1.2.16：个人条目的 payload 就写在这几个键上（见 `KbUserEntry.payloadOf`），
 * 因此新增这类条目**不需要改这里的渲染路径**，也不会出现「存了但看不见」的字段。
 */
@Composable
private fun PayloadBody(payload: JSONObject) {
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
}

/**
 * 来源与证据层级。
 *
 * v1.1.2：详情页把层级展开成**完整释义**（列表只给短版），S4 的「商业平台 · 仅备用」这一限定
 * 也在这里才说得清。v1.2.16：个人条目没有「适配 / 复核 / 版本」这套溯源字段（复核日固定
 * 9999-12-31 只为不触发到期告警），显示出来只会是一串假日期，故只给层级释义。
 */
@Composable
private fun SourceBlock(entry: KbEntry, mine: Boolean) {
    val uriHandler = LocalUriHandler.current
    Surface(
        Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            if (entry.sourceName.isNotBlank()) {
                Text(
                    stringResource(R.string.knowledge_source_line, entry.sourceName),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (mine) {
                Text(
                    tierFullLabel(entry.sourceTier),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    stringResource(
                        R.string.knowledge_evidence_meta,
                        tierFullLabel(entry.sourceTier),
                        entry.adaptedAt,
                        entry.reviewDue,
                        entry.version,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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
}

/** 弹窗左下角的「编辑 / 删除」（只有个人条目才会走到这里，见 [KbDetailDialog.dismissButton]）。 */
@Composable
private fun KbDialogDismissActions(
    entryTitle: String,
    onEdit: (() -> Unit)?,
    onDelete: (() -> Unit)?,
) {
    Row {
        if (onEdit != null) {
            TextButton(onClick = onEdit) { Text(stringResource(R.string.common_edit)) }
        }
        if (onDelete != null) {
            DestructiveAction(
                label = stringResource(R.string.common_delete),
                confirmTitle = stringResource(R.string.kb_user_delete_confirm_title),
                confirmBody = stringResource(R.string.kb_user_delete_confirm_body, entryTitle),
                onConfirm = onDelete,
            )
        }
    }
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
