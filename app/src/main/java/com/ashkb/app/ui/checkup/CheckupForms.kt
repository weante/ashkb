package com.ashkb.app.ui.checkup

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource

import com.ashkb.app.R
import com.ashkb.app.data.entity.CheckupItem
import com.ashkb.app.data.entity.CheckupRecord
import com.ashkb.app.data.entity.CheckupType
import com.ashkb.app.data.entity.DoctorConfirm
import com.ashkb.app.data.entity.VaccineRecord
import com.ashkb.app.data.entity.VaccineType
import com.ashkb.app.data.repo.nowIso
import com.ashkb.app.ui.components.DateFieldRules
import com.ashkb.app.ui.components.DateTextField
import com.ashkb.app.ui.components.DestructiveAction
import com.ashkb.app.ui.theme.Size
import com.ashkb.app.ui.theme.Spacing
import java.time.LocalDate

// ===== 复诊项目表单（多字段，ModalBottomSheet） =====
/**
 * v1.0.80（批次 6）：[existing] 非空 = **编辑**（回填原值、沿用原主键与创建时间）。
 *
 * 回填而不是「再建一条」是硬要求：复诊项目是排程周期的依据（`cycleDays`），
 * 多出一条同名项目会让准备清单里出现两份同样的待办，而用户以为自己只是改了个周期。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun CheckupItemFormSheet(
    existing: CheckupItem? = null,
    onSave: (CheckupItem) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember(existing?.id) { mutableStateOf(existing?.name ?: "") }
    // 新增时的默认档与旧实现一致（LAB）——fromKey(null) 会落到 OTHER，那是为脏数据准备的兜底，不该当成默认值
    var type by remember(existing?.id) { mutableStateOf(existing?.let { CheckupType.fromKey(it.checkType) } ?: CheckupType.LAB) }
    var cycleDays by remember(existing?.id) { mutableStateOf(existing?.cycleDays?.toString() ?: "") }
    var notes by remember(existing?.id) { mutableStateOf(existing?.notes ?: "") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        SheetColumn {
            Text(
                stringResource(if (existing == null) R.string.checkup_add_item_button else R.string.checkup_edit_item_title),
                style = MaterialTheme.typography.titleLarge,
            )
            OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.checkup_item_name)) }, singleLine = true)
            ChipGroupLabel(stringResource(R.string.common_type))
            ChipGroup(
                options = CheckupType.entries.map { it.name to it.label },
                selected = type.name,
                onSelect = { type = CheckupType.valueOf(it) },
            )
            OutlinedTextField(cycleDays, { cycleDays = it },
                label = { Text(stringResource(R.string.med_cycle_days_field)) }, singleLine = true)
            OutlinedTextField(notes, { notes = it }, label = { Text(stringResource(R.string.common_notes)) })
            SheetSaveButton(
                text = stringResource(R.string.common_save),
                enabled = name.isNotBlank(),
                onClick = {
                    if (name.isNotBlank()) {
                        onSave(
                            CheckupItem(
                                id = existing?.id ?: "",
                                name = name.trim(), checkType = type.name,
                                cycleDays = cycleDays.toIntOrNull(),
                                notes = notes.ifBlank { null },
                                createdAt = existing?.createdAt ?: nowIso(),
                                updatedAt = nowIso(),
                            )
                        )
                    }
                },
            )
        }
    }
}

// ===== 复诊记录表单（多字段，ModalBottomSheet） =====
/**
 * v1.0.80（批次 6）：[existing] 非空 = 编辑（回填原值并沿用主键，避免「改一次多一条」）。
 *
 * v1.0.87（批次 13）：[presetItemName] 只在**新增**时充当项目名初值——从「记录」tab 的项目筛选
 * 空态点「记录复诊」进来时，项目名已填好那个项目，补录的这条才会落回当前筛选下
 * （记录与项目的关联靠名字快照，不预填就是白补一条）。编辑时一律以原记录为准，它被忽略。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun CheckupRecordFormSheet(
    items: List<CheckupItem>,
    existing: CheckupRecord? = null,
    presetItemName: String? = null,
    onSave: (CheckupRecord) -> Unit,
    onDismiss: () -> Unit,
) {
    // 表单初值先算好（编辑回填 / 新增给默认档），字段状态以它为 key——
    // 详情见 CheckupRecordDraft.of 的注释（圈复杂度与「一处实现」两个理由）
    val draft = CheckupRecordDraft.of(existing, presetItemName)
    var itemName by remember(draft) { mutableStateOf(draft.itemName) }
    var checkType by remember(draft) { mutableStateOf(draft.checkType) }
    var date by remember(draft) { mutableStateOf(draft.isoDate) }
    var hospital by remember(draft) { mutableStateOf(draft.hospital) }
    var doctor by remember(draft) { mutableStateOf(draft.doctor) }
    var nextDate by remember(draft) { mutableStateOf(draft.nextDate) }
    var conclusion by remember(draft) { mutableStateOf(draft.conclusion) }
    var notes by remember(draft) { mutableStateOf(draft.notes) }
    // v1.0.78（批次 4 收尾）：日期此前是自由文本且**零校验**——`2026-13-45` 会被原样写进 date 列。
    // 这里与输入框提示共用 DateFieldRules 同一套判定，保存按钮与提示不会互相矛盾。
    val dateOk = DateFieldRules.requiredOk(date)
    val nextDateOk = DateFieldRules.optionalOk(nextDate)

    ModalBottomSheet(onDismissRequest = onDismiss) {
        SheetColumn {
            Text(
                stringResource(if (existing == null) R.string.checkup_record_visit else R.string.checkup_edit_record_title),
                style = MaterialTheme.typography.titleLarge,
            )
            OutlinedTextField(itemName, { itemName = it },
                label = { Text(stringResource(R.string.checkup_item_name)) }, singleLine = true)
            ChipGroupLabel(stringResource(R.string.common_type))
            ChipGroup(
                options = CheckupType.entries.map { it.name to it.label },
                selected = checkType.name,
                onSelect = { checkType = CheckupType.valueOf(it) },
            )
            DateTextField(value = date, onValueChange = { date = it }, label = stringResource(R.string.common_date))
            OutlinedTextField(hospital, { hospital = it },
                label = { Text(stringResource(R.string.common_hospital)) }, singleLine = true)
            OutlinedTextField(doctor, { doctor = it },
                label = { Text(stringResource(R.string.checkup_doctor_short)) }, singleLine = true)
            DateTextField(
                value = nextDate, onValueChange = { nextDate = it },
                label = stringResource(R.string.checkup_next_date_field), required = false,
            )
            OutlinedTextField(conclusion, { conclusion = it },
                label = { Text(stringResource(R.string.imaging_conclusion)) })
            OutlinedTextField(notes, { notes = it }, label = { Text(stringResource(R.string.common_notes)) })
            SheetSaveButton(
                text = stringResource(R.string.common_save),
                enabled = itemName.isNotBlank() && dateOk && nextDateOk,
                onClick = {
                    // 双保险：按钮已按同一判定置灰，这里再拦一次——非法日期绝不落库
                    val isoDate = DateFieldRules.toIsoOrNull(date)
                    if (itemName.isNotBlank() && isoDate != null) {
                        onSave(
                            CheckupRecordDraft(
                                itemName, checkType, isoDate, hospital, doctor, nextDate, conclusion, notes,
                            ).toRecord(existing)
                        )
                    }
                },
            )
        }
    }
}

/**
 * 由表单字段拼出要落库的复诊记录（新增 / 编辑共用）。
 *
 * 抽成非 Composable 顶层函数（而不是写在 `onClick` 里）有两个原因：
 * ① 「编辑沿用原主键与记录时间」这条不变量只有一处实现；
 * ② 这些 `?:` 兜底会给 Composable 叠加圈复杂度，撞上 detekt 的 `CyclomaticComplexMethod`
 *    阈值（Compose 的声明式分支本就多）。
 *
 * 字段先收进 [CheckupRecordDraft]：九个散参数既过不了长参数列表检查，调用点也读不出谁是谁。
 */
