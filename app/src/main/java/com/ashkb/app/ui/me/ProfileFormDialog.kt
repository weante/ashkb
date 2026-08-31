package com.ashkb.app.ui.me

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ashkb.app.data.entity.Profile
import com.ashkb.app.data.repo.nowIso
import java.time.LocalDate
import kotlinx.coroutines.launch

/** M0 建档：AS 专属字段（过敏史 / 血型 / 合并症为禁忌检查与紧急卡数据源） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileFormDialog(
    initial: Profile?,
    onSave: (Profile) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial?.displayName ?: "") }
    var diagnosis by remember { mutableStateOf(initial?.diagnosis ?: "中轴型脊柱关节炎") }
    var year by remember { mutableStateOf(initial?.diagnoseYear?.toString() ?: "") }
    var hla by remember { mutableStateOf(initial?.hlaB27 ?: "unknown") }
    var allergies by remember { mutableStateOf(initial?.allergies?.removeSurrounding("[", "]")?.replace("\"", "") ?: "") }
    var bloodType by remember { mutableStateOf(initial?.emergencyBloodType ?: "") }
    var comorbid by remember { mutableStateOf(initial?.comorbidities?.removeSurrounding("[", "]")?.replace("\"", "") ?: "") }
    // R27 矩阵两输入：分期维 + 颈椎受累维（驱动 M4 运动过滤 / M5 预警灵敏度）
    var stage by remember { mutableStateOf(initial?.diseaseStage ?: "unknown") }
    var spine by remember { mutableStateOf(initial?.spineMobility ?: "none") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "健康档案建档" else "编辑健康档案") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(name, { name = it }, label = { Text("称呼（必填）") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(diagnosis, { diagnosis = it }, label = { Text("诊断（必填）") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(year, { year = it.filter { c -> c.isDigit() }.take(4) },
                    label = { Text("确诊年份") }, modifier = Modifier.fillMaxWidth())
                Row(Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("positive" to "阳性", "negative" to "阴性", "unknown" to "未知").forEach { (k, l) ->
                        FilterChip(selected = hla == k, onClick = { hla = k }, label = { Text("B27 $l") })
                    }
                }
                Text("病情分期（运动处方与预警灵敏度依据）", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp))
                Row(Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "active" to "活动期（疼痛晨僵加重）",
                        "stable" to "缓解期",
                        "unknown" to "不确定",
                    ).forEach { (k, l) ->
                        FilterChip(selected = stage == k, onClick = { stage = k }, label = { Text(l) })
                    }
                }
                Text("脊柱活动度受限程度", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp))
                Text(
                    "颈椎受累（中度以上）将自动收紧泳姿与颈部动作条目",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "none" to "无受限", "mild" to "轻度",
                        "moderate" to "中度", "severe" to "重度",
                    ).forEach { (k, l) ->
                        FilterChip(selected = spine == k, onClick = { spine = k }, label = { Text(l) })
                    }
                }
                OutlinedTextField(allergies, { allergies = it }, label = { Text("过敏史（逗号分隔，如：青霉素,磺胺）") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(bloodType, { bloodType = it }, label = { Text("血型（紧急卡用，如 A+）") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(comorbid, { comorbid = it }, label = { Text("合并症（逗号分隔，如：葡萄膜炎史,骨质疏松）") }, modifier = Modifier.fillMaxWidth())
                Text(
                    "档案数据仅存本机；血型与过敏史将用于紧急信息卡。分期可随病情变化随时更新——运动处方即时重算。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isBlank()) return@TextButton
                    val now = nowIso()
                    onSave(
                        Profile(
                            displayName = name.trim(),
                            diagnosis = diagnosis.trim(),
                            diagnoseYear = year.toIntOrNull(),
                            hlaB27 = hla,
                            diseaseStage = stage,
                            spineMobility = spine,
                            comorbidities = csvToJson(comorbid),
                            allergies = csvToJson(allergies),
                            emergencyBloodType = bloodType.trim().ifBlank { null },
                            createdAt = initial?.createdAt ?: now,
                            updatedAt = now,
                        )
                    )
                },
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

private fun csvToJson(csv: String): String? {
    val items = csv.split("，", ",").map { it.trim() }.filter { it.isNotBlank() }
    if (items.isEmpty()) return null
    return "[" + items.joinToString(",") { "\"$it\"" } + "]"
}
