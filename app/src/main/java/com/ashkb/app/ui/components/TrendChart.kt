package com.ashkb.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Size as GeometrySize

import com.ashkb.app.R
import com.ashkb.app.ui.theme.Clinical
import com.ashkb.app.ui.theme.Motion
import com.ashkb.app.ui.theme.Size
import com.ashkb.app.ui.theme.Spacing
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

/** 趋势图数据点。`date` 为 ISO 日期串（yyyy-MM-dd）。 */
data class TrendPoint(val date: String, val value: Float)

/** 刻度间隔上限：再多就会让标签互相挤压（真实可容纳数由可用高度与实测字高决定）。 */
private const val MAX_TICK_INTERVALS = 5

/**
 * 趋势折线图（零第三方依赖，Canvas 自绘）。
 *
 * v1.0.45 起（第三轮审查后的趋势页改造，方案 A）：
 *  1. **X 轴按日期真实间隔定位**。此前用 `i / (size-1)` 等距铺点，于是「隔 3 天测一次」与
 *     「隔 3 小时测一次」画出来一样长——时间轴在骗人。[dayOffsetsOf] 取各点相对首点的天数偏移，
 *     任一日无法解析时回退等距，保证不会因一行脏数据整体不出图。
 *  2. **刻度数按可用高度与实测字高自适应**（[niceScale] 的 `maxIntervals`）。此前写死 `(hi-lo)/4`，
 *     5 个标签在矮画布 / 大字号下必然互相压叠。
 *  3. **画布高度确定化**（`height` 而非 `heightIn(min)`），不再依赖父级约束。
 *  4. **每个数据点画标记、末点直接标数值**——不拖动也能读到数；点太密时自动只留末点，避免糊成一条。
 *  5. **阈值标签移出数据区**：进左侧轴槽，且其宽度参与轴宽计算；与刻度同高时让位给阈值标签。
 *  6. 图上方补一行**读数摘要**（当前 / 较首次 / 均值 / 超阈值次数）。
 *
 * 另保留原有能力：虚线网格、圆头折线 + 渐变填充、首/中/尾三个日期刻度、拖动读数气泡、
 * TalkBack 文本摘要、入场动画只对新数据播一次。
 */
