package com.ashkb.app.ui.report

import android.content.Intent
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ashkb.app.data.repo.ReportRepository
import kotlinx.coroutines.launch
import java.time.LocalDate

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

    message?.let { msg ->
        AlertDialog(
            onDismissRequest = { vm.clearMessage() },
            title = { Text("提示") },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = { vm.clearMessage() }) { Text("知道了") } },
        )
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("报表与数据", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${o.adherence.medRatePct}%",
                        style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold,
                        color = when {
                            o.adherence.medRatePct >= 80 -> MaterialTheme.colorScheme.primary
                            o.adherence.medRatePct >= 50 -> Color(0xFFB8860B)
                            else -> MaterialTheme.colorScheme.error
                        },
                    )
                    Spacer(Modifier.padding(start = 16.dp))
                    Column {
                        Text("完成 ${o.adherence.medDone} · 部分 ${o.adherence.medPartial} · 跳过 ${o.adherence.medSkipped}",
                            style = MaterialTheme.typography.bodyMedium)
                        Text("共 ${o.adherence.medTotal} 次打卡（部分完成按 0.5 计）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { o.adherence.medRatePct / 100f }, modifier = Modifier.fillMaxWidth())
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
                    Spacer(Modifier.height(6.dp))
                    Text("⚠ 眼部症状天数 > 0：AS 合并葡萄膜炎需眼科评估（emr-001）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error)
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
                        Spacer(Modifier.height(6.dp))
                        Text("⚠ BASDAI ≥ 4.0：疾病活动度高，复诊时请与医生讨论（edu-th-002）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        item {
            SectionCard(title = "发作与体重") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCell("发作次数", "${o.flareCount}" + if (o.flareActive) " ⚠活跃" else "", Modifier.weight(1f))
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
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ======================= 趋势 =======================

@Composable
private fun TrendsPage(t: ReportRepository.Trends?) {
    if (t == null) {
        Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(48.dp))
            Text("趋势加载中…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { Spacer(Modifier.height(12.dp)) }
        item {
            SectionCard(title = "BASDAI 总分走势") {
                TrendChart(
                    points = t.basdai.map { it.date to it.total.toFloat() },
                    yMax = 10f, threshold = 4f, thresholdLabel = "活动度 4.0",
                )
            }
        }
        item {
            SectionCard(title = "疼痛评分（0–10）") {
                TrendChart(points = t.symptom.mapNotNull { s ->
                    s.painScore?.let { s.date to it.toFloat() }
                }, yMax = 10f)
            }
        }
        item {
            SectionCard(title = "晨僵时长（分钟）") {
                TrendChart(points = t.symptom.mapNotNull { s ->
                    s.morningStiffnessMin?.let { s.date to it.toFloat() }
                }, yMax = null)
            }
        }
        item {
            SectionCard(title = "体重（kg）") {
                TrendChart(points = t.weight.map { it.date to it.weightKg.toFloat() }, yMax = null)
            }
        }
        item {
            SectionCard(title = "收缩压 / 心率") {
                TrendChart(points = t.vitals.mapNotNull { v ->
                    v.bpSys?.let { v.date to it.toFloat() }
                }, yMax = null, lineColor = Color(0xFFD32F2F))
                Spacer(Modifier.height(8.dp))
                TrendChart(points = t.vitals.mapNotNull { v ->
                    v.heartRate?.let { v.date to it.toFloat() }
                }, yMax = null, lineColor = Color(0xFF1976D2))
            }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

/** 极简趋势折线图：网格 + 折线 + 端点 + 首末日期标签（零依赖 Canvas 实现）。 */
@Composable
private fun TrendChart(
    points: List<Pair<String, Float>>,
    yMax: Float?,
    threshold: Float? = null,
    thresholdLabel: String? = null,
    lineColor: Color = MaterialTheme.colorScheme.primary,
) {
    if (points.isEmpty()) {
        Text("暂无数据", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 12.dp))
        return
    }
    val values = points.map { it.second }
    val vMax = (yMax ?: ((values.max() * 1.15f).coerceAtLeast(1f))).coerceAtLeast(0.001f)
    val vMin = if (yMax != null) 0f else (values.min() * 0.85f).coerceAtMost(values.max())
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    Column {
        Canvas(Modifier.fillMaxWidth().height(150.dp)) {
            val w = size.width
            val h = size.height
            val left = 34f
            val bottom = h - 4f
            val top = 10f
            val grid = Color(0x22888888)
            val textPx = 9f.sp.toPx()

            // 网格与 y 轴刻度（4 条）
            for (i in 0..4) {
                val fy = bottom - (bottom - top) * i / 4f
                drawLine(grid, Offset(left, fy), Offset(w - 4f, fy), 1f)
                val label = vMin + (vMax - vMin) * i / 4f
                drawContext.canvas.nativeCanvas.drawText(
                    if (label >= 100) "%.0f".format(label) else "%.1f".format(label),
                    2f, fy + textPx / 3, android.graphics.Paint().apply {
                        this.color = android.graphics.Color.argb(160, 100, 100, 110)
                        this.textSize = textPx
                        isAntiAlias = true
                    })
            }

            // 阈值线（如 BASDAI 4.0）
            if (threshold != null) {
                val fy = bottom - (bottom - top) * (threshold - vMin) / (vMax - vMin)
                if (fy in top..bottom) {
                    drawLine(Color(0x80CC4444), Offset(left, fy), Offset(w - 4f, fy), 1.5f)
                    drawContext.canvas.nativeCanvas.drawText(thresholdLabel ?: "",
                        w - 90f, fy - 4f, android.graphics.Paint().apply {
                            color = android.graphics.Color.argb(180, 180, 50, 50)
                            textSize = textPx; isAntiAlias = true
                        })
                }
            }

            fun px(i: Int): Float =
                if (points.size == 1) (left + w) / 2 else left + (w - 4f - left) * i / (points.size - 1f)
            fun py(v: Float): Float = bottom - (bottom - top) * (v - vMin) / (vMax - vMin)

            // 折线
            if (points.size > 1) {
                val path = Path()
                points.forEachIndexed { i, p ->
                    val x = px(i); val y = py(p.second)
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, lineColor, style = Stroke(width = 4f))
            }
            // 数据点
            points.forEachIndexed { i, p ->
                drawCircle(lineColor, 4f, Offset(px(i), py(p.second)))
            }
            // 末值标签
            val last = points.last()
            drawContext.canvas.nativeCanvas.drawText("%.1f".format(last.second),
                (px(points.size - 1) - 30f).coerceIn(0f, w - 40f), py(last.second) - 8f,
                android.graphics.Paint().apply {
                    this.color = android.graphics.Color.argb(220, 30, 30, 40)
                    this.textSize = textPx * 1.1f; isAntiAlias = true
                    isFakeBoldText = true
                })
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(points.first().first.takeLast(5), style = MaterialTheme.typography.labelSmall, color = labelColor)
            Text("${points.size} 点", style = MaterialTheme.typography.labelSmall, color = labelColor)
            Text(points.last().first.takeLast(5), style = MaterialTheme.typography.labelSmall, color = labelColor)
        }
    }
}

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
                            onReady = { intent -> runCatching { context.startActivity(Intent.createChooser(intent, "分享复诊报告")) } },
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
                            onReady = { intent -> runCatching { context.startActivity(Intent.createChooser(intent, "分享紧急卡")) } },
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

@Composable
private fun SectionCard(title: String, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
        Column(Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp))
            content()
        }
    }
}
