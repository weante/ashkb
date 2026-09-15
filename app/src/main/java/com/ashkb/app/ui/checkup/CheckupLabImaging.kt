package com.ashkb.app.ui.checkup

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ashkb.app.data.entity.CheckupRecord
import com.ashkb.app.data.entity.ImagingRecord
import com.ashkb.app.data.entity.LabResult
import com.ashkb.app.data.repo.nowIso
import com.ashkb.app.domain.ImagingImport
import com.ashkb.app.domain.ImportTemplates
import com.ashkb.app.domain.LabImport
import com.ashkb.app.domain.ReportImportParser

// ===== 化验详情弹窗 =====
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LabDetailDialog(record: CheckupRecord, vm: CheckupViewModel, onDismiss: () -> Unit) {
    val labs by vm.labResultsFor(record.id).collectAsState(initial = emptyList())
    var showAdd by remember { mutableStateOf(false) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${record.itemName} 化验结果") },
        text = {
            Column {
                if (labs.isEmpty()) {
                    Text("暂无化验指标", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    labs.forEach { lab ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(lab.testName, Modifier.weight(1f))
                            Text(
                                "${lab.value ?: lab.valueText ?: "-"}${lab.unit?.let { " $it" } ?: ""}",
                                color = when (lab.abnormal) {
                                    "high", "low" -> MaterialTheme.colorScheme.error
                                    else -> MaterialTheme.colorScheme.onSurface
                                },
                            )
                        }
                        HorizontalDivider()
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { showAdd = true }) { Text("添加指标") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
        dismissButton = {},
    )

    if (showAdd) {
        var testName by remember { mutableStateOf("") }
        var value by remember { mutableStateOf("") }
        var unit by remember { mutableStateOf("") }
        var refLow by remember { mutableStateOf("") }
        var refHigh by remember { mutableStateOf("") }

        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("添加化验指标") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(testName, { testName = it },
                        label = { Text("指标名称") }, singleLine = true)
                    OutlinedTextField(value, { value = it },
                        label = { Text("数值") }, singleLine = true)
                    OutlinedTextField(unit, { unit = it },
                        label = { Text("单位") }, singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(refLow, { refLow = it },
                            label = { Text("参考下限") }, singleLine = true,
                            modifier = Modifier.weight(1f))
                        OutlinedTextField(refHigh, { refHigh = it },
                            label = { Text("参考上限") }, singleLine = true,
                            modifier = Modifier.weight(1f))
                    }
                }
            },
            confirmButton = {
                TextButton(
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
                            showAdd = false
                        }
                    },
                    enabled = testName.isNotBlank(),
                ) { Text("添加") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("取消") } },
        )
    }
}

