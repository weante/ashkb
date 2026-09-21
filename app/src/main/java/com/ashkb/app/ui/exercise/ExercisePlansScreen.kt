package com.ashkb.app.ui.exercise

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EventNote
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle

import com.ashkb.app.R
import com.ashkb.app.data.entity.ExercisePlan
import com.ashkb.app.domain.ExercisePlanProgress
import com.ashkb.app.domain.ExercisePlanTemplates
import com.ashkb.app.ui.components.EmptyState
import com.ashkb.app.ui.components.ScreenTopBar
import com.ashkb.app.ui.components.SectionCard
import com.ashkb.app.ui.components.StatusChip
import com.ashkb.app.ui.theme.Size
import com.ashkb.app.ui.theme.Spacing
import com.ashkb.app.ui.theme.StatusTone
import com.ashkb.app.ui.theme.accent

/**
 * B7（v1.0.39）周期康复计划：4–12 周按周递进的模板列表 + 启用 / 停用 + 完成度反算。
 *
 * 页面只呈现仓储与纯函数算好的结论：模板动作不写死（仍由 `ExerciseEngine` 按当日分期生成），
 * 完成度由 `ExercisePlanProgress` 依据运动打卡日期去重反算，避免第二份真相。
 */
@Composable
fun ExercisePlansScreen(vm: ExercisePlansViewModel, onBack: () -> Unit) {
    val ui by vm.uiState.collectAsStateWithLifecycle()
    // 每周结构展开态：多张卡可同时展开（记 id 集合，卡片复用不串状态）
    var expandedIds by remember { mutableStateOf(emptySet<String>()) }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(title = stringResource(R.string.plans_title), onBack = onBack)

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Spacing.lg, end = Spacing.lg,
                top = Spacing.md, bottom = Spacing.xxl,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            // ---- 模板入口卡：种子按钮 + 结果常驻提示（不占 Snackbar） ----
            item {
                SectionCard(
                    title = stringResource(R.string.plans_title),
                    subtitle = stringResource(R.string.plans_entry_sub),
                    action = {
                        Button(
                            onClick = { vm.seed() },
                            modifier = Modifier.heightIn(min = Size.touchMin),
                        ) { Text(stringResource(R.string.plans_seed_button)) }
                    },
                ) {
                    if (ui.plans.isEmpty()) {
                        EmptyState(
                            icon = Icons.Rounded.EventNote,
                            title = stringResource(R.string.plans_empty),
                        )
                    }
                    // 种子结果常驻：>0 本次新增条数；=0 表示模板已齐全
                    ui.seededCount?.let { added ->
                        Text(
                            if (added > 0) stringResource(R.string.plans_seed_done, added)
                            else stringResource(R.string.plans_seed_all),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // ---- 计划卡：按周数升序（DAO 已排序：4 周 → 12 周） ----
            items(ui.plans, key = { it.id }) { plan ->
                PlanCard(
                    plan = plan,
                    // 进度只属于在用计划（其它计划没有起算日，算出来的周次无意义）
                    progress = if (plan.isActive) ui.progress else null,
                    expanded = plan.id in expandedIds,
                    onToggle = {
                        expandedIds = if (plan.id in expandedIds) expandedIds - plan.id
                        else expandedIds + plan.id
                    },
                    onActivate = { vm.activate(plan.id) },
                    onDeactivate = { vm.deactivate(plan.id) },
                )
            }

            item { Spacer(Modifier.height(Spacing.xxl)) }
        }
    }
}

/** stageMode → 中文标签（any / stable / flare）。 */
@Composable
private fun stageLabel(mode: String): String = stringResource(
    when (mode) {
        "stable" -> R.string.plans_stage_stable
        "flare" -> R.string.plans_stage_flare
        else -> R.string.plans_stage_any
    }
)

/**
 * 单张计划卡：标题 + 分期 + 周数 + 在用徽标，在用计划附完成度进度条；
 * 点击结构行展开该计划的逐周结构（grade / 每周目标天数 + 该周提示）。
 */
@Composable
private fun PlanCard(
    plan: ExercisePlan,
    progress: ExercisePlanProgress.Progress?,
    expanded: Boolean,
    onToggle: () -> Unit,
    onActivate: () -> Unit,
    onDeactivate: () -> Unit,
) {
    // week_structure 只在计划变化时解析一次（脏数据由 parse 兜底：解析不到的周直接忽略）
    val spec = remember(plan.weekStructure) { ExercisePlanTemplates.parse(plan.weekStructure) }
    val firstWeek = spec.firstOrNull()
    val lastWeek = spec.lastOrNull()

    SectionCard(
        title = plan.title,
        // 副标题用末周结构承载「周数」（末周序号即总周数），结构与分级一眼可读
        subtitle = buildString {
            append(stageLabel(plan.stageMode))
            lastWeek?.let {
                append(" · ")
                append(stringResource(R.string.plans_week_item, it.week, it.grade, it.days))
            }
        },
        action = {
            if (plan.isActive) {
                StatusChip(text = stringResource(R.string.plans_active_badge), tone = StatusTone.Success)
            }
        },
    ) {
        // ---- 在用计划的完成度：本周 / 累计 / 百分比 ----
        if (progress != null) {
            if (progress.finished) {
                Text(
                    stringResource(R.string.plans_progress_finished, progress.totalWeeks),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                // 与 ReportScreen 概览卡同口径：数值与进度条同色，轨道用 surfaceContainerHigh
                val accent = StatusTone.Brand.accent()
                Text(
                    stringResource(R.string.plans_progress_week, progress.currentWeek, progress.totalWeeks),
                    style = MaterialTheme.typography.titleSmall,
                    color = accent,
                )
                Spacer(Modifier.height(Spacing.xxs))
                Text(
                    stringResource(R.string.plans_progress_week_days, progress.weekDoneDays, progress.weekTargetDays),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(R.string.plans_progress_overall, progress.overallDoneDays, progress.overallTargetDays, progress.pct),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Spacing.sm))
                LinearProgressIndicator(
                    progress = { progress.pct / 100f },
                    modifier = Modifier.fillMaxWidth(),
                    color = accent,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                )
            }
            Spacer(Modifier.height(Spacing.md))
        }

        // ---- 启用 / 停用：同一时刻只允许一个在用计划（仓储内先全部停用） ----
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            if (plan.isActive) {
                OutlinedButton(
                    onClick = onDeactivate,
                    modifier = Modifier.heightIn(min = Size.touchMin),
                ) { Text(stringResource(R.string.plans_deactivate)) }
            } else {
                Button(
                    onClick = onActivate,
                    modifier = Modifier.heightIn(min = Size.touchMin),
                ) { Text(stringResource(R.string.plans_activate)) }
            }
        }

        if (spec.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.sm))
            // ---- 结构行：整行可点展开 / 收起；收起态用首周结构做摘要（兼作本行语义文案） ----
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .heightIn(min = Size.touchMin),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!expanded && firstWeek != null) {
                    Text(
                        stringResource(R.string.plans_week_item, firstWeek.week, firstWeek.grade, firstWeek.days),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                Icon(
                    imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = null,   // 装饰性：整行可点，语义见行内摘要
                    modifier = Modifier.size(Size.iconMd),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // ---- 展开：逐周结构 + 该周提示（小字） ----
            if (expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    spec.forEach { week ->
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                            Text(
                                stringResource(R.string.plans_week_item, week.week, week.grade, week.days),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            if (week.note.isNotBlank()) {
                                Text(
                                    week.note,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
