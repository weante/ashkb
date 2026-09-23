package com.ashkb.app.ui.me

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle

import com.ashkb.app.R
import com.ashkb.app.data.entity.DoseState
import com.ashkb.app.data.entity.MedFrequency
import com.ashkb.app.data.entity.Medication
import com.ashkb.app.data.entity.MedicationLog
import com.ashkb.app.data.entity.SkipReason
import com.ashkb.app.data.entity.StopReason
import com.ashkb.app.data.repo.MedicationRepository
import com.ashkb.app.domain.AdherenceCalc
import com.ashkb.app.domain.ClinicalThresholds
import com.ashkb.app.domain.StopWarning
import com.ashkb.app.ui.checkup.SheetColumn
import com.ashkb.app.ui.components.DividerList
import com.ashkb.app.ui.components.EmptyState
import com.ashkb.app.ui.components.ScreenTopBar
import com.ashkb.app.ui.components.SectionCard
import com.ashkb.app.ui.components.StatusChip
import com.ashkb.app.ui.theme.DataLarge
import com.ashkb.app.ui.theme.Size
import com.ashkb.app.ui.theme.Spacing
import com.ashkb.app.ui.theme.StatusTone
import com.ashkb.app.ui.theme.accent

/** 药单管理（route `meds`）：从「我的」页拆出的独立二级页。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedsScreen(
    vm: MeViewModel,
    onAdd: () -> Unit,
    onEdit: (Medication) -> Unit,
    onBack: () -> Unit,
) {
    val meds by vm.meds.collectAsStateWithLifecycle()
    val archived by vm.archivedMeds.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var stopTarget by remember { mutableStateOf<Medication?>(null) }
    // v1.0.48：点药名 → 看该药用药记录（在用与已停用共用同一个弹层）
    var historyTarget by remember { mutableStateOf<Medication?>(null) }
    var archivedExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            ScreenTopBar(
                title = stringResource(R.string.med_manage_title),
                onBack = onBack,
                actions = {
                    IconButton(onClick = onAdd, modifier = Modifier.size(Size.touchMin)) {
                        Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.med_add_new))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(
                start = Spacing.lg,
                end = Spacing.lg,
                top = Spacing.md,
                bottom = Spacing.xxl,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            if (meds.isEmpty()) {
                item(key = "meds-empty") {
                    SectionCard(title = stringResource(R.string.nav_meds)) {
                        EmptyState(
                            icon = Icons.Rounded.Medication,
                            title = stringResource(R.string.med_empty_hint),
                            body = stringResource(R.string.med_add_plan_note),
                            actionLabel = stringResource(R.string.med_add_medication),
                            onAction = onAdd,
                        )
                    }
                }
            } else {
                item(key = "meds-header") {
                    SectionCard(title = stringResource(R.string.me_meds_count, meds.size)) {}
                }
                items(meds, key = { it.id }) { med ->
                    MedRow(
                        med = med,
                        onOpenHistory = { historyTarget = med },
                        onEdit = { onEdit(med) },
                        onStop = { stopTarget = med },
                    )
                    if (med != meds.last()) {
                        androidx.compose.material3.HorizontalDivider(
                            Modifier.padding(vertical = Spacing.sm),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                }
                item(key = "meds-add") {
                    FilledTonalButton(
                        onClick = onAdd,
                        modifier = Modifier.fillMaxWidth().height(Size.touchComfort),
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null)
                        Spacer(Modifier.width(Spacing.sm))
                        Text(stringResource(R.string.med_add_medication))
                    }
                }
            }
            // v1.0.48：已停用药品折叠区。放在 LazyColumn 层级（而非 else 分支内）——
            // 全部药都停用时「在用」列表为空，这里仍应显示，否则停药后就再也找不到该药了。
            // 无归档药时整段不显示（「已停用（0）」只是噪声）。
            if (archived.isNotEmpty()) {
                item(key = "meds-archived") {
                    ArchivedSection(
                        items = archived,
                        expanded = archivedExpanded,
                        onToggle = { archivedExpanded = !archivedExpanded },
                        onOpenHistory = { historyTarget = it },
                    )
                }
            }
        }
    }

    // v1.0.48：用药记录弹层（在用药与被停用的药共用）
    historyTarget?.let { med ->
        MedicationHistorySheet(vm = vm, med = med, onDismiss = { historyTarget = null })
    }

    stopTarget?.let { med ->
        StopMedDialog(
            med = med,
            onConfirm = { reason, note ->
                vm.stopMedication(context, med, reason, note)
                stopTarget = null
            },
            onDismiss = { stopTarget = null },
        )
    }
}

@Composable
private fun MedRow(
    med: Medication,
    onOpenHistory: () -> Unit,
    onEdit: () -> Unit,
    onStop: () -> Unit,
) {
    val needsCheck = !med.checkDoctorTold || !med.checkLeafletRead
    Row(
        Modifier.fillMaxWidth().padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        // v1.0.48：药名区可点 → 该药用药记录。用 onClickLabel 让 TalkBack 念出「查看用药记录」，
        // 否则只念药名，读屏用户点了会以为没反应。
        Column(
            Modifier
                .weight(1f)
                .clickable(
                    onClickLabel = stringResource(R.string.med_view_history),
                    onClick = onOpenHistory,
                )
                .padding(vertical = Spacing.xxs),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Text("${med.name} ${med.dose}", style = MaterialTheme.typography.bodyLarge)
                // 可点提示：不加这个图标，「点药名能看记录」完全不可发现
                Icon(
                    Icons.Rounded.History,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(Size.iconSm),
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Text(
                    buildString {
                        append(MedFrequency.fromKey(med.frequency).label)
                        if (med.route == "injection") append(stringResource(R.string.med_injection_suffix))
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (needsCheck) {
                    StatusChip(stringResource(R.string.med_verify_todo_tag), StatusTone.Warning, Icons.Rounded.WarningAmber)
                }
                // C6：医嘱减量方案进行中（此态下停药不提示「自行停药」风险）
                if (DoseState.of(med) == DoseState.TAPERING) {
                    StatusChip(DoseState.TAPERING.label, StatusTone.Info)
                }
            }
        }
        // v1.0.31：编辑在用药品参数（剂量 / 频次 / 时刻 / 周期等）
        IconButton(onClick = onEdit, modifier = Modifier.size(Size.touchMin)) {
            Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.med_edit_medication))
        }
        TextButton(onClick = onStop) { Text(stringResource(R.string.med_deactivate)) }
    }
}

/** R17 停药原因分类：self_stopped / side_effect 弹警示（D-2 §7）；C6 起减量中豁免「自行停药」警示 */
@Composable
private fun StopMedDialog(
    med: Medication,
    onConfirm: (reason: String, note: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    // 标题保留剂量，与停用前的展示一致（只显示药名会让人不确定停的是哪一条）
    val medName = "${med.name} ${med.dose}"
    var reason by remember { mutableStateOf(StopReason.DOCTOR_SCHEDULED) }
    var note by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.me_med_disable, medName)) },
        text = {
            Column {
                Text(stringResource(R.string.med_deactivate_reason_note), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(Spacing.xs))
                StopReason.entries.forEach { r ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        RadioButton(selected = reason == r, onClick = { reason = r })
                        Text(r.label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (reason == StopReason.OTHER) {
                    OutlinedTextField(
                        value = note, onValueChange = { note = it },
                        label = { Text(stringResource(R.string.med_deactivate_reason_field)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                StopWarning.forStop(reason, med)?.let { w ->
                    Spacer(Modifier.height(Spacing.sm))
                    Text(
                        w,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (StopWarning.showBiologicStopWarning(reason, med)) {
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        stringResource(R.string.med_bio_stop_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(reason.name.lowercase(), note.ifBlank { null }) },
                enabled = reason != StopReason.OTHER || note.isNotBlank(),
            ) { Text(stringResource(R.string.med_deactivate_and_record)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

/**
 * v1.0.48：已停用药品折叠区。
 *
 * 默认收起（列表长时不干扰在用药品）；点标题行展开。每行显示**停药日期与原因**，
 * 点行可看该药停用前的用药记录——这两项此前完全看不到：药单一停用就查无此药。
 */
@Composable
private fun ArchivedSection(
    items: List<MedicationRepository.ArchivedMedication>,
    expanded: Boolean,
    onToggle: () -> Unit,
    onOpenHistory: (Medication) -> Unit,
) {
    SectionCard(title = stringResource(R.string.meds_archived_section, items.size)) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = Size.touchMin)
                .clickable(onClick = onToggle),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(
                stringResource(R.string.meds_archived_hint),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Icon(
                if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (expanded) {
            DividerList(items, key = { it.med.id }) { a ->
                ArchivedRow(a = a, onOpenHistory = { onOpenHistory(a.med) })
            }
        }
    }
}

@Composable
private fun RowScope.ArchivedRow(a: MedicationRepository.ArchivedMedication, onOpenHistory: () -> Unit) {
    Column(
        Modifier
            .weight(1f)
            .clickable(
                onClickLabel = stringResource(R.string.med_view_history),
                onClick = onOpenHistory,
            )
            .padding(vertical = Spacing.xxs),
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text("${a.med.name} ${a.med.dose}", style = MaterialTheme.typography.bodyLarge)
            Icon(
                Icons.Rounded.History,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(Size.iconSm),
            )
        }
        Text(
            // 查不到停药变更时只说明「无记录」，不臆测原因（老数据 / 恢复的旧备份）
            buildString {
                a.stopDate?.let { append(stringResource(R.string.meds_stop_line, it)) }
                if (isNotEmpty()) append(" · ")
                append(
                    a.stopReason?.let { stringResource(R.string.meds_stop_reason_line, it.label) }
                        ?: stringResource(R.string.meds_stop_reason_none),
                )
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        a.stopNote?.takeIf { it.isNotBlank() }?.let {
            Text(
                stringResource(R.string.meds_stop_note_line, it),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * v1.0.48：某条药的用药记录（近 90 天，倒序）。
 *
 * 补剂早就有「服用历史」（WellnessScreen 的 SupplementHistorySheet），药品却只能看报表汇总——
 * 看不到「哪天哪一针打了没有、跳过的原因是什么」。本弹层与补剂那套同款，顶部给出该药
 * 90 天依从率，口径与报表**完全同源**（见 [AdherenceCalc]）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MedicationHistorySheet(vm: MeViewModel, med: Medication, onDismiss: () -> Unit) {
    val logs by remember(med.id) { vm.observeLogsForMed(med.id) }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val summary = remember(logs) { AdherenceCalc.summarize(logs.map { it.status }) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        SheetColumn {
            Text(
                stringResource(R.string.med_history_title, "${med.name} ${med.dose}"),
                style = MaterialTheme.typography.titleLarge,
            )
            if (logs.isEmpty()) {
                Text(
                    stringResource(R.string.med_history_empty, med.name),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    stringResource(R.string.report_adherence_days, MED_HISTORY_DAYS),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // 阈值与配色沿用报表同一套（90 / 70），避免同一指标在两处显示不同等级
                val tone = when {
                    summary.ratePct >= ClinicalThresholds.ADHERENCE_GOOD -> StatusTone.Success
                    summary.ratePct >= ClinicalThresholds.ADHERENCE_FAIR -> StatusTone.Warning
                    else -> StatusTone.Danger
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${summary.ratePct}%", style = DataLarge, color = tone.accent())
                    Spacer(Modifier.width(Spacing.lg))
                    Text(
                        stringResource(
                            R.string.report_adherence_breakdown,
                            summary.done, summary.partial, summary.skipped,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.weight(1f))
                    StatusChip(ClinicalThresholds.adherenceLabel(summary.ratePct), tone)
                }
                Spacer(Modifier.height(Spacing.sm))
                DividerList(logs, key = { it.id }) { log -> MedicationLogRow(log) }
            }
        }
    }
}

/** 用药记录窗口（天）。与 [MedicationRepository.observeLogsForMed] 默认值一致。 */
private const val MED_HISTORY_DAYS = 90

@Composable
private fun RowScope.MedicationLogRow(log: MedicationLog) {
    val slotText = when {
        log.scheduledTime != null -> stringResource(R.string.med_history_slot_line, log.scheduledTime)
        log.prnFlag -> stringResource(R.string.med_history_prn)
        else -> null
    }
    val siteText = log.injSite?.takeIf { it.isNotBlank() }
        ?.let { stringResource(R.string.med_history_inj_site_line, it) }
    // 原因优先取枚举中文名；取不到（未知值）就显示原始字符串，不伪装成「其他」
    val reasonText = log.reason?.takeIf { it.isNotBlank() }
        ?.let { stringResource(R.string.med_history_reason_line, SkipReason.fromKey(it)?.label ?: it) }
    val detail = listOfNotNull(slotText, siteText, reasonText, log.notes?.takeIf { it.isNotBlank() })
        .joinToString(" · ")

    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(log.date, style = MaterialTheme.typography.bodyMedium)
            StatusChip(statusLabel(log.status), statusTone(log.status))
        }
        if (detail.isNotEmpty()) {
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 记录状态文案（写入侧只产生三态，未知值按「已服」兜底） */
@Composable
private fun statusLabel(status: String): String = when (status) {
    AdherenceCalc.SKIPPED -> stringResource(R.string.med_history_status_skipped)
    AdherenceCalc.PARTIAL -> stringResource(R.string.med_history_status_partial)
    else -> stringResource(R.string.med_history_status_done)
}

/** 跳过用**中性色**而非红色：遵医嘱暂停不算「错误」，不该报警。 */
private fun statusTone(status: String): StatusTone = when (status) {
    AdherenceCalc.SKIPPED -> StatusTone.Neutral
    AdherenceCalc.PARTIAL -> StatusTone.Warning
    else -> StatusTone.Success
}
