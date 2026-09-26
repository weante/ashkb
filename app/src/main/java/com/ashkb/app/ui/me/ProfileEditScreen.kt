package com.ashkb.app.ui.me

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource

import com.ashkb.app.R
import com.ashkb.app.data.entity.Profile
import com.ashkb.app.data.repo.nowIso
import com.ashkb.app.domain.Labels
import com.ashkb.app.domain.Lifestyle
import com.ashkb.app.ui.components.ScreenTopBar
import com.ashkb.app.ui.theme.Size
import com.ashkb.app.ui.theme.Spacing

/**
 * M0 建档：AS 专属字段（过敏史 / 血型 / 合并症为禁忌检查与紧急卡数据源）。
 * 原为 10 字段 `AlertDialog`，改为全屏表单（route `profile/edit`）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditScreen(
    initial: Profile?,
    onSave: (Profile) -> Unit,
    onBack: () -> Unit,
) {
    val defaultDiagnosis = stringResource(R.string.profile_diag_axial_spa)
    var name by remember { mutableStateOf(initial?.displayName ?: "") }
    var diagnosis by remember { mutableStateOf(initial?.diagnosis ?: defaultDiagnosis) }
    var year by remember { mutableStateOf(initial?.diagnoseYear?.toString() ?: "") }
    var hla by remember { mutableStateOf(initial?.hlaB27 ?: "unknown") }
    var allergies by remember {
        mutableStateOf(initial?.allergies?.removeSurrounding("[", "]")?.replace("\"", "") ?: "")
    }
    var bloodType by remember { mutableStateOf(initial?.emergencyBloodType ?: "") }
    var comorbid by remember {
        mutableStateOf(initial?.comorbidities?.removeSurrounding("[", "]")?.replace("\"", "") ?: "")
    }
    // R27 矩阵两输入：分期维 + 颈椎受累维（驱动 M4 运动过滤 / M5 预警灵敏度）
    var stage by remember { mutableStateOf(initial?.diseaseStage ?: "unknown") }
    var spine by remember { mutableStateOf(initial?.spineMobility ?: "none") }
    // v1.0.67 C1：骶髂关节影像分期（null = 未评估）
    var sacroGrade by remember { mutableStateOf(initial?.sacroiliitisGrade) }
    // v10（C9）体重目标区间（kg）——可留空
    var wLow by remember { mutableStateOf(initial?.weightTargetLow?.toString() ?: "") }
    var wHigh by remember { mutableStateOf(initial?.weightTargetHigh?.toString() ?: "") }
    // v1.0.64 B13：生活方式画像（此前 lifestyle 列只被透传、从未采集）
    val life = remember { Lifestyle.fromJson(initial?.lifestyle) }
    var smoking by remember { mutableStateOf(life.smoking) }
    var habit by remember { mutableStateOf(life.exerciseHabit) }
    var sedentary by remember { mutableStateOf(life.sedentaryHours?.toString() ?: "") }
    var sleepH by remember { mutableStateOf(life.sleepHours?.toString() ?: "") }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            ScreenTopBar(
                title = if (initial == null) stringResource(R.string.profile_build_title) else stringResource(R.string.profile_edit_action),
                onBack = onBack,
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    OutlinedButton(
                        onClick = onBack,
                        modifier = Modifier.weight(1f).heightIn(min = Size.touchMin),
                    ) { Text(stringResource(R.string.common_cancel)) }
                    Button(
                        onClick = {
                            if (name.isBlank()) return@Button
                            val now = nowIso()
                            onSave(
                                Profile(
                                    displayName = name.trim(),
                                    diagnosis = diagnosis.trim(),
                                    diagnoseYear = year.toIntOrNull(),
                                    hlaB27 = hla,
                                    diseaseStage = stage,
                                    spineMobility = spine,
                                    sacroiliitisGrade = sacroGrade,
                                    comorbidities = csvToJson(comorbid),
                                    allergies = csvToJson(allergies),
                                    emergencyBloodType = bloodType.trim().ifBlank { null },
                                    // v10：未在本表单呈现的字段必须从 initial 透传——
                                    // 否则保存会把它们重置为实体默认值（潜在数据丢失）
                                    // （lifestyle 自 v1.0.64 B13 起已在本表单采集，不再透传）
                                    lifestyle = Lifestyle(
                                        smoking = smoking,
                                        sedentaryHours = sedentary.toIntOrNull(),
                                        exerciseHabit = habit,
                                        sleepHours = sleepH.toIntOrNull(),
                                    ).toJson(),
                                    emergencyMedSummary = initial?.emergencyMedSummary,
                                    emergencyNote = initial?.emergencyNote,
                                    uiMode = initial?.uiMode ?: "normal",
                                    // v1.0.65 B12：极简进入时刻不在本表单呈现，必须透传——
                                    // 否则保存会把极简态重置成「有 ui_mode 无 minimal_since」的不一致状态
                                    minimalSince = initial?.minimalSince,
                                    weightTargetLow = wLow.toDoubleOrNull(),
                                    weightTargetHigh = wHigh.toDoubleOrNull(),
                                    createdAt = initial?.createdAt ?: now,
                                    updatedAt = now,
                                )
                            )
                        },
                        modifier = Modifier.weight(1f).heightIn(min = Size.touchMin),
                    ) { Text(stringResource(R.string.common_save)) }
                }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            OutlinedTextField(
                name, { name = it },
                label = { Text(stringResource(R.string.profile_display_name_required)) },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                diagnosis, { diagnosis = it },
                label = { Text(stringResource(R.string.profile_diag_required)) },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                year, { year = it.filter { c -> c.isDigit() }.take(4) },
                label = { Text(stringResource(R.string.profile_diagnosis_year)) },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                listOf("positive" to stringResource(R.string.checkup_positive), "negative" to stringResource(R.string.checkup_negative), "unknown" to stringResource(R.string.common_unknown)).forEach { (k, l) ->
                    FilterChip(selected = hla == k, onClick = { hla = k }, label = { Text("B27 $l") })
                }
            }
            Text(stringResource(R.string.profile_stage_field_note), style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                // R1 三态：缓解期 / 控制中 / 发作期（不确定由引擎按发作期保守处理）
                listOf(
                    "stable" to stringResource(R.string.stage_stable),
                    "controlled" to stringResource(R.string.stage_controlled),
                    "flare" to stringResource(R.string.stage_flare),
                    "unknown" to stringResource(R.string.common_uncertain),
                ).forEach { (k, l) ->
                    FilterChip(selected = stage == k, onClick = { stage = k }, label = { Text(l) })
                }
            }
            Text(stringResource(R.string.profile_mobility_degree), style = MaterialTheme.typography.labelMedium)
            Text(
                stringResource(R.string.profile_cervical_note),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                listOf(
                    "none" to stringResource(R.string.profile_mobility_none), "mild" to stringResource(R.string.severity_mild),
                    "moderate" to stringResource(R.string.profile_mobility_moderate), "severe" to stringResource(R.string.severity_severe),
                ).forEach { (k, l) ->
                    FilterChip(selected = spine == k, onClick = { spine = k }, label = { Text(l) })
                }
            }
            OutlinedTextField(
                allergies, { allergies = it },
                label = { Text(stringResource(R.string.profile_allergy_field)) },
                modifier = Modifier.fillMaxWidth(),
            )
            // v1.0.67 C1：骶髂关节影像分期（改良纽约标准 mNY，0–IV）
            Text(stringResource(R.string.profile_sacroiliitis_field), style = MaterialTheme.typography.labelMedium)
            Text(
                stringResource(R.string.profile_sacroiliitis_note),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                FilterChip(
                    selected = sacroGrade == null,
                    onClick = { sacroGrade = null },
                    label = { Text(Labels.sacroiliitisGrade(null)) },
                )
                Labels.SACROILIITIS_KEYS.forEach { k ->
                    FilterChip(
                        selected = sacroGrade == k,
                        onClick = { sacroGrade = k },
                        label = { Text(Labels.sacroiliitisGrade(k)) },
                    )
                }
            }
            OutlinedTextField(
                bloodType, { bloodType = it },
                label = { Text(stringResource(R.string.profile_blood_field)) },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                comorbid, { comorbid = it },
                label = { Text(stringResource(R.string.profile_comorbidity_field)) },
                modifier = Modifier.fillMaxWidth(),
            )
            // v10（C9）体重目标区间：两列并排，可留空
            Text(stringResource(R.string.weight_target_title), style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedTextField(
                    wLow, { wLow = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text(stringResource(R.string.weight_target_low)) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                OutlinedTextField(
                    wHigh, { wHigh = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text(stringResource(R.string.weight_target_high)) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
            }
            Text(
                stringResource(R.string.weight_target_note),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // v1.0.64 B13：生活方式画像（驱动运动页个性化提示 + 知识库置顶）
            Text(stringResource(R.string.profile_lifestyle_title), style = MaterialTheme.typography.labelMedium)
            Text(
                stringResource(R.string.profile_lifestyle_note),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(stringResource(R.string.profile_smoking_field), style = MaterialTheme.typography.labelSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                listOf(
                    Lifestyle.SMOKING_NEVER to stringResource(R.string.profile_smoking_never),
                    Lifestyle.SMOKING_FORMER to stringResource(R.string.profile_smoking_former),
                    Lifestyle.SMOKING_CURRENT to stringResource(R.string.profile_smoking_current),
                    Lifestyle.SMOKING_UNKNOWN to stringResource(R.string.common_unknown),
                ).forEach { (k, l) ->
                    FilterChip(selected = smoking == k, onClick = { smoking = k }, label = { Text(l) })
                }
            }
            Text(stringResource(R.string.profile_habit_field), style = MaterialTheme.typography.labelSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                listOf(
                    Lifestyle.HABIT_NONE to stringResource(R.string.profile_habit_none),
                    Lifestyle.HABIT_OCCASIONAL to stringResource(R.string.profile_habit_occasional),
                    Lifestyle.HABIT_REGULAR to stringResource(R.string.profile_habit_regular),
                    Lifestyle.HABIT_UNKNOWN to stringResource(R.string.common_unknown),
                ).forEach { (k, l) ->
                    FilterChip(selected = habit == k, onClick = { habit = k }, label = { Text(l) })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedTextField(
                    sedentary, { sedentary = it.filter { c -> c.isDigit() }.take(2) },
                    label = { Text(stringResource(R.string.profile_sedentary_field)) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                OutlinedTextField(
                    sleepH, { sleepH = it.filter { c -> c.isDigit() }.take(2) },
                    label = { Text(stringResource(R.string.profile_sleep_field)) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
            }
            Text(
                stringResource(R.string.profile_local_data_note) +
                    stringResource(R.string.stage_update_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("", Modifier.padding(bottom = Spacing.xl))
        }
    }
}

private fun csvToJson(csv: String): String? {
    val items = csv.split("，", ",").map { it.trim() }.filter { it.isNotBlank() }
    if (items.isEmpty()) return null
    return "[" + items.joinToString(",") { "\"$it\"" } + "]"
}
