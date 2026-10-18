package com.ashkb.app.ui.emergency

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import com.ashkb.app.ui.components.AshkbTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.ashkb.app.R
import com.ashkb.app.data.entity.EmergencyEvent
import com.ashkb.app.data.repo.nowIso
import com.ashkb.app.data.entity.EmergencyScene
import com.ashkb.app.ui.theme.Size
import com.ashkb.app.ui.theme.Spacing
import java.time.LocalDate

/**
 * v1.2.0：急诊事件的新增 / 编辑弹层，含两个小组件与落库草稿。
 *
 * ### 为什么搬出来
 * 原先与主屏、联系人表单同处 EmergencyScreen.kt（912 行 ✅ 超过本项目 ~500 行上限 ✅）。
 * 这四块（[EmergencyEventSheet] ✅ [EventToggleRow] ✅ [EmergencyScenePicker] ✅ [EventDraft] ✅）
 * **只依赖 EmergencyEvent / EmergencyScene** ✅ 与主屏**零耦合** ✅
 *
 * ### 顺带还掉一处 detekt 基线豁免
 * [EmergencyEventSheet] 原为 85 行 ✅ 超过 LongMethod 阈值 80 ✅ 因此登记在基线里 ✅
 * 本次把三处多行调用压成单行 ✅ 降到 **73 行** ✅ —— **低于阈值** ✅
 * 于是 config/detekt/baseline.xml 里那条 LongMethod 记录**已删除** ✅
 * **不是换个文件继续豁免 ✅ 而是代码真正达标** ✅
 *
 * ⚠️ 删基线条目后必须真跑一次 detekt ✅ 否则"以为它豁免"会掩盖真实问题 ✅（本项目铁律）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EmergencyEventSheet(
    existing: EmergencyEvent? = null,
    onSave: (EmergencyEvent) -> Unit,
    onDismiss: () -> Unit,
) {
    val draft = EventDraft.of(existing)
    var scene by remember(draft) { mutableStateOf(draft.scene) }
    // v1.2.10（审查 §6.9）：只有用户**自己点过**场景单选，才允许把场景按枚举落库。
    // 否则一条 scene 字段损坏的旧记录，打开看一眼再保存就会被改写成「感染发热」。
    var sceneTouched by remember(draft) { mutableStateOf(false) }
    var date by remember(draft) { mutableStateOf(draft.date) }
    var symptoms by remember(draft) { mutableStateOf(draft.symptoms) }
    var actions by remember(draft) { mutableStateOf(draft.actions) }
    var hospitalVisit by remember(draft) { mutableStateOf(draft.hospitalVisit) }
    var hospitalName by remember(draft) { mutableStateOf(draft.hospitalName) }
    var outcome by remember(draft) { mutableStateOf(draft.outcome) }
    var resolved by remember(draft) { mutableStateOf(draft.resolved) }
    var notes by remember(draft) { mutableStateOf(draft.notes) }
    var attempted by remember { mutableStateOf(false) }
    val dateOk = runCatching { LocalDate.parse(date.trim()) }.getOrNull() != null

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg)
                .padding(bottom = Spacing.xl)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            val titleRes =
                if (existing == null) R.string.emergency_record_event else R.string.emergency_edit_event_title
            Text(stringResource(titleRes), style = MaterialTheme.typography.titleLarge)

            Text(stringResource(R.string.emergency_scenario), style = MaterialTheme.typography.labelLarge)
            UnrecognizedSceneHint(raw = draft.sceneRaw, touched = sceneTouched)
            EmergencyScenePicker(selected = scene, onSelect = { scene = it; sceneTouched = true })

            AshkbTextField(
                date, { date = it }, label = { Text(stringResource(R.string.common_date)) }, singleLine = true,
                isError = attempted && !dateOk,
                supportingText = { if (attempted && !dateOk) Text(stringResource(R.string.common_date_format_hint2)) },
            )
            AshkbTextField(symptoms, { symptoms = it }, label = { Text(stringResource(R.string.symptom_description)) })
            // v1.2.10：这一格曾经**整格消失**。v1.2.2 拆文件时是按行号"目测"截断的（那次提交
            // 的说明自己记了这件事：`git diff --shortstat` 显示删了 912 行而本意只删 167 行，
            // 靠 diff 自己发现并从 git 恢复重做），结果把上面那格「症状描述」复制成了两份、
            // 而「已采取措施」那一格没了。后果不是显示问题：`actions` 变成没有任何输入能写进去的
            // 状态变量，`toEvent` 于是每次都把 `actionsTaken` 写成 null ——
            // 用户填的现场处置（吃了什么药、冷敷、去了急诊）在保存时被静默丢掉，且无任何提示。
            AshkbTextField(
                actions, { actions = it },
                label = { Text(stringResource(R.string.emergency_actions_taken)) },
            )

            EventToggleRow(stringResource(R.string.emergency_seek_care), hospitalVisit) { hospitalVisit = it }
            if (hospitalVisit) {
                AshkbTextField(
                    hospitalName, { hospitalName = it },
                    label = { Text(stringResource(R.string.checkup_hospital_field)) },
                    singleLine = true,
                )
            }

            EventToggleRow(stringResource(R.string.symptom_outcome_recovered), resolved) { resolved = it }
            if (resolved) {
                AshkbTextField(
                    outcome, { outcome = it },
                    label = { Text(stringResource(R.string.symptom_outcome_label)) },
                    singleLine = true,
                )
            }

            AshkbTextField(notes, { notes = it }, label = { Text(stringResource(R.string.common_notes)) })

            Button(
                onClick = {
                    attempted = true
                    if (dateOk) {
                        onSave(
                            EventDraft(
                                scene, date, symptoms, actions, hospitalVisit, hospitalName, outcome, resolved, notes,
                                sceneRaw = draft.sceneRaw,
                                sceneTouched = sceneTouched,
                            ).toEvent(existing)
                        )
                    }
                },
                enabled = dateOk,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Size.touchMin),
            ) { Text(stringResource(R.string.common_save)) }
        }
    }
}

/**
 * v1.2.10（审查 §6.9）：脏值（本应用读不出的场景存库值）必须先说出来再让用户选。
 * 不提示的话单选会停在「感染发热」上，用户以为记录本来就是这样。
 *
 * 抽成独立组件而不是内联 `if`：那个 `if` 的四个条件把 [EmergencyEventSheet] 顶过了
 * detekt 的 LongMethod 阈值（86 > 80），而这里的文案与判据都只服务于这一件事。
 */
