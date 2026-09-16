package com.ashkb.app.ui.exercise

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.style.TextOverflow
import com.ashkb.app.data.entity.ExerciseLog
import com.ashkb.app.domain.ClinicalThresholds
import com.ashkb.app.domain.ExerciseEngine
import com.ashkb.app.ui.components.AlertBanner
import com.ashkb.app.ui.components.DividerList
import com.ashkb.app.ui.components.EmptyState
import com.ashkb.app.ui.components.ScreenTopBar
import com.ashkb.app.ui.components.SectionCard
import com.ashkb.app.ui.components.StatTile
import com.ashkb.app.ui.components.StatusChip
import com.ashkb.app.ui.knowledge.KbDetailDialog
import com.ashkb.app.ui.theme.Size
import com.ashkb.app.ui.theme.Spacing
import com.ashkb.app.ui.theme.StatusTone
import com.ashkb.app.ui.theme.accent
import com.ashkb.app.ui.theme.colors
import com.ashkb.app.ui.theme.container

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseScreen(vm: ExerciseViewModel, onBack: () -> Unit) {
    val ui by vm.uiState.collectAsState()
    val todayLogs by vm.todayLogs.collectAsState()
    val pending by vm.feedbackPending.collectAsState()
    val yesterday by vm.yesterdaySymptom.collectAsState()

    var checkInTarget by remember { mutableStateOf<ExerciseEngine.ExerciseCard?>(null) }
    var feedbackTarget by remember { mutableStateOf<ExerciseLog?>(null) }
    var kbDetail by remember { mutableStateOf<com.ashkb.app.data.entity.KbEntry?>(null) }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(title = "今日运动", onBack = onBack)
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = Spacing.lg, end = Spacing.lg,
                top = Spacing.md, bottom = Spacing.md,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            // ---- 黑榜拦截区：安全信息必须先看到，置于处方之前（方案 §10.5） ----
            items(ui.blocked, key = { "blocked-${it.entry.id}" }) { card ->
                AlertBanner(
                    tone = StatusTone.Danger,
                    icon = Icons.Rounded.Block,
                    title = card.entry.title,
                    body = "L3 · ${card.movements.joinToString("、")}｜${card.hint}",
                    actionLabel = "查看条目",
                    onAction = { kbDetail = card.entry },
                )
            }

            // ---- 当日处方 hero：大号结论 + 分期色带 + 判读依据 ----
            item {
                PrescriptionHero(ui = ui, yesterday = yesterday)
            }

            // ---- R21 次日反馈待填 ----
            if (pending.isNotEmpty()) {
                item {
                    SectionCard(
                        title = "昨日运动反馈待填",
                        subtitle = "填写后系统按「运动后 2 小时疼痛规则」（exc-010）判读是否需要减量",
                        container = StatusTone.Warning.container(),
                    ) {
                        DividerList(pending, key = { it.id }) { log ->
                            Text(
                                log.excName,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedButton(onClick = { feedbackTarget = log }) { Text("填反馈") }
                        }
                    }
                }
            }

            // ---- 今日已打卡 ----
            if (todayLogs.isNotEmpty()) {
                item {
                    SectionCard(title = "今日已打卡 ${todayLogs.size} 项") {
                        DividerList(todayLogs, key = { it.id }) { log ->
                            Text(
                                buildString {
                                    append(log.excName)
                                    log.durationMin?.let { append(" · $it 分钟") }
                                    if (log.fbPainChange != null) append(" · 已反馈")
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = "已完成",
                                modifier = Modifier.size(Size.iconSm),
                                tint = StatusTone.Success.accent(),
                            )
                        }
                    }
                }
            }

            // ---- 今日处方（红榜） ----
            if (ui.plan.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Rounded.Schedule,
                        title = "今日没有推荐运动",
                        body = "完善健康档案中的病情分期后，会按 R27 矩阵生成当日处方",
                    )
                }
            } else {
                item {
                    Text("今日处方", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "按 R27 矩阵（L1/L2/L3 × 活动期/缓解期）过滤生成",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(ui.plan, key = { it.entry.id }) { card ->
                    PlanCard(
                        card = card,
                        onCheckIn = { checkInTarget = card },
                        onDetail = { kbDetail = card.entry },
                    )
                }
            }

            item { Spacer(Modifier.height(Spacing.xxl)) }
        }
    }

    checkInTarget?.let { card ->
        CheckInDialog(
            excName = card.entry.title,
            defaultDuration = null,
            onConfirm = { duration, intensity, note ->
                vm.checkIn(card, duration, intensity, note)
                checkInTarget = null
            },
            onDismiss = { checkInTarget = null },
        )
    }

    feedbackTarget?.let { log ->
        FeedbackSheet(
            excName = log.excName,
            onSave = { pain, stiffness, soreness, note ->
                vm.saveFeedback(log.id, pain, stiffness, soreness, note)
                feedbackTarget = null
            },
            onDismiss = { feedbackTarget = null },
        )
    }

    kbDetail?.let { KbDetailDialog(entry = it, onDismiss = { kbDetail = null }) }
}

