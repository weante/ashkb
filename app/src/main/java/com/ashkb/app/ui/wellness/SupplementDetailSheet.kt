package com.ashkb.app.ui.wellness

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle

import com.ashkb.app.R
import com.ashkb.app.data.entity.MedFrequency
import com.ashkb.app.data.entity.Supplement
import com.ashkb.app.data.entity.SupplementCategory
import com.ashkb.app.data.entity.SupplementLog
import com.ashkb.app.domain.SupplementDeletion
import com.ashkb.app.domain.SupplementHistory
import com.ashkb.app.ui.checkup.SheetColumn
import com.ashkb.app.ui.components.DividerList
import com.ashkb.app.ui.components.EmptyState
import com.ashkb.app.ui.components.StatusChip
import com.ashkb.app.ui.theme.Size
import com.ashkb.app.ui.theme.Spacing
import com.ashkb.app.ui.theme.StatusTone

/**
 * v1.0.81（批次 7）：补剂详情弹层——补剂本身的关键信息 + **最近服用记录（逐条可删）**。
 *
 * **为什么需要这个弹层**（维护者原话）：点补剂旁边的删除，确认框说的是「删掉整个补剂」，
 * 而用户当时的意图往往只是「某一天记错了，把那条记录去掉」。一个入口承担两种语义，
 * 文案写得再准也救不回来——所以这里把「哪一天吃过」摊开，并给每一行一个**只删那一行**的入口。
 *
 * 两种删除的分工（文案必须各说各的）：
 *  · 档案列表行上的删除 = 删**整个补剂条目**（连带它的全部记录，确认框报条数）→ [SupplementDeleteDialog]
 *  · 本弹层记录行上的删除 = 只删**这一条记录**（补剂条目与其它日期不受影响）
 *
 * 记录取数规则（近 90 天窗口 / 最多列 30 行 / 日期倒序，且倒序由 SQL 保证）见 `domain/SupplementHistory`。
 * 列表走 Room Flow，删一行后**即时刷新**：不需要手动重查，也不需要「刷新」按钮。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SupplementDetailSheet(vm: WellnessViewModel, sup: Supplement, onDismiss: () -> Unit) {
    // remember(sup.id)：换一个补剂就重新订阅，绝不沿用上一个补剂的记录流（Flow identity 必须稳定）
    val history by remember(sup.id) { vm.observeSupplementHistory(sup) }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val visible = remember(history) { SupplementHistory.visible(history) }
    // 待确认删除的那一条：整张弹层只挂一个确认框，不必每行各持一个（与 WeightManageSheet 的同款取舍）
    var pending by remember(sup.id) { mutableStateOf<SupplementLog?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        SheetColumn {
            Text("${sup.name} ${sup.dose}", style = MaterialTheme.typography.titleLarge)
            Text(
                SupplementCategory.fromKey(sup.category).label + (sup.brand?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // 频率取 MedFrequency 的既有标签（与药单同一套词），补剂表单写入的就是它的 key
            Text(
                MedFrequency.fromKey(sup.frequency).label +
                    (sup.times?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text(stringResource(R.string.nutrition_supplement_history_title), style = MaterialTheme.typography.titleMedium)
            if (history.isEmpty()) {
                EmptyState(
                    icon = Icons.Rounded.Medication,
                    title = stringResource(R.string.nutrition_supplement_history_empty, sup.name),
                )
            } else {
                Text(
                    // 计数用**窗口内的真实条数**（不是截断后的行数），否则用户会以为自己只吃了 30 次
                    stringResource(R.string.nutrition_supplement_history_count, history.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                DividerList(visible, key = { it.id }) { log ->
                    SupplementLogRow(log = log, onDelete = { pending = log })
                }
                if (SupplementHistory.isTruncated(history.size)) {
                    Text(
                        stringResource(R.string.nutrition_supplement_history_truncated, visible.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    pending?.let { log ->
        SupplementLogDeleteDialog(
            log = log,
            onConfirm = {
                vm.deleteSupplementLog(log.id)
                pending = null
            },
            onDismiss = { pending = null },
        )
    }
}

/**
 * 记录行左半部：日期 + 服用时刻；右半部是「已服」胶囊与**只删这一条**的删除入口。
 *
 * 必须是 [RowScope] 扩展：行内的左半列要 `Modifier.weight(1f)` 吃掉剩余宽度，把胶囊与删除按钮推到右边，
 * 而 `weight` 只在 Row 的作用域里存在。挂到 RowScope 上（而不是自带一个 Row）是为了让本函数
 * 直接成为 [DividerList] 那一行的三个子项，不额外套一层 Row——多套一层会让行内边距翻倍。
 */
