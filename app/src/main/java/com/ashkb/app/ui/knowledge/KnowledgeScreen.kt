package com.ashkb.app.ui.knowledge

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ashkb.app.data.entity.KbEntry
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
        TopAppBar(title = { Text("AS 知识库（${ui.entries.size} 条）") })

        Column(Modifier.padding(horizontal = 16.dp)) {
            OutlinedTextField(
                value = ui.query,
                onValueChange = { vm.setQuery(it) },
                label = { Text("搜索标题 / 摘要 / 内容") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                KB_CATEGORIES.forEach { (key, label) ->
                    FilterChip(
                        selected = ui.category == key,
                        onClick = { vm.setCategory(key) },
                        label = { Text(label) },
                    )
                }
            }
            if (ui.overdueCount > 0) {
                Spacer(Modifier.height(8.dp))
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.10f)),
                ) {
                    Text(
                        "${ui.overdueCount} 条内容已过复核日——就医核对时以医生意见为准",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.padding(10.dp),
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
        }

        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (ui.entries.isEmpty() && ui.query.isNotBlank()) {
                item {
                    Text(
                        "无匹配条目——换个关键词试试（如药名、运动名、症状）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 20.dp),
                    )
                }
            }
            ui.entries.forEach { entry ->
                item(key = entry.id) {
                    KbListCard(entry = entry, overdue = entry.reviewDue < today, onClick = { detail = entry })
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    detail?.let { KbDetailDialog(entry = it, onDismiss = { detail = null }) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KbListCard(entry: KbEntry, overdue: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (overdue) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
        ),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryBadge(entry.category)
                Spacer(Modifier.weight(1f))
                if (entry.severityLevel == "high") {
                    Text(
                        "高风险",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Text(
                entry.title,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 6.dp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                entry.summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                "${entry.sourceTier} · ${entry.sourceName}" + if (overdue) " · 已过复核日" else "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun CategoryBadge(category: String) {
    val (label, color) = when (category) {
        "interaction" -> "相互作用" to MaterialTheme.colorScheme.error
        "food_drug" -> "食物药物" to MaterialTheme.colorScheme.tertiary
        "exercise" -> "运动" to MaterialTheme.colorScheme.primary
        "emergency" -> "应急" to MaterialTheme.colorScheme.error
        "edu" -> "教育" to MaterialTheme.colorScheme.secondary
        else -> category to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Card(colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f))) {
        Text(
            label, Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold,
        )
    }
}
