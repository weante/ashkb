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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle

import com.ashkb.app.R
import com.ashkb.app.data.entity.KbEntry
import com.ashkb.app.ui.components.ScreenTopBar
import com.ashkb.app.ui.components.SectionCard
import com.ashkb.app.ui.knowledge.KbDetailDialog
import com.ashkb.app.ui.theme.Spacing
import java.time.LocalDate
import kotlinx.coroutines.launch

/** M5 症状与自评页：每日症状 / BASDAI / 发作登记 / 系统警报。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SymptomScreen(vm: SymptomViewModel, onBack: () -> Unit) {
    val symptom by vm.symptom.collectAsStateWithLifecycle()
    val selectedDate by vm.selectedDate.collectAsStateWithLifecycle()
    val alerts by vm.alerts.collectAsStateWithLifecycle()
    val activeFlare by vm.activeFlare.collectAsStateWithLifecycle()
    val basdaiHistory by vm.basdaiHistory.collectAsStateWithLifecycle()
    val flareHistory by vm.flareHistory.collectAsStateWithLifecycle()
    val isToday = selectedDate == vm.today

    var showFlareStart by remember { mutableStateOf(false) }
    var showResolve by remember { mutableStateOf(false) }
    var showBasdai by remember { mutableStateOf(false) }
    var kbDetail by remember { mutableStateOf<KbEntry?>(null) }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(title = stringResource(R.string.symptom_self_eval_section), onBack = onBack)
        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item { Spacer(Modifier.height(Spacing.xs)) }

            // ---- 系统警报区 ----
            if (alerts.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
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
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.common_record_date), style = MaterialTheme.typography.bodySmall)
                    FilterChip(
                        selected = isToday,
                        onClick = { vm.selectDate(vm.today) },
                        label = { Text(stringResource(R.string.common_today)) },
                    )
                    FilterChip(
                        selected = !isToday,
                        onClick = { vm.selectDate(vm.today.minusDays(1)) },
                        label = { Text(stringResource(R.string.symptom_yesterday_fill)) },
                    )
                    if (!isToday) {
                        Text(
                            stringResource(R.string.symptom_editable_hint),
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
                    dateLabel = if (isToday) stringResource(R.string.today_tab) else stringResource(R.string.common_yesterday),
                    dateKey = selectedDate,
                    onSave = { f -> vm.saveSymptom(f.morningStiffnessMin, f.nightPain, f.painScore, f.feverish, f.feverTemp, f.eyeSymptom, f.neuroRedFlag, f.mood, f.sleep, f.fatigue, f.notes) },
                )
            }

            // ---- BASDAI ----
            item {
                SectionCard(title = stringResource(R.string.basdai_title)) {
                    Text(
                        stringResource(R.string.basdai_intro_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(Spacing.sm))
                    val basdaiExisting = basdaiHistory.firstOrNull { it.date == selectedDate.toString() }
                    if (basdaiExisting != null) {
                        Text(
                            "$selectedDate 已记录（总分 %.1f），可修改后重新提交，覆盖原记录。".format(basdaiExisting.total),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(Spacing.sm))
                    }
                    Button(onClick = { showBasdai = true }) {
                        Text(
                            when {
                                isToday && basdaiExisting == null -> stringResource(R.string.symptom_start_today_eval)
                                isToday -> stringResource(R.string.symptom_edit_today_self)
                                basdaiExisting == null -> stringResource(R.string.symptom_fill_yesterday_eval)
                                else -> stringResource(R.string.symptom_edit_yesterday_eval)
                            }
                        )
                    }
                    Spacer(Modifier.height(Spacing.sm))
                    if (basdaiHistory.isEmpty()) {
                        Text(stringResource(R.string.common_no_records_yet), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        BasdaiList(basdaiHistory.take(8))
                    }
                }
            }

            // ---- 发作历史 ----
            if (flareHistory.isNotEmpty()) {
                item {
                    SectionCard(title = stringResource(R.string.symptom_flare_history)) {
                        FlareHistoryList(flareHistory.take(10))
                    }
                }
            }
            item { Spacer(Modifier.height(Spacing.xxl)) }
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
