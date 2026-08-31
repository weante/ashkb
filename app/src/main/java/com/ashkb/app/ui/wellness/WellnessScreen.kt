package com.ashkb.app.ui.wellness

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ashkb.app.data.entity.BodyMeasure
import com.ashkb.app.data.entity.DietProfile
import com.ashkb.app.data.entity.FoodAvoidItem
import com.ashkb.app.data.entity.Supplement
import com.ashkb.app.data.entity.SupplementCategory
import com.ashkb.app.data.entity.Vitals
import com.ashkb.app.data.repo.nowIso
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
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
    var showAvoidForm by remember { mutableStateOf(false) }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { Spacer(Modifier.height(16.dp)) }

        // ---- 今日体征 ----
        item {
            SectionCard(title = "今日体征") {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("体温 / 血压 / 心率", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            if (vitals != null) buildString {
                                vitals!!.temperature?.let { append("${it}℃  ") }
                                if (vitals!!.bpSys != null && vitals!!.bpDia != null) {
                                    append("${vitals!!.bpSys}/${vitals!!.bpDia}  ")
                                }
                                vitals!!.heartRate?.let { append("${it}bpm") }
                                if (isEmpty()) append("已记录（部分字段）")
                            } else "今日未记录",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OutlinedButton(onClick = { showVitals = true }) { Text(if (vitals != null) "编辑" else "记录") }
                }
            }
        }

        // ---- 体重追踪 ----
        item {
            SectionCard(title = "体重追踪") {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (weight != null) "${weight!!.weightKg} kg" else "今日未记录",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "近 30 天 ${weightList.size} 条记录",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OutlinedButton(onClick = { showWeight = true }) { Text(if (weight != null) "编辑" else "记录") }
                }
            }
        }

        // ---- 身体指标 ----
        item {
            SectionCard(title = "身体指标") {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        if (bm != null) {
                            bm!!.heightCm?.let { Text("身高：${it} cm") }
                            bm!!.waistCm?.let { Text("腰围：${it} cm") }
                            bm!!.bmi?.let { Text("BMI：$it", style = MaterialTheme.typography.bodyMedium) }
                        } else {
                            Text("未记录基线数据", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    OutlinedButton(onClick = { showBodyMeasure = true }) { Text(if (bm != null) "更新" else "录入") }
                }
            }
        }

        // ---- 补剂管理 ----
        item {
            SectionCard(title = "补剂档案（${supplements.size} 种）") {
                if (supplements.isEmpty()) {
                    Text("暂无补剂——钙 / 维 D / Omega-3 等可在此记录",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                } else {
                    supplements.forEach { sup ->
                        val done = supLogs.any { it.supId == sup.id && it.status == "done" }
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("${sup.name} ${sup.dose}", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    SupplementCategory.fromKey(sup.category).label,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (done) {
                                Text("✓ 已服", color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.bodySmall)
                            } else {
                                TextButton(onClick = {
                                    vm.checkInSupplement(sup, "done", null, null)
                                }) { Text("打卡") }
                            }
                        }
                        HorizontalDivider()
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { showSupplementForm = true }) { Text("添加补剂") }
                }
            }
        }

        // ---- 饮食画像 ----
        item {
            SectionCard(title = "饮食画像") {
                if (diet != null) {
                    val d = diet!!
                    Text("饮食模式：${dietPatternLabel(d.dietPattern)}")
                    d.seafoodFreq?.let { Text("海鱼摄入：${freqLabel(it)}") }
                    d.dairyTolerant?.let { Text("乳制品：${dairyLabel(it)}") }
                    Spacer(Modifier.height(4.dp))
                } else {
                    Text("未设置饮食画像——设置后可获得更精准的抗炎饮食建议",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                }
                OutlinedButton(onClick = { showDietForm = true }) {
                    Text(if (diet != null) "编辑画像" else "设置画像")
                }
            }
        }

        // ---- 忌口清单 ----
        item {
            SectionCard(title = "忌口清单（${avoids.size} 项）") {
                if (avoids.isEmpty()) {
                    Text("可添加过敏 / 不耐受 / 医生建议忌口项",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    avoids.take(5).forEach { item ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                            Text(item.name, Modifier.weight(1f))
                            Text(avoidCategoryLabel(item.category),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (avoids.size > 5) {
                        Text("... 还有 ${avoids.size - 5} 项",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(4.dp))
                OutlinedButton(onClick = { showAvoidForm = true }) { Text("管理忌口") }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }

    // ---- 弹窗 ----
    if (showVitals) VitalsDialog(vm = vm, onDismiss = { showVitals = false })
    if (showWeight) WeightDialog(vm = vm, onDismiss = { showWeight = false })
    if (showBodyMeasure) BodyMeasureDialog(vm = vm, onDismiss = { showBodyMeasure = false })
    if (showSupplementForm) SupplementFormDialog(vm = vm, onDismiss = { showSupplementForm = false })
    if (showDietForm) DietFormDialog(vm = vm, current = diet, onDismiss = { showDietForm = false })
    if (showAvoidForm) AvoidListDialog(vm = vm, onDismiss = { showAvoidForm = false })
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp))
            content()
        }
    }
}

// ===== 体征录入弹窗 =====
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VitalsDialog(vm: WellnessViewModel, onDismiss: () -> Unit) {
    val current by vm.vitalsToday.collectAsState()
    var temp by remember { mutableStateOf(current?.temperature?.toString() ?: "") }
    var sys by remember { mutableStateOf(current?.bpSys?.toString() ?: "") }
    var dia by remember { mutableStateOf(current?.bpDia?.toString() ?: "") }
    var hr by remember { mutableStateOf(current?.heartRate?.toString() ?: "") }
    var notes by remember { mutableStateOf(current?.notes ?: "") }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("记录体征") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(temp, { temp = it }, label = { Text("体温 (℃)") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(sys, { sys = it }, label = { Text("收缩压") },
                        singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(dia, { dia = it }, label = { Text("舒张压") },
                        singleLine = true, modifier = Modifier.weight(1f))
                }
                OutlinedTextField(hr, { hr = it }, label = { Text("心率 (次/分)") }, singleLine = true)
                OutlinedTextField(notes, { notes = it }, label = { Text("备注") })
            }
        },
        confirmButton = {
            TextButton(onClick = {
                vm.saveVitals(
                    temperature = temp.toDoubleOrNull(),
                    bpSys = sys.toIntOrNull(),
                    bpDia = dia.toIntOrNull(),
                    heartRate = hr.toIntOrNull(),
                    notes = notes.ifBlank { null },
                )
                onDismiss()
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

// ===== 体重录入弹窗 =====
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WeightDialog(vm: WellnessViewModel, onDismiss: () -> Unit) {
    val current by vm.weightToday.collectAsState()
    var weight by remember { mutableStateOf(current?.weightKg?.toString() ?: "") }
    var notes by remember { mutableStateOf(current?.notes ?: "") }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("记录体重") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(weight, { weight = it }, label = { Text("体重 (kg)") }, singleLine = true)
                OutlinedTextField(notes, { notes = it }, label = { Text("备注") })
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    weight.toDoubleOrNull()?.let { w ->
                        vm.saveWeight(w, notes.ifBlank { null })
                        onDismiss()
                    }
                },
                enabled = weight.toDoubleOrNull() != null,
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

// ===== 身体指标弹窗 =====
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BodyMeasureDialog(vm: WellnessViewModel, onDismiss: () -> Unit) {
    val current by vm.bodyMeasureLatest.collectAsState()
    var height by remember { mutableStateOf(current?.heightCm?.toString() ?: "") }
    var waist by remember { mutableStateOf(current?.waistCm?.toString() ?: "") }
    var hip by remember { mutableStateOf(current?.hipCm?.toString() ?: "") }
    var bmi by remember { mutableStateOf(current?.bmi?.toString() ?: "") }
    var notes by remember { mutableStateOf(current?.notes ?: "") }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("身体指标") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(height, { height = it }, label = { Text("身高 (cm)") }, singleLine = true)
                OutlinedTextField(waist, { waist = it }, label = { Text("腰围 (cm)") }, singleLine = true)
                OutlinedTextField(hip, { hip = it }, label = { Text("臀围 (cm)") }, singleLine = true)
                OutlinedTextField(bmi, { bmi = it }, label = { Text("BMI（可自动）") }, singleLine = true)
                OutlinedTextField(notes, { notes = it }, label = { Text("备注") })
            }
        },
        confirmButton = {
            TextButton(onClick = {
                vm.saveBodyMeasure(
                    heightCm = height.toDoubleOrNull(),
                    waistCm = waist.toDoubleOrNull(),
                    hipCm = hip.toDoubleOrNull(),
                    bmi = bmi.toDoubleOrNull(),
                    notes = notes.ifBlank { null },
                )
                onDismiss()
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

// ===== 补剂表单弹窗 =====
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SupplementFormDialog(vm: WellnessViewModel, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var brand by remember { mutableStateOf("") }
    var dose by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(SupplementCategory.OTHER) }
    var notes by remember { mutableStateOf("") }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加补剂") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("名称") }, singleLine = true)
                OutlinedTextField(brand, { brand = it }, label = { Text("品牌") }, singleLine = true)
                OutlinedTextField(dose, { dose = it }, label = { Text("剂量（如 1000IU）") }, singleLine = true)
                Text("类别", style = MaterialTheme.typography.bodySmall)
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    SupplementCategory.entries.forEach { cat ->
                        androidx.compose.material3.FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat.label) },
                        )
                    }
                }
                OutlinedTextField(notes, { notes = it }, label = { Text("备注") })
            }
        },
        confirmButton = {
            TextButton(
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
                enabled = name.isNotBlank() && dose.isNotBlank(),
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

// ===== 饮食画像表单 =====
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DietFormDialog(vm: WellnessViewModel, current: DietProfile?, onDismiss: () -> Unit) {
    var pattern by remember { mutableStateOf(current?.dietPattern ?: "mixed") }
    var seafood by remember { mutableStateOf(current?.seafoodFreq ?: "") }
    var dairy by remember { mutableStateOf(current?.dairyTolerant ?: "") }
    var notes by remember { mutableStateOf(current?.notes ?: "") }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("饮食画像") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("饮食模式", style = MaterialTheme.typography.bodySmall)
                val patterns = listOf(
                    "mixed" to "杂食", "mediterranean" to "地中海式",
                    "vegetarian" to "素食", "vegan" to "纯素",
                    "low_starch" to "低淀粉", "paleo" to "旧石器",
                )
                patterns.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { (key, label) ->
                            androidx.compose.material3.FilterChip(
                                selected = pattern == key,
                                onClick = { pattern = key },
                                label = { Text(label) },
                            )
                        }
                    }
                }
                OutlinedTextField(seafood, { seafood = it },
                    label = { Text("海鱼/海鲜频率（rare/weekly/frequent）") }, singleLine = true)
                OutlinedTextField(dairy, { dairy = it },
                    label = { Text("乳制品耐受（yes/no/lactose_free_only）") }, singleLine = true)
                OutlinedTextField(notes, { notes = it }, label = { Text("备注") })
            }
        },
        confirmButton = {
            TextButton(onClick = {
                vm.saveDietProfile(
                    DietProfile(
                        id = 1, dietPattern = pattern,
                        seafoodFreq = seafood.ifBlank { null },
                        dairyTolerant = dairy.ifBlank { null },
                        notes = notes.ifBlank { null },
                        updatedAt = nowIso(),
                    )
                )
                onDismiss()
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

// ===== 忌口清单管理弹窗 =====
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AvoidListDialog(vm: WellnessViewModel, onDismiss: () -> Unit) {
    val items by vm.foodAvoidItems.collectAsState()
    var showAdd by remember { mutableStateOf(false) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("忌口清单（${items.size} 项）") },
        text = {
            Column {
                if (items.isEmpty()) {
                    Text("暂无忌口项", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    items.forEach { item ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(item.name, style = MaterialTheme.typography.bodyMedium)
                                Text("${avoidCategoryLabel(item.category)} · ${severityLabel(item.severity)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TextButton(onClick = { vm.deleteFoodAvoid(item.id) }) {
                                Text("删除", color = MaterialTheme.colorScheme.error)
                            }
                        }
                        HorizontalDivider()
                    }
                }
                Spacer(Modifier.height(8.dp))
                Button(onClick = { showAdd = true }) { Text("添加忌口项") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } },
        dismissButton = {},
    )

    if (showAdd) {
        var name by remember { mutableStateOf("") }
        var category by remember { mutableStateOf("allergy") }
        var severity by remember { mutableStateOf("medium") }

        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("添加忌口项") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text("食物名称") }, singleLine = true)
                    Text("类别", style = MaterialTheme.typography.bodySmall)
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        listOf("allergy" to "过敏", "intolerance" to "不耐受",
                            "doctor_advice" to "医嘱", "personal_experience" to "个人体验",
                            "drug_interaction" to "药效冲突").forEach { (k, l) ->
                            androidx.compose.material3.FilterChip(
                                selected = category == k, onClick = { category = k },
                                label = { Text(l) },
                            )
                        }
                    }
                    Text("严重程度", style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("high" to "高", "medium" to "中", "low" to "低").forEach { (k, l) ->
                            androidx.compose.material3.FilterChip(
                                selected = severity == k, onClick = { severity = k },
                                label = { Text(l) },
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (name.isNotBlank()) {
                            vm.saveFoodAvoid(
                                FoodAvoidItem(
                                    id = "", name = name.trim(), category = category,
                                    severity = severity, notes = null,
                                    createdAt = nowIso(), updatedAt = nowIso(),
                                )
                            )
                            showAdd = false
                        }
                    },
                    enabled = name.isNotBlank(),
                ) { Text("添加") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("取消") } },
        )
    }
}

// ===== 标签工具 =====
private fun dietPatternLabel(k: String) = when (k) {
    "mediterranean" -> "地中海式"; "paleo" -> "旧石器式"
    "vegan" -> "纯素"; "vegetarian" -> "素食"
    "low_starch" -> "低淀粉"; else -> "杂食"
}

private fun freqLabel(k: String) = when (k) {
    "never" -> "不吃"; "rare" -> "偶尔"
    "weekly" -> "每周"; "frequent" -> "经常"; else -> k
}

private fun dairyLabel(k: String) = when (k) {
    "yes" -> "耐受"; "no" -> "不耐受"
    "lactose_free_only" -> "仅无乳糖"; else -> k
}

private fun avoidCategoryLabel(k: String) = when (k) {
    "allergy" -> "过敏"; "intolerance" -> "不耐受"
    "doctor_advice" -> "医嘱"; "personal_experience" -> "个人体验"
    "drug_interaction" -> "药效冲突"; else -> k
}

private fun severityLabel(k: String) = when (k) {
    "high" -> "高风险"; "medium" -> "中"; "low" -> "低"; else -> k
}
