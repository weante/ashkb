package com.ashkb.app.ui.checkup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.TrendingDown
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle


import com.ashkb.app.data.entity.CheckupRecord
import com.ashkb.app.data.entity.ImagingRecord
import com.ashkb.app.data.entity.LabResult
import com.ashkb.app.data.repo.nowIso
import com.ashkb.app.domain.ImagingImport
import com.ashkb.app.domain.ImportTemplates
import com.ashkb.app.domain.LabImport
import com.ashkb.app.domain.LabImportRow
import com.ashkb.app.domain.ReportImportParser
import com.ashkb.app.R
import com.ashkb.app.ui.components.DividerList
import com.ashkb.app.ui.components.SectionCard
import com.ashkb.app.ui.components.StatusChip
import com.ashkb.app.ui.theme.Size
import com.ashkb.app.ui.theme.Spacing
import com.ashkb.app.ui.theme.StatusTone

// ===== 化验值展示：异常不再只用红色——偏高/偏低分方向三重编码 =====

@Composable
private fun LabValue(valueText: String, unit: String?, abnormal: String?, onClick: (() -> Unit)? = null) {
    val text = listOfNotNull(valueText, unit).joinToString(" ")
    when (abnormal) {
        "high" -> StatusChip(text = stringResource(R.string.lab_value_high, text), tone = StatusTone.Danger, icon = Icons.Rounded.TrendingUp, onClick = onClick)
        "low" -> StatusChip(text = stringResource(R.string.lab_value_low, text), tone = StatusTone.Warning, icon = Icons.Rounded.TrendingDown, onClick = onClick)
        else -> Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun fmtDouble(v: Double): String = "%.2f".format(v).trimEnd('0').trimEnd('.')

private fun labValueText(lab: LabResult): String =
    lab.value?.let { fmtDouble(it) } ?: lab.valueText ?: "-"

private fun LabResult.isAbnormal() = abnormal == "high" || abnormal == "low"

/** X1：该指标的参考范围文案（refLow/refHigh 缺一侧时按 ≥/≤ 表述；都缺则如实说明）。 */
@Composable
private fun refRangeText(lab: LabResult): String {
    val unit = lab.unit?.let { " $it" } ?: ""
    return when {
        lab.refLow != null && lab.refHigh != null ->
            stringResource(R.string.lab_ref_range_both, fmtDouble(lab.refLow!!), fmtDouble(lab.refHigh!!)) + unit
        lab.refHigh != null -> stringResource(R.string.lab_ref_range_max, fmtDouble(lab.refHigh!!)) + unit
        lab.refLow != null -> stringResource(R.string.lab_ref_range_min, fmtDouble(lab.refLow!!)) + unit
        else -> stringResource(R.string.lab_ref_range_missing)
    }
}

/** X1：点击偏高/偏低胶囊展开该指标的参考范围——复诊沟通时「正常值是多少」张口就来。 */
@Composable
private fun RowScope.LabRow(lab: LabResult) {
    var showRef by remember { mutableStateOf(false) }
    Column(Modifier.weight(1f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(lab.testName, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            LabValue(labValueText(lab), lab.unit, lab.abnormal, onClick = if (lab.isAbnormal()) ({ showRef = !showRef }) else null)
        }
        if (showRef) {
            Text(
                refRangeText(lab),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 化验分组：异常项置顶，正常项默认折叠——复诊沟通先看要紧的。 */
@Composable
private fun LabGroup(rows: List<LabResult>) {
    val (abnormal, normal) = remember(rows) { rows.partition { it.isAbnormal() } }
    var showNormal by remember { mutableStateOf(false) }
    if (abnormal.isNotEmpty()) {
        DividerList(items = abnormal, key = { it.id }) { lab -> LabRow(lab) }
    }
    if (normal.isNotEmpty()) {
        if (abnormal.isNotEmpty()) {
            HorizontalDivider(
                Modifier.padding(vertical = Spacing.xs),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }
        if (showNormal) {
            DividerList(items = normal, key = { it.id }) { lab -> LabRow(lab) }
        }
        TextButton(
            onClick = { showNormal = !showNormal },
            modifier = Modifier.heightIn(min = Size.touchMin),
        ) {
            Text(if (showNormal) stringResource(R.string.lab_collapse_normal) else stringResource(R.string.lab_expand_normal, normal.size))
        }
    }
}

// ===== 化验详情弹窗 =====
@Composable
internal fun LabDetailDialog(record: CheckupRecord, vm: CheckupViewModel, onDismiss: () -> Unit) {
    val labFlow = remember(record.id) { vm.labResultsFor(record.id) }
    val labs by labFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    var showAdd by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.lab_dialog_title, record.itemName)) },
        text = {
            Column {
                if (labs.isEmpty()) {
                    Text(stringResource(R.string.lab_indicators_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LabGroup(labs)
                }
                Spacer(Modifier.height(Spacing.sm))
                OutlinedButton(onClick = { showAdd = true }) { Text(stringResource(R.string.lab_add_indicator)) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) } },
    )

    if (showAdd) {
        AddLabSheet(record = record, vm = vm, onDismiss = { showAdd = false })
    }
}

// ===== 化验结果列表（按日期分组，支持 AI 导入；底部翻页加载更早记录） =====
@Composable
internal fun LabsList(
    labs: List<LabResult>,
    canLoadMore: Boolean,
    onLoadMore: () -> Unit,
    onImport: () -> Unit,
) {
    // 派生计算上提到 LazyColumn 之外并 remember：LazyListScope 不是 @Composable 作用域，
    // 写在 item/forEach 内会随每次重组重跑 groupBy / maxOfOrNull（数十条化验 × 每次重组）
    val grouped = remember(labs) { labs.groupBy { it.date } }
    val latestDate = remember(labs) { labs.maxOfOrNull { it.date } }
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        if (labs.isEmpty()) {
            item {
                SectionCard(title = stringResource(R.string.report_no_lab_data)) {
                    Text(
                        stringResource(R.string.lab_ai_import_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(Spacing.sm))
                    Button(
                        onClick = onImport,
                        modifier = Modifier.fillMaxWidth().heightIn(min = Size.touchMin),
                    ) { Text(stringResource(R.string.lab_ai_import_title)) }
                }
            }
        } else {
            item {
                Button(
                    onClick = onImport,
                    modifier = Modifier.fillMaxWidth().heightIn(min = Size.touchMin),
                ) { Text(stringResource(R.string.lab_ai_import_title)) }
            }
            // Y1：日期分组折叠——历史数据多时页面不再被全展开的旧日期撑长；
            // 默认展开规则贴合复诊沟通导向：有异常的日期或最近一次化验展开，其余收起
            grouped.forEach { (date, rows) ->
                item(key = "lab-$date") {
                    val abnormalCount = rows.count { it.isAbnormal() }
                    // item key 稳定 + rememberSaveable：翻页加载更早记录、滚动回收、旋转屏均保持折叠状态
                    var expanded by rememberSaveable {
                        mutableStateOf(abnormalCount > 0 || date == latestDate)
                    }
                    SectionCard(
                        title = date,
                        subtitle = if (abnormalCount > 0) stringResource(R.string.lab_count_abnormal, rows.size, abnormalCount) else stringResource(R.string.lab_count_plain, rows.size),
                        action = {
                            TextButton(
                                onClick = { expanded = !expanded },
                                modifier = Modifier.heightIn(min = Size.touchMin),
                            ) {
                                Icon(
                                    if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                    contentDescription = null, // 语义由文字承载
                                    modifier = Modifier.size(Size.iconSm),
                                )
                                Text(stringResource(if (expanded) R.string.lab_group_collapse else R.string.lab_group_expand))
                            }
                        },
                    ) {
                        if (expanded) LabGroup(rows)
                    }
                }
            }
            if (canLoadMore) {
                item(key = "lab-load-more") {
                    OutlinedButton(
                        onClick = onLoadMore,
                        modifier = Modifier.fillMaxWidth().heightIn(min = Size.touchMin),
                    ) { Text(stringResource(R.string.lab_load_older)) }
                }
            }
            item { Spacer(Modifier.height(Spacing.xxl)) }
        }
    }
}

// ===== 影像列表（MRI/CT/X线，支持 AI 导入） =====
@Composable
internal fun ImagingList(
    records: List<ImagingRecord>,
    onImport: () -> Unit,
    onView: (ImagingRecord) -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        if (records.isEmpty()) {
            item {
                SectionCard(title = stringResource(R.string.imaging_empty)) {
                    Text(
                        stringResource(R.string.imaging_ai_import_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(Spacing.sm))
                    Button(
                        onClick = onImport,
                        modifier = Modifier.fillMaxWidth().heightIn(min = Size.touchMin),
                    ) { Text(stringResource(R.string.imaging_ai_import_title)) }
                }
            }
        } else {
            item {
                Button(
                    onClick = onImport,
                    modifier = Modifier.fillMaxWidth().heightIn(min = Size.touchMin),
                ) { Text(stringResource(R.string.imaging_ai_import_title)) }
            }
            items(records, key = { it.id }) { rec ->
                Surface(
                    onClick = { onView(rec) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(Spacing.lg),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        ) {
                            Text(rec.examDate, style = MaterialTheme.typography.titleSmall)
                            Text(
                                ImagingRecord.modalityLabel(rec.modality),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.weight(1f))
                            if (rec.backfill) {
                                StatusChip(text = stringResource(R.string.today_supplement_log), tone = StatusTone.Warning)
                            }
                        }
                        Text(rec.bodyPart, style = MaterialTheme.typography.bodyMedium)
                        rec.hospital?.let {
                            Text(
                                stringResource(R.string.checkup_hospital_line, it),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        rec.conclusion?.let { c ->
                            Text(
                                "结论：${c.lineSequence().firstOrNull { it.isNotBlank() } ?: ""}",
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2,
                            )
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(Spacing.xxl)) }
        }
    }
}

// ===== 影像详情弹窗 =====
@Composable
internal fun ImagingDetailDialog(record: ImagingRecord, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("${ImagingRecord.modalityLabel(record.modality)} · ${record.bodyPart}（${record.examDate}）")
        },
        text = {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                record.hospital?.let {
                    Text(
                        stringResource(R.string.checkup_hospital_line, it),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                record.findings?.let { f -> LabeledBlock(stringResource(R.string.imaging_findings), f) }
                record.conclusion?.let { c -> LabeledBlock(stringResource(R.string.imaging_impression), c) }
                record.notes?.let { n -> LabeledBlock(stringResource(R.string.common_notes), n) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) } },
    )
}

@Composable
private fun LabeledBlock(label: String, content: String) {
    Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    Text(content, style = MaterialTheme.typography.bodySmall)
}

// ===== AI 导入（复制模板 → 粘贴 AI 回复 → 解析保存；流程长，迁 ModalBottomSheet） =====
internal enum class ImportKind { LAB, IMAGING }

@Composable
internal fun ImportKind.title(): String = when (this) {
    ImportKind.LAB -> stringResource(R.string.lab_ai_import_title)
    ImportKind.IMAGING -> stringResource(R.string.imaging_ai_import_title)
}

/** R4：解析未识别的行不再静默丢弃——「已导入 N 项 · M 行未识别」，可展开查看原文手补。 */
@Composable
private fun SkippedLinesHint(importedCount: Int, lines: List<String>) {
    var expanded by remember(lines) { mutableStateOf(false) }
    TextButton(
        onClick = { expanded = !expanded },
        modifier = Modifier.heightIn(min = Size.touchMin),
    ) {
        Text(stringResource(R.string.ai_import_skipped_summary, importedCount, lines.size))
    }
    if (expanded) {
        Surface(
            Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column(
                Modifier
                    .heightIn(max = 120.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Text(stringResource(R.string.ai_import_skipped_title), style = MaterialTheme.typography.labelLarge)
                lines.forEach {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AiImportSheet(kind: ImportKind, vm: CheckupViewModel, onDismiss: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    val template = if (kind == ImportKind.LAB) ImportTemplates.LAB else ImportTemplates.IMAGING
    var pasted by remember { mutableStateOf("") }
    var copied by remember { mutableStateOf(false) }
    var labImport by remember { mutableStateOf<LabImport?>(null) }
    var imagingImport by remember { mutableStateOf<ImagingImport?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val labParseEmpty = stringResource(R.string.lab_parse_empty)
    val imagingParseEmpty = stringResource(R.string.imaging_parse_empty)

    fun tryParse() {
        error = null
        labImport = null
        imagingImport = null
        if (kind == ImportKind.LAB) {
            val parsed = ReportImportParser.parseLab(pasted)
            if (parsed == null || parsed.rows.isEmpty()) {
                error = labParseEmpty
            } else {
                labImport = parsed
            }
        } else {
            val parsed = ReportImportParser.parseImaging(pasted)
            if (parsed == null) {
                error = imagingParseEmpty
            } else {
                imagingImport = parsed
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        SheetColumn {
            Text(kind.title(), style = MaterialTheme.typography.titleLarge)

            Text(stringResource(R.string.ai_import_step1), style = MaterialTheme.typography.bodyMedium)
            Surface(
                Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Text(
                    template,
                    Modifier
                        .heightIn(max = 160.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(Spacing.md),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            OutlinedButton(
                onClick = { clipboard.setText(AnnotatedString(template)); copied = true },
                modifier = Modifier.fillMaxWidth().heightIn(min = Size.touchMin),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (copied) {
                        Icon(
                            Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(Size.iconSm),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.size(Spacing.xs))
                    }
                    Text(if (copied) stringResource(R.string.common_copied) else stringResource(R.string.ai_import_copy_template))
                }
            }

            Text(stringResource(R.string.ai_import_step2), style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(
                value = pasted,
                onValueChange = { pasted = it },
                label = { Text(stringResource(R.string.common_paste_ai_reply)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
            )
            error?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            OutlinedButton(
                onClick = { tryParse() },
                enabled = pasted.isNotBlank(),
                modifier = Modifier.fillMaxWidth().heightIn(min = Size.touchMin),
            ) { Text(stringResource(R.string.ai_import_parse_action)) }

            labImport?.let { imp ->
                val abnormalCount = imp.rows.count { it.abnormal == "high" || it.abnormal == "low" }
                Text(
                    if (abnormalCount > 0) stringResource(R.string.lab_parse_result_abnormal, imp.rows.size, abnormalCount)
                    else stringResource(R.string.lab_parse_result, imp.rows.size),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    buildString {
                        append("日期：${imp.date ?: stringResource(R.string.symptom_unrecognized_today)}")
                        imp.hospital?.let { append(stringResource(R.string.lab_hospital_suffix, it)) }
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
                Surface(
                    Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    Column(
                        Modifier
                            .heightIn(max = 200.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(Spacing.md),
                    ) {
                        DividerList(items = imp.rows) { r: LabImportRow ->
                            Text(r.testName, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            LabValue(r.value?.toString() ?: r.valueText, r.unit, r.abnormal)
                        }
                    }
                }
                if (imp.skippedLines.isNotEmpty()) SkippedLinesHint(imp.rows.size, imp.skippedLines)
            }
            imagingImport?.let { imp ->
                Text(stringResource(R.string.ai_import_parse_result), style = MaterialTheme.typography.titleSmall)
                Text(
                    "${ImagingRecord.modalityLabel(imp.modality)} · ${imp.bodyPart} · ${imp.date ?: stringResource(R.string.symptom_unrecognized_today)}",
                    style = MaterialTheme.typography.bodySmall,
                )
                imp.hospital?.let {
                    Text(stringResource(R.string.checkup_hospital_line, it), style = MaterialTheme.typography.bodySmall)
                }
                if (imp.skippedLines.isNotEmpty()) SkippedLinesHint(1, imp.skippedLines)
            }

            SheetSaveButton(
                text = stringResource(R.string.common_save),
                enabled = labImport != null || imagingImport != null,
                onClick = {
                    labImport?.let { vm.importLabReport(it) }
                    imagingImport?.let { vm.importImagingReport(it) }
                    onDismiss()
                },
            )
        }
    }
}

// ===== 添加化验指标（多字段表单，ModalBottomSheet） =====
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddLabSheet(record: CheckupRecord, vm: CheckupViewModel, onDismiss: () -> Unit) {
    var testName by remember { mutableStateOf("") }
    var value by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("") }
    var refLow by remember { mutableStateOf("") }
    var refHigh by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        SheetColumn {
            Text(stringResource(R.string.lab_add_indicator_action), style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(testName, { testName = it }, label = { Text(stringResource(R.string.lab_indicator_name)) }, singleLine = true)
            OutlinedTextField(value, { value = it }, label = { Text(stringResource(R.string.lab_value)) }, singleLine = true)
            OutlinedTextField(unit, { unit = it }, label = { Text(stringResource(R.string.common_unit)) }, singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedTextField(refLow, { refLow = it },
                    label = { Text(stringResource(R.string.lab_ref_lower)) }, singleLine = true,
                    modifier = Modifier.weight(1f))
                OutlinedTextField(refHigh, { refHigh = it },
                    label = { Text(stringResource(R.string.lab_ref_upper)) }, singleLine = true,
                    modifier = Modifier.weight(1f))
            }
            SheetSaveButton(
                text = stringResource(R.string.common_add),
                enabled = testName.isNotBlank(),
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
                        onDismiss()
                    }
                },
            )
        }
    }
}

// ===== sheet 内部布局（与 WellnessScreen 同款惯例） =====

@Composable
internal fun SheetColumn(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.lg)
            .padding(bottom = Spacing.xl)
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        content = content,
    )
}

@Composable
internal fun SheetSaveButton(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = Size.touchMin),
    ) { Text(text) }
}

