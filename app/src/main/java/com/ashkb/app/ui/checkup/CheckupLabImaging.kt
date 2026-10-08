package com.ashkb.app.ui.checkup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.TrendingDown
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.ashkb.app.domain.ImagingImport
import com.ashkb.app.domain.ImportTemplates
import com.ashkb.app.domain.LabImport
import com.ashkb.app.domain.LabImportRow
import com.ashkb.app.domain.LabUnits
import com.ashkb.app.domain.ReportImportParser
import com.ashkb.app.R
import com.ashkb.app.ui.components.DisclaimerNote
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

/**
 * v1.0.78（批次 4 收尾）：异常标记 key（`high` / `low` / `normal` / …）→ 展示文案。
 * key 绝不出现在 UI，文案一律走 strings.xml（改版方案 §11）。
 * v1.0.80（批次 6）：由 private 放开为 internal——化验编辑表单（CheckupLabForms.kt）也要用它标注只读的 AI 标记。
 */
@Composable
internal fun labMarkLabel(key: String?): String = when (key) {
    "high" -> stringResource(R.string.lab_mark_high)
    "low" -> stringResource(R.string.lab_mark_low)
    "normal" -> stringResource(R.string.lab_mark_normal)
    "abnormal" -> stringResource(R.string.lab_mark_abnormal)
    else -> stringResource(R.string.lab_mark_unknown)
}

/**
 * v1.0.78（批次 4 收尾）：AI 原始标记与本地判读**不一致**时的并列说明；一致（或任一侧缺失）返回 null。
 *
 * 为什么只在不一致时显示：`abnormal` 已由本地参考范围判读接管（v1.0.77），AI 标记只在本地判不了时
 * 兜底——两条口径相同是常态，逐行加一句「AI 也这么标」纯属噪声；只有**分歧**才值得占一行：
 * 那正是「AI 说正常、参考范围说偏高」这类必须让复诊医生看见的信息（第三份审查报告 S-12）。
 */