// ===== 化验结果列表（v1.0.4：按日期分组，支持 AI 导入） =====
@Composable
internal fun LabsList(labs: List<LabResult>, onImport: () -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (labs.isEmpty()) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("暂无化验数据", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "把「AI 导入模板」连同检验报告照片发给任意 AI 助手，AI 按格式整理后粘贴回来即可批量入库",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = onImport) { Text("AI 导入化验单") }
                    }
                }
            }
        } else {
            item {
                Button(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
                    Text("AI 导入化验单")
                }
            }
            labs.groupBy { it.date }.forEach { (date, rows) ->
                item(key = "lab-$date") {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            val abnormalCount = rows.count { it.abnormal == "high" || it.abnormal == "low" }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(date, fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium)
                                Spacer(Modifier.weight(1f))
                                Text(
                                    if (abnormalCount > 0) "${rows.size} 项 · $abnormalCount 项异常"
                                    else "${rows.size} 项",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (abnormalCount > 0) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            rows.firstNotNullOfOrNull { it.notes }?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            rows.forEach { lab ->
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(lab.testName, Modifier.weight(1f),
                                        style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        listOfNotNull(
                                            lab.value?.let { "%.2f".format(it).trimEnd('0').trimEnd('.') }
                                                ?: lab.valueText,
                                            lab.unit,
                                        ).joinToString(" "),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (lab.abnormal == "high" || lab.abnormal == "low")
                                            FontWeight.SemiBold else FontWeight.Normal,
                                        color = when (lab.abnormal) {
                                            "high", "low" -> MaterialTheme.colorScheme.error
                                            else -> MaterialTheme.colorScheme.onSurface
                                        },
                                    )
                                }
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
            item {
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

// ===== 影像列表（v1.0.4：MRI/CT/X线，支持 AI 导入） =====
@Composable
internal fun ImagingList(
    records: List<ImagingRecord>,
    onImport: () -> Unit,
    onView: (ImagingRecord) -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (records.isEmpty()) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("暂无影像记录", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "把「AI 导入模板」连同 MRI/CT/X线报告照片发给任意 AI 助手，粘贴回复即可入库",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = onImport) { Text("AI 导入影像报告") }
                    }
                }
            }
        } else {
            item {
                Button(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
                    Text("AI 导入影像报告")
                }
            }
            items(records, key = { it.id }) { rec ->
                Card(Modifier.fillMaxWidth().clickable { onView(rec) }) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(rec.examDate, fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium)
                            Text(" · ${ImagingRecord.modalityLabel(rec.modality)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.weight(1f))
                            if (rec.backfill) {
                                Text("（补）", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.tertiary)
                            }
                        }
                        Text(rec.bodyPart, style = MaterialTheme.typography.bodyMedium)
                        rec.hospital?.let {
                            Text("医院：$it", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            item {
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

// ===== 影像详情弹窗 =====
@Composable
internal fun ImagingDetailDialog(record: ImagingRecord, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("${ImagingRecord.modalityLabel(record.modality)} · ${record.bodyPart}（${record.examDate}）")
        },
        text = {
            Column(
                Modifier.fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                record.hospital?.let {
                    Text("医院：$it", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                record.findings?.let { f ->
                    Text("检查所见", fontWeight = FontWeight.Bold)
                    Text(f, style = MaterialTheme.typography.bodySmall)
                }
                record.conclusion?.let { c ->
                    Text("结论 / 印象", fontWeight = FontWeight.Bold)
                    Text(c, style = MaterialTheme.typography.bodySmall)
                }
                record.notes?.let { n ->
                    Text("备注", fontWeight = FontWeight.Bold)
                    Text(n, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
        dismissButton = {},
    )
}

// ===== AI 导入弹窗（v1.0.4：复制模板 → 粘贴 AI 回复 → 解析保存） =====
internal enum class ImportKind(val title: String) {
    LAB("AI 导入化验单"), IMAGING("AI 导入影像报告")
}

@Composable
internal fun AiImportDialog(kind: ImportKind, vm: CheckupViewModel, onDismiss: () -> Unit) {
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

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(kind.title) },
        text = {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("第一步：复制模板，连同报告照片一起发给任意 AI 助手",
                    style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                Card(Modifier.fillMaxWidth()) {
                    Text(
                        template,
                        Modifier.padding(10.dp)
                            .heightIn(max = 160.dp)
                            .verticalScroll(rememberScrollState()),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                OutlinedButton(
                    onClick = { clipboard.setText(AnnotatedString(template)); copied = true },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (copied) "模板已复制 ✓" else "复制模板") }

                Text("第二步：把 AI 的回复原文粘贴到下面",
                    style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                OutlinedTextField(
                    value = pasted,
                    onValueChange = { pasted = it },
                    label = { Text("粘贴 AI 回复") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4,
                )
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                }
                Button(
                    onClick = { tryParse() },
                    enabled = pasted.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("解析") }

                labImport?.let { imp ->
                    HorizontalDivider()
                    val abnormalCount = imp.rows.count { it.abnormal == "high" || it.abnormal == "low" }
                    Text("解析结果：${imp.rows.size} 项" + if (abnormalCount > 0) " · $abnormalCount 项异常" else "",
                        fontWeight = FontWeight.Bold)
                    Text(
                        buildString {
                            append("日期：${imp.date ?: "未识别（将记为今天）"}")
                            imp.hospital?.let { append(" · 医院：$it") }
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Column(
                        Modifier.fillMaxWidth().heightIn(max = 180.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        imp.rows.forEach { r ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(r.testName, Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodySmall)
                                Text(
                                    listOfNotNull(r.value?.toString() ?: r.valueText, r.unit)
                                        .joinToString(" "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = when (r.abnormal) {
                                        "high", "low" -> MaterialTheme.colorScheme.error
                                        else -> MaterialTheme.colorScheme.onSurface
                                    },
                                )
                            }
                        }
                    }
                }
                imagingImport?.let { imp ->
                    HorizontalDivider()
                    Text("解析结果", fontWeight = FontWeight.Bold)
                    Text(
                        "${ImagingRecord.modalityLabel(imp.modality)} · ${imp.bodyPart} · ${imp.date ?: "未识别（将记为今天）"}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    imp.hospital?.let {
                        Text("医院：$it", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    labImport?.let { vm.importLabReport(it) }
                    imagingImport?.let { vm.importImagingReport(it) }
                    onDismiss()
                },
                enabled = labImport != null || imagingImport != null,
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