@Composable
fun TrendChart(
    points: List<TrendPoint>,
    unit: String,
    label: String,
    threshold: Float? = null,
    accent: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier,
) {
    if (points.isEmpty()) {
        EmptyState(
            icon = Icons.Outlined.ShowChart,
            title = stringResource(R.string.common_no_data),
            body = stringResource(R.string.report_trend_wait_note),
        )
        return
    }

    val cs = MaterialTheme.colorScheme
    val cl = Clinical.colors
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()

    // 文字随系统字号缩放：画布高度同步放宽，避免大字号下标签互相压叠
    val chartHeight = Size.chartHeight * density.fontScale.coerceIn(1f, 1.6f)

    val axisStyle: TextStyle = MaterialTheme.typography.labelSmall.copy(color = cs.onSurfaceVariant)
    val lastValueStyle: TextStyle = MaterialTheme.typography.labelMedium.copy(color = cs.onSurface)

    val gridDash = remember { PathEffect.dashPathEffect(floatArrayOf(6f, 6f)) }
    val thresholdDash = remember { PathEffect.dashPathEffect(floatArrayOf(10f, 8f)) }
    val linePx = with(density) { 2.dp.toPx() }
    val lineStroke = remember(linePx) {
        Stroke(width = linePx, cap = StrokeCap.Round, join = StrokeJoin.Round)
    }
    val linePath = remember { Path() }
    val fillPath = remember { Path() }
    val fillBrush = remember(accent) {
        Brush.verticalGradient(colors = listOf(accent.copy(alpha = 0.16f), Color.Transparent))
    }
    val thresholdTextStyle = remember(axisStyle, cl.warning) { axisStyle.copy(color = cl.warning) }
    val dotPx = with(density) { Size.chartDot.toPx() }

    // ---- 刻度数自适应：按「画布净高 ÷ (实测字高 + 间隙)」决定最多几段 ----
    val tickHeightPx = remember(axisStyle, textMeasurer) {
        textMeasurer.measure("0", axisStyle).size.height.toFloat()
    }
    val plotHeightPx = with(density) { (chartHeight - Spacing.sm - Spacing.xxl).toPx() }
    val tickGapPx = with(density) { Spacing.sm.toPx() }
    val maxIntervals = (plotHeightPx / (tickHeightPx + tickGapPx))
        .toInt().coerceIn(2, MAX_TICK_INTERVALS)

    val summary = remember(points, threshold) { summarizeValues(points, threshold) }
    val scale = remember(points, threshold, maxIntervals) {
        niceScale(points.map { it.value }, threshold, maxIntervals)
    }
    val vMin = scale.first
    val vMax = scale.second
    val step = scale.third

    val ticks = remember(scale) {
        buildList {
            var v = vMin
            var guard = 0
            while (v <= vMax + 1e-4f && guard < 24) {
                add(v)
                v += step
                guard++
            }
        }
    }

    // 文本只在组合期测量一次（旧实现每次重绘 new 7 个 Paint）
    val tickLayouts = remember(ticks, step, axisStyle) {
        ticks.map { textMeasurer.measure(tickLabel(it, step), axisStyle) }
    }
    // 阈值文字进左侧轴槽，宽度必须参与轴宽计算，否则会与标签争位或溢出画布
    val thresholdLayout = remember(threshold, step, thresholdTextStyle, textMeasurer) {
        threshold?.let { textMeasurer.measure(tickLabel(it, step), thresholdTextStyle) }
    }
    val axisWidthPx = (listOfNotNull(
        tickLayouts.maxOfOrNull { it.size.width }?.toFloat(),
        thresholdLayout?.size?.width?.toFloat(),
    ).maxOrNull() ?: 0f) + with(density) { Spacing.sm.toPx() }

    // ---- X 轴定位：优先按日期真实间隔 ----
    val dayOffsets = remember(points) { dayOffsetsOf(points) }
    val spanDays = (dayOffsets?.lastOrNull() ?: 0L).coerceAtLeast(0L)

    val xTickItems = remember(points, axisStyle, dayOffsets, spanDays) {
        val idx = midTickIndex(points.size, dayOffsets, spanDays)
        listOf(0, idx, points.lastIndex).distinct().map { i ->
            i to textMeasurer.measure(
                dateTick(points.first().date, points.last().date, points[i].date),
                axisStyle,
            )
        }
    }

    val lastValueLayout = remember(points, unit, lastValueStyle, textMeasurer) {
        textMeasurer.measure(fmtValue(points.last().value) + unit, lastValueStyle)
    }

    var selected by remember(points) { mutableStateOf<Int?>(null) }

    val selectedLayout = remember(selected, points, unit, axisStyle) {
        selected?.let { i ->
            val idx = i.coerceIn(0, points.lastIndex)
            val txt = "${dateTick(points.first().date, points.last().date, points[idx].date)} · " +
                "${fmtValue(points[idx].value)}$unit"
            textMeasurer.measure(txt, axisStyle.copy(color = cs.onSurface))
        }
    }

    // 入场动画只对「新数据」播一次：页签切换（HorizontalPager 回收视口外页面）或旋转屏回来时，
    // points 内容未变则直接呈现终态、不重播。dataKey 用内容 hash（List.hashCode 按元素计算），
    // 上游 Room 流重新发射的新 List 实例只要内容相同也不触发。
    val dataKey = remember(points) { points.hashCode() }
    val progress = remember { Animatable(0f) }
    var playedKey by rememberSaveable { mutableStateOf(Int.MIN_VALUE) }
    LaunchedEffect(dataKey) {
        if (playedKey == dataKey) {
            progress.snapTo(1f)
        } else {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(Motion.SlowMs, easing = EaseOut))
            playedKey = dataKey
        }
    }

    val dirRise = stringResource(R.string.trend_up_to)
    val dirFall = stringResource(R.string.trend_down_to)
    val dirFlat = stringResource(R.string.trend_flat)
    val a11y = remember(points, threshold, summary) {
        summarize(label, points, unit, threshold, dirRise, dirFall, dirFlat, summary)
    }

    Column(modifier) {
        ReadoutRow(summary = summary, unit = unit, threshold = threshold, accent = accent)
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(chartHeight)
                .semantics { contentDescription = a11y }
                .pointerInput(points, axisWidthPx, dayOffsets, spanDays) {
                    detectHorizontalDragGestures(
                        onDragStart = { off ->
                            selected = nearestIndex(off.x, points.size, axisWidthPx, size.width.toFloat(), dayOffsets, spanDays)
                        },
                        onDragEnd = { selected = null },
                        onDragCancel = { selected = null },
                    ) { change, _ ->
                        change.consume()
                        selected = nearestIndex(change.position.x, points.size, axisWidthPx, size.width.toFloat(), dayOffsets, spanDays)
                    }
                },
        ) {
            val padH = Spacing.sm.toPx()
            val left = axisWidthPx
            val right = size.width - padH
            val top = padH
            val bottom = size.height - Spacing.xxl.toPx()
            val gridPx = Size.divider.toPx()

            fun xAt(i: Int): Float = when {
                points.size == 1 -> (left + right) / 2f
                dayOffsets != null && spanDays > 0L -> {
                    // v1.0.46：clamp 防御未按日期排序的输入——中段出现比末点更晚的日期时，
                    // 不加 clamp 会画到绘图区右边界之外（数据层目前均升序，纯防御）。
                    val r = (dayOffsets[i] / spanDays.toFloat()).coerceIn(0f, 1f)
                    left + (right - left) * r
                }
                else -> left + (right - left) * i / (points.size - 1).toFloat()
            }
            fun yAt(v: Float): Float =
                bottom - (bottom - top) * ((v - vMin) / (vMax - vMin)).coerceIn(0f, 1f)

            val thrY = threshold?.let { yAt(it) }

            // 点太密时只留末点，否则圆点会糊成一条粗带
            val minGapPx = if (points.size > 1) {
                (1 until points.size).minOf { xAt(it) - xAt(it - 1) }
            } else {
                Float.MAX_VALUE
            }
            val showDots = minGapPx >= dotPx * 3f

            // ---- 虚线网格 + Y 轴刻度（与阈值同高时让位，避免两层文字叠在一起）----
            ticks.forEachIndexed { i, v ->
                val y = yAt(v)
                drawLine(
                    color = cs.outlineVariant,
                    start = Offset(left, y),
                    end = Offset(right, y),
                    strokeWidth = gridPx,
                    pathEffect = gridDash,
                )
                if (thrY != null && abs(y - thrY) < tickHeightPx * 0.9f) return@forEachIndexed
                val layout = tickLayouts[i]
                drawText(
                    textLayoutResult = layout,
                    topLeft = Offset(left - padH - layout.size.width, y - layout.size.height / 2f),
                )
            }

            // ---- 坐标轴 ----
            drawLine(cs.outlineVariant, Offset(left, top), Offset(left, bottom), gridPx)
            drawLine(cs.outlineVariant, Offset(left, bottom), Offset(right, bottom), gridPx)

            // ---- 阈值线 + 左侧轴槽标签（不再压在数据上）----
            if (threshold != null && thrY != null) {
                drawLine(
                    color = cl.warning,
                    start = Offset(left, thrY),
                    end = Offset(right, thrY),
                    strokeWidth = Size.chartStroke.toPx(),
                    pathEffect = thresholdDash,
                )
                thresholdLayout?.let { l ->
                    // v1.0.46：量程边缘（阈值恰为最大/最小值）时标签会垂直溢出画布——
                    // 旧 chip 实现有 coerceAtLeast(top)，v1.0.45 重写时丢了，这里补回。
                    val ty = (thrY - l.size.height / 2f)
                        .coerceIn(top, (bottom - l.size.height).coerceAtLeast(top))
                    drawText(
                        textLayoutResult = l,
                        topLeft = Offset(left - padH - l.size.width, ty),
                    )
                }
            }

            // ---- 数据线（按进度从左到右描出）+ 渐变填充 ----
            val visible = (points.size * progress.value).roundToInt().coerceIn(1, points.size)
            linePath.reset()
            for (i in 0 until visible) {
                val px = xAt(i)
                val py = yAt(points[i].value)
                if (i == 0) linePath.moveTo(px, py) else linePath.lineTo(px, py)
            }

            if (points.size == 1) {
                // 单点：画水平参考线，不画折线
                val y = yAt(points[0].value)
                drawLine(accent.copy(alpha = 0.45f), Offset(left, y), Offset(right, y), linePx)
            } else if (visible > 1) {
                fillPath.reset()
                fillPath.addPath(linePath)
                fillPath.lineTo(xAt(visible - 1), bottom)
                fillPath.lineTo(xAt(0), bottom)
                fillPath.close()
                drawPath(path = fillPath, brush = fillBrush)
            }

            if (points.size > 1) {
                drawPath(path = linePath, color = accent, style = lineStroke)
            }

            // ---- 数据点标记（稀疏时才逐点画）----
            if (showDots && visible > 1) {
                for (i in 0 until visible - 1) {
                    drawCircle(accent, dotPx * 0.55f, Offset(xAt(i), yAt(points[i].value)))
                }
            }

            // ---- 末点高亮 + 数值（不拖动也能读数）----
            // v1.0.46：仅入场动画播完后绘制——动画期间折线尚未扫到末点，末点圆与数值气泡
            // 若无条件绘制，会提前悬在折线终点位置（v1.0.45 的缺陷）。
            if (visible >= points.size) {
                val lastIdx = points.lastIndex
                val lx = xAt(lastIdx)
                val ly = yAt(points[lastIdx].value)
                drawCircle(accent, dotPx * 0.9f, Offset(lx, ly))
                drawCircle(cs.surfaceContainerLowest, dotPx * 0.45f, Offset(lx, ly))

                // 拖动选中时不画末点数值，避免与气泡叠字
                if (selected == null || selected != lastIdx) {
                    val padBoxH = Spacing.xs.toPx()
                    val padBoxV = Spacing.xxs.toPx()
                    val bw = lastValueLayout.size.width + padBoxH * 2
                    val bh = lastValueLayout.size.height + padBoxV * 2
                    val bx = (right - bw).coerceAtLeast(left)
                    var by = ly - bh - padBoxV * 2
                    if (by < top) by = ly + padBoxV * 2
                    if (by + bh > bottom) by = (bottom - bh).coerceAtLeast(top)
                    drawRoundRect(
                        color = cs.surfaceContainerLowest,
                        topLeft = Offset(bx, by),
                        size = GeometrySize(bw, bh),
                        cornerRadius = CornerRadius(Spacing.xs.toPx()),
                    )
                    drawText(lastValueLayout, topLeft = Offset(bx + padBoxH, by + padBoxV))
                }
            }

            // ---- 选中点读数 ----
            val selIdx = selected?.coerceIn(0, points.lastIndex)
            if (selIdx != null) {
                val x = xAt(selIdx)
                val y = yAt(points[selIdx].value)
                drawLine(cs.outline, Offset(x, top), Offset(x, bottom), gridPx)
                drawCircle(accent, dotPx, Offset(x, y))
                drawCircle(cs.surfaceContainerLowest, dotPx / 2, Offset(x, y))

                selectedLayout?.let { l ->
                    val padBoxH = Spacing.sm.toPx()
                    val padBoxV = Spacing.xs.toPx()
                    val bw = l.size.width + padBoxH * 2
                    val bh = l.size.height + padBoxV * 2
                    val bx = (x - bw / 2f).coerceIn(left, (right - bw).coerceAtLeast(left))
                    drawRoundRect(
                        color = cs.surfaceContainerHigh,
                        topLeft = Offset(bx, top),
                        size = GeometrySize(bw, bh),
                        cornerRadius = CornerRadius(Spacing.sm.toPx()),
                    )
                    drawText(l, topLeft = Offset(bx + padBoxH, top + padBoxV))
                }
            }

            // ---- X 轴：首 / 中 / 尾三个日期刻度；首尾强制贴边，不再靠 clamp 漂移 ----
            val padTick = Spacing.xs.toPx()
            xTickItems.forEachIndexed { k, (i, l) ->
                val x = when {
                    k == 0 -> left
                    i == points.lastIndex -> right - l.size.width
                    else -> xAt(i) - l.size.width / 2f
                }
                drawText(l, topLeft = Offset(x, bottom + padTick))
            }
        }
    }
}

