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
import androidx.compose.ui.res.stringResource

import com.ashkb.app.R
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
                SectionCard(title = stringResource(R.string.checkup_items_empty)) {
                    Text(
                        stringResource(R.string.checkup_add_item_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(Spacing.sm))
                    Button(
                        onClick = onAdd,
                        modifier = Modifier.fillMaxWidth().heightIn(min = Size.touchMin),
                    ) { Text(stringResource(R.string.checkup_add_item)) }
                }
            }
        } else {
            items(items) { item ->
                SectionCard(
                    title = item.name,
                    subtitle = buildString {
                        append(CheckupType.fromKey(item.checkType).label)
                        if (item.cycleDays != null) append(stringResource(R.string.checkup_cycle_days_suffix, item.cycleDays))
                        else append(stringResource(R.string.med_prn_suffix))
                    },
                    action = {
                        DestructiveAction(
                            label = stringResource(R.string.med_deactivate),
                            confirmTitle = stringResource(R.string.checkup_deactivate_title),
                            confirmBody = stringResource(R.string.checkup_disable_confirm, item.name),
                            onConfirm = { onDeactivate(item.id) },
                        )
                    },
                ) {}
            }
            item {
                OutlinedButton(
                    onClick = onAdd,
                    modifier = Modifier.fillMaxWidth().heightIn(min = Size.touchMin),
                ) { Text(stringResource(R.string.checkup_add_item)) }
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
                SectionCard(title = stringResource(R.string.checkup_records_empty)) {
                    Text(
                        stringResource(R.string.checkup_section_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(Spacing.sm))
                    Button(
                        onClick = onAdd,
                        modifier = Modifier.fillMaxWidth().heightIn(min = Size.touchMin),
                    ) { Text(stringResource(R.string.checkup_record_visit)) }
                }
            }
        } else {
            items(records) { rec ->
                SectionCard(
                    title = rec.date,
                    subtitle = CheckupType.fromKey(rec.checkType).label,
                    action = if (rec.checkType == "LAB") {
                        {
                            TextButton(onClick = { onViewLab(rec) }) { Text(stringResource(R.string.lab_detail_title)) }
                        }
                    } else {
                        null
                    },
                ) {
                    Text(rec.itemName, style = MaterialTheme.typography.bodyMedium)
                    rec.hospital?.let {
                        Text(
                            stringResource(R.string.checkup_hospital_line, it),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    rec.conclusion?.let {
                        Text(stringResource(R.string.checkup_conclusion_line, it), style = MaterialTheme.typography.bodySmall)
                    }
                    rec.nextDate?.let {
                        Text(
                            stringResource(R.string.checkup_next_visit_line, it),
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
                ) { Text(stringResource(R.string.common_add_record)) }
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
                SectionCard(title = stringResource(R.string.vaccine_empty)) {
                    Text(
                        stringResource(R.string.vaccine_bio_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(Spacing.sm))
                    Button(
                        onClick = onAdd,
                        modifier = Modifier.fillMaxWidth().heightIn(min = Size.touchMin),
                    ) { Text(stringResource(R.string.vaccine_record_short)) }
                }
            }
        } else {
            items(vaccines) { vac ->
                SectionCard(
                    title = vac.date,
                    subtitle = vac.vaccineName,
                    action = if (vac.vaccineType == "LIVE") {
                        { StatusChip(text = stringResource(R.string.vaccine_live), tone = StatusTone.Danger, icon = Icons.Rounded.WarningAmber) }
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
                            stringResource(R.string.checkup_next_short, it),
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
                ) { Text(stringResource(R.string.common_add_record)) }
                Spacer(Modifier.height(Spacing.xxl))
            }
        }
    }
}
