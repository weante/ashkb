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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
    var times by rememberSaveable { mutableStateOf(listOf("08:00")) }
    var customTime by rememberSaveable { mutableStateOf("") }
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
        times = ScheduleCalc.takeTimesOf(med).ifEmpty { listOf("08:00") }
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
                            custom = customTime,
                            onCustomChange = { customTime = it },
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
                            custom = customTime,
                            onCustomChange = { customTime = it },
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

/** 常用计划时刻（口服与注射共用同一套候选） */
private val COMMON_PLAN_TIMES = listOf("06:30", "08:00", "12:00", "18:00", "21:00")

/**
 * 计划用药时间选择器（v1.0.51 从口服分支抽出，供口服与注射共用）。
 *
 * - **口服可多选**：一天多次（如每日两次 = 08:00 + 20:00），再点一次即取消；
 * - **注射只能单选**：一针只有一个时刻，且单选态下点「已选中」的时刻**不做取消**——
 *   否则会退化成「零个时刻」，`take_times` 变 null 后计划时刻静默回退到 09:00。
 *
 * 自定义时刻用 [ScheduleCalc.TIME_PATTERN] 校验（合法 00:00–23:59）。此前用
 * `\d{2}:\d{2}` 会放行 `25:99`：它被加进「已选时刻」看起来生效了，存库后又被
 * `takeTimesOf` 过滤掉——**用户以为设了时刻，其实那个槽位根本不存在**，且毫无提示。
 */
@Composable
private fun PlanTimePicker(
    times: List<String>,
    onTimesChange: (List<String>) -> Unit,
    custom: String,
    onCustomChange: (String) -> Unit,
    single: Boolean,
) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        COMMON_PLAN_TIMES.forEach { t ->
            FilterChip(
                selected = t in times,
                onClick = {
                    onTimesChange(
                        when {
                            single -> listOf(t)
                            t in times -> times - t
                            else -> times + t
                        }
                    )
                },
                label = { Text(t) },
            )
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        OutlinedTextField(
            custom, onCustomChange,
            label = { Text(stringResource(R.string.med_custom_time)) },
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = {
            if (ScheduleCalc.TIME_PATTERN.matches(custom) && custom !in times) {
                onTimesChange(if (single) listOf(custom) else times + custom)
                onCustomChange("")
            }
        }) { Text(stringResource(R.string.common_add)) }
    }
    // 单选（注射）时已选值就是唯一那个 chip 本身，无需再列一遍
    if (!single && times.isNotEmpty()) {
        Text(
            "已选时刻：${times.sorted().joinToString("、")}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
