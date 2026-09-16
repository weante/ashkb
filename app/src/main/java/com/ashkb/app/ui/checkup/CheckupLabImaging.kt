package com.ashkb.app.ui.checkup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.TrendingDown
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.ashkb.app.data.entity.CheckupRecord
import com.ashkb.app.data.entity.ImagingRecord
import com.ashkb.app.data.entity.LabResult
import com.ashkb.app.data.repo.nowIso
import com.ashkb.app.domain.ImagingImport
import com.ashkb.app.domain.ImportTemplates
import com.ashkb.app.domain.LabImport
import com.ashkb.app.domain.LabImportRow
import com.ashkb.app.domain.ReportImportParser
import com.ashkb.app.ui.components.DividerList
import com.ashkb.app.ui.components.SectionCard
import com.ashkb.app.ui.components.StatusChip
import com.ashkb.app.ui.theme.Size
import com.ashkb.app.ui.theme.Spacing
import com.ashkb.app.ui.theme.StatusTone

// ===== 化验值展示：异常不再只用红色——偏高/偏低分方向三重编码 =====

@Composable
private fun LabValue(valueText: String, unit: String?, abnormal: String?) {
    val text = listOfNotNull(valueText, unit).joinToString(" ")
    when (abnormal) {
        "high" -> StatusChip(text = "$text 偏高", tone = StatusTone.Danger, icon = Icons.Rounded.TrendingUp)
        "low" -> StatusChip(text = "$text 偏低", tone = StatusTone.Warning, icon = Icons.Rounded.TrendingDown)
        else -> Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun labValueText(lab: LabResult): String =
    lab.value?.let { "%.2f".format(it).trimEnd('0').trimEnd('.') } ?: lab.valueText ?: "-"

// ===== 化验详情弹窗 =====
@Composable
internal fun LabDetailDialog(record: CheckupRecord, vm: CheckupViewModel, onDismiss: () -> Unit) {
    val labs by vm.labResultsFor(record.id).collectAsState(initial = emptyList())
    var showAdd by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${record.itemName} 化验结果") },
        text = {
            Column {
                if (labs.isEmpty()) {
                    Text("暂无化验指标", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    DividerList(items = labs) { lab ->
                        Text(lab.testName, Modifier.weight(1f))
                        LabValue(labValueText(lab), lab.unit, lab.abnormal)
                    }
                }
                Spacer(Modifier.height(Spacing.sm))
                OutlinedButton(onClick = { showAdd = true }) { Text("添加指标") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )

    if (showAdd) {
        AddLabSheet(record = record, vm = vm, onDismiss = { showAdd = false })
    }
}

// ===== 化验结果列表（按日期分组，支持 AI 导入） =====
@Composable
internal fun LabsList(labs: List<LabResult>, onImport: () -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        if (labs.isEmpty()) {
            item {
                SectionCard(title = "暂无化验数据") {
                    Text(
                        "把「AI 导入模板」连同检验报告照片发给任意 AI 助手，AI 按格式整理后粘贴回来即可批量入库",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(Spacing.sm))
                    Button(
                        onClick = onImport,
                        modifier = Modifier.fillMaxWidth().heightIn(min = Size.touchMin),
                    ) { Text("AI 导入化验单") }
                }
            }
        } else {
            item {
                Button(
                    onClick = onImport,
                    modifier = Modifier.fillMaxWidth().heightIn(min = Size.touchMin),
                ) { Text("AI 导入化验单") }
            }
            labs.groupBy { it.date }.forEach { (date, rows) ->
                item(key = "lab-$date") {
                    val abnormalCount = rows.count { it.abnormal == "high" || it.abnormal == "low" }
                    SectionCard(
                        title = date,
                        subtitle = if (abnormalCount > 0) "${rows.size} 项 · $abnormalCount 项异常" else "${rows.size} 项",
                    ) {
                        DividerList(items = rows) { lab ->
                            Text(lab.testName, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            LabValue(labValueText(lab), lab.unit, lab.abnormal)
                        }
                    }
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
                SectionCard(title = "暂无影像记录") {
                    Text(
                        "把「AI 导入模板」连同 MRI/CT/X线报告照片发给任意 AI 助手，粘贴回复即可入库",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(Spacing.sm))
                    Button(
                        onClick = onImport,
                        modifier = Modifier.fillMaxWidth().heightIn(min = Size.touchMin),
                    ) { Text("AI 导入影像报告") }
                }
            }
        } else {
            item {
                Button(
                    onClick = onImport,
                    modifier = Modifier.fillMaxWidth().heightIn(min = Size.touchMin),
                ) { Text("AI 导入影像报告") }
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
                                StatusChip(text = "补记", tone = StatusTone.Warning)
                            }
                        }
                        Text(rec.bodyPart, style = MaterialTheme.typography.bodyMedium)
                        rec.hospital?.let {
                            Text(
                                "医院：$it",
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
                        "医院：$it",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                record.findings?.let { f -> LabeledBlock("检查所见", f) }
                record.conclusion?.let { c -> LabeledBlock("结论 / 印象", c) }
                record.notes?.let { n -> LabeledBlock("备注", n) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

@Composable
private fun LabeledBlock(label: String, content: String) {
    Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    Text(content, style = MaterialTheme.typography.bodySmall)
}

// ===== AI 导入（复制模板 → 粘贴 AI 回复 → 解析保存；流程长，迁 ModalBottomSheet） =====
internal enum class ImportKind(val title: String) {
    LAB("AI 导入化验单"), IMAGING("AI 导入影像报告")
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

    fun tryParse() {
        error = null
        labImport = null
        imagingImport = null
        if (kind == ImportKind.LAB) {
            val parsed = ReportImportParser.parseLab(pasted)
            if (parsed == null || parsed.rows.isEmpty()) {
                error = "未解析出化验数据——请粘贴 AI 按模板输出的完整文本（含「日期」「医院」和各行数据）"
            } else {
                labImport = parsed
            }
        } else {
            val parsed = ReportImportParser.parseImaging(pasted)
            if (parsed == null) {
                error = "未解析出影像数据——请粘贴 AI 按模板输出的完整文本（需含「类型」行）"
            } else {
                imagingImport = parsed
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        SheetColumn {
            Text(kind.title, style = MaterialTheme.typography.titleLarge)

            Text("第一步：复制模板，连同报告照片一起发给任意 AI 助手", style = MaterialTheme.typography.bodyMedium)
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
                    Text(if (copied) "已复制" else "复制模板")
                }
            }

            Text("第二步：把 AI 的回复原文粘贴到下面", style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(
                value = pasted,
                onValueChange = { pasted = it },
                label = { Text("粘贴 AI 回复") },
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
            ) { Text("解析") }

            labImport?.let { imp ->
                val abnormalCount = imp.rows.count { it.abnormal == "high" || it.abnormal == "low" }
                Text(
                    "解析结果：${imp.rows.size} 项" + if (abnormalCount > 0) " · $abnormalCount 项异常" else "",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    buildString {
                        append("日期：${imp.date ?: "未识别（将记为今天）"}")
                        imp.hospital?.let { append(" · 医院：$it") }
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
            }
            imagingImport?.let { imp ->
                Text("解析结果", style = MaterialTheme.typography.titleSmall)
                Text(
                    "${ImagingRecord.modalityLabel(imp.modality)} · ${imp.bodyPart} · ${imp.date ?: "未识别（将记为今天）"}",
                    style = MaterialTheme.typography.bodySmall,
                )
                imp.hospital?.let {
                    Text("医院：$it", style = MaterialTheme.typography.bodySmall)
                }
            }

            SheetSaveButton(
                text = "保存",
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
            Text("添加化验指标", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(testName, { testName = it }, label = { Text("指标名称") }, singleLine = true)
            OutlinedTextField(value, { value = it }, label = { Text("数值") }, singleLine = true)
            OutlinedTextField(unit, { unit = it }, label = { Text("单位") }, singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedTextField(refLow, { refLow = it },
                    label = { Text("参考下限") }, singleLine = true,
                    modifier = Modifier.weight(1f))
                OutlinedTextField(refHigh, { refHigh = it },
                    label = { Text("参考上限") }, singleLine = true,
                    modifier = Modifier.weight(1f))
            }
            SheetSaveButton(
                text = "添加",
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
