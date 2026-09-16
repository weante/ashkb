package com.ashkb.app.ui.checkup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ashkb.app.data.entity.CheckupItem
import com.ashkb.app.data.entity.CheckupRecord
import com.ashkb.app.data.entity.CheckupType
import com.ashkb.app.data.entity.DoctorConfirm
import com.ashkb.app.data.entity.VaccineRecord
import com.ashkb.app.data.entity.VaccineType
import com.ashkb.app.ui.components.DestructiveAction
import com.ashkb.app.ui.components.SectionCard
import com.ashkb.app.ui.components.StatusChip
import com.ashkb.app.ui.theme.Size
import com.ashkb.app.ui.theme.Spacing
import com.ashkb.app.ui.theme.StatusTone

// ===== 复诊项目列表 =====
@Composable
internal fun CheckupItemsList(items: List<CheckupItem>, onAdd: () -> Unit, onDeactivate: (String) -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        if (items.isEmpty()) {
            item {
                SectionCard(title = "暂无复诊项目") {
                    Text(
                        "添加周期性检查项目（如血常规、眼科年检），系统将自动追踪",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(Spacing.sm))
                    Button(
                        onClick = onAdd,
                        modifier = Modifier.fillMaxWidth().heightIn(min = Size.touchMin),
                    ) { Text("添加项目") }
                }
            }
        } else {
            items(items) { item ->
                SectionCard(
                    title = item.name,
                    subtitle = buildString {
                        append(CheckupType.fromKey(item.checkType).label)
                        if (item.cycleDays != null) append(" · 每 ${item.cycleDays} 天")
                        else append(" · 按需")
                    },
                    action = {
                        DestructiveAction(
                            label = "停用",
                            confirmTitle = "停用该项目？",
                            confirmBody = "停用后不再追踪「${item.name}」，历史记录保留。",
                            onConfirm = { onDeactivate(item.id) },
                        )
                    },
                ) {}
            }
            item {
                OutlinedButton(
                    onClick = onAdd,
                    modifier = Modifier.fillMaxWidth().heightIn(min = Size.touchMin),
                ) { Text("添加项目") }
                Spacer(Modifier.height(Spacing.xxl))
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
        Modifier.fillMaxSize().padding(horizontal = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        if (records.isEmpty()) {
            item {
                SectionCard(title = "暂无复诊记录") {
                    Text(
                        "记录复诊结果、化验指标，便于趋势追踪与就医回顾",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(Spacing.sm))
                    Button(
                        onClick = onAdd,
                        modifier = Modifier.fillMaxWidth().heightIn(min = Size.touchMin),
                    ) { Text("记录复诊") }
                }
            }
        } else {
            items(records) { rec ->
                SectionCard(
                    title = rec.date,
                    subtitle = CheckupType.fromKey(rec.checkType).label,
                    action = if (rec.checkType == "LAB") {
                        {
                            TextButton(onClick = { onViewLab(rec) }) { Text("化验详情") }
                        }
                    } else {
                        null
                    },
                ) {
                    Text(rec.itemName, style = MaterialTheme.typography.bodyMedium)
                    rec.hospital?.let {
                        Text(
                            "医院：$it",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    rec.conclusion?.let {
                        Text("结论：$it", style = MaterialTheme.typography.bodySmall)
                    }
                    rec.nextDate?.let {
                        Text(
                            "下次复诊：$it",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            item {
                OutlinedButton(
                    onClick = onAdd,
                    modifier = Modifier.fillMaxWidth().heightIn(min = Size.touchMin),
                ) { Text("添加记录") }
                Spacer(Modifier.height(Spacing.xxl))
            }
        }
    }
}

// ===== 疫苗列表 =====
@Composable
internal fun VaccineList(vaccines: List<VaccineRecord>, onAdd: () -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        if (vaccines.isEmpty()) {
            item {
                SectionCard(title = "暂无疫苗记录") {
                    Text(
                        "AS 患者使用生物制剂期间接种疫苗需谨慎——活疫苗务必先与医生确认",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(Spacing.sm))
                    Button(
                        onClick = onAdd,
                        modifier = Modifier.fillMaxWidth().heightIn(min = Size.touchMin),
                    ) { Text("记录疫苗") }
                }
            }
        } else {
            items(vaccines) { vac ->
                SectionCard(
                    title = vac.date,
                    subtitle = vac.vaccineName,
                    action = if (vac.vaccineType == "LIVE") {
                        { StatusChip(text = "活疫苗", tone = StatusTone.Danger, icon = Icons.Rounded.WarningAmber) }
                    } else {
                        null
                    },
                ) {
                    Text(
                        "医生确认：${DoctorConfirm.fromKey(vac.doctorConfirm).label}",
                        style = MaterialTheme.typography.bodySmall,
                        color = when (vac.doctorConfirm) {
                            "CONFIRMED" -> MaterialTheme.colorScheme.primary
                            "DECLINED" -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    vac.nextDueDate?.let {
                        Text(
                            "下次：$it",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            item {
                OutlinedButton(
                    onClick = onAdd,
                    modifier = Modifier.fillMaxWidth().heightIn(min = Size.touchMin),
                ) { Text("添加记录") }
                Spacer(Modifier.height(Spacing.xxl))
            }
        }
    }
}
