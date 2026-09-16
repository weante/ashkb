package com.ashkb.app.ui.checkup

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
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
import com.ashkb.app.data.entity.CheckupItem
import com.ashkb.app.data.entity.CheckupRecord
import com.ashkb.app.data.entity.CheckupType
import com.ashkb.app.data.entity.DoctorConfirm
import com.ashkb.app.data.entity.VaccineRecord
import com.ashkb.app.data.entity.VaccineType
import com.ashkb.app.data.repo.nowIso
import com.ashkb.app.ui.theme.Size
import com.ashkb.app.ui.theme.Spacing
import java.time.LocalDate

// ===== 复诊项目表单（多字段，ModalBottomSheet） =====
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun CheckupItemFormSheet(onSave: (CheckupItem) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(CheckupType.LAB) }
    var cycleDays by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        SheetColumn {
            Text("添加复诊项目", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(name, { name = it }, label = { Text("项目名称") }, singleLine = true)
            ChipGroupLabel("类型")
            ChipGroup(
                options = CheckupType.entries.map { it.name to it.label },
                selected = type.name,
                onSelect = { type = CheckupType.valueOf(it) },
            )
            OutlinedTextField(cycleDays, { cycleDays = it },
                label = { Text("周期（天，留空=按需）") }, singleLine = true)
            OutlinedTextField(notes, { notes = it }, label = { Text("备注") })
            SheetSaveButton(
                text = "保存",
                enabled = name.isNotBlank(),
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
            )
        }
    }
}

// ===== 复诊记录表单（多字段，ModalBottomSheet） =====
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun CheckupRecordFormSheet(
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

    ModalBottomSheet(onDismissRequest = onDismiss) {
        SheetColumn {
            Text("记录复诊", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(itemName, { itemName = it },
                label = { Text("项目名称") }, singleLine = true)
            ChipGroupLabel("类型")
            ChipGroup(
                options = CheckupType.entries.map { it.name to it.label },
                selected = checkType.name,
                onSelect = { checkType = CheckupType.valueOf(it) },
            )
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
            SheetSaveButton(
                text = "保存",
                enabled = itemName.isNotBlank(),
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
            )
        }
    }
}

// ===== 疫苗表单（多字段，ModalBottomSheet） =====
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun VaccineFormSheet(onSave: (VaccineRecord) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(VaccineType.INACTIVATED) }
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var dose by remember { mutableStateOf("") }
    var hospital by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf(DoctorConfirm.CONFIRMED) }
    var nextDue by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        SheetColumn {
            Text("记录疫苗接种", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(name, { name = it },
                label = { Text("疫苗名称") }, singleLine = true)
            ChipGroupLabel("类型")
            ChipGroup(
                options = VaccineType.entries.map { it.name to it.label },
                selected = type.name,
                onSelect = { type = VaccineType.valueOf(it) },
            )
            OutlinedTextField(date, { date = it }, label = { Text("接种日期") }, singleLine = true)
            OutlinedTextField(dose, { dose = it },
                label = { Text("剂次（可选）") }, singleLine = true)
            OutlinedTextField(hospital, { hospital = it },
                label = { Text("接种医院") }, singleLine = true)
            ChipGroupLabel("医生确认")
            ChipGroup(
                options = DoctorConfirm.entries.map { it.name to it.label },
                selected = confirm.name,
                onSelect = { confirm = DoctorConfirm.valueOf(it) },
            )
            OutlinedTextField(nextDue, { nextDue = it },
                label = { Text("下次加强日期（可选）") }, singleLine = true)
            OutlinedTextField(notes, { notes = it }, label = { Text("备注 / 反应") })
            SheetSaveButton(
                text = "保存",
                enabled = name.isNotBlank(),
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
            )
        }
    }
}

@Composable
private fun ChipGroupLabel(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall)
}

/** 单选 chip 组：FlowRow 自动换行防截断，触摸目标 ≥48dp。 */
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
