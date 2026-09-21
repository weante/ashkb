package com.ashkb.app.ui.wellness

import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.NoMeals
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle


import com.ashkb.app.data.entity.DietProfile
import com.ashkb.app.data.entity.FoodAvoidItem
import com.ashkb.app.data.entity.Supplement
import com.ashkb.app.data.entity.SupplementCategory
import com.ashkb.app.data.entity.Vitals
import com.ashkb.app.data.repo.nowIso
import com.ashkb.app.domain.ClinicalThresholds
import com.ashkb.app.domain.Labels
import com.ashkb.app.domain.WeightTarget
import com.ashkb.app.R
import com.ashkb.app.ui.components.DestructiveAction
import com.ashkb.app.ui.components.DividerList
import com.ashkb.app.ui.components.EmptyState
import com.ashkb.app.ui.components.KeyValueRow
import com.ashkb.app.ui.components.ScreenTopBar
import com.ashkb.app.ui.components.SectionCard
import com.ashkb.app.ui.components.StatTile
import com.ashkb.app.ui.components.StatusChip
import com.ashkb.app.ui.components.TrendChart
import com.ashkb.app.ui.components.TrendPoint
import com.ashkb.app.ui.theme.Size
import com.ashkb.app.ui.theme.Spacing
import com.ashkb.app.ui.theme.StatusTone

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun WellnessScreen(vm: WellnessViewModel, onBack: () -> Unit) {
    val vitals by vm.vitalsToday.collectAsStateWithLifecycle()
    val weight by vm.weightToday.collectAsStateWithLifecycle()
    val weightList by vm.weightRecent.collectAsStateWithLifecycle()
    val profile by vm.profile.collectAsStateWithLifecycle()
    val bm by vm.bodyMeasureLatest.collectAsStateWithLifecycle()
    val supplements by vm.supplements.collectAsStateWithLifecycle()
    val supLogs by vm.supplementLogsToday.collectAsStateWithLifecycle()
    val diet by vm.dietProfile.collectAsStateWithLifecycle()
    val avoids by vm.foodAvoidItems.collectAsStateWithLifecycle()

    var showVitals by remember { mutableStateOf(false) }
    var showWeight by remember { mutableStateOf(false) }
    var showWeightManage by remember { mutableStateOf(false) }
    var showBodyMeasure by remember { mutableStateOf(false) }
    var showSupplementForm by remember { mutableStateOf(false) }
    var showDietForm by remember { mutableStateOf(false) }
    var showAvoidManage by remember { mutableStateOf(false) }
    var historySup by remember { mutableStateOf<Supplement?>(null) }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(title = stringResource(R.string.nutrition_bone_health_title), onBack = onBack)

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Spacing.lg, end = Spacing.lg,
                top = Spacing.md, bottom = Spacing.xxl,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            // ---- 分组一：体征 ----
            stickyHeader { GroupHeader(stringResource(R.string.vitals_short)) }
            item { VitalsHero(vitals = vitals, onEdit = { showVitals = true }) }

            // ---- 分组二：身体成分 ----
            stickyHeader { GroupHeader(stringResource(R.string.vitals_body_composition)) }
            item {
                SectionCard(
                    title = stringResource(R.string.wellness_weight_tracking),
                    subtitle = if (weightList.isEmpty()) null else stringResource(R.string.wellness_weight_records, weightList.size),
                    action = {
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            OutlinedButton(onClick = { showWeight = true }) {
                                Text(if (weight != null) stringResource(R.string.common_edit) else stringResource(R.string.common_record))
                            }
                            if (weightList.isNotEmpty()) {
                                TextButton(onClick = { showWeightManage = true }) {
                                    Text(stringResource(R.string.common_manage))
                                }
                            }
                        }
                    },
                ) {
                    // DAO 按日期倒序返回，趋势图需要从旧到新。
                    // 必须 remember：List.map 每次返回新实例，会让 TrendChart 的入场动画（以 points 为 key）
                    // 在任何一次父级重组时反复从头播放
                    val points = remember(weightList) {
                        weightList.asReversed().map { TrendPoint(it.date, it.weightKg.toFloat()) }
                    }
                    if (points.isNotEmpty()) {
                        TrendChart(points = points, unit = "kg", label = stringResource(R.string.vitals_weight))
                        Spacer(Modifier.height(Spacing.md))
                    }
                    StatTile(
                        label = stringResource(R.string.vitals_today_weight),
                        value = weight?.weightKg?.let { "%.1f".format(it) } ?: stringResource(R.string.common_not_recorded),
                        unit = weight?.let { "kg" },
                    )
                    // v10（C9）：体重目标区间提示（区间由档案设定）
                    WeightTargetHint(weightKg = weight?.weightKg, profile = profile)
                }
            }
            item {
                SectionCard(
                    title = stringResource(R.string.vitals_body_measures),
                    action = {
                        OutlinedButton(onClick = { showBodyMeasure = true }) {
                            Text(if (bm != null) stringResource(R.string.common_update) else stringResource(R.string.common_input_action))
                        }
                    },
                ) {
                    if (bm != null) {
                        KeyValueRow(stringResource(R.string.vitals_height_short), bm!!.heightCm?.let { "%.0f cm".format(it) } ?: stringResource(R.string.common_unfilled))
                        KeyValueRow(stringResource(R.string.vitals_waist), bm!!.waistCm?.let { "%.0f cm".format(it) } ?: stringResource(R.string.common_unfilled))
                        KeyValueRow(stringResource(R.string.vitals_hip), bm!!.hipCm?.let { "%.0f cm".format(it) } ?: stringResource(R.string.common_unfilled))
                        KeyValueRow("BMI", bm!!.bmi?.let { "%.1f".format(it) } ?: stringResource(R.string.common_unfilled))
                    } else {
                        EmptyState(
                            icon = Icons.Rounded.Straighten,
                            title = stringResource(R.string.report_no_baseline),
                            body = stringResource(R.string.vitals_bmi_hint),
                        )
                    }
                }
            }

            // ---- 分组三：营养与饮食 ----
            stickyHeader { GroupHeader(stringResource(R.string.wellness_nutrition_section)) }
            item {
                // 补剂打卡态集合一次性算好：避免每行对全部 supLogs 做 O(N·M) 线性扫描
                val doneIds: Set<String?> = remember(supLogs) {
                    supLogs.asSequence().filter { it.status == "done" }.map { it.supId }.toSet()
                }
                SectionCard(
                    title = stringResource(R.string.nutrition_supplement_archive),
                    subtitle = stringResource(R.string.wellness_supplements_count, supplements.size),
                    action = {
                        OutlinedButton(onClick = { showSupplementForm = true }) { Text(stringResource(R.string.common_add)) }
                    },
                ) {
                    if (supplements.isEmpty()) {
                        EmptyState(
                            icon = Icons.Rounded.Medication,
                            title = stringResource(R.string.nutrition_no_supplements),
                            body = stringResource(R.string.nutrition_supplement_hint),
                            actionLabel = stringResource(R.string.nutrition_add_supplement),
                            onAction = { showSupplementForm = true },
                        )
                    } else {
                        DividerList(supplements, key = { it.id }) { sup ->
                            Column(
                                Modifier.weight(1f).clickable { historySup = sup },
                                verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
                            ) {
                                Text("${sup.name} ${sup.dose}", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    SupplementCategory.fromKey(sup.category).label +
                                        (sup.brand?.let { " · $it" } ?: ""),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (sup.id in doneIds) {
                                StatusChip(text = stringResource(R.string.med_status_taken_short), tone = StatusTone.Success, icon = Icons.Rounded.CheckCircle)
                            } else {
                                TextButton(onClick = {
                                    vm.checkInSupplement(sup, "done", null, null)
                                }) { Text(stringResource(R.string.exercise_checkin_short)) }
                            }
                            DestructiveAction(
                                label = stringResource(R.string.common_delete),
                                confirmTitle = stringResource(R.string.wellness_delete_confirm, sup.name),
                                confirmBody = stringResource(R.string.nutrition_supplement_delete_note),
                                onConfirm = { vm.deleteSupplement(sup.id) },
                            )
                        }
                        Text(
                            stringResource(R.string.nutrition_supplement_history_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item {
                SectionCard(
                    title = stringResource(R.string.nutrition_diet_profile),
                    action = {
                        if (diet != null) {
                            OutlinedButton(onClick = { showDietForm = true }) { Text(stringResource(R.string.common_edit)) }
                        }
                    },
                ) {
                    if (diet != null) {
                        val d = diet!!
                        KeyValueRow(stringResource(R.string.nutrition_diet_pattern), Labels.dietPattern(d.dietPattern))
                        KeyValueRow(stringResource(R.string.nutrition_fish_intake), Labels.seafoodFreq(d.seafoodFreq))
                        KeyValueRow(stringResource(R.string.nutrition_dairy), Labels.dairyTolerance(d.dairyTolerant))
                    } else {
                        EmptyState(
                            icon = Icons.Rounded.Restaurant,
                            title = stringResource(R.string.nutrition_profile_empty),
                            body = stringResource(R.string.nutrition_profile_benefit),
                            actionLabel = stringResource(R.string.nutrition_set_profile),
                            onAction = { showDietForm = true },
                        )
                    }
                }
            }
            item {
                SectionCard(
                    title = stringResource(R.string.nutrition_avoid_list),
                    subtitle = stringResource(R.string.wellness_avoid_count, avoids.size),
                    action = {
                        OutlinedButton(onClick = { showAvoidManage = true }) { Text(stringResource(R.string.common_manage)) }
                    },
                ) {
                    if (avoids.isEmpty()) {
                        EmptyState(
                            icon = Icons.Rounded.NoMeals,
                            title = stringResource(R.string.nutrition_no_avoid_items),
                            body = stringResource(R.string.nutrition_avoid_hint),
                            actionLabel = stringResource(R.string.nutrition_add_avoid_item),
                            onAction = { showAvoidManage = true },
                        )
                    } else {
                        DividerList(avoids.take(5)) { item ->
                            Text(
                                item.name,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                "${Labels.foodAvoidCategory(item.category)} · ${Labels.foodAvoidSeverity(item.severity)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (avoids.size > 5) {
                            TextButton(onClick = { showAvoidManage = true }) {
                                Text(stringResource(R.string.wellness_view_all_avoids, avoids.size))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showVitals) VitalsSheet(vm = vm, onDismiss = { showVitals = false })
    if (showWeight) WeightSheet(vm = vm, onDismiss = { showWeight = false })
    if (showWeightManage) WeightManageSheet(vm = vm, onDismiss = { showWeightManage = false })
    if (showBodyMeasure) BodyMeasureSheet(vm = vm, onDismiss = { showBodyMeasure = false })
    if (showSupplementForm) SupplementSheet(vm = vm, onDismiss = { showSupplementForm = false })
    if (showDietForm) DietSheet(vm = vm, current = diet, onDismiss = { showDietForm = false })
    if (showAvoidManage) AvoidManageSheet(vm = vm, onDismiss = { showAvoidManage = false })
    historySup?.let { sup -> SupplementHistorySheet(vm = vm, sup = sup, onDismiss = { historySup = null }) }
}

/**
 * v10（C9）：体重目标区间提示。
 *
 * 判定逻辑在纯函数 `domain/WeightTarget`（可单测）；这里只呈现结论。
 * 未设目标时不打扰用户（只在填了区间后出现）；只填一侧提示补全。
 */
@Composable
private fun WeightTargetHint(weightKg: Double?, profile: com.ashkb.app.data.entity.Profile?) {
    val low = profile?.weightTargetLow
    val high = profile?.weightTargetHigh
    val status = WeightTarget.status(weightKg, low, high)
    if (status == WeightTarget.Status.NO_TARGET) return

    Spacer(Modifier.height(Spacing.sm))
    val range = WeightTarget.normalize(low, high)
    when (status) {
        WeightTarget.Status.INCOMPLETE -> Text(
            stringResource(R.string.weight_target_incomplete),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        WeightTarget.Status.IN_RANGE -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            StatusChip(stringResource(R.string.weight_target_in_range), StatusTone.Success, Icons.Rounded.CheckCircle)
            range?.let {
                Text(
                    stringResource(R.string.weight_target_range_label, fmtKg(it.first), fmtKg(it.second)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        WeightTarget.Status.BELOW, WeightTarget.Status.ABOVE -> {
            val dev = WeightTarget.deviation(weightKg, low, high) ?: 0.0
            val text = if (status == WeightTarget.Status.BELOW) {
                stringResource(R.string.weight_target_below, fmtKg(-dev))
            } else {
                stringResource(R.string.weight_target_above, fmtKg(dev))
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                StatusChip(text, StatusTone.Warning, Icons.Rounded.WarningAmber)
                range?.let {
                    Text(
                        stringResource(R.string.weight_target_range_label, fmtKg(it.first), fmtKg(it.second)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        else -> Unit
    }
}

/** 体重数值显示：整数不带小数点，其余一位小数。 */
private fun fmtKg(v: Double): String = if (v == v.toLong().toDouble()) v.toLong().toString() else "%.1f".format(v)

/** 分组头：sticky。吸附时用页面底色融入背景，底边 1dp 分隔。 */
@Composable
private fun GroupHeader(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.fillMaxWidth()) {
            Text(
                text,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = Spacing.xs),
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

/** 今日体征 hero：体温 / 血压 / 心率三格 StatTile，异常格按 ClinicalThresholds 着色。 */
@Composable
private fun VitalsHero(vitals: Vitals?, onEdit: () -> Unit) {
    val temp = vitals?.temperature
    val sys = vitals?.bpSys
    val dia = vitals?.bpDia
    val hr = vitals?.heartRate

    val tempTone = when {
        temp == null -> StatusTone.Neutral
        temp >= ClinicalThresholds.FEVER_ALERT -> StatusTone.Danger
        temp >= ClinicalThresholds.FEVER_LOW -> StatusTone.Warning
        else -> StatusTone.Success
    }
    val bpTone = when {
        sys == null || dia == null -> StatusTone.Neutral
        sys >= ClinicalThresholds.BP_HIGH_SYS || dia >= ClinicalThresholds.BP_HIGH_DIA -> StatusTone.Danger
        sys <= ClinicalThresholds.BP_LOW_SYS -> StatusTone.Warning
        else -> StatusTone.Success
    }
    val hrTone = when {
        hr == null -> StatusTone.Neutral
        hr > ClinicalThresholds.HR_HIGH || hr < ClinicalThresholds.HR_LOW -> StatusTone.Warning
        else -> StatusTone.Success
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(min = Size.heroMinHeight)
                .padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.vitals_today_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                FilledTonalButton(onClick = onEdit) {
                    Text(if (vitals != null) stringResource(R.string.common_edit) else stringResource(R.string.common_record))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Box(Modifier.weight(1f)) {
                    StatTile(
                        label = stringResource(R.string.vitals_temperature_short),
                        value = temp?.let { "%.1f".format(it) } ?: stringResource(R.string.common_not_recorded),
                        unit = temp?.let { "℃" },
                        tone = tempTone,
                    )
                }
                Box(Modifier.weight(1f)) {
                    StatTile(
                        label = stringResource(R.string.vitals_bp),
                        value = if (sys != null && dia != null) "$sys/$dia" else stringResource(R.string.common_not_recorded),
                        tone = bpTone,
                    )
                }
                Box(Modifier.weight(1f)) {
                    StatTile(
                        label = stringResource(R.string.vitals_heart_rate),
                        value = hr?.toString() ?: stringResource(R.string.common_not_recorded),
                        unit = hr?.let { stringResource(R.string.vitals_bpm_unit) },
                        tone = hrTone,
                    )
                }
            }
            vitals?.notes?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 表单：多字段一律 ModalBottomSheet（半屏可拖、键盘弹起体验远好于 AlertDialog）
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VitalsSheet(vm: WellnessViewModel, onDismiss: () -> Unit) {
    val current by vm.vitalsToday.collectAsStateWithLifecycle()
    var temp by remember { mutableStateOf(current?.temperature?.toString() ?: "") }
    var sys by remember { mutableStateOf(current?.bpSys?.toString() ?: "") }
    var dia by remember { mutableStateOf(current?.bpDia?.toString() ?: "") }
    var hr by remember { mutableStateOf(current?.heartRate?.toString() ?: "") }
    var notes by remember { mutableStateOf(current?.notes ?: "") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        SheetColumn {
            Text(stringResource(R.string.vitals_record_action), style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(temp, { temp = it }, label = { Text(stringResource(R.string.vitals_temperature_field)) }, singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedTextField(sys, { sys = it }, label = { Text(stringResource(R.string.vitals_bp_systolic)) },
                    singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(dia, { dia = it }, label = { Text(stringResource(R.string.vitals_bp_diastolic)) },
                    singleLine = true, modifier = Modifier.weight(1f))
            }
            OutlinedTextField(hr, { hr = it }, label = { Text(stringResource(R.string.vitals_heart_rate_field)) }, singleLine = true)
            OutlinedTextField(notes, { notes = it }, label = { Text(stringResource(R.string.common_notes_optional)) })
            SheetSaveButton(
                text = stringResource(R.string.common_save),
                onClick = {
                    vm.saveVitals(
                        temperature = temp.toDoubleOrNull(),
                        bpSys = sys.toIntOrNull(),
                        bpDia = dia.toIntOrNull(),
                        heartRate = hr.toIntOrNull(),
                        notes = notes.ifBlank { null },
                    )
                    onDismiss()
                },
            )
            if (current != null) {
                DestructiveAction(
                    label = stringResource(R.string.wellness_delete_today_vitals),
                    confirmTitle = stringResource(R.string.wellness_delete_vitals_confirm),
                    confirmBody = stringResource(R.string.wellness_delete_vitals_note),
                    onConfirm = {
                        vm.deleteVitalsToday()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WeightSheet(vm: WellnessViewModel, onDismiss: () -> Unit) {
    val current by vm.weightToday.collectAsStateWithLifecycle()
    var weight by remember { mutableStateOf(current?.weightKg?.toString() ?: "") }
    var notes by remember { mutableStateOf(current?.notes ?: "") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        SheetColumn {
            Text(stringResource(R.string.vitals_record_weight), style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(weight, { weight = it }, label = { Text(stringResource(R.string.vitals_weight_field)) }, singleLine = true)
            OutlinedTextField(notes, { notes = it }, label = { Text(stringResource(R.string.common_notes_optional)) })
            SheetSaveButton(
                text = stringResource(R.string.common_save),
                enabled = weight.toDoubleOrNull() != null,
                onClick = {
                    weight.toDoubleOrNull()?.let { w ->
                        vm.saveWeight(w, notes.ifBlank { null })
                        onDismiss()
                    }
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BodyMeasureSheet(vm: WellnessViewModel, onDismiss: () -> Unit) {
    val current by vm.bodyMeasureLatest.collectAsStateWithLifecycle()
    val weightList by vm.weightRecent.collectAsStateWithLifecycle()
    var height by remember { mutableStateOf(current?.heightCm?.toString() ?: "") }
    var waist by remember { mutableStateOf(current?.waistCm?.toString() ?: "") }
    var hip by remember { mutableStateOf(current?.hipCm?.toString() ?: "") }
    var bmi by remember { mutableStateOf(current?.bmi?.toString() ?: "") }
    var notes by remember { mutableStateOf(current?.notes ?: "") }

    // U2：身高 + 最新体重齐备 → BMI 自动回填（仍可手动覆盖）；身高明显异常（<50 或 >250cm）不参与计算
    val weightKg = weightList.firstOrNull()?.weightKg
    val heightNum = height.toDoubleOrNull()
    val autoBmi = if (weightKg != null && heightNum != null && heightNum in 50.0..250.0) {
        String.format(java.util.Locale.US, "%.1f", weightKg / ((heightNum / 100) * (heightNum / 100)))
    } else null
    LaunchedEffect(autoBmi) { if (autoBmi != null) bmi = autoBmi }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        SheetColumn {
            Text(stringResource(R.string.vitals_body_measures), style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(height, { height = it }, label = { Text(stringResource(R.string.vitals_height_cm)) }, singleLine = true)
            OutlinedTextField(waist, { waist = it }, label = { Text(stringResource(R.string.vitals_waist_cm)) }, singleLine = true)
            OutlinedTextField(hip, { hip = it }, label = { Text(stringResource(R.string.vitals_hip_cm)) }, singleLine = true)
            OutlinedTextField(bmi, { bmi = it }, label = { Text(stringResource(R.string.profile_bmi_auto)) }, singleLine = true)
            Text(
                if (weightKg != null) stringResource(R.string.vitals_bmi_auto_note, "%.1f".format(weightKg))
                else stringResource(R.string.vitals_bmi_need_weight),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(notes, { notes = it }, label = { Text(stringResource(R.string.common_notes_optional)) })
            SheetSaveButton(
                text = stringResource(R.string.common_save),
                onClick = {
                    vm.saveBodyMeasure(
                        heightCm = height.toDoubleOrNull(),
                        waistCm = waist.toDoubleOrNull(),
                        hipCm = hip.toDoubleOrNull(),
                        bmi = bmi.toDoubleOrNull(),
                        notes = notes.ifBlank { null },
                    )
                    onDismiss()
                },
            )
            if (current != null) {
                DestructiveAction(
                    label = stringResource(R.string.wellness_delete_this_record),
                    confirmTitle = stringResource(R.string.wellness_delete_body_confirm),
                    confirmBody = stringResource(R.string.wellness_delete_body_note),
                    onConfirm = {
                        vm.deleteBodyMeasureLatest()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun SupplementSheet(vm: WellnessViewModel, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var brand by remember { mutableStateOf("") }
    var dose by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(SupplementCategory.OTHER) }
    var notes by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        SheetColumn {
            Text(stringResource(R.string.nutrition_add_supplement), style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.common_name)) }, singleLine = true)
            OutlinedTextField(brand, { brand = it }, label = { Text(stringResource(R.string.nutrition_brand_field)) }, singleLine = true)
            OutlinedTextField(dose, { dose = it }, label = { Text(stringResource(R.string.nutrition_dose_field)) }, singleLine = true)
            Text(stringResource(R.string.common_category), style = MaterialTheme.typography.labelMedium)
            ChipGroup(
                options = SupplementCategory.entries.map { it.name to it.label },
                selected = category.name,
                onSelect = { key -> category = SupplementCategory.fromKey(key) },
            )
            OutlinedTextField(notes, { notes = it }, label = { Text(stringResource(R.string.common_notes_optional)) })
            SheetSaveButton(
                text = stringResource(R.string.common_save),
                enabled = name.isNotBlank() && dose.isNotBlank(),
                onClick = {
                    if (name.isNotBlank() && dose.isNotBlank()) {
                        vm.saveSupplement(
                            Supplement(
                                id = "", name = name.trim(), brand = brand.ifBlank { null },
                                category = category.name, dose = dose.trim(),
                                notes = notes.ifBlank { null },
                                createdAt = nowIso(), updatedAt = nowIso(),
                            )
                        )
                        onDismiss()
                    }
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun DietSheet(vm: WellnessViewModel, current: DietProfile?, onDismiss: () -> Unit) {
    var pattern by remember { mutableStateOf(current?.dietPattern ?: "mixed") }
    var seafood by remember { mutableStateOf(current?.seafoodFreq ?: "rare") }
    var dairy by remember { mutableStateOf(current?.dairyTolerant ?: "yes") }
    var notes by remember { mutableStateOf(current?.notes ?: "") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        SheetColumn {
            Text(stringResource(R.string.nutrition_diet_profile), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.nutrition_diet_pattern), style = MaterialTheme.typography.labelMedium)
            ChipGroup(
                options = listOf(
                    "mixed" to stringResource(R.string.nutrition_diet_omnivore), "mediterranean" to stringResource(R.string.nutrition_diet_med), "vegetarian" to stringResource(R.string.nutrition_diet_vegetarian),
                    "vegan" to stringResource(R.string.nutrition_diet_vegan), "low_starch" to stringResource(R.string.nutrition_diet_low_starch), "paleo" to stringResource(R.string.nutrition_diet_paleo),
                ),
                selected = pattern,
                onSelect = { pattern = it },
            )
            Text(stringResource(R.string.nutrition_fish_frequency), style = MaterialTheme.typography.labelMedium)
            ChipGroup(
                options = listOf(
                    "never" to stringResource(R.string.nutrition_fish_never), "rare" to stringResource(R.string.nutrition_fish_occasionally), "weekly" to stringResource(R.string.med_freq_weekly), "frequent" to stringResource(R.string.nutrition_fish_often),
                ),
                selected = seafood,
                onSelect = { seafood = it },
            )
            Text(stringResource(R.string.nutrition_dairy_tolerance), style = MaterialTheme.typography.labelMedium)
            ChipGroup(
                options = listOf(
                    "yes" to stringResource(R.string.nutrition_tolerance_tag), "no" to stringResource(R.string.nutrition_intolerance), "lactose_free_only" to stringResource(R.string.nutrition_diet_lactose_free),
                ),
                selected = dairy,
                onSelect = { dairy = it },
            )
            OutlinedTextField(notes, { notes = it }, label = { Text(stringResource(R.string.common_notes_optional)) })
            SheetSaveButton(
                text = stringResource(R.string.common_save),
                onClick = {
                    vm.saveDietProfile(
                        DietProfile(
                            id = 1, dietPattern = pattern,
                            seafoodFreq = seafood, dairyTolerant = dairy,
                            notes = notes.ifBlank { null },
                            updatedAt = nowIso(),
                        )
                    )
                    onDismiss()
                },
            )
            if (current != null) {
                DestructiveAction(
                    label = stringResource(R.string.wellness_clear_diet_profile),
                    confirmTitle = stringResource(R.string.nutrition_delete_profile_confirm),
                    confirmBody = stringResource(R.string.nutrition_delete_profile_note),
                    onConfirm = {
                        vm.deleteDietProfile()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * 忌口管理：同一 sheet 内两步（列表 ⇄ 添加），不再 dialog 套 dialog。
 * 删除走 DestructiveAction 二次确认。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AvoidManageSheet(vm: WellnessViewModel, onDismiss: () -> Unit) {
    val items by vm.foodAvoidItems.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        if (adding) {
            AvoidAddStep(
                onSave = { item ->
                    vm.saveFoodAvoid(item)
                    adding = false
                },
                onBack = { adding = false },
            )
        } else {
            SheetColumn {
                Text(stringResource(R.string.nutrition_avoid_list), style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(R.string.wellness_avoid_section_count, items.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (items.isEmpty()) {
                    Text(
                        stringResource(R.string.nutrition_avoid_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    DividerList(items, key = { it.id }) { item ->
                        Column(
                            Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
                        ) {
                            Text(item.name, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "${Labels.foodAvoidCategory(item.category)} · ${Labels.foodAvoidSeverity(item.severity)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        DestructiveAction(
                            label = stringResource(R.string.common_delete),
                            confirmTitle = stringResource(R.string.wellness_delete_confirm, item.name),
                            confirmBody = stringResource(R.string.nutrition_avoid_delete_note),
                            onConfirm = { vm.deleteFoodAvoid(item.id) },
                        )
                    }
                }
                Button(
                    onClick = { adding = true },
                    modifier = Modifier.fillMaxWidth().heightIn(min = Size.touchMin),
                ) { Text(stringResource(R.string.nutrition_add_avoid_item)) }
            }
        }
    }
}

@Composable
private fun AvoidAddStep(onSave: (FoodAvoidItem) -> Unit, onBack: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("allergy") }
    var severity by remember { mutableStateOf("medium") }

    SheetColumn {
        Text(stringResource(R.string.nutrition_add_avoid_item), style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.nutrition_food_name)) }, singleLine = true)
        Text(stringResource(R.string.common_category), style = MaterialTheme.typography.labelMedium)
        ChipGroup(
            options = listOf(
                "allergy" to stringResource(R.string.profile_allergy_short), "intolerance" to stringResource(R.string.nutrition_intolerance), "doctor_advice" to stringResource(R.string.checkup_doctor_advice),
                "personal_experience" to stringResource(R.string.knowledge_personal_experience_tag), "drug_interaction" to stringResource(R.string.knowledge_drug_conflict),
            ),
            selected = category,
            onSelect = { category = it },
        )
        Text(stringResource(R.string.symptom_severity), style = MaterialTheme.typography.labelMedium)
        ChipGroup(
            options = listOf("high" to stringResource(R.string.severity_high), "medium" to stringResource(R.string.severity_moderate), "low" to stringResource(R.string.severity_low)),
            selected = severity,
            onSelect = { severity = it },
        )
        SheetSaveButton(
            text = stringResource(R.string.common_add),
            enabled = name.isNotBlank(),
            onClick = {
                if (name.isNotBlank()) {
                    onSave(
                        FoodAvoidItem(
                            id = "", name = name.trim(), category = category,
                            severity = severity, notes = null,
                            createdAt = nowIso(), updatedAt = nowIso(),
                        )
                    )
                }
            },
        )
        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.knowledge_back_to_list)) }
    }
}

/** sheet 内容列的统一骨架：横向留白 + 键盘避让 + 底部安全距离。 */
@Composable
private fun SheetColumn(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.lg)
            .padding(bottom = Spacing.xl)
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        content = content,
    )
}

@Composable
private fun SheetSaveButton(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = Size.touchMin),
    ) { Text(text) }
}

/** 单选 chip 组：FlowRow 自动换行，触摸目标 ≥48dp，枚举 key 不出现在 UI。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipGroup(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        options.forEach { (key, label) ->
            FilterChip(
                selected = selected == key,
                onClick = { onSelect(key) },
                label = { Text(label) },
                modifier = Modifier.heightIn(min = Size.touchMin),
            )
        }
    }
}

/** U3 补剂服用历史：近 90 天打卡（done）按日倒序；「我什么时候吃过」一查即知。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SupplementHistorySheet(vm: WellnessViewModel, sup: Supplement, onDismiss: () -> Unit) {
    val history by remember(sup.id) { vm.observeSupplementHistory(sup) }.collectAsStateWithLifecycle(initialValue = emptyList())

    ModalBottomSheet(onDismissRequest = onDismiss) {
        SheetColumn {
            Text("${sup.name} ${sup.dose}", style = MaterialTheme.typography.titleLarge)
            Text(
                if (history.isEmpty()) stringResource(R.string.nutrition_supplement_history_empty, sup.name)
                else stringResource(R.string.nutrition_supplement_history_count, history.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (history.isNotEmpty()) {
                DividerList(history, key = { it.id }) { log ->
                    Column(
                        Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
                    ) {
                        Text(log.date, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            stringResource(
                                R.string.nutrition_supplement_history_entry,
                                (log.takenAt ?: log.recordedAt).take(16).replace("T", " "),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** U4 体重记录管理：近 30 天逐条删除（误录）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WeightManageSheet(vm: WellnessViewModel, onDismiss: () -> Unit) {
    val weightList by vm.weightRecent.collectAsStateWithLifecycle()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        SheetColumn {
            Text(stringResource(R.string.wellness_weight_manage_title), style = MaterialTheme.typography.titleLarge)
            if (weightList.isEmpty()) {
                Text(
                    stringResource(R.string.common_no_records),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                DividerList(weightList, key = { it.id }) { log ->
                    Column(
                        Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
                    ) {
                        Text(
                            stringResource(R.string.wellness_weight_entry, log.date, "%.1f".format(log.weightKg)),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        log.notes?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    DestructiveAction(
                        label = stringResource(R.string.common_delete),
                        confirmTitle = stringResource(R.string.wellness_delete_weight_confirm, log.date),
                        confirmBody = stringResource(R.string.wellness_delete_weight_note),
                        onConfirm = { vm.deleteWeight(log.id) },
                    )
                }
            }
        }
    }
}

