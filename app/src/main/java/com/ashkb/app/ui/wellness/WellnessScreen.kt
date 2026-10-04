package com.ashkb.app.ui.wellness

import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.NoMeals
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Restaurant
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.ashkb.app.ui.theme.Size
import com.ashkb.app.ui.theme.Spacing
import com.ashkb.app.ui.theme.StatusTone

/**
 * 「营养与骨骼」主页（route `wellness`）。
 *
 * ### v1.0.91（批次 16）：按分区收口，根级 Flow 由 10 条降到 1 条
 *
 * **拆分前**：本屏在根级收集 10 条 Flow（`vitalsToday` / `weightToday` / `weightRecent` /
 * `profile` / `bodyMeasureLatest` / `supplements` / `supplementLogsToday` / `dietProfile` /
 * `foodAvoidItems` / `medications`）——**任何一条发射都会重跑整屏**：补一次体重，补剂列表、
 * 合并时间表、饮食画像、忌口清单全跟着重组；打卡一次补剂同理。这正是批次 12（TodayScreen 9→3）、
 * 批次 12（BackupScreen 19→3）修掉的同一类问题。
 *
 * **拆分后**：每条流的读点落在**渲染它的那个 section** 内（`collectAsStateWithLifecycle` 的
 * 快照读点在哪，重组就限定在哪）：
 *  · `vitalsToday` / `weightToday` / `weightRecent` / `bodyMeasureLatest` / `profile`
 *    → [vitalsSections] 里的三张卡（体征 hero / 体重卡 / 身体成分卡）；
 *  · `supplements` / `supplementLogsToday` / `medications` → [nutritionSection]（补剂档案卡 +
 *    合并时间表，两张卡共用同一份补剂/药单，故由同一个 section 收一次，不各收一份）；
 *  · `dietProfile` → 饮食画像卡与饮食弹层各自收（档案是只读流，各收一份不会出现「两个真相」）；
 *  · `foodAvoidItems` → 忌口清单卡与忌口管理弹层各自收。
 *
 * ### 为什么根级**一条 Flow 都不留**，而 TodayScreen 还留三条
 * TodayScreen 留 `profile` / `todayDate` / `items` 是因为三处 section 必须共用同一个值，
 * 尤其 `todayDate`（两处各读一次系统时钟会出现「两个今天」，批次 9 的缺陷）。
 * 本屏没有这种「必须共用同一份、且各收一份会漂移」的值：
 *  · 时间相关的判断（今日体征 / 今日体重 / 今日补剂记录）在 `WellnessViewModel` 里就按
 *    `dateProvider.today` 取好了窗口，UI 层不参与「今天」的判定；
 *  · `profile` 与 `dateProvider` 无关，是**只读**档案流，各 section 各收一份不存在语义漂移。
 *
 * ### 根级为什么**只**留这些 state
 *  · [WellnessSheetState]——「当前打开哪个弹层」。它是**跨 section 的胶水**：营养卡的开表单、
 *    体重卡的录入 / 管理、体征 hero 的编辑、饮食卡、忌口卡的入口都要写同一份可见性，
 *    而弹层本体在 [WellnessOverlays] 里渲染（`ModalBottomSheet` 是独立窗口，不能登记成列表 item——
 *    那样一旦滚出视口就会被回收、弹层跟着消失）。
 *  · 三个补剂目标（详情 / 编辑 / 待删）——【必须】留在根级：它们的**写入点是补剂行的回调**，
 *    而补剂行由 `LazyListScope` 扩展在**列表作用域**里登记。`LazyListScope.item { }` 的 lambda
 *    本身是组合上下文（`@Composable LazyItemScope.() -> Unit`），但那层作用域里能写的 state
 *    必须是**父级持有**的——section 里 `remember` 出来的东西，行回调根本拿不到。
 *    它们不是 Flow（不自己发射），留着只是三个 `MutableState` 槽位。
 *    三个目标与 [WellnessOverlays] 读的是**同一份引用**：写成两份 state 的话，点击只改一份、
 *    弹层读另一份，点了没反应（v1.0.81 的缺陷正是这个形状）。
 *
 * ### 技术边界（批次 16 实测复现，与批次 12 的记录一致）
 * 「既自己收 Flow、又往**宿主**列表发 item」的 section **不能**写成 `@Composable LazyListScope.` 扩展：
 * 在 `LazyColumn` 的内容 lambda 里调用它会报
 * `@Composable invocations can only happen from the context of a @Composable function`
 * （批次 16 用一个一次性编译探针把这条报错原样复现了一次；探针已删，结论记在这里）。
 * 因此本屏的分工是：
 *  · [vitalsSections] / [nutritionSection] / [dietProfileSection] / [avoidListSection] 都是
 *    **非** `@Composable` 的 `LazyListScope` 扩展——只登记 item，不碰 state；
 *  · 真正收 Flow 的是它们 `item { }` 里的**内容 composable**（如 [WeightCard]、[SupplementArchiveCard]），
 *    item 的内容 lambda 是组合上下文，`remember` / `collectAsStateWithLifecycle` 都能用。
 * 顺带说明一条**被本批证伪**的旧结论：`item { }` 的内容 lambda 里其实**可以**直接
 * `remember` / `collectAsStateWithLifecycle`（同一次探针编译通过）。
 * 但本屏不采用那种写法——它会让「谁收哪条流」散落在列表 lambda 里，与
 * [WellnessVitalsSections] 这类 section 文件的分工相冲突；批次的约定是
 * **一条流由渲染它的那个 section 收**，读代码的人在 section 文件里就能看全。
 *
 * ### 一条不能动的约束：detekt 基线按「文件 + 完整签名」记账
 * 本文件里 [VitalsHero] / [VitalsSheet] / [SupplementSheet] 三个函数在
 * `config/detekt/baseline.xml` 里有历史条目，ID 形如
 * `CyclomaticComplexMethod:WellnessScreen.kt$@Composable private fun VitalsHero(vitals: Vitals?, onEdit: () -> Unit)`。
 * 本批**不得新增基线**，所以这三个函数的名字、形参表与所在文件都保持原样——
 * 取数一律下移，摆放层不动。详见 [VitalsHero] 的注释。
 */
