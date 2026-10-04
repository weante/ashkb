package com.ashkb.app.ui.me

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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.ashkb.app.domain.DrugInteractionKeys
import com.ashkb.app.domain.ScheduleCalc
import com.ashkb.app.ui.components.DateFieldRules
import com.ashkb.app.ui.components.ScreenTopBar
import com.ashkb.app.ui.theme.Size
import com.ashkb.app.ui.theme.Spacing
import java.time.LocalDate
import kotlinx.coroutines.launch
import org.json.JSONArray

/**
 * M1 添加 / 编辑药品：两步流程（route `meds/edit`）。
 *
 * 原为 15 字段的两步 `AlertDialog`，弹窗里必然局促且无法保存草稿 —— 改为全屏表单：
 * 顶栏承载步骤标题与返回，底部固定操作条，内容区整屏滚动。
 * v1.0.31：[editId] 非空 = 编辑既有在用药品（预填全部参数，保留 id / createdAt / 核对记录）。
 *
 * ### v1.0.92（批次 17）：按字段组拆 section，敲键只重组那个字段所在的 section
 *
 * **拆分前**：24 个 state 全挂在根级，而且每个字段都在**根级正文**里被读一次
 * （`OutlinedTextField(name, { name = it }, …)`：实参在根级求值）→ 敲一个键就把整个表单
 * 从头重组一遍。这是本屏最痛的地方，也是它排进本批的原因。
 *
 * **拆分后**：字段 state 仍在根级**声明**，但一个都不在根级正文里读——每个 section 收
 * `MutableState`，在**自己的组合作用域**里 `.value`。重组范围由快照读点决定，读点在哪、
 * 重组就限定在哪，于是：敲「药名」只重组基础信息 section；敲「存放」只重组存放 section；
 * 底部操作条只在它自己依赖的字段（step / editMissing / route / frequency / startDate）变化时
 * 重组（`Scaffold` 的 topBar / bottomBar / content 是三个独立作用域）。各 section 见
 * `MedEditSections.kt`。
 *
 * ### 为什么字段 state 仍在根级（而不是搬进 section 里 remember）
 * ① **保存路径读全表单**：`buildMed()` 在底部按钮的点击回调里读全部 20 个字段。回调不是
 *    组合上下文，读点天然是"延迟"的；但那些 state 必须由根级持有，回调才够得着。
 * ② **编辑模式的一次性预填要写同一份 state**：`LaunchedEffect(editId)` 按 id 直查后写 20 个
 *    字段（v1.0.43 的缺陷正是这条链断了：编辑被静默变成新建）。state 搬进 section 就成了两份，
 *    预填只写得进其中一份；更硬的一条是 `cycleDays` / `startDate` 这类**字段不可见时仍会落库**
 *    的值（`buildMed` 里 `injCycleDays` 只看 `route`，不看字段可不可见）——state 跟着可见性
 *    分支一起消失，等于在隐藏期间悄悄改值。
 * ③ 这些字段是 `rememberSaveable`：转屏 / 进程死亡后的恢复语义与拆分前**逐字相同**——每个字段
 *    仍是自己的一个 Bundle 槽，没有引入新的 `Saver`（本模块**无 Compose UI 测试依赖**，
 *    新 Saver 一旦把字段顺序或键写错，只有在真机转屏时才看得出来，静态测试抓不到）。
 *
 * ### 根级**读点**：0
 * 本函数正文里不再出现任何字段的读（`step` / `editMissing` 也只在 topBar / bottomBar /
 * content 三个子作用域里读）。`cycleAnchorVisible` 与 `DateFieldRules.requiredOk(startDate)`
 * 原本是根级正文里的两个 `val`——正是它们让"敲锚点日期重组整屏"，现在改在**使用它们的
 * 作用域**里求值（见 [MedScheduleSection] 与底部操作条）。
 *
 * ### detekt 基线的硬边界
 * 本函数在 `config/detekt/baseline.xml` 里有 `LongMethod` / `CyclomaticComplexMethod` 两条历史
 * 条目，ID 是「文件 + 完整签名」（连 `@OptIn` 一起）。故本批**签名与注解逐字未动**，也不在这里
 * 使用任何需要新增 `@OptIn` 的 API（`FlowRow` 之类都进了 section 文件）。
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

    // ---- 根级 state（24 个槽位，读点全在 section / 子作用域里；声明顺序与拆分前逐字一致，
    //      `rememberSaveable` 的位置键因此不变）----
    val step = rememberSaveable { mutableStateOf(1) }

    // ---- 第一步字段 ----
    val name = rememberSaveable { mutableStateOf("") }
    val brand = rememberSaveable { mutableStateOf("") }
    val nameKey = rememberSaveable { mutableStateOf("") }
    val medClass = rememberSaveable { mutableStateOf(MedClass.OTHER) }
    val route = rememberSaveable { mutableStateOf("oral") }
    val dose = rememberSaveable { mutableStateOf("") }
    val frequency = rememberSaveable { mutableStateOf(MedFrequency.DAILY) }
    val times = rememberSaveable { mutableStateOf(listOf(ScheduleCalc.DEFAULT_PLAN_TIME)) }
    val weekday = rememberSaveable { mutableStateOf(1) }
    val weekday2 = rememberSaveable { mutableStateOf(4) }
    val biwError = rememberSaveable { mutableStateOf(false) }
    // v1.1.2（批次 18）：甲氨蝶呤选到非每周频次时的强提示（与 biwError 同款「提交时才亮」的错误位）
    val mtxFrequencyError = rememberSaveable { mutableStateOf(false) }
    val cycleDays = rememberSaveable { mutableStateOf("14") }
    val food = rememberSaveable { mutableStateOf("any") }
    val prnReason = rememberSaveable { mutableStateOf("") }
    val storage = rememberSaveable { mutableStateOf("") }
    val startDate = rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    // v1.0.78（批次 4 收尾）：注射周期锚点日期此前是自由文本、**无任何校验**——
    // `2026-13-45` 一旦落库，「每 N 天」的周期推算会整体错位，且没有任何地方会报错。
    // 「可见 == 校验生效」的那条判定收在 [cycleAnchorVisible] 一处（见 MedEditSections.kt）。
    // ---- C6 服药三态：固定 / 按需 / 减量中（默认固定，编辑时按原值预填） ----
    val doseState = rememberSaveable { mutableStateOf(DoseState.FIXED) }
    val taperNote = rememberSaveable { mutableStateOf("") }

    // ---- 第二步 R03 ----
    val hits = remember { mutableStateOf<List<KbEntry>?>(null) }
    val doctorTold = rememberSaveable { mutableStateOf(false) }
    val leafletRead = rememberSaveable { mutableStateOf(false) }

    val prnFallback = stringResource(R.string.med_reason_backup)

    // ---- 编辑模式：预填在用药品参数（仅按 editId 跑一次，不覆盖用户后续输入） ----
    // v1.0.43 修复：原先在 vm.meds（**仅「在用」列表**）上 `first { 含该 id }`——目标药若已停用、
    // 或恢复备份后 id 漂移，谓词永不为真 → 协程永久挂起 → original 恒为 null → 保存时
    // id = Ids.new("med")，把「编辑」静默变成**新建一条重复药**，并丢掉病史核对记录。
    // 改为按 id 一次性直查（不限在用）；查不到则明确提示并禁止保存，绝不静默新建。
    val original = remember { mutableStateOf<Medication?>(null) }
    val editMissing = rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(editId) {
        if (editId == null) return@LaunchedEffect
        val med = vm.medicationById(editId)
        if (med == null) {
            editMissing.value = true
            return@LaunchedEffect
        }
        original.value = med
        name.value = med.name
        brand.value = med.brandName ?: ""
        nameKey.value = med.nameKey
        medClass.value = MedClass.fromKey(med.medClass)
        route.value = med.route
        dose.value = med.dose
        frequency.value = MedFrequency.fromKey(med.frequency)
        times.value = ScheduleCalc.takeTimesOf(med).ifEmpty { listOf(ScheduleCalc.DEFAULT_PLAN_TIME) }
        weekday.value = med.weeklyWeekday ?: 1
        weekday2.value = med.weeklyWeekday2 ?: 4
        cycleDays.value = med.injCycleDays?.toString() ?: "14"
        food.value = med.takeWithFood ?: "any"
        prnReason.value = med.prnReason ?: ""
        storage.value = med.storage ?: ""
        startDate.value = med.startDate
        // C6：未显式设置（旧数据）时按 frequency 推断
        doseState.value = DoseState.of(med)
        taperNote.value = med.taperNote ?: ""
        doctorTold.value = med.checkDoctorTold
        leafletRead.value = med.checkLeafletRead
    }

    fun buildMed(): Medication = Medication(
        // 编辑模式保留身份与创建时间（updatedAt 由仓库层 upsert 刷新）；新增模式维持原逻辑
        id = original.value?.id ?: Ids.new("med"),
        name = name.value.trim(),
        brandName = brand.value.trim().ifBlank { null },
        nameKey = nameKey.value.trim().lowercase(),
        medClass = medClass.value.name,
        route = route.value,
        dose = dose.value.trim(),
        frequency = frequency.value.name,
        prnReason = if (frequency.value == MedFrequency.PRN) prnReason.value.trim().ifBlank { prnFallback } else null,
        takeTimes = if (frequency.value == MedFrequency.PRN || route.value == "injection") {
            times.value.take(1).let { if (it.isEmpty()) null else JSONArray(it).toString() }
        } else {
            JSONArray(times.value).toString()
        },
        weeklyWeekday = if (frequency.value == MedFrequency.WEEKLY || frequency.value == MedFrequency.BIW) {
            weekday.value
        } else {
            null
        },
        weeklyWeekday2 = if (frequency.value == MedFrequency.BIW) weekday2.value else null,
        startDate = DateFieldRules.toIsoOrNull(startDate.value) ?: startDate.value,
        injCycleDays = if (route.value == "injection") cycleDays.value.toIntOrNull() ?: 14 else null,
        storage = storage.value.trim().ifBlank { null },
        takeWithFood = if (route.value == "oral") food.value else null,
        // C6：服药状态；减量备注仅在「减量中」时落库（切回固定 / 按需即清空，避免残留脏备注）
        doseState = doseState.value.name,
        taperNote = if (doseState.value == DoseState.TAPERING) taperNote.value.trim().ifBlank { null } else null,
        checkDoctorTold = doctorTold.value,
        checkLeafletRead = leafletRead.value,
        interactionCheckDate = original.value?.interactionCheckDate
            ?: if (step.value >= 2) LocalDate.now().toString() else null,
        createdAt = original.value?.createdAt ?: nowIso(),
        updatedAt = nowIso(),
        // v1.0.43：编辑模式必须保留归档态——buildMed 未列出该字段时取实体默认值 false，
        // 会把「已停用」的药静默复活（预填改为按 id 直查后可能拿到归档药，故此处置为必需）
        isArchived = original.value?.isArchived ?: false,
    )

    /**
     * 底部主按钮的动作（v1.0.92 从 `Button` 的内联 lambda 抽出）。
     *
     * 抽出来的**唯一**目的是把"读全表单"这件事留在回调里：回调不在组合上下文里执行，
     * 因此这 20 个 `.value` 读不会让任何 composable 订阅它们——这正是本批要的形状。
     * 校验规则与拆分前逐字一致（包括 `return` 的时机）。
     */
    fun onPrimary() {
        // v1.0.43：编辑目标查不到时禁止保存——否则会静默新建一条重复药
        if (editMissing.value) return
        if (step.value == 1) {
            if (name.value.isBlank() || nameKey.value.isBlank() || dose.value.isBlank()) return
            // v1.0.78（批次 4 收尾）：锚点日期非法时不许进入核对步骤（按钮已同步置灰）
            if (cycleAnchorVisible(route.value, frequency.value) &&
                !DateFieldRules.requiredOk(startDate.value)
            ) {
                return
            }
            if (frequency.value == MedFrequency.BIW && weekday.value == weekday2.value) {
                biwError.value = true
                return
            }
            // v1.1.2（批次 18）：甲氨蝶呤的频次只能是每周一次（itx-001 的行为承诺，第四份审查报告 §一）。
            // 放在 step 1 拦，**新增与编辑走的是同一个 onPrimary**——只拦新增会漏掉
            // 「把 weekly 改成 daily」这条更要命的路径。
            if (frequency.value != MedFrequency.WEEKLY && DrugInteractionKeys.requiresWeeklyFrequency(nameKey.value)) {
                mtxFrequencyError.value = true
                return
            }
            val draft = buildMed()
            scope.launch {
                hits.value = vm.interactionsFor(draft)
                step.value = 2
            }
        } else {
            vm.saveMedication(context, buildMed())
            onSaved()
        }
    }

    fun goBackStep() {
        if (step.value == 1) onBack() else step.value = 1
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            ScreenTopBar(
                title = when {
                    editId != null && step.value == 1 -> stringResource(R.string.med_edit_medication)
                    step.value == 1 -> stringResource(R.string.med_add_medication)
                    else -> stringResource(R.string.med_verify_checklist)
                },
                onBack = ::goBackStep,
            )
        },
        bottomBar = {
            // v1.0.92（批次 17）：两个判定改在**本 lambda 内**求值。它们原本是根级正文里的
            // `val cycleAnchorVisible` / `val startDateOk`——只要 startDate 一变，根级就重组，
            // 整个表单跟着重跑。本 lambda 是 Scaffold 的一个独立作用域：这些字段变化只重组
            // 这根操作条（它本来就依赖它们，见下方 `enabled`）。
            val cycleAnchorShown = cycleAnchorVisible(route.value, frequency.value)
            val anchorDateOk = DateFieldRules.requiredOk(startDate.value)
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
                    ) {
                        Text(
                            if (step.value == 1) stringResource(R.string.common_cancel)
                            else stringResource(R.string.backup_return_modify)
                        )
                    }

                    Button(
                        onClick = ::onPrimary,
                        enabled = !editMissing.value && (!cycleAnchorShown || anchorDateOk),
                        modifier = Modifier.weight(1f).heightIn(min = Size.touchMin),
                    ) {
                        Text(
                            if (step.value == 1) stringResource(R.string.med_next_verify)
                            else stringResource(R.string.checkup_save_record)
                        )
                    }
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
            if (step.value == 1) {
                // v1.0.43：编辑目标不存在（已删除 / id 失效）时明确告知，不静默新建
                if (editMissing.value) {
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
                // 各 section 只收自己那组 state（读点因此落在 section 内）；顺序与拆分前逐字一致
                MedBasicsSection(name = name, brand = brand, nameKey = nameKey, medClass = medClass)
                MedRouteDoseSection(route = route, dose = dose)
                MedScheduleSection(
                    frequency = frequency,
                    route = route,
                    times = times,
                    prnReason = prnReason,
                    cycleDays = cycleDays,
                    startDate = startDate,
                    nameKey = nameKey,
                    mtxError = mtxFrequencyError,
                )
                MedWeekdaySection(frequency = frequency, weekday = weekday, weekday2 = weekday2, biwError = biwError)
                MedMealSection(route = route, food = food)
                MedStorageSection(storage = storage)
                MedDoseStateSection(doseState = doseState, taperNote = taperNote)
            } else {
                // ---- 第二步：R03 核对清单 ----
                MedVerifySection(hits = hits, doctorTold = doctorTold, leafletRead = leafletRead)
            }
            // 底部操作条已固定，这里补足滚动余量
            Text("", Modifier.padding(bottom = Spacing.xl))
        }
    }
}
