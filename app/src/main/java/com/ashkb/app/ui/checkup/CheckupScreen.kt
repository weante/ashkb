package com.ashkb.app.ui.checkup

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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import com.ashkb.app.data.entity.CheckupItem
import com.ashkb.app.data.entity.CheckupRecord
import com.ashkb.app.data.entity.CheckupType
import com.ashkb.app.data.entity.DoctorConfirm
import com.ashkb.app.data.entity.LabResult
import com.ashkb.app.data.entity.VaccineRecord
import com.ashkb.app.data.entity.VaccineType
import com.ashkb.app.data.repo.nowIso
import java.time.LocalDate

enum class CheckupTab(val label: String) { ITEMS("项目"), RECORDS("记录"), VACCINES("疫苗") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckupScreen(vm: CheckupViewModel, onBack: () -> Unit) {
    var tab by remember { mutableStateOf(CheckupTab.ITEMS) }
    val items by vm.checkupItems.collectAsState()
    val records by vm.checkupRecords.collectAsState()
    val vaccines by vm.vaccineRecords.collectAsState()

    var showItemForm by remember { mutableStateOf(false) }
    var showRecordForm by remember { mutableStateOf(false) }
    var showVaccineForm by remember { mutableStateOf(false) }
    var showLabDetail by remember { mutableStateOf<CheckupRecord?>(null) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("复诊管理") })