@Composable
private fun RowScope.SupplementLogRow(log: SupplementLog, onDelete: () -> Unit) {
    Column(
        Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        Text(log.date, style = MaterialTheme.typography.bodyMedium)
        Text(
            stringResource(
                R.string.nutrition_supplement_history_entry,
                (log.takenAt ?: log.recordedAt).take(16).replace("T", " "),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    // 状态胶囊与记录流同源（历史流只取 status='done'），故此处恒为「已服」；
    // 用资源而不是字面量，是为了与今日列表上的同一状态永远同一个词。
    StatusChip(
        text = stringResource(R.string.med_status_taken_short),
        tone = StatusTone.Success,
        icon = Icons.Rounded.CheckCircle,
    )
    IconButton(onClick = onDelete, modifier = Modifier.size(Size.touchMin)) {
        Icon(
            Icons.Rounded.DeleteOutline,
            contentDescription = stringResource(R.string.nutrition_supplement_log_delete_action, log.date),
            modifier = Modifier.size(Size.iconSm),
            tint = MaterialTheme.colorScheme.error,
        )
    }
}

/**
 * v1.0.81（批次 7）：删除**某一条**服用记录的确认框。
 *
 * 措辞的关键是**说清删的是什么、不删什么**：用户上一版的困惑正来自「删除」二字被两种语义共用，
 * 所以这里点名「这一条」「该日期」，并明确补剂条目与其它日期不受影响。
 */
@Composable
private fun SupplementLogDeleteDialog(log: SupplementLog, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.nutrition_supplement_log_delete_confirm, log.date)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(
                    stringResource(R.string.nutrition_supplement_log_delete_note),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    stringResource(R.string.common_delete_irreversible),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

/**
 * v1.0.81（批次 7）：删除**整个补剂条目**的确认框（连带删除它名下的全部服用记录）。
 *
 * 两个设计要点（与批次 6 的复诊记录级联删除同款，理由见 `CheckupDeleteDialogs`）：
 *  · **条数是异步取的**（[LaunchedEffect]）：档案列表里可能有很多补剂，为每行预先算一次计数纯属白烧 IO
 *    ——用户点的只是其中一条。取数期间确认按钮保持禁用，绝不允许「还没算清就让人点确认」。
 *  · **报数与否分两套文案**（`domain/SupplementDeletion` 的变体）：没有记录时不显示「共 0 条」这种噪声，
 *    但也仍要说清「档案会被移除、之后不能再用它打卡」。
 */
@Composable
internal fun SupplementDeleteDialog(vm: WellnessViewModel, sup: Supplement, onDismiss: () -> Unit) {
    var count by remember(sup.id) { mutableStateOf<Int?>(null) }
    // key 到 sup.id：列表里换一条补剂时重新取数，不会沿用上一条的条数
    LaunchedEffect(sup.id) { count = vm.supplementLogCount(sup.id) }
    val loaded = count

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.nutrition_supplement_delete_confirm, sup.name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(
                    when {
                        loaded == null -> stringResource(R.string.nutrition_supplement_delete_counting)
                        SupplementDeletion.variant(loaded) == SupplementDeletion.Variant.CASCADE -> stringResource(
                            R.string.nutrition_supplement_delete_note_cascade,
                            SupplementDeletion.normalizeCount(loaded),
                        )
                        else -> stringResource(R.string.nutrition_supplement_delete_note_plain)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    stringResource(R.string.common_delete_irreversible),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = loaded != null,
                onClick = {
                    vm.deleteSupplement(sup.id)
                    onDismiss()
                },
            ) {
                Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}
