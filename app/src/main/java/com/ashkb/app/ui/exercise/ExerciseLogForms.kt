package com.ashkb.app.ui.exercise

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource

import com.ashkb.app.R
import com.ashkb.app.data.entity.ExerciseLog
import com.ashkb.app.ui.checkup.SheetColumn
import com.ashkb.app.ui.checkup.SheetSaveButton
import com.ashkb.app.ui.components.DestructiveAction
import com.ashkb.app.ui.theme.Size
import com.ashkb.app.ui.theme.Spacing

/**
 * v1.0.80（批次 6）：**修改 / 删除**一条运动打卡（ModalBottomSheet，与全仓表单约定一致）。
 *
 * 为什么「改打卡」值得单开一张表单：打卡是「今天我做了什么」的记录，时长记错一位、
 * 强度点错一档都是常事，而它同时喂给**完成度统计**与**次日反馈的判读基线**
 * （`ExerciseEngine.interpretFeedback` 拿运动量当背景）。此前唯一的补救是「再打一次卡」——
 * 那只会多一条记录，统计更乱。
 *
 * 可改项与打卡表单严格一致（时长 / 强度 / 备注），刻意**不含**动作名与日期：
 * 前者是处方快照（改了就不是当初做的那件事），后者决定它算哪一天的完成度，
 * 与用药记录「只改内容、不改归属日与槽位」是同一条口径（见 `MedicationRepository.updateLog`）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExerciseLogEditSheet(
    log: ExerciseLog,
    onSave: (Int?, String?, String?) -> Unit,
    onDelete: (ExerciseLog) -> Unit,
    onDismiss: () -> Unit,
) {
    var duration by remember(log.id) { mutableStateOf(log.durationMin?.toString() ?: "") }
    var intensity by remember(log.id) { mutableStateOf(log.intensity) }
    var note by remember(log.id) { mutableStateOf(log.notes ?: "") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        SheetColumn {
            Text(
                stringResource(R.string.exercise_edit_log_title),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(log.excName, style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(
                value = duration,
                onValueChange = { duration = it.filter { c -> c.isDigit() }.take(3) },
                label = { Text(stringResource(R.string.exercise_duration_field)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Text(
                stringResource(R.string.exercise_intensity_self_test),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                listOf(
                    "low" to stringResource(R.string.severity_low),
                    "moderate" to stringResource(R.string.severity_moderate),
                    "high" to stringResource(R.string.severity_high),
                ).forEach { (k, l) ->
                    FilterChip(
                        selected = intensity == k,
                        onClick = { intensity = if (intensity == k) null else k },
                        label = { Text(l) },
                        modifier = Modifier.heightIn(min = Size.touchMin),
                    )
                }
            }
            OutlinedTextField(
                value = note, onValueChange = { note = it },
                label = { Text(stringResource(R.string.common_notes_optional)) },
                modifier = Modifier.fillMaxWidth(),
            )
            SheetSaveButton(
                text = stringResource(R.string.common_save),
                onClick = {
                    onSave(duration.toIntOrNull(), intensity, note.ifBlank { null })
                    onDismiss()
                },
            )
            DestructiveAction(
                label = stringResource(R.string.common_delete),
                confirmTitle = stringResource(R.string.exercise_delete_confirm, log.excName),
                confirmBody = stringResource(R.string.exercise_delete_note) + "\n" +
                    stringResource(R.string.common_delete_irreversible),
                onConfirm = {
                    onDelete(log)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