/** 处方 hero：大号结论文字 + 分期状态色带 + 昨日判读依据（疼痛 / 晨僵 / 体温）。 */
@Composable
private fun PrescriptionHero(ui: com.ashkb.app.ui.exercise.ExerciseUiState, yesterday: com.ashkb.app.data.entity.SymptomDaily?) {
    val tone = when (ui.stage) {
        "stable" -> StatusTone.Success
        "active" -> StatusTone.Warning
        else -> StatusTone.Neutral
    }
    val (bg, fg) = tone.colors()
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            color = bg,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = Size.heroMinHeight)
                    .padding(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Text(
                    when (ui.stage) {
                        "stable" -> "缓解期处方"
                        "active" -> "活动期处方（保守过滤已生效）"
                        else -> "分期未设置（按活动期保守过滤）"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = fg,
                )
                Text(
                    if (ui.plan.isEmpty()) "今日暂无推荐运动"
                    else "今日适合：${ui.plan.take(2).joinToString("、") { it.entry.title }}",
                    style = MaterialTheme.typography.headlineMedium,
                    color = fg,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    buildString {
                        append(if (ui.stage == "stable") "缓解期：L1/L2 按指南剂量渐进；L3 黑榜仍拦截。" else "活动期：L1 轻柔项为主，L2 多数暂停或减量，L3 拦截。")
                        if (ui.cervicalInvolved) append("颈椎受累已标记：泳姿与颈部动作条目自动收紧。")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = fg,
                )
            }
        }

        // 判读依据：昨日症状三项
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Box(Modifier.weight(1f)) {
                val pain = yesterday?.painScore
                StatTile(
                    label = "昨日疼痛",
                    value = pain?.toString() ?: "未记录",
                    unit = pain?.let { "/ 10" },
                    tone = when {
                        pain == null -> StatusTone.Neutral
                        pain >= ClinicalThresholds.PAIN_SEVERE -> StatusTone.Danger
                        pain >= ClinicalThresholds.PAIN_MODERATE -> StatusTone.Warning
                        else -> StatusTone.Success
                    },
                )
            }
            Box(Modifier.weight(1f)) {
                StatTile(
                    label = "昨日晨僵",
                    value = yesterday?.morningStiffnessMin?.toString() ?: "未记录",
                    unit = yesterday?.morningStiffnessMin?.let { "分" },
                )
            }
            Box(Modifier.weight(1f)) {
                val feverish = yesterday?.feverish == true
                StatTile(
                    label = "昨日体温",
                    value = when {
                        yesterday?.feverTemp != null -> "%.1f".format(yesterday.feverTemp)
                        feverish -> "自觉发热"
                        else -> "无热"
                    },
                    unit = yesterday?.feverTemp?.let { "℃" },
                    tone = if (feverish) StatusTone.Danger else StatusTone.Neutral,
                )
            }
        }
    }
}

private fun verdictLabel(v: String) = when (v) {
    "recommend" -> "推荐"; "allow" -> "允许"; "downgrade" -> "减量执行"; "conditional" -> "条件允许"; else -> v
}

private fun verdictTone(v: String) = when (v) {
    "recommend" -> StatusTone.Success
    "allow" -> StatusTone.Info
    "downgrade", "conditional" -> StatusTone.Warning
    else -> StatusTone.Neutral
}

