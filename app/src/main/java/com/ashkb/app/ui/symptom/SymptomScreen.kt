package com.ashkb.app.ui.symptom

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ashkb.app.data.entity.KbEntry
import com.ashkb.app.ui.knowledge.KbDetailDialog
import java.time.LocalDate
import kotlinx.coroutines.launch

/** M5 症状与自评页：每日症状 / BASDAI / 发作登记 / 系统警报。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SymptomScreen(vm: SymptomViewModel, onBack: () -> Unit) {
    val symptom by vm.symptom.collectAsState()
    val selectedDate by vm.selectedDate.collectAsState()
    val alerts by vm.alerts.collectAsState()
    val activeFlare by vm.activeFlare.collectAsState()
    val basdaiHistory by vm.basdaiHistory.collectAsState()
    val flareHistory by vm.flareHistory.collectAsState()
    val isToday = selectedDate == vm.today

    var showFlareStart by remember { mutableStateOf(false) }
    var showResolve by remember { mutableStateOf(false) }
    var showBasdai by remember { mutableStateOf(false) }
    var kbDetail by remember { mutableStateOf<KbEntry?>(null) }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("症状与自评") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
            },
        )
        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { Spacer(Modifier.height(4.dp)) }

            // ---- 系统警报区 ----
            if (alerts.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        alerts.take(3).forEach { alert ->
                            AlertCard(
                                alert = alert,
                                onView = {
                                    alert.kbRef?.let { ref ->
                                        scope.launch { kbDetail = vm.kbEntry(ref) }
                                    }
                                },
                                onAck = { vm.ackAlert(alert.id) },
                            )
                        }
                        if (alerts.size > 3) {
                            Text(
                                "还有 ${alerts.size - 3} 条未读警报",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            // ---- 发作状态 ----
            item { FlareStatusCard(activeFlare, vm.flareDays(), onResolve = { showResolve = true }, onStart = { showFlareStart = true }) }

            // ---- 自评记录日期（今天 / 昨天补写） ----
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("记录日期", style = MaterialTheme.typography.bodySmall)
                    FilterChip(
                        selected = isToday,
                        onClick = { vm.selectDate(vm.today) },
                        label = { Text("今天") },
                    )
                    FilterChip(
                        selected = !isToday,
                        onClick = { vm.selectDate(vm.today.minusDays(1)) },
                        label = { Text("昨天（补写）") },
                    )
                    if (!isToday) {
                        Text(
                            "漏记可补写，已记可修改",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // ---- 每日症状 ----
            item {
                SymptomFormCard(
                    existing = symptom,
                    dateLabel = if (isToday) "今日" else "昨日",
                    dateKey = selectedDate,
                    onSave = { f -> vm.saveSymptom(f.morningStiffnessMin, f.nightPain, f.painScore, f.feverish, f.feverTemp, f.eyeSymptom, f.neuroRedFlag, f.mood, f.sleep, f.fatigue, f.notes) },
                )
            }

            // ---- BASDAI ----
            item {
                SectionCard(title = "BASDAI 疾病活动度自评") {
                    Text(
                        "6 题自评（0–10），总分 (Q1+Q2+Q3+Q4+(Q5+Q6)/2)/5。建议每周固定同日自评一次，就诊时给医生看趋势。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    val basdaiExisting = basdaiHistory.firstOrNull { it.date == selectedDate.toString() }
                    if (basdaiExisting != null) {
                        Text(
                            "$selectedDate 已记录（总分 %.1f），可修改后重新提交，覆盖原记录。".format(basdaiExisting.total),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    Button(onClick = { showBasdai = true }) {
                        Text(
                            when {
                                isToday && basdaiExisting == null -> "开始今日自评"
                                isToday -> "修改今日自评"
                                basdaiExisting == null -> "补写昨日自评"
                                else -> "修改昨日自评"
                            }
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    if (basdaiHistory.isEmpty()) {
                        Text("尚无记录", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        basdaiHistory.take(8).forEach { r ->
                            BasdaiRow(r)
                            androidx.compose.material3.HorizontalDivider()
                        }
                    }
                }
            }

            // ---- 发作历史 ----
            if (flareHistory.isNotEmpty()) {
                item {
                    SectionCard(title = "发作历史") {
                        flareHistory.take(10).forEach { f ->
                            FlareHistoryRow(f)
                            androidx.compose.material3.HorizontalDivider()
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (showFlareStart) {
        FlareStartDialog(
            onConfirm = { trigger, actions, peak, note ->
                vm.startFlare(trigger, actions, peak, note)
                showFlareStart = false
            },
            onDismiss = { showFlareStart = false },
        )
    }
    if (showResolve) {
        FlareResolveDialog(
            onConfirm = { note ->
                vm.resolveFlare(note)
                showResolve = false
            },
            onDismiss = { showResolve = false },
        )
    }
    if (showBasdai) {
        BasdaiDialog(
            date = selectedDate,
            existing = basdaiHistory.firstOrNull { it.date == selectedDate.toString() },
            onConfirm = { q1, q2, q3, q4, q5, q6, note ->
                vm.saveBasdai(q1, q2, q3, q4, q5, q6, note)
                showBasdai = false
            },
            onDismiss = { showBasdai = false },
        )
    }
    kbDetail?.let { KbDetailDialog(entry = it, onDismiss = { kbDetail = null }) }
}
