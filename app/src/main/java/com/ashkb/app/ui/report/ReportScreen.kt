package com.ashkb.app.ui.report

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle


import com.ashkb.app.data.repo.ReportRepository
import com.ashkb.app.domain.ClinicalThresholds
import com.ashkb.app.R
import com.ashkb.app.ui.components.KeyValueRow
import com.ashkb.app.ui.components.LoadingBlock
import com.ashkb.app.ui.components.NavRow
import com.ashkb.app.ui.components.SectionCard
import com.ashkb.app.ui.components.StatusChip
import com.ashkb.app.ui.components.TrendChart
import com.ashkb.app.ui.components.TrendPoint
import com.ashkb.app.ui.GlobalMessages
import com.ashkb.app.ui.theme.accent
import com.ashkb.app.ui.theme.DataLarge
import com.ashkb.app.ui.theme.Size
import com.ashkb.app.ui.theme.Spacing
import com.ashkb.app.ui.theme.StatusTone
import kotlinx.coroutines.launch

/** P4 M9 报表页：概览 / 趋势 / 周月报 / 报告导出 四页签。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(vm: ReportViewModel, onOpenBackup: () -> Unit) {
    val overview by vm.overview.collectAsStateWithLifecycle()
    val trends by vm.trends.collectAsStateWithLifecycle()
    val trendDays by vm.trendDays.collectAsStateWithLifecycle()
    val periodic by vm.periodic.collectAsStateWithLifecycle()
    val periodDays by vm.periodDays.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState(pageCount = { 4 })
    val tabs = listOf(
        stringResource(R.string.report_overview_tab),
        stringResource(R.string.report_trends_tab),
        stringResource(R.string.report_tab_periodic),
        stringResource(R.string.report_export_section),
    )

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
                1 -> TrendsPage(trends, trendDays, vm::setTrendDays)
                2 -> PeriodicPage(vm, periodic, periodDays)
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

        // C2：补剂依从——取数/阈值/配色与上方「用药依从」完全同口径（部分完成按 0.5 计）
        item {
            SectionCard(title = stringResource(R.string.report_supp_adherence, o.adherence.days)) {
                val sup = o.supplement
                if (sup.total == 0) {
                    // 无打卡记录时不摆 0% 的假进度条，直接说明"暂无数据"
                    Text(
                        stringResource(R.string.report_supp_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    val rate = sup.ratePct
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
                                stringResource(R.string.report_supp_adherence_detail, sup.done, sup.partial, sup.skipped),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                stringResource(R.string.report_adherence_total, sup.total),
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
private fun TrendsPage(
    t: ReportRepository.Trends?,
    days: Int,
    onDays: (Int) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        // v1.0.45：时间范围切换**钉在顶部**，不随 6 张图滚走
        //（FilterChip 形态与 48dp 触摸下限沿用项目既有约定）
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TrendRangeChip(stringResource(R.string.report_range_7d), days == 7) { onDays(7) }
            TrendRangeChip(stringResource(R.string.report_range_30d), days == 30) { onDays(30) }
            TrendRangeChip(stringResource(R.string.report_range_90d), days == 90) { onDays(90) }
        }
        Box(Modifier.weight(1f)) {
            if (t == null) {
                LoadingBlock(minHeight = 240.dp, label = stringResource(R.string.report_stats_loading_dots))
            } else {
                TrendCharts(t)
            }
        }
    }
}

@Composable
private fun TrendRangeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        modifier = Modifier.heightIn(min = Size.touchMin),
    )
}

@Composable
private fun TrendCharts(t: ReportRepository.Trends) {
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { Spacer(Modifier.height(Spacing.md)) }
        item {
            SectionCard(title = stringResource(R.string.report_basdai_trend), subtitle = stringResource(R.string.report_threshold_note2)) {
                val basdaiPoints = remember(t.basdai) { t.basdai.map { TrendPoint(it.date, it.total.toFloat()) } }
                TrendChart(
                    points = basdaiPoints,
                    unit = "",
                    label = stringResource(R.string.basdai_total_score),
                    threshold = ClinicalThresholds.BASDAI_HIGH,
                )
            }
        }
        item {
            SectionCard(title = stringResource(R.string.symptom_pain_score), subtitle = stringResource(R.string.common_score_range)) {
                val painPoints = remember(t.symptom) {
                    t.symptom.mapNotNull { s -> s.painScore?.let { TrendPoint(s.date, it.toFloat()) } }
                }
                TrendChart(
                    points = painPoints,
                    unit = stringResource(R.string.exercise_minute_suffix),
                    label = stringResource(R.string.symptom_pain_score),
                )
            }
        }
        item {
            SectionCard(title = stringResource(R.string.symptom_morning_stiffness), subtitle = stringResource(R.string.exercise_minutes)) {
                val stiffPoints = remember(t.symptom) {
                    t.symptom.mapNotNull { s -> s.morningStiffnessMin?.let { TrendPoint(s.date, it.toFloat()) } }
                }
                TrendChart(
                    points = stiffPoints,
                    unit = stringResource(R.string.exercise_minute_space_suffix),
                    label = stringResource(R.string.symptom_morning_stiffness),
                )
            }
        }
        item {
            SectionCard(title = stringResource(R.string.vitals_weight), subtitle = "kg") {
                val weightPoints = remember(t.weight) { t.weight.map { TrendPoint(it.date, it.weightKg.toFloat()) } }
                TrendChart(
                    points = weightPoints,
                    unit = " kg",
                    label = stringResource(R.string.vitals_weight),
                )
            }
        }
        item {
            SectionCard(title = stringResource(R.string.vitals_bp_systolic), subtitle = "mmHg") {
                val bpPoints = remember(t.vitals) {
                    t.vitals.mapNotNull { v -> v.bpSys?.let { TrendPoint(v.date, it.toFloat()) } }
                }
                TrendChart(
                    points = bpPoints,
                    unit = " mmHg",
                    label = stringResource(R.string.vitals_bp_systolic),
                    accent = MaterialTheme.colorScheme.error,
                )
            }
        }
        item {
            SectionCard(title = stringResource(R.string.vitals_heart_rate), subtitle = "bpm") {
                val hrPoints = remember(t.vitals) {
                    t.vitals.mapNotNull { v -> v.heartRate?.let { TrendPoint(v.date, it.toFloat()) } }
                }
                TrendChart(
                    points = hrPoints,
                    unit = " bpm",
                    label = stringResource(R.string.vitals_heart_rate),
                    accent = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
        item { Spacer(Modifier.height(Spacing.xl)) }
    }
}

// 趋势图已抽到 ui/components/TrendChart.kt
// （按日期时间轴 / 自适应刻度 / 数据点与末点数值 / 读数摘要 / 阈值入轴槽 / 拖动读数 / 无障碍摘要）

// ======================= 周月报 =======================

/**
 * B4 周报 / 月报的均值口径：统一保留 1 位小数（与概览页 "%.1f/10" 同精度）。
 * 均值天然是小数，不做"整值去尾"，否则疼痛/晨僵/BASDAI/体重在图上与列表里的精度会不一致。
 */
