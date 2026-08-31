package com.ashkb.app.ui.symptom

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ashkb.app.data.entity.Alert
import com.ashkb.app.data.entity.BasdaiRecord
import com.ashkb.app.data.entity.FlareAction
import com.ashkb.app.data.entity.FlareEvent
import com.ashkb.app.data.entity.FlareTrigger
import com.ashkb.app.data.entity.KbEntry
import com.ashkb.app.ui.knowledge.KbDetailDialog
import java.time.LocalDate
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SymptomScreen(vm: SymptomViewModel, onBack: () -> Unit) {
    val symptom by vm.symptom.collectAsState()
    val alerts by vm.alerts.collectAsState()
    val activeFlare by vm.activeFlare.collectAsState()
    val basdaiHistory by vm.basdaiHistory.collectAsState()
    val flareHistory by vm.flareHistory.collectAsState()

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

            // ---- 每日症状 ----
            item { SymptomFormCard(symptom, onSave = { f -> vm.saveSymptom(f.morningStiffnessMin, f.nightPain, f.painScore, f.feverish, f.feverTemp, f.eyeSymptom, f.neuroRedFlag, f.mood, f.sleep, f.fatigue, f.notes) }) }

            // ---- BASDAI ----
            item {
                SectionCard(title = "BASDAI 疾病活动度自评") {
                    Text(
                        "6 题自评（0–10），总分 (Q1+Q2+Q3+Q4+(Q5+Q6)/2)/5。建议每周固定同日自评一次，就诊时给医生看趋势。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { showBasdai = true }) { Text("开始今日自评") }
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
            date = vm.date,
            onConfirm = { q1, q2, q3, q4, q5, q6, note ->
                vm.saveBasdai(q1, q2, q3, q4, q5, q6, note)
                showBasdai = false
            },
            onDismiss = { showBasdai = false },
        )
    }
    kbDetail?.let { KbDetailDialog(entry = it, onDismiss = { kbDetail = null }) }
}

// ---------------------------------------------------------------------------
// 症状录入：数值不预填——「未记录」与「实际为 0」严格区分
// ---------------------------------------------------------------------------

data class SymptomFormState(
    val morningStiffnessMin: Int? = null,
    val nightPain: Int? = null,
    val painScore: Int? = null,
    val feverish: Boolean = false,
    val feverTemp: Double? = null,
    val eyeSymptom: Boolean = false,
    val neuroRedFlag: Boolean = false,
    val mood: Int? = null,
    val sleep: Int? = null,
    val fatigue: Int? = null,
    val notes: String? = null,
)

@Composable
private fun SymptomFormCard(existing: com.ashkb.app.data.entity.SymptomDaily?, onSave: (SymptomFormState) -> Unit) {
    // 编辑已有记录时回显（有值才回显；无行则全空——不预填 0）
    var stiffnessMin by remember(existing?.id) { mutableStateOf(existing?.morningStiffnessMin?.toString() ?: "") }
    var nightPain by remember(existing?.id) { mutableStateOf(existing?.nightPain) }
    var painScore by remember(existing?.id) { mutableStateOf(existing?.painScore) }
    var feverish by remember(existing?.id) { mutableStateOf(existing?.feverish ?: false) }
    var feverTemp by remember(existing?.id) { mutableStateOf(existing?.feverTemp?.toString() ?: "") }
    var eye by remember(existing?.id) { mutableStateOf(existing?.eyeSymptom ?: false) }
    var neuro by remember(existing?.id) { mutableStateOf(existing?.neuroRedFlag ?: false) }
    var mood by remember(existing?.id) { mutableStateOf(existing?.mood) }
    var sleepScore by remember(existing?.id) { mutableStateOf(existing?.sleep) }
    var fatigue by remember(existing?.id) { mutableStateOf(existing?.fatigue) }
    var notes by remember(existing?.id) { mutableStateOf(existing?.notes ?: "") }

    SectionCard(title = if (existing == null) "今日症状（未记录）" else "今日症状") {
        if (existing != null) {
            Text(
                "已记录于 ${existing.recordedAt.take(16).replace("T", " ")}，再次保存将覆盖",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
        }
        OutlinedTextField(
            value = stiffnessMin,
            onValueChange = { stiffnessMin = it.filter { c -> c.isDigit() }.take(4) },
            label = { Text("晨僵时长（分钟，醒后至僵硬感消退）") },
            modifier = Modifier.fillMaxWidth(),
        )
        ScoreRow("夜间痛（是否夜间痛醒，0=无）", nightPain) { nightPain = it }
        ScoreRow("整体疼痛（0=无，10=最重）", painScore) { painScore = it }

        SwitchRow("发热", feverish, "体温 ≥ 38.5℃ 将触发应急警报") { feverish = it }
        if (feverish) {
            OutlinedTextField(
                value = feverTemp,
                onValueChange = { feverTemp = it.filter { c -> c.isDigit() || c == '.' }.take(5) },
                label = { Text("最高体温（℃，如 38.6）") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        SwitchRow("眼部症状", eye, "眼痛 / 发红 / 畏光 / 视物模糊——葡萄膜炎警示") { eye = it }
        SwitchRow("神经症状", neuro, "麻木 / 无力 / 大小便控制变化——需立即就医") { neuro = it }

        Spacer(Modifier.height(6.dp))
        Text("以下可选（生活质量参考）", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        ScoreRow("心情", mood) { mood = it }
        ScoreRow("睡眠质量", sleepScore) { sleepScore = it }
        ScoreRow("疲乏程度", fatigue) { fatigue = it }

        OutlinedTextField(
            value = notes, onValueChange = { notes = it },
            label = { Text("备注（可选）") }, modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                onSave(
                    SymptomFormState(
                        morningStiffnessMin = stiffnessMin.toIntOrNull(),
                        nightPain = nightPain, painScore = painScore,
                        feverish = feverish, feverTemp = feverTemp.toDoubleOrNull(),
                        eyeSymptom = eye, neuroRedFlag = neuro,
                        mood = mood, sleep = sleepScore, fatigue = fatigue,
                        notes = notes.ifBlank { null },
                    )
                )
            }) { Text(if (existing == null) "保存今日症状" else "更新今日症状") }
        }
    }
}

/** 0–10 选择器：不预填（null = 未记录）；点已选值再点一次可清除 */
@Composable
private fun ScoreRow(label: String, value: Int?, onChange: (Int?) -> Unit) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Row {
            Text(label, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.width(6.dp))
            Text(
                if (value == null) "未记录" else "$value",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = if (value == null) MaterialTheme.colorScheme.onSurfaceVariant
                else if (value >= 7) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.primary,
            )
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            (0..10).forEach { v ->
                FilterChip(
                    selected = value == v,
                    onClick = { onChange(if (value == v) null else v) },
                    label = { Text("$v") },
                )
            }
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, hint: String, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodySmall)
            Text(hint, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

// ---------------------------------------------------------------------------
// 警报 / 发作 / BASDAI 卡
// ---------------------------------------------------------------------------

@Composable
private fun AlertCard(alert: Alert, onView: () -> Unit, onAck: () -> Unit) {
    val color = when (alert.severity) {
        "high" -> MaterialTheme.colorScheme.error
        "medium" -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.10f)),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when (alert.alertType) {
                        "symptom_abnormal" -> "症状警报"
                        "basdai_high" -> "自评偏高"
                        "flare_day7" -> "发作追踪"
                        "review_due" -> "内容复核"
                        "neuro_red_flag" -> "神经红旗"
                        else -> "提醒"
                    },
                    style = MaterialTheme.typography.labelMedium, color = color,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(alert.message, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                if (alert.kbRef != null) {
                    TextButton(onClick = onView, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 0.dp)) { Text("查看依据") }
                }
                TextButton(onClick = onAck, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 0.dp)) { Text("知道了") }
            }
        }
    }
}