@Composable
fun WellnessScreen(vm: WellnessViewModel, onOpenRecipes: () -> Unit, onBack: () -> Unit) {
    // ---- 根级**仅存**的 state（无 Flow，理由见类注释）----
    val sheets = remember { mutableStateOf(WellnessSheetState()) }
    // 三个补剂目标：写入点在列表作用域里创建的回调上，故必须由根级持有
    val detailSup = remember { mutableStateOf<Supplement?>(null) }
    val editSup = remember { mutableStateOf<Supplement?>(null) }
    val deletingSup = remember { mutableStateOf<Supplement?>(null) }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(title = stringResource(R.string.nutrition_bone_health_title), onBack = onBack)

        WellnessSections(
            vm = vm,
            sheets = sheets,
            detailSup = detailSup,
            editSup = editSup,
            deletingSup = deletingSup,
            onOpenRecipes = onOpenRecipes,
        )
    }

    // ---- 弹层：全部在 `LazyColumn` 之外渲染（与拆分前位置一致）----
    // 为什么不像其它 section 那样抽成独立文件里的宿主函数：这七张表单 composable 都是本文件的
    // `private`，而它们的签名又被 `config/detekt/baseline.xml` 按「文件 + 完整签名」钉死
    // （见 [VitalsHero] 的说明），两边都动不了——所以这 10 行 if 就留在这里，
    // 它们是**条件组合**，只在开关真正翻转时才执行。
    val sheetState = sheets.value
    if (sheetState.vitals) VitalsSheet(vm = vm, onDismiss = { sheets.value = sheetState.copy(vitals = false) })
    if (sheetState.weight) WeightSheet(vm = vm, onDismiss = { sheets.value = sheetState.copy(weight = false) })
    if (sheetState.weightManage) WeightManageSheet(vm = vm, onDismiss = { sheets.value = sheetState.copy(weightManage = false) })
    if (sheetState.bodyMeasure) BodyMeasureSheet(vm = vm, onDismiss = { sheets.value = sheetState.copy(bodyMeasure = false) })
    if (sheetState.supplementForm) SupplementSheet(vm = vm, onDismiss = { sheets.value = sheetState.copy(supplementForm = false) })
    if (sheetState.dietForm) DietSheet(vm = vm, onDismiss = { sheets.value = sheetState.copy(dietForm = false) })
    if (sheetState.avoidManage) AvoidManageSheet(vm = vm, onDismiss = { sheets.value = sheetState.copy(avoidManage = false) })
    detailSup.value?.let { sup -> SupplementDetailSheet(vm = vm, sup = sup, onDismiss = { detailSup.value = null }) }
    editSup.value?.let { sup -> SupplementSheet(vm = vm, current = sup, onDismiss = { editSup.value = null }) }
    deletingSup.value?.let { sup -> SupplementDeleteDialog(vm = vm, sup = sup, onDismiss = { deletingSup.value = null }) }
}

