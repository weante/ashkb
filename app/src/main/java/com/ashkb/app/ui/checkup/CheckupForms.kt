package com.ashkb.app.ui.checkup

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ashkb.app.data.entity.CheckupItem
import com.ashkb.app.data.entity.CheckupRecord
import com.ashkb.app.data.entity.CheckupType
import com.ashkb.app.data.entity.DoctorConfirm
import com.ashkb.app.data.entity.VaccineRecord
import com.ashkb.app.data.entity.VaccineType
import com.ashkb.app.data.repo.nowIso
import java.time.LocalDate

// ===== 复诊项目表单 =====
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CheckupItemFormDialog(onSave: (CheckupItem) -> Unit, onDismiss: () -> Unit) {
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
internal fun CheckupRecordFormDialog(
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
internal fun VaccineFormDialog(onSave: (VaccineRecord) -> Unit, onDismiss: () -> Unit) {
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
