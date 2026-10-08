package com.ashkb.app.ui.me

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource

import com.ashkb.app.R
import com.ashkb.app.data.entity.DoseState
import com.ashkb.app.data.entity.KbEntry
import com.ashkb.app.data.entity.MedClass
import com.ashkb.app.domain.DrugInteractionKeys
import com.ashkb.app.data.entity.MedFrequency
import com.ashkb.app.domain.DrugKeyCatalog
import com.ashkb.app.domain.ScheduleCalc
import com.ashkb.app.ui.components.DateTextField
import com.ashkb.app.ui.theme.Size
import com.ashkb.app.ui.theme.Spacing

/**
 * [MedEditScreen] 第一步 / 第二步的各个 section（v1.0.92 批次 17 从 `MedEditScreen.kt` 抽出）。
 *
 * ### 为什么 section 收 `MutableState` 而不是「值 + 回调」
 * 这是本批的**全部要点**：重组范围由**快照读点**决定。若 section 收的是值
 * （`MedBasicsSection(name = name, onNameChange = { name = it })`），那么 `name` 是在
 * **根级正文**里被读的——敲一个键就重组整屏（拆分前的形状）。收 `MutableState` 后，
 * `.value` 在 section 自己的组合作用域里读，敲键只重组那个 section。
 * 这与 v1.0.91 的 `VitalsSection(vm, sheets: MutableState<WellnessSheetState>)` 是同一写法。
 *
 * ### 为什么 section 里**不**自己 `remember` 这些字段
 * ① 保存路径 `buildMed()`（在 `MedEditScreen` 的底部按钮回调里）要读**全表单**；
 * ② 编辑模式的一次性预填 `LaunchedEffect(editId)` 要写**同一份** state——写成两份就会出现
 *    「预填只生效一半」，而 `cycleDays` 这类字段**不可见时仍会落库**
 *    （`buildMed` 里 `injCycleDays` 只看 `route`，不看字段是否可见），state 跟着可见性分支
 *    一起消失就等于悄悄改值；
 * ③ 这些字段是 `rememberSaveable`，转屏 / 进程死亡的恢复语义与拆分前逐字一致。
 * 详见 `MedEditScreen.kt` 的 KDoc。
 *
 * ### 一条规则只写一处
 * [cycleAnchorVisible] 同时被本文件的注射周期分支与 `MedEditScreen` 的保存按钮门控使用，
 * 因此它是**唯一一份**判定（拆分前它也是唯一一份，只是位置在根级正文里）。
 */

/**
 * 「注射周期锚点日期」是否可见 / 是否需要校验。
 *
 * v1.0.78（批次 4 收尾）：锚点日期此前是自由文本、**无任何校验**——`2026-13-45` 一旦落库，
 * 「每 N 天」的周期推算会整体错位，且没有任何地方会报错。该输入框只在「注射 + 每 N 天 /
 * 自定义」分支出现，故校验也只在它可见时生效（不可见时不动既有值，避免挡住与它无关的编辑）。
 *
 * v1.0.92（批次 17）：本判定改由调用方在**自己的作用域**里求值——写进根级正文会让敲锚点日期
 * 重组整屏（`DateFieldRules.requiredOk(startDate)` 依赖 startDate）。
 *
 * v1.2.4：**自定义周期 / 每月一次不再要求 `route == "injection"`**。原来这一条把口服挡在门外，
 * 于是「口服 + 自定义周期」选完没有任何地方能填周期天数与锚点日期（维护者报「自定义周期
 * 没法自定义日期」）；而排程侧同时也只对注射生效，等于选了等于没选。
 * 每两周（Q2W）仍是注射专属——它是「几周一针」的概念，保持旧口径不动。
 */
internal fun cycleAnchorVisible(route: String, frequency: MedFrequency): Boolean = when (frequency) {
    MedFrequency.CUSTOM, MedFrequency.MONTHLY -> true
    MedFrequency.Q2W -> route == "injection"
    else -> false
}