/**
 * 本屏的分区骨架：一个 `LazyColumn`，把各 section 登记的 item 串起来。
 *
 * v1.0.91（批次 16）从 [WellnessScreen] 抽成独立 composable：根级正文因此只剩
 * 「顶部栏 + 列表 + 弹层」，各分区的槽位登记与取数都进了对应 section 文件。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun WellnessSections(
    vm: WellnessViewModel,
    sheets: MutableState<WellnessSheetState>,
    detailSup: MutableState<Supplement?>,
    editSup: MutableState<Supplement?>,
    deletingSup: MutableState<Supplement?>,
    onOpenRecipes: () -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Spacing.lg, end = Spacing.lg,
            top = Spacing.md, bottom = Spacing.xxl,
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        // ---- 分组一 / 二：体征、身体成分（各自的卡片自己收 Flow，见 WellnessVitalsSections.kt）----
        vitalsSections(vm = vm, sheets = sheets)

        // ---- 分组三：营养与饮食 ----
        // stickyHeader（{ }）与批次 14 之前逐字一致：吸附头不是普通 item，不能直调 composable
        stickyHeader { WellnessGroupHeader(stringResource(R.string.wellness_nutrition_section)) }
        // B3：推荐食谱库入口——放在饮食分组首位，与下方饮食画像 / 忌口清单同属「吃什么」的决策链
        item {
            NavRow(
                icon = Icons.Rounded.Restaurant,
                title = stringResource(R.string.recipes_title),
                subtitle = stringResource(R.string.recipes_entry_sub),
                onClick = onOpenRecipes,
            )
        }
        // 补剂档案卡 + 合并时间表（两张卡各自收自己渲染的流）
        nutritionSection(vm = vm, sheets = sheets, detailSup = detailSup, editSup = editSup, deletingSup = deletingSup)
        dietProfileSection(vm = vm, sheets = sheets)
        avoidListSection(vm = vm, sheets = sheets)
    }
}

/**
 * 分组头：sticky。吸附时用页面底色融入背景，底边 1dp 分隔。
 *
 * v1.0.91（批次 16）：从 `private` 提为 `internal`——体征分组的两个吸附头在
 * `WellnessVitalsSections.kt` 里，需要一个同包可见的分组头实现（原先它是
 * `WellnessScreen.kt` 的私有函数，拆文件后就够不着了）。
 */