private data class CheckupRecordDraft(
    val itemName: String,
    val checkType: CheckupType,
    val isoDate: String,
    val hospital: String,
    val doctor: String,
    val nextDate: String,
    val conclusion: String,
    val notes: String,
) {
    companion object {
        /**
         * 表单初值（新增时给默认档，编辑时回填原值）。
         *
         * 为什么把这一串 `?:` 收进工厂：它们直接写在 Composable 里会一条条叠加圈复杂度
         * （每个安全调用与 Elvis 各算一个分支，九个字段就顶到 detekt 的 `CyclomaticComplexMethod`
         * 阈值），而 Composable 里本就还有标题、校验、保存分支。
         *
         * v1.0.87（批次 13）：[presetItemName] 只在新增时兜底项目名（项目筛选空态进来的预填）；
         * 编辑时原记录的项目名优先，预填不影响已有记录。
         */
        fun of(existing: CheckupRecord?, presetItemName: String? = null): CheckupRecordDraft = CheckupRecordDraft(
            itemName = existing?.itemName ?: presetItemName.orEmpty(),
            // 新增时的默认档与旧实现一致（CONSULT）；fromKey(null) 的 OTHER 是给脏数据的兜底
            checkType = existing?.let { CheckupType.fromKey(it.checkType) } ?: CheckupType.CONSULT,
            isoDate = existing?.date ?: LocalDate.now().toString(),
            hospital = existing?.hospital ?: "",
            doctor = existing?.doctor ?: "",
            nextDate = existing?.nextDate ?: "",
            conclusion = existing?.conclusion ?: "",
            notes = existing?.notes ?: "",
        )
    }

    fun toRecord(existing: CheckupRecord?): CheckupRecord = CheckupRecord(
        id = existing?.id ?: "", date = isoDate,
        recordedAt = existing?.recordedAt ?: nowIso(),
        itemName = itemName.trim(), checkType = checkType.name,
        hospital = hospital.ifBlank { null },
        doctor = doctor.ifBlank { null },
        nextDate = DateFieldRules.toIsoOrNull(nextDate),
        conclusion = conclusion.ifBlank { null },
        notes = notes.ifBlank { null },
    )
}