/**
 * 周期类频次：按「每 N 天」或「每月某日号数」出卡，**不按星期**。
 *
 * v1.2.4：与 [cycleAnchorVisible] 成对——凡按周期出卡的频次，注射说明
 * （[R.string.med_inj_schedule_note]，讲的是「按下方固定星期自动出卡」）都不该显示，
 * 否则会告诉用户一件不会发生的事。
 */
internal fun isCycleFrequency(frequency: MedFrequency): Boolean =
    frequency == MedFrequency.Q2W || frequency == MedFrequency.CUSTOM ||
        frequency == MedFrequency.MONTHLY

// ===== 第一步：基础信息 =====

/**
 * 基础信息：药品名 / 商品名 / 通用名键 + 自动匹配建议 + 药品分类。
 *
 * 四个字段同属一件事（写的是同一张卡的同一个区块），故合成一个 section：在它内部敲键只重组
 * 这个 section，不会碰到下面的给药途径、频次、存放等区块。
 */
@Composable
internal fun MedBasicsSection(
    name: MutableState<String>,
    brand: MutableState<String>,
    nameKey: MutableState<String>,
    medClass: MutableState<MedClass>,
) {
    OutlinedTextField(
        name.value, { name.value = it },
        label = { Text(stringResource(R.string.med_name_field)) },
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        brand.value, { brand.value = it },
        label = { Text(stringResource(R.string.med_brand_field)) },
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        nameKey.value, { nameKey.value = it },
        label = { Text(stringResource(R.string.med_generic_key_field)) },
        modifier = Modifier.fillMaxWidth(),
        supportingText = { Text(stringResource(R.string.med_generic_key_note)) },
    )
    // P5 R8：自动匹配——键为空时按药品名检索建议
    //
    // v1.0.86（批次 11）：检索结果用 remember 记住。原实现每次组合都跑一遍
    // DrugKeyCatalog.suggest（40 条目录 × 键/中文名/商品名/别名各一次 lowercase 匹配），
    // 而本页每次按键都会重组 → 每键一次全表扫描。
    //
    // key 取 `keyQuery` 这个**单一字符串**，而不是 (nameKey, name) 二元组：
    //   · 它已经把"到底拿哪个字段去查"（nameKey 非空用 nameKey，否则用 name）折进自身，
    //     两个输入任一变都必然变 key —— 不存在"漏了某个影响结果的输入"；
    //   · 用字符串而非 Pair 当 key，避免每次重组都新建一个 Pair 让 remember 白失效。
    // limit 用默认值 6（本文件是唯一调用点），故不进 key。
    val keyQuery = if (nameKey.value.isBlank()) name.value else nameKey.value
    val keySuggestions = remember(keyQuery) { DrugKeyCatalog.suggest(keyQuery) }
    if (keySuggestions.isNotEmpty() && !DrugKeyCatalog.isExactKey(nameKey.value)) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            keySuggestions.forEach { s ->
                Column {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                nameKey.value = s.key
                                if (name.value.isBlank()) name.value = s.display
                                if (brand.value.isBlank() && !s.brand.isNullOrBlank()) brand.value = s.brand
                                if (medClass.value == MedClass.OTHER) medClass.value = s.medClass
                            },
                    ) {
                        Text(
                            "${s.key} · ${s.display}${s.brand?.let { "（$it）" } ?: ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    // v1.1.3（批次 19 · J-7）：选中前后都把 [DrugKeyEntry.note] 摆在建议行下方。
                    // 放在**建议列表里**而不是选中后的确认弹窗：患者是在这一步决定「录哪支药」，
                    // 等录完再提示就晚了。中性措辞见 DrugKeyCatalog.IL23_NO_AS。
                    s.note?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
    Text(stringResource(R.string.med_category), style = MaterialTheme.typography.labelMedium)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        MedClass.entries.forEach { c ->
            FilterChip(selected = medClass.value == c, onClick = { medClass.value = c }, label = { Text(stringResource(c.labelRes)) })
        }
    }
}

// ===== 第一步：给药途径与剂量 =====

/** 给药途径（口服 / 注射）+ 剂量。途径是跨 section 的联动源（频次、注射周期、与餐关系都看它）。 */
@Composable
internal fun MedRouteDoseSection(
    route: MutableState<String>,
    dose: MutableState<String>,
) {
    Text(stringResource(R.string.med_route), style = MaterialTheme.typography.labelMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        FilterChip(
            selected = route.value == "oral",
            onClick = { route.value = "oral" },
            label = { Text(stringResource(R.string.med_route_oral)) },
        )
        FilterChip(
            selected = route.value == "injection",
            onClick = { route.value = "injection" },
            label = { Text(stringResource(R.string.med_route_injection)) },
        )
    }
    OutlinedTextField(
        dose.value, { dose.value = it },
        label = { Text(stringResource(R.string.med_dose_field)) },
        modifier = Modifier.fillMaxWidth(),
    )
}

// ===== 第一步：频次与计划用药时间 =====

/**
 * 用药频次 + 计划用药时间（口服多选 / 注射单选）+ 按需原因。
 *
 * 注射分支里嵌了 [MedCycleSection]（位置与拆分前一致：在「计划用药时间」标签**之前**）——
 * 它是独立 section，所以在周期天数 / 锚点日期里敲键只重组那一小块。
 */
@Composable
internal fun MedScheduleSection(
    frequency: MutableState<MedFrequency>,
    route: MutableState<String>,
    times: MutableState<List<String>>,
    prnReason: MutableState<String>,
    cycleDays: MutableState<String>,
    startDate: MutableState<String>,
    // v1.1.2（批次 18）：甲氨蝶呤频次强提示需要「用户填的是什么药」，
    // 而「填药名」在 MedBasicsSection——多收这一组 state 只为让错误提示**贴着频次 chips** 显示，
    // 与 `biwError` 的做法一致（错误要出现在它约束的那几个控件旁边，不是另找一处）。
    nameKey: MutableState<String> = mutableStateOf(""),
    mtxError: MutableState<Boolean> = mutableStateOf(false),
) {
    Text(stringResource(R.string.med_frequency), style = MaterialTheme.typography.labelMedium)
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        // v1.2.4：`hidden` 项（每 8 小时）不再作为新选项出现；但**当前值若是 hidden 项**
        // 仍要显示出来——否则编辑一张历史「每 8 小时」的药单时，频次区会一片空白，
        // 用户看不出它现在是什么，一保存还可能被别的选项顶掉。
        MedFrequency.entries.filter { !it.hidden || it == frequency.value }.forEach { f ->
            FilterChip(
                selected = frequency.value == f,
                onClick = { frequency.value = f; mtxError.value = false },
                label = { Text(stringResource(f.labelRes)) },
            )
        }
    }
    if (mtxError.value && DrugInteractionKeys.requiresWeeklyFrequency(nameKey.value) &&
        frequency.value != MedFrequency.WEEKLY
    ) {
        Text(
            stringResource(R.string.med_mtx_frequency_error),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
    if (frequency.value != MedFrequency.PRN) {
        // v1.2.4：周期锚点从注射分支里提出来——「自定义周期 / 每月一次」在口服下也要能填。
        // 原来它被 `else`（注射）挡着，口服选完这两个频次看不到任何周期输入框，
        // 而排程侧同时也不按周期出卡 → 选了等于没选（维护者报「自定义周期没法自定义日期」）。
        if (cycleAnchorVisible(route.value, frequency.value)) {
            MedCycleSection(
                cycleDays = cycleDays,
                startDate = startDate,
                isInjection = route.value != "oral",
                isMonthly = frequency.value == MedFrequency.MONTHLY,
            )
        }
        // v1.0.51：注射类此前**没有任何「计划用药时间」入口**——时刻选择只在口服分支里，
        // 注射的时刻被静默写成表单默认值 08:00，用户既看不到也改不了；
        // 今日卡又只显示「注射」不显示时刻，于是「计划用药时间」在全应用都无处可见。
        // 与保存按钮的判定同一份：**校验生效的范围 == 输入框可见的范围**，
        // 两处各写一遍条件迟早会漂移（改了这里忘了那里，就会出现「看得见却没人校验」）
        Text(stringResource(R.string.med_dose_time_field), style = MaterialTheme.typography.labelMedium)
        PlanTimePicker(
            times = times.value,
            onTimesChange = { times.value = it },
            single = route.value != "oral",
        )
        // 固定星期类（WEEKLY / BIW）才按星期出卡，故说明只在这类频次下显示；
        // 周期类（Q2W / CUSTOM / MONTHLY）同样不按星期，也要排除
        if (route.value != "oral" && !isCycleFrequency(frequency.value)) {
            Text(
                stringResource(R.string.med_inj_schedule_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        OutlinedTextField(
            prnReason.value, { prnReason.value = it },
            label = { Text(stringResource(R.string.med_prn_reason_field)) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * 周期输入（每 N 天 + 锚点日期，或每月用药日）。
 *
 * 可见性由 [cycleAnchorVisible] 决定；它的两个字段**任何时候都会落库**
 * （`buildMed` 只在 [cycleAnchorVisible] 为真时写 `injCycleDays`），
 * 所以 state 由根级持有、本 section 只读它：把 state 搬进分支会让「隐藏期间的值」随分支一起消失。
 *
 * v1.2.4：① 不再只服务注射——口服的「自定义周期 / 每月一次」同样走这里，故文案按 [isInjection] 分岔；
 * ② 每月一次按**日历日号数**出卡（见 `ScheduleCalc.isCycleDay`），周期天数对它没有意义，
 * 故 [isMonthly] 时整个不渲染天数输入框（少一个填了也不生效的字段）。
 */
@Composable
internal fun MedCycleSection(
    cycleDays: MutableState<String>,
    startDate: MutableState<String>,
    isInjection: Boolean,
    isMonthly: Boolean,
) {
    if (!isMonthly) {
        OutlinedTextField(
            cycleDays.value, { cycleDays.value = it.filter { c -> c.isDigit() }.take(3) },
            label = {
                Text(
                    stringResource(
                        if (isInjection) R.string.med_inj_cycle_field else R.string.med_oral_cycle_field,
                    ),
                )
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
    DateTextField(
        value = startDate.value,
        onValueChange = { startDate.value = it },
        label = stringResource(
            when {
                isMonthly -> R.string.med_monthly_anchor_date
                isInjection -> R.string.med_cycle_anchor_date
                else -> R.string.med_oral_cycle_anchor_date
            },
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

// ===== 第一步：固定星期 =====

/** 固定星期（WEEKLY 一天 / BIW 两天）。不可见时不发出任何节点，与拆分前一致。 */
@Composable
internal fun MedWeekdaySection(
    frequency: MutableState<MedFrequency>,
    weekday: MutableState<Int>,
    weekday2: MutableState<Int>,
    biwError: MutableState<Boolean>,
) {
    if (frequency.value == MedFrequency.WEEKLY || frequency.value == MedFrequency.BIW) {
        Text(
            if (frequency.value == MedFrequency.BIW) stringResource(R.string.med_biweekly_note)
            else stringResource(R.string.med_freq_fixed_weekday),
            style = MaterialTheme.typography.labelMedium,
        )
        WeekdayChipRow(selected = weekday.value, onSelect = { weekday.value = it; biwError.value = false })
        if (frequency.value == MedFrequency.BIW) {
            WeekdayChipRow(selected = weekday2.value, onSelect = { weekday2.value = it; biwError.value = false })
            if (biwError.value && weekday.value == weekday2.value) {
                Text(
                    stringResource(R.string.med_inj_two_days_error),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/**
 * 一周七天 chips（横向可滚）。
 *
 * v1.0.92（批次 17）：原来两行（第一次 / 第二次）各内联一份七天 `listOf(1 to …7 to …)`，
 * 每行都超过 detekt 的 `MaxLineLength`（140）而挂在基线上。抽成一份后两处不可能再分叉，
 * 也顺手把两行超长行改回合规长度（基线不动，条目自然失效）。
 */
@Composable
private fun WeekdayChipRow(selected: Int, onSelect: (Int) -> Unit) {
    val days = listOf(
        1 to stringResource(R.string.weekday_one),
        2 to stringResource(R.string.weekday_two),
        3 to stringResource(R.string.weekday_three),
        4 to stringResource(R.string.weekday_four),
        5 to stringResource(R.string.weekday_five),
        6 to stringResource(R.string.weekday_six),
        7 to stringResource(R.string.weekday_sunday),
    )
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        days.forEach { (d, l) ->
            FilterChip(selected = selected == d, onClick = { onSelect(d) }, label = { Text(l) })
        }
    }
}

// ===== 第一步：与餐关系 / 存放 / 服药状态 =====

/** 与餐关系（仅口服）。 */
@Composable
internal fun MedMealSection(
    route: MutableState<String>,
    food: MutableState<String>,
) {
    if (route.value == "oral") {
        Text(stringResource(R.string.nutrition_meal_relation), style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            listOf(
                "with_food" to stringResource(R.string.med_with_meal),
                "empty_stomach" to stringResource(R.string.med_fasting),
                "any" to stringResource(R.string.common_any),
            ).forEach { (k, l) ->
                FilterChip(selected = food.value == k, onClick = { food.value = k }, label = { Text(l) })
            }
        }
    }
}

/** 存放说明（备注类字段，独立 section：在它里面敲键不碰其它区块）。 */
@Composable
internal fun MedStorageSection(storage: MutableState<String>) {
    OutlinedTextField(
        storage.value, { storage.value = it },
        label = { Text(stringResource(R.string.med_storage_hint)) },
        modifier = Modifier.fillMaxWidth(),
    )
}

/** 服药三态（固定 / 按需 / 减量中）+ 减量备注。 */
@Composable
internal fun MedDoseStateSection(
    doseState: MutableState<DoseState>,
    taperNote: MutableState<String>,
) {
    // C6：服药三态（固定 / 按需 / 减量中），与分类、给药途径同为单选 chip
    Text(stringResource(R.string.med_dose_state_label), style = MaterialTheme.typography.labelMedium)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        DoseState.entries.forEach { s ->
            FilterChip(selected = doseState.value == s, onClick = { doseState.value = s }, label = { Text(stringResource(s.labelRes)) })
        }
    }
    if (doseState.value == DoseState.TAPERING) {
        OutlinedTextField(
            taperNote.value, { taperNote.value = it },
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
}

// ===== 第二步：R03 核对清单 =====

/**
 * 第二步核对清单：相互作用检索结果 + 两个确认勾选。
 *
 * [hits] 的**写入点**在 `MedEditScreen` 底部按钮的协程里（点击时才检索），读点在这里——
 * 因此它必须由根级持有（section 自己 `remember` 的东西，底部按钮的回调够不着）。
 */
@Composable
internal fun MedVerifySection(
    hits: MutableState<List<KbEntry>?>,
    doctorTold: MutableState<Boolean>,
    leafletRead: MutableState<Boolean>,
) {
    val h = hits.value
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
                // v1.0.92（批次 17）：前缀先取出来再拼串——原来那一行超出 140 字符上限
                // （挂在 detekt 的 MaxLineLength 基线上），拆开后文案一字不变。
                val severityPrefix = if (e.severityLevel == "high") {
                    stringResource(R.string.knowledge_high_risk_prefix)
                } else {
                    stringResource(R.string.common_notice_prefix)
                }
                Text(
                    "· $severityPrefix${e.title}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (e.severityLevel == "high") MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurface,
                )
            }
            if (h.size > 5) {
                Text(stringResource(R.string.ui_med_kb_remaining, h.size - 5), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    HorizontalDivider(Modifier.padding(vertical = Spacing.xs))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = doctorTold.value, onCheckedChange = { doctorTold.value = it })
        Text(stringResource(R.string.med_told_doctor))
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = leafletRead.value, onCheckedChange = { leafletRead.value = it })
        Text(stringResource(R.string.med_verified_manual))
    }
    Text(
        stringResource(R.string.med_verify_optional_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

// ===== 计划用药时间选择器（随「频次与时间」section 一起搬来）=====

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
internal fun PlanTimePicker(
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
