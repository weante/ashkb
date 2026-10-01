package com.ashkb.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ashkb.app.R
import com.ashkb.app.ui.theme.Clinical
import com.ashkb.app.ui.theme.Size
import com.ashkb.app.ui.theme.Spacing
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/**
 * 小多图单元（趋势页方案 C）。
 *
 * 与 [TrendChart] 的分工：本组件只回答「**形状**如何、跟别的指标是否同向波动」，
 * 因此刻意做小、去掉网格、刻度与拖动读数；要看**具体数值**请点开（[onClick] → 大图）。
 * 保留的能力：末值直接标在右上角（不点也能读到最后一次的值）、阈值虚线、逐点标记。
 *
 * ⚠️ **横轴必须由「共享窗口」决定，不能各自按自己的数据范围铺开**——否则每张图都是
 * 「自己的 0%–100%」，同一时间点在各图里落在不同位置，小多图最关键的价值
 * （上下对齐着看谁先动）就没了。故这里用 [xFraction] 按 (date − 窗口起) / 窗口长度 定位，
 * 窗口由 [sharedWindow] 在**全部序列日期的并集**上算一次、各单元共用。
 *
 * @param caveat 卡片底部补注（如「另有 N 条因单位不同未纳入」）。**不静默**是硬要求：
 *   被排除的数据必须说出来，否则「图上看不见」会被误读成「没测过」。
 * @param emptyText 无数据时的占位文案。**必须由调用方给对**——v1.0.55 之前这里写死成
 *   「暂无化验数据」，于是收缩压 / 心率这些**非化验**指标的空格子也在说「暂无化验数据」（用户实测发现）。
 */