@Composable
private fun UnrecognizedSceneHint(raw: String?, touched: Boolean) {
    if (raw == null || touched || EmergencyScene.fromKeyOrNull(raw) != null) return
    Text(
        stringResource(R.string.emergency_scene_unrecognized),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * 勾选行（从 [EmergencyEventSheet] 抽出）。触摸目标 ≥48dp、整行可点（不只是那个方块），
 * 与联系人表单里两个勾选项同款——急诊时手抖，点方块的容错太低。
 */
@Composable
internal fun EventToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Size.touchMin)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Spacer(Modifier.width(Spacing.xs))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

/** 应急场景单选（从 [EmergencyEventSheet] 抽出：表单要控制长度与圈复杂度）。 */
@Composable
internal fun EmergencyScenePicker(selected: EmergencyScene, onSelect: (EmergencyScene) -> Unit) {
    EmergencyScene.entries.forEach { s ->
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = Size.touchMin)
                .selectable(selected = selected == s, role = Role.RadioButton, onClick = { onSelect(s) }),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected == s, onClick = null)
            Spacer(Modifier.width(Spacing.xs))
            Text(stringResource(s.labelRes), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

/**
 * 紧急事件表单草稿 → 落库行（新增 / 编辑共用，理由同 [ContactDraft]）。
 *
 * `resolvedDate` 由「已缓解」勾选驱动：勾上写今天、取消则清空——与新增时同一口径，
 * 避免出现「填了缓解日期却仍显示进行中」这种自相矛盾的行。
 * 编辑时保留原有的 `severity` / `onsetTime`：它们不在表单里，不该被这次保存抹掉。
 */
internal data class EventDraft(
    val scene: EmergencyScene,
    val date: String,
    val symptoms: String,
    val actions: String,
    val hospitalVisit: Boolean,
    val hospitalName: String,
    val outcome: String,
    val resolved: Boolean,
    val notes: String,
    /**
     * 原记录的**原始**场景存库值（新增时为 null）。v1.2.10（审查 §6.9）新增。
     *
     * 存在的唯一理由：`EmergencyScene.fromKey` 是 fail-open 的，一个读不出来的脏值会被静默
     * 当成「感染发热」。若这里不把原值留住，用户打开一条旧记录再按保存，脏值就被**就地改写**
     * 成 `INFECTION_FEVER` 落库——原始信息永久丢失且零提示。
     */
    val sceneRaw: String? = null,
    /** 用户本次是否动过场景单选。没动过 = 不替用户做决定，见 [sceneValue]。 */
    val sceneTouched: Boolean = false,
) {
    companion object {
        /**
         * 表单初值（新增给默认档、编辑回填原值）。
         *
         * 收进工厂而不是写在 Composable 里：九个字段的安全调用 + Elvis 会一条条叠加圈复杂度，
         * 直接顶穿 detekt 的 `CyclomaticComplexMethod` 阈值（表单里本就有勾选 / 条件项的嵌套分支）。
         */
        fun of(existing: EmergencyEvent?): EventDraft = EventDraft(
            // fromKey(null) 落到 INFECTION_FEVER，与新增时的默认场景一致。
            // v1.2.10（§6.9）：这只在**原值为 null**（新增）时才是对的；原值是脏值时
            // 单选会显示成「感染发热」，但落库值由 sceneValue() 保住原样，且界面另有提示。
            scene = EmergencyScene.fromKey(existing?.scene),
            date = existing?.date ?: LocalDate.now().toString(),
            symptoms = existing?.symptoms ?: "",
            actions = existing?.actionsTaken ?: "",
            hospitalVisit = existing?.hospitalVisit ?: false,
            hospitalName = existing?.hospitalName ?: "",
            outcome = existing?.outcome ?: "",
            resolved = existing?.resolvedDate != null,
            notes = existing?.notes ?: "",
            sceneRaw = existing?.scene,
        )
    }

    /**
     * 场景落库值。
     *
     * v1.2.10（审查 §6.9）：**用户没动过单选、且原值本应用不认识时，原样写回**。
     * 其余情况按用户选中的枚举落库（含"脏值 → 用户重新选了场景"这条正常路径）。
     */
    private fun sceneValue(): String {
        if (sceneTouched) return scene.name
        val raw = sceneRaw
        return if (raw != null && EmergencyScene.fromKeyOrNull(raw) == null) raw else scene.name
    }

    fun toEvent(existing: EmergencyEvent?): EmergencyEvent = EmergencyEvent(
        id = existing?.id ?: "", date = date.trim(),
        recordedAt = existing?.recordedAt ?: nowIso(),
        scene = sceneValue(), severity = existing?.severity ?: "high",
        onsetTime = existing?.onsetTime,
        symptoms = symptoms.ifBlank { null },
        actionsTaken = actions.ifBlank { null },
        hospitalVisit = hospitalVisit,
        hospitalName = hospitalName.ifBlank { null },
        outcome = outcome.ifBlank { null },
        resolvedDate = if (resolved) LocalDate.now().toString() else null,
        notes = notes.ifBlank { null },
    )
}
