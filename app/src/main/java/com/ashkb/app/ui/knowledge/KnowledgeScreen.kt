package com.ashkb.app.ui.knowledge

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle

import com.ashkb.app.R
import com.ashkb.app.data.entity.KbEntry
import com.ashkb.app.domain.KbSearch
import com.ashkb.app.ui.components.AlertBanner
import com.ashkb.app.ui.components.EmptyState
import com.ashkb.app.ui.components.ScreenTopBar
import com.ashkb.app.ui.components.StatusChip
import com.ashkb.app.ui.theme.Size
import com.ashkb.app.ui.theme.Spacing
import com.ashkb.app.ui.theme.StatusTone
import kotlinx.coroutines.delay

/** K 知识库：47 条种子的浏览 / 搜索 / 详情 + 复核到期提示 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KnowledgeScreen(vm: KnowledgeViewModel) {
    val ui by vm.uiState.collectAsStateWithLifecycle()
    var detail by remember { mutableStateOf<KbEntry?>(null) }
    val todayDate by vm.date.collectAsStateWithLifecycle()
    val today = remember(todayDate) { todayDate.toString() }
    val noteCount by vm.noteCount.collectAsStateWithLifecycle()

    // 搜索框本地态 + 防抖：避免每敲一键就重算 uiState 导致整屏重组（VM 内防抖只护住了 DAO 查询）
    var queryText by rememberSaveable { mutableStateOf(ui.query) }
    LaunchedEffect(queryText) {
        if (queryText == ui.query) return@LaunchedEffect
        delay(KbSearch.DEBOUNCE_MS)
        vm.setQuery(queryText)
    }

    LaunchedEffect(Unit) { vm.refreshReviewCheck() }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(title = stringResource(R.string.knowledge_title_count, ui.entries.size))

        // 搜索 + 分类筛选固定吸顶，滚动不消失
        Column(Modifier.padding(horizontal = Spacing.lg)) {
            OutlinedTextField(
                value = queryText,
                onValueChange = { queryText = it },
                label = { Text(stringResource(R.string.knowledge_search_hint)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(Spacing.sm))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                KB_CATEGORIES.forEach { (key, labelRes) ->
                    FilterChip(
                        selected = ui.category == key,
                        onClick = { vm.setCategory(key) },
                        label = { Text(stringResource(labelRes)) },
                        modifier = Modifier.heightIn(min = Size.touchMin),
                    )
                }
            }
            // B2：把「有备注」的条数放在筛选之后、到期告警之前——先看到自己积累的内容，再看到需要处理的异常
            if (noteCount > 0) {
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    stringResource(R.string.kb_note_count, noteCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (ui.overdueCount > 0) {
                Spacer(Modifier.height(Spacing.sm))
                AlertBanner(
                    tone = StatusTone.Warning,
                    icon = Icons.Rounded.Schedule,
                    title = stringResource(R.string.knowledge_overdue_count, ui.overdueCount),
                    body = stringResource(R.string.common_doctor_final_note),
                )
            }
            Spacer(Modifier.height(Spacing.xs))
        }

        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = Spacing.lg),
            contentPadding = PaddingValues(bottom = Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            if (ui.entries.isEmpty() && ui.query.isNotBlank()) {
                item {
                    EmptyState(
                        icon = Icons.Rounded.SearchOff,
                        title = stringResource(R.string.knowledge_no_match),
                        body = stringResource(R.string.knowledge_search_empty),
                    )
                }
            }
            ui.entries.forEach { entry ->
                item(key = entry.id) {
                    KbListCard(
                        entry = entry,
                        overdue = entry.reviewDue < today,
                        hasNote = !entry.userNote.isNullOrBlank(),
                        onClick = { detail = entry },
                    )
                }
            }
        }
    }

    // 用列表里的最新 entry 渲染弹窗：备注保存后 Room 会推新实例，若沿用点开那一刻的旧对象，
    // 弹窗内的 userNote 永远停在旧值，保存看起来「没生效」。
    detail?.let { opened ->
        val live = ui.entries.firstOrNull { it.id == opened.id } ?: opened
        KbDetailDialog(
            entry = live,
            onSaveNote = { vm.saveNote(live.id, it) },
            onDismiss = { detail = null },
        )
    }
}

@Composable
private fun categoryLabel(category: String): String = when (category) {
    "interaction" -> stringResource(R.string.knowledge_interaction_title); "food_drug" -> stringResource(R.string.knowledge_food_drug_section); "exercise" -> stringResource(R.string.exercise_tab)
    "emergency" -> stringResource(R.string.emergency_tab); "edu" -> stringResource(R.string.knowledge_education_tag); else -> category
}

/** 类别固定映射（方案 §10.9）：药物 Info、食物 Brand、指南 Neutral、相互作用 Warning。 */
private fun categoryTone(category: String): StatusTone = when (category) {
    "interaction" -> StatusTone.Warning
    "food_drug" -> StatusTone.Brand
    "exercise" -> StatusTone.Info
    "emergency" -> StatusTone.Danger
    else -> StatusTone.Neutral
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KbListCard(
    entry: KbEntry,
    overdue: Boolean,
    hasNote: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                StatusChip(
                    text = categoryLabel(entry.category),
                    tone = categoryTone(entry.category),
                )
                Spacer(Modifier.weight(1f))
                if (entry.severityLevel == "high") {
                    StatusChip(text = stringResource(R.string.knowledge_high_risk), tone = StatusTone.Danger, icon = Icons.Rounded.WarningAmber)
                }
                // B2：有备注的条目给个中性标签，方便在长列表里找回自己做过批注的那几条
                if (hasNote) {
                    StatusChip(text = stringResource(R.string.kb_note_has_tag), tone = StatusTone.Info)
                }
                if (overdue) {
                    StatusChip(text = stringResource(R.string.checkup_overdue_short), tone = StatusTone.Warning, icon = Icons.Rounded.Schedule)
                }
            }
            Text(
                entry.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                entry.summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${entry.sourceTier} · ${entry.sourceName}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
