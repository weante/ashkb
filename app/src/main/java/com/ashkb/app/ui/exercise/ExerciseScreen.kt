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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow

import com.ashkb.app.R
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
        ScreenTopBar(title = stringResource(R.string.exercise_today_title), onBack = onBack)
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
                    actionLabel = stringResource(R.string.knowledge_view_entry),
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
                        title = stringResource(R.string.exercise_feedback_pending),
                        subtitle = stringResource(R.string.exercise_pain_rule_note),
                        container = StatusTone.Warning.container(),
                    ) {
                        DividerList(pending, key = { it.id }) { log ->
                            Text(
                                log.excName,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedButton(onClick = { feedbackTarget = log }) { Text(stringResource(R.string.exercise_fill_feedback)) }
                        }
                    }
                }
            }

            // ---- 今日已打卡 ----
            if (todayLogs.isNotEmpty()) {
                item {
                    SectionCard(title = stringResource(R.string.exercise_today_done, todayLogs.size)) {
                        DividerList(todayLogs, key = { it.id }) { log ->
                            Text(
                                buildString {
                                    append(log.excName)
                                    log.durationMin?.let { append(stringResource(R.string.exercise_minutes_suffix, it)) }
                                    if (log.fbPainChange != null) append(stringResource(R.string.exercise_feedback_done_suffix))
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = stringResource(R.string.common_completed),
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
                        title = stringResource(R.string.exercise_none_today),
                        body = stringResource(R.string.exercise_no_stage_hint),
                    )
                }
            } else {
                item {
                    Text(stringResource(R.string.exercise_today_prescription), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.exercise_filter_rule_note),
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
        "controlled" -> StatusTone.Info
        "flare" -> StatusTone.Warning
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
                        "stable" -> stringResource(R.string.exercise_stable_prescription)
                        "controlled" -> stringResource(R.string.exercise_controlled_prescription)
                        "flare" -> stringResource(R.string.exercise_flare_prescription)
                        else -> stringResource(R.string.stage_not_set_filtered)
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = fg,
                )
                Text(
                    if (ui.plan.isEmpty()) stringResource(R.string.exercise_no_recommendation)
                    else "今日适合：${ui.plan.take(2).joinToString("、") { it.entry.title }}",
                    style = MaterialTheme.typography.headlineMedium,
                    color = fg,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    buildString {
                        append(
                            when (ui.stage) {
                                "stable" -> stringResource(R.string.exercise_stable_rule_note)
                                "controlled" -> stringResource(R.string.exercise_controlled_rule_note)
                                // 未设置分期按发作期保守过滤（R1：unknown → flare）
                                else -> stringResource(R.string.exercise_flare_rule_note)
                            }
                        )
                        if (ui.cervicalInvolved) append(stringResource(R.string.exercise_cervical_marked))
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
                    label = stringResource(R.string.symptom_yesterday_pain),
                    value = pain?.toString() ?: stringResource(R.string.common_not_recorded),
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
                    label = stringResource(R.string.symptom_yesterday_stiffness),
                    value = yesterday?.morningStiffnessMin?.toString() ?: stringResource(R.string.common_not_recorded),
                    unit = yesterday?.morningStiffnessMin?.let { stringResource(R.string.common_minute_unit) },
                )
            }
            Box(Modifier.weight(1f)) {
                val feverish = yesterday?.feverish == true
                StatTile(
                    label = stringResource(R.string.symptom_yesterday_fever),
                    value = when {
                        yesterday?.feverTemp != null -> "%.1f".format(yesterday.feverTemp)
                        feverish -> stringResource(R.string.symptom_fever_self)
                        else -> stringResource(R.string.symptom_no_fever)
                    },
                    unit = yesterday?.feverTemp?.let { "℃" },
                    tone = if (feverish) StatusTone.Danger else StatusTone.Neutral,
                )
            }
        }
    }
}

@Composable
private fun verdictLabel(v: String) = when (v) {
    "recommend" -> stringResource(R.string.common_recommended); "allow" -> stringResource(R.string.permission_allowed); "downgrade" -> stringResource(R.string.exercise_reduce_action); "conditional" -> stringResource(R.string.knowledge_condition_allowed); else -> v
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
    val verdict = verdictLabel(card.verdict)
    SectionCard(
        title = card.entry.title,
        subtitle = buildString {
            append("${card.grade} · $verdict")
            if (card.movements.isNotEmpty()) append(" · ${card.movements.joinToString("、")}")
        },
        action = {
            StatusChip(
                text = when (card.verdict) {
                    "recommend" -> stringResource(R.string.common_recommended); "downgrade" -> stringResource(R.string.exercise_reduce_amount); "conditional" -> stringResource(R.string.common_condition); else -> stringResource(R.string.permission_allowed)
                },
                tone = verdictTone(card.verdict),
            )
        },
    ) {
        Text(card.hint, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(Spacing.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Button(onClick = onCheckIn) { Text(stringResource(R.string.exercise_checkin_short)) }
            OutlinedButton(onClick = onDetail) { Text(stringResource(R.string.common_details)) }
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
        title = { Text(stringResource(R.string.exercise_checkin_title, excName)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedTextField(
                    value = duration,
                    onValueChange = { duration = it.filter { c -> c.isDigit() }.take(3) },
                    label = { Text(stringResource(R.string.exercise_duration_field)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Text(
                    stringResource(R.string.exercise_intensity_self_test),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    listOf("low" to stringResource(R.string.severity_low), "moderate" to stringResource(R.string.severity_moderate), "high" to stringResource(R.string.severity_high)).forEach { (k, l) ->
                        FilterChip(selected = intensity == k, onClick = { intensity = k }, label = { Text(l) })
                    }
                }
                OutlinedTextField(
                    value = note, onValueChange = { note = it },
                    label = { Text(stringResource(R.string.common_notes_optional)) }, modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(duration.toIntOrNull(), intensity, note.ifBlank { null }) }) { Text(stringResource(R.string.exercise_checkin_done)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
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
            Text(stringResource(R.string.exercise_feedback_title, excName), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.exercise_compare_baseline),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FeedbackRadio(stringResource(R.string.exercise_post_pain), pain) { pain = it }
            FeedbackRadio(stringResource(R.string.exercise_next_day_stiffness), stiffness) { stiffness = it }

            Text(
                stringResource(R.string.symptom_worse_pattern_label),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                FilterChip(
                    selected = soreness == true, onClick = { soreness = true },
                    label = { Text(stringResource(R.string.exercise_dom)) },
                )
                FilterChip(
                    selected = soreness == false, onClick = { soreness = false },
                    label = { Text(stringResource(R.string.symptom_inflammation)) },
                )
            }
            OutlinedTextField(
                value = note, onValueChange = { note = it },
                label = { Text(stringResource(R.string.common_notes_optional)) }, modifier = Modifier.fillMaxWidth(),
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
            ) { Text(stringResource(R.string.exercise_save_feedback)) }
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
            listOf("better" to stringResource(R.string.symptom_outcome_improved), "same" to stringResource(R.string.trend_flat_short), "worse" to stringResource(R.string.symptom_worse)).forEach { (k, l) ->
                FilterChip(selected = value == k, onClick = { onChange(k) }, label = { Text(l) })
            }
        }
    }
}
