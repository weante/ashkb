package com.ashkb.app.ui.wellness

import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.NoMeals
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle


import com.ashkb.app.data.entity.DietProfile
import com.ashkb.app.data.entity.FoodAvoidItem
import com.ashkb.app.data.entity.Medication
import com.ashkb.app.data.entity.Supplement
import com.ashkb.app.data.entity.SupplementCategory
import com.ashkb.app.data.entity.SupplementLog
import com.ashkb.app.data.entity.Vitals
import com.ashkb.app.data.repo.nowIso
import com.ashkb.app.domain.AdherenceCalc
import com.ashkb.app.domain.ClinicalThresholds
import com.ashkb.app.domain.Labels
import com.ashkb.app.domain.SupplementLimits
import com.ashkb.app.domain.SupplementLogStatus
import com.ashkb.app.domain.SupplementTiming
import com.ashkb.app.domain.WeightTarget
import com.ashkb.app.R
import com.ashkb.app.ui.components.DestructiveAction
import com.ashkb.app.ui.components.DividerList
import com.ashkb.app.ui.components.EmptyState
import com.ashkb.app.ui.components.KeyValueRow
import com.ashkb.app.ui.components.NavRow
import com.ashkb.app.ui.components.ScreenTopBar
import com.ashkb.app.ui.components.SectionCard
import com.ashkb.app.ui.components.StatTile
import com.ashkb.app.ui.components.StatusChip
import com.ashkb.app.ui.components.TrendChart
import com.ashkb.app.ui.components.TrendPoint
import com.ashkb.app.ui.theme.Size
import com.ashkb.app.ui.theme.Spacing
import com.ashkb.app.ui.theme.StatusTone

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun WellnessScreen(vm: WellnessViewModel, onOpenRecipes: () -> Unit, onBack: () -> Unit) {
    val vitals by vm.vitalsToday.collectAsStateWithLifecycle()
    val weight by vm.weightToday.collectAsStateWithLifecycle()
    val weightList by vm.weightRecent.collectAsStateWithLifecycle()
    val profile by vm.profile.collectAsStateWithLifecycle()
    val bm by vm.bodyMeasureLatest.collectAsStateWithLifecycle()
    val supplements by vm.supplements.collectAsStateWithLifecycle()
    val supLogs by vm.supplementLogsToday.collectAsStateWithLifecycle()
    val diet by vm.dietProfile.collectAsStateWithLifecycle()
    val avoids by vm.foodAvoidItems.collectAsStateWithLifecycle()
    // v1.0.38（B11）：在用药单——补剂错开提醒与合并时间表的数据源
    val medications by vm.medications.collectAsStateWithLifecycle()

    var showVitals by remember { mutableStateOf(false) }
    var showWeight by remember { mutableStateOf(false) }
    var showWeightManage by remember { mutableStateOf(false) }
    var showBodyMeasure by remember { mutableStateOf(false) }
    var showSupplementForm by remember { mutableStateOf(false) }
    var showDietForm by remember { mutableStateOf(false) }
    var showAvoidManage by remember { mutableStateOf(false) }
    var detailSup by remember { mutableStateOf<Supplement?>(null) }
    // v1.0.38（B11）：编辑补剂时复用同一表单并预填既有值
    var editSup by remember { mutableStateOf<Supplement?>(null) }
    // v1.0.81（批次 7）：待删的**整个补剂条目**（与详情里的逐条删除是两回事，文案各说各的）
    var deletingSup by remember { mutableStateOf<Supplement?>(null) }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(title = stringResource(R.string.nutrition_bone_health_title), onBack = onBack)

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Spacing.lg, end = Spacing.lg,
                top = Spacing.md, bottom = Spacing.xxl,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            // ---- 分组一：体征 ----
            stickyHeader { GroupHeader(stringResource(R.string.vitals_short)) }
            item { VitalsHero(vitals = vitals, onEdit = { showVitals = true }) }

            // ---- 分组二：身体成分 ----
            stickyHeader { GroupHeader(stringResource(R.string.vitals_body_composition)) }
            item {
                SectionCard(
                    title = stringResource(R.string.wellness_weight_tracking),
                    subtitle = if (weightList.isEmpty()) null else stringResource(R.string.wellness_weight_records, weightList.size),
                    action = {
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            OutlinedButton(onClick = { showWeight = true }) {
                                Text(if (weight != null) stringResource(R.string.common_edit) else stringResource(R.string.common_record))
                            }
                            if (weightList.isNotEmpty()) {
                                TextButton(onClick = { showWeightManage = true }) {
                                    Text(stringResource(R.string.common_manage))
                                }
                            }
                        }
                    },
                ) {
                    // DAO 按日期倒序返回，趋势图需要从旧到新。
                    // 必须 remember：List.map 每次返回新实例，会让 TrendChart 的入场动画（以 points 为 key）
                    // 在任何一次父级重组时反复从头播放
                    val points = remember(weightList) {
                        weightList.asReversed().map { TrendPoint(it.date, it.weightKg.toFloat()) }
                    }
                    if (points.isNotEmpty()) {
                        TrendChart(points = points, unit = "kg", label = stringResource(R.string.vitals_weight))
                        Spacer(Modifier.height(Spacing.md))
                    }
                    StatTile(
                        label = stringResource(R.string.vitals_today_weight),
                        value = weight?.weightKg?.let { "%.1f".format(it) } ?: stringResource(R.string.common_not_recorded),
                        unit = weight?.let { "kg" },
                    )
                    // v10（C9）：体重目标区间提示（区间由档案设定）
                    WeightTargetHint(weightKg = weight?.weightKg, profile = profile)
                }
            }
            item {
                SectionCard(
                    title = stringResource(R.string.vitals_body_measures),
                    action = {
                        OutlinedButton(onClick = { showBodyMeasure = true }) {
                            Text(if (bm != null) stringResource(R.string.common_update) else stringResource(R.string.common_input_action))
                        }
                    },
                ) {
                    if (bm != null) {
                        KeyValueRow(stringResource(R.string.vitals_height_short), bm!!.heightCm?.let { "%.0f cm".format(it) } ?: stringResource(R.string.common_unfilled))
                        KeyValueRow(stringResource(R.string.vitals_waist), bm!!.waistCm?.let { "%.0f cm".format(it) } ?: stringResource(R.string.common_unfilled))
                        KeyValueRow(stringResource(R.string.vitals_hip), bm!!.hipCm?.let { "%.0f cm".format(it) } ?: stringResource(R.string.common_unfilled))
                        KeyValueRow("BMI", bm!!.bmi?.let { "%.1f".format(it) } ?: stringResource(R.string.common_unfilled))
                    } else {
                        EmptyState(
                            icon = Icons.Rounded.Straighten,
                            title = stringResource(R.string.report_no_baseline),
                            body = stringResource(R.string.vitals_bmi_hint),
                        )
                    }
                }
            }

            // ---- 分组三：营养与饮食 ----
            stickyHeader { GroupHeader(stringResource(R.string.wellness_nutrition_section)) }
            // B3：推荐食谱库入口——放在饮食分组首位，与下方饮食画像 / 忌口清单同属「吃什么」的决策链
            item {
                NavRow(
                    icon = Icons.Rounded.Restaurant,
                    title = stringResource(R.string.recipes_title),
                    subtitle = stringResource(R.string.recipes_entry_sub),
                    onClick = onOpenRecipes,
                )
            }
            item {
                SectionCard(
                    title = stringResource(R.string.nutrition_supplement_archive),
                    subtitle = stringResource(R.string.wellness_supplements_count, supplements.size),
                    action = {
                        OutlinedButton(onClick = { showSupplementForm = true }) { Text(stringResource(R.string.common_add)) }
                    },
                ) {
                    if (supplements.isEmpty()) {
                        EmptyState(
                            icon = Icons.Rounded.Medication,
                            title = stringResource(R.string.nutrition_no_supplements),
                            body = stringResource(R.string.nutrition_supplement_hint),
                            actionLabel = stringResource(R.string.nutrition_add_supplement),
                            onAction = { showSupplementForm = true },
                        )
                    } else {
                        DividerList(supplements, key = { it.id }) { sup ->
                            SupplementCardRow(
                                sup = sup,
                                // 打卡态集合一次性算好：避免每行对全部 supLogs 做 O(N·M) 线性扫描
                                todayStatus = SupplementLogStatus.loggedToday(supLogs, sup.id),
                                actions = SupplementRowActions(
                                    onOpenDetail = { detailSup = sup },
                                    onEdit = { editSup = sup },
                                    onDelete = { deletingSup = sup },
                                ),
                                onCheckIn = { vm.checkInSupplement(sup, AdherenceCalc.DONE, null, null) },
                                onSkip = { vm.checkInSupplement(sup, AdherenceCalc.SKIPPED, null, null) },
                                onUndo = { vm.undoSupplementCheckIn(sup) },
                            )
                        }
                        // B11：矿物类补剂与「螯合类」用药服用时间过近（间隔 < 2 小时）→ 错开提醒
                        val conflicts = remember(supplements, medications) {
                            SupplementTiming.conflicts(supplements, medications)
                        }
                        if (conflicts.isNotEmpty()) {
                            Spacer(Modifier.height(Spacing.sm))
                            conflicts.forEach { c ->
                                Text(
                                    stringResource(R.string.supp_timing_conflict, c.medName, c.gapMinutes),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                        Text(
                            stringResource(R.string.nutrition_supplement_history_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            // ---- B11：今日服药 / 补剂合并时间表 ----
            item {
                SectionCard(title = stringResource(R.string.supp_merged_title)) {
                    // 只取当前在用药单（takeTimes）与在补剂（times），按时刻升序合并
                    val rows = remember(medications, supplements) {
                        mergedTimeline(medications, supplements)
                    }
                    if (rows.isEmpty()) {
                        Text(
                            stringResource(R.string.supp_merged_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        DividerList(rows, key = { it.key }) { row ->
                            Text(row.time, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                row.name,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            StatusChip(
                                text = stringResource(
                                    if (row.isMed) R.string.supp_merged_med_tag else R.string.supp_merged_supp_tag
                                ),
                                tone = if (row.isMed) StatusTone.Info else StatusTone.Brand,
                            )
                        }
                    }
                }
            }
            item {
                SectionCard(
                    title = stringResource(R.string.nutrition_diet_profile),
                    action = {
                        if (diet != null) {
                            OutlinedButton(onClick = { showDietForm = true }) { Text(stringResource(R.string.common_edit)) }
                        }
                    },
                ) {
                    if (diet != null) {
                        val d = diet!!
                        KeyValueRow(stringResource(R.string.nutrition_diet_pattern), Labels.dietPattern(d.dietPattern))
                        KeyValueRow(stringResource(R.string.nutrition_fish_intake), Labels.seafoodFreq(d.seafoodFreq))
                        KeyValueRow(stringResource(R.string.nutrition_dairy), Labels.dairyTolerance(d.dairyTolerant))
                    } else {
                        EmptyState(
                            icon = Icons.Rounded.Restaurant,
                            title = stringResource(R.string.nutrition_profile_empty),
                            body = stringResource(R.string.nutrition_profile_benefit),
                            actionLabel = stringResource(R.string.nutrition_set_profile),
                            onAction = { showDietForm = true },
                        )
                    }
                }
            }
            item {
                SectionCard(
                    title = stringResource(R.string.nutrition_avoid_list),
                    subtitle = stringResource(R.string.wellness_avoid_count, avoids.size),
                    action = {
                        OutlinedButton(onClick = { showAvoidManage = true }) { Text(stringResource(R.string.common_manage)) }
                    },
                ) {
                    if (avoids.isEmpty()) {
                        EmptyState(
                            icon = Icons.Rounded.NoMeals,
                            title = stringResource(R.string.nutrition_no_avoid_items),
                            body = stringResource(R.string.nutrition_avoid_hint),
                            actionLabel = stringResource(R.string.nutrition_add_avoid_item),
                            onAction = { showAvoidManage = true },
                        )
                    } else {
                        DividerList(avoids.take(5)) { item ->
                            Text(
                                item.name,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                "${Labels.foodAvoidCategory(item.category)} · ${Labels.foodAvoidSeverity(item.severity)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (avoids.size > 5) {
                            TextButton(onClick = { showAvoidManage = true }) {
                                Text(stringResource(R.string.wellness_view_all_avoids, avoids.size))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showVitals) VitalsSheet(vm = vm, onDismiss = { showVitals = false })
    if (showWeight) WeightSheet(vm = vm, onDismiss = { showWeight = false })
    if (showWeightManage) WeightManageSheet(vm = vm, onDismiss = { showWeightManage = false })
    if (showBodyMeasure) BodyMeasureSheet(vm = vm, onDismiss = { showBodyMeasure = false })
    if (showSupplementForm) SupplementSheet(vm = vm, onDismiss = { showSupplementForm = false })
    if (showDietForm) DietSheet(vm = vm, current = diet, onDismiss = { showDietForm = false })
    if (showAvoidManage) AvoidManageSheet(vm = vm, onDismiss = { showAvoidManage = false })
    detailSup?.let { sup -> SupplementDetailSheet(vm = vm, sup = sup, onDismiss = { detailSup = null }) }
    editSup?.let { sup -> SupplementSheet(vm = vm, current = sup, onDismiss = { editSup = null }) }
    deletingSup?.let { sup -> SupplementDeleteDialog(vm = vm, sup = sup, onDismiss = { deletingSup = null }) }
}

/**
 * v1.0.87（批次 12）：补剂行的**归属类动作**（与「今天打卡态」无关的那三个入口）。
 *
 * 为什么收成一个参数对象：补剂行同时要接「今日动作」（打卡 / 跳过 / 撤销）、「状态」
 * （`todayStatus`）与「档案类动作」（详情 / 编辑 / 删除）三组东西，摊平就是 8 个形参——
 * detekt 的 `LongParameterList`（阈值 8）立刻报警。这三件事的共同点是**与今天无关**：
 * 无论今天有没有打卡，它们都长在行上、都指向同一条补剂。收成一组后形参降到 6 个，
 * 顺带让「今天的状态变化」不会牵动这三个入口的语义。
 */
internal data class SupplementRowActions(
    val onOpenDetail: () -> Unit,
    val onEdit: () -> Unit,
    val onDelete: () -> Unit,
)

/**
 * v1.0.87（批次 12）：补剂档案列表的一行——左侧补剂信息（点开详情）+ 右侧今日动作或状态。
 *
 * 新增「跳过」后，这一行的右半部从「一个按钮 / 一个胶囊」变成最多 4 个元素
 * （胶囊或两个动作按钮 + 编辑 + 删除），因此整行收进本函数：原来那 3 个块直接挂在
 * `DividerList` 的 `RowScope` 里，加一个按钮就会把名称列挤成一条缝。
 *
 * 布局与批次 7 保持一致（分割线、可点区域、编辑 / 删除入口的位置都没动）：
 *  · 左列：`fillMaxWidth()` + 父 Row 的 `weight(1f)` 一起把右侧推到屏幕边缘；
 *  · 右侧：胶囊（已记录）**或**两个动作按钮（未记录），下面依次是编辑与删除。
 *
 * 「跳过」不弹任何表单、不要求填理由：补剂不是处方药，没有「为什么没吃」这一问
 * （药品那套 `SkipReason` 是给漏服追责用的，照搬到补剂只是多一步无用输入）。
 */
@Composable
private fun RowScope.SupplementCardRow(
    sup: Supplement,
    todayStatus: String?,
    actions: SupplementRowActions,
    onCheckIn: () -> Unit,
    onSkip: () -> Unit,
    onUndo: () -> Unit,
) {
    Column(
        // v1.0.81（批次 7）：点整行打开「补剂详情」（最近服用记录 + 逐条删除）。卡片状态由
        // WellnessScreen 的 detailSup 持有，这里只回调——写成两份 state 的话，点击只改一份、
        // 弹层读另一份，点了没反应。
        // v1.0.88（批次 12 回归修复）：这里必须是 weight(1f) 而不是 fillMaxWidth()。
        // DividerList 的 itemContent 是 RowScope——fillMaxWidth() 会把整行宽度吃光，
        // 后面的「打卡/跳过/撤销/编辑/删除」全被挤成零宽（维护者真机看到卡片只剩名称，
        // 按钮全部消失）。weight(1f) 既保留"点整行开详情"的触达区，又给按钮留下自己的宽度。
        Modifier.weight(1f).clickable { actions.onOpenDetail() },
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        Text("${sup.name} ${sup.dose}", style = MaterialTheme.typography.bodyMedium)
        Text(
            SupplementCategory.fromKey(sup.category).label +
                (sup.brand?.let { " · $it" } ?: ""),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // B11：当日累计（单次剂量 × 每日次数）超出用户自填的每日上限 → 警示；
        // 未填上限 / 未量化单次剂量时 exceedsDailyMax 返回 null，静默不提示
        if (SupplementLimits.exceedsDailyMax(sup) == true) {
            val total = SupplementLimits.dailyTotal(sup)
            val max = sup.dailyMax
            if (total != null && max != null) {
                Text(
                    stringResource(
                        R.string.supp_over_limit,
                        fmtNum(total), SupplementLimits.unitLabel(sup), fmtNum(max),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
    when (todayStatus) {
        // 已服 / 跳过都由 SupplementLogStatus 映射（文案与色调与详情弹层同源）；
        // 跳过用**中性色**：与药品卡（`medStatusOf`）一致——不是错误，不该报警
        AdherenceCalc.SKIPPED -> StatusChip(
            text = stringResource(SupplementLogStatus.labelRes(todayStatus)),
            tone = SupplementLogStatus.tone(todayStatus),
            icon = Icons.Rounded.RemoveCircleOutline,
        )
        null -> Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            TextButton(onClick = onCheckIn, modifier = Modifier.heightIn(min = Size.touchMin)) {
                Text(stringResource(R.string.exercise_checkin_short))
            }
            TextButton(onClick = onSkip, modifier = Modifier.heightIn(min = Size.touchMin)) {
                Text(stringResource(R.string.med_skip))
            }
        }
        else -> StatusChip(
            text = stringResource(SupplementLogStatus.labelRes(todayStatus)),
            tone = SupplementLogStatus.tone(todayStatus),
            icon = Icons.Rounded.CheckCircle,
        )
    }
    // v1.0.80（批次 6）：撤销误点的打卡——此前点错了就再也回不到「今天还没吃」，
    // 「已服用」胶囊会一直挂着（而它与补剂本身的删除入口长得一样危险）。
    // v1.0.87（批次 12）：对「已服」与「跳过」用同一个入口与同一句文案——撤销就是把今天的
    // 记录整个抹掉回到未记录态，两种状态在这里没有区别，分成两句话只会让用户多读一行。
    if (todayStatus != null) {
        DestructiveAction(
            label = stringResource(R.string.nutrition_supplement_undo),
            confirmTitle = stringResource(R.string.nutrition_supplement_undo_confirm, sup.name),
            confirmBody = stringResource(R.string.nutrition_supplement_undo_note),
            onConfirm = onUndo,
        )
    }
    TextButton(onClick = actions.onEdit) { Text(stringResource(R.string.common_edit)) }
    // v1.0.81（批次 7）：这里的删除 = **删掉整个补剂条目**（连带它名下的服用记录，
    // 条数由确认框异步取并报出，见 SupplementDeleteDialog）。只想删某一天的记录时，
    // 走「点击补剂」打开的详情弹层——两种语义此前共用一句「删除…？」，
    // 而确认框里那句「已产生的服用记录仍保留」更是与实情相反，正是用户困惑的根源。
    TextButton(
        onClick = actions.onDelete,
        modifier = Modifier.heightIn(min = Size.touchMin),
    ) { Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error) }
}

/**
 * v10（C9）：体重目标区间提示。
 *
 * 判定逻辑在纯函数 `domain/WeightTarget`（可单测）；这里只呈现结论。
 * 未设目标时不打扰用户（只在填了区间后出现）；只填一侧提示补全。
 */
@Composable
private fun WeightTargetHint(weightKg: Double?, profile: com.ashkb.app.data.entity.Profile?) {
    val low = profile?.weightTargetLow
    val high = profile?.weightTargetHigh
    val status = WeightTarget.status(weightKg, low, high)
    if (status == WeightTarget.Status.NO_TARGET) return

    Spacer(Modifier.height(Spacing.sm))
    val range = WeightTarget.normalize(low, high)
    when (status) {
        WeightTarget.Status.INCOMPLETE -> Text(
            stringResource(R.string.weight_target_incomplete),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        WeightTarget.Status.IN_RANGE -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            StatusChip(stringResource(R.string.weight_target_in_range), StatusTone.Success, Icons.Rounded.CheckCircle)
            range?.let {
                Text(
                    stringResource(R.string.weight_target_range_label, fmtKg(it.first), fmtKg(it.second)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        WeightTarget.Status.BELOW, WeightTarget.Status.ABOVE -> {
            val dev = WeightTarget.deviation(weightKg, low, high) ?: 0.0
            val text = if (status == WeightTarget.Status.BELOW) {
                stringResource(R.string.weight_target_below, fmtKg(-dev))
            } else {
                stringResource(R.string.weight_target_above, fmtKg(dev))
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                StatusChip(text, StatusTone.Warning, Icons.Rounded.WarningAmber)
                range?.let {
                    Text(
                        stringResource(R.string.weight_target_range_label, fmtKg(it.first), fmtKg(it.second)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        else -> Unit
    }
}

/** 数值显示：整数不带小数点，其余一位小数（体重、补剂剂量共用）。 */
private fun fmtNum(v: Double): String = if (v == v.toLong().toDouble()) v.toLong().toString() else "%.1f".format(v)

/** 体重数值显示：整数不带小数点，其余一位小数。 */
private fun fmtKg(v: Double): String = fmtNum(v)

/**
 * B11 合并时间表的一行：时刻 + 名称 + 类别（药 / 补）。
 * `sortKey` 为时刻的当日分钟数，非法时刻排到最后；`key` 供 DividerList 稳定复用。
 */
private data class TimelineRow(val time: String, val name: String, val isMed: Boolean) {
    val sortKey: Int get() = SupplementTiming.minutesOf(time) ?: Int.MAX_VALUE
    val key: String get() = "$time|$name|$isMed"
}

/**
 * B11：把药单 `takeTimes` 与补剂 `times` 展平、按时刻升序合并成时间表；
 * 非法时刻直接丢弃（不抛异常）。
 */
private fun mergedTimeline(
    medications: List<Medication>,
    supplements: List<Supplement>,
): List<TimelineRow> {
    val rows = mutableListOf<TimelineRow>()
    fun add(raw: String?, name: String, isMed: Boolean) {
        raw?.split(',')?.forEach { slot ->
            val t = slot.trim()
            if (SupplementTiming.minutesOf(t) != null) rows.add(TimelineRow(t, name, isMed))
        }
    }
    medications.forEach { add(it.takeTimes, it.name, true) }
    supplements.forEach { add(it.times, it.name, false) }
    return rows.sortedWith(compareBy({ it.sortKey }, { it.name }))
}

/** 分组头：sticky。吸附时用页面底色融入背景，底边 1dp 分隔。 */
@Composable
private fun GroupHeader(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.fillMaxWidth()) {
            Text(
                text,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = Spacing.xs),
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

/** 今日体征 hero：体温 / 血压 / 心率三格 StatTile，异常格按 ClinicalThresholds 着色。 */
@Composable
private fun VitalsHero(vitals: Vitals?, onEdit: () -> Unit) {
    val temp = vitals?.temperature
    val sys = vitals?.bpSys
    val dia = vitals?.bpDia
    val hr = vitals?.heartRate

    val tempTone = when {
        temp == null -> StatusTone.Neutral
        temp >= ClinicalThresholds.FEVER_ALERT -> StatusTone.Danger
        temp >= ClinicalThresholds.FEVER_LOW -> StatusTone.Warning
        else -> StatusTone.Success
    }
    val bpTone = when {
        sys == null || dia == null -> StatusTone.Neutral
        sys >= ClinicalThresholds.BP_HIGH_SYS || dia >= ClinicalThresholds.BP_HIGH_DIA -> StatusTone.Danger
        sys <= ClinicalThresholds.BP_LOW_SYS -> StatusTone.Warning
        else -> StatusTone.Success
    }
    val hrTone = when {
        hr == null -> StatusTone.Neutral
        hr > ClinicalThresholds.HR_HIGH || hr < ClinicalThresholds.HR_LOW -> StatusTone.Warning
        else -> StatusTone.Success
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(min = Size.heroMinHeight)
                .padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.vitals_today_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                FilledTonalButton(onClick = onEdit) {
                    Text(if (vitals != null) stringResource(R.string.common_edit) else stringResource(R.string.common_record))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Box(Modifier.weight(1f)) {
                    StatTile(
                        label = stringResource(R.string.vitals_temperature_short),
                        value = temp?.let { "%.1f".format(it) } ?: stringResource(R.string.common_not_recorded),
                        unit = temp?.let { "℃" },
                        tone = tempTone,
                    )
                }
                Box(Modifier.weight(1f)) {
                    StatTile(
                        label = stringResource(R.string.vitals_bp),
                        value = if (sys != null && dia != null) "$sys/$dia" else stringResource(R.string.common_not_recorded),
                        tone = bpTone,
                    )
                }
                Box(Modifier.weight(1f)) {
                    StatTile(
                        label = stringResource(R.string.vitals_heart_rate),
                        value = hr?.toString() ?: stringResource(R.string.common_not_recorded),
                        unit = hr?.let { stringResource(R.string.vitals_bpm_unit) },
                        tone = hrTone,
                    )
                }
            }
            vitals?.notes?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 表单：多字段一律 ModalBottomSheet（半屏可拖、键盘弹起体验远好于 AlertDialog）
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VitalsSheet(vm: WellnessViewModel, onDismiss: () -> Unit) {
    val current by vm.vitalsToday.collectAsStateWithLifecycle()
    // v1.0.73（D4）：remember 以行 id 为 key——原先用无 key 的 remember，冷启动时 `current` 先为
    // null、后到达，字段会停在空白（与下面的「同日覆盖」叠加就是一次静默数据丢失）。
    var temp by remember(current?.id) { mutableStateOf(current?.temperature?.toString() ?: "") }
    var sys by remember(current?.id) { mutableStateOf(current?.bpSys?.toString() ?: "") }
    var dia by remember(current?.id) { mutableStateOf(current?.bpDia?.toString() ?: "") }
    var hr by remember(current?.id) { mutableStateOf(current?.heartRate?.toString() ?: "") }
    var notes by remember(current?.id) { mutableStateOf(current?.notes ?: "") }

    // v1.0.73（D4）：保存前必须「至少一项数值」且每项落在生理可信区间内。
    // 此前空表单可直接提交，而 saveVitals 是**同日覆盖**——一次误提交会抹掉当天已录的血压/心率。
    val tempVal = temp.trim().toDoubleOrNull()
    val sysVal = sys.trim().toIntOrNull()
    val diaVal = dia.trim().toIntOrNull()
    val hrVal = hr.trim().toIntOrNull()
    val anyValue = tempVal != null || sysVal != null || diaVal != null || hrVal != null
    val rangesOk = (tempVal == null || tempVal in 30.0..45.0) &&
        (sysVal == null || sysVal in 50..300) &&
        (diaVal == null || diaVal in 30..200) &&
        (hrVal == null || hrVal in 20..250) &&
        (sysVal == null || diaVal == null || sysVal > diaVal)
    val canSave = anyValue && rangesOk

    ModalBottomSheet(onDismissRequest = onDismiss) {
        SheetColumn {
            Text(stringResource(R.string.vitals_record_action), style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(temp, { temp = it }, label = { Text(stringResource(R.string.vitals_temperature_field)) }, singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedTextField(sys, { sys = it }, label = { Text(stringResource(R.string.vitals_bp_systolic)) },
                    singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(dia, { dia = it }, label = { Text(stringResource(R.string.vitals_bp_diastolic)) },
                    singleLine = true, modifier = Modifier.weight(1f))
            }
            OutlinedTextField(hr, { hr = it }, label = { Text(stringResource(R.string.vitals_heart_rate_field)) }, singleLine = true)
            OutlinedTextField(notes, { notes = it }, label = { Text(stringResource(R.string.common_notes_optional)) })
            // v1.0.73（D4）：空表单 / 越界数值一律不允许提交（此前无 enabled 门，可直接覆盖当天数据）
            if (!canSave) {
                Text(
                    stringResource(if (anyValue) R.string.vitals_invalid_range else R.string.vitals_need_one_value),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            SheetSaveButton(
                text = stringResource(R.string.common_save),
                enabled = canSave,
                onClick = {
                    if (!canSave) return@SheetSaveButton
                    vm.saveVitals(
                        temperature = tempVal,
                        bpSys = sysVal,
                        bpDia = diaVal,
                        heartRate = hrVal,
                        notes = notes.ifBlank { null },
                    )
                    onDismiss()
                },
            )
            if (current != null) {
                DestructiveAction(
                    label = stringResource(R.string.wellness_delete_today_vitals),
                    confirmTitle = stringResource(R.string.wellness_delete_vitals_confirm),
                    confirmBody = stringResource(R.string.wellness_delete_vitals_note),
                    onConfirm = {
                        vm.deleteVitalsToday()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WeightSheet(vm: WellnessViewModel, onDismiss: () -> Unit) {
    val current by vm.weightToday.collectAsStateWithLifecycle()
    var weight by remember { mutableStateOf(current?.weightKg?.toString() ?: "") }
    var notes by remember { mutableStateOf(current?.notes ?: "") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        SheetColumn {
            Text(stringResource(R.string.vitals_record_weight), style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(weight, { weight = it }, label = { Text(stringResource(R.string.vitals_weight_field)) }, singleLine = true)
            OutlinedTextField(notes, { notes = it }, label = { Text(stringResource(R.string.common_notes_optional)) })
            SheetSaveButton(
                text = stringResource(R.string.common_save),
                enabled = weight.toDoubleOrNull() != null,
                onClick = {
                    weight.toDoubleOrNull()?.let { w ->
                        vm.saveWeight(w, notes.ifBlank { null })
                        onDismiss()
                    }
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BodyMeasureSheet(vm: WellnessViewModel, onDismiss: () -> Unit) {
    val current by vm.bodyMeasureLatest.collectAsStateWithLifecycle()
    val weightList by vm.weightRecent.collectAsStateWithLifecycle()
    var height by remember { mutableStateOf(current?.heightCm?.toString() ?: "") }
    var waist by remember { mutableStateOf(current?.waistCm?.toString() ?: "") }
    var hip by remember { mutableStateOf(current?.hipCm?.toString() ?: "") }
    var bmi by remember { mutableStateOf(current?.bmi?.toString() ?: "") }
    var notes by remember { mutableStateOf(current?.notes ?: "") }

    // U2：身高 + 最新体重齐备 → BMI 自动回填（仍可手动覆盖）；身高明显异常（<50 或 >250cm）不参与计算
    val weightKg = weightList.firstOrNull()?.weightKg
    val heightNum = height.toDoubleOrNull()
    val autoBmi = if (weightKg != null && heightNum != null && heightNum in 50.0..250.0) {
        String.format(java.util.Locale.US, "%.1f", weightKg / ((heightNum / 100) * (heightNum / 100)))
    } else null
    LaunchedEffect(autoBmi) { if (autoBmi != null) bmi = autoBmi }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        SheetColumn {
            Text(stringResource(R.string.vitals_body_measures), style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(height, { height = it }, label = { Text(stringResource(R.string.vitals_height_cm)) }, singleLine = true)
            OutlinedTextField(waist, { waist = it }, label = { Text(stringResource(R.string.vitals_waist_cm)) }, singleLine = true)
            OutlinedTextField(hip, { hip = it }, label = { Text(stringResource(R.string.vitals_hip_cm)) }, singleLine = true)
            OutlinedTextField(bmi, { bmi = it }, label = { Text(stringResource(R.string.profile_bmi_auto)) }, singleLine = true)
            Text(
                if (weightKg != null) stringResource(R.string.vitals_bmi_auto_note, "%.1f".format(weightKg))
                else stringResource(R.string.vitals_bmi_need_weight),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(notes, { notes = it }, label = { Text(stringResource(R.string.common_notes_optional)) })
            SheetSaveButton(
                text = stringResource(R.string.common_save),
                onClick = {
                    vm.saveBodyMeasure(
                        heightCm = height.toDoubleOrNull(),
                        waistCm = waist.toDoubleOrNull(),
                        hipCm = hip.toDoubleOrNull(),
                        bmi = bmi.toDoubleOrNull(),
                        notes = notes.ifBlank { null },
                    )
                    onDismiss()
                },
            )
            if (current != null) {
                DestructiveAction(
                    label = stringResource(R.string.wellness_delete_this_record),
                    confirmTitle = stringResource(R.string.wellness_delete_body_confirm),
                    confirmBody = stringResource(R.string.wellness_delete_body_note),
                    onConfirm = {
                        vm.deleteBodyMeasureLatest()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun SupplementSheet(vm: WellnessViewModel, current: Supplement? = null, onDismiss: () -> Unit) {
    // B11：编辑时预填既有值（含剂量三态字段）；新增时全空
    var name by remember { mutableStateOf(current?.name ?: "") }
    var brand by remember { mutableStateOf(current?.brand ?: "") }
    var dose by remember { mutableStateOf(current?.dose ?: "") }
    var doseAmount by remember { mutableStateOf(current?.doseAmount?.let { fmtNum(it) } ?: "") }
    var doseUnit by remember { mutableStateOf(current?.doseUnit ?: "") }
    var dailyMax by remember { mutableStateOf(current?.dailyMax?.let { fmtNum(it) } ?: "") }
    var category by remember { mutableStateOf(SupplementCategory.fromKey(current?.category)) }
    var notes by remember { mutableStateOf(current?.notes ?: "") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        SheetColumn {
            Text(
                stringResource(if (current == null) R.string.nutrition_add_supplement else R.string.common_edit),
                style = MaterialTheme.typography.titleLarge,
            )
            OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.common_name)) }, singleLine = true)
            OutlinedTextField(brand, { brand = it }, label = { Text(stringResource(R.string.nutrition_brand_field)) }, singleLine = true)
            OutlinedTextField(dose, { dose = it }, label = { Text(stringResource(R.string.nutrition_dose_field)) }, singleLine = true)
            // B11：量化剂量三输入——单次剂量 / 单位 / 每日参考上限（上限由用户自填，App 不内置医学数值）
            OutlinedTextField(doseAmount, { doseAmount = it }, label = { Text(stringResource(R.string.supp_dose_amount_label)) }, singleLine = true)
            OutlinedTextField(doseUnit, { doseUnit = it }, label = { Text(stringResource(R.string.supp_dose_unit_label)) }, singleLine = true)
            OutlinedTextField(dailyMax, { dailyMax = it }, label = { Text(stringResource(R.string.supp_daily_max_label)) }, singleLine = true)
            Text(
                stringResource(R.string.supp_daily_max_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(stringResource(R.string.common_category), style = MaterialTheme.typography.labelMedium)
            ChipGroup(
                options = SupplementCategory.entries.map { it.name to it.label },
                selected = category.name,
                onSelect = { key -> category = SupplementCategory.fromKey(key) },
            )
            OutlinedTextField(notes, { notes = it }, label = { Text(stringResource(R.string.common_notes_optional)) })
            SheetSaveButton(
                text = stringResource(R.string.common_save),
                enabled = name.isNotBlank() && dose.isNotBlank(),
                onClick = {
                    if (name.isNotBlank() && dose.isNotBlank()) {
                        vm.saveSupplement(
                            Supplement(
                                id = current?.id ?: "",
                                name = name.trim(),
                                brand = brand.ifBlank { null },
                                category = category.name,
                                dose = dose.trim(),
                                // 编辑时保留原排班 / 用药属性，避免被默认值覆盖
                                frequency = current?.frequency ?: "daily",
                                times = current?.times,
                                takeWithFood = current?.takeWithFood,
                                prescribed = current?.prescribed ?: false,
                                isArchived = current?.isArchived ?: false,
                                notes = notes.ifBlank { null },
                                // 非数字 / 空串一律落地为 null（不抛异常）；单位 trim 后空串同样为 null
                                doseAmount = doseAmount.trim().toDoubleOrNull(),
                                doseUnit = doseUnit.trim().ifBlank { null },
                                dailyMax = dailyMax.trim().toDoubleOrNull(),
                                createdAt = current?.createdAt ?: nowIso(),
                                updatedAt = nowIso(),
                            )
                        )
                        onDismiss()
                    }
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun DietSheet(vm: WellnessViewModel, current: DietProfile?, onDismiss: () -> Unit) {
    var pattern by remember { mutableStateOf(current?.dietPattern ?: "mixed") }
    var seafood by remember { mutableStateOf(current?.seafoodFreq ?: "rare") }
    var dairy by remember { mutableStateOf(current?.dairyTolerant ?: "yes") }
    var notes by remember { mutableStateOf(current?.notes ?: "") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        SheetColumn {
            Text(stringResource(R.string.nutrition_diet_profile), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.nutrition_diet_pattern), style = MaterialTheme.typography.labelMedium)
            ChipGroup(
                options = listOf(
                    "mixed" to stringResource(R.string.nutrition_diet_omnivore), "mediterranean" to stringResource(R.string.nutrition_diet_med), "vegetarian" to stringResource(R.string.nutrition_diet_vegetarian),
                    "vegan" to stringResource(R.string.nutrition_diet_vegan), "low_starch" to stringResource(R.string.nutrition_diet_low_starch), "paleo" to stringResource(R.string.nutrition_diet_paleo),
                ),
                selected = pattern,
                onSelect = { pattern = it },
            )
            Text(stringResource(R.string.nutrition_fish_frequency), style = MaterialTheme.typography.labelMedium)
            ChipGroup(
                options = listOf(
                    "never" to stringResource(R.string.nutrition_fish_never), "rare" to stringResource(R.string.nutrition_fish_occasionally), "weekly" to stringResource(R.string.med_freq_weekly), "frequent" to stringResource(R.string.nutrition_fish_often),
                ),
                selected = seafood,
                onSelect = { seafood = it },
            )
            Text(stringResource(R.string.nutrition_dairy_tolerance), style = MaterialTheme.typography.labelMedium)
            ChipGroup(
                options = listOf(
                    "yes" to stringResource(R.string.nutrition_tolerance_tag), "no" to stringResource(R.string.nutrition_intolerance), "lactose_free_only" to stringResource(R.string.nutrition_diet_lactose_free),
                ),
                selected = dairy,
                onSelect = { dairy = it },
            )
            OutlinedTextField(notes, { notes = it }, label = { Text(stringResource(R.string.common_notes_optional)) })
            SheetSaveButton(
                text = stringResource(R.string.common_save),
                onClick = {
                    vm.saveDietProfile(
                        DietProfile(
                            id = 1, dietPattern = pattern,
                            seafoodFreq = seafood, dairyTolerant = dairy,
                            notes = notes.ifBlank { null },
                            updatedAt = nowIso(),
                        )
                    )
                    onDismiss()
                },
            )
            if (current != null) {
                DestructiveAction(
                    label = stringResource(R.string.wellness_clear_diet_profile),
                    confirmTitle = stringResource(R.string.nutrition_delete_profile_confirm),
                    confirmBody = stringResource(R.string.nutrition_delete_profile_note),
                    onConfirm = {
                        vm.deleteDietProfile()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * 忌口管理：同一 sheet 内两步（列表 ⇄ 添加），不再 dialog 套 dialog。
 * 删除走 DestructiveAction 二次确认。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AvoidManageSheet(vm: WellnessViewModel, onDismiss: () -> Unit) {
    val items by vm.foodAvoidItems.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }
    // v1.0.80（批次 6）：编辑目标（非空时列表切到表单并回填原值）
    var editing by remember { mutableStateOf<FoodAvoidItem?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        val target = editing
        if (adding || target != null) {
            AvoidAddStep(
                current = target,
                onSave = { item ->
                    vm.saveFoodAvoid(item)
                    adding = false
                    editing = null
                },
                onBack = {
                    adding = false
                    editing = null
                },
            )
        } else {
            SheetColumn {
                Text(stringResource(R.string.nutrition_avoid_list), style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(R.string.wellness_avoid_section_count, items.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (items.isEmpty()) {
                    Text(
                        stringResource(R.string.nutrition_avoid_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    DividerList(items, key = { it.id }) { item ->
                        Column(
                            Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
                        ) {
                            Text(item.name, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "${Labels.foodAvoidCategory(item.category)} · ${Labels.foodAvoidSeverity(item.severity)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        // v1.0.80（批次 6）：改（回填原值）与删并排——忌口条目记错分类是常事
                        TextButton(
                            onClick = { editing = item },
                            modifier = Modifier.heightIn(min = Size.touchMin),
                        ) { Text(stringResource(R.string.common_edit)) }
                        DestructiveAction(
                            label = stringResource(R.string.common_delete),
                            confirmTitle = stringResource(R.string.wellness_delete_confirm, item.name),
                            confirmBody = stringResource(R.string.nutrition_avoid_delete_note),
                            onConfirm = { vm.deleteFoodAvoid(item.id) },
                        )
                    }
                }
                Button(
                    onClick = { adding = true },
                    modifier = Modifier.fillMaxWidth().heightIn(min = Size.touchMin),
                ) { Text(stringResource(R.string.nutrition_add_avoid_item)) }
            }
        }
    }
}

/**
 * 忌口条目表单（新增 / **编辑**共用）。
 *
 * v1.0.80（批次 6）：[current] 非空 = 编辑——回填原值并沿用主键，
 * 否则「改个分类」会变成「多一条同名忌口」。
 */
@Composable
private fun AvoidAddStep(current: FoodAvoidItem? = null, onSave: (FoodAvoidItem) -> Unit, onBack: () -> Unit) {
    var name by remember(current?.id) { mutableStateOf(current?.name ?: "") }
    var category by remember(current?.id) { mutableStateOf(current?.category ?: "allergy") }
    var severity by remember(current?.id) { mutableStateOf(current?.severity ?: "medium") }

    SheetColumn {
        Text(
            stringResource(if (current == null) R.string.nutrition_add_avoid_item else R.string.common_edit),
            style = MaterialTheme.typography.titleLarge,
        )
        OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.nutrition_food_name)) }, singleLine = true)
        Text(stringResource(R.string.common_category), style = MaterialTheme.typography.labelMedium)
        ChipGroup(
            options = listOf(
                "allergy" to stringResource(R.string.profile_allergy_short), "intolerance" to stringResource(R.string.nutrition_intolerance), "doctor_advice" to stringResource(R.string.checkup_doctor_advice),
                "personal_experience" to stringResource(R.string.knowledge_personal_experience_tag), "drug_interaction" to stringResource(R.string.knowledge_drug_conflict),
            ),
            selected = category,
            onSelect = { category = it },
        )
        Text(stringResource(R.string.symptom_severity), style = MaterialTheme.typography.labelMedium)
        ChipGroup(
            options = listOf("high" to stringResource(R.string.severity_high), "medium" to stringResource(R.string.severity_moderate), "low" to stringResource(R.string.severity_low)),
            selected = severity,
            onSelect = { severity = it },
        )
        SheetSaveButton(
            text = stringResource(if (current == null) R.string.common_add else R.string.common_save),
            enabled = name.isNotBlank(),
            onClick = {
                if (name.isNotBlank()) {
                    onSave(
                        FoodAvoidItem(
                            id = current?.id ?: "", name = name.trim(), category = category,
                            severity = severity, notes = current?.notes,
                            createdAt = current?.createdAt ?: nowIso(), updatedAt = nowIso(),
                        )
                    )
                }
            },
        )
        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.knowledge_back_to_list)) }
    }
}

/** sheet 内容列的统一骨架：横向留白 + 键盘避让 + 底部安全距离。 */
@Composable
private fun SheetColumn(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
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
private fun SheetSaveButton(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = Size.touchMin),
    ) { Text(text) }
}

/** 单选 chip 组：FlowRow 自动换行，触摸目标 ≥48dp，枚举 key 不出现在 UI。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipGroup(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
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

/** U4 体重记录管理：近 30 天逐条删除（误录）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WeightManageSheet(vm: WellnessViewModel, onDismiss: () -> Unit) {
    val weightList by vm.weightRecent.collectAsStateWithLifecycle()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        SheetColumn {
            Text(stringResource(R.string.wellness_weight_manage_title), style = MaterialTheme.typography.titleLarge)
            if (weightList.isEmpty()) {
                Text(
                    stringResource(R.string.common_no_records),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                DividerList(weightList, key = { it.id }) { log ->
                    Column(
                        Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
                    ) {
                        Text(
                            stringResource(R.string.wellness_weight_entry, log.date, "%.1f".format(log.weightKg)),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        log.notes?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    DestructiveAction(
                        label = stringResource(R.string.common_delete),
                        confirmTitle = stringResource(R.string.wellness_delete_weight_confirm, log.date),
                        confirmBody = stringResource(R.string.wellness_delete_weight_note),
                        onConfirm = { vm.deleteWeight(log.id) },
                    )
                }
            }
        }
    }
}

