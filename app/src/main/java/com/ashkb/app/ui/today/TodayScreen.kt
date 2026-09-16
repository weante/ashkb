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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ashkb.app.data.entity.Medication
import com.ashkb.app.data.entity.SkipReason
import com.ashkb.app.data.repo.TodayItem
import com.ashkb.app.ui.components.AlertBanner
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
    val profile by vm.profile.collectAsState()
    val items by vm.today.collectAsState()
    val alerts by vm.alerts.collectAsState()
    val symptomRecorded by vm.symptomRecorded.collectAsState()
    val exerciseDone by vm.exerciseDone.collectAsState()
    val context = LocalContext.current
    var skipTarget by remember { mutableStateOf<TodayItem?>(null) }
    var injTarget by remember { mutableStateOf<TodayItem?>(null) }
    var postponeTarget by remember { mutableStateOf<TodayItem?>(null) }

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
                dateText = todayDate.format(DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINESE)),
                who = profile?.let { "${it.displayName} · ${it.diagnosis}" } ?: "先完成健康档案建档",
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
                    title = "${alerts.size} 条未读健康警报",
                    body = alerts.first().message,
                    actionLabel = "查看",
                    onAction = onOpenSymptom,
                )
            }
        }

        // ---- 两枚大按钮（≥56dp），取代两个同款小卡 ----
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                QuickEntryButton(
                    icon = Icons.Rounded.MonitorHeart,
                    title = "记症状",
                    status = if (symptomRecorded) "今日已记录" else "今日未记录",
                    done = symptomRecorded,
                    modifier = Modifier.weight(1f),
                    onClick = onOpenSymptom,
                )
                QuickEntryButton(
                    icon = Icons.Rounded.FitnessCenter,
                    title = "今日运动",
                    status = if (exerciseDone > 0) "已打卡 $exerciseDone 项" else "按分期推荐",
                    done = exerciseDone > 0,
                    modifier = Modifier.weight(1f),
                    onClick = onOpenExercise,
                )
            }
        }

        if (items.isEmpty()) {
            item {
                SectionCard(title = "今日用药") {
                    EmptyState(
                        icon = Icons.Rounded.Medication,
                        title = "还没有添加药品",
                        body = if (profile == null) {
                            "建档后添加药品，这里会显示今天该服用的药与打卡入口"
                        } else {
                            "添加后，这里会显示今天该服用的药"
                        },
                        actionLabel = "添加药品",
                        onAction = onMedListNeeded,
                    )
                }
            }
        } else {
            items(items, key = { (it.med.id) + (it.slotKey ?: "prn") }) { item ->
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
                        scheduledCount == 0 -> "今天没有用药计划"
                        pendingCount == 0 -> "今天的用药都记完了"
                        else -> "今天还有 $pendingCount 次用药待记录"
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
                        "待记录",
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

private fun medStatusOf(item: TodayItem, missed: Boolean): MedStatus = when {
    item.done -> MedStatus(
        chip = "已服",
        tone = StatusTone.Success,
        icon = Icons.Rounded.CheckCircle,
        detail = "已服 " + (item.log?.takenAt?.takeLast(5) ?: "") +
            if (item.isLate) "（晚于计划 30 分钟以上）" else "",
        actionable = false,
    )
    item.skipped -> MedStatus(
        chip = "已跳过",
        tone = StatusTone.Neutral,
        icon = Icons.Rounded.RemoveCircleOutline,
        detail = "已跳过：" + (
            SkipReason.entries.firstOrNull { it.name.equals(item.log?.reason, true) }?.label
                ?: item.log?.reason ?: ""
            ),
        actionable = false,
    )
    missed -> MedStatus(
        chip = "漏服",
        tone = StatusTone.Danger,
        icon = Icons.Rounded.ErrorOutline,
        detail = "计划 ${item.slotTime} · 尚未记录",
        actionable = true,
    )
    else -> MedStatus(
        chip = "待服",
        tone = StatusTone.Info,
        icon = Icons.Rounded.Schedule,
        detail = "计划 ${item.slotTime ?: "按需"}",
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
                        if (med.route == "injection") StatusChip("注射", StatusTone.Info)
                        if (med.route == "oral") {
                            StatusChip(
                                when (med.takeWithFood) {
                                    "empty_stomach" -> "空腹"
                                    "with_food" -> "随餐"
                                    else -> "均可"
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

            if (st.actionable && !item.isPrn) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Button(onClick = onCheckIn, modifier = Modifier.heightIn(min = Size.touchMin)) {
                        Text("服用")
                    }
                    OutlinedButton(onClick = onSkip, modifier = Modifier.heightIn(min = Size.touchMin)) {
                        Text("跳过")
                    }
                    if (med.route == "injection") {
                        OutlinedButton(onClick = onPostpone, modifier = Modifier.heightIn(min = Size.touchMin)) {
                            Text("顺延")
                        }
                    }
                }
            }

            if (item.isPrn) {
                Text(
                    "按需药不设提醒，记录实际使用即可",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!item.done) {
                    Button(onClick = onPrnTaken, modifier = Modifier.heightIn(min = Size.touchMin)) {
                        Text("记录使用")
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
        title = { Text("跳过 $medName") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("请选择原因（跳过会如实记录，不计入漏服）", style = MaterialTheme.typography.bodySmall)
                SkipReason.entries.forEach { r ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = reason == r, onClick = { reason = r })
                        Text(r.label)
                    }
                }
                if (reason == SkipReason.OTHER) {
                    androidx.compose.material3.OutlinedTextField(
                        value = note, onValueChange = { note = it },
                        label = { Text("补充说明") }, modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(reason.name, note.ifBlank { null }) }) { Text("记录跳过") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/** 依那西普等皮下注射可选部位（大腿前外侧 / 腹部 / 上臂外侧，左右各一）。 */
private val INJ_SITES = listOf(
    "thigh_l" to "左大腿", "thigh_r" to "右大腿",
    "abdomen_l" to "左腹部", "abdomen_r" to "右腹部",
    "arm_l" to "左上臂", "arm_r" to "右上臂",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InjSiteDialog(
    medName: String,
    lastSite: String?,
    onConfirm: (site: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var site by remember { mutableStateOf(INJ_SITES.firstOrNull { it.first != lastSite }?.first ?: "thigh_l") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("注射 $medName") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                if (lastSite != null) {
                    val lastLabel = INJ_SITES.firstOrNull { it.first == lastSite }?.second ?: lastSite
                    Text("上次部位：$lastLabel——建议轮换", style = MaterialTheme.typography.bodySmall)
                }
                Text("请选择本次注射部位：", style = MaterialTheme.typography.bodyMedium)
                // FlowRow：6 个 chip 一行放不下会自动换行（旧 Row 会把后面的选项截在屏幕外）
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    INJ_SITES.forEach { (key, label) ->
                        FilterChip(
                            selected = site == key,
                            onClick = { site = key },
                            label = { Text(label) },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(site) }) { Text("完成注射") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
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
    val fmt = DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINESE)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("顺延 $medName 注射") },
        text = {
            Column {
                Text(
                    buildString {
                        append("顺延后以新日期为周期锚点")
                        if (cycleDays != null) append("（每 $cycleDays 天起算）")
                        append("，后续注射与提醒自动重排。今日卡将移至新日期。")
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1, 2, 3).forEach { d ->
                        FilterChip(
                            selected = custom.isBlank() && offset == d,
                            onClick = { offset = d; custom = "" },
                            label = { Text("+$d 天") },
                        )
                    }
                }
                androidx.compose.material3.OutlinedTextField(
                    value = custom,
                    onValueChange = { custom = it },
                    label = { Text("或输入日期 YYYY-MM-DD") },
                    modifier = Modifier.fillMaxWidth(),
                    isError = custom.isNotBlank() && customDate == null,
                    supportingText = if (custom.isNotBlank() && customDate == null) {
                        { Text("格式：2026-09-01") }
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
                    "如因发热 / 感染延迟注射，请先联系医生再顺延——生物制剂治疗期间感染需医生评估。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(target) },
                enabled = custom.isBlank() || customDate != null,
            ) { Text("顺延至此日") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
