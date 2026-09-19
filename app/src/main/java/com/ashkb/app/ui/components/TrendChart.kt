package com.ashkb.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

/** 趋势图数据点。`date` 为 ISO 日期串（yyyy-MM-dd）。 */
data class TrendPoint(val date: String, val value: Float)

/**
 * 趋势折线图（零第三方依赖，Canvas 自绘）。
 *
 * 相对旧实现的改动：坐标轴与虚线网格、nice-number 整数刻度、12sp 且对比度达标的标签、
 * 组合期一次性测量文本（不再每次重绘 new 7 个 `Paint`）、圆头折线 + 渐变填充、
 * 阈值线右侧 chip（不再硬编码 `w - 90f`）、首/中/尾三个**日期**刻度、
 * 拖动可读数并弹出数值气泡、以及 TalkBack 可念出的文本摘要。
 */
@Composable
fun TrendChart(
    points: List<TrendPoint>,
    unit: String,
    label: String,
    threshold: Float? = null,
    thresholdLabel: String? = null,
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
    val thresholdLayout = remember(thresholdLabel, thresholdTextStyle) {
        thresholdLabel?.let { textMeasurer.measure(it, thresholdTextStyle) }
    }
    val selectedTextStyle = remember(axisStyle, cs.onSurface) { axisStyle.copy(color = cs.onSurface) }

    val scale = remember(points, threshold) { niceScale(points.map { it.value }, threshold) }
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
    val axisWidthPx = (tickLayouts.maxOfOrNull { it.size.width }?.toFloat() ?: 0f) +
        with(density) { Spacing.sm.toPx() }

    val xTickItems = remember(points, axisStyle) {
        listOf(0, (points.size - 1) / 2, points.lastIndex).distinct().map { i ->
            i to textMeasurer.measure(
                dateTick(points.first().date, points.last().date, points[i].date),
                axisStyle,
            )
        }
    }

    var selected by remember(points) { mutableStateOf<Int?>(null) }

    val selectedLayout = remember(selected, points, unit, selectedTextStyle) {
        selected?.let { i ->
            val idx = i.coerceIn(0, points.lastIndex)
            val txt = "${dateTick(points.first().date, points.last().date, points[idx].date)} · " +
                "${fmtValue(points[idx].value)}$unit"
            textMeasurer.measure(txt, selectedTextStyle)
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
    val a11y = remember(points, threshold) { summarize(label, points, unit, threshold, dirRise, dirFall, dirFlat) }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = chartHeight)
            .semantics { contentDescription = a11y }
            .pointerInput(points, axisWidthPx) {
                detectHorizontalDragGestures(
                    onDragStart = { off ->
                        selected = nearestIndex(off.x, points.size, axisWidthPx, size.width.toFloat())
                    },
                    onDragEnd = { selected = null },
                    onDragCancel = { selected = null },
                ) { change, _ ->
                    change.consume()
                    selected = nearestIndex(change.position.x, points.size, axisWidthPx, size.width.toFloat())
                }
            },
    ) {
        val padH = Spacing.sm.toPx()
        val left = axisWidthPx
        val right = size.width - padH
        val top = padH
        val bottom = size.height - Spacing.xxl.toPx()
        val gridPx = Size.divider.toPx()

        fun xAt(i: Int): Float =
            if (points.size == 1) (left + right) / 2f
            else left + (right - left) * i / (points.size - 1).toFloat()
        fun yAt(v: Float): Float =
            bottom - (bottom - top) * ((v - vMin) / (vMax - vMin)).coerceIn(0f, 1f)

        // ---- 虚线网格 + Y 轴刻度 ----
        ticks.forEachIndexed { i, v ->
            val y = yAt(v)
            drawLine(
                color = cs.outlineVariant,
                start = Offset(left, y),
                end = Offset(right, y),
                strokeWidth = gridPx,
                pathEffect = gridDash,
            )
            val layout = tickLayouts[i]
            drawText(
                textLayoutResult = layout,
                topLeft = Offset(left - padH - layout.size.width, y - layout.size.height / 2f),
            )
        }

        // ---- 坐标轴 ----
        drawLine(cs.outlineVariant, Offset(left, top), Offset(left, bottom), gridPx)
        drawLine(cs.outlineVariant, Offset(left, bottom), Offset(right, bottom), gridPx)

        // ---- 阈值线 + 右侧 chip（先测宽再定位，窄屏不溢出）----
        if (threshold != null && threshold >= vMin && threshold <= vMax) {
            val y = yAt(threshold)
            drawLine(
                color = cl.warning,
                start = Offset(left, y),
                end = Offset(right, y),
                strokeWidth = Size.chartStroke.toPx(),
                pathEffect = thresholdDash,
            )
            thresholdLayout?.let { l ->
                val padBoxH = Spacing.xs.toPx()
                val padBoxV = Spacing.xxs.toPx()
                val bw = l.size.width + padBoxH * 2
                val bh = l.size.height + padBoxV * 2
                val bx = right - bw
                val by = (y - bh - padBoxV).coerceAtLeast(top)
                drawRoundRect(
                    color = cs.surfaceContainerLowest,
                    topLeft = Offset(bx, by),
                    size = GeometrySize(bw, bh),
                    cornerRadius = CornerRadius(bh / 2f),
                )
                drawText(l, topLeft = Offset(bx + padBoxH, by + padBoxV))
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

        // ---- 选中点读数 ----
        val selIdx = selected?.coerceIn(0, points.lastIndex)
        if (selIdx != null) {
            val x = xAt(selIdx)
            val y = yAt(points[selIdx].value)
            drawLine(cs.outline, Offset(x, top), Offset(x, bottom), gridPx)
            drawCircle(accent, Size.chartDot.toPx(), Offset(x, y))
            drawCircle(cs.surfaceContainerLowest, Size.chartDot.toPx() / 2, Offset(x, y))

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

        // ---- X 轴：首 / 中 / 尾三个日期刻度（不再显示"${points.size} 点"）----
        val padTick = Spacing.xs.toPx()
        xTickItems.forEach { (i, l) ->
            val x = (xAt(i) - l.size.width / 2f).coerceIn(left, (right - l.size.width).coerceAtLeast(left))
            drawText(l, topLeft = Offset(x, bottom + padTick))
        }
    }
}

/** nice-number：步长取 1 / 2 / 2.5 / 5 / 10 × 10ⁿ，刻度稳定落在整数或半整数上。 */
internal fun niceScale(values: List<Float>, threshold: Float?): Triple<Float, Float, Float> {
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
    val rough = (hi - lo) / 4f
    val mag = 10.0.pow(floor(log10(rough.toDouble())).toInt()).toFloat()
    val norm = rough / mag
    val factor = when {
        norm <= 1f -> 1f
        norm <= 2f -> 2f
        norm <= 2.5f -> 2.5f
        norm <= 5f -> 5f
        else -> 10f
    }
    val step = factor * mag
    return Triple(floor(lo / step) * step, ceil(hi / step) * step, step)
}

private fun tickLabel(v: Float, step: Float): String =
    if (step >= 1f && step == floor(step)) v.roundToInt().toString() else "%.1f".format(v)

private fun fmtValue(v: Float): String =
    if (v == v.roundToInt().toFloat()) v.roundToInt().toString() else "%.1f".format(v)

/** 跨年时显示完整日期，否则只显示 MM-dd（旧实现 `takeLast(5)` 会掉年份）。 */
private fun dateTick(first: String, last: String, date: String): String =
    if (first.take(4) != last.take(4)) date else date.takeLast(5)

private fun nearestIndex(x: Float, count: Int, left: Float, width: Float): Int {
    if (count <= 1) return 0
    val usable = (width - left).coerceAtLeast(1f)
    val ratio = ((x - left) / usable).coerceIn(0f, 1f)
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
): String {
    val first = points.first().value
    val last = points.last().value
    val avg = points.map { it.value.toDouble() }.average()
    val dir = when {
        last > first + 0.05f -> dirRise
        last < first - 0.05f -> dirFall
        else -> dirFlat
    }
    return buildString {
        append("$label 趋势，共 ${points.size} 个数据点")
        if (points.size > 1) append("，从 ${fmtValue(first)}$unit $dir ${fmtValue(last)}$unit")
        append("，均值 ${fmtValue(avg.toFloat())}$unit")
        threshold?.let { th ->
            val over = points.count { it.value > th }
            if (over > 0) append("，其中 $over 次超过阈值 ${fmtValue(th)}$unit")
        }
    }
}
