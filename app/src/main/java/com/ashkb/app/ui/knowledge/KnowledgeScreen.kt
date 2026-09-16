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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.ashkb.app.data.entity.KbEntry
import com.ashkb.app.ui.components.AlertBanner
import com.ashkb.app.ui.components.EmptyState
import com.ashkb.app.ui.components.ScreenTopBar
import com.ashkb.app.ui.components.StatusChip
import com.ashkb.app.ui.theme.Size
import com.ashkb.app.ui.theme.Spacing
import com.ashkb.app.ui.theme.StatusTone
import java.time.LocalDate

/** K 知识库：47 条种子的浏览 / 搜索 / 详情 + 复核到期提示 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KnowledgeScreen(vm: KnowledgeViewModel) {
    val ui by vm.uiState.collectAsState()
    var detail by remember { mutableStateOf<KbEntry?>(null) }
    val today = LocalDate.now().toString()

    LaunchedEffect(Unit) { vm.refreshReviewCheck() }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(title = "AS 知识库（${ui.entries.size} 条）")

        // 搜索 + 分类筛选固定吸顶，滚动不消失
        Column(Modifier.padding(horizontal = Spacing.lg)) {
            OutlinedTextField(
                value = ui.query,
                onValueChange = { vm.setQuery(it) },
                label = { Text("搜索标题 / 摘要 / 内容") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(Spacing.sm))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                KB_CATEGORIES.forEach { (key, label) ->
                    FilterChip(
                        selected = ui.category == key,
                        onClick = { vm.setCategory(key) },
                        label = { Text(label) },
                        modifier = Modifier.heightIn(min = Size.touchMin),
                    )
                }
            }
            if (ui.overdueCount > 0) {
                Spacer(Modifier.height(Spacing.sm))
                AlertBanner(
                    tone = StatusTone.Warning,
                    icon = Icons.Rounded.Schedule,
                    title = "${ui.overdueCount} 条内容已过复核日",
                    body = "就医核对时以医生意见为准",
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
                        title = "没有匹配的条目",
                        body = "换个关键词试试（如药名、运动名、症状）",
                    )
                }
            }
            ui.entries.forEach { entry ->
                item(key = entry.id) {
                    KbListCard(entry = entry, overdue = entry.reviewDue < today, onClick = { detail = entry })
                }
            }
        }
    }

    detail?.let { KbDetailDialog(entry = it, onDismiss = { detail = null }) }
}

private fun categoryLabel(category: String): String = when (category) {
    "interaction" -> "相互作用"; "food_drug" -> "食物药物"; "exercise" -> "运动"
    "emergency" -> "应急"; "edu" -> "教育"; else -> category
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
private fun KbListCard(entry: KbEntry, overdue: Boolean, onClick: () -> Unit) {
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
                    StatusChip(text = "高风险", tone = StatusTone.Danger, icon = Icons.Rounded.WarningAmber)
                }
                if (overdue) {
                    StatusChip(text = "已过复核日", tone = StatusTone.Warning, icon = Icons.Rounded.Schedule)
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
