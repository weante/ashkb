package com.ashkb.app.ui.checkup

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ashkb.app.data.entity.CheckupItem
import com.ashkb.app.data.entity.CheckupRecord
import com.ashkb.app.data.entity.CheckupType
import com.ashkb.app.data.entity.DoctorConfirm
import com.ashkb.app.data.entity.VaccineRecord
import com.ashkb.app.data.entity.VaccineType

// ===== 复诊项目列表 =====
@Composable
internal fun CheckupItemsList(items: List<CheckupItem>, onAdd: () -> Unit, onDeactivate: (String) -> Unit) {
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
                Column {
                    OutlinedButton(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
                        Text("添加项目")
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

// ===== 复诊记录列表 =====
@Composable
internal fun CheckupRecordsList(
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
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(rec.date, fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium)
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
                            Text("结论：$it", style = MaterialTheme.typography.bodySmall)
                        }
                        rec.nextDate?.let {
                            Text("下次复诊：$it",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
            item {
                Column {
                    OutlinedButton(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
                        Text("添加记录")
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

// ===== 疫苗列表 =====
@Composable
internal fun VaccineList(vaccines: List<VaccineRecord>, onAdd: () -> Unit) {
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
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                Column {
                    OutlinedButton(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
                        Text("添加记录")
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}
