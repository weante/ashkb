package com.ashkb.app.ui.me

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ashkb.app.data.db.Ids
import com.ashkb.app.data.entity.KbEntry
import com.ashkb.app.data.entity.MedClass
import com.ashkb.app.data.entity.MedFrequency
import com.ashkb.app.data.entity.Medication
import com.ashkb.app.data.repo.nowIso
import com.ashkb.app.domain.DrugKeyCatalog
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.json.JSONArray

/**
 * M1 添加药品：两步流程。
 * 第一步录入档案 → 第二步 R03 核对清单（相互作用命中展示 + 两项待办勾选；无数据明确提示不默认放行）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedFormDialog(
    vm: MeViewModel,
    onSave: (Medication) -> Unit,
    onDismiss: () -> Unit,
    scope: CoroutineScope,
) {
    var step by remember { mutableStateOf(1) }

    // ---- 第一步字段 ----
    var name by remember { mutableStateOf("") }
    var brand by remember { mutableStateOf("") }
    var nameKey by remember { mutableStateOf("") }
    var medClass by remember { mutableStateOf(MedClass.OTHER) }
    var route by remember { mutableStateOf("oral") }
    var dose by remember { mutableStateOf("") }
    var frequency by remember { mutableStateOf(MedFrequency.DAILY) }
    var times by remember { mutableStateOf(listOf("08:00")) }
    var customTime by remember { mutableStateOf("") }
    var weekday by remember { mutableStateOf(1) }
    var weekday2 by remember { mutableStateOf(4) }
    var biwError by remember { mutableStateOf(false) }
    var cycleDays by remember { mutableStateOf("14") }
    var food by remember { mutableStateOf("any") }
    var prnReason by remember { mutableStateOf("") }
    var storage by remember { mutableStateOf("") }
    var startDate by remember { mutableStateOf(LocalDate.now().toString()) }

    // ---- 第二步 R03 ----
    var hits by remember { mutableStateOf<List<KbEntry>?>(null) }
    var doctorTold by remember { mutableStateOf(false) }
    var leafletRead by remember { mutableStateOf(false) }

    fun buildMed(): Medication = Medication(
        id = Ids.new("med"),
        name = name.trim(),
        brandName = brand.trim().ifBlank { null },
        nameKey = nameKey.trim().lowercase(),
        medClass = medClass.name,
        route = route,
        dose = dose.trim(),
        frequency = frequency.name,
        prnReason = if (frequency == MedFrequency.PRN) prnReason.trim().ifBlank { "备用" } else null,
        takeTimes = if (frequency == MedFrequency.PRN || route == "injection")
            times.take(1).let { if (it.isEmpty()) null else JSONArray(it).toString() }
        else JSONArray(times).toString(),
        weeklyWeekday = if (frequency == MedFrequency.WEEKLY || frequency == MedFrequency.BIW) weekday else null,
        weeklyWeekday2 = if (frequency == MedFrequency.BIW) weekday2 else null,
        startDate = startDate,
        injCycleDays = if (route == "injection") cycleDays.toIntOrNull() ?: 14 else null,
        storage = storage.trim().ifBlank { null },
        takeWithFood = if (route == "oral") food else null,
        checkDoctorTold = doctorTold,
        checkLeafletRead = leafletRead,
        interactionCheckDate = if (step >= 2) LocalDate.now().toString() else null,
        createdAt = nowIso(),
        updatedAt = nowIso(),
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (step == 1) "添加药品" else "用药核对清单") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (step == 1) {
                    OutlinedTextField(name, { name = it }, label = { Text("药品名（必填，如：阿达木单抗）") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(brand, { brand = it }, label = { Text("商品名（选填，如：修美乐）") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(nameKey, { nameKey = it }, label = { Text("通用名键（必填，小写英文，如 adalimumab）") }, modifier = Modifier.fillMaxWidth(), supportingText = { Text("用于知识库相互作用检索；输入中英文自动匹配") })
                    // P5 R8：自动匹配——键为空时按药品名检索建议
                    val keySuggestions = DrugKeyCatalog.suggest(if (nameKey.isBlank()) name else nameKey)
                    if (keySuggestions.isNotEmpty() && !DrugKeyCatalog.isExactKey(nameKey)) {
                        keySuggestions.forEach { s ->
                            Row(
                                Modifier.fillMaxWidth().clickable {
                                    nameKey = s.key
                                    if (name.isBlank()) name = s.display
                                    if (brand.isBlank() && !s.brand.isNullOrBlank()) brand = s.brand
                                    if (medClass == MedClass.OTHER) medClass = s.medClass
                                }.padding(vertical = 4.dp),
                            ) {
                                Text(
                                    "${s.key} · ${s.display}${s.brand?.let { "（$it）" } ?: ""}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("药物类别", style = MaterialTheme.typography.labelMedium)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                        MedClass.entries.forEach { c ->
                            FilterChip(selected = medClass == c, onClick = { medClass = c }, label = { Text(c.label) })
                        }
                    }
                    Text("给药途径", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = route == "oral", onClick = { route = "oral" }, label = { Text("口服") })
                        FilterChip(selected = route == "injection", onClick = { route = "injection" }, label = { Text("注射") })
                    }
                    OutlinedTextField(dose, { dose = it }, label = { Text("剂量（必填，含单位，如 40 mg）") }, modifier = Modifier.fillMaxWidth())
                    Text("频次", style = MaterialTheme.typography.labelMedium)
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        MedFrequency.entries.forEach { f ->
                            FilterChip(selected = frequency == f, onClick = { frequency = f }, label = { Text(f.label) })
                        }
                    }
                    if (frequency != MedFrequency.PRN) {
                        if (route == "oral") {
                            Text("服药时刻（本地提醒时刻）", style = MaterialTheme.typography.labelMedium)
                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                                listOf("06:30", "08:00", "12:00", "18:00", "21:00").forEach { t ->
                                    FilterChip(
                                        selected = t in times,
                                        onClick = { times = if (t in times) times - t else times + t },
                                        label = { Text(t) },
                                    )
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(customTime, { customTime = it }, label = { Text("自定义 HH:mm") },
                                    modifier = Modifier.weight(1f))
                                TextButton(onClick = {
                                    if (Regex("\\d{2}:\\d{2}").matches(customTime) && customTime !in times) {
                                        times = times + customTime; customTime = ""
                                    }
                                }) { Text("添加") }
                            }
                            times.sorted().forEach { t -> Text("· $t", style = MaterialTheme.typography.bodySmall) }
                        } else if (frequency == MedFrequency.Q2W || frequency == MedFrequency.CUSTOM) {
                            OutlinedTextField(cycleDays, { cycleDays = it.filter { c -> c.isDigit() }.take(3) },
                                label = { Text("注射周期（天，如 14 = 每两周）") }, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(startDate, { startDate = it }, label = { Text("周期锚点日期（YYYY-MM-DD，本期注射日）") }, modifier = Modifier.fillMaxWidth())
                        } else {
                            Text(
                                "注射日按下方选择的固定星期自动出卡（默认 09:00 提醒）。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        OutlinedTextField(prnReason, { prnReason = it }, label = { Text("按需原因（如：备用镇痛）") }, modifier = Modifier.fillMaxWidth())
                    }
                    if (frequency == MedFrequency.WEEKLY || frequency == MedFrequency.BIW) {
                        Text(
                            if (frequency == MedFrequency.BIW) "每周两针——两个注射星期"
                            else "每周固定星期（如甲氨蝶呤）",
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                            listOf(1 to "一", 2 to "二", 3 to "三", 4 to "四", 5 to "五", 6 to "六", 7 to "日").forEach { (d, l) ->
                                FilterChip(selected = weekday == d, onClick = { weekday = d; biwError = false }, label = { Text(l) })
                            }
                        }
                        if (frequency == MedFrequency.BIW) {
                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                                listOf(1 to "一", 2 to "二", 3 to "三", 4 to "四", 5 to "五", 6 to "六", 7 to "日").forEach { (d, l) ->
                                    FilterChip(selected = weekday2 == d, onClick = { weekday2 = d; biwError = false }, label = { Text(l) })
                                }
                            }
                            if (biwError && weekday == weekday2) {
                                Text(
                                    "两针星期需不同（如周一 / 周四），请调整",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                    if (route == "oral") {
                        Text("餐食关系", style = MaterialTheme.typography.labelMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("with_food" to "随餐", "empty_stomach" to "空腹", "any" to "均可").forEach { (k, l) ->
                                FilterChip(selected = food == k, onClick = { food = k }, label = { Text(l) })
                            }
                        }
                    }
                    OutlinedTextField(storage, { storage = it }, label = { Text("储存提示（如：2–8℃ 冷藏）") }, modifier = Modifier.fillMaxWidth())
                } else {
                    // ---- 第二步：R03 核对清单 ----
                    val h = hits
                    if (h == null) {
                        Text("正在检索知识库…")
                    } else if (h.isEmpty()) {
                        Text(
                            "⚠ 本系统知识库无此药相关数据，无法判断相互作用。请咨询医生 / 药师后使用。",
                            color = MaterialTheme.colorScheme.error,
                        )
                    } else {
                        Text("知识库命中 ${h.size} 条相关提示：", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(4.dp))
                        h.take(5).forEach { e ->
                            Text(
                                "· ${if (e.severityLevel == "high") "【高危】" else "【提示】"}${e.title}",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (e.severityLevel == "high") MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        if (h.size > 5) Text("…其余 ${h.size - 5} 条可在知识库查看", style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Checkbox(checked = doctorTold, onCheckedChange = { doctorTold = it })
                        Text("已告知风湿科医生我在使用此药")
                    }
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Checkbox(checked = leafletRead, onCheckedChange = { leafletRead = it })
                        Text("已核对药品说明书用法用量")
                    }
                    Text(
                        "两项均为待办——未勾选也可保存，药单将显示「核对待办」标记提醒您补办。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            if (step == 1) {
                TextButton(
                    onClick = {
                        if (name.isBlank() || nameKey.isBlank() || dose.isBlank()) return@TextButton
                        if (frequency == MedFrequency.BIW && weekday == weekday2) {
                            biwError = true; return@TextButton
                        }
                        val draft = buildMed()
                        scope.launch {
                            hits = vm.interactionsFor(draft)
                            step = 2
                        }
                    },
                ) { Text("下一步 · 核对") }
            } else {
                TextButton(onClick = { onSave(buildMed()) }) { Text("保存入库") }
            }
        },
        dismissButton = {
            if (step == 1) TextButton(onClick = onDismiss) { Text("取消") }
            else TextButton(onClick = { step = 1 }) { Text("返回修改") }
        },
    )
}
