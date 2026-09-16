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

    SectionCard(title = if (existing == null) "$dateLabel 症状（未记录）" else "$dateLabel 症状") {
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
            label = { Text("晨僵时长（分钟，醒后至僵硬感消退）") },
            modifier = Modifier.fillMaxWidth(),
        )
        ScoreRow("夜间痛（是否夜间痛醒，0=无）", nightPain) { nightPain = it }
        ScoreRow("整体疼痛（0=无，10=最重）", painScore) { painScore = it }

        SwitchRow("发热", feverish, "体温 ≥ 38.5℃ 将触发应急警报") { feverish = it }
        if (feverish) {
            OutlinedTextField(
                value = feverTemp,
                onValueChange = { feverTemp = it.filter { c -> c.isDigit() || c == '.' }.take(5) },
                label = { Text("最高体温（℃，如 38.6）") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        SwitchRow("眼部症状", eye, "眼痛 / 发红 / 畏光 / 视物模糊——葡萄膜炎警示") { eye = it }
        SwitchRow("神经症状", neuro, "麻木 / 无力 / 大小便控制变化——需立即就医") { neuro = it }

        Spacer(Modifier.height(Spacing.xs))
        Text("以下可选（生活质量参考）", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        ScoreRow("心情", mood) { mood = it }
        ScoreRow("睡眠质量", sleepScore) { sleepScore = it }
        ScoreRow("疲乏程度", fatigue) { fatigue = it }

        OutlinedTextField(
            value = notes, onValueChange = { notes = it },
            label = { Text("备注（可选）") }, modifier = Modifier.fillMaxWidth(),
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
            }) { Text(if (existing == null) "保存$dateLabel 症状" else "更新$dateLabel 症状") }
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
        label = if (value == null) "$label（点按开始记录）" else label,
        tone = ::painTone,
    )
    if (value != null) {
        TextButton(
            onClick = { onChange(null) },
            contentPadding = PaddingValues(horizontal = Spacing.sm),
        ) { Text("清除本次记录") }
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
