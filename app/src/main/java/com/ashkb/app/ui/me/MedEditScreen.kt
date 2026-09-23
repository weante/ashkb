package com.ashkb.app.ui.me

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource

import com.ashkb.app.R
import com.ashkb.app.data.db.Ids
import com.ashkb.app.data.entity.DoseState
import com.ashkb.app.data.entity.KbEntry
import com.ashkb.app.data.entity.MedClass
import com.ashkb.app.data.entity.MedFrequency
import com.ashkb.app.data.entity.Medication
import com.ashkb.app.data.repo.nowIso
import com.ashkb.app.domain.DrugKeyCatalog
import com.ashkb.app.domain.ScheduleCalc
import com.ashkb.app.ui.components.ScreenTopBar
import com.ashkb.app.ui.theme.Size
import com.ashkb.app.ui.theme.Spacing
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONArray

/**
 * M1 添加 / 编辑药品：两步流程（route `meds/edit`）。
 *
 * 原为 15 字段的两步 `AlertDialog`，弹窗里必然局促且无法保存草稿 —— 改为全屏表单：
 * 顶栏承载步骤标题与返回，底部固定操作条，内容区整屏滚动。
 * v1.0.31：[editId] 非空 = 编辑既有在用药品（预填全部参数，保留 id / createdAt / 核对记录）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedEditScreen(
    vm: MeViewModel,
    editId: String? = null,
    onSaved: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var step by rememberSaveable { mutableStateOf(1) }

    // ---- 第一步字段 ----
    var name by rememberSaveable { mutableStateOf("") }
    var brand by rememberSaveable { mutableStateOf("") }
    var nameKey by rememberSaveable { mutableStateOf("") }
    var medClass by rememberSaveable { mutableStateOf(MedClass.OTHER) }
    var route by rememberSaveable { mutableStateOf("oral") }
    var dose by rememberSaveable { mutableStateOf("") }
    var frequency by rememberSaveable { mutableStateOf(MedFrequency.DAILY) }
    var times by rememberSaveable { mutableStateOf(listOf(ScheduleCalc.DEFAULT_PLAN_TIME)) }
    var weekday by rememberSaveable { mutableStateOf(1) }
    var weekday2 by rememberSaveable { mutableStateOf(4) }
    var biwError by rememberSaveable { mutableStateOf(false) }
    var cycleDays by rememberSaveable { mutableStateOf("14") }
    var food by rememberSaveable { mutableStateOf("any") }
    var prnReason by rememberSaveable { mutableStateOf("") }
    var storage by rememberSaveable { mutableStateOf("") }
    var startDate by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    // ---- C6 服药三态：固定 / 按需 / 减量中（默认固定，编辑时按原值预填） ----
    var doseState by rememberSaveable { mutableStateOf(DoseState.FIXED) }
    var taperNote by rememberSaveable { mutableStateOf("") }

    // ---- 第二步 R03 ----
    var hits by remember { mutableStateOf<List<KbEntry>?>(null) }
    var doctorTold by rememberSaveable { mutableStateOf(false) }
    var leafletRead by rememberSaveable { mutableStateOf(false) }

    val prnFallback = stringResource(R.string.med_reason_backup)

    // ---- 编辑模式：预填在用药品参数（仅按 editId 跑一次，不覆盖用户后续输入） ----
    // v1.0.43 修复：原先在 vm.meds（**仅「在用」列表**）上 `first { 含该 id }`——目标药若已停用、
    // 或恢复备份后 id 漂移，谓词永不为真 → 协程永久挂起 → original 恒为 null → 保存时
    // id = Ids.new("med")，把「编辑」静默变成**新建一条重复药**，并丢掉病史核对记录。
    // 改为按 id 一次性直查（不限在用）；查不到则明确提示并禁止保存，绝不静默新建。
    var original by remember { mutableStateOf<Medication?>(null) }
    var editMissing by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(editId) {
        if (editId == null) return@LaunchedEffect
        val med = vm.medicationById(editId)
        if (med == null) {
            editMissing = true
            return@LaunchedEffect
        }
        original = med
        name = med.name
        brand = med.brandName ?: ""
        nameKey = med.nameKey
        medClass = MedClass.fromKey(med.medClass)
        route = med.route
        dose = med.dose
        frequency = MedFrequency.fromKey(med.frequency)
        times = ScheduleCalc.takeTimesOf(med).ifEmpty { listOf(ScheduleCalc.DEFAULT_PLAN_TIME) }
        weekday = med.weeklyWeekday ?: 1
        weekday2 = med.weeklyWeekday2 ?: 4
        cycleDays = med.injCycleDays?.toString() ?: "14"
        food = med.takeWithFood ?: "any"
        prnReason = med.prnReason ?: ""
        storage = med.storage ?: ""
        startDate = med.startDate
        // C6：未显式设置（旧数据）时按 frequency 推断
        doseState = DoseState.of(med)
        taperNote = med.taperNote ?: ""
        doctorTold = med.checkDoctorTold
        leafletRead = med.checkLeafletRead
    }

    fun buildMed(): Medication = Medication(
        // 编辑模式保留身份与创建时间（updatedAt 由仓库层 upsert 刷新）；新增模式维持原逻辑
        id = original?.id ?: Ids.new("med"),
        name = name.trim(),
        brandName = brand.trim().ifBlank { null },
        nameKey = nameKey.trim().lowercase(),
        medClass = medClass.name,
        route = route,
        dose = dose.trim(),
        frequency = frequency.name,
        prnReason = if (frequency == MedFrequency.PRN) prnReason.trim().ifBlank { prnFallback } else null,
        takeTimes = if (frequency == MedFrequency.PRN || route == "injection") {
            times.take(1).let { if (it.isEmpty()) null else JSONArray(it).toString() }
        } else {
            JSONArray(times).toString()
        },
        weeklyWeekday = if (frequency == MedFrequency.WEEKLY || frequency == MedFrequency.BIW) weekday else null,
        weeklyWeekday2 = if (frequency == MedFrequency.BIW) weekday2 else null,
        startDate = startDate,
        injCycleDays = if (route == "injection") cycleDays.toIntOrNull() ?: 14 else null,
        storage = storage.trim().ifBlank { null },
        takeWithFood = if (route == "oral") food else null,
        // C6：服药状态；减量备注仅在「减量中」时落库（切回固定 / 按需即清空，避免残留脏备注）
        doseState = doseState.name,
        taperNote = if (doseState == DoseState.TAPERING) taperNote.trim().ifBlank { null } else null,
        checkDoctorTold = doctorTold,
        checkLeafletRead = leafletRead,
        interactionCheckDate = original?.interactionCheckDate
            ?: if (step >= 2) LocalDate.now().toString() else null,
        createdAt = original?.createdAt ?: nowIso(),
        updatedAt = nowIso(),
        // v1.0.43：编辑模式必须保留归档态——buildMed 未列出该字段时取实体默认值 false，
        // 会把「已停用」的药静默复活（预填改为按 id 直查后可能拿到归档药，故此处置为必需）
        isArchived = original?.isArchived ?: false,
    )

    fun goBackStep() {
        if (step == 1) onBack() else step = 1
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            ScreenTopBar(
                title = when {
                    editId != null && step == 1 -> stringResource(R.string.med_edit_medication)
                    step == 1 -> stringResource(R.string.med_add_medication)
                    else -> stringResource(R.string.med_verify_checklist)
                },
                onBack = ::goBackStep,
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.lg),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    OutlinedButton(
                        onClick = ::goBackStep,
                        modifier = Modifier.weight(1f).heightIn(min = Size.touchMin),
                    ) { Text(if (step == 1) stringResource(R.string.common_cancel) else stringResource(R.string.backup_return_modify)) }

                    Button(
                        onClick = {
                            // v1.0.43：编辑目标查不到时禁止保存——否则会静默新建一条重复药
                            if (editMissing) return@Button
                            if (step == 1) {
                                if (name.isBlank() || nameKey.isBlank() || dose.isBlank()) return@Button
                                if (frequency == MedFrequency.BIW && weekday == weekday2) {
                                    biwError = true
                                    return@Button
                                }
                                val draft = buildMed()
                                scope.launch {
                                    hits = vm.interactionsFor(draft)
                                    step = 2
                                }
                            } else {
                                vm.saveMedication(context, buildMed())
                                onSaved()
                            }
                        },
                        enabled = !editMissing,
                        modifier = Modifier.weight(1f).heightIn(min = Size.touchMin),
                    ) { Text(if (step == 1) stringResource(R.string.med_next_verify) else stringResource(R.string.checkup_save_record)) }
                }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            if (step == 1) {
                // v1.0.43：编辑目标不存在（已删除 / id 失效）时明确告知，不静默新建
                if (editMissing) {
                    Surface(
                        Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Text(
                            stringResource(R.string.med_edit_missing),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(Spacing.sm),
                        )
                    }
                }
                OutlinedTextField(
                    name, { name = it },
                    label = { Text(stringResource(R.string.med_name_field)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    brand, { brand = it },
                    label = { Text(stringResource(R.string.med_brand_field)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    nameKey, { nameKey = it },
                    label = { Text(stringResource(R.string.med_generic_key_field)) },
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = { Text(stringResource(R.string.med_generic_key_note)) },
                )
                // P5 R8：自动匹配——键为空时按药品名检索建议
                val keySuggestions = DrugKeyCatalog.suggest(if (nameKey.isBlank()) name else nameKey)
                if (keySuggestions.isNotEmpty() && !DrugKeyCatalog.isExactKey(nameKey)) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        keySuggestions.forEach { s ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        nameKey = s.key
                                        if (name.isBlank()) name = s.display
                                        if (brand.isBlank() && !s.brand.isNullOrBlank()) brand = s.brand
                                        if (medClass == MedClass.OTHER) medClass = s.medClass
                                    },
                            ) {
                                Text(
                                    "${s.key} · ${s.display}${s.brand?.let { "（$it）" } ?: ""}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
                Text(stringResource(R.string.med_category), style = MaterialTheme.typography.labelMedium)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                    MedClass.entries.forEach { c ->
                        FilterChip(selected = medClass == c, onClick = { medClass = c }, label = { Text(c.label) })
                    }
                }
                Text(stringResource(R.string.med_route), style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    FilterChip(selected = route == "oral", onClick = { route = "oral" }, label = { Text(stringResource(R.string.med_route_oral)) })
                    FilterChip(selected = route == "injection", onClick = { route = "injection" }, label = { Text(stringResource(R.string.med_route_injection)) })
                }
                OutlinedTextField(
                    dose, { dose = it },
                    label = { Text(stringResource(R.string.med_dose_field)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.med_frequency), style = MaterialTheme.typography.labelMedium)
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                    MedFrequency.entries.forEach { f ->
                        FilterChip(selected = frequency == f, onClick = { frequency = f }, label = { Text(f.label) })
                    }
                }
                if (frequency != MedFrequency.PRN) {
                    if (route == "oral") {
                        Text(stringResource(R.string.med_dose_time_field), style = MaterialTheme.typography.labelMedium)
                        PlanTimePicker(
                            times = times,
                            onTimesChange = { times = it },
                            single = false,
                        )
                    } else {
                        // v1.0.51：注射类此前**没有任何「计划用药时间」入口**——时刻选择只在口服分支里，
                        // 注射的时刻被静默写成表单默认值 08:00，用户既看不到也改不了；
                        // 今日卡又只显示「注射」不显示时刻，于是「计划用药时间」在全应用都无处可见。
                        if (frequency == MedFrequency.Q2W || frequency == MedFrequency.CUSTOM) {
                            OutlinedTextField(
                                cycleDays, { cycleDays = it.filter { c -> c.isDigit() }.take(3) },
                                label = { Text(stringResource(R.string.med_inj_cycle_field)) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            OutlinedTextField(
                                startDate, { startDate = it },
                                label = { Text(stringResource(R.string.med_cycle_anchor_date)) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        Text(stringResource(R.string.med_dose_time_field), style = MaterialTheme.typography.labelMedium)
                        PlanTimePicker(
                            times = times,
                            onTimesChange = { times = it },
                            single = true,
                        )
                        // 固定星期类（WEEKLY / BIW）才按星期出卡，故说明只在这类频次下显示
                        if (frequency != MedFrequency.Q2W && frequency != MedFrequency.CUSTOM) {
                            Text(
                                stringResource(R.string.med_inj_schedule_note),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    OutlinedTextField(
                        prnReason, { prnReason = it },
                        label = { Text(stringResource(R.string.med_prn_reason_field)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (frequency == MedFrequency.WEEKLY || frequency == MedFrequency.BIW) {
                    Text(
                        if (frequency == MedFrequency.BIW) stringResource(R.string.med_biweekly_note)
                        else stringResource(R.string.med_freq_fixed_weekday),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                        listOf(1 to stringResource(R.string.weekday_one), 2 to stringResource(R.string.weekday_two), 3 to stringResource(R.string.weekday_three), 4 to stringResource(R.string.weekday_four), 5 to stringResource(R.string.weekday_five), 6 to stringResource(R.string.weekday_six), 7 to stringResource(R.string.weekday_sunday)).forEach { (d, l) ->
                            FilterChip(
                                selected = weekday == d,
                                onClick = { weekday = d; biwError = false },
                                label = { Text(l) },
                            )
                        }
                    }
                    if (frequency == MedFrequency.BIW) {
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                            listOf(1 to stringResource(R.string.weekday_one), 2 to stringResource(R.string.weekday_two), 3 to stringResource(R.string.weekday_three), 4 to stringResource(R.string.weekday_four), 5 to stringResource(R.string.weekday_five), 6 to stringResource(R.string.weekday_six), 7 to stringResource(R.string.weekday_sunday)).forEach { (d, l) ->
                                FilterChip(
                                    selected = weekday2 == d,
                                    onClick = { weekday2 = d; biwError = false },
                                    label = { Text(l) },
                                )
                            }
                        }
                        if (biwError && weekday == weekday2) {
                            Text(
                                stringResource(R.string.med_inj_two_days_error),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
                if (route == "oral") {
                    Text(stringResource(R.string.nutrition_meal_relation), style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        listOf("with_food" to stringResource(R.string.med_with_meal), "empty_stomach" to stringResource(R.string.med_fasting), "any" to stringResource(R.string.common_any)).forEach { (k, l) ->
                            FilterChip(selected = food == k, onClick = { food = k }, label = { Text(l) })
                        }
                    }
                }
                OutlinedTextField(
                    storage, { storage = it },
                    label = { Text(stringResource(R.string.med_storage_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                // C6：服药三态（固定 / 按需 / 减量中），与分类、给药途径同为单选 chip
                Text(stringResource(R.string.med_dose_state_label), style = MaterialTheme.typography.labelMedium)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                    DoseState.entries.forEach { s ->
                        FilterChip(selected = doseState == s, onClick = { doseState = s }, label = { Text(s.label) })
                    }
                }
                if (doseState == DoseState.TAPERING) {
                    OutlinedTextField(
                        taperNote, { taperNote = it },
                        label = { Text(stringResource(R.string.med_taper_note_label)) },
                        placeholder = { Text(stringResource(R.string.med_taper_note_hint)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    // 说明「减量中」在停药流程里豁免自行停药警示
                    Text(
                        stringResource(R.string.med_taper_exempt_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                // ---- 第二步：R03 核对清单 ----
                val h = hits
                if (h == null) {
                    Text(stringResource(R.string.knowledge_searching))
                } else if (h.isEmpty()) {
                    Text(
                        stringResource(R.string.knowledge_no_interaction_data),
                        color = MaterialTheme.colorScheme.error,
                    )
                } else {
                    Text(stringResource(R.string.med_kb_hits_prefix, h.size), style = MaterialTheme.typography.titleSmall)
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                        h.take(5).forEach { e ->
                            Text(
                                "· ${if (e.severityLevel == "high") stringResource(R.string.knowledge_high_risk_prefix) else stringResource(R.string.common_notice_prefix)}${e.title}",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (e.severityLevel == "high") MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        if (h.size > 5) {
                            Text("…其余 ${h.size - 5} 条可在知识库查看", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = Spacing.xs))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = doctorTold, onCheckedChange = { doctorTold = it })
                    Text(stringResource(R.string.med_told_doctor))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = leafletRead, onCheckedChange = { leafletRead = it })
                    Text(stringResource(R.string.med_verified_manual))
                }
                Text(
                    stringResource(R.string.med_verify_optional_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // 底部操作条已固定，这里补足滚动余量
            Text("", Modifier.padding(bottom = Spacing.xl))
        }
    }
}

/** 表示「正在新增一个时刻」的下标哨兵（非负下标表示在编辑已有时刻） */
private const val NEW_TIME_INDEX = -1

