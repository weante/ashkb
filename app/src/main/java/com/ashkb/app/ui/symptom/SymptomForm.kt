package com.ashkb.app.ui.symptom

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton

import com.ashkb.app.R
import com.ashkb.app.domain.ClinicalThresholds
import com.ashkb.app.ui.components.ScoreInput
import com.ashkb.app.ui.components.SectionCard
import com.ashkb.app.ui.theme.Spacing
import com.ashkb.app.ui.theme.StatusTone
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import java.time.LocalDate

// ---------------------------------------------------------------------------
// 症状录入：数值不预填——「未记录」与「实际为 0」严格区分
// ---------------------------------------------------------------------------

internal data class SymptomFormState(
    val morningStiffnessMin: Int? = null,
    val nightPain: Int? = null,
    val painScore: Int? = null,
    val feverish: Boolean = false,
    val feverTemp: Double? = null,
    val eyeSymptom: Boolean = false,
    val neuroRedFlag: Boolean = false,
    val mood: Int? = null,
    val sleep: Int? = null,
    val fatigue: Int? = null,
    val notes: String? = null,
)

@Composable
internal fun SymptomFormCard(
    existing: com.ashkb.app.data.entity.SymptomDaily?,
    dateLabel: String,
    dateKey: LocalDate,
    onSave: (SymptomFormState) -> Unit,
) {
    // 编辑已有记录时回显（有值才回显；无行则全空——不预填 0）；dateKey 让切换记录日期时重置草稿
    var stiffnessMin by remember(existing?.id, dateKey) { mutableStateOf(existing?.morningStiffnessMin?.toString() ?: "") }
    var nightPain by remember(existing?.id, dateKey) { mutableStateOf(existing?.nightPain) }
    var painScore by remember(existing?.id, dateKey) { mutableStateOf(existing?.painScore) }
    var feverish by remember(existing?.id, dateKey) { mutableStateOf(existing?.feverish ?: false) }
    var feverTemp by remember(existing?.id, dateKey) { mutableStateOf(existing?.feverTemp?.toString() ?: "") }
    var eye by remember(existing?.id, dateKey) { mutableStateOf(existing?.eyeSymptom ?: false) }
    var neuro by remember(existing?.id, dateKey) { mutableStateOf(existing?.neuroRedFlag ?: false) }
    var mood by remember(existing?.id, dateKey) { mutableStateOf(existing?.mood) }
    var sleepScore by remember(existing?.id, dateKey) { mutableStateOf(existing?.sleep) }
    var fatigue by remember(existing?.id, dateKey) { mutableStateOf(existing?.fatigue) }
    var notes by remember(existing?.id, dateKey) { mutableStateOf(existing?.notes ?: "") }

    SectionCard(title = if (existing == null) stringResource(R.string.symptom_form_title_unrecorded, dateLabel) else stringResource(R.string.symptom_form_title, dateLabel)) {
        if (existing != null) {
            Text(
                "已记录于 ${existing.recordedAt.take(16).replace("T", " ")}，再次保存将覆盖",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.xs))
        }
        OutlinedTextField(
            value = stiffnessMin,
            onValueChange = { stiffnessMin = it.filter { c -> c.isDigit() }.take(4) },
            label = { Text(stringResource(R.string.symptom_stiffness_field)) },
            modifier = Modifier.fillMaxWidth(),
        )
        ScoreRow(stringResource(R.string.symptom_night_pain_label), nightPain) { nightPain = it }
        ScoreRow(stringResource(R.string.symptom_pain_overall_label), painScore) { painScore = it }

        SwitchRow(stringResource(R.string.symptom_fever), feverish, stringResource(R.string.emergency_fever_threshold_note)) { feverish = it }
        if (feverish) {
            OutlinedTextField(
                value = feverTemp,
                onValueChange = { feverTemp = it.filter { c -> c.isDigit() || c == '.' }.take(5) },
                label = { Text(stringResource(R.string.symptom_max_fever_field)) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        SwitchRow(stringResource(R.string.symptom_eye_symptoms), eye, stringResource(R.string.symptom_uveitis_warning)) { eye = it }
        SwitchRow(stringResource(R.string.symptom_neuro), neuro, stringResource(R.string.emergency_neuro_redflag)) { neuro = it }

        Spacer(Modifier.height(Spacing.xs))
        Text(stringResource(R.string.symptom_optional_qol), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        ScoreRow(stringResource(R.string.wellness_mood), mood) { mood = it }
        ScoreRow(stringResource(R.string.wellness_sleep_quality), sleepScore) { sleepScore = it }
        ScoreRow(stringResource(R.string.symptom_fatigue), fatigue) { fatigue = it }

        OutlinedTextField(
            value = notes, onValueChange = { notes = it },
            label = { Text(stringResource(R.string.common_notes_optional)) }, modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(Spacing.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Button(onClick = {
                onSave(
                    SymptomFormState(
                        morningStiffnessMin = stiffnessMin.toIntOrNull(),
                        nightPain = nightPain, painScore = painScore,
                        feverish = feverish, feverTemp = feverTemp.toDoubleOrNull(),
                        eyeSymptom = eye, neuroRedFlag = neuro,
                        mood = mood, sleep = sleepScore, fatigue = fatigue,
                        notes = notes.ifBlank { null },
                    )
                )
            }) { Text(if (existing == null) stringResource(R.string.symptom_form_save, dateLabel) else stringResource(R.string.symptom_form_update, dateLabel)) }
        }
    }
}

/**
 * 0–10 选择器：不预填（null = 未记录，与"0 = 无"在 BASDAI 里语义不同）。
 *
 * 原实现是 11 个 32dp `FilterChip` 单行横滑 —— 触摸目标低于 48dp，
 * 而这是疼痛评分与 BASDAI 六题（共 66 个 chip）的唯一入口，属临床输入硬伤。
 * 改为 `ScoreInput`：大号等宽数字 + 48dp ± 按钮 + 滑杆。
 */
@Composable
internal fun ScoreRow(label: String, value: Int?, onChange: (Int?) -> Unit) {
    ScoreInput(
        value = value ?: 0,
        onValueChange = { onChange(it) },
        label = if (value == null) stringResource(R.string.symptom_record_hint, label) else label,
        unrecorded = value == null,
        tone = ::painTone,
    )
    if (value != null) {
        TextButton(
            onClick = { onChange(null) },
            contentPadding = PaddingValues(horizontal = Spacing.sm),
        ) { Text(stringResource(R.string.symptom_clear_record)) }
    }
}

private fun painTone(v: Int): StatusTone = when {
    v >= ClinicalThresholds.PAIN_SEVERE -> StatusTone.Danger
    v >= ClinicalThresholds.PAIN_MODERATE -> StatusTone.Warning
    else -> StatusTone.Success
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, hint: String, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = Spacing.xxs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodySmall)
            Text(hint, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