@Composable
fun SmallTrendChart(
    title: String,
    points: List<TrendPoint>,
    unit: String,
    threshold: Float?,
    fromDate: String,
    toDate: String,
    emptyText: String,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
    caveat: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val cs = MaterialTheme.colorScheme
    val warning = Clinical.colors.warning
    val density = LocalDensity.current
    val titleStyle = MaterialTheme.typography.labelMedium.copy(color = cs.onSurfaceVariant)
    val axisStyle = MaterialTheme.typography.labelSmall.copy(color = cs.onSurfaceVariant)
    val valueStyle = MaterialTheme.typography.labelLarge.copy(color = accent)

    val dotPx = with(density) { Size.chartDot.toPx() }
    val dash = remember { PathEffect.dashPathEffect(floatArrayOf(8f, 6f)) }
    val stroke = remember(density) {
        Stroke(width = with(density) { Size.chartStroke.toPx() }, cap = StrokeCap.Round, join = StrokeJoin.Round)
    }
    val fillBrush = remember(accent) {
        Brush.verticalGradient(listOf(accent.copy(alpha = 0.18f), Color.Transparent))
    }

    val lastLabel = remember(points, unit) {
        points.lastOrNull()?.let { fmtMini(it.value) + unit }
    }
    // 文案在组合期取（stringResource 不能在 remember 的计算块里调用）
    val a11y = if (points.isEmpty()) {
        "$title $emptyText"
    } else {
        stringResource(R.string.report_small_chart_a11y, title, lastLabel ?: "", points.size)
    }

    val clickModifier = if (onClick != null) {
        Modifier.clickable(onClickLabel = stringResource(R.string.report_expand_chart), onClick = onClick)
    } else Modifier

    Column(
        modifier = modifier
            .then(clickModifier)
            .semantics { contentDescription = a11y },
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(
                text = title,
                style = titleStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (lastLabel != null) {
                Text(lastLabel, style = valueStyle, maxLines = 1)
            }
        }

        if (points.isEmpty()) {
            Box(
                Modifier.fillMaxWidth().height(Size.chartMiniHeight),
                contentAlignment = Alignment.Center,
            ) {
                Text(emptyText, style = axisStyle)
            }
            return@Column
        }

        // 量程：与共享横轴无关（各指标量纲不同），故每单元独立。maxIntervals=3 → 只取端点两个标签
        val scale = remember(points, threshold) {
            val base = points.map { it.value }
            niceScale(if (threshold != null) base + threshold else base, null, 3)
        }
        val vMin = scale.first
        val vMax = scale.second
        val range = (vMax - vMin).coerceAtLeast(MIN_RANGE)

        // v1.0.84（批次 9）：坐标换算整个搬到组合期。原先在 Canvas 的 draw lambda 里做——
        // 趋势页有 10+ 个 mini cell 同时重绘，于是**每帧**都要：对每个点做 3 次 LocalDate.parse、
        // 新建一个 List（mapNotNull）、new 2 个 Path。现在只在数据 / 窗口 / 量程变化时算一次，
        // draw 里只剩「相对坐标 × 画布尺寸」的乘加。
        val miniPoints = remember(points, fromDate, toDate, vMin, vMax) {
            miniPointsOf(points, fromDate, toDate, vMin, vMax)
        }
        // Path 复用（照搬 [TrendChart] 的 linePath / fillPath）：draw 里 reset() 重填，不再每帧新建
        val linePath = remember { Path() }
        val fillPath = remember { Path() }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // 左侧刻度槽：只标「上界 / 下界」，让量程可见（小多图不标全刻度）
            Column(
                Modifier
                    .width(MINI_AXIS_WIDTH)
                    .height(Size.chartMiniHeight)
                    .padding(end = Spacing.xs),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End,
            ) {
                Text(fmtMini(vMax), style = axisStyle, maxLines = 1)
                Text(fmtMini(vMin), style = axisStyle, maxLines = 1)
            }

            Canvas(Modifier.weight(1f).height(Size.chartMiniHeight)) {
                val w = size.width
                val h = size.height
                val padY = Spacing.xs.toPx()
                val plotH = (h - padY * 2f).coerceAtLeast(1f)

                // 阈值虚线（超出量程则不画，避免贴边误导）
                if (threshold != null && threshold in vMin..vMax) {
                    val ty = padY + (1f - (threshold - vMin) / range) * plotH
                    drawLine(
                        color = warning,
                        start = Offset(0f, ty),
                        end = Offset(w, ty),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = dash,
                    )
                }

                // 折线 + 渐变填充：点位在组合期已算好（日期只解析一次），
                // 这里只把「窗口内相对坐标」映射到画布尺寸；Path 复用，不掉点也不改变落点。
                if (miniPoints.size >= 2) {
                    linePath.reset()
                    fillPath.reset()
                    val first = miniPoints.first()
                    val fx = first.xFraction * w
                    val fy = padY + first.yFraction * plotH
                    linePath.moveTo(fx, fy)
                    fillPath.moveTo(fx, h)
                    fillPath.lineTo(fx, fy)
                    for (i in 1 until miniPoints.size) {
                        val p = miniPoints[i]
                        val x = p.xFraction * w
                        val y = padY + p.yFraction * plotH
                        linePath.lineTo(x, y)
                        fillPath.lineTo(x, y)
                    }
                    fillPath.lineTo(miniPoints.last().xFraction * w, h)
                    fillPath.close()
                    drawPath(fillPath, brush = fillBrush)
                    drawPath(linePath, color = accent, style = stroke)
                }
                miniPoints.forEach { p ->
                    drawCircle(
                        color = accent,
                        radius = dotPx,
                        center = Offset(p.xFraction * w, padY + p.yFraction * plotH),
                    )
                }
            }
        }

        caveat?.let { Text(it, style = axisStyle, maxLines = 2) }
    }
}

/** 小多图左侧刻度槽宽度（容纳 4 位数字 + 1 位小数）。 */
private val MINI_AXIS_WIDTH = 40.dp

/** 量程下限（与 [niceScale] 同口径）：`vMax == vMin` 时避免除零。 */
private const val MIN_RANGE = 1e-3f

