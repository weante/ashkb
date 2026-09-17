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
                                    comorbidities = csvToJson(comorbid),
                                    allergies = csvToJson(allergies),
                                    emergencyBloodType = bloodType.trim().ifBlank { null },
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
                listOf(
                    "active" to stringResource(R.string.stage_active_with_note),
                    "stable" to stringResource(R.string.stage_stable),
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
