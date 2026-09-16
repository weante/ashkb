package com.ashkb.app.ui.wellness

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.NoMeals
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.ashkb.app.data.entity.DietProfile
import com.ashkb.app.data.entity.FoodAvoidItem
import com.ashkb.app.data.entity.Supplement
import com.ashkb.app.data.entity.SupplementCategory
import com.ashkb.app.data.entity.Vitals
import com.ashkb.app.data.repo.nowIso
import com.ashkb.app.domain.ClinicalThresholds
import com.ashkb.app.domain.Labels
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
    val vitals by vm.vitalsToday.collectAsState()
    val weight by vm.weightToday.collectAsState()
    val weightList by vm.weightRecent.collectAsState()
    val bm by vm.bodyMeasureLatest.collectAsState()
    val supplements by vm.supplements.collectAsState()
    val supLogs by vm.supplementLogsToday.collectAsState()
    val diet by vm.dietProfile.collectAsState()
    val avoids by vm.foodAvoidItems.collectAsState()

    var showVitals by remember { mutableStateOf(false) }
    var showWeight by remember { mutableStateOf(false) }
    var showBodyMeasure by remember { mutableStateOf(false) }
    var showSupplementForm by remember { mutableStateOf(false) }
    var showDietForm by remember { mutableStateOf(false) }
    var showAvoidManage by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(title = "营养与骨健康", onBack = onBack)

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Spacing.lg, end = Spacing.lg,
                top = Spacing.md, bottom = Spacing.xxl,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            // ---- 分组一：体征 ----
            stickyHeader { GroupHeader("体征") }
            item { VitalsHero(vitals = vitals, onEdit = { showVitals = true }) }

            // ---- 分组二：身体成分 ----
            stickyHeader { GroupHeader("身体成分") }
            item {
                SectionCard(
                    title = "体重追踪",
                    subtitle = if (weightList.isEmpty()) null else "近 30 天 ${weightList.size} 条记录",
                    action = {
                        OutlinedButton(onClick = { showWeight = true }) {
                            Text(if (weight != null) "编辑" else "记录")
                        }
                    },
                ) {
                    // DAO 按日期倒序返回，趋势图需要从旧到新
                    val points = weightList.asReversed().map { TrendPoint(it.date, it.weightKg.toFloat()) }
                    if (points.isNotEmpty()) {
                        TrendChart(points = points, unit = "kg", label = "体重")
                        Spacer(Modifier.height(Spacing.md))
                    }
                    StatTile(
                        label = "今日体重",
                        value = weight?.weightKg?.let { "%.1f".format(it) } ?: "未记录",
                        unit = weight?.let { "kg" },
                    )
                }
            }
            item {
                SectionCard(
                    title = "身体指标",
                    action = {
                        OutlinedButton(onClick = { showBodyMeasure = true }) {
                            Text(if (bm != null) "更新" else "录入")
                        }
                    },
                ) {
                    if (bm != null) {
                        KeyValueRow("身高", bm!!.heightCm?.let { "%.0f cm".format(it) } ?: "未填")
                        KeyValueRow("腰围", bm!!.waistCm?.let { "%.0f cm".format(it) } ?: "未填")
                        KeyValueRow("臀围", bm!!.hipCm?.let { "%.0f cm".format(it) } ?: "未填")
                        KeyValueRow("BMI", bm!!.bmi?.let { "%.1f".format(it) } ?: "未填")
                    } else {
                        EmptyState(
                            icon = Icons.Rounded.Straighten,
                            title = "还没有基线数据",
                            body = "录入身高与腰围，可长期跟踪 BMI 变化",
                        )
                    }
                }
            }

            // ---- 分组三：营养与饮食 ----
            stickyHeader { GroupHeader("营养与饮食") }
            item {
                SectionCard(
                    title = "补剂档案",
                    subtitle = "${supplements.size} 种",
                    action = {
                        OutlinedButton(onClick = { showSupplementForm = true }) { Text("添加") }
                    },
                ) {
                    if (supplements.isEmpty()) {
                        EmptyState(
                            icon = Icons.Rounded.Medication,
                            title = "还没有添加补剂",
                            body = "钙 / 维 D / Omega-3 等可在此记录",
                            actionLabel = "添加补剂",
                            onAction = { showSupplementForm = true },
                        )
                    } else {
                        DividerList(supplements, key = { it.id }) { sup ->
                            Column(
                                Modifier.weight(1f),
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
                            if (supLogs.any { it.supId == sup.id && it.status == "done" }) {
                                StatusChip(text = "已服", tone = StatusTone.Success, icon = Icons.Rounded.CheckCircle)
                            } else {
                                TextButton(onClick = {
                                    vm.checkInSupplement(sup, "done", null, null)
                                }) { Text("打卡") }
                            }
                        }
                    }
                }
            }
            item {
                SectionCard(
                    title = "饮食画像",
                    action = {
                        if (diet != null) {
                            OutlinedButton(onClick = { showDietForm = true }) { Text("编辑") }
                        }
                    },
                ) {
                    if (diet != null) {
                        val d = diet!!
                        KeyValueRow("饮食模式", Labels.dietPattern(d.dietPattern))
                        KeyValueRow("海鱼摄入", Labels.seafoodFreq(d.seafoodFreq))
                        KeyValueRow("乳制品", Labels.dairyTolerance(d.dairyTolerant))
                    } else {
                        EmptyState(
                            icon = Icons.Rounded.Restaurant,
                            title = "还没有设置饮食画像",
                            body = "设置后可获得更精准的抗炎饮食建议",
                            actionLabel = "设置画像",
                            onAction = { showDietForm = true },
                        )
                    }
                }
            }
            item {
                SectionCard(
                    title = "忌口清单",
                    subtitle = "${avoids.size} 项",
                    action = {
                        OutlinedButton(onClick = { showAvoidManage = true }) { Text("管理") }
                    },
                ) {
                    if (avoids.isEmpty()) {
                        EmptyState(
                            icon = Icons.Rounded.NoMeals,
                            title = "暂无忌口项",
                            body = "可添加过敏 / 不耐受 / 医生建议忌口项",
                            actionLabel = "添加忌口项",
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
                                Text("查看全部 ${avoids.size} 项")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showVitals) VitalsSheet(vm = vm, onDismiss = { showVitals = false })
    if (showWeight) WeightSheet(vm = vm, onDismiss = { showWeight = false })
    if (showBodyMeasure) BodyMeasureSheet(vm = vm, onDismiss = { showBodyMeasure = false })
    if (showSupplementForm) SupplementSheet(vm = vm, onDismiss = { showSupplementForm = false })
    if (showDietForm) DietSheet(vm = vm, current = diet, onDismiss = { showDietForm = false })
    if (showAvoidManage) AvoidManageSheet(vm = vm, onDismiss = { showAvoidManage = false })
}

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
                    "今日体征",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                FilledTonalButton(onClick = onEdit) {
                    Text(if (vitals != null) "编辑" else "记录")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Box(Modifier.weight(1f)) {
                    StatTile(
                        label = "体温",
                        value = temp?.let { "%.1f".format(it) } ?: "未记录",
                        unit = temp?.let { "℃" },
                        tone = tempTone,
                    )
                }
                Box(Modifier.weight(1f)) {
                    StatTile(
                        label = "血压",
                        value = if (sys != null && dia != null) "$sys/$dia" else "未记录",
                        tone = bpTone,
                    )
                }
                Box(Modifier.weight(1f)) {
                    StatTile(
                        label = "心率",
                        value = hr?.toString() ?: "未记录",
                        unit = hr?.let { "次/分" },
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
    val current by vm.vitalsToday.collectAsState()
    var temp by remember { mutableStateOf(current?.temperature?.toString() ?: "") }
    var sys by remember { mutableStateOf(current?.bpSys?.toString() ?: "") }
    var dia by remember { mutableStateOf(current?.bpDia?.toString() ?: "") }
    var hr by remember { mutableStateOf(current?.heartRate?.toString() ?: "") }
    var notes by remember { mutableStateOf(current?.notes ?: "") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        SheetColumn {
            Text("记录体征", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(temp, { temp = it }, label = { Text("体温 (℃)") }, singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedTextField(sys, { sys = it }, label = { Text("收缩压") },
                    singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(dia, { dia = it }, label = { Text("舒张压") },
                    singleLine = true, modifier = Modifier.weight(1f))
            }
            OutlinedTextField(hr, { hr = it }, label = { Text("心率 (次/分)") }, singleLine = true)
            OutlinedTextField(notes, { notes = it }, label = { Text("备注（可选）") })
            SheetSaveButton(
                text = "保存",
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
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WeightSheet(vm: WellnessViewModel, onDismiss: () -> Unit) {
    val current by vm.weightToday.collectAsState()
    var weight by remember { mutableStateOf(current?.weightKg?.toString() ?: "") }
    var notes by remember { mutableStateOf(current?.notes ?: "") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        SheetColumn {
            Text("记录体重", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(weight, { weight = it }, label = { Text("体重 (kg)") }, singleLine = true)
            OutlinedTextField(notes, { notes = it }, label = { Text("备注（可选）") })
            SheetSaveButton(
                text = "保存",
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
    val current by vm.bodyMeasureLatest.collectAsState()
    var height by remember { mutableStateOf(current?.heightCm?.toString() ?: "") }
    var waist by remember { mutableStateOf(current?.waistCm?.toString() ?: "") }
    var hip by remember { mutableStateOf(current?.hipCm?.toString() ?: "") }
    var bmi by remember { mutableStateOf(current?.bmi?.toString() ?: "") }
    var notes by remember { mutableStateOf(current?.notes ?: "") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        SheetColumn {
            Text("身体指标", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(height, { height = it }, label = { Text("身高 (cm)") }, singleLine = true)
            OutlinedTextField(waist, { waist = it }, label = { Text("腰围 (cm)") }, singleLine = true)
            OutlinedTextField(hip, { hip = it }, label = { Text("臀围 (cm)") }, singleLine = true)
            OutlinedTextField(bmi, { bmi = it }, label = { Text("BMI（可自动）") }, singleLine = true)
            OutlinedTextField(notes, { notes = it }, label = { Text("备注（可选）") })
            SheetSaveButton(
                text = "保存",
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
            Text("添加补剂", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(name, { name = it }, label = { Text("名称") }, singleLine = true)
            OutlinedTextField(brand, { brand = it }, label = { Text("品牌（可选）") }, singleLine = true)
            OutlinedTextField(dose, { dose = it }, label = { Text("剂量（如 1000IU）") }, singleLine = true)
            Text("类别", style = MaterialTheme.typography.labelMedium)
            ChipGroup(
                options = SupplementCategory.entries.map { it.name to it.label },
                selected = category.name,
                onSelect = { key -> category = SupplementCategory.fromKey(key) },
            )
            OutlinedTextField(notes, { notes = it }, label = { Text("备注（可选）") })
            SheetSaveButton(
                text = "保存",
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
            Text("饮食画像", style = MaterialTheme.typography.titleLarge)
            Text("饮食模式", style = MaterialTheme.typography.labelMedium)
            ChipGroup(
                options = listOf(
                    "mixed" to "杂食", "mediterranean" to "地中海式", "vegetarian" to "素食",
                    "vegan" to "纯素", "low_starch" to "低淀粉", "paleo" to "旧石器式",
                ),
                selected = pattern,
                onSelect = { pattern = it },
            )
            Text("海鱼 / 海鲜频率", style = MaterialTheme.typography.labelMedium)
            ChipGroup(
                options = listOf(
                    "never" to "不吃", "rare" to "偶尔", "weekly" to "每周", "frequent" to "经常",
                ),
                selected = seafood,
                onSelect = { seafood = it },
            )
            Text("乳制品耐受", style = MaterialTheme.typography.labelMedium)
            ChipGroup(
                options = listOf(
                    "yes" to "耐受", "no" to "不耐受", "lactose_free_only" to "仅无乳糖",
                ),
                selected = dairy,
                onSelect = { dairy = it },
            )
            OutlinedTextField(notes, { notes = it }, label = { Text("备注（可选）") })
            SheetSaveButton(
                text = "保存",
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
    val items by vm.foodAvoidItems.collectAsState()
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
                Text("忌口清单", style = MaterialTheme.typography.titleLarge)
                Text(
                    "${items.size} 项 · 过敏 / 不耐受 / 医嘱 / 药效冲突",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (items.isEmpty()) {
                    Text(
                        "暂无忌口项，点下方按钮添加",
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
                            label = "删除",
                            confirmTitle = "删除「${item.name}」？",
                            confirmBody = "删除后该忌口项不再出现在清单与知识库提示中，可在备份中恢复。",
                            onConfirm = { vm.deleteFoodAvoid(item.id) },
                        )
                    }
                }
                Button(
                    onClick = { adding = true },
                    modifier = Modifier.fillMaxWidth().heightIn(min = Size.touchMin),
                ) { Text("添加忌口项") }
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
        Text("添加忌口项", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(name, { name = it }, label = { Text("食物名称") }, singleLine = true)
        Text("类别", style = MaterialTheme.typography.labelMedium)
        ChipGroup(
            options = listOf(
                "allergy" to "过敏", "intolerance" to "不耐受", "doctor_advice" to "医嘱",
                "personal_experience" to "个人体验", "drug_interaction" to "药效冲突",
            ),
            selected = category,
            onSelect = { category = it },
        )
        Text("严重程度", style = MaterialTheme.typography.labelMedium)
        ChipGroup(
            options = listOf("high" to "高", "medium" to "中", "low" to "低"),
            selected = severity,
            onSelect = { severity = it },
        )
        SheetSaveButton(
            text = "添加",
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
        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("返回清单") }
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
