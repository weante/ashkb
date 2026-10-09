package com.ashkb.app.ui.knowledge

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle

import com.ashkb.app.R
import com.ashkb.app.data.entity.KbEntry
import com.ashkb.app.domain.KbSearch
import com.ashkb.app.ui.components.AlertBanner
import com.ashkb.app.ui.components.EmptyState
import com.ashkb.app.ui.components.ScreenTitleCard
import com.ashkb.app.ui.components.TabChips
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
        // v1.2.3：标题从 `ScreenTopBar`（铺在米色底上的裸标题）改为**圆角卡片**，
        // 与下方那排白卡片同一套容器。见 PageChrome.kt 的说明。
        Spacer(Modifier.height(Spacing.sm))
        ScreenTitleCard(title = stringResource(R.string.knowledge_title_count, ui.entries.size))
        Spacer(Modifier.height(Spacing.md))

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
            // v1.2.3：分类标签从 `FilterChip`（浅琥珀选中，对比度低）改为与报表页签同一套
            // 深青胶囊（`TabChips`）。维护者原话「要和其他页面一样……颜色对比度要高」——
            // 报表页已经改过，这里跟着统一，两个页面的筛选/页签从此长得一样。
            TabChips(
                labels = KB_CATEGORIES.map { stringResource(it.second) },
                selectedIndex = KB_CATEGORIES.indexOfFirst { it.first == ui.category }.coerceAtLeast(0),
                onSelect = { i -> vm.setCategory(KB_CATEGORIES[i].first) },
            )
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
            // v1.2.7（批次 14 / R8）：把本屏已经算好的 `today` 传进去，弹窗不再自己读时钟
            today = today,
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

/**
 * 知识库卡片。
 *
 * v1.0.90（批次 15）：标签行由 `Row`（含 `Spacer(weight(1f))`）改 `FlowRow`——四枚胶囊
 * （类别 + 高风险 + 有备注 + 已过复核日）最坏组合固有宽度 ≈72+70+66+127 = **335dp**，
 * 卡片内宽：360dp 屏 296dp、320dp 屏 256dp。原写法里 `Spacer(weight(1f))` 前是无权重文本，
 * 胶囊按固有宽度测量、放不下只会逐字折行（末位「已过复核日」折 2–3 行、被 28dp 下限压扁）；
 * `FlowRow` 让整枚胶囊折到下一行。`Spacer(weight(1f))` 同时删掉：`FlowRow` 没有权重概念，
 * 且胶囊组本来就该左对齐贴排（不再需要"把右侧徽标顶到行尾"这一步——那正是挤压的来源）。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                StatusChip(
                    text = categoryLabel(entry.category),
                    tone = categoryTone(entry.category),
                )
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
            // v1.1.2：层级不再只显示裸字母——列表给「字母 + 短释义」一行放得下，
            // 最重要的是这行是**多数人唯一会看**的地方（详情未必有人点开）
            Text(
                "${tierShortLabel(entry.sourceTier)} · ${entry.sourceName}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
