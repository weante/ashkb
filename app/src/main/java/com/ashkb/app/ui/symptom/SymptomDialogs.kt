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
import androidx.compose.ui.unit.dp
import com.ashkb.app.data.entity.BasdaiRecord
import com.ashkb.app.data.entity.FlareAction
import com.ashkb.app.data.entity.FlareTrigger
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
        title = { Text("登记发作") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("诱因", style = MaterialTheme.typography.labelLarge)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    FlareTrigger.entries.forEach { t ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = trigger == t, onClick = { trigger = t })
                            Text(t.label, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Text("已采取的处理（可多选）", style = MaterialTheme.typography.labelLarge)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
                ScoreRow("本次峰值疼痛", peak) { peak = it }
                OutlinedTextField(
                    value = note, onValueChange = { note = it },
                    label = { Text("备注（可选）") }, modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(trigger, actions.toList(), peak, note.ifBlank { null }) }) { Text("登记") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
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
        title = { Text("标记缓解") },
        text = {
            Column {
                Text("将本次发作标记为今日缓解。记录完整的发作时长有助于复诊时判断病情活动。", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = note, onValueChange = { note = it },
                    label = { Text("缓解方式 / 备注（可选）") }, modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(note.ifBlank { null }) }) { Text("确认缓解") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
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
    val complete = listOf(q1, q2, q3, q4, q5, q6).all { it != null }
    val total = if (complete) BasdaiRecord.total(q1!!, q2!!, q3!!, q4!!, q5!!, q6!!) else null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("BASDAI 自评（$date）") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    if (existing == null) "回顾最近一周的感受作答（0=无，10=最重）。全部作答后可提交。"
                    else "已回显当日原值，修改后提交将覆盖更新（可多次修改）。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                ScoreRow("Q1 整体疲乏程度", q1) { q1 = it }
                ScoreRow("Q2 脊柱痛程度", q2) { q2 = it }
                ScoreRow("Q3 外周关节痛程度", q3) { q3 = it }
                ScoreRow("Q4 触痛部位程度（按压痛）", q4) { q4 = it }
                ScoreRow("Q5 晨僵程度", q5) { q5 = it }
                ScoreRow("Q6 晨僵时长（0=无，10=全天）", q6) { q6 = it }
                OutlinedTextField(
                    value = note, onValueChange = { note = it },
                    label = { Text("备注（可选）") }, modifier = Modifier.fillMaxWidth(),
                )
                if (total != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "总分：%.1f".format(total) + if (total >= 4.0) "（≥4.0：活动度偏高，两周内两次将提示复诊）" else "",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (total >= 4.0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(q1!!, q2!!, q3!!, q4!!, q5!!, q6!!, note.ifBlank { null }) },
                enabled = complete,
            ) { Text(if (existing == null) "提交" else "更新") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
