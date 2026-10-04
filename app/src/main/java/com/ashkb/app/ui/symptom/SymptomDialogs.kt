package com.ashkb.app.ui.symptom

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource

import com.ashkb.app.domain.BasdaiScoring
import com.ashkb.app.domain.ClinicalThresholds
import com.ashkb.app.R
import com.ashkb.app.data.entity.BasdaiRecord
import com.ashkb.app.data.entity.FlareAction
import com.ashkb.app.data.entity.FlareEvent
import com.ashkb.app.data.entity.FlareTrigger
import com.ashkb.app.ui.checkup.SheetColumn
import com.ashkb.app.ui.checkup.SheetSaveButton
import com.ashkb.app.ui.components.DateFieldRules
import com.ashkb.app.ui.components.DateTextField
import com.ashkb.app.ui.components.DestructiveAction
import com.ashkb.app.ui.theme.Size
import com.ashkb.app.ui.theme.Spacing
import java.time.LocalDate
import org.json.JSONArray

// ---------------------------------------------------------------------------
// 弹窗：发作开始 / 缓解 / BASDAI 自评
// ---------------------------------------------------------------------------

@Composable
internal fun FlareStartDialog(
    onConfirm: (FlareTrigger, List<FlareAction>, Int?, String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var trigger by remember { mutableStateOf(FlareTrigger.UNKNOWN) }
    var actions by remember { mutableStateOf(setOf<FlareAction>()) }
    var peak by remember { mutableStateOf<Int?>(null) }
    var note by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.symptom_log_flare)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(stringResource(R.string.symptom_trigger), style = MaterialTheme.typography.labelLarge)
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                    FlareTrigger.entries.forEach { t ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = trigger == t, onClick = { trigger = t })
                            Text(t.label, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Text(stringResource(R.string.emergency_actions_multi), style = MaterialTheme.typography.labelLarge)
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                    FlareAction.entries.forEach { a ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = a in actions,
                                onCheckedChange = { checked -> actions = if (checked) actions + a else actions - a },
                            )
                            Text(a.label, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                ScoreRow(stringResource(R.string.symptom_peak_pain), peak) { peak = it }
                OutlinedTextField(
                    value = note, onValueChange = { note = it },
                    label = { Text(stringResource(R.string.common_notes_optional)) }, modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(trigger, actions.toList(), peak, note.ifBlank { null }) }) { Text(stringResource(R.string.emergency_record_action)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

/**
 * v1.0.80（批次 6）：**修改**一次发作登记（ModalBottomSheet，与全仓表单约定一致）。
 *
 * 可改：开始日期 / 结束日期 / 诱因 / 已采取的措施 / 峰值疼痛 / 备注。
 *
 * **不可改 `status`**（活跃 ⇄ 已缓解）：那条迁移由「登记发作 / 标记缓解」两个动作负责，
 * 编辑表单再开一个入口，就可能出现两条 `status='active'` 的记录——
 * 而症状页的发作卡只取最近一条，多出来的那条会永远看不见（幽灵活跃发作）。
 *
 * 改开始 / 结束日期会**重算派生警报**：「已第 7 天」警报按发作窗口判定归属，
 * 窗口一挪，旧窗口里那条就不再成立（见 `HealthRepository.saveFlare`）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FlareEditSheet(
    existing: FlareEvent,
    onSave: (FlareEvent) -> Unit,
    onDelete: (FlareEvent) -> Unit,
    onDismiss: () -> Unit,
) {
    var startDate by remember(existing.id) { mutableStateOf(existing.startDate) }
    var endDate by remember(existing.id) { mutableStateOf(existing.endDate ?: "") }
    var trigger by remember(existing.id) { mutableStateOf(FlareTrigger.fromKey(existing.trigger)) }
    var actions by remember(existing.id) { mutableStateOf(parseActions(existing.actionsTaken)) }
    var peak by remember(existing.id) { mutableStateOf(existing.severityPeak) }
    var note by remember(existing.id) { mutableStateOf(existing.notes ?: "") }

    val startOk = DateFieldRules.requiredOk(startDate)
    val endOk = DateFieldRules.optionalOk(endDate)

    ModalBottomSheet(onDismissRequest = onDismiss) {
        SheetColumn {
            Text(stringResource(R.string.flare_edit_title), style = MaterialTheme.typography.titleLarge)
            DateTextField(
                value = startDate, onValueChange = { startDate = it },
                label = stringResource(R.string.flare_start_date_field),
            )
            DateTextField(
                value = endDate, onValueChange = { endDate = it },
                label = stringResource(R.string.flare_end_date_field), required = false,
            )
            Text(stringResource(R.string.symptom_trigger), style = MaterialTheme.typography.labelLarge)
            FlareTriggerPicker(selected = trigger, onSelect = { trigger = it })
            Text(stringResource(R.string.emergency_actions_multi), style = MaterialTheme.typography.labelLarge)
            FlareActionPicker(selected = actions, onToggle = { a, checked ->
                actions = if (checked) actions + a else actions - a
            })
            ScoreRow(stringResource(R.string.symptom_peak_pain), peak) { peak = it }
            OutlinedTextField(
                value = note, onValueChange = { note = it },
                label = { Text(stringResource(R.string.common_notes_optional)) }, modifier = Modifier.fillMaxWidth(),
            )
            SheetSaveButton(
                text = stringResource(R.string.common_save),
                enabled = startOk && endOk,
                onClick = {
                    val start = DateFieldRules.toIsoOrNull(startDate) ?: return@SheetSaveButton
                    onSave(
                        existing.copy(
                            startDate = start,
                            endDate = DateFieldRules.toIsoOrNull(endDate),
                            // 结束日期填了却仍是 active 会让「已第几天」一直涨：填了结束日就一并落成 resolved
                            status = if (endDate.isBlank()) existing.status else "resolved",
                            trigger = trigger.name,
                            actionsTaken = JSONArray(actions.map { it.name }).toString(),
                            severityPeak = peak,
                            notes = note.ifBlank { null },
                        )
                    )
                    onDismiss()
                },
            )
            DestructiveAction(
                label = stringResource(R.string.common_delete),
                confirmTitle = stringResource(R.string.flare_delete_confirm, existing.startDate),
                confirmBody = stringResource(R.string.flare_delete_note) + "\n" +
                    stringResource(R.string.common_delete_irreversible),
                onConfirm = {
                    onDelete(existing)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** 诱因单选（从 [FlareEditSheet] 抽出：表单本身要控制长度，这一组是独立可读的一件事）。 */
@Composable
private fun FlareTriggerPicker(selected: FlareTrigger, onSelect: (FlareTrigger) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        FlareTrigger.entries.forEach { t ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = Size.touchMin),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = selected == t, onClick = { onSelect(t) })
                Text(t.label, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** 已采取措施多选（同上，抽出理由一致）。 */
@Composable
private fun FlareActionPicker(selected: Set<FlareAction>, onToggle: (FlareAction, Boolean) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        FlareAction.entries.forEach { a ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = Size.touchMin),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = a in selected, onCheckedChange = { checked -> onToggle(a, checked) })
                Text(a.label, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** `actions_taken` 的 JSON 数组 → 枚举集合；脏值（非 JSON / 未知名）一律忽略，不抛异常。 */
private fun parseActions(json: String?): Set<FlareAction> {
    if (json.isNullOrBlank()) return emptySet()
    val names = runCatching {
        JSONArray(json).let { arr -> (0 until arr.length()).map { arr.optString(it) } }
    }.getOrDefault(emptyList())
    return names.mapNotNullTo(mutableSetOf()) { n ->
        FlareAction.entries.firstOrNull { it.name == n }
    }
}

@Composable
internal fun FlareResolveDialog(
    onConfirm: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.symptom_mark_remission)) },
        text = {
            Column {
                Text(stringResource(R.string.symptom_remission_confirm_note), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(Spacing.sm))
                OutlinedTextField(
                    value = note, onValueChange = { note = it },
                    label = { Text(stringResource(R.string.symptom_relieve_notes_field)) }, modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(note.ifBlank { null }) }) { Text(stringResource(R.string.symptom_confirm_remission)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

@Composable
internal fun BasdaiDialog(
    date: LocalDate,
    existing: BasdaiRecord?,
    onConfirm: (Int, Int, Int, Int, Int, Int, String?) -> Unit,
    onDismiss: () -> Unit,
) {
    // 编辑模式：回显同日已有自评（数值不预填的原则对「编辑旧值」不适用——编辑就是要改旧值）
    var q1 by remember(existing?.id, date) { mutableStateOf<Int?>(existing?.q1Fatigue) }
    var q2 by remember(existing?.id, date) { mutableStateOf<Int?>(existing?.q2SpinePain) }
    var q3 by remember(existing?.id, date) { mutableStateOf<Int?>(existing?.q3PeripheralPain) }
    var q4 by remember(existing?.id, date) { mutableStateOf<Int?>(existing?.q4TenderPoints) }
    var q5 by remember(existing?.id, date) { mutableStateOf<Int?>(existing?.q5StiffnessDegree) }
    var q6 by remember(existing?.id, date) { mutableStateOf<Int?>(existing?.q6StiffnessDuration) }
    var note by remember(existing?.id, date) { mutableStateOf(existing?.notes ?: "") }
    // v1.1.3（批次 19 · J-6）：**六题全部作答才计分**。旧实现门槛是「至少答 1 题」，
    // 未答的题按 0 计入——只答 Q1=8 会得到 1.6 分这种「看起来合法、实际不成立」的总分，
    // 并流进趋势图 / PDF / 复诊提示。判定收敛到 domain 的 [BasdaiScoring]（纯函数，可单测）。
    val answers = listOf(q1, q2, q3, q4, q5, q6)
    val answered = BasdaiScoring.answeredCount(answers)
    val complete = BasdaiScoring.isComplete(answers)
    // 不完整时**不显示总分**：显示了就是在鼓励患者把一个残缺分数当成结果。
    val total = if (complete) BasdaiRecord.total(q1!!, q2!!, q3!!, q4!!, q5!!, q6!!) else null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.basdai_dialog_title, date)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    if (existing == null) stringResource(R.string.basdai_weekly_note)
                    else stringResource(R.string.vitals_editable_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Spacing.sm))
                ScoreRow(stringResource(R.string.basdai_q1_fatigue), q1) { q1 = it }
                ScoreRow(stringResource(R.string.basdai_q2_spinal_pain), q2) { q2 = it }
                ScoreRow(stringResource(R.string.basdai_q3_peripheral), q3) { q3 = it }
                ScoreRow(stringResource(R.string.basdai_q4_tenderness), q4) { q4 = it }
                ScoreRow(stringResource(R.string.basdai_q5_stiffness), q5) { q5 = it }
                ScoreRow(stringResource(R.string.basdai_q6_stiffness), q6) { q6 = it }
                OutlinedTextField(
                    value = note, onValueChange = { note = it },
                    label = { Text(stringResource(R.string.common_notes_optional)) }, modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(Spacing.sm))
                if (!complete) {
                    Text(
                        stringResource(R.string.basdai_incomplete_note, answered),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (total != null) {
                    Spacer(Modifier.height(Spacing.sm))
                    Text(
                        "总分：%.1f".format(total) +
                        if (ClinicalThresholds.basdaiHigh(total)) stringResource(R.string.basdai_high_note_paren) else "",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (ClinicalThresholds.basdaiHigh(total)) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (complete) {
                        onConfirm(q1!!, q2!!, q3!!, q4!!, q5!!, q6!!, note.ifBlank { null })
                    }
                },
                enabled = complete,
            ) { Text(if (existing == null) stringResource(R.string.common_submit) else stringResource(R.string.common_update)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}