@Composable
private fun FlareStatusCard(flare: FlareEvent?, days: Long?, onResolve: () -> Unit, onStart: () -> Unit) {
    SectionCard(title = "发作登记") {
        if (flare == null) {
            Text(
                "当前无活跃发作。症状明显加重（疼痛 / 晨僵突然变重）时在此登记，系统将追踪天数并在第 7 天提醒就医指征。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onStart) { Text("登记发作") }
        } else {
            Text(
                if (days != null) "发作进行中 · 第 $days 天" else "发作进行中",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error,
            )
            Text(
                "开始：${flare.startDate} · 诱因：${FlareTrigger.fromKey(flare.trigger).label}" +
                    (flare.severityPeak?.let { " · 峰值疼痛 $it/10" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            flare.actionsTaken?.let { json ->
                val acts = runCatching {
                    org.json.JSONArray(json).let { a -> (0 until a.length()).map { a.optString(it) } }
                }.getOrDefault(emptyList<String>())
                if (acts.isNotEmpty()) {
                    Text(
                        "已采取：${acts.joinToString("、") { FlareAction.fromKey(it).label }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = onResolve) { Text("标记缓解") }
            Text(
                "自我处理（休息 / 温和活动 / 热敷）7–10 天无改善应联系风湿科",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun FlareHistoryRow(f: FlareEvent) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(Modifier.weight(1f)) {
            Text(
                "${f.startDate} → ${f.endDate ?: "进行中"}",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "${FlareTrigger.fromKey(f.trigger).label}${f.severityPeak?.let { " · 峰值 $it/10" } ?: ""}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            if (f.status == "active") "进行中" else "已缓解",
            style = MaterialTheme.typography.labelMedium,
            color = if (f.status == "active") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun BasdaiRow(r: BasdaiRecord) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(Modifier.weight(1f)) {
            Text(r.date, style = MaterialTheme.typography.bodySmall)
            Text(
                "Q1 ${r.q1Fatigue} · Q2 ${r.q2SpinePain} · Q3 ${r.q3PeripheralPain} · Q4 ${r.q4TenderPoints} · Q5 ${r.q5StiffnessDegree} · Q6 ${r.q6StiffnessDuration}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            "%.1f".format(r.total),
            style = MaterialTheme.typography.titleMedium,
            color = if (r.total >= 4.0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        )
    }
}

// ---------------------------------------------------------------------------
// 弹窗：发作开始 / 缓解 / BASDAI 自评
// ---------------------------------------------------------------------------

@Composable
private fun FlareStartDialog(
    onConfirm: (FlareTrigger, List<FlareAction>, Int?, String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var trigger by remember { mutableStateOf(FlareTrigger.UNKNOWN) }
    var actions by remember { mutableStateOf(setOf<FlareAction>()) }
    var peak by remember { mutableStateOf<Int?>(null) }
    var note by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("登记发作") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("诱因", style = MaterialTheme.typography.labelLarge)
                FlareTrigger.entries.forEach { t ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = trigger == t, onClick = { trigger = t })
                        Text(t.label, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("已采取的处理（可多选）", style = MaterialTheme.typography.labelLarge)
                FlareAction.entries.forEach { a ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = a in actions,
                            onCheckedChange = { checked -> actions = if (checked) actions + a else actions - a },
                        )
                        Text(a.label, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Spacer(Modifier.height(8.dp))
                ScoreRow("本次峰值疼痛", peak) { peak = it }
                OutlinedTextField(
                    value = note, onValueChange = { note = it },
                    label = { Text("备注（可选）") }, modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(trigger, actions.toList(), peak, note.ifBlank { null }) }) { Text("登记") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun FlareResolveDialog(
    onConfirm: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("标记缓解") },
        text = {
            Column {
                Text("将本次发作标记为今日缓解。记录完整的发作时长有助于复诊时判断病情活动。", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = note, onValueChange = { note = it },
                    label = { Text("缓解方式 / 备注（可选）") }, modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(note.ifBlank { null }) }) { Text("确认缓解") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun BasdaiDialog(
    date: LocalDate,
    onConfirm: (Int, Int, Int, Int, Int, Int, String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var q1 by remember { mutableStateOf<Int?>(null) }
    var q2 by remember { mutableStateOf<Int?>(null) }
    var q3 by remember { mutableStateOf<Int?>(null) }
    var q4 by remember { mutableStateOf<Int?>(null) }
    var q5 by remember { mutableStateOf<Int?>(null) }
    var q6 by remember { mutableStateOf<Int?>(null) }
    var note by remember { mutableStateOf("") }
    val complete = listOf(q1, q2, q3, q4, q5, q6).all { it != null }
    val total = if (complete) BasdaiRecord.total(q1!!, q2!!, q3!!, q4!!, q5!!, q6!!) else null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("BASDAI 自评（$date）") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "回顾最近一周的感受作答（0=无，10=最重）。全部作答后可提交。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                ScoreRow("Q1 整体疲乏程度", q1) { q1 = it }
                ScoreRow("Q2 脊柱痛程度", q2) { q2 = it }
                ScoreRow("Q3 外周关节痛程度", q3) { q3 = it }
                ScoreRow("Q4 触痛部位程度（按压痛）", q4) { q4 = it }
                ScoreRow("Q5 晨僵程度", q5) { q5 = it }
                ScoreRow("Q6 晨僵时长（0=无，10=全天）", q6) { q6 = it }
                OutlinedTextField(
                    value = note, onValueChange = { note = it },
                    label = { Text("备注（可选）") }, modifier = Modifier.fillMaxWidth(),
                )
                if (total != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "总分：%.1f".format(total) + if (total >= 4.0) "（≥4.0：活动度偏高，两周内两次将提示复诊）" else "",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (total >= 4.0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(q1!!, q2!!, q3!!, q4!!, q5!!, q6!!, note.ifBlank { null }) },
                enabled = complete,
            ) { Text("提交") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
internal fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}
