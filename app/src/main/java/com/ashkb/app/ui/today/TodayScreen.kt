package com.ashkb.app.ui.today

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle

import com.ashkb.app.R
import com.ashkb.app.data.entity.InjSite
import com.ashkb.app.data.entity.Medication
import com.ashkb.app.data.entity.SkipReason
import com.ashkb.app.data.repo.TodayItem
import com.ashkb.app.domain.MinimalMode
import com.ashkb.app.domain.MissedDose
import com.ashkb.app.domain.PendingDoses
import com.ashkb.app.domain.ScheduleCalc
import com.ashkb.app.ui.components.AlertBanner
import com.ashkb.app.ui.components.DisclaimerNote
import com.ashkb.app.ui.components.EmptyState
import com.ashkb.app.ui.components.SectionCard
import com.ashkb.app.ui.components.StatusChip
import com.ashkb.app.ui.theme.Clinical
import com.ashkb.app.ui.theme.DataLarge
import com.ashkb.app.ui.theme.Size
import com.ashkb.app.ui.theme.Spacing
import com.ashkb.app.ui.theme.StatusTone
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 今日页（route `today`）。
 *
 * ### v1.0.87（批次 12）：按分区收口，根级 Flow 由 9 条降到 3 条
 * 本页此前在**根级**收集 9 条 Flow（profile / today / alerts / symptomRecorded / exerciseDone /
 * minimalPrompt / yesterdayPending / todayDate，外加由 todayDate 派生的 yesterdayDate），
 * 其中 `today` 会在每次打卡、`minimalPrompt` 会在每次进页时发射——因为读点在根级，
 * 任何一条变化都会把整屏（Hero、极简横幅、告警横幅、两枚快捷入口、补记卡、整张药品列表）
 * 一起拖进重组。现在改为**按分区收口**：每个 section 在自己内部收集它渲染的状态，
 * 根部只留三条：
 *  · [profile]——Hero 的「姓名 · 诊断」、极简横幅、补记卡（空态文案分叉）三处都要它，
 *    `isMinimal` 也由它派生（同一份判据给横幅与快捷入口共用，不能各收一次）；
 *  · [todayDate]——Hero 的日期标题、药品卡的漏服判定（`isMissed` 要求 `today == LocalDate.now()`）、
 *    补记卡的「昨天」三处**必须是同一个「今天」**。批次 9 修的就是「两处各读一次系统时钟、
 *    跨零点前后差一天」的缺陷，所以这条日期流绝不允许下移到 section 里各收一份；
 *  · [today]——药品列表的**内容**，必须由根部的 `LazyColumn` 亲自发出（理由见下）。
 * 生命周期观察（进页 / 回前台跟系统时钟对一次日期）不进任何 section：它不渲染任何东西，
 * 却必须在整页存活期间生效。
 *
 * ### 为什么 `today` 不能像其它六条那样下移（本轮的技术边界，实测编译失败）
 * `LazyListScope.item { }` / `items(...) { }` 只在**列表作用域**里解析得到，而
 * `remember` / `mutableStateOf` / `collectAsStateWithLifecycle` 只能在**组合上下文**里调用。
 * 想让一个 section 同时「自己收 Flow」并「把卡片发进宿主列表」，实测两条路都走不通：
 *  · 标成普通 `LazyListScope` 扩展 → 里面的 `remember` / `collectAsStateWithLifecycle` 报
 *    `Functions which invoke @Composable functions must be marked with the @Composable annotation`；
 *  · 标成 `@Composable LazyListScope.` 扩展 → 在 `LazyColumn` 的内容 lambda 里调用时，
 *    调用点本身不是组合上下文，报
 *    `@Composable invocations can only happen from the context of a @Composable function`。
 * 两条绕法也都被否掉：
 *  · 在 section 里再套一个 `LazyColumn`——嵌套滚动容器在无界高度下布局失败，且 `key` 与
 *    滚动位置一并丢失；
 *  · 把整个列表塞进一个 `item { }`——等于放弃虚拟化，药一多就全量组合。
 * 所以列表**内容**（[today]）留在根部；item 的**内容**仍是独立 composable（[MedCheckCard]），
 * 四个弹层的状态也只能留在根部（它们的写入点是卡片回调，而卡片回调在列表作用域里创建）。
 * 好消息是这四个 `remember` **不是 Flow**：它们不会自己发射，只是四个状态槽位。
 *
 * 真正下移的是**六条会自己发射的流**：`alerts` / `symptomRecorded` / `exerciseDone` /
 * `minimalPrompt` / `yesterdayPending` 各自收进对应 section 的 `item { }` 内容 composable，
 * `yesterdayDate` 这个派生值直接在补记卡里由入参今天算。它们此前每一次发射都在拖着整屏重组。
 *
 * 维护约定（后续加状态时请沿用，与 `BackupScreen` v1.0.85 同一套）：
 *  · 某个状态只被一个 section 渲染 → 在那个 section 里 collect，不要拿回根部；
 *  · section 的入参只给「父级自己也要用」的值，其余进去自己收；
 *  · 不要在 section 里顺手读与自己无关的 VM 状态——那等于把重组范围又扩大回来。
 *  · **`collectAsStateWithLifecycle` 一律用无参重载**：它取的是 VM 里 `stateIn` 的 `initialValue`，
 *    写成 `initialValue = …` 等于在同一条流上再声明一份初值，两处迟早不一致
 *    （本页 `todayDate` 的初值就是 `DateProvider` 给的「今天」，重写只会引入第三个日期源）。
 */