// ===== 疫苗表单（多字段，ModalBottomSheet） =====
/**
 * v1.0.80（批次 6）：[existing] 非空 = 编辑（回填原值、沿用主键）。
 *
 * 编辑同样要回填 `doctorConfirm`：把「活疫苗 + 待确认」改成「医生同意」是**撤销安全警报**的正规路径
 * （见 `HealthRepository.saveVaccineRecord` 的重算），若编辑时默认回落到 PENDING，
 * 用户每次改个剂次都会把警报重新点着。
 *
 * @param onDelete 非空时才渲染删除入口（只有已存在的行才谈得上删除）
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun VaccineFormSheet(
    existing: VaccineRecord? = null,
    onSave: (VaccineRecord) -> Unit,
    onDelete: ((VaccineRecord) -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    val draft = VaccineDraft.of(existing)
    var name by remember(draft) { mutableStateOf(draft.name) }
    var type by remember(draft) { mutableStateOf(draft.type) }
    var date by remember(draft) { mutableStateOf(draft.isoDate) }
    var dose by remember(draft) { mutableStateOf(draft.dose) }
    var hospital by remember(draft) { mutableStateOf(draft.hospital) }
    // v1.0.73：默认取最保守档「待确认」——此前默认 CONFIRMED，患者登记活疫苗时只要不动这个 chip，
    // 就会被静默记成「医生已确认」，活疫苗 × 生物制剂的安全警报永不触发（安全默认值反转）。
    var confirm by remember(draft) { mutableStateOf(draft.confirm) }
    var nextDue by remember(draft) { mutableStateOf(draft.nextDue) }
    var notes by remember(draft) { mutableStateOf(draft.notes) }
    // v1.0.78（批次 4 收尾）：同复诊表单——接种日期必填、加强日期可留空，非法一律不许保存
    val dateOk = DateFieldRules.requiredOk(date)
    val nextDueOk = DateFieldRules.optionalOk(nextDue)

    ModalBottomSheet(onDismissRequest = onDismiss) {
        SheetColumn {
            Text(
                stringResource(if (existing == null) R.string.vaccine_record_action else R.string.vaccine_edit_title),
                style = MaterialTheme.typography.titleLarge,
            )
            OutlinedTextField(name, { name = it },
                label = { Text(stringResource(R.string.vaccine_name_field)) }, singleLine = true)
            ChipGroupLabel(stringResource(R.string.common_type))
            ChipGroup(
                options = VaccineType.entries.map { it.name to it.label },
                selected = type.name,
                onSelect = { type = VaccineType.valueOf(it) },
            )
            DateTextField(value = date, onValueChange = { date = it }, label = stringResource(R.string.vaccine_date))
            OutlinedTextField(dose, { dose = it },
                label = { Text(stringResource(R.string.vaccine_dose_field)) }, singleLine = true)
            OutlinedTextField(hospital, { hospital = it },
                label = { Text(stringResource(R.string.vaccine_hospital)) }, singleLine = true)
            ChipGroupLabel(stringResource(R.string.vaccine_doctor_confirm))
            ChipGroup(
                options = DoctorConfirm.entries.map { it.name to it.label },
                selected = confirm.name,
                onSelect = { confirm = DoctorConfirm.valueOf(it) },
            )
            DateTextField(
                value = nextDue, onValueChange = { nextDue = it },
                label = stringResource(R.string.vaccine_booster_date), required = false,
            )
            OutlinedTextField(notes, { notes = it }, label = { Text(stringResource(R.string.exercise_notes_reaction)) })
            SheetSaveButton(
                text = stringResource(R.string.common_save),
                enabled = name.isNotBlank() && dateOk && nextDueOk,
                onClick = {
                    // 双保险：非法日期绝不落库（按钮已按同一判定置灰）
                    val isoDate = DateFieldRules.toIsoOrNull(date)
                    if (name.isNotBlank() && isoDate != null) {
                        onSave(
                            VaccineDraft(name, type, isoDate, dose, hospital, confirm, nextDue, notes)
                                .toRecord(existing)
                        )
                    }
                    onDismiss()
                },
            )
            // 删除入口与「保存」同屏（与化验 / 影像 / 体征的既有做法一致）
            if (existing != null && onDelete != null) {
                DestructiveAction(
                    label = stringResource(R.string.common_delete),
                    confirmTitle = stringResource(R.string.vaccine_delete_confirm, existing.vaccineName),
                    confirmBody = stringResource(R.string.vaccine_delete_note) + "\n" +
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
 * 由表单字段拼出要落库的疫苗记录（新增 / 编辑共用，理由同 [CheckupRecordDraft]）。
 *
 * 编辑时**必须回填 `doctorConfirm`**：把「活疫苗 + 待确认」改成「医生同意」是撤销安全警报的
 * 正规路径，若这里默认回落到 PENDING，用户每次改个剂次都会把 high 级警报重新点着。
 */