        // Tab 切换
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CheckupTab.entries.forEach { t ->
                FilterChip(
                    selected = tab == t, onClick = { tab = t },
                    label = { Text(t.label) },
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        when (tab) {
            CheckupTab.ITEMS -> CheckupItemsList(
                items = items,
                onAdd = { showItemForm = true },
                onDeactivate = { vm.deactivateCheckupItem(it) },
            )
            CheckupTab.RECORDS -> CheckupRecordsList(
                records = records,
                onAdd = { showRecordForm = true },
                onViewLab = { showLabDetail = it },
            )
            CheckupTab.VACCINES -> VaccineList(
                vaccines = vaccines,
                onAdd = { showVaccineForm = true },
            )
        }
    }

    if (showItemForm) CheckupItemFormDialog(
        onSave = { vm.saveCheckupItem(it); showItemForm = false },
        onDismiss = { showItemForm = false },
    )
    if (showRecordForm) CheckupRecordFormDialog(
        items = items,
        onSave = { vm.saveCheckupRecord(it); showRecordForm = false },
        onDismiss = { showRecordForm = false },
    )
    if (showVaccineForm) VaccineFormDialog(
        onSave = { vm.saveVaccineRecord(it); showVaccineForm = false },
        onDismiss = { showVaccineForm = false },
    )
    showLabDetail?.let { rec ->
        LabDetailDialog(
            record = rec,
            vm = vm,
            onDismiss = { showLabDetail = null },
        )
    }
}

// ===== 复诊项目列表 =====
@Composable
private fun CheckupItemsList(items: List<CheckupItem>, onAdd: () -> Unit, onDeactivate: (String) -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (items.isEmpty()) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("暂无复诊项目", style = MaterialTheme.typography.bodyMedium)
                        Text("添加周期性检查项目（如血常规、眼科年检），系统将自动追踪",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = onAdd) { Text("添加项目") }
                    }
                }
            }
        } else {
            items(items) { item ->
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(item.name, style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium)
                            Text(
                                buildString {
                                    append(CheckupType.fromKey(item.checkType).label)
                                    if (item.cycleDays != null) append(" · 每 ${item.cycleDays} 天")
                                    else append(" · 按需")
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = { onDeactivate(item.id) }) {
                            Text("停用", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
            item {
                OutlinedButton(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
                    Text("添加项目")
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

// ===== 复诊记录列表 =====
@Composable
private fun CheckupRecordsList(
    records: List<CheckupRecord>,
    onAdd: () -> Unit,
    onViewLab: (CheckupRecord) -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (records.isEmpty()) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("暂无复诊记录", style = MaterialTheme.typography.bodyMedium)
                        Text("记录复诊结果、化验指标，便于趋势追踪与就医回顾",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = onAdd) { Text("记录复诊") }
                    }
                }
            }
        } else {
            items(records) { rec ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(rec.date, fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(4.dp))
                            Text(" · ${CheckupType.fromKey(rec.checkType).label}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.weight(1f))
                            if (rec.checkType == "LAB") {
                                TextButton(onClick = { onViewLab(rec) }) { Text("化验详情") }
                            }
                        }
                        Text(rec.itemName, style = MaterialTheme.typography.bodyMedium)
                        rec.hospital?.let { Text("医院：$it",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        rec.conclusion?.let {
                            Spacer(Modifier.height(4.dp))
                            Text("结论：$it", style = MaterialTheme.typography.bodySmall)
                        }
                        rec.nextDate?.let {
                            Spacer(Modifier.height(4.dp))
                            Text("下次复诊：$it",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
            item {
                OutlinedButton(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
                    Text("添加记录")
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

// ===== 疫苗列表 =====
@Composable
private fun VaccineList(vaccines: List<VaccineRecord>, onAdd: () -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (vaccines.isEmpty()) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("暂无疫苗记录", style = MaterialTheme.typography.bodyMedium)
                        Text("AS 患者使用生物制剂期间接种疫苗需谨慎——活疫苗务必先与医生确认",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = onAdd) { Text("记录疫苗") }
                    }
                }
            }
        } else {
            items(vaccines) { vac ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(vac.date, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.weight(1f))
                            Text(
                                VaccineType.fromKey(vac.vaccineType).label,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (vac.vaccineType == "LIVE") MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(vac.vaccineName, style = MaterialTheme.typography.bodyMedium)
                        Row {
                            Text("医生确认：", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                DoctorConfirm.fromKey(vac.doctorConfirm).label,
                                style = MaterialTheme.typography.bodySmall,
                                color = when (vac.doctorConfirm) {
                                    "CONFIRMED" -> MaterialTheme.colorScheme.primary
                                    "DECLINED" -> MaterialTheme.colorScheme.error
                                    else -> MaterialTheme.colorScheme.tertiary
                                },
                            )
                        }
                        vac.nextDueDate?.let {
                            Text("下次：$it", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
            item {
                OutlinedButton(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
                    Text("添加记录")
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

// ===== 复诊项目表单 =====
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CheckupItemFormDialog(onSave: (CheckupItem) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(CheckupType.LAB) }
    var cycleDays by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加复诊项目") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("项目名称") }, singleLine = true)
                Text("类型", style = MaterialTheme.typography.bodySmall)
                Row(
                    Modifier.fillMaxWidth()
                        .horizontalScroll(androidx.compose.foundation.rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    CheckupType.entries.forEach { t ->
                        FilterChip(selected = type == t, onClick = { type = t },
                            label = { Text(t.label) })
                    }
                }
                OutlinedTextField(cycleDays, { cycleDays = it },
                    label = { Text("周期（天，留空=按需）") }, singleLine = true)
                OutlinedTextField(notes, { notes = it }, label = { Text("备注") })
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank()) {
                        onSave(
                            CheckupItem(
                                id = "", name = name.trim(), checkType = type.name,
                                cycleDays = cycleDays.toIntOrNull(),
                                notes = notes.ifBlank { null },
                                createdAt = nowIso(), updatedAt = nowIso(),
                            )
                        )
                    }
                },
                enabled = name.isNotBlank(),
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

// ===== 复诊记录表单 =====
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CheckupRecordFormDialog(
    items: List<CheckupItem>,
    onSave: (CheckupRecord) -> Unit,
    onDismiss: () -> Unit,
) {
    var itemName by remember { mutableStateOf("") }
    var checkType by remember { mutableStateOf(CheckupType.CONSULT) }
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var hospital by remember { mutableStateOf("") }
    var doctor by remember { mutableStateOf("") }
    var nextDate by remember { mutableStateOf("") }
    var conclusion by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("记录复诊") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
                    .verticalScroll(androidx.compose.foundation.rememberScrollState()),
            ) {
                OutlinedTextField(itemName, { itemName = it },
                    label = { Text("项目名称") }, singleLine = true)
                Text("类型", style = MaterialTheme.typography.bodySmall)
                Row(
                    Modifier.fillMaxWidth()
                        .horizontalScroll(androidx.compose.foundation.rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    CheckupType.entries.forEach { t ->
                        FilterChip(selected = checkType == t, onClick = { checkType = t },
                            label = { Text(t.label) })
                    }
                }
                OutlinedTextField(date, { date = it }, label = { Text("日期") }, singleLine = true)
                OutlinedTextField(hospital, { hospital = it },
                    label = { Text("医院") }, singleLine = true)
                OutlinedTextField(doctor, { doctor = it },
                    label = { Text("医生") }, singleLine = true)
                OutlinedTextField(nextDate, { nextDate = it },
                    label = { Text("下次复诊日期（可选）") }, singleLine = true)
                OutlinedTextField(conclusion, { conclusion = it },
                    label = { Text("诊断结论 / 医生意见") })
                OutlinedTextField(notes, { notes = it }, label = { Text("备注") })
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (itemName.isNotBlank()) {
                        onSave(
                            CheckupRecord(
                                id = "", date = date, recordedAt = nowIso(),
                                itemName = itemName.trim(), checkType = checkType.name,
                                hospital = hospital.ifBlank { null },
                                doctor = doctor.ifBlank { null },
                                nextDate = nextDate.ifBlank { null },
                                conclusion = conclusion.ifBlank { null },
                                notes = notes.ifBlank { null },
                            )
                        )
                    }
                },
                enabled = itemName.isNotBlank(),
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

// ===== 疫苗表单 =====
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VaccineFormDialog(onSave: (VaccineRecord) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(VaccineType.INACTIVATED) }
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var dose by remember { mutableStateOf("") }
    var hospital by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf(DoctorConfirm.CONFIRMED) }
    var nextDue by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("记录疫苗接种") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
                    .verticalScroll(androidx.compose.foundation.rememberScrollState()),
            ) {
                OutlinedTextField(name, { name = it },
                    label = { Text("疫苗名称") }, singleLine = true)
                Text("类型", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    VaccineType.entries.forEach { t ->
                        FilterChip(selected = type == t, onClick = { type = t },
                            label = { Text(t.label) })
                    }
                }
                OutlinedTextField(date, { date = it }, label = { Text("接种日期") }, singleLine = true)
                OutlinedTextField(dose, { dose = it },
                    label = { Text("剂次（可选）") }, singleLine = true)
                OutlinedTextField(hospital, { hospital = it },
                    label = { Text("接种医院") }, singleLine = true)
                Text("医生确认", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DoctorConfirm.entries.forEach { c ->
                        FilterChip(selected = confirm == c, onClick = { confirm = c },
                            label = { Text(c.label) })
                    }
                }
                OutlinedTextField(nextDue, { nextDue = it },
                    label = { Text("下次加强日期（可选）") }, singleLine = true)
                OutlinedTextField(notes, { notes = it }, label = { Text("备注 / 反应") })
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank()) {
                        onSave(
                            VaccineRecord(
                                id = "", date = date, recordedAt = nowIso(),
                                vaccineName = name.trim(), vaccineType = type.name,
                                dose = dose.ifBlank { null },
                                hospital = hospital.ifBlank { null },
                                doctorConfirm = confirm.name,
                                nextDueDate = nextDue.ifBlank { null },
                                notes = notes.ifBlank { null },
                            )
                        )
                    }
                },
                enabled = name.isNotBlank(),
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

// ===== 化验详情弹窗 =====
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LabDetailDialog(record: CheckupRecord, vm: CheckupViewModel, onDismiss: () -> Unit) {
    val labs by vm.labResultsFor(record.id).collectAsState(initial = emptyList())
    var showAdd by remember { mutableStateOf(false) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${record.itemName} 化验结果") },
        text = {
            Column {
                if (labs.isEmpty()) {
                    Text("暂无化验指标", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    labs.forEach { lab ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(lab.testName, Modifier.weight(1f))
                            Text(
                                "${lab.value ?: lab.valueText ?: "-"}${lab.unit?.let { " $it" } ?: ""}",
                                color = when (lab.abnormal) {
                                    "high", "low" -> MaterialTheme.colorScheme.error
                                    else -> MaterialTheme.colorScheme.onSurface
                                },
                            )
                        }
                        HorizontalDivider()
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { showAdd = true }) { Text("添加指标") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
        dismissButton = {},
    )

    if (showAdd) {
        var testName by remember { mutableStateOf("") }
        var value by remember { mutableStateOf("") }
        var unit by remember { mutableStateOf("") }
        var refLow by remember { mutableStateOf("") }
        var refHigh by remember { mutableStateOf("") }

        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("添加化验指标") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(testName, { testName = it },
                        label = { Text("指标名称") }, singleLine = true)
                    OutlinedTextField(value, { value = it },
                        label = { Text("数值") }, singleLine = true)
                    OutlinedTextField(unit, { unit = it },
                        label = { Text("单位") }, singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(refLow, { refLow = it },
                            label = { Text("参考下限") }, singleLine = true,
                            modifier = Modifier.weight(1f))
                        OutlinedTextField(refHigh, { refHigh = it },
                            label = { Text("参考上限") }, singleLine = true,
                            modifier = Modifier.weight(1f))
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (testName.isNotBlank()) {
                            vm.saveLabResult(
                                LabResult(
                                    id = "", date = record.date, recordedAt = nowIso(),
                                    checkupId = record.id, testName = testName.trim(),
                                    value = value.toDoubleOrNull(),
                                    unit = unit.ifBlank { null },
                                    refLow = refLow.toDoubleOrNull(),
                                    refHigh = refHigh.toDoubleOrNull(),
                                )
                            )
                            showAdd = false
                        }
                    },
                    enabled = testName.isNotBlank(),
                ) { Text("添加") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("取消") } },
        )
    }
}