/**
 * 计划用药时间选择器。
 *
 * v1.0.52：**不再给「06:30 / 08:00 / …」这类固定候选**，改为一个真正的时间选择器
 * （[TimePickerDialog]），并且**始终把当前已设的时刻显示出来**。
 *
 * 旧实现用固定 chip 的「选中态」表达当前值，只要存的时刻不在那 5 个候选里
 * （如 07:30），编辑页上**没有任何 chip 被选中**——用户看不到自己设过什么，
 * 也无从判断该不该改。
 *
 * - **口服可多选**（一天多次）：已选时刻逐个列出，点它改、点 ✕ 删，另有「添加时刻」；
 * - **注射单选**：只有一行，点开即改——一针只有一个时刻，且不会退化成「零个时刻」
 *   （否则 `take_times` 变 null，计划时刻会被静默回退到 [ScheduleCalc.DEFAULT_PLAN_TIME]）。
 */
@Composable
private fun PlanTimePicker(
    times: List<String>,
    onTimesChange: (List<String>) -> Unit,
    single: Boolean,
) {
    val shown = times.distinct().sorted()
    var editingIndex by remember { mutableStateOf<Int?>(null) }

    if (single) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = Size.touchMin)
                .clickable(onClickLabel = stringResource(R.string.med_edit_time)) { editingIndex = 0 },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(
                shown.firstOrNull() ?: ScheduleCalc.DEFAULT_PLAN_TIME,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Icon(
                Icons.Rounded.Schedule,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(Size.iconSm),
            )
        }
    } else {
        shown.forEachIndexed { idx, t ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier
                        .weight(1f)
                        .heightIn(min = Size.touchMin)
                        .clickable(onClickLabel = stringResource(R.string.med_edit_time)) { editingIndex = idx },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Text(t, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Icon(
                        Icons.Rounded.Edit,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(Size.iconSm),
                    )
                }
                IconButton(
                    onClick = { onTimesChange(shown.filterNot { it == t }) },
                    modifier = Modifier.size(Size.touchMin),
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.med_remove_time))
                }
            }
        }
        TextButton(onClick = { editingIndex = NEW_TIME_INDEX }) {
            Text(stringResource(R.string.med_add_time))
        }
    }

    editingIndex?.let { idx ->
        TimePickerDialog(
            // 初始值 = 该行当前已设的时刻（新增时用默认值）——编辑已有药品时看到的就是存过的值
            initial = shown.getOrNull(idx) ?: ScheduleCalc.DEFAULT_PLAN_TIME,
            onConfirm = { picked ->
                onTimesChange(
                    when {
                        single -> listOf(picked)
                        idx == NEW_TIME_INDEX -> (shown + picked).distinct().sorted()
                        else -> shown.toMutableList().also { it[idx] = picked }.distinct().sorted()
                    }
                )
                editingIndex = null
            },
            onDismiss = { editingIndex = null },
        )
    }
}

/**
 * 计划用药时间选择对话框（M3 `TimePicker`，24 小时制）。
 *
 * 初始值取**当前已设时刻**并容错（见 [ScheduleCalc.timeParts]），
 * 因此「编辑已有药品」时显示的是已保存的值，而不是某个写死的默认值。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val hm = remember(initial) { ScheduleCalc.timeParts(initial) }
    val state = rememberTimePickerState(initialHour = hm.first, initialMinute = hm.second, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.med_time_picker_title)) },
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = { onConfirm("%02d:%02d".format(state.hour, state.minute)) }) {
                Text(stringResource(R.string.common_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}
