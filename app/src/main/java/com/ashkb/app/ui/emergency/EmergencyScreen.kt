package com.ashkb.app.ui.emergency

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ashkb.app.data.entity.EmergencyContact
import com.ashkb.app.data.entity.EmergencyEvent
import com.ashkb.app.data.entity.EmergencyScene
import com.ashkb.app.data.entity.KbEntry
import com.ashkb.app.data.repo.nowIso
import com.ashkb.app.ui.knowledge.KbDetailDialog
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmergencyScreen(vm: EmergencyViewModel, onBack: () -> Unit) {
    val contacts by vm.contacts.collectAsState()
    val events by vm.events.collectAsState()
    val profile by vm.profile.collectAsState()
    val context = LocalContext.current

    var cards by remember { mutableStateOf<List<KbEntry>>(emptyList()) }
    var selectedCard by remember { mutableStateOf<KbEntry?>(null) }
    var showContactForm by remember { mutableStateOf(false) }
    var showEventForm by remember { mutableStateOf(false) }

    // 加载应急卡种子
    androidx.compose.runtime.LaunchedEffect(Unit) {
        cards = vm.emergencyCards()
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("紧急卡") })

        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // ---- 快速拨号 ----
            item {
                Card(
                    Modifier.fillMaxWidth(),
                    colors = androidx.compose.material3.CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.08f)
                    ),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text("紧急呼叫", style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    context.startActivity(
                                        Intent(Intent.ACTION_DIAL, Uri.parse("tel:120"))
                                    )
                                },
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                ),
                            ) { Text("120 急救") }
                            if (contacts.isNotEmpty()) {
                                OutlinedButton(onClick = {
                                    val first = contacts.first()
                                    context.startActivity(
                                        Intent(Intent.ACTION_DIAL, Uri.parse("tel:${first.phone}"))
                                    )
                                }) { Text("联系 ${contacts.first().name}") }
                            }
                        }
                    }
                }
            }

            // ---- 五应急场景卡 ----
            item {
                Text("应急处理卡", style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 4.dp))
                Text("出现以下情况时快速查阅，严重情况请立即就医",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            items(cards) { card ->
                Card(
                    onClick = { selectedCard = card },
                    modifier = Modifier.fillMaxWidth(),
                    colors = androidx.compose.material3.CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                ) {
                    Row(
                        Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("⚠", style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(card.title, fontWeight = FontWeight.Medium)
                            Text(card.summary, maxLines = 2,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("查看 →", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            // ---- 紧急联系人 ----
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("紧急联系人", style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f))
                            TextButton(onClick = { showContactForm = true }) { Text("添加") }
                        }
                        if (contacts.isEmpty()) {
                            Text("尚未添加紧急联系人",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall)
                        } else {
                            contacts.forEach { c ->
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(c.name, fontWeight = FontWeight.Medium)
                                        c.relation?.let {
                                            Text(it, style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    TextButton(onClick = {
                                        context.startActivity(
                                            Intent(Intent.ACTION_DIAL, Uri.parse("tel:${c.phone}"))
                                        )
                                    }) { Text(c.phone) }
                                }
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }

            // ---- 个人信息卡 ----
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Text("我的信息（供急救人员参考）",
                            style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(6.dp))
                        profile?.let { p ->
                            Text("姓名：${p.displayName}")
                            Text("诊断：${p.diagnosis}")
                            Text("HLA-B27：${hlaLabel(p.hlaB27)}")
                            p.allergies?.let { Text("过敏史：$it") }
                            p.emergencyBloodType?.let { Text("血型：$it") }
                        } ?: Text("未建档", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // ---- 紧急事件历史 ----
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("紧急事件记录", style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f))
                            TextButton(onClick = { showEventForm = true }) { Text("记录") }
                        }
                        if (events.isEmpty()) {
                            Text("暂无记录",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall)
                        } else {
                            events.take(3).forEach { e ->
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(e.date, fontWeight = FontWeight.Medium)
                                    Spacer(Modifier.height(8.dp))
                                    Text(EmergencyScene.fromKey(e.scene).label,
                                        Modifier.weight(1f),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(if (e.resolvedDate != null) "已转归" else "进行中",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (e.resolvedDate != null)
                                            MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.error)
                                }
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    selectedCard?.let { KbDetailDialog(entry = it, onDismiss = { selectedCard = null }) }

    if (showContactForm) ContactFormDialog(
        onSave = { vm.saveContact(it); showContactForm = false },
        onDismiss = { showContactForm = false },
    )

    if (showEventForm) EmergencyEventFormDialog(
        onSave = { vm.saveEmergencyEvent(it); showEventForm = false },
        onDismiss = { showEventForm = false },
    )
}

private fun hlaLabel(k: String) = when (k) {
    "positive" -> "阳性"; "negative" -> "阴性"; else -> "未知"
}

// ===== 联系人表单 =====
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContactFormDialog(onSave: (EmergencyContact) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var relation by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var isEmergency by remember { mutableStateOf(true) }
    var isDoctor by remember { mutableStateOf(false) }
    var hospital by remember { mutableStateOf("") }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加联系人") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("姓名") }, singleLine = true)
                OutlinedTextField(relation, { relation = it },
                    label = { Text("关系") }, singleLine = true)
                OutlinedTextField(phone, { phone = it },
                    label = { Text("电话") }, singleLine = true)
                OutlinedTextField(hospital, { hospital = it },
                    label = { Text("医院（医生填写）") }, singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Checkbox(
                        checked = isEmergency, onCheckedChange = { isEmergency = it })
                    Text("紧急联系人", style = MaterialTheme.typography.bodyMedium)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Checkbox(
                        checked = isDoctor, onCheckedChange = { isDoctor = it })
                    Text("主治医生", style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank() && phone.isNotBlank()) {
                        onSave(
                            EmergencyContact(
                                id = "", name = name.trim(),
                                relation = relation.ifBlank { null },
                                phone = phone.trim(), isEmergency = isEmergency,
                                isDoctor = isDoctor,
                                hospital = hospital.ifBlank { null },
                                createdAt = nowIso(), updatedAt = nowIso(),
                            )
                        )
                    }
                },
                enabled = name.isNotBlank() && phone.isNotBlank(),
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

// ===== 紧急事件表单 =====
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EmergencyEventFormDialog(
    onSave: (EmergencyEvent) -> Unit,
    onDismiss: () -> Unit,
) {
    var scene by remember { mutableStateOf(EmergencyScene.INFECTION_FEVER) }
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var symptoms by remember { mutableStateOf("") }
    var actions by remember { mutableStateOf("") }
    var hospitalVisit by remember { mutableStateOf(false) }
    var hospitalName by remember { mutableStateOf("") }
    var outcome by remember { mutableStateOf("") }
    var resolved by remember { mutableStateOf(false) }
    var notes by remember { mutableStateOf("") }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("记录紧急事件") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
                    .verticalScroll(androidx.compose.foundation.rememberScrollState()),
            ) {
                Text("场景", style = MaterialTheme.typography.bodySmall)
                EmergencyScene.entries.forEach { s ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.RadioButton(
                            selected = scene == s, onClick = { scene = s })
                        Text(s.label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                OutlinedTextField(date, { date = it },
                    label = { Text("日期") }, singleLine = true)
                OutlinedTextField(symptoms, { symptoms = it },
                    label = { Text("症状描述") })
                OutlinedTextField(actions, { actions = it },
                    label = { Text("已采取措施") })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Checkbox(
                        checked = hospitalVisit, onCheckedChange = { hospitalVisit = it })
                    Text("是否就医", style = MaterialTheme.typography.bodyMedium)
                }
                if (hospitalVisit) {
                    OutlinedTextField(hospitalName, { hospitalName = it },
                        label = { Text("医院名称") }, singleLine = true)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Checkbox(
                        checked = resolved, onCheckedChange = { resolved = it })
                    Text("已转归/恢复", style = MaterialTheme.typography.bodyMedium)
                }
                if (resolved) {
                    OutlinedTextField(outcome, { outcome = it },
                        label = { Text("转归结果") }, singleLine = true)
                }
                OutlinedTextField(notes, { notes = it }, label = { Text("备注") })
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    EmergencyEvent(
                        id = "", date = date, recordedAt = nowIso(),
                        scene = scene.name, severity = "high",
                        symptoms = symptoms.ifBlank { null },
                        actionsTaken = actions.ifBlank { null },
                        hospitalVisit = hospitalVisit,
                        hospitalName = hospitalName.ifBlank { null },
                        outcome = outcome.ifBlank { null },
                        resolvedDate = if (resolved) LocalDate.now().toString() else null,
                        notes = notes.ifBlank { null },
                    )
                )
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