@Composable
private fun PlanCard(
    card: ExerciseEngine.ExerciseCard,
    onCheckIn: () -> Unit,
    onDetail: () -> Unit,
) {
    SectionCard(
        title = card.entry.title,
        subtitle = buildString {
            append("${card.grade} · ${verdictLabel(card.verdict)}")
            if (card.movements.isNotEmpty()) append(" · ${card.movements.joinToString("、")}")
        },
        action = {
            StatusChip(
                text = when (card.verdict) {
                    "recommend" -> "推荐"; "downgrade" -> "减量"; "conditional" -> "条件"; else -> "允许"
                },
                tone = verdictTone(card.verdict),
            )
        },
    ) {
        Text(card.hint, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(Spacing.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Button(onClick = onCheckIn) { Text("打卡") }
            OutlinedButton(onClick = onDetail) { Text("详情") }
        }
    }
}

// ---------------------------------------------------------------------------
// 弹窗：打卡（字段少，AlertDialog）/ 次日反馈（迁 ModalBottomSheet）
// ---------------------------------------------------------------------------

@Composable
private fun CheckInDialog(
    excName: String,
    defaultDuration: Int?,
    onConfirm: (Int?, String?, String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var duration by remember { mutableStateOf(defaultDuration?.toString() ?: "") }
    var intensity by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("打卡：$excName") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedTextField(
                    value = duration,
                    onValueChange = { duration = it.filter { c -> c.isDigit() }.take(3) },
                    label = { Text("时长（分钟）") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Text(
                    "强度自测（说话试验：能自如说话=中等，有点费力=高）",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    listOf("low" to "低", "moderate" to "中", "high" to "高").forEach { (k, l) ->
                        FilterChip(selected = intensity == k, onClick = { intensity = k }, label = { Text(l) })
                    }
                }
                OutlinedTextField(
                    value = note, onValueChange = { note = it },
                    label = { Text("备注（可选）") }, modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(duration.toIntOrNull(), intensity, note.ifBlank { null }) }) { Text("完成打卡") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/** R21 反馈：疼痛 / 晨僵变化 + 酸痛 vs 炎症加重区分（exc-010 判读） */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FeedbackSheet(
    excName: String,
    onSave: (String?, String?, Boolean?, String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var pain by remember { mutableStateOf<String?>(null) }
    var stiffness by remember { mutableStateOf<String?>(null) }
    var soreness by remember { mutableStateOf<Boolean?>(null) }
    var note by remember { mutableStateOf("") }

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
            Text("昨日运动后感受：$excName", style = MaterialTheme.typography.titleLarge)
            Text(
                "与不运动的平常日相比",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FeedbackRadio("运动后疼痛", pain) { pain = it }
            FeedbackRadio("次日晨僵", stiffness) { stiffness = it }

            Text(
                "加重时：更像哪种？",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                FilterChip(
                    selected = soreness == true, onClick = { soreness = true },
                    label = { Text("肌肉酸痛（延迟性）") },
                )
                FilterChip(
                    selected = soreness == false, onClick = { soreness = false },
                    label = { Text("关节 / 脊柱炎症感") },
                )
            }
            OutlinedTextField(
                value = note, onValueChange = { note = it },
                label = { Text("备注（可选）") }, modifier = Modifier.fillMaxWidth(),
            )
            if (pain != null && stiffness != null) {
                Text(
                    ExerciseEngine.interpretFeedback(pain, stiffness, soreness),
                    style = MaterialTheme.typography.bodyMedium,
                    color = StatusTone.Info.accent(),
                )
            }
            Button(
                onClick = { onSave(pain, stiffness, soreness, note.ifBlank { null }) },
                enabled = pain != null && stiffness != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Size.touchMin),
            ) { Text("保存反馈") }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FeedbackRadio(label: String, value: String?, onChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            listOf("better" to "好转", "same" to "不变", "worse" to "加重").forEach { (k, l) ->
                FilterChip(selected = value == k, onClick = { onChange(k) }, label = { Text(l) })
            }
        }
    }
}
