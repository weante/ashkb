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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

import com.ashkb.app.R
import com.ashkb.app.data.entity.InjSite
import com.ashkb.app.data.entity.Medication
import com.ashkb.app.data.entity.SkipReason
import com.ashkb.app.data.repo.TodayItem
import com.ashkb.app.domain.MissedDose
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    vm: TodayViewModel,
    onMedListNeeded: () -> Unit,
    onOpenSymptom: () -> Unit = {},
    onOpenExercise: () -> Unit = {},
) {
    val profile by vm.profile.collectAsStateWithLifecycle()
    val items by vm.today.collectAsStateWithLifecycle()
    val alerts by vm.alerts.collectAsStateWithLifecycle()
    val symptomRecorded by vm.symptomRecorded.collectAsStateWithLifecycle()
    val exerciseDone by vm.exerciseDone.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var skipTarget by remember { mutableStateOf<TodayItem?>(null) }
    var injTarget by remember { mutableStateOf<TodayItem?>(null) }
    var postponeTarget by remember { mutableStateOf<TodayItem?>(null) }
    var missedGuideTarget by remember { mutableStateOf<TodayItem?>(null) }

    val todayDate = vm.date
    val scheduled = items.count { !it.isPrn }
    val pending = items.count { !it.done && !it.skipped && !it.isPrn }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item { Spacer(Modifier.height(Spacing.md)) }

        // ---- Hero：一屏一主角（今天是"下一次该做什么"）----
        item {
            HeroHeader(
                dateText = todayDate.format(DateTimeFormatter.ofPattern(stringResource(R.string.date_pattern_month_day_week), Locale.CHINESE)),
                who = profile?.let { "${it.displayName} · ${it.diagnosis}" } ?: stringResource(R.string.today_profile_not_built),
                pendingCount = pending,
                scheduledCount = scheduled,
            )
        }

        // ---- 告警必须成为视觉主角（原为 error.copy(alpha=0.10) 的扁平卡）----
        if (alerts.isNotEmpty()) {
            item {
                AlertBanner(
                    tone = StatusTone.Danger,
                    icon = Icons.Rounded.WarningAmber,
                    title = stringResource(R.string.today_alerts_count, alerts.size),
                    body = alerts.first().message,
                    actionLabel = stringResource(R.string.common_view),
                    onAction = onOpenSymptom,
                )
            }
        }

        // ---- 两枚大按钮（≥56dp），取代两个同款小卡 ----
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                QuickEntryButton(
                    icon = Icons.Rounded.MonitorHeart,
                    title = stringResource(R.string.today_log_symptom),
                    status = if (symptomRecorded) stringResource(R.string.today_recorded) else stringResource(R.string.today_not_recorded),
                    done = symptomRecorded,
                    modifier = Modifier.weight(1f),
                    onClick = onOpenSymptom,
                )
                QuickEntryButton(
                    icon = Icons.Rounded.FitnessCenter,
                    title = stringResource(R.string.exercise_today_title),
                    status = if (exerciseDone > 0) stringResource(R.string.today_exercise_done, exerciseDone) else stringResource(R.string.exercise_by_stage),
                    done = exerciseDone > 0,
                    modifier = Modifier.weight(1f),
                    onClick = onOpenExercise,
                )
            }
        }

        if (items.isEmpty()) {
            item {
                SectionCard(title = stringResource(R.string.today_meds_section)) {
                    EmptyState(
                        icon = Icons.Rounded.Medication,
                        title = stringResource(R.string.med_empty_hint),
                        body = if (profile == null) {
                            stringResource(R.string.today_build_then_add_note)
                        } else {
                            stringResource(R.string.med_add_hint_today)
                        },
                        actionLabel = stringResource(R.string.med_add_medication),
                        onAction = onMedListNeeded,
                    )
                }
            }
        } else {
            items(items, key = { it.med.id to it.slotKey }) { item ->
                MedCheckCard(
                    item = item,
                    today = todayDate,
                    onCheckIn = {
                        if (item.med.route == "injection" && !item.isPrn) injTarget = item
                        else vm.checkIn(item)
                        vm.reschedule(context)
                    },
                    onSkip = { skipTarget = item },
                    onPostpone = { postponeTarget = item },
                    onPrnTaken = {
                        vm.checkIn(item)
                        vm.reschedule(context)
                    },
                    onMissedGuide = { missedGuideTarget = item },
                )
            }
        }
        item { Spacer(Modifier.height(Spacing.xxl)) }
    }

    skipTarget?.let { target ->
        SkipDialog(
            medName = target.med.name,
            onConfirm = { reason, note ->
                vm.skip(target, reason, note)
                vm.reschedule(context)
                skipTarget = null
            },
            onDismiss = { skipTarget = null },
        )
    }

    injTarget?.let { target ->
        InjSiteDialog(
            medName = target.med.name,
            lastSite = target.med.injLastSite,
            onConfirm = { site ->
                vm.checkIn(target, injSite = site)
                vm.reschedule(context)
                injTarget = null
            },
            onDismiss = { injTarget = null },
        )
    }

    postponeTarget?.let { target ->
        PostponeDialog(
            medName = target.med.name,
            cycleDays = target.med.injCycleDays,
            onConfirm = { date ->
                vm.postpone(target, date)
                vm.reschedule(context)
                postponeTarget = null
            },
            onDismiss = { postponeTarget = null },
        )
    }

    missedGuideTarget?.let { target ->
        MissedDoseDialog(item = target, onDismiss = { missedGuideTarget = null })
    }
}

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
