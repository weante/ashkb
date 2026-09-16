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
import androidx.compose.ui.unit.dp
import com.ashkb.app.ui.GlobalMessages
import com.ashkb.app.domain.ClinicalThresholds
import com.ashkb.app.ui.components.LoadingBlock
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
fun ReportScreen(vm: ReportViewModel) {
    val overview by vm.overview.collectAsState()
    val trends by vm.trends.collectAsState()
    val busy by vm.busy.collectAsState()
    val message by vm.message.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState(pageCount = { 3 })
    val tabs = listOf("概览", "趋势", "报告导出")

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
                Text("报表与数据", style = MaterialTheme.typography.headlineSmall)
                Text("近 30 天统计 · 趋势 · 复诊报告",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedButton(onClick = { vm.refresh() }, enabled = !busy) { Text("刷新") }
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
                else -> ExportPage(vm, busy, context)
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
            Text("统计加载中…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { Spacer(Modifier.height(12.dp)) }

        item {
            SectionCard(title = "服药依从（${o.adherence.days} 天）") {
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
                            "完成 ${o.adherence.medDone} · 部分 ${o.adherence.medPartial} · 跳过 ${o.adherence.medSkipped}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            "共 ${o.adherence.medTotal} 次打卡（部分完成按 0.5 计）",
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
            SectionCard(title = "运动执行") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCell("完成次数", "${o.exercise.doneCount}", Modifier.weight(1f))
                    StatCell("累计时长", "${o.exercise.totalMinutes} 分", Modifier.weight(1f))
                    StatCell("跳过", "${o.exercise.skippedCount}", Modifier.weight(1f))
                }
            }
        }

        item {
            SectionCard(title = "症状概览") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCell("记录天数", "${o.symptom.daysRecorded}/30", Modifier.weight(1f))
                    StatCell("平均疼痛", o.symptom.avgPain?.let { "%.1f/10".format(it) } ?: "—", Modifier.weight(1f))
                    StatCell("平均晨僵", o.symptom.avgStiffnessMin?.let { "%.0f 分".format(it) } ?: "—", Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCell("夜间痛天数", "${o.symptom.nightPainDays}", Modifier.weight(1f))
                    StatCell("眼部症状", "${o.symptom.eyeDays} 天", Modifier.weight(1f))
                    StatCell("发热天数", "${o.symptom.feverDays}", Modifier.weight(1f))
                }
                if (o.symptom.eyeDays > 0) {
                    Spacer(Modifier.height(Spacing.xs))
                    StatusChip(
                        text = "眼部症状 ${o.symptom.eyeDays} 天：AS 合并葡萄膜炎需眼科评估（emr-001）",
                        tone = StatusTone.Danger,
                        icon = Icons.Rounded.WarningAmber,
                    )
                }
            }
        }

        item {
            SectionCard(title = "疾病活动度（BASDAI）") {
                if (o.basdaiLatest == null) {
                    Text("近 30 天无 BASDAI 自评", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatCell("最新", "%.1f/10".format(o.basdaiLatest.total), Modifier.weight(1f))
                        StatCell("环比", o.basdaiDelta?.let { "%+.1f".format(it) } ?: "—", Modifier.weight(1f))
                        StatCell("次数", "${o.basdaiCount30}", Modifier.weight(1f))
                    }
                    if (o.basdaiLatest.total >= 4.0) {
                        Spacer(Modifier.height(Spacing.xs))
                        StatusChip(
                            text = "BASDAI ≥ 4.0：疾病活动度高，复诊时请与医生讨论（edu-th-002）",
                            tone = StatusTone.Danger,
                            icon = Icons.Rounded.WarningAmber,
                        )
                    }
                }
            }
        }

        item {
            SectionCard(title = "发作与体重") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCell("发作次数", "${o.flareCount}" + if (o.flareActive) "（活跃）" else "", Modifier.weight(1f))
                    StatCell("最新体重", o.weightLatest?.let { "${it.weightKg} kg" } ?: "—", Modifier.weight(1f))
                    StatCell("较上次", o.weightDelta?.let { "%+.1f kg".format(it) } ?: "—", Modifier.weight(1f))
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
        LoadingBlock(minHeight = 240.dp, label = "正在统计…")
        return
    }
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { Spacer(Modifier.height(12.dp)) }
        item {
            SectionCard(title = "BASDAI 总分走势", subtitle = "阈值以上为高活动度") {
                TrendChart(
                    points = t.basdai.map { TrendPoint(it.date, it.total.toFloat()) },
                    unit = "",
                    label = "BASDAI 总分",
                    threshold = ClinicalThresholds.BASDAI_HIGH,
                    thresholdLabel = "活动度 ${ClinicalThresholds.BASDAI_HIGH}",
                )
            }
        }
        item {
            SectionCard(title = "疼痛评分", subtitle = "0–10 分") {
                TrendChart(
                    points = t.symptom.mapNotNull { s ->
                        s.painScore?.let { TrendPoint(s.date, it.toFloat()) }
                    },
                    unit = " 分",
                    label = "疼痛评分",
                )
            }
        }
        item {
            SectionCard(title = "晨僵时长", subtitle = "分钟") {
                TrendChart(
                    points = t.symptom.mapNotNull { s ->
                        s.morningStiffnessMin?.let { TrendPoint(s.date, it.toFloat()) }
                    },
                    unit = " 分钟",
                    label = "晨僵时长",
                )
            }
        }
        item {
            SectionCard(title = "体重", subtitle = "kg") {
                TrendChart(
                    points = t.weight.map { TrendPoint(it.date, it.weightKg.toFloat()) },
                    unit = " kg",
                    label = "体重",
                )
            }
        }
        item {
            SectionCard(title = "收缩压", subtitle = "mmHg") {
                TrendChart(
                    points = t.vitals.mapNotNull { v ->
                        v.bpSys?.let { TrendPoint(v.date, it.toFloat()) }
                    },
                    unit = " mmHg",
                    label = "收缩压",
                    accent = MaterialTheme.colorScheme.error,
                )
            }
        }
        item {
            SectionCard(title = "心率", subtitle = "bpm") {
                TrendChart(
                    points = t.vitals.mapNotNull { v ->
                        v.heartRate?.let { TrendPoint(v.date, it.toFloat()) }
                    },
                    unit = " bpm",
                    label = "心率",
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
private fun ExportPage(vm: ReportViewModel, busy: Boolean, context: android.content.Context) {
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { Spacer(Modifier.height(12.dp)) }

        item {
            SectionCard(title = "复诊报告（PDF）") {
                Text(
                    "汇总健康档案、当前用药、30 天依从与症状、BASDAI 走势、近 180 天化验与复诊记录、下次复诊安排，生成 PDF 供复诊时出示。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = {
                        vm.generateReportPdf(
                            onReady = { intent ->
                                runCatching { context.startActivity(Intent.createChooser(intent, "分享复诊报告")) }
                                    .onFailure { GlobalMessages.post("打开分享面板失败：${it.message}") }
                            },
                            onError = { vm.reportError(it) },
                        )
                    },
                    enabled = !busy,
                ) { Text(if (busy) "生成中…" else "生成复诊报告 PDF") }
            }
        }

        item {
            SectionCard(title = "紧急卡打印版（PDF）") {
                Text(
                    "M7 紧急卡纸质版：患者信息、紧急联系人、五应急场景处理卡，打印后随身携带。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = {
                        vm.generateEmergencyCardPdf(
                            onReady = { intent ->
                                runCatching { context.startActivity(Intent.createChooser(intent, "分享紧急卡")) }
                                    .onFailure { GlobalMessages.post("打开分享面板失败：${it.message}") }
                            },
                            onError = { vm.reportError(it) },
                        )
                    },
                    enabled = !busy,
                ) { Text("生成紧急卡 PDF") }
            }
        }

        item {
            SectionCard(title = "数据备份（R20）") {
                Text(
                    "全量加密备份 / 恢复 / WebDAV 远程备份在「我的 → 备份与数据」中进行。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item { Spacer(Modifier.height(20.dp)) }
    }
}

// ======================= 公共 =======================
// SectionCard 已统一到 ui/components/Cards.kt（此前 5 份同名实现在此收口）
