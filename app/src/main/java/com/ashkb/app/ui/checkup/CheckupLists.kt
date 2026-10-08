package com.ashkb.app.ui.checkup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
 * @param onOpenRecords v1.0.87（批次 13）整行可点 → 跳到「记录」tab 并**按该项目筛选**。
 *                   此前项目卡片只有「编辑 / 停用」两个按钮、整行不可点，维护者在真机上点「MRI」
 *                   以为会进该项目的记录，结果毫无反应。
 */
@Composable
internal fun CheckupItemsList(
    items: List<CheckupItem>,
    onAdd: () -> Unit,
    onDeactivate: (String) -> Unit,
    seedResult: Int?,
    onSeed: () -> Unit,
    onEdit: (CheckupItem) -> Unit,
    onOpenRecords: (CheckupItem) -> Unit,
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
                CheckupItemCard(
                    item = item,
                    onEdit = onEdit,
                    onDeactivate = onDeactivate,
                    onOpenRecords = onOpenRecords,
                )
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

/**
 * 单个复诊项目卡片（从 [CheckupItemsList] 抽出来，与 [CheckupRecordCard] 同一理由：
 * 列表函数还要管空态、种入入口与底部按钮，而这张卡自己承载三件事——
 * 整行可点进该项目的记录、编辑、停用）。
 *
 * v1.0.87（批次 13）：整行可点。此前卡片只有「编辑 / 停用」两个按钮、整行不可点，
 * 维护者在真机上点「MRI」以为会进该项目的记录，结果毫无反应。
 */
@Composable
private fun CheckupItemCard(
    item: CheckupItem,
    onEdit: (CheckupItem) -> Unit,
    onDeactivate: (String) -> Unit,
    onOpenRecords: (CheckupItem) -> Unit,
) {
    SectionCard(
        title = item.name,
        subtitle = buildString {
            append(stringResource(CheckupType.fromKey(item.checkType).labelRes))
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
                // 该动作只是「停用」（历史记录保留），故确认按钮也必须说「停用」而不是默认的「删除」
                confirmLabel = stringResource(R.string.med_deactivate),
                onConfirm = { onDeactivate(item.id) },
            )
        },
        onClick = { onOpenRecords(item) },
        onClickLabel = stringResource(R.string.checkup_item_open_records),
    ) {
        // 「整行可点」在界面里本身看不见：给一行明示入口 + 雪佛龙（沿用 NavRow 的可点惯例，
        // 图标装饰性、语义由这行字承载）。维护者上次点「MRI」毫无反应，正是缺这个入口
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(
                stringResource(R.string.checkup_item_open_records_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(Size.iconSm),
                tint = MaterialTheme.colorScheme.primary,
            )
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
 * @param filterItemName v1.0.87（批次 13）：非空 = 当前**只显示该项目的记录**（从「项目」tab 点进来的）。
 *                   过滤本身由调用方完成（本列表拿到的就是筛过的数据），这里只负责两件事：
 *                   顶部那个可清除的筛选标识、以及点名到项目的空态。
 * @param onClearFilter 清除筛选（null = 不渲染清除按钮）
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
    filterItemName: String? = null,
    onClearFilter: (() -> Unit)? = null,
) {
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        // v1.0.87（批次 13）：筛选标识排在**最前**——从「项目」点进来的人第一眼就要看到
        // 「现在只有这一个项目的记录」；否则一段短列表会被当成「记录丢了」
        if (filterItemName != null) {
            item(key = "records-filter") { RecordsFilterRow(filterItemName, onClearFilter) }
        }
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
                // 筛选后的空态必须点名到项目：通用的「暂无复诊记录」会让用户以为整份记录丢了
                if (filterItemName != null) {
                    RecordsFilteredEmptyCard(name = filterItemName, onAdd = onAdd)
                } else {
                    RecordsEmptyCard(onAdd = onAdd)
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
 * v1.0.87（批次 13）：筛选标识（「仅显示：MRI」+ ✕）。
 * 整行只有「清除」这一件事，故 ✕ 做成独立按钮（48dp 触达区）而不是把胶囊本身变成按钮——
 * 避免两个可点目标重叠，读屏也能只报一个「清除筛选」。
 */
@Composable
private fun RecordsFilterRow(name: String, onClearFilter: (() -> Unit)?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        StatusChip(text = stringResource(R.string.checkup_records_filter_chip, name), tone = StatusTone.Info)
        if (onClearFilter != null) {
            IconButton(onClick = onClearFilter, modifier = Modifier.size(Size.touchMin)) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.checkup_records_filter_clear),
                    modifier = Modifier.size(Size.iconSm),
                )
            }
        }
    }
}

/** 一条记录都没有时的通用空态（未筛选时的展示与 v1.0.80 之前一致，未被本次改动触碰）。 */
@Composable
private fun RecordsEmptyCard(onAdd: () -> Unit) {
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

/**
 * v1.0.87（批次 13）：「该项目名下没有任何记录」的专属空态。
 *
 * 与通用空态的差别不只是措辞：从这里点「记录复诊」进表单时，项目名会被**预填**成该项目
 * （见 `CheckupScreen` 的 `recordFormPresetItem`）——记录表单的项目名是自由文本、与项目的关联
 * 靠名字快照，不预填就成了「补录完仍然不在该项目下」的空转。
 */
@Composable
private fun RecordsFilteredEmptyCard(name: String, onAdd: () -> Unit) {
    SectionCard(title = stringResource(R.string.checkup_records_filtered_empty, name)) {
        Text(
            stringResource(R.string.checkup_records_filtered_empty_note, name),
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
        subtitle = stringResource(CheckupType.fromKey(rec.checkType).labelRes),
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
                        "医生确认：${stringResource(DoctorConfirm.fromKey(vac.doctorConfirm).labelRes)}",
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
