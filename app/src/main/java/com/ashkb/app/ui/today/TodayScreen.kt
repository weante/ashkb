package com.ashkb.app.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ashkb.app.data.entity.Medication
import com.ashkb.app.data.entity.SkipReason
import com.ashkb.app.data.repo.TodayItem
import java.time.LocalDate
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

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column(Modifier.padding(top = 16.dp, bottom = 4.dp)) {
                val today = vm.date.format(DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINESE))
                Text(today, style = MaterialTheme.typography.titleLarge)
                Text(
                    profile?.let { "${it.displayName} · ${it.diagnosis}" } ?: "先完成健康档案建档",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // ---- P2 警报横幅（未读） ----
        if (alerts.isNotEmpty()) {
            item {
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.10f)
                    ),
                ) {
                    Row(
                        Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "${alerts.size} 条未读健康警报",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                            Text(
                                alerts.first().message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            )
                        }
                        Button(onClick = onOpenSymptom) { Text("查看") }
                    }
                }
            }
        }

        // ---- P2 快捷入口：症状 / 运动 ----
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickEntryCard(
                    title = "症状与自评",
                    status = if (symptomRecorded) "今日已记录" else "今日未记录",
                    done = symptomRecorded,
                    modifier = Modifier.weight(1f),
                    onClick = onOpenSymptom,
                )
                QuickEntryCard(
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
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("今日暂无用药计划", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            if (profile == null) "建档后添加您的药品，系统将生成每日打卡与提醒"
                            else "药单为空——到「我的 → 药单管理」添加药品",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = onMedListNeeded) { Text(if (profile == null) "去建档" else "去添加药品") }
                    }
                }
            }
        } else {
            items(items, key = { (it.med.id) + (it.slotKey ?: "prn") }) { item ->
                MedCheckCard(
                    item = item,
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
        item { Spacer(Modifier.height(24.dp)) }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickEntryCard(
    title: String,
    status: String,
    done: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Card(modifier = modifier, onClick = onClick) {
        Column(Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                status,
                style = MaterialTheme.typography.bodySmall,
                color = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MedCheckCard(
    item: TodayItem,
    onCheckIn: () -> Unit,
    onSkip: () -> Unit,
    onPostpone: () -> Unit,
    onPrnTaken: () -> Unit,
) {
    val med: Medication = item.med
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                item.done -> MaterialTheme.colorScheme.surfaceVariant
                item.skipped -> MaterialTheme.colorScheme.surfaceVariant
                else -> MaterialTheme.colorScheme.surface
            }
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "${med.name} ${med.dose}",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (item.done || item.skipped)
                            MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        buildString {
                            append(item.slotLabel)
                            if (med.route == "injection") append(" · 注射")
                            if (med.takeWithFood == "empty_stomach") append(" · 晨起空腹先于早餐")
                            if (med.storage != null) append(" · ${med.storage}")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StatusBadge(item)
            }

            when {
                item.done -> Text(
                    if (item.isLate) "已服用（晚于计划 30 分钟以上）" else "已服用",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                item.skipped -> Text(
                    "已跳过：${SkipReason.entries.firstOrNull { it.name.equals(item.log?.reason, true) }?.label ?: item.log?.reason ?: ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> Row(
                    Modifier.padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(onClick = onCheckIn) { Text(if (item.isPrn) "记录使用" else "服用") }
                    OutlinedButton(onClick = onSkip) { Text("跳过") }
                    if (med.route == "injection" && !item.isPrn) {
                        OutlinedButton(onClick = onPostpone) { Text("顺延") }
                    }
                    if (item.isPrn) Text(
                        "按需药不设提醒，记录实际使用即可",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(item: TodayItem) {
    val (text, color) = when {
        item.done -> "完成" to MaterialTheme.colorScheme.primary
        item.skipped -> "跳过" to MaterialTheme.colorScheme.onSurfaceVariant
        else -> "待服用" to MaterialTheme.colorScheme.tertiary
    }
    Card(colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f))) {
        Text(
            text, Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium, color = color,
        )
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
            Column {
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

private val INJ_SITES = listOf(
    "thigh_l" to "左大腿", "thigh_r" to "右大腿",
    "abdomen_l" to "左腹部", "abdomen_r" to "右腹部",
)

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
            Column {
                if (lastSite != null) {
                    val lastLabel = INJ_SITES.firstOrNull { it.first == lastSite }?.second ?: lastSite
                    Text("上次部位：$lastLabel——建议轮换", style = MaterialTheme.typography.bodySmall)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    INJ_SITES.forEach { (key, label) ->
                        FilterChip(selected = site == key, onClick = { site = key }, label = { Text(label) })
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