@Composable
internal fun WellnessGroupHeader(text: String) {
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

/**
 * 体征 section 的**取数层**：自己收 `vitalsToday`，再把值交给摆放层 [VitalsHero]。
 *
 * 为什么拆成两层（取数 + 摆放）而不是让 [VitalsHero] 自己收：
 * `VitalsHero` 带 `<ID>CyclomaticComplexMethod:WellnessScreen.kt$@Composable private fun VitalsHero(vitals: Vitals?, onEdit: () -> Unit)</ID>`
 * 这条基线——ID 里写死了**文件 + 完整签名 + `private`**。本批不得新增基线，所以它的名字、形参表、
 * 所在文件与可见性都得逐字不变（改 `internal` 也会失配，实测被 detekt 抓过）。
 * 于是取数放在这里（本 item 的重组作用域内），摆放仍是原来那个函数。
 */
@Composable
internal fun VitalsSection(vm: WellnessViewModel, sheets: MutableState<WellnessSheetState>) {
    val vitals by vm.vitalsToday.collectAsStateWithLifecycle()
    VitalsHero(vitals = vitals, onEdit = { sheets.value = sheets.value.copy(vitals = true) })
}

/**
 * 今日体征 hero：体温 / 血压 / 心率三格 StatTile，异常格按 ClinicalThresholds 着色。
 *
 * v1.0.91（批次 16）：**取数已下移到** [VitalsSection]（体征变化只重组那一块 item）。
 * 本函数只负责摆放，签名、所在文件与 `private` 与拆分前逐字一致。
 *
 * ⚠️ **名字 / 形参表 / 文件 / 可见性都不能动**：`config/detekt/baseline.xml` 里那条
 * `<ID>CyclomaticComplexMethod:WellnessScreen.kt$@Composable private fun VitalsHero(vitals: Vitals?, onEdit: () -> Unit)</ID>`
 * 把「文件 + 完整签名」写死在 ID 里（复杂度 24，阈值 15）。加一个 `vm` 形参、搬去别的文件、
 * 甚至只是把 `private` 改成 `internal`，都会让条目失配、detekt 立刻报新问题——
 * 而本批的约束是**不得新增基线**。[VitalsSheet] / [SupplementSheet] 同理（它们也必须继续拿 `vm`）。
 */
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

/**
 * 营养分组的**两张内容卡**的槽位登记：补剂档案卡 + 今日服药 / 补剂合并时间表。
 *
 * ### 为什么是**两个** item，而不是塞进一个内容 composable
 * 每张卡必须是宿主 `LazyColumn` 的一个 item：item 之间的 `Spacing.md` 由宿主的
 * `verticalArrangement` 给，item 也只有各自独立才能被分别虚拟化。把两张卡塞进同一个
 * `item { Column { … } }` 会同时丢掉这两样（卡片间距要手写、两张卡永远一起组合）。
 *
 * ### 为什么 `supplements` 被收了两次（有意为之）
 * 合并时间表要展平的 `times` 与档案卡渲染的列表**是同一份补剂数据**，理论上可以由一个
 * `@Composable` 收一次再分给两张卡——但那要求把两张卡放进同一个 item（见上，代价更大）。
 * 各收一份的后果只是**多一个订阅**：两条都读同一个 `WellnessViewModel.supplements`
 * （`SharingStarted.WhileSubscribed` 的 `StateFlow`），值必然一致，不存在「两个真相」；
 * `collectAsStateWithLifecycle` 这种重复订阅在本项目里本来就随处可见（例如 `profile` 在
 * 本屏被 2 个 section、2 个弹层各收一份）。真正要避免的是「整屏根部收一次」——
 * 那才是每次发射都重组全屏的原因。
 *
 * ### 技术边界（批次 12 / 16 实测）
 * 本函数**不是** `@Composable`（它是 `LazyListScope` 扩展，只登记 item），
 * 所以它不能调用 `collectAsStateWithLifecycle`——真正收 Flow 的是两个 item 的**内容 composable**。
 */
@OptIn(ExperimentalFoundationApi::class)
private fun LazyListScope.nutritionSection(
    vm: WellnessViewModel,
    sheets: MutableState<WellnessSheetState>,
    detailSup: MutableState<Supplement?>,
    editSup: MutableState<Supplement?>,
    deletingSup: MutableState<Supplement?>,
) {
    item {
        SupplementArchiveCard(
            vm = vm,
            onAdd = { sheets.value = sheets.value.copy(supplementForm = true) },
            onOpenDetail = { detailSup.value = it },
            onEdit = { editSup.value = it },
            onDelete = { deletingSup.value = it },
        )
    }
    item { MergedTimelineCard(vm = vm) }
}

/**
 * 补剂档案卡：列表 + 空态 + 错开提醒 + 历史提示。
 *
 * v1.0.91（批次 16）：`supplements` / `supplementLogsToday` / `medications` 三条流改由本卡自收。
 * `medications` 在这里只服务「矿物类补剂与螯合类用药错开提醒」，合并时间表另收一份（同款说明见
 * [nutritionSection]）。打卡一次补剂只会重组这一张卡，不再连带整屏。
 *
 * 三个档案类动作按 `(Supplement) -> Unit` 收：卡片**不持有**那三个目标（它们的写入点必须与
 * 弹层同一份 state，见 [WellnessScreen] 类注释），只把「这一行的 sup」交回根部。
 */
@Composable
private fun SupplementArchiveCard(
    vm: WellnessViewModel,
    onAdd: () -> Unit,
    onOpenDetail: (Supplement) -> Unit,
    onEdit: (Supplement) -> Unit,
    onDelete: (Supplement) -> Unit,
) {
    val supplements by vm.supplements.collectAsStateWithLifecycle()
    val supLogs by vm.supplementLogsToday.collectAsStateWithLifecycle()
    // v1.0.38（B11）：在用药单——补剂错开提醒与合并时间表的数据源
    val medications by vm.medications.collectAsStateWithLifecycle()

    SectionCard(
        title = stringResource(R.string.nutrition_supplement_archive),
        subtitle = stringResource(R.string.wellness_supplements_count, supplements.size),
        action = {
            OutlinedButton(onClick = onAdd) { Text(stringResource(R.string.common_add)) }
        },
    ) {
        if (supplements.isEmpty()) {
            EmptyState(
                icon = Icons.Rounded.Medication,
                title = stringResource(R.string.nutrition_no_supplements),
                body = stringResource(R.string.nutrition_supplement_hint),
                actionLabel = stringResource(R.string.nutrition_add_supplement),
                onAction = onAdd,
            )
        } else {
            DividerList(supplements, key = { it.id }) { sup ->
                SupplementCardRow(
                    sup = sup,
                    // 打卡态集合一次性算好：避免每行对全部 supLogs 做 O(N·M) 线性扫描
                    todayStatus = SupplementLogStatus.loggedToday(supLogs, sup.id),
                    // 三个「档案类动作」：把这一行的 sup 交回根部持有的目标
                    // （与拆分前 `SupplementRowActions(onOpenDetail = { detailSup = sup }, …)` 等价）
                    actions = SupplementRowActions(
                        onOpenDetail = { onOpenDetail(sup) },
                        onEdit = { onEdit(sup) },
                        onDelete = { onDelete(sup) },
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

/**
 * B11：今日服药 / 补剂合并时间表。
 *
 * v1.0.91（批次 16）：`medications` / `supplements` 改由本卡自收——它只在这张表里被展平，
 * 不需要根部代收（同一条流被档案卡再收一份的取舍见 [nutritionSection]）。
 */
@Composable
private fun MergedTimelineCard(vm: WellnessViewModel) {
    val medications by vm.medications.collectAsStateWithLifecycle()
    val supplements by vm.supplements.collectAsStateWithLifecycle()

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

/** 饮食画像卡：有画像给三项 KeyValue，无画像给空态与设置入口。 */
@Composable
private fun DietProfileCard(vm: WellnessViewModel, onEdit: () -> Unit) {
    val diet by vm.dietProfile.collectAsStateWithLifecycle()

    SectionCard(
        title = stringResource(R.string.nutrition_diet_profile),
        action = {
            if (diet != null) {
                OutlinedButton(onClick = onEdit) { Text(stringResource(R.string.common_edit)) }
            }
        },
    ) {
        val d = diet
        if (d != null) {
            KeyValueRow(stringResource(R.string.nutrition_diet_pattern), Labels.dietPattern(d.dietPattern))
            KeyValueRow(stringResource(R.string.nutrition_fish_intake), Labels.seafoodFreq(d.seafoodFreq))
            KeyValueRow(stringResource(R.string.nutrition_dairy), Labels.dairyTolerance(d.dairyTolerant))
        } else {
            EmptyState(
                icon = Icons.Rounded.Restaurant,
                title = stringResource(R.string.nutrition_profile_empty),
                body = stringResource(R.string.nutrition_profile_benefit),
                actionLabel = stringResource(R.string.nutrition_set_profile),
                onAction = onEdit,
            )
        }
    }
}

/**
 * 忌口清单卡：默认最多展示 5 条，**但高危项一条都不截**（见 [prioritizeHigh]），超出给「查看全部」。
 *
 * v1.0.91（批次 16）：`foodAvoidItems` 改由本卡自收（原读点在根部）。
 */
@Composable
private fun AvoidListCard(vm: WellnessViewModel, onManage: () -> Unit) {
    val avoids by vm.foodAvoidItems.collectAsStateWithLifecycle()
    // 预览集合按「高危优先 + 其余补足」算一次：avoids 每条增删改都会换新列表，remember 的 key 就是它
    val preview = remember(avoids) { prioritizeHigh(avoids, AVOID_PREVIEW_LIMIT) }

    SectionCard(
        title = stringResource(R.string.nutrition_avoid_list),
        subtitle = stringResource(R.string.wellness_avoid_count, avoids.size),
        action = {
            OutlinedButton(onClick = onManage) { Text(stringResource(R.string.common_manage)) }
        },
    ) {
        if (avoids.isEmpty()) {
            EmptyState(
                icon = Icons.Rounded.NoMeals,
                title = stringResource(R.string.nutrition_no_avoid_items),
                body = stringResource(R.string.nutrition_avoid_hint),
                actionLabel = stringResource(R.string.nutrition_add_avoid_item),
                onAction = onManage,
            )
        } else {
            DividerList(preview) { item ->
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
            // 有条目真的被藏起来时才给这个入口（v1.1.2：高危项超限时仍会藏「其余」那条）
            if (avoids.size > preview.size) {
                TextButton(onClick = onManage) {
                    Text(stringResource(R.string.wellness_view_all_avoids, avoids.size))
                }
            }
        }
    }
}

/** 忌口卡的预览条数：卡片的**信息意图**是「摘要 + 全部入口」，不是把整份清单铺开。 */
private const val AVOID_PREVIEW_LIMIT = 5

/**
 * v1.1.2：忌口清单卡的预览集合——**高危（`high`）条目一条都不许被截掉**。
 *
 * ### 为什么这层不能只靠 DAO 排序
 * 批次 18 已经把 `FoodAvoidItemDao.observeAll` 的 `ORDER BY CASE` 修成「高危在前」，
 * 看上去 `take(5)` 已经够了。但那是**两层之外的 SQL 排序**：展示层的安全性质
 * （「高危项不会被这一屏藏起来」）挂在一个改查询就会静默失效的隐式约定上——
 * 任何人把排序调回去、或换个未排序的集合喂进来，症状是**过敏/禁忌这种危险信息凭空消失**，
 * 而且不报任何错。安全性质必须落在**渲染它的那一层**。
 *
 * ### 为什么不干脆全量展示
 * 忌口是**用户自己攒**的、条数无上界；全量铺开会让这张卡在长列表里长到需要滚动，
 * 把同屏的体征 / 补剂内容挤下去。所以保留「只展示前 N 条」的意图，只把**截断规则**换成
 * 「高危全给 + 其余按原顺序补足到 N」：卡片长度仍然有上界（`高危数 + N`），
 * 而**最该被看到的信息永远在最上面、且一条不少**。
 *
 * 全部条目另有出口（卡片右上「管理」与这条「查看全部 N 项」都进同一张管理弹层），
 * 所以被折叠的只可能是「非高危的补充条目」，不是「看不到」的信息。
 */
internal fun prioritizeHigh(items: List<FoodAvoidItem>, limit: Int): List<FoodAvoidItem> {
    if (items.size <= limit) return items
    // partition 保序：两段内部都维持传入顺序，所以「其余」仍是原顺序（不是重排过的）
    val (high, others) = items.partition { it.severity == "high" }
    return (high + others).take(maxOf(limit, high.size))
}

/** 饮食画像卡的槽位登记（内容见 [DietProfileCard]）。 */
@OptIn(ExperimentalFoundationApi::class)
private fun LazyListScope.dietProfileSection(vm: WellnessViewModel, sheets: MutableState<WellnessSheetState>) {
    item { DietProfileCard(vm = vm, onEdit = { sheets.value = sheets.value.copy(dietForm = true) }) }
}

/** 忌口清单卡的槽位登记（内容见 [AvoidListCard]）。 */
@OptIn(ExperimentalFoundationApi::class)
private fun LazyListScope.avoidListSection(vm: WellnessViewModel, sheets: MutableState<WellnessSheetState>) {
    item { AvoidListCard(vm = vm, onManage = { sheets.value = sheets.value.copy(avoidManage = true) }) }
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
 * v1.0.87（批次 12）：补剂档案列表的一行——上方补剂信息（点开详情），下方动作区。
 *
 * v1.0.89（批次 14 布局回归修复）：动作整体移到名称的**下一行**。
 *
 * 为什么不能再挂在 `DividerList` 的 `RowScope` 上：`itemContent` 是 `RowScope.(T) -> Unit`，
 * 函数体里的每一块都是那个横向 Row 的**直接子项**，于是名称列与「打卡 / 跳过 / 撤销 / 编辑 / 删除」
 * 抢同一行的宽度。四个 TextButton 的固有宽度（文字 + 12dp×2 内边距，下限 58dp）合计 ≈244dp，
 * 360dp 屏上卡片内宽只剩 ≈14dp 给名称——维护者真机看到的就是「名称被挤没、只剩四个按钮」。
 * 根节点改成 Column 后，内容列与动作行各自独占一整行宽度，名称不再与按钮争宽。
 *
 * 动作行用 `FlowRow` 而非 `Row`：最坏组合（状态胶囊 + 撤销打卡 + 编辑 + 删除）≈282dp，
 * 320dp 宽的机型（卡片内 ≈256dp）或大字号下仍放不下——`Row` 会把末尾按钮截到屏幕外，
 * `FlowRow` 则整体折到下一行（与本文件 `ChipGroup` 同一成例）。
 * 每个动作都带 `heightIn(min = Size.touchMin)`：既守住触达区下限，又让各项等高，
 * 折行时不会出现「胶囊与按钮各吊各的」错位。
 *
 * 「跳过」不弹任何表单、不要求填理由：补剂不是处方药，没有「为什么没吃」这一问
 * （药品那套 `SkipReason` 是给漏服追责用的，照搬到补剂只是多一步无用输入）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SupplementCardRow(
    sup: Supplement,
    todayStatus: String?,
    actions: SupplementRowActions,
    onCheckIn: () -> Unit,
    onSkip: () -> Unit,
    onUndo: () -> Unit,
) {
    // v1.0.89（批次 14）：根是纵向 Column——本函数已不再挂在 DividerList 的 RowScope 上，
    // 所以这里可以放心 fillMaxWidth()（此前必须是 weight(1f)，否则名称会把按钮挤没）。
    Column(Modifier.fillMaxWidth()) {
        Column(
            // v1.0.81（批次 7）：点整行打开「补剂详情」（最近服用记录 + 逐条删除）。卡片状态由
            // WellnessScreen 的 detailSup 持有，这里只回调——写成两份 state 的话，点击只改一份、
            // 弹层读另一份，点了没反应。
            // v1.0.88（批次 12 回归修复）：这里必须是 weight(1f) 而不是 fillMaxWidth()。
            // DividerList 的 itemContent 是 RowScope——fillMaxWidth() 会把整行宽度吃光，
            // 后面的「打卡/跳过/撤销/编辑/删除」全被挤成零宽（维护者真机看到卡片只剩名称，
            // 按钮全部消失）。weight(1f) 既保留"点整行开详情"的触达区，又给按钮留下自己的宽度。
            // v1.0.89（批次 14）：根改成 Column 后，本列是这一行唯一的子项，fillMaxWidth() 不再
            // 与任何兄弟争宽——它只吃自己这一行，顺带把「点整行开详情」的触达区还给了整行。
            Modifier.fillMaxWidth().clickable { actions.onOpenDetail() },
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
        // 动作行：独占名称下方的一行。用 FlowRow 而不是 Row——见上方 KDoc 的宽度推算，
        // 窄屏 / 大字号下它会整体折行，而 Row 只会把末尾按钮挤出屏幕。
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
        ) {
            when (todayStatus) {
                // 已服 / 跳过都由 SupplementLogStatus 映射（文案与色调与详情弹层同源）；
                // 跳过用**中性色**：与药品卡（`medStatusOf`）一致——不是错误，不该报警
                AdherenceCalc.SKIPPED -> SupplementStatusSlot(
                    status = todayStatus,
                    icon = Icons.Rounded.RemoveCircleOutline,
                )
                // v1.0.89（批次 14）：两个按钮直接作为 FlowRow 的项（不再套一层 Row），
                // 窄屏上可以各自折行，而不是被当成一个整体一起挤出去。
                null -> {
                    TextButton(onClick = onCheckIn, modifier = Modifier.heightIn(min = Size.touchMin)) {
                        Text(stringResource(R.string.exercise_checkin_short))
                    }
                    TextButton(onClick = onSkip, modifier = Modifier.heightIn(min = Size.touchMin)) {
                        Text(stringResource(R.string.med_skip))
                    }
                }
                else -> SupplementStatusSlot(status = todayStatus, icon = Icons.Rounded.CheckCircle)
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
                    // v1.0.89（批次 14）：与其它动作等高，折行时才不会与胶囊错位
                    modifier = Modifier.heightIn(min = Size.touchMin),
                )
            }
            // v1.0.89（批次 14）：此前「编辑」没有高度下限（TextButton 默认 40dp，低于触达区
            // 下限 48dp），补齐后动作行各项等高。
            TextButton(onClick = actions.onEdit, modifier = Modifier.heightIn(min = Size.touchMin)) {
                Text(stringResource(R.string.common_edit))
            }
            // v1.0.81（批次 7）：这里的删除 = **删掉整个补剂条目**（连带它名下的服用记录，
            // 条数由确认框异步取并报出，见 SupplementDeleteDialog）。只想删某一天的记录时，
            // 走「点击补剂」打开的详情弹层——两种语义此前共用一句「删除…？」，
            // 而确认框里那句「已产生的服用记录仍保留」更是与实情相反，正是用户困惑的根源。
            TextButton(
                onClick = actions.onDelete,
                modifier = Modifier.heightIn(min = Size.touchMin),
            ) { Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error) }
        }
    }
}

/**
 * v1.0.89（批次 14）：动作行里的状态胶囊槽。
 *
 * `StatusChip` 不收 modifier（自身钉了 28dp 下限），直接放进一排 48dp 高的按钮里会顶在上沿；
 * 套一个等高槽并居中后两者等高——换不换行都不会出现「胶囊吊在按钮上方」的错位。
 */
@Composable
private fun SupplementStatusSlot(status: String, icon: ImageVector) {
    Box(
        modifier = Modifier.heightIn(min = Size.touchMin),
        contentAlignment = Alignment.Center,
    ) {
        StatusChip(
            text = stringResource(SupplementLogStatus.labelRes(status)),
            tone = SupplementLogStatus.tone(status),
            icon = icon,
        )
    }
}

/** 数值显示：整数不带小数点，其余一位小数（补剂剂量与体重提示共用）。 */
internal fun fmtNum(v: Double): String = if (v == v.toLong().toDouble()) v.toLong().toString() else "%.1f".format(v)

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

// ---------------------------------------------------------------------------
// 表单：多字段一律 ModalBottomSheet（半屏可拖、键盘弹起体验远好于 AlertDialog）
//
// v1.0.91（批次 16）说明：以下弹层 composable **留在本文件**，不搬进
// `WellnessOverlays.kt`。原因是它们背着 `config/detekt/baseline.xml` 里按
// 「文件名 + 函数签名」登记的历史条目（`VitalsSheet` / `SupplementSheet` 的
// `CyclomaticComplexMethod`、若干行超 140 字符的 `MaxLineLength`）——换个文件那些条目
// 就会失配、detekt 立刻报新问题，而本批的约束是**不得新增基线**。
// 因此本轮只搬「Flow 收在哪」，不搬这些已经带着历史账的 composable；
// 它们本身都是**条件组合**的（`if (sheets.xxx) …`），不参与本屏的重组范围。
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
private fun DietSheet(vm: WellnessViewModel, onDismiss: () -> Unit) {
    // 「当前是否已有画像」决定要不要显示「清除画像」：由本弹层自己收，不必根部代收
    // （批次 16 之前 diet 收在根部，只为了让根级 `if (showDietForm) DietSheet(current = diet)` 传参）
    val diet by vm.dietProfile.collectAsStateWithLifecycle()
    val current = diet

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
