package com.ashkb.app.ui.symptom

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
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

import com.ashkb.app.R
import com.ashkb.app.data.entity.BasdaiRecord
import com.ashkb.app.data.entity.FlareAction
import com.ashkb.app.data.entity.FlareTrigger
import com.ashkb.app.ui.theme.Spacing
import java.time.LocalDate

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
    // W3：BASDAI 语境 0=无症状——未作答的题按 0 计入总分（用户实测「只有疲劳感」场景：
    // 答一题 + 其余留空即可提交，与逐题点 0 等价）；提交门槛 = 至少一题已答，防空记录。
    val answered = listOf(q1, q2, q3, q4, q5, q6).count { it != null }
    val total = BasdaiRecord.total(q1 ?: 0, q2 ?: 0, q3 ?: 0, q4 ?: 0, q5 ?: 0, q6 ?: 0)

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
                if (answered < 6) {
                    Text(
                        stringResource(R.string.basdai_default_zero_note, answered),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    "总分：%.1f".format(total) + if (total >= 4.0) stringResource(R.string.basdai_high_note_paren) else "",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (total >= 4.0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(q1 ?: 0, q2 ?: 0, q3 ?: 0, q4 ?: 0, q5 ?: 0, q6 ?: 0, note.ifBlank { null }) },
                enabled = answered >= 1,
            ) { Text(if (existing == null) stringResource(R.string.common_submit) else stringResource(R.string.common_update)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}
