package com.ashkb.app.ui.report

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

import com.ashkb.app.R
import com.ashkb.app.ui.GlobalMessages
import com.ashkb.app.domain.ClinicalThresholds
import com.ashkb.app.ui.components.LoadingBlock
import com.ashkb.app.ui.components.NavRow
import com.ashkb.app.ui.components.SectionCard
import com.ashkb.app.ui.components.StatusChip
import com.ashkb.app.ui.components.TrendChart
import com.ashkb.app.ui.components.TrendPoint
import com.ashkb.app.ui.theme.DataLarge
import com.ashkb.app.ui.theme.Spacing
import com.ashkb.app.ui.theme.StatusTone
import com.ashkb.app.ui.theme.accent
import com.ashkb.app.data.repo.ReportRepository
import kotlinx.coroutines.launch

/** P4 M9 报表页：概览 / 趋势 / 报告导出 三页签。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(vm: ReportViewModel, onOpenBackup: () -> Unit) {
    val overview by vm.overview.collectAsState()
    val trends by vm.trends.collectAsState()
    val busy by vm.busy.collectAsState()
    val message by vm.message.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState(pageCount = { 3 })
    val tabs = listOf(stringResource(R.string.report_overview_tab), stringResource(R.string.report_trends_tab), stringResource(R.string.report_export_section))

    // 提示类消息改走全局 Snackbar（非阻塞）——不再"每个操作都要点一次知道了"
    LaunchedEffect(message) {
        message?.let {
            GlobalMessages.post(it)
            vm.clearMessage()
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.me_report_nav), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(R.string.report_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedButton(onClick = { vm.refresh() }, enabled = !busy) { Text(stringResource(R.string.common_refresh)) }
        }

        ScrollableTabRow(selectedTabIndex = pager.currentPage, edgePadding = 8.dp) {
            tabs.forEachIndexed { i, t ->
                Tab(selected = pager.currentPage == i, onClick = {
                    scope.launch { pager.animateScrollToPage(i) }
                }, text = { Text(t) })
            }
        }

        HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { page ->
            when (page) {
                0 -> OverviewPage(overview)
                1 -> TrendsPage(trends)
                else -> ExportPage(vm, busy, context, onOpenBackup)
            }
        }
    }
}

// ======================= 概览 =======================

@Composable
private fun OverviewPage(o: ReportRepository.Overview?) {
    if (o == null) {
        Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(48.dp))
            Text(stringResource(R.string.report_stats_loading), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { Spacer(Modifier.height(12.dp)) }

        item {
            SectionCard(title = stringResource(R.string.report_adherence_days, o.adherence.days)) {
                // 阈值 90/70（原为 80/50），且数字与进度条必须同 tone（修此前的矛盾）
                val rate = o.adherence.medRatePct
                val tone = when {
                    rate >= ClinicalThresholds.ADHERENCE_GOOD -> StatusTone.Success
                    rate >= ClinicalThresholds.ADHERENCE_FAIR -> StatusTone.Warning
                    else -> StatusTone.Danger
                }
                val accent = tone.accent()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("$rate%", style = DataLarge, color = accent)
                    Spacer(Modifier.padding(start = Spacing.lg))
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                        Text(
                            stringResource(R.string.report_adherence_breakdown, o.adherence.medDone, o.adherence.medPartial, o.adherence.medSkipped),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            stringResource(R.string.report_adherence_total, o.adherence.medTotal),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    StatusChip(ClinicalThresholds.adherenceLabel(rate), tone)
                }
                Spacer(Modifier.height(Spacing.sm))
                LinearProgressIndicator(
                    progress = { rate / 100f },
                    modifier = Modifier.fillMaxWidth(),
                    color = accent,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                )
            }
        }

        item {
            SectionCard(title = stringResource(R.string.report_exercise_section)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCell(stringResource(R.string.report_completed_count), "${o.exercise.doneCount}", Modifier.weight(1f))
                    StatCell(stringResource(R.string.report_total_duration), stringResource(R.string.report_exercise_minutes, o.exercise.totalMinutes), Modifier.weight(1f))
                    StatCell(stringResource(R.string.med_skip), "${o.exercise.skippedCount}", Modifier.weight(1f))
                }
            }
        }

        item {
            SectionCard(title = stringResource(R.string.symptom_overview_title)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCell(stringResource(R.string.report_record_days), "${o.symptom.daysRecorded}/30", Modifier.weight(1f))
                    StatCell(stringResource(R.string.report_avg_pain), o.symptom.avgPain?.let { "%.1f/10".format(it) } ?: "—", Modifier.weight(1f))
                    StatCell(stringResource(R.string.report_avg_stiffness), o.symptom.avgStiffnessMin?.let { "%.0f 分".format(it) } ?: "—", Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCell(stringResource(R.string.symptom_night_pain_days), "${o.symptom.nightPainDays}", Modifier.weight(1f))
                    StatCell(stringResource(R.string.symptom_eye_symptoms), stringResource(R.string.report_eye_days, o.symptom.eyeDays), Modifier.weight(1f))
                    StatCell(stringResource(R.string.report_fever_days), "${o.symptom.feverDays}", Modifier.weight(1f))
                }
                if (o.symptom.eyeDays > 0) {
                    Spacer(Modifier.height(Spacing.xs))
                    StatusChip(
                        text = stringResource(R.string.report_eye_days_warning, o.symptom.eyeDays),
                        tone = StatusTone.Danger,
                        icon = Icons.Rounded.WarningAmber,
                    )
                }
            }
        }

        item {
            SectionCard(title = stringResource(R.string.report_basdai_section)) {
                if (o.basdaiLatest == null) {
                    Text(stringResource(R.string.report_no_basdai), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatCell(stringResource(R.string.report_latest), "%.1f/10".format(o.basdaiLatest.total), Modifier.weight(1f))
                        StatCell(stringResource(R.string.report_mom), o.basdaiDelta?.let { "%+.1f".format(it) } ?: "—", Modifier.weight(1f))
                        StatCell(stringResource(R.string.exercise_count), "${o.basdaiCount30}", Modifier.weight(1f))
                    }
                    if (o.basdaiLatest.total >= 4.0) {
                        Spacer(Modifier.height(Spacing.xs))
                        StatusChip(
                            text = stringResource(R.string.basdai_high_alert_note),
                            tone = StatusTone.Danger,
                            icon = Icons.Rounded.WarningAmber,
                        )
                    }
                }
            }
        }

        item {
            SectionCard(title = stringResource(R.string.report_flare_weight_section)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCell(stringResource(R.string.report_flare_count), "${o.flareCount}" + if (o.flareActive) stringResource(R.string.stage_active_paren) else "", Modifier.weight(1f))
                    StatCell(stringResource(R.string.wellness_latest_weight), o.weightLatest?.let { "${it.weightKg} kg" } ?: "—", Modifier.weight(1f))
                    StatCell(stringResource(R.string.trend_vs_last), o.weightDelta?.let { "%+.1f kg".format(it) } ?: "—", Modifier.weight(1f))
                }
            }
        }

        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
private fun StatCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, style = MaterialTheme.typography.titleLarge)
        Text(label, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ======================= 趋势 =======================

@Composable
private fun TrendsPage(t: ReportRepository.Trends?) {
    if (t == null) {
        LoadingBlock(minHeight = 240.dp, label = stringResource(R.string.report_stats_loading_dots))
        return
    }
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { Spacer(Modifier.height(12.dp)) }
        item {
            SectionCard(title = stringResource(R.string.report_basdai_trend), subtitle = stringResource(R.string.report_threshold_note2)) {
                TrendChart(
                    points = t.basdai.map { TrendPoint(it.date, it.total.toFloat()) },
                    unit = "",
                    label = stringResource(R.string.basdai_total_score),
                    threshold = ClinicalThresholds.BASDAI_HIGH,
                    thresholdLabel = stringResource(R.string.report_activity_level, ClinicalThresholds.BASDAI_HIGH),
                )
            }
        }
        item {
            SectionCard(title = stringResource(R.string.symptom_pain_score), subtitle = stringResource(R.string.common_score_range)) {
                TrendChart(
                    points = t.symptom.mapNotNull { s ->
                        s.painScore?.let { TrendPoint(s.date, it.toFloat()) }
                    },
                    unit = stringResource(R.string.exercise_minute_suffix),
                    label = stringResource(R.string.symptom_pain_score),
                )
            }
        }
        item {
            SectionCard(title = stringResource(R.string.symptom_morning_stiffness), subtitle = stringResource(R.string.exercise_minutes)) {
                TrendChart(
                    points = t.symptom.mapNotNull { s ->
                        s.morningStiffnessMin?.let { TrendPoint(s.date, it.toFloat()) }
                    },
                    unit = stringResource(R.string.exercise_minute_space_suffix),
                    label = stringResource(R.string.symptom_morning_stiffness),
                )
            }
        }
        item {
            SectionCard(title = stringResource(R.string.vitals_weight), subtitle = "kg") {
                TrendChart(
                    points = t.weight.map { TrendPoint(it.date, it.weightKg.toFloat()) },
                    unit = " kg",
                    label = stringResource(R.string.vitals_weight),
                )
            }
        }
        item {
            SectionCard(title = stringResource(R.string.vitals_bp_systolic), subtitle = "mmHg") {
                TrendChart(
                    points = t.vitals.mapNotNull { v ->
                        v.bpSys?.let { TrendPoint(v.date, it.toFloat()) }
                    },
                    unit = " mmHg",
                    label = stringResource(R.string.vitals_bp_systolic),
                    accent = MaterialTheme.colorScheme.error,
                )
            }
        }
        item {
            SectionCard(title = stringResource(R.string.vitals_heart_rate), subtitle = "bpm") {
                TrendChart(
                    points = t.vitals.mapNotNull { v ->
                        v.heartRate?.let { TrendPoint(v.date, it.toFloat()) }
                    },
                    unit = " bpm",
                    label = stringResource(R.string.vitals_heart_rate),
                    accent = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

// 趋势图已抽到 ui/components/TrendChart.kt（坐标轴 / 整数刻度 / 拖动读数 / 无障碍摘要）

// ======================= 报告导出 =======================

@Composable
private fun ExportPage(vm: ReportViewModel, busy: Boolean, context: android.content.Context, onOpenBackup: () -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { Spacer(Modifier.height(12.dp)) }

        item {
            SectionCard(title = stringResource(R.string.report_pdf_title)) {
                Text(
                    stringResource(R.string.report_pdf_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                val shareTitle = stringResource(R.string.report_share_action)
                Button(
                    onClick = {
                        vm.generateReportPdf(
                            onReady = { intent ->
                                runCatching { context.startActivity(Intent.createChooser(intent, shareTitle)) }
                                    .onFailure { context.getString(R.string.report_share_fail, it.message) }
                            },
                            onError = { vm.reportError(it) },
                        )
                    },
                    enabled = !busy,
                ) { Text(if (busy) stringResource(R.string.backup_generating_dots) else stringResource(R.string.report_generate_pdf)) }
            }
        }

        item {
            SectionCard(title = stringResource(R.string.emergency_card_pdf_title)) {
                Text(
                    stringResource(R.string.emergency_pdf_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                val shareTitle = stringResource(R.string.emergency_share_card)
                OutlinedButton(
                    onClick = {
                        vm.generateEmergencyCardPdf(
                            onReady = { intent ->
                                runCatching { context.startActivity(Intent.createChooser(intent, shareTitle)) }
                                    .onFailure { context.getString(R.string.report_share_fail, it.message) }
                            },
                            onError = { vm.reportError(it) },
                        )
                    },
                    enabled = !busy,
                ) { Text(stringResource(R.string.emergency_generate_pdf)) }
            }
        }

        item {
            NavRow(
                icon = Icons.Rounded.Backup,
                title = stringResource(R.string.backup_section_title),
                subtitle = stringResource(R.string.backup_section_subtitle),
                onClick = onOpenBackup,
            )
        }

        item { Spacer(Modifier.height(20.dp)) }
    }
}

// ======================= 公共 =======================
// SectionCard 已统一到 ui/components/Cards.kt（此前 5 份同名实现在此收口）