/**
 * 图上方读数摘要：首行「当前值 + 较首次变化」，次行「均值 [· 超阈值次数]」。
 *
 * 方向**不做配色**——同一段组件既画 BASDAI 也画体重，「升」并不总是坏，用中性色避免误导；
 * 有阈值的序列才多显示超阈值次数。
 */
@Composable
private fun ReadoutRow(
    summary: TrendSummary,
    unit: String,
    threshold: Float?,
    accent: Color,
) {
    val cs = MaterialTheme.colorScheme
    val deltaText = summary.delta?.let { d ->
        stringResource(
            R.string.trend_readout_vs_first,
            if (abs(d) < 0.05f) fmtValue(0f) else (if (d > 0) "+" else "") + fmtValue(d),
        )
    }
    val secondary = buildString {
        append(stringResource(R.string.trend_readout_avg, fmtValue(summary.avg)))
        if (threshold != null) {
            append(" · ")
            append(stringResource(R.string.trend_readout_over, summary.overCount, summary.total))
        }
    }
    Column(Modifier.padding(bottom = Spacing.sm)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                fmtValue(summary.last) + unit,
                style = MaterialTheme.typography.titleMedium,
                color = accent,
            )
            if (deltaText != null) {
                Spacer(Modifier.width(Spacing.sm))
                Text(
                    deltaText,
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                )
            }
        }
        Text(
            secondary,
            style = MaterialTheme.typography.bodySmall,
            color = cs.onSurfaceVariant,
        )
    }
}