private fun fmtMini(v: Float): String =
    if (v == v.roundToInt().toFloat()) v.roundToInt().toString() else "%.1f".format(v)

/**
 * 一个数据点在画布上的**相对**位置，两轴都已归一化到 0..1（y 轴 0 在顶部）。
 *
 * v1.0.84（批次 9）：存在的意义是让「日期解析 + 量程归一化」能在**组合期**做完一次，
 * draw lambda 里只留乘加——见 [miniPointsOf]。
 */
internal data class MiniChartPoint(val xFraction: Float, val yFraction: Float)

/**
 * 把数据点换算成相对坐标（组合期调用一次，[SmallTrendChart] 用 `remember` 缓存）。
 *
 * 口径与改动前的逐帧实现**逐点等价**（有单测比对）：
 * - 横轴走**共享窗口** [xFraction]；(date − 窗口起) / 窗口长度，窗口外夹到边界；
 * - 起点 / 终点日期解析不了 → 整条序列都拿不到横坐标，返回空表（原实现是每个点都得到 null）；
 * - 单点日期解析不了 → **跳过该点**，不让一行脏数据把整图搞没；
 * - 纵轴 = 1 − (v − vMin) / range，与原来的 `yOf` 同一表达式、同一运算顺序。
 */
internal fun miniPointsOf(
    points: List<TrendPoint>,
    fromDate: String,
    toDate: String,
    vMin: Float,
    vMax: Float,
): List<MiniChartPoint> {
    val from = parseIsoDate(fromDate) ?: return emptyList()
    val to = parseIsoDate(toDate) ?: return emptyList()
    val range = (vMax - vMin).coerceAtLeast(MIN_RANGE)
    return points.mapNotNull { p ->
        val f = xFraction(parseIsoDate(p.date), from, to) ?: return@mapNotNull null
        MiniChartPoint(f, 1f - (p.value - vMin) / range)
    }
}

/** ISO 日期解析：解析不了返回 null（调用方负责「跳过该点」而不是画到假位置）。 */
internal fun parseIsoDate(text: String): LocalDate? = runCatching { LocalDate.parse(text) }.getOrNull()

/**
 * 全部序列日期的并集 → [最早, 最晚]。日期解析不了的忽略；整体无可用日期返回 `null`。
 *
 * 这就是小多图共用的「时间轴窗口」。用数据并集而非「7/30/90 天窗口」，
 * 好处是横向不留大片空白、且天然包含只有化验（平时不打卡）的时段。
 */
internal fun sharedWindow(seriesDates: List<List<String>>): Pair<String, String>? {
    val parsed = seriesDates.flatten().mapNotNull { parseIsoDate(it) }
    if (parsed.isEmpty()) return null
    return parsed.min().toString() to parsed.max().toString()
}

/**
 * 日期在共享窗口内的相对横坐标（0..1）。
 *
 * - 单日窗口（`from == to`）返回 **0.5**（居中）——避免除零；
 * - 任一日解析不了返回 `null`（调用方跳过该点，而不是画到 0 或 1 这种「假位置」上）。
 */
internal fun xFraction(date: String, fromDate: String, toDate: String): Float? {
    val f = parseIsoDate(fromDate) ?: return null
    val t = parseIsoDate(toDate) ?: return null
    return xFraction(parseIsoDate(date), f, t)
}

/**
 * 已解析日期版重载（v1.0.84，批次 9）。
 *
 * 原实现每次调用都要把 `fromDate` / `toDate` 各解析一遍——Chart 里按点循环就是 **3N 次解析**。
 * 解析结果由调用方复用（组合期一次），语义与字符串版完全一致。
 */
internal fun xFraction(date: LocalDate?, from: LocalDate, to: LocalDate): Float? {
    if (date == null) return null
    val span = ChronoUnit.DAYS.between(from, to)
    if (span <= 0L) return 0.5f
    return (ChronoUnit.DAYS.between(from, date).toFloat() / span.toFloat()).coerceIn(0f, 1f)
}