private data class VaccineDraft(
    val name: String,
    val type: VaccineType,
    val isoDate: String,
    val dose: String,
    val hospital: String,
    val confirm: DoctorConfirm,
    val nextDue: String,
    val notes: String,
) {
    companion object {
        /** 表单初值（理由同 [CheckupRecordDraft.of]）。 */
        fun of(existing: VaccineRecord?): VaccineDraft = VaccineDraft(
            name = existing?.vaccineName ?: "",
            // 新增默认「灭活 / 重组」与旧实现一致；fromKey(null) 的 UNKNOWN 是给脏数据的保守兜底
            type = existing?.let { VaccineType.fromKey(it.vaccineType) } ?: VaccineType.INACTIVATED,
            isoDate = existing?.date ?: LocalDate.now().toString(),
            dose = existing?.dose ?: "",
            hospital = existing?.hospital ?: "",
            // v1.0.73：默认必须是「待确认」而非「医生同意」（医学上「未表态」不等于「已同意」），
            // 编辑时则回填用户当初选的那一档——否则改个剂次就会把安全警报重新点着。
            confirm = existing?.let { DoctorConfirm.fromKey(it.doctorConfirm) } ?: DoctorConfirm.PENDING,
            nextDue = existing?.nextDueDate ?: "",
            notes = existing?.notes ?: "",
        )
    }

    fun toRecord(existing: VaccineRecord?): VaccineRecord = VaccineRecord(
        id = existing?.id ?: "", date = isoDate,
        recordedAt = existing?.recordedAt ?: nowIso(),
        vaccineName = name.trim(), vaccineType = type.name,
        dose = dose.ifBlank { null },
        hospital = hospital.ifBlank { null },
        doctorConfirm = confirm.name,
        nextDueDate = DateFieldRules.toIsoOrNull(nextDue),
        notes = notes.ifBlank { null },
    )
}

@Composable
internal fun ChipGroupLabel(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall)
}

/** 单选 chip 组：FlowRow 自动换行防截断，触摸目标 ≥48dp。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ChipGroup(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        options.forEach { (key, label) ->
            FilterChip(
                selected = selected == key,
                onClick = { onSelect(key) },
                label = { Text(label) },
                modifier = Modifier.heightIn(min = Size.touchMin),
            )
        }
    }
}
