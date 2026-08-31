package com.ashkb.app.ui.exercise

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ashkb.app.data.entity.ExerciseLog
import com.ashkb.app.domain.ExerciseEngine
import com.ashkb.app.ui.knowledge.KbDetailDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseScreen(vm: ExerciseViewModel, onBack: () -> Unit) {
    val ui by vm.uiState.collectAsState()
    val todayLogs by vm.todayLogs.collectAsState()
    val pending by vm.feedbackPending.collectAsState()

    var checkInTarget by remember { mutableStateOf<ExerciseEngine.ExerciseCard?>(null) }
    var feedbackTarget by remember { mutableStateOf<ExerciseLog?>(null) }
    var kbDetail by remember { mutableStateOf<com.ashkb.app.data.entity.KbEntry?>(null) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("今日运动") },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") }
            },
        )
        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { Spacer(Modifier.height(4.dp)) }

            // ---- 分期横幅（R27 矩阵状态） ----
            item {
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (ui.stage == "stable")
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                        else MaterialTheme.colorScheme.tertiary.copy(alpha = 0.10f)
                    ),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            when (ui.stage) {
                                "stable" -> "缓解期处方"
                                "active" -> "活动期处方（保守过滤已生效）"
                                else -> "分期未设置（按活动期保守过滤）"
                            },
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            buildString {
                                append(if (ui.stage == "stable") "缓解期：L1/L2 按指南剂量渐进；L3 黑榜仍拦截。" else "活动期：L1 轻柔项为主，L2 多数暂停或减量，L3 拦截。")
                                if (ui.cervicalInvolved) append("\n颈椎受累已标记：泳姿与颈部动作条目自动收紧（蛙泳 / 倒立类拦截）。")
                                if (ui.stage == "unknown") append("可在「我的 → 健康档案」设置分期与颈椎受累。")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }

            // ---- R21 次日反馈 ----
            if (pending.isNotEmpty()) {
                item {
                    Card(
                        Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.10f)),
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text("昨日运动反馈待填", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.tertiary)
                            Text(
                                "填写后系统按「运动后 2 小时疼痛规则」（exc-010）判读是否需要减量。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(8.dp))
                            pending.forEach { log ->
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(log.excName, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                    OutlinedButton(onClick = { feedbackTarget = log }) { Text("填反馈") }
                                }
                            }
                        }
                    }
                }
            }

            // ---- 今日已完成 ----
            if (todayLogs.isNotEmpty()) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp)) {
                            Text("今日已打卡 ${todayLogs.size} 项", style = MaterialTheme.typography.titleSmall)
                            todayLogs.forEach { log ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                                    Text(
                                        buildString {
                                            append(log.excName)
                                            log.durationMin?.let { append(" · $it 分钟") }
                                            if (log.fbPainChange != null) append(" · 已反馈")
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text("✓", color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }

            // ---- 当日处方（红榜） ----
            item {
                Text("今日处方", style = MaterialTheme.typography.titleMedium)
                Text(
                    "按 R27 矩阵（L1/L2/L3 × 活动期/缓解期）过滤生成",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items2(ui.plan) { card ->
                PlanCard(
                    card = card,
                    onCheckIn = { checkInTarget = card },
                    onDetail = { kbDetail = card.entry },
                )
            }

            // ---- 黑榜拦截区 ----
            item {
                Spacer(Modifier.height(6.dp))
                Text("黑榜 · 条件化拦截", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                Text(
                    "默认拦截非禁止——条件化规则详见各条目",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items2(ui.blocked) { card ->
                BlockedCard(card = card, onDetail = { kbDetail = card.entry })
            }

            item { Spacer(Modifier.height(24.dp)) }
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
        FeedbackDialog(
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

/** LazyColumn items 替代（避免与 foundation.items 命名冲突） */
private fun <T> androidx.compose.foundation.lazy.LazyListScope.items2(
    list: List<T>,
    content: @Composable (T) -> Unit,
) {
    list.forEach { item { content(it) } }
}

@Composable
private fun PlanCard(
    card: ExerciseEngine.ExerciseCard,
    onCheckIn: () -> Unit,
    onDetail: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(card.entry.title, style = MaterialTheme.typography.titleSmall)
                    Text(
                        buildString {
                            append("${card.grade} · ")
                            append(
                                when (card.verdict) {
                                    "recommend" -> "推荐"
                                    "allow" -> "允许"
                                    "downgrade" -> "减量执行"
                                    "conditional" -> "条件允许"
                                    else -> card.verdict
                                }
                            )
                            if (card.movements.isNotEmpty()) append(" · ${card.movements.joinToString("、")}")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Badge(
                    when (card.verdict) {
                        "recommend" -> "推荐"
                        "downgrade" -> "减量"
                        "conditional" -> "条件"
                        else -> "允许"
                    },
                    if (card.verdict == "recommend") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(card.hint, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onCheckIn) { Text("打卡") }
                OutlinedButton(onClick = onDetail) { Text("详情") }
            }
        }
    }
}

@Composable
private fun BlockedCard(card: ExerciseEngine.ExerciseCard, onDetail: () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.07f)),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(card.entry.title, style = MaterialTheme.typography.titleSmall)
                    Text(
                        "L3 · ${card.movements.joinToString("、")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Badge("拦截", MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(6.dp))
            Text(card.hint, style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = onDetail, modifier = Modifier.padding(top = 8.dp)) { Text("查看条目") }
        }
    }
}

@Composable
private fun Badge(text: String, color: androidx.compose.ui.graphics.Color) {
    Card(colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f))) {
        Text(
            text, Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.Bold,
        )
    }
}

// ---------------------------------------------------------------------------
// 弹窗：打卡 / 次日反馈
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
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = duration,
                    onValueChange = { duration = it.filter { c -> c.isDigit() }.take(3) },
                    label = { Text("时长（分钟）") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text("强度自测（说话试验：能自如说话=中等，有点费力=高）", style = MaterialTheme.typography.labelSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
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
@Composable
private fun FeedbackDialog(
    excName: String,
    onSave: (String?, String?, Boolean?, String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var pain by remember { mutableStateOf<String?>(null) }
    var stiffness by remember { mutableStateOf<String?>(null) }
    var soreness by remember { mutableStateOf<Boolean?>(null) }
    var note by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("昨日运动后感受：$excName") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("与不运动的平常日相比", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                FeedbackRadio("运动后疼痛", pain) { pain = it }
                FeedbackRadio("次日晨僵", stiffness) { stiffness = it }
                Spacer(Modifier.height(6.dp))
                Text("加重时：更像哪种？", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    label = { Text("备注（可选）") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                if (pain != null && stiffness != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        ExerciseEngine.interpretFeedback(pain, stiffness, soreness),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(pain, stiffness, soreness, note.ifBlank { null }) },
                enabled = pain != null && stiffness != null,
            ) { Text("保存反馈") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun FeedbackRadio(label: String, value: String?, onChange: (String) -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("better" to "好转", "same" to "不变", "worse" to "加重").forEach { (k, l) ->
                FilterChip(selected = value == k, onClick = { onChange(k) }, label = { Text(l) })
            }
        }
    }
}
