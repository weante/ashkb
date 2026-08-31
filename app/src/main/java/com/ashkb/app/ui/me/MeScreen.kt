package com.ashkb.app.ui.me

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ashkb.app.data.entity.MedFrequency
import com.ashkb.app.data.entity.Medication
import kotlinx.coroutines.launch

@Composable
fun MeScreen(vm: MeViewModel) {
    val profile by vm.profile.collectAsState()
    val meds by vm.meds.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showProfileForm by remember { mutableStateOf(false) }
    var showMedForm by remember { mutableStateOf(false) }
    var stopTarget by remember { mutableStateOf<Medication?>(null) }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { Spacer(Modifier.height(16.dp)) }

        // ---- 档案卡 ----
        item {
            SectionCard(title = "健康档案") {
                if (profile == null) {
                    Text("尚未建档——过敏史、血型与用药史是禁忌检查与紧急卡的数据基础")
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { showProfileForm = true }) { Text("开始建档") }
                } else {
                    val p = profile!!
                    ProfileRow("称呼", p.displayName)
                    ProfileRow("诊断", p.diagnosis)
                    ProfileRow("确诊年份", p.diagnoseYear?.toString() ?: "未填")
                    ProfileRow("HLA-B27", hlaLabel(p.hlaB27))
                    ProfileRow("病情分期", stageLabel(p.diseaseStage))
                    ProfileRow("脊柱活动度", spineLabel(p.spineMobility))
                    ProfileRow("过敏史", p.allergies ?: "未填")
                    ProfileRow("血型", p.emergencyBloodType ?: "未填")
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { showProfileForm = true }) { Text("编辑档案") }
                }
            }
        }

        // ---- 药单管理 ----
        item {
            SectionCard(title = "药单管理") {
                if (meds.isEmpty()) {
                    Text("药单为空。添加药品后将生成每日打卡计划与用药提醒（含甲氨蝶呤周一次、注射周期顺延、按需药独立口径）")
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { showMedForm = true }) { Text("添加药品") }
                } else {
                    meds.forEach { med ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("${med.name} ${med.dose}", style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    buildString {
                                        append(MedFrequency.fromKey(med.frequency).label)
                                        if (med.route == "injection") append(" · 注射")
                                        if (!med.checkDoctorTold || !med.checkLeafletRead) append(" · 核对待办")
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (!med.checkDoctorTold || !med.checkLeafletRead)
                                        MaterialTheme.colorScheme.tertiary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(onClick = { stopTarget = med }) { Text("停用") }
                        }
                        HorizontalDivider()
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { showMedForm = true }) { Text("添加药品") }
                }
            }
        }

        // ---- M10 提醒与权限自检 ----
        item {
            ReminderSelfCheckCard()
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

    if (showProfileForm) {
        ProfileFormDialog(
            initial = profile,
            onSave = {
                vm.saveProfile(it)
                showProfileForm = false
            },
            onDismiss = { showProfileForm = false },
        )
    }
    if (showMedForm) {
        MedFormDialog(
            vm = vm,
            onSave = { med ->
                vm.saveMedication(context, med)
                showMedForm = false
            },
            onDismiss = { showMedForm = false },
            scope = scope,
        )
    }

    stopTarget?.let { med ->
        StopMedDialog(
            medName = "${med.name} ${med.dose}",
            isBiologic = med.medClass.equals("BIOLOGIC", true),
            onConfirm = { reason, note ->
                vm.stopMedication(context, med, reason, note)
                stopTarget = null
            },
            onDismiss = { stopTarget = null },
        )
    }
}

/** R17 停药原因分类：self_stopped / side_effect 弹警示（D-2 §7） */
@Composable
private fun StopMedDialog(
    medName: String,
    isBiologic: Boolean,
    onConfirm: (reason: String, note: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var reason by remember { mutableStateOf(com.ashkb.app.data.entity.StopReason.DOCTOR_SCHEDULED) }
    var note by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("停用 $medName") },
        text = {
            Column {
                Text("请选择停用原因（将写入药单变更记录）", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(4.dp))
                com.ashkb.app.data.entity.StopReason.entries.forEach { r ->
                    Row(
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        androidx.compose.material3.RadioButton(selected = reason == r, onClick = { reason = r })
                        Text(r.label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (reason == com.ashkb.app.data.entity.StopReason.OTHER) {
                    androidx.compose.material3.OutlinedTextField(
                        value = note, onValueChange = { note = it },
                        label = { Text("停用原因说明（必填）") }, modifier = Modifier.fillMaxWidth(),
                    )
                }
                reason.warning?.let { w ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (isBiologic && reason == com.ashkb.app.data.entity.StopReason.SELF_STOPPED)
                            "⚠ $w" else w,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(reason.name.lowercase(), note.ifBlank { null }) },
                enabled = reason != com.ashkb.app.data.entity.StopReason.OTHER || note.isNotBlank(),
            ) { Text("停用并记录") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun hlaLabel(k: String) = when (k) { "positive" -> "阳性"; "negative" -> "阴性"; else -> "未知" }

@Composable
private fun stageLabel(k: String?) = when (k) {
    "active" -> "活动期（运动处方已保守过滤）"
    "stable" -> "缓解期"
    else -> "未设置（按活动期保守处理）"
}

@Composable
private fun spineLabel(k: String?) = when (k) {
    "none" -> "无受限"; "mild" -> "轻度"; "moderate" -> "中度（颈椎受累）"; "severe" -> "重度（颈椎受累）"
    else -> "未填"
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun ProfileRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value.ifBlank { "未填" }, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ReminderSelfCheckCard() {
    val context = LocalContext.current
    val notifOk = androidx.core.content.ContextCompat.checkSelfPermission(
        context, android.Manifest.permission.POST_NOTIFICATIONS
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val exactOk = if (Build.VERSION.SDK_INT >= 31) am.canScheduleExactAlarms() else true

    SectionCard(title = "提醒可靠性自检") {
        CheckRow("通知权限", if (notifOk) "已授权" else "未授权——提醒将无法显示", notifOk)
        CheckRow("精确闹钟", if (exactOk) "已允许——按分钟准时提醒" else "未允许——提醒可能有 15 分钟内偏差", exactOk)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!exactOk && Build.VERSION.SDK_INT >= 31) {
                OutlinedButton(onClick = {
                    context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                }) { Text("申请精确闹钟") }
            }
            OutlinedButton(onClick = {
                context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                })
            }) { Text("通知设置") }
        }
        Text(
            "提醒为本机系统通知，不依赖任何服务器。部分厂商系统可能限制后台——若提醒未响，请将本应用加入电池白名单。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun CheckRow(label: String, desc: String, ok: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(desc, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(if (ok) "✓" else "!", color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
    }
}