@Composable
fun TodayScreen(
    vm: TodayViewModel,
    onMedListNeeded: () -> Unit,
    onOpenSymptom: () -> Unit = {},
    onOpenExercise: () -> Unit = {},
) {
    // ---- 根级仅存的 3 条 Flow（整屏骨架 / 跨 section 胶水 / 列表内容）----
    val profile by vm.profile.collectAsStateWithLifecycle()
    val todayDate by vm.todayDate.collectAsStateWithLifecycle()
    val items by vm.today.collectAsStateWithLifecycle()
    // 极简模式判据：横幅与快捷入口行共用（各收一次 profile 就会出现两份真相，且多一次订阅）
    val isMinimal = profile?.uiMode == MinimalMode.MODE_MINIMAL

    // v1.0.73：不再需要 context——打卡 / 跳过 / 顺延后的重排已折进 TodayViewModel（写入后同协程 + IO）
    // v1.0.87（批次 12）：这四个 target 是**唯一**没能下移的输入状态，理由见类注释
    // （列表作用域上不能 `remember`，而它们的写入点全在卡片回调里）。它们不是 Flow：
    // 留着只是四个 `remember` 槽位，不会自己发射、不会扩大重组。
    var skipTarget by remember { mutableStateOf<TodayItem?>(null) }
    var injTarget by remember { mutableStateOf<TodayItem?>(null) }
    var postponeTarget by remember { mutableStateOf<TodayItem?>(null) }
    var missedGuideTarget by remember { mutableStateOf<TodayItem?>(null) }

    // v1.0.84（批次 9）：进页 / 回前台各跟系统时钟对一次日期（实现见 RefreshDateOnResume）
    RefreshDateOnResume(vm = vm)

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item { Spacer(Modifier.height(Spacing.md)) }

        // ---- Hero：一屏一主角（今天是"下一次该做什么"）----
        item {
            HeroHeader(
                dateText = todayDate.format(
                    DateTimeFormatter.ofPattern(stringResource(R.string.date_pattern_month_day_week), Locale.CHINESE),
                ),
                who = profile?.let { "${it.displayName} · ${it.diagnosis}" }
                    ?: stringResource(R.string.today_profile_not_built),
                // 两个计数只喂这一行：它们随 `items` 变，放根部算等于每次打卡都让整屏跟着重组
                pendingCount = items.count { !it.done && !it.skipped && !it.isPrn },
                scheduledCount = items.count { !it.isPrn },
            )
        }

        // ---- v1.0.65 B12：极简模式横幅（发作期输入减负；退出入口就在此处）----
        item { MinimalModeBanner(vm = vm, visible = isMinimal) }

        // ---- 告警必须成为视觉主角（原为 error.copy(alpha=0.10) 的扁平卡）----
        item { TodayAlertsSection(vm = vm, onOpenSymptom = onOpenSymptom) }

        // ---- 两枚大按钮（≥56dp），取代两个同款小卡 ----
        // v1.0.65 B12：极简模式下只留核心的「症状记录」，隐藏运动入口（减负）
        item {
            QuickEntryRow(
                vm = vm,
                isMinimal = isMinimal,
                onOpenSymptom = onOpenSymptom,
                onOpenExercise = onOpenExercise,
            )
        }

        // v1.0.74：跨零点补记卡——昨天已到点却没记录的剂量，在这里补记（写入槽位所属日）。
        // 缺口背景（2026-09-30 真机实测）：23:55 那剂的追问落在次日 00:25，用户被提醒后回到应用
        // 只能给「今天」的槽位打卡 → 昨天那剂永远补不上、今天那剂却被提前记录。通知上的「已服用」
        // 走的是槽位所属日（v1.0.73），但用户往往直接回应用，故必须有这个入口。
        yesterdayPendingSection(vm = vm, todayDate = todayDate)

        // 药品列表 + 四态卡片（四个弹层的状态与渲染都在根级，理由见类注释）
        medCardItems(
            items = items,
            todayDate = todayDate,
            profileMissing = profile == null,
            onMedListNeeded = onMedListNeeded,
            callbacks = MedCardCallbacks(
                onCheckIn = { item ->
                    // 注射剂（且非按需）要先选部位，其余直接打卡——旧实现在 onCheckIn 里的同一个分支
                    if (item.med.route == "injection" && !item.isPrn) injTarget = item else vm.checkIn(item)
                },
                onSkip = { skipTarget = it },
                onPostpone = { postponeTarget = it },
                onPrnTaken = { vm.checkIn(it) },
                onMissedGuide = { missedGuideTarget = it },
            ),
        )

        item { Spacer(Modifier.height(Spacing.xxl)) }
    }

    // 四个弹层在 `LazyColumn` 之外渲染（与旧实现位置相同）：AlertDialog 是独立窗口，不参与列表布局
    TodayDialogs(
        vm = vm,
        pending = TodayDialogTargets(
            skip = skipTarget,
            injSite = injTarget,
            postpone = postponeTarget,
            missedGuide = missedGuideTarget,
        ),
        onClear = { which ->
            when (which) {
                TodayDialog.SKIP -> skipTarget = null
                TodayDialog.INJ_SITE -> injTarget = null
                TodayDialog.POSTPONE -> postponeTarget = null
                TodayDialog.MISSED_GUIDE -> missedGuideTarget = null
            }
        },
    )

    // v1.0.65 B12：连续 3 天无核心记录 → 问原因；身体不适 / 住院 → 极简模式
    // 这是**跨 section 的一次性事件**（不渲染任何卡片，但必须在整页存活期间可弹），故留在根部。
    MinimalPromptDialog(vm = vm)
}

