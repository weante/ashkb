package com.ashkb.app.ui.checkup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import com.ashkb.app.ui.components.AshkbTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import java.util.Locale

import com.ashkb.app.R
import com.ashkb.app.data.entity.ImagingRecord
import com.ashkb.app.data.entity.LabResult
import com.ashkb.app.data.repo.nowIso
import com.ashkb.app.ui.components.DateFieldRules
import com.ashkb.app.ui.components.DateTextField
import com.ashkb.app.ui.components.DestructiveAction
import com.ashkb.app.ui.theme.Spacing

/**
 * v1.0.80（批次 6）：化验结果 / 影像记录的**编辑表单**（ModalBottomSheet，与全仓约定一致）。
 *
 * 与 `CheckupForms.kt` 分文件的原因：那个文件已接近 500 行的单文件上限（本仓约定），
 * 而化验 / 影像这两个表单各自带着「编辑时哪些字段不可动」的说明，独立成文更好读。
 */

/** 化验值回填文本：去掉无意义的小数尾零（`25.0` → `25`），否则用户每改一次都要先删掉 `.0`。 */
private fun editText(v: Double?): String =
    v?.let { String.format(Locale.US, "%.4f", it).trimEnd('0').trimEnd('.') } ?: ""

/**
 * v1.2.11（缺陷工单 A07）：数字字段的即时错误说明。
 *
 * 只在该字段**确实填错了**时才返回一行（留空合法 = 未填），与 `DateTextField` 的口径一致：
 * 表单初值都来自库里既有值，只有用户自己改坏才会非法，此时立刻反馈最不容易让人困惑。
 */
@Composable
private fun numberError(raw: String): (@Composable () -> Unit)? =
    if (LabDraftRules.invalidNumber(raw)) {
        { Text(stringResource(R.string.lab_field_number_invalid)) }
    } else null

/**
 * 化验结果表单：新增（[existing] 为 null）与修改共用。
 *
 * @param date 该次化验的日期（新增时取所属复诊记录的日期；修改时取原记录的日期）
 * @param onDelete 非空时才渲染删除入口——只有「已存在的行」才谈得上删除
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LabResultFormSheet(
    existing: LabResult?,
    date: String,
    checkupId: String?,
    onSave: (LabResult) -> Unit,
    onDelete: ((LabResult) -> Unit)?,
    onDismiss: () -> Unit,
) {
    var testName by remember(existing?.id) { mutableStateOf(existing?.testName ?: "") }
    var value by remember(existing?.id) { mutableStateOf(editText(existing?.value)) }
    var valueText by remember(existing?.id) { mutableStateOf(existing?.valueText ?: "") }
    var unit by remember(existing?.id) { mutableStateOf(existing?.unit ?: "") }
    var refLow by remember(existing?.id) { mutableStateOf(editText(existing?.refLow)) }
    var refHigh by remember(existing?.id) { mutableStateOf(editText(existing?.refHigh)) }
    // v1.2.11（缺陷工单 A07）：反向区间是「两个字段一起错」，判据抽在 LabDraftRules 里，
    // 红框、下方说明、保存按钮的启用条件共用同一组判据——否则会出现「框红了却还能存」。
    val rangeReversed = LabDraftRules.reversedRange(refLow, refHigh)
    val canSave = LabDraftRules.canSave(testName, value, refLow, refHigh)

    ModalBottomSheet(onDismissRequest = onDismiss) {
        SheetColumn {
            Text(
                stringResource(
                    if (existing == null) R.string.lab_add_indicator_action else R.string.lab_edit_indicator_action
                ),
                style = MaterialTheme.typography.titleLarge,
            )
            // 日期只读展示：化验按「日期」整组归属复诊记录（linkLabsByDate），
            // 悄悄改掉日期会把这一行挪到另一次就诊的组里，用户不会意识到自己改了归属。
            Text(
                stringResource(R.string.lab_edit_date_hint, date),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AshkbTextField(testName, { testName = it }, label = { Text(stringResource(R.string.lab_indicator_name)) }, singleLine = true)
            AshkbTextField(
                value, { value = it }, label = { Text(stringResource(R.string.lab_value)) }, singleLine = true,
                isError = LabDraftRules.invalidNumber(value),
                supportingText = numberError(value),
            )
            AshkbTextField(valueText, { valueText = it },
                label = { Text(stringResource(R.string.lab_value_text_field)) }, singleLine = true)
            AshkbTextField(unit, { unit = it }, label = { Text(stringResource(R.string.common_unit)) }, singleLine = true)
            LabReferenceRangeRow(
                refLow = refLow,
                refHigh = refHigh,
                onRefLowChange = { refLow = it },
                onRefHighChange = { refHigh = it },
                reversed = rangeReversed,
            )
            // AI 原始标记只读留档：它**不参与**本地判读，编辑数值时也不该被改写
            // （两列并列展示的意义就在这里，见 LabResult.aiAbnormal 与 v1.0.78 的裁决）。
            existing?.aiAbnormal?.let { ai ->
                Text(
                    stringResource(R.string.lab_edit_ai_mark_note, labMarkLabel(ai)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SheetSaveButton(
                text = stringResource(R.string.common_save),
                enabled = canSave,
                onClick = {
                    if (!canSave) return@SheetSaveButton
                    onSave(
                        LabDraft(testName, value, valueText, unit, refLow, refHigh)
                            .toRecord(existing, date, checkupId)
                    )
                    onDismiss()
                },
            )
            // 删除入口与「保存」同屏（与体征 / 体重 / 补剂的既有做法一致）：
            // 用户点开这一行本就是想「改掉它」，删除不该再藏到别的页面。
            if (existing != null && onDelete != null) {
                DestructiveAction(
                    label = stringResource(R.string.common_delete),
                    confirmTitle = stringResource(R.string.lab_delete_confirm, existing.testName),
                    confirmBody = stringResource(R.string.lab_delete_note) + "\n" +
                        stringResource(R.string.common_delete_irreversible),
                    onConfirm = {
                        onDelete(existing)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * v1.2.11（缺陷工单 A07）：参考范围两格 + 反向区间提示。
 *
 * 抽出来一是为了让 `LabResultFormSheet` 回到 detekt 的 80 行以内（不是放宽阈值），
 * 二是让「两格共用一个反向判据」这件事在代码里只有一个落点——红框与下方提示
 * 必须同时出现，分开写迟早漂移。
 */
