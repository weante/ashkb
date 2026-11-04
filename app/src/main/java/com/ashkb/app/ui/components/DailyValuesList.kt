package com.ashkb.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ashkb.app.R
import com.ashkb.app.ui.theme.Spacing
import java.util.Locale

/**
 * 「逐日数值」文本列表（V-3，外部 UI 审查）。
 *
 * 缺陷形态：完整图表只有**拖动手势**能读到单个点——读屏用户听不到，手指粗或抖的人也很难
 * 停在某一天。图上方那行 `ReadoutRow` 只给「当前 / 较首次 / 均值 / 超阈值次数」，不含逐日值。
 * 审查建议增加一个可展开的逐日列表，与图表**同数据源、同单位**，比用 semantics 模拟拖动
 * 既简单又好测。
 *
 * 为什么单独成一个文件、而不塞进 [TrendChart]：`TrendChart` 的 `LongMethod` /
 * `CyclomaticComplexMethod` 在 `config/detekt/baseline.xml` 里是**按完整签名**登记的，
 * 一旦给它加形参，基线失配、旧问题重新冒出来（批次 3 的 `OverviewPage` 就踩过）。
 * 所以这里坚持「新组件 + 调用点各取所需」，`TrendChart` 签名一个字不动。
 *
 * 为什么折叠而不是常显：一次化验能带出几十天，常显会把图挤到屏幕外；默认收起、
 * 按钮上直接写明有多少天，用户不必展开才知道值不值得展开。
 *
 * @param points 与图表同一份数据（升序，见 [TrendPoint]）。
 * @param unit 与图表同一个单位串（可为 `""`、`"kg"`、`"/10"`、`" mmHg"`）。
 */
@Composable
fun DailyValuesList(
    points: List<TrendPoint>,
    unit: String,
    modifier: Modifier = Modifier,
) {
    if (points.isEmpty()) return
    // 折叠状态要跨旋转保留：展开到一半转个屏又收起来，等于白点。
    var expanded by rememberSaveable { mutableStateOf(false) }
    val label = if (expanded) {
        stringResource(R.string.trend_daily_hide, points.size)
    } else {
        stringResource(R.string.trend_daily_values, points.size)
    }

    Column(modifier.fillMaxWidth()) {
        TextButton(onClick = { expanded = !expanded }, modifier = Modifier.heightIn(min = 48.dp)) {
            Icon(
                imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = null,
            )
            Text(label, modifier = Modifier.padding(start = Spacing.xs))
        }
        if (expanded) {
            // 列表自身限高并滚动：外面那层是 ModalBottomSheet 的普通 Column（不可滚），
            // 三十天以上的数据不设上限会把整张 sheet 顶出屏幕，连收起按钮都够不到。
            Column(Modifier.heightIn(max = LIST_MAX_HEIGHT).verticalScroll(rememberScrollState())) {
                points.forEachIndexed { index, point ->
                    if (index > 0) HorizontalDivider(Modifier.padding(vertical = Spacing.xxs))
                    DailyValueRow(date = point.date, value = point.value, unit = unit)
                }
            }
        }
    }
}

/** 单日一行：日期左、数值右。整行合成一个语义节点，读屏一次读完「日期 + 数值 + 单位」。 */
@Composable
private fun DailyValueRow(date: String, value: Float, unit: String) {
    val text = dailyValueText(value, unit)
    val fullDate = date
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .semantics(mergeDescendants = true) { contentDescription = "$fullDate $text" },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = dailyDateText(date),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** 列表最大高度：约五行半，露出的半行是「下面还有」的视觉提示。 */
private val LIST_MAX_HEIGHT = 220.dp

/**
 * 日期显示：跨年与否都由调用方决定不了，这里统一 `MM-dd`（完整年份在语义描述里）。
 * 与 [TrendChart] 的坐标轴刻度同一口径，避免同一张图上出现两种日期写法。
 */
internal fun dailyDateText(date: String): String = if (date.length >= 10) date.takeLast(5) else date

/**
 * 数值 + 单位。`/10` 这类「后缀式」单位直接贴上去（`7/10`），`kg` 这类词式单位留一个空格
 * （`65.2 kg`）——`" 分"`、`" mmHg"` 那种自带前导空格的旧写法在这里统一去掉前导空格再拼，
 * 避免出现双空格（批次 3 已把 `" 分"` 从字符串表里删掉）。
 */
internal fun dailyValueText(value: Float, unit: String): String {
    val number = if (value == value.toInt().toFloat()) {
        value.toInt().toString()
    } else {
        String.format(Locale.US, "%.1f", value)
    }
    val tail = unit.trim()
    return when {
        tail.isEmpty() -> number
        tail.startsWith("/") -> number + tail
        else -> "$number $tail"
    }
}