/** 四个弹层各自的打开目标（`null` = 关着）。收成一组，让 [TodayDialogs] 的形参停在 3 个。 */
private data class TodayDialogTargets(
    val skip: TodayItem?,
    val injSite: TodayItem?,
    val postpone: TodayItem?,
    val missedGuide: TodayItem?,
)

/** 弹层标识：清空回调据此只清一个（比四个 `() -> Unit` 形参更省，也更难写错）。 */
private enum class TodayDialog { SKIP, INJ_SITE, POSTPONE, MISSED_GUIDE }

/**
 * v1.0.84（批次 9）：进页 / 回前台各跟系统时钟对一次日期。
 *
 * 深睡会暂停 Handler 的 `postDelayed` 计时（见 [TodayViewModel.refreshDateIfStale]），只靠 VM 的
 * 跨零点 ticker 在「夜里手机睡着」这个最常见的情形下会晚点，届时日期标题与昨日待补卡都会落后一天。
 *
 * v1.0.87（批次 12）抽成独立函数：它是**纯副作用、不渲染任何东西**，却必须随整页存活——
 * 留在根部只会把列表骨架的代码挤长（detekt `LongMethod`）。
 */
@Composable
private fun RefreshDateOnResume(vm: TodayViewModel) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        vm.refreshDateIfStale()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.refreshDateIfStale()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}

/**
 * 卡片点击触发的四个弹层（跳过原因 / 注射部位 / 注射顺延 / 漏服指引）。
 *
 * v1.0.87（批次 12）从 `TodayScreen` 抽出：根部函数要同时容纳列表骨架与这四个弹层，
 * 会越过 detekt 的 `LongMethod`（80 行）。抽出后职责也更清楚——根部只管「屏上有哪几块」，
 * 弹层怎么呈现归这里。状态仍由根部持有（它们的写入点是卡片回调，见 `TodayScreen` 类注释），
 * 这里只接值 + 一个按标识清空的回调。
 */
@Composable
private fun TodayDialogs(vm: TodayViewModel, pending: TodayDialogTargets, onClear: (TodayDialog) -> Unit) {
    pending.skip?.let { target ->
        SkipDialog(
            medName = target.med.name,
            onConfirm = { reason, note ->
                vm.skip(target, reason, note)
                onClear(TodayDialog.SKIP)
            },
            onDismiss = { onClear(TodayDialog.SKIP) },
        )
    }

    pending.injSite?.let { target ->
        InjSiteDialog(
            medName = target.med.name,
            lastSite = target.med.injLastSite,
            onConfirm = { site ->
                vm.checkIn(target, injSite = site)
                onClear(TodayDialog.INJ_SITE)
            },
            onDismiss = { onClear(TodayDialog.INJ_SITE) },
        )
    }

    pending.postpone?.let { target ->
        PostponeDialog(
            medName = target.med.name,
            cycleDays = target.med.injCycleDays,
            onConfirm = { date ->
                vm.postpone(target, date)
                onClear(TodayDialog.POSTPONE)
            },
            onDismiss = { onClear(TodayDialog.POSTPONE) },
        )
    }

    pending.missedGuide?.let { target ->
        MissedDoseDialog(item = target, onDismiss = { onClear(TodayDialog.MISSED_GUIDE) })
    }
}

