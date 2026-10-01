package com.ashkb.app.ui.checkup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Alignment
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
/**
 * @param seedResult C10 一键种入的结果：null = 尚未操作、0 = 节点已齐全、>0 = 本次新增条数。
 *                   由调用方从 VM 采集后传入，文案在本列表内用 stringResource 组装
 *                   （VM 无 Context，不负责取词）。
 * @param onSeed     触发种入结核 / 乙肝 / 丙肝筛查 + 生物制剂续方节点。
 * @param onEdit     v1.0.80（批次 6）修改复诊项目（周期 / 类型 / 备注）——此前只能新建，写错了只能停用重建。
 */
@Composable
internal fun CheckupItemsList(
    items: List<CheckupItem>,
    onAdd: () -> Unit,
    onDeactivate: (String) -> Unit,
    seedResult: Int?,
    onSeed: () -> Unit,
    onEdit: (CheckupItem) -> Unit,
) {
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
            items(items, key = { it.id }) { item ->
                SectionCard(
                    title = item.name,
                    subtitle = buildString {
                        append(CheckupType.fromKey(item.checkType).label)
                        if (item.cycleDays != null) append(stringResource(R.string.checkup_cycle_days_suffix, item.cycleDays))
                        else append(stringResource(R.string.med_prn_suffix))
                    },
                    action = {
                        // 改（编辑）与删（停用）并排：复诊项目没有物理删除入口，
                        // 「停用」即它的生命周期终点（历史记录靠 item_name 快照自持，见 README 口径）
                        TextButton(
                            onClick = { onEdit(item) },
                            modifier = Modifier.heightIn(min = Size.touchMin),
                        ) { Text(stringResource(R.string.common_edit)) }
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
            }
        }
        // C10 生物制剂筛查 / 续方节点：属"补全项目"的批量入口，排在列表末尾（空态时同样出现），
        // 不抢「添加复诊项目」主按钮的位置
        item {
            SectionCard(title = stringResource(R.string.checkup_seed_title)) {
                Text(
                    stringResource(R.string.checkup_seed_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Spacing.sm))
                Button(
                    onClick = onSeed,
                    modifier = Modifier.fillMaxWidth().heightIn(min = Size.touchMin),
                ) { Text(stringResource(R.string.checkup_seed_button)) }
                // 结果只在点过之后出现：0 条与新增若干条是两种不同反馈，不能都沉默
                seedResult?.let { added ->
                    Spacer(Modifier.height(Spacing.sm))
                    Text(
                        if (added > 0) stringResource(R.string.checkup_seed_done, added)
                        else stringResource(R.string.checkup_seed_all),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(Modifier.height(Spacing.xxl))
        }
    }
}

// ===== 复诊记录列表 =====
/**
 * @param onAttach   打开某条记录的附件归档 sheet（附件入口在卡片正文里，与右上角动作槽区分开）
 * @param onOpenAllAttachments 打开全部附件总览：未归属复诊的附件（从化验/影像侧导入）只在这里可见
 * @param onEdit     v1.0.80（批次 6）修改该条复诊记录（回填原值、沿用主键）
 * @param onDelete   v1.0.80（批次 6）删除该条复诊记录——走**级联确认框**（列明将一并删除的化验 / 影像 / 附件条数）
 * @param prepHeader 列表顶部插槽（复诊准备清单）。做成插槽而非固定内容：
 *                   卡片需要 items/records/today 三路数据，由调用方组装，本列表不必知道 C4。
 */
@Composable
internal fun CheckupRecordsList(
    records: List<CheckupRecord>,
    onAdd: () -> Unit,
    onViewLab: (CheckupRecord) -> Unit,
    onAttach: (CheckupRecord) -> Unit,
    onOpenAllAttachments: () -> Unit,
    onEdit: (CheckupRecord) -> Unit,
    onDelete: (CheckupRecord) -> Unit,
    prepHeader: (@Composable () -> Unit)? = null,
) {
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        // 准备清单必须排在空态/列表之前：它是"下次复诊该做什么"的唯一答案，不能滚出首屏
        prepHeader?.let { header ->
            item { header() }
        }
        // 总览入口紧跟在准备清单之后、记录列表之前：未归属的附件不属于任何一条记录，
        // 只能挂在这一层，否则用户在记录页永远找不到它们
        item {
            TextButton(
                onClick = onOpenAllAttachments,
                modifier = Modifier.heightIn(min = Size.touchMin),
            ) { Text(stringResource(R.string.attach_title)) }
        }
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
            items(records, key = { it.id }) { rec ->
                CheckupRecordCard(rec = rec, onViewLab = onViewLab, onAttach = onAttach, onEdit = onEdit, onDelete = onDelete)
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

/**
 * 单条复诊记录卡片（从 [CheckupRecordsList] 抽出来：列表函数要控制长度，
 * 而这张卡片承载了三件事——右上角的「化验详情」动作槽、正文里的编辑 / 附件 / 删除）。
 *
 * 删除刻意放在最右且用 error 色：与「编辑 / 附件」拉开距离，避免并排时误触
 * （这是不可逆的级联删除）。
 */
@Composable
private fun CheckupRecordCard(
    rec: CheckupRecord,
    onViewLab: (CheckupRecord) -> Unit,
    onAttach: (CheckupRecord) -> Unit,
    onEdit: (CheckupRecord) -> Unit,
    onDelete: (CheckupRecord) -> Unit,
) {
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
        // 附件入口放正文末尾：化验记录右上角已被"化验详情"占用，
        // 多条操作挤在动作槽里在窄屏会互相截断
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            TextButton(
                onClick = { onEdit(rec) },
                modifier = Modifier.heightIn(min = Size.touchMin),
            ) { Text(stringResource(R.string.common_edit)) }
            TextButton(
                onClick = { onAttach(rec) },
                modifier = Modifier.heightIn(min = Size.touchMin),
            ) { Text(stringResource(R.string.attach_title)) }
            TextButton(
                onClick = { onDelete(rec) },
                modifier = Modifier.heightIn(min = Size.touchMin),
            ) { Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error) }
        }
    }
}

// ===== 疫苗列表 =====
/**
 * @param onEdit v1.0.80（批次 6）修改该条疫苗记录——表单内可删除（含派生安全警报的重算）
 */
@Composable
internal fun VaccineList(vaccines: List<VaccineRecord>, onAdd: () -> Unit, onEdit: (VaccineRecord) -> Unit) {
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
            items(vaccines, key = { it.id }) { vac ->
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
                    TextButton(
                        onClick = { onEdit(vac) },
                        modifier = Modifier.heightIn(min = Size.touchMin),
                    ) { Text(stringResource(R.string.common_edit)) }
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