/** 图上读数摘要（纯函数产物，可单测）。 */
internal data class TrendSummary(
    val last: Float,
    val avg: Float,
    /** 末值 − 首值；单点时 null */
    val delta: Float?,
    /** 严格大于阈值的点数；无阈值时为 0 */
    val overCount: Int,
    val total: Int,
)

internal fun summarizeValues(points: List<TrendPoint>, threshold: Float?): TrendSummary {
    val vals = points.map { it.value }
    return TrendSummary(
        last = vals.last(),
        avg = vals.average().toFloat(),
        delta = if (vals.size > 1) vals.last() - vals.first() else null,
        overCount = threshold?.let { th -> vals.count { it > th } } ?: 0,
        total = vals.size,
    )
}

/**
 * 各点相对首点的天数偏移（用于按真实日期间隔定位）。
 * 任一日无法解析为 ISO 日期时返回 null——调用方回退等距，避免一行脏数据导致整张图不出。
 */
internal fun dayOffsetsOf(points: List<TrendPoint>): List<Long>? {
    val dates = points.map { runCatching { LocalDate.parse(it.date) }.getOrNull() ?: return null }
    val first = dates.first()
    return dates.map { ChronoUnit.DAYS.between(first, it) }
}

/** 中间那个 X 刻度的下标：按**日期中点**取（而非序号中点），时间轴才对称。 */
internal fun midTickIndex(size: Int, dayOffsets: List<Long>?, spanDays: Long): Int {
    if (size <= 2) return (size - 1) / 2
    if (dayOffsets != null && spanDays > 0L) {
        val half = spanDays / 2.0
        return dayOffsets.indices.minByOrNull { abs(dayOffsets[it] - half) } ?: size / 2
    }
    return size / 2
}