@Composable
private fun LabReferenceRangeRow(
    refLow: String,
    refHigh: String,
    onRefLowChange: (String) -> Unit,
    onRefHighChange: (String) -> Unit,
    reversed: Boolean,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        AshkbTextField(refLow, onRefLowChange,
            label = { Text(stringResource(R.string.lab_ref_lower)) }, singleLine = true,
            isError = LabDraftRules.invalidNumber(refLow) || reversed,
            supportingText = numberError(refLow),
            modifier = Modifier.weight(1f))
        AshkbTextField(refHigh, onRefHighChange,
            label = { Text(stringResource(R.string.lab_ref_upper)) }, singleLine = true,
            isError = LabDraftRules.invalidNumber(refHigh) || reversed,
            supportingText = numberError(refHigh),
            modifier = Modifier.weight(1f))
    }
    // 反向区间是「两个字段一起错」，提示放在这一行下方，不挂在某一个框上。
    if (reversed) {
        Text(
            stringResource(R.string.lab_ref_range_reversed),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

/**
 * 影像记录表单：只用于**修改**（影像没有手工新增入口，导入走 AI 解析模板）。
 *
 * 可改项 = 检查日期 /  modality / 部位 / 医院 / 检查所见 / 结论 / 备注；
 * `checkupId`（归属哪次复诊）不在表单里——它由列表上的「归属」入口单独设置，
 * 两个入口各管一件事，避免同一个字段在两处都能改出不一致。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ImagingFormSheet(
    existing: ImagingRecord,
    onSave: (ImagingRecord) -> Unit,
    onDelete: ((ImagingRecord) -> Unit)?,
    onDismiss: () -> Unit,
) {
    var date by remember(existing.id) { mutableStateOf(existing.examDate) }
    var modality by remember(existing.id) { mutableStateOf(existing.modality) }
    var bodyPart by remember(existing.id) { mutableStateOf(existing.bodyPart) }
    var hospital by remember(existing.id) { mutableStateOf(existing.hospital ?: "") }
    var findings by remember(existing.id) { mutableStateOf(existing.findings ?: "") }
    var conclusion by remember(existing.id) { mutableStateOf(existing.conclusion ?: "") }
    var notes by remember(existing.id) { mutableStateOf(existing.notes ?: "") }
    val dateOk = DateFieldRules.requiredOk(date)

    ModalBottomSheet(onDismissRequest = onDismiss) {
        SheetColumn {
            Text(stringResource(R.string.imaging_edit_title), style = MaterialTheme.typography.titleLarge)
            DateTextField(value = date, onValueChange = { date = it }, label = stringResource(R.string.common_date))
            ChipGroupLabel(stringResource(R.string.common_type))
            ChipGroup(
                options = MODALITIES.map { it to ImagingRecord.modalityLabel(it) },
                selected = modality,
                onSelect = { modality = it },
            )
            AshkbTextField(bodyPart, { bodyPart = it },
                label = { Text(stringResource(R.string.imaging_body_part_field)) }, singleLine = true)
            AshkbTextField(hospital, { hospital = it },
                label = { Text(stringResource(R.string.imaging_hospital_label)) }, singleLine = true)
            AshkbTextField(findings, { findings = it },
                label = { Text(stringResource(R.string.imaging_findings)) })
            AshkbTextField(conclusion, { conclusion = it },
                label = { Text(stringResource(R.string.imaging_conclusion)) })
            AshkbTextField(notes, { notes = it }, label = { Text(stringResource(R.string.common_notes)) })
            SheetSaveButton(
                text = stringResource(R.string.common_save),
                enabled = bodyPart.isNotBlank() && dateOk,
                onClick = {
                    val isoDate = DateFieldRules.toIsoOrNull(date)
                    if (bodyPart.isNotBlank() && isoDate != null) {
                        onSave(
                            existing.copy(
                                examDate = isoDate,
                                modality = modality,
                                bodyPart = bodyPart.trim(),
                                hospital = hospital.ifBlank { null },
                                findings = findings.ifBlank { null },
                                conclusion = conclusion.ifBlank { null },
                                notes = notes.ifBlank { null },
                            )
                        )
                        onDismiss()
                    }
                },
            )
            if (onDelete != null) {
                DestructiveAction(
                    label = stringResource(R.string.common_delete),
                    confirmTitle = stringResource(
                        R.string.imaging_delete_confirm,
                        "${ImagingRecord.modalityLabel(existing.modality)} · ${existing.bodyPart}",
                    ),
                    confirmBody = stringResource(R.string.imaging_delete_note) + "\n" +
                        stringResource(R.string.common_delete_irreversible),
                    onConfirm = {
                        onDelete(existing)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** 与 `ImagingRecord.modalityLabel` 对应的原始键（导入解析器只产出这三种）。 */
private val MODALITIES = listOf("MRI", "CT", "XRAY")

/**
 * 化验表单草稿 → 落库行（新增 / 编辑共用）。
 *
 * 三处「刻意为之」必须留在代码里、不能挪进 UI：
 *  · **编辑沿用原主键**（新 id = 新行，用户会看到「改完多了一条」）；
 *  · **`abnormal` 带旧值作兜底**——v1.2.11（缺陷工单 A07）修正：此前这里是「刻意不带」，
 *    理由是「交给 `repo.saveLabResult` 重判」。但 `saveLabResult` 只在**能算出本地判读**时
 *    覆盖，而它算不出判读的条件正是「没有参考范围」（用户把范围清空、或这条本来就只有
 *    AI 标记）。于是编辑一条无范围记录 = 把它的 `abnormal` 静默清成 null，红/黄标记消失，
 *    趋势图与 PDF 里那项异常也随之不见——用户只是改了个数值，却丢了判读结论。
 *    现在把旧值原样带过去：有范围时 `saveLabResult` 照常按新范围覆盖（重判仍优先），
 *    没范围时旧值就是唯一兜底。**这条不再由 UI 决定判读，只是别把已知结论抹掉**；
 *  · `aiAbnormal` 则原样带过来：它是只读留档，不参与判读，也不该被编辑抹掉。
 */
private data class LabDraft(
    val testName: String,
    val value: String,
    val valueText: String,
    val unit: String,
    val refLow: String,
    val refHigh: String,
) {
    fun toRecord(existing: LabResult?, date: String, checkupId: String?): LabResult = LabResult(
        id = existing?.id ?: "",
        date = existing?.date ?: date,
        recordedAt = existing?.recordedAt ?: nowIso(),
        backfill = existing?.backfill ?: false,
        checkupId = existing?.checkupId ?: checkupId,
        testName = testName.trim(),
        value = value.trim().toDoubleOrNull(),
        valueText = valueText.ifBlank { null },
        unit = unit.ifBlank { null },
        refLow = refLow.trim().toDoubleOrNull(),
        refHigh = refHigh.trim().toDoubleOrNull(),
        // v1.2.11（缺陷工单 A06）：手工表单的上下界一律**包含边界**（表单里没有 `<`/`≤` 的输入位），
        // 原样带过 `existing` 的严格标志没有意义——用户在这里改了数值，语义就回到「含边界」。
        // AI 导入路径（ReportImportParser）才区分 `<5` 与 `≤5`，那条路径不经本函数。
        refLowInclusive = true,
        refHighInclusive = true,
        // v1.2.11（缺陷工单 A07）：旧判读原样带过去，别在「本地判不了」时把它抹掉。
        abnormal = existing?.abnormal,
        aiAbnormal = existing?.aiAbnormal,
        notes = existing?.notes,
    )
}