/**
 * v1.0.65 B12：极简模式横幅（发作期输入减负，退出入口就在横幅上）。
 *
 * v1.0.87（批次 12）：自己收 `profile`（横幅文案里的「自 … 起」只在被显示时才需要）。
 * 与快捷入口行共用同一份 `uiMode` 判据（由根部算好传进来）——各收一次 profile 会出现
 * 两份真相，也会让「横幅显示了、运动入口却没隐藏」这种半截状态成为可能。
 */
@Composable
private fun MinimalModeBanner(vm: TodayViewModel, visible: Boolean) {
    val profile by vm.profile.collectAsStateWithLifecycle()
    AlertBanner(
        tone = StatusTone.Warning,
        icon = Icons.Rounded.SelfImprovement,
        title = stringResource(R.string.minimal_mode_title) +
            (profile?.minimalSince?.take(10)?.let { " · " + stringResource(R.string.minimal_mode_since, it) } ?: ""),
        body = stringResource(R.string.minimal_mode_note),
        actionLabel = stringResource(R.string.minimal_exit),
        onAction = { vm.exitMinimalMode() },
        // 不可见时整块不占位（AnimatedVisibility 收起），与旧实现的 `if (isMinimal)` 等效
        visible = visible,
    )
}

/**
 * 未读告警横幅：有告警才显示。
 *
 * v1.0.87（批次 12）：自己收 `alerts`（只有这块渲染它；告警条数变化不该带动整屏重组）。
 */
@Composable
private fun TodayAlertsSection(vm: TodayViewModel, onOpenSymptom: () -> Unit) {
    val alerts by vm.alerts.collectAsStateWithLifecycle()
    if (alerts.isEmpty()) return

    AlertBanner(
        tone = StatusTone.Danger,
        icon = Icons.Rounded.WarningAmber,
        title = stringResource(R.string.today_alerts_count, alerts.size),
        body = alerts.first().message,
        actionLabel = stringResource(R.string.common_view),
        onAction = onOpenSymptom,
    )
}

/**
 * 两枚快捷入口大按钮（≥56dp）。
 *
 * v1.0.87（批次 12）：自己收 `symptomRecorded` 与 `exerciseDone`——它们只渲染这两个按钮上的
 * 状态行；此前挂在根部，于是每次记一条症状都会把整张药品列表一起拖进重组。
 */
@Composable
private fun QuickEntryRow(
    vm: TodayViewModel,
    isMinimal: Boolean,
    onOpenSymptom: () -> Unit,
    onOpenExercise: () -> Unit,
) {
    val symptomRecorded by vm.symptomRecorded.collectAsStateWithLifecycle()
    val exerciseDone by vm.exerciseDone.collectAsStateWithLifecycle()

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        QuickEntryButton(
            icon = Icons.Rounded.MonitorHeart,
            title = stringResource(R.string.today_log_symptom),
            status = if (symptomRecorded) {
                stringResource(R.string.today_recorded)
            } else {
                stringResource(R.string.today_not_recorded)
            },
            done = symptomRecorded,
            modifier = Modifier.weight(1f),
            onClick = onOpenSymptom,
        )
        if (!isMinimal) {
            QuickEntryButton(
                icon = Icons.Rounded.FitnessCenter,
                title = stringResource(R.string.exercise_today_title),
                status = if (exerciseDone > 0) {
                    stringResource(R.string.today_exercise_done, exerciseDone)
                } else {
                    stringResource(R.string.exercise_by_stage)
                },
                done = exerciseDone > 0,
                modifier = Modifier.weight(1f),
                onClick = onOpenExercise,
            )
        }
    }
}

/**
 * v1.0.74：跨零点补记卡（昨天已到点却没记录的剂量）在列表里的**槽位**。
 *
 * v1.0.87（批次 12）：`yesterdayPending` 由卡片自己收（本函数是普通 `LazyListScope` 扩展，
 * 而 `collectAsStateWithLifecycle` / `remember` 都是 `@Composable` 函数——**只能在
 * `item { }` 的内容 composable 里调用**，不能在列表作用域上调用）。于是这条流变化时
 * 只重组那张卡，不牵动宿主列表的其它 item。
 * 「昨天」由**入参的今天**推导（[yesterdayIso]），不从 section 里再读一次系统时钟——
 * 那正是批次 9 修掉的「卡里按系统时钟的昨天算、标题按日期流的昨天显示」的缺口。
 */
private fun LazyListScope.yesterdayPendingSection(vm: TodayViewModel, todayDate: LocalDate) {
    item { YesterdayPendingCard(vm = vm, todayDate = todayDate) }
}