/** nice-number 阶梯：步长取 1 / 2 / 2.5 / 5 / 10 × 10ⁿ，刻度稳定落在整数或半整数上。 */
private val NICE_FACTORS = listOf(1f, 2f, 2.5f, 5f, 10f)

/**
 * 计算 Y 轴刻度。
 *
 * v1.0.45：新增 `maxIntervals`——从 nice 阶梯里挑第一个「舍入后间隔数 ≤ 预算」的步长，
 * 而不是固定按 4 段取值（矮画布 / 大字号下固定 4 段会让标签互压）。
 *
 * 预算下限取 2 而非 1：`interval = ceil(hi/s) − floor(lo/s)` 只能保证
 * `interval ≤ 预算 + 1`；当区间跨越 0 时（如 [-3, 7]）无论步长多大都会得到 2 段，
 * 预算 1 是无解输入。取 ≥2 后 `interval ≤ 预算` 严格成立（已由单测覆盖）。
 * `10 × mag` 一定落在阶梯内满足条件，循环必然收敛。
 */
internal fun niceScale(
    values: List<Float>,
    threshold: Float?,
    maxIntervals: Int = 4,
): Triple<Float, Float, Float> {
    var lo = values.min()
    var hi = values.max()
    threshold?.let {
        lo = minOf(lo, it)
        hi = maxOf(hi, it)
    }
    if (hi - lo < 1e-3f) {
        lo -= 1f
        hi += 1f
    }
    val target = maxIntervals.coerceIn(2, 10)
    val rough = (hi - lo) / target
    val mag = 10.0.pow(floor(log10(rough.toDouble())).toInt()).toFloat()
    var step = 10f * mag
    for (factor in NICE_FACTORS) {
        val s = factor * mag
        val interval = ceil(hi / s) - floor(lo / s)
        if (interval <= target) {
            step = s
            break
        }
    }
    return Triple(floor(lo / step) * step, ceil(hi / step) * step, step)
}