private fun avg1(v: Double): String = "%.1f".format(v)

/**
 * B4 周月报页：7 天 / 30 天窗口的结构化小结。
 * 逐行「标签 : 数值」直接复用 KeyValueRow（全 App 唯一键值行形态），不另起一套行样式；
 * 这里只做"回看"，所以不摆进度条与达标色——进度语义交给概览页。
 */
@Composable
private fun PeriodicPage(
    vm: ReportViewModel,
    p: ReportRepository.PeriodicReport?,
    days: Int,
) {
    // 进入本页签时按当前窗口取一次数（保留上次结果不清空，避免来回切页签时闪空态）；
    // 之后切窗口由 chips 走 vm.setPeriodDays 直接重载，不会重复触发这里。
    LaunchedEffect(Unit) { vm.loadPeriodic(days) }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { Spacer(Modifier.height(12.dp)) }

        item {
            // 窗口切换：沿用项目既有 FilterChip 形态（SymptomScreen 今天 / 昨天），并满足 48dp 触摸下限
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(
                    selected = days == 7,
                    onClick = { vm.setPeriodDays(7) },
                    label = { Text(stringResource(R.string.report_periodic_week)) },
                    modifier = Modifier.heightIn(min = Size.touchMin),
                )
                FilterChip(
                    selected = days == 30,
                    onClick = { vm.setPeriodDays(30) },
                    label = { Text(stringResource(R.string.report_periodic_month)) },
                    modifier = Modifier.heightIn(min = Size.touchMin),
                )
            }
        }

        if (p == null) {
            item { LoadingBlock(minHeight = 240.dp, label = stringResource(R.string.report_stats_loading_dots)) }
        } else {
            item {
                // 副标题回显"这份数据是哪一段"，与上方 chips 互为确认（取 p.days，反映真实取数窗口）
                SectionCard(
                    title = stringResource(R.string.report_tab_periodic),
                    subtitle = stringResource(
                        if (p.days == 7) R.string.report_periodic_week else R.string.report_periodic_month,
                    ),
                ) {
                    // 用药依从：无打卡记录就不摆 0%（假数据），直接说"暂无"
                    KeyValueRow(
                        label = stringResource(R.string.report_periodic_med_adherence),
                        value = if (p.medTotal == 0) {
                            stringResource(R.string.report_periodic_none)
                        } else {
                            stringResource(
                                R.string.report_periodic_value_adherence,
                                p.medRatePct, p.medDone, p.medPartial, p.medSkipped,
                            )
                        },
                    )

                    // 补剂依从：字段与阈值口径同用药（部分完成计 0.5）
                    KeyValueRow(
                        label = stringResource(R.string.report_periodic_supp_adherence),
                        value = if (p.suppTotal == 0) {
                            stringResource(R.string.report_periodic_none)
                        } else {
                            stringResource(
                                R.string.report_periodic_value_adherence,
                                p.suppRatePct, p.suppDone, p.suppPartial, p.suppSkipped,
                            )
                        },
                    )

                    // 运动：exDoneDays 是"有完成记录的天数"，非完成条数
                    KeyValueRow(
                        label = stringResource(R.string.report_periodic_exercise),
                        value = stringResource(R.string.report_periodic_value_exercise, p.exDoneDays, p.exMinutes),
                    )

                    // 症状：两个均值都有才带出疼痛 / 晨僵，否则退化为只报记录天数
                    val avgPain = p.avgPain
                    val avgStiffness = p.avgStiffnessMin
                    KeyValueRow(
                        label = stringResource(R.string.report_periodic_symptom),
                        value = if (avgPain != null && avgStiffness != null) {
                            stringResource(
                                R.string.report_periodic_value_symptom_pain,
                                p.symptomDays, avg1(avgPain), avg1(avgStiffness),
                            )
                        } else {
                            stringResource(R.string.report_periodic_value_symptom, p.symptomDays)
                        },
                    )

                    // BASDAI：窗口内没做过则"暂无"，避免"0 次 · 平均 —"这类残句
                    KeyValueRow(
                        label = stringResource(R.string.report_periodic_basdai),
                        value = if (p.basdaiCount == 0) {
                            stringResource(R.string.report_periodic_none)
                        } else {
                            stringResource(
                                R.string.report_periodic_value_basdai,
                                p.basdaiCount, p.basdaiAvg?.let { avg1(it) } ?: "—",
                            )
                        },
                    )

                    // 体重：取窗口内最近一次，不显示"波动"（那属于趋势页）
                    KeyValueRow(
                        label = stringResource(R.string.report_periodic_weight),
                        value = p.weightLatest?.let { stringResource(R.string.report_periodic_value_weight, avg1(it)) }
                            ?: stringResource(R.string.report_periodic_none),
                    )

                    // 发作
                    KeyValueRow(
                        label = stringResource(R.string.report_periodic_flare),
                        value = stringResource(R.string.report_periodic_value_flare, p.flareCount),
                    )

                    // 复诊 / 检查：本期次数 + 下一次复诊（未来时，与本期计数分开陈述）
                    KeyValueRow(
                        label = stringResource(R.string.report_periodic_checkup),
                        value = stringResource(R.string.report_periodic_value_checkup, p.checkupCount),
                    )
                    if (!p.nextCheckupDate.isNullOrBlank()) {
                        Spacer(Modifier.height(Spacing.xs))
                        Text(
                            stringResource(R.string.report_periodic_next_checkup, p.nextCheckupDate),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        item { Spacer(Modifier.height(20.dp)) }
    }
}

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