@Composable
private fun labMarkConflictNote(lab: LabResult): String? {
    val ai = lab.aiAbnormal ?: return null
    val local = lab.abnormal ?: return null
    if (ai == local) return null
    return stringResource(R.string.lab_mark_note, labMarkLabel(ai), labMarkLabel(local))
}

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
private fun RowScope.LabRow(lab: LabResult, onEdit: ((LabResult) -> Unit)?) {
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
        // v1.0.78（批次 4 收尾）：AI 标记与本地判读分歧时并列展示（一致则整行不出现）
        labMarkConflictNote(lab)?.let { note ->
            Text(
                note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    // v1.0.80（批次 6）：每行一个「修改」入口——改数值必然重跑本地判读，故不能只做删除。
    // 用图标而不是文字按钮：一行已经要放指标名 + 数值胶囊，再挤一个文字按钮会在窄屏截断指标名。
    if (onEdit != null) {
        IconButton(onClick = { onEdit(lab) }, modifier = Modifier.size(Size.touchMin)) {
            Icon(
                Icons.Rounded.Edit,
                contentDescription = stringResource(R.string.lab_edit_action),
                modifier = Modifier.size(Size.iconSm),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * C3：把一段化验按「项目名 + 单位」成组渲染——同一项目跨院 / 换设备用了不同单位时，
 * 不会被误当成同一列数值直接比较。组头用 R.string.lab_unit_label 标注单位（unit 为空则不占行）。
 */
@Composable
private fun LabUnitGroups(rows: List<LabResult>, onEdit: ((LabResult) -> Unit)? = null) {
    val groups = remember(rows) { LabUnits.groupByUnit(rows) }
    // 只有「同一项目存在多个单位」时才需要标出单位——否则行内已带单位，再挂一行会白白把列表拉长一倍
    val mixed = remember(rows) { LabUnits.mixedUnitTests(rows).toSet() }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        groups.forEachIndexed { index, group ->
            if (index > 0) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                if (group.testName in mixed) {
                    group.unit?.let { unit ->
                        Text(
                            stringResource(R.string.lab_unit_label, unit),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                DividerList(items = group.results, key = { lab -> lab.id }) { lab -> LabRow(lab, onEdit) }
            }
        }
    }
}

/**
 * 化验分组：异常项置顶，正常项默认折叠——复诊沟通先看要紧的。
 * C3：段内再按「项目名 + 单位」分组，异常 / 正常两段的划分与折叠开关保持不变（折叠状态不退化）。
 * v1.0.80（批次 6）：[onEdit] 一路透传到行内图标（化验详情弹窗里也给入口，两处行为一致）。
 */
@Composable
private fun LabGroup(rows: List<LabResult>, onEdit: ((LabResult) -> Unit)? = null) {
    val (abnormal, normal) = remember(rows) { rows.partition { it.isAbnormal() } }
    var showNormal by remember { mutableStateOf(false) }
    if (abnormal.isNotEmpty()) {
        LabUnitGroups(abnormal, onEdit)
    }
    if (normal.isNotEmpty()) {
        if (abnormal.isNotEmpty()) {
            HorizontalDivider(
                Modifier.padding(vertical = Spacing.xs),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }
        if (showNormal) {
            LabUnitGroups(normal, onEdit)
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
/**
 * 某次复诊记录下的化验明细。
 *
 * v1.0.80（批次 6）：行内给「修改」入口（图标），表单里同时能删除——
 * 从记录卡进来时看到的化验与此处完全同源，两处的改 / 删行为也必须一致。
 */
@Composable
internal fun LabDetailDialog(record: CheckupRecord, vm: CheckupViewModel, onDismiss: () -> Unit) {
    val labFlow = remember(record.id) { vm.labResultsFor(record.id) }
    val labs by labFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    var showAdd by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<LabResult?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.lab_dialog_title, record.itemName)) },
        text = {
            Column {
                if (labs.isEmpty()) {
                    Text(stringResource(R.string.lab_indicators_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LabGroup(labs, onEdit = { editTarget = it })
                }
                Spacer(Modifier.height(Spacing.sm))
                OutlinedButton(onClick = { showAdd = true }) { Text(stringResource(R.string.lab_add_indicator)) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) } },
    )

    if (showAdd) {
        LabResultFormSheet(
            existing = null, date = record.date, checkupId = record.id,
            onSave = { vm.saveLabResult(it) }, onDelete = null,
            onDismiss = { showAdd = false },
        )
    }
    editTarget?.let { lab ->
        LabResultFormSheet(
            existing = lab, date = lab.date, checkupId = lab.checkupId,
            onSave = { vm.saveLabResult(it) },
            onDelete = { vm.deleteLabResult(it.id) },
            onDismiss = { editTarget = null },
        )
    }
}

// ===== 化验结果列表（按日期分组，支持 AI 导入；底部翻页加载更早记录） =====
/**
 * @param totalCount v1.0.87（批次 13）：库里化验的**总条数**（COUNT(*)）。
 *   列表本身只是一个分页窗口（[labs] 最多装窗口大小行），窗口装满时页面上的分组数之和
 *   会小于总数——健康页摘要正是报总数，不把这层口径写出来，两个数字看起来就是"对不上"。
 * @param onAttachDate 归档该日期的附件（附件本身也属于这次抽血）
 * @param onLinkDate   把该日期的整组化验归属到某条复诊记录
 * @param onEditLab    v1.0.80（批次 6）修改单条化验（表单内可删除）：改数值 / 参考范围会重跑本地判读
 */
@Composable
internal fun LabsList(
    labs: List<LabResult>,
    canLoadMore: Boolean,
    onLoadMore: () -> Unit,
    onImport: () -> Unit,
    onAttachDate: (String) -> Unit,
    onLinkDate: (String) -> Unit,
    onEditLab: (LabResult) -> Unit,
    totalCount: Int = labs.size,
) {
    // 派生计算上提到 LazyColumn 之外并 remember：LazyListScope 不是 @Composable 作用域，
    // 写在 item/forEach 内会随每次重组重跑 groupBy / maxOfOrNull（数十条化验 × 每次重组）
    val grouped = remember(labs) { labs.groupBy { it.date } }
    val latestDate = remember(labs) { labs.maxOfOrNull { it.date } }
    // C3：同一项目存在多个单位（跨院 / 换设备）时，列表顶部给一条「数值不可直接比较」的提示
    val mixedUnits = remember(labs) { LabUnits.mixedUnitTests(labs) }
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
            // C3：跨院多单位提示放列表顶部——先声明"不可比"，再看下面的分组数值
            if (mixedUnits.isNotEmpty()) {
                item(key = "lab-unit-hint") {
                    Column(modifier = Modifier.padding(top = Spacing.xs)) {
                        StatusChip(
                            text = stringResource(R.string.lab_unit_group_hint),
                            tone = StatusTone.Warning,
                            icon = Icons.Rounded.WarningAmber,
                        )
                        DisclaimerNote()
                    }
                }
            }
            item {
                Button(
                    onClick = onImport,
                    modifier = Modifier.fillMaxWidth().heightIn(min = Size.touchMin),
                ) { Text(stringResource(R.string.lab_ai_import_title)) }
            }
            // v1.0.87（批次 13）：只在"窗口装不下全部"时提示——两个数字（总数 / 本页列出）
            // 不同时必须说清哪个是哪个，否则会被当成又一处"数字对不上"
            if (totalCount > labs.size) {
                item(key = "lab-window-hint") {
                    Text(
                        stringResource(R.string.lab_window_hint, totalCount, labs.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // Y1：日期分组折叠——历史数据多时页面不再被全展开的旧日期撑长；
            // 默认展开规则贴合复诊沟通导向：有异常的日期或最近一次化验展开，其余收起
            grouped.forEach { (date, rows) ->
                item(key = "lab-$date") {
                    LabDateGroup(
                        date = date, rows = rows, isLatest = date == latestDate,
                        onEditLab = onEditLab, onLinkDate = onLinkDate, onAttachDate = onAttachDate,
                    )
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

/**
 * 化验列表里「一个日期分组」的卡片（从 [LabsList] 抽出来：列表函数本身已接近 detekt 的
 * `LongMethod` 上限，而这一组的折叠状态、归属胶囊、三个动作都是独立可读的一件事）。
 *
 * item key 稳定 + `rememberSaveable`：翻页加载更早记录、滚动回收、旋转屏均保持折叠状态。
 *
 * v1.0.90（批次 15）：动作行由 `Row` 改 `FlowRow`——该行是「归属胶囊 + 归属复诊记录 + 附件归档」，
 * 最坏组合（已归属时）固有宽度 ≈94+8+114+8+84 = **308dp**，而卡片内宽：360dp 屏 296dp、
 * 320dp 屏仅 256dp。`Row` 只会把末位「附件归档」挤到 6dp（文字逐字竖排、不可见也不可点），
 * `FlowRow` 则把放不下的那一项整体折到下一行，每项都保住自己的固有宽度。
 * 折行只发生在窄屏/大字号下，宽屏观感与原来逐像素一致（同一行、同一间距）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LabDateGroup(
    date: String,
    rows: List<LabResult>,
    isLatest: Boolean,
    onEditLab: (LabResult) -> Unit,
    onLinkDate: (String) -> Unit,
    onAttachDate: (String) -> Unit,
) {
    val abnormalCount = rows.count { it.isAbnormal() }
    // 整组化验共用一条归属：同一天的一次抽血属于同一次复诊，逐项设置只会变成负担
    val linkedId = rows.firstOrNull { it.checkupId != null }?.checkupId
    var expanded by rememberSaveable { mutableStateOf(abnormalCount > 0 || isLatest) }
    SectionCard(
        title = date,
        subtitle = if (abnormalCount > 0) stringResource(R.string.lab_count_abnormal, rows.size, abnormalCount)
        else stringResource(R.string.lab_count_plain, rows.size),
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
        // 归属/附件动作放在折叠开关之外：折叠状态下也要能直接归档，
        // 否则"这天有没有归属"得先展开才能查、才能改
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            if (linkedId != null) {
                // 只有"已归属"才给胶囊：未归属是常态，不额外占用视觉噪音
                StatusChip(text = stringResource(R.string.attach_link_title), tone = StatusTone.Success)
            }
            TextButton(
                onClick = { onLinkDate(date) },
                modifier = Modifier.heightIn(min = Size.touchMin),
            ) { Text(stringResource(R.string.lab_link_action)) }
            TextButton(
                onClick = { onAttachDate(date) },
                modifier = Modifier.heightIn(min = Size.touchMin),
            ) { Text(stringResource(R.string.attach_title)) }
        }
        if (expanded) LabGroup(rows, onEdit = onEditLab)
    }
}

// ===== 影像列表（MRI/CT/X线，支持 AI 导入） =====
/**
 * @param onAttach 归档该条影像的附件（报告 PDF / 片子照片）
 * @param onLink   把该条影像归属到某条复诊记录
 * @param onEdit   v1.0.80（批次 6）修改该条影像（表单内可删除）——影像此前只能导入、不能改一个字
 */
@Composable
internal fun ImagingList(
    records: List<ImagingRecord>,
    onImport: () -> Unit,
    onView: (ImagingRecord) -> Unit,
    onAttach: (ImagingRecord) -> Unit,
    onLink: (ImagingRecord) -> Unit,
    onEdit: (ImagingRecord) -> Unit,
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
                ImagingRow(rec = rec, onView = onView, onEdit = onEdit, onLink = onLink, onAttach = onAttach)
            }
            item { Spacer(Modifier.height(Spacing.xxl)) }
        }
    }
}

/**
 * 单条影像卡片（从 [ImagingList] 抽出来：列表函数本身要控制长度，而这一张卡片的排版
 * ——日期 / 类型 / 部位 / 医院 / 结论摘要 / 三个动作——是独立可读的一件事）。
 *
 * v1.0.90（批次 15）：动作行由 `Row` 改 `FlowRow`——该行是「归属胶囊 + 编辑 + 归属复诊记录 +
 * 附件归档」，最坏组合（导入影像后归属过复诊记录）固有宽度 ≈94+58+114+84+3×8 = **374dp**，
 * 而卡片内宽：360dp 屏 296dp、320dp 屏 256dp。`Row` 按序测量，末位「附件归档」只能拿到
 * 6dp（文字逐字竖排 → 不可见且不可点，远低于 48dp 触达区）；`FlowRow` 把放不下的项整体
 * 折到下一行，每项保住固有宽度与 `Size.touchMin` 高度。
 *
 * 为什么用 `FlowRow` 而不是把「归属」胶囊挪出该行：胶囊是**状态**、三个按钮是**动作**，
 * 拆成两行要额外占一行高度、且「已归属」这个状态与「改归属」的入口被拉开；`FlowRow` 只在
 * 真的放不下时才折行，宽屏观感与原来逐像素一致（同一行、同一间距）。本仓库已有同一成例
 * （`WellnessScreen` 的补剂动作行、`MedsScreen` 的注射部位组、`CheckupForms` 的单选组）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ImagingRow(
    rec: ImagingRecord,
    onView: (ImagingRecord) -> Unit,
    onEdit: (ImagingRecord) -> Unit,
    onLink: (ImagingRecord) -> Unit,
    onAttach: (ImagingRecord) -> Unit,
) {
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
                    stringResource(
                        R.string.checkup_conclusion_line,
                        c.lineSequence().firstOrNull { it.isNotBlank() } ?: "",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                )
            }
            // 归属/附件排在结论之后：先读结论（这才是这张片子的价值），再决定它算哪次复诊。
            // 外层 Surface 有点击（打开详情），Compose 里子节点优先消费点击，按钮不会被吞。
            // v1.0.90（批次 15）：FlowRow 而非 Row——见上方 KDoc 的宽度推算（374 > 296）。
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                if (rec.checkupId != null) {
                    // 只有"已归属"才给胶囊：未归属是常态，不额外占用视觉噪音
                    StatusChip(text = stringResource(R.string.attach_link_title), tone = StatusTone.Success)
                }
                // v1.0.80（批次 6）：修改入口与归属/附件同排——「这条记错了」是最常见的诉求
                TextButton(
                    onClick = { onEdit(rec) },
                    modifier = Modifier.heightIn(min = Size.touchMin),
                ) { Text(stringResource(R.string.common_edit)) }
                TextButton(
                    onClick = { onLink(rec) },
                    modifier = Modifier.heightIn(min = Size.touchMin),
                ) { Text(stringResource(R.string.imaging_link_action)) }
                TextButton(
                    onClick = { onAttach(rec) },
                    modifier = Modifier.heightIn(min = Size.touchMin),
                ) { Text(stringResource(R.string.attach_title)) }
            }
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
    val template = stringResource(if (kind == ImportKind.LAB) ImportTemplates.LAB else ImportTemplates.IMAGING)
    var pasted by remember { mutableStateOf("") }
    var copied by remember { mutableStateOf(false) }
    var labImport by remember { mutableStateOf<LabImport?>(null) }
    var imagingImport by remember { mutableStateOf<ImagingImport?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val labParseEmpty = stringResource(R.string.lab_parse_empty)
    val imagingParseEmpty = stringResource(R.string.imaging_parse_empty)
    // v1.2.6（i18n）：解析兜底文案与多部位分隔符在 Composable 作用域取好，再传给纯函数的 parser。
    val imagingBodyPartUnset = stringResource(R.string.ui_imaging_bodypart_unset)
    val listSeparator = stringResource(R.string.ui_list_separator)

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
            val parsed = ReportImportParser.parseImaging(
                pasted,
                imagingBodyPartUnset,
                listSeparator,
            )
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
                        append(
                            stringResource(
                                R.string.ui_checkup_lab_date_prefix,
                                imp.date ?: stringResource(R.string.symptom_unrecognized_today),
                            ),
                        )
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

// ===== 添加 / 修改化验指标 =====
// v1.0.80（批次 6）：原先这个文件里私有的 AddLabSheet 已由 CheckupLabForms.kt 的
// LabResultFormSheet 取代——新增与修改共用同一张表单（回填原值、沿用主键、表单内可删除），
// 分成两份实现必然漂移（典型：改了新增的校验忘了改编辑的）。

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