private fun tickLabel(v: Float, step: Float): String =
    if (step >= 1f && step == floor(step)) v.roundToInt().toString() else "%.1f".format(v)

private fun fmtValue(v: Float): String =
    if (v == v.roundToInt().toFloat()) v.roundToInt().toString() else "%.1f".format(v)

/** 跨年时显示完整日期，否则只显示 MM-dd（旧实现 `takeLast(5)` 会掉年份）。 */
private fun dateTick(first: String, last: String, date: String): String =
    if (first.take(4) != last.take(4)) date else date.takeLast(5)

/**
 * 拖动位置 → 最近数据点。
 *
 * v1.0.46 修复：X 轴改为按日期间隔定位后，本函数若仍按「序号比例」映射就会**选错点**——
 * 日期间隔不均时（如偏移 [0,1,2,20]），手指按在 60% 宽度处，旧实现选中 index 2，
 * 而那个点画在 10% 的位置，读数气泡会跳到离手指很远的地方。
 * 现在与 [xAt] 同一套定位：有日期偏移时按「天数比例」找最近点，否则回退序号比例。
 */
internal fun nearestIndex(
    x: Float,
    count: Int,
    left: Float,
    width: Float,
    dayOffsets: List<Long>? = null,
    spanDays: Long = 0L,
): Int {
    if (count <= 1) return 0
    val usable = (width - left).coerceAtLeast(1f)
    val ratio = ((x - left) / usable).coerceIn(0f, 1f)
    if (dayOffsets != null && spanDays > 0L) {
        var best = 0
        var bestDist = Float.MAX_VALUE
        for (i in dayOffsets.indices) {
            if (i >= count) break
            val d = abs(dayOffsets[i] / spanDays.toFloat() - ratio)
            if (d < bestDist) {
                bestDist = d
                best = i
            }
        }
        return best
    }
    return (ratio * (count - 1)).roundToInt().coerceIn(0, count - 1)
}

private fun summarize(
    label: String,
    points: List<TrendPoint>,
    unit: String,
    threshold: Float?,
    dirRise: String,
    dirFall: String,
    dirFlat: String,
    summary: TrendSummary,
): String {
    val first = points.first().value
    val dir = when {
        summary.last > first + 0.05f -> dirRise
        summary.last < first - 0.05f -> dirFall
        else -> dirFlat
    }
    return buildString {
        append("$label 趋势，共 ${summary.total} 个数据点")
        if (summary.total > 1) append("，从 ${fmtValue(first)}$unit $dir ${fmtValue(summary.last)}$unit")
        append("，均值 ${fmtValue(summary.avg)}$unit")
        threshold?.let { th ->
            if (summary.overCount > 0) {
                append("，其中 ${summary.overCount} 次超过阈值 ${fmtValue(th)}$unit")
            }
        }
    }
}