/** 补记卡本体：自己收 `yesterdayPending`（本批下移的六条流之一），无待补时整块不渲染。 */
@Composable
private fun YesterdayPendingCard(vm: TodayViewModel, todayDate: LocalDate) {
    val pending by vm.yesterdayPending.collectAsStateWithLifecycle()
    if (pending.isEmpty()) return

    SectionCard(
        title = stringResource(R.string.today_yesterday_pending_title, pending.size),
    ) {
        Text(
            stringResource(R.string.today_yesterday_pending_hint, yesterdayIso(todayDate)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        pending.forEach { p ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(p.medName, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(R.string.today_yesterday_pending_plan, p.slotTime),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedButton(
                    onClick = { vm.checkInYesterday(p) },
                    modifier = Modifier.height(Size.touchMin),
                ) {
                    Text(stringResource(R.string.today_yesterday_pending_mark))
                }
            }
        }
    }
}

/**
 * 药品卡片上的五个动作（打卡 / 跳过 / 顺延 / 按需已用 / 漏服指引）。
 *
 * v1.0.87（批次 12）：收成一个参数对象，让 [medCardItems] 的形参停在 5 个——
 * 摊平就是 9 个，直接越过 detekt 的 `LongParameterList`（阈值 8）。这五个回调的共同点是
 * 「都从卡片上来、都只改根级那四个 target 之一」，与列表数据无关。
 */
private data class MedCardCallbacks(
    val onCheckIn: (TodayItem) -> Unit,
    val onSkip: (TodayItem) -> Unit,
    val onPostpone: (TodayItem) -> Unit,
    val onPrnTaken: (TodayItem) -> Unit,
    val onMissedGuide: (TodayItem) -> Unit,
)

/**
 * 药品列表在宿主 `LazyColumn` 里的**槽位登记**（空态卡或一列 [MedCheckCard]）。
 *
 * v1.0.87（批次 12）：普通 `LazyListScope` 扩展（**不能**带 `@Composable`）；它只登记 item
 * 与回调，四个弹层的状态留在根级（由调用方经 [MedCardCallbacks] 传入）。
 * 卡片必须作为宿主列表的 item 发出去，虚拟化与 `key` 都由宿主统一管理。
 */
private fun LazyListScope.medCardItems(
    items: List<TodayItem>,
    todayDate: LocalDate,
    profileMissing: Boolean,
    onMedListNeeded: () -> Unit,
    callbacks: MedCardCallbacks,
) {
    if (items.isEmpty()) {
        item { MedsEmptyCard(profileMissing = profileMissing, onMedListNeeded = onMedListNeeded) }
        return
    }
    items(items, key = { it.med.id to it.slotKey }) { item ->
        MedCheckCard(
            item = item,
            today = todayDate,
            onCheckIn = { callbacks.onCheckIn(item) },
            onSkip = { callbacks.onSkip(item) },
            onPostpone = { callbacks.onPostpone(item) },
            onPrnTaken = { callbacks.onPrnTaken(item) },
            onMissedGuide = { callbacks.onMissedGuide(item) },
        )
    }
}

/**
 * 药品列表的空态卡（没有任何在用药品时）。
 *
 * v1.0.87（批次 12）抽成独立函数：让 [todayMedsSection] 同时容纳四个弹层的状态而不超
 * detekt 的 `LongMethod` 阈值，并把「空态文案随是否有档案分叉」这条口径收在一处。
 */
@Composable
private fun MedsEmptyCard(profileMissing: Boolean, onMedListNeeded: () -> Unit) {
    SectionCard(title = stringResource(R.string.today_meds_section)) {
        EmptyState(
            icon = Icons.Rounded.Medication,
            title = stringResource(R.string.med_empty_hint),
            body = if (profileMissing) {
                stringResource(R.string.today_build_then_add_note)
            } else {
                stringResource(R.string.med_add_hint_today)
            },
            actionLabel = stringResource(R.string.med_add_medication),
            onAction = onMedListNeeded,
        )
    }
}

/**
 * v1.0.65 B12：连续 3 天无核心记录 → 问原因；身体不适 / 住院 → 极简模式。
 *
 * v1.0.87（批次 12）：自己收 `minimalPrompt`，但**语句留在根部**——它不是某个分区的渲染内容，
 * 而是整页级的一次性事件（不渲染任何卡片，却必须在整页存活期间随时可弹）。前提判定
 * （已建档 / 非极简态 / 今天没问过 / 近 3 天症状记录缺失）全在 `TodayViewModel.minimalPrompt`
 * 里，本函数只负责呈现与回写答案。
 */
@Composable
private fun MinimalPromptDialog(vm: TodayViewModel) {
    val minimalPrompt by vm.minimalPrompt.collectAsStateWithLifecycle()
    if (!minimalPrompt) return

    AlertDialog(
        onDismissRequest = { /* 必须选一项：不给点外部关闭，避免留下"未回答"状态 */ },
        title = { Text(stringResource(R.string.minimal_prompt_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(
                    stringResource(R.string.minimal_prompt_body),
                    style = MaterialTheme.typography.bodyMedium,
                )
                MinimalMode.REASONS.forEach { reason ->
                    TextButton(
                        onClick = { vm.answerMinimalPrompt(reason) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            stringResource(minimalReasonLabel(reason)),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        },
        confirmButton = {},
    )
}

/** B12：询问原因 → 文案资源。 */
private fun minimalReasonLabel(reason: String) = when (reason) {
    MinimalMode.REASON_ILLNESS -> R.string.minimal_reason_illness
    MinimalMode.REASON_HOSPITAL -> R.string.minimal_reason_hospital
    else -> R.string.minimal_reason_other
}

/**
 * 「昨日待补」卡上显示的日期 = 今天 − 1 天。
 *
 * v1.0.84（批次 9）：必须是**入参「今天」的纯函数**。原实现在组合期直接
 * `remember { LocalDate.now().minusDays(1) }`——既绕开了 VM 的日期流（跨零点不重算，
 * 标题比卡里重算过的剂量早一天），又多出一份「读系统时钟」的真相。抽成纯函数后
 * 该口径可被单测锁住（`ui/today` 的单测）。
 */
internal fun yesterdayIso(today: LocalDate): String = today.minusDays(1).toString()

/** 今日页主角：一眼看清"今天还剩什么"。 */
@Composable
private fun HeroHeader(
    dateText: String,
    who: String,
    pendingCount: Int,
    scheduledCount: Int,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = Size.heroMinHeight)
                .padding(Spacing.xl),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(
                    dateText,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    when {
                        scheduledCount == 0 -> stringResource(R.string.today_no_med_plan)
                        pendingCount == 0 -> stringResource(R.string.today_all_done)
                        else -> stringResource(R.string.today_pending_meds, pendingCount)
                    },
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    who,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (scheduledCount > 0) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
                ) {
                    Text(
                        "$pendingCount",
                        style = DataLarge,
                        color = if (pendingCount == 0) {
                            Clinical.colors.success
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                    )
                    Text(
                        stringResource(R.string.common_pending_record),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** 快捷入口：≥56dp 的整块按钮，图标 + 标题 + 状态。 */
@Composable
private fun QuickEntryButton(
    icon: ImageVector,
    title: String,
    status: String,
    done: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val container = if (done) Clinical.colors.successContainer else MaterialTheme.colorScheme.secondaryContainer
    val content = if (done) Clinical.colors.onSuccessContainer else MaterialTheme.colorScheme.onSecondaryContainer
    val accent = if (done) Clinical.colors.success else MaterialTheme.colorScheme.onSecondaryContainer
    Surface(
        modifier = modifier.heightIn(min = Size.touchComfort),
        shape = MaterialTheme.shapes.medium,
        color = container,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(Size.iconMd), tint = accent)
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = content)
                Text(status, style = MaterialTheme.typography.labelSmall, color = content)
            }
        }
    }
}

/** 四态严格区分：待服 / 已服 / 已跳过 / 漏服。原实现把"已服"与"跳过"映射到同一个 surfaceVariant。 */
private data class MedStatus(
    val chip: String,
    val tone: StatusTone,
    val icon: ImageVector,
    val detail: String,
    val actionable: Boolean,
)

@Composable
private fun medStatusOf(item: TodayItem, missed: Boolean): MedStatus = when {
    item.done -> MedStatus(
        chip = stringResource(R.string.med_status_taken_short),
        tone = StatusTone.Success,
        icon = Icons.Rounded.CheckCircle,
        // v1.0.50：时刻必须解析后再格式化。原实现取 `takenAt.takeLast(5)`，
        // 而 takenAt 带小数秒时串长会浮动（见 ScheduleCalc.hhmm），
        // 于是「已服 22:31」被显示成「已服 19981」这类小数秒数字。
        // 解析不出时刻时不留下悬空的「已服 」前缀。
        detail = listOfNotNull(
            ScheduleCalc.hhmm(item.log?.takenAt)?.let { stringResource(R.string.med_taken_prefix) + it },
            if (item.isLate) stringResource(R.string.med_late_note) else null,
        ).joinToString(""),
        actionable = false,
    )
    item.skipped -> MedStatus(
        chip = stringResource(R.string.med_status_skipped),
        tone = StatusTone.Neutral,
        icon = Icons.Rounded.RemoveCircleOutline,
        detail = stringResource(R.string.backup_skipped_prefix) + (
            SkipReason.entries.firstOrNull { it.name.equals(item.log?.reason, true) }?.label
                ?: item.log?.reason ?: ""
            ),
        actionable = false,
    )
    missed -> MedStatus(
        chip = stringResource(R.string.med_status_missed),
        tone = StatusTone.Danger,
        icon = Icons.Rounded.ErrorOutline,
        detail = stringResource(R.string.today_plan_no_log, item.slotTime ?: ""),
        actionable = true,
    )
    else -> MedStatus(
        chip = stringResource(R.string.med_status_pending),
        tone = StatusTone.Info,
        icon = Icons.Rounded.Schedule,
        detail = "计划 ${item.slotTime ?: stringResource(R.string.med_prn_short)}",
        actionable = true,
    )
}

private fun isMissed(item: TodayItem, today: LocalDate): Boolean {
    if (item.isPrn || item.done || item.skipped) return false
    val t = item.slotTime ?: return false
    if (today != LocalDate.now()) return false
    val slot = runCatching { LocalTime.parse(t) }.getOrNull() ?: return false
    return LocalTime.now().isAfter(slot)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MedCheckCard(
    item: TodayItem,
    today: LocalDate,
    onCheckIn: () -> Unit,
    onSkip: () -> Unit,
    onPostpone: () -> Unit,
    onPrnTaken: () -> Unit,
    onMissedGuide: () -> Unit,
) {
    val med: Medication = item.med
    val missed = isMissed(item, today)
    val st = medStatusOf(item, missed)

    val container = when {
        item.done -> Clinical.colors.successContainer
        item.skipped -> MaterialTheme.colorScheme.surfaceContainerHighest
        missed -> Clinical.colors.dangerContainer
        else -> MaterialTheme.colorScheme.surfaceContainerLowest
    }
    val onContainer = when {
        item.done -> Clinical.colors.onSuccessContainer
        item.skipped -> MaterialTheme.colorScheme.onSurfaceVariant
        missed -> Clinical.colors.onDangerContainer
        else -> MaterialTheme.colorScheme.onSurface
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = container,
        // 待服：白卡 + primary 描边（唯一需要描边的状态）
        border = if (item.done || item.skipped || missed) null
        else BorderStroke(Size.divider, MaterialTheme.colorScheme.primary),
    ) {
        Column(
            Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text(
                        "${med.name} ${med.dose}",
                        style = MaterialTheme.typography.titleMedium,
                        color = onContainer,
                        textDecoration = if (item.skipped) TextDecoration.LineThrough else null,
                    )
                    // 时段 / 剂型 / 服法 / 储存：4 个 chip，替代原来的字符串拼接一整行
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        StatusChip(item.slotLabel, st.tone, st.icon)
                        if (med.route == "injection") StatusChip(stringResource(R.string.med_route_injection), StatusTone.Info)
                        if (med.route == "oral") {
                            StatusChip(
                                when (med.takeWithFood) {
                                    "empty_stomach" -> stringResource(R.string.med_fasting)
                                    "with_food" -> stringResource(R.string.med_with_meal)
                                    else -> stringResource(R.string.common_any)
                                },
                                StatusTone.Neutral,
                            )
                        }
                        med.storage?.let { StatusChip(it, StatusTone.Warning, Icons.Rounded.AcUnit) }
                    }
                }
            }

            // 文字一环（三重编码：色 + 图标 + 文字）
            Text(st.detail, style = MaterialTheme.typography.bodySmall, color = onContainer)

            // v10（C7）：漏服时给出通用处理指引入口（口服补服规则 / 注射窗口分级）
            if (missed) {
                TextButton(
                    onClick = onMissedGuide,
                    modifier = Modifier.heightIn(min = Size.touchMin),
                ) { Text(stringResource(R.string.missed_dose_title)) }
            }

            if (st.actionable && !item.isPrn) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Button(onClick = onCheckIn, modifier = Modifier.heightIn(min = Size.touchMin)) {
                        Text(stringResource(R.string.med_take_action))
                    }
                    OutlinedButton(onClick = onSkip, modifier = Modifier.heightIn(min = Size.touchMin)) {
                        Text(stringResource(R.string.med_skip))
                    }
                    if (med.route == "injection") {
                        OutlinedButton(onClick = onPostpone, modifier = Modifier.heightIn(min = Size.touchMin)) {
                            Text(stringResource(R.string.med_postpone))
                        }
                    }
                }
            }

            if (item.isPrn) {
                Text(
                    stringResource(R.string.med_prn_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!item.done) {
                    Button(onClick = onPrnTaken, modifier = Modifier.heightIn(min = Size.touchMin)) {
                        Text(stringResource(R.string.med_record_prn_use))
                    }
                }
            }
        }
    }
}

@Composable
private fun SkipDialog(
    medName: String,
    onConfirm: (reason: String, note: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var reason by remember { mutableStateOf(SkipReason.OTHER) }
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.med_skip_title, medName)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.med_skip_reason_note), style = MaterialTheme.typography.bodySmall)
                SkipReason.entries.forEach { r ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = reason == r, onClick = { reason = r })
                        Text(r.label)
                    }
                }
                if (reason == SkipReason.OTHER) {
                    androidx.compose.material3.OutlinedTextField(
                        value = note, onValueChange = { note = it },
                        label = { Text(stringResource(R.string.common_extra_notes)) }, modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(reason.name, note.ifBlank { null }) }) { Text(stringResource(R.string.med_record_skip)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InjSiteDialog(
    medName: String,
    lastSite: String?,
    onConfirm: (site: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sites = InjSite.entries.map { it.key to it.label }
    var site by remember { mutableStateOf(sites.firstOrNull { it.first != lastSite }?.first ?: "thigh_l") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.med_injection_title, medName)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                if (lastSite != null) {
                    // v1.0.49：部位映射收拢到 InjSite（此前本页私有），展示中文而非存库键
                    val lastLabel = InjSite.fromKey(lastSite)?.label ?: lastSite
                    Text(stringResource(R.string.med_last_site_note, lastLabel), style = MaterialTheme.typography.bodySmall)
                }
                Text(stringResource(R.string.med_inj_site_prompt), style = MaterialTheme.typography.bodyMedium)
                // FlowRow：6 个 chip 一行放不下会自动换行（旧 Row 会把后面的选项截在屏幕外）
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    sites.forEach { (key, label) ->
                        FilterChip(
                            selected = site == key,
                            onClick = { site = key },
                            label = { Text(label) },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(site) }) { Text(stringResource(R.string.med_complete_injection)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

/** R17 注射顺延：锚点移至新日期，周期从新日期起算；实际注射时才写日志 */
@Composable
private fun PostponeDialog(
    medName: String,
    cycleDays: Int?,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val today = LocalDate.now()
    var offset by remember { mutableStateOf(1) }
    var custom by remember { mutableStateOf("") }
    val customDate = runCatching { LocalDate.parse(custom.trim()) }.getOrNull()
    val target = customDate ?: today.plusDays(offset.toLong())
    val fmt = DateTimeFormatter.ofPattern(stringResource(R.string.date_pattern_month_day_week), Locale.CHINESE)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.med_postpone_title, medName)) },
        text = {
            Column {
                Text(
                    buildString {
                        append(stringResource(R.string.med_postpone_anchor_note))
                        if (cycleDays != null) append(stringResource(R.string.med_cycle_note, cycleDays))
                        append(stringResource(R.string.med_postpone_reschedule_note))
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1, 2, 3).forEach { d ->
                        FilterChip(
                            selected = custom.isBlank() && offset == d,
                            onClick = { offset = d; custom = "" },
                            label = { Text(stringResource(R.string.med_plus_days, d)) },
                        )
                    }
                }
                androidx.compose.material3.OutlinedTextField(
                    value = custom,
                    onValueChange = { custom = it },
                    label = { Text(stringResource(R.string.med_postpone_manual_date)) },
                    modifier = Modifier.fillMaxWidth(),
                    isError = custom.isNotBlank() && customDate == null,
                    supportingText = if (custom.isNotBlank() && customDate == null) {
                        { Text(stringResource(R.string.common_date_format_hint)) }
                    } else null,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "目标注射日：${target.format(fmt)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.med_postpone_infection_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(target) },
                enabled = custom.isBlank() || customDate != null,
            ) { Text(stringResource(R.string.med_postpone_to_date)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

/**
 * v10（C7）：漏服 / 延迟处理指引。
 *
 * 规划要求「口服按通用补服规则、注射按窗口期内补注 / 超窗联系医师分级」。
 * 文案由纯函数 `domain/MissedDose` 生成（可单测），这里只负责呈现——
 * 医疗边界：不给出个体化剂量决策，始终提示以说明书与主治医师医嘱为准。
 */
@Composable
private fun MissedDoseDialog(item: TodayItem, onDismiss: () -> Unit) {
    val nowMinutes = LocalTime.now().let { it.hour * 60 + it.minute }
    val late = MissedDose.minutesLate(item.slotTime, nowMinutes)
    val guide = remember(item, late) { MissedDose.guidanceFor(item.med, late, item.isPrn) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.missed_dose_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                if (guide == null) {
                    // 理论上入口只在漏服态出现；兜底给通用提示而不是空白弹窗
                    DisclaimerNote(R.string.missed_dose_disclaimer)
                } else {
                    Text(
                        stringResource(
                            R.string.missed_dose_late,
                            if (late < 60) "$late 分钟" else "%.1f 小时".format(late / 60.0),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        guide.headline,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (guide.contactDoctor) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary,
                    )
                    if (guide.contactDoctor) {
                        StatusChip(
                            stringResource(R.string.missed_dose_contact_doctor),
                            StatusTone.Danger,
                            Icons.Rounded.ErrorOutline,
                        )
                    }
                    guide.steps.forEach { s ->
                        Text(
                            "· ${s.replace("**", "")}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    HorizontalDivider(Modifier.padding(vertical = Spacing.xs))
                    DisclaimerNote(R.string.missed_dose_disclaimer)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) } },
    )
}
