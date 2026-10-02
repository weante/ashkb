package com.ashkb.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.TrendingDown
import androidx.compose.material.icons.rounded.TrendingFlat
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import com.ashkb.app.ui.theme.DataMedium
import com.ashkb.app.ui.theme.Size
import com.ashkb.app.ui.theme.Spacing
import com.ashkb.app.ui.theme.StatusTone
import com.ashkb.app.ui.theme.accent

enum class Trend { Up, Down, Flat, None }

private val Trend.icon: ImageVector?
    get() = when (this) {
        Trend.Up -> Icons.Rounded.TrendingUp
        Trend.Down -> Icons.Rounded.TrendingDown
        Trend.Flat -> Icons.Rounded.TrendingFlat
        Trend.None -> null
    }

/**
 * 指标格：数值 + 单位 + 语义色调 + 趋势箭头。
 * 数值一律等宽（`DataXxx`），避免刷新时字宽跳动。
 */
@Composable
fun StatTile(
    label: String,
    value: String,
    unit: String? = null,
    tone: StatusTone = StatusTone.Neutral,
    trend: Trend = Trend.None,
    onClick: (() -> Unit)? = null,
) {
    val valueColor = if (tone == StatusTone.Neutral) MaterialTheme.colorScheme.onSurface else tone.accent()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = Size.rowMinHeight)
            .padding(vertical = Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Text(value, style = DataMedium, color = valueColor, textAlign = TextAlign.Start)
            if (unit != null) {
                Text(
                    unit,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = Spacing.xs),
                )
            }
            trend.icon?.let {
                Icon(
                    imageVector = it,
                    contentDescription = null,   // 装饰性：数值本身已承载信息
                    modifier = Modifier
                        .padding(bottom = Spacing.xs)
                        .size(Size.iconSm),
                    tint = valueColor,
                )
            }
        }
    }
}

/**
 * 键值行：取代此前 6 个同义变体（`ProfileRow` / `CheckRow` / `LedgerRow` / 化验行 …）。
 * 数值可带语义色调与状态图标（三重编码）。
 *
 * v1.0.90（批次 15）：**数值列参与权重分配**（`weight(1f)`），不再让「标签 + 弹性 Spacer +
 * 数值 + trailing」四者中只有 Spacer 有权重。
 *
 * 为什么必须改（对照 Compose foundation-layout 1.7.3 `RowColumnMeasurePolicyKt.measure` 字节码）：
 * `Row` 先测量**非权重**子项（各拿"当前可用宽 − 间距"），剩下的才分给权重子项；而权重子项拿到的
 * 宽上限由 `fill` 决定——`fill = true`（默认）时约束被收紧成"分给它的那一份"，`fill = false` 时
 * 仍是**整行宽**。过去只有 Spacer 有权重，于是标签与数值都按**固有宽度**先测：数值一长就吃掉整行，
 * 排在最后的 trailing 拿到 maxWidth ≈ 0。急救页「当前用药」的免疫抑制标记
 * （`KeyValueRow(label = 药名, value = 剂量说明, trailing = 胶囊)`）就是受害者——「免疫抑制」
 * 被压成 2.5dp 后逐字竖排。
 *
 * 改法（两步）：
 *  · 数值列给 `weight(1f)`（**不能**用 `fill = false`，那样约束仍是整行宽、等于没改）——
 *    它被钉在"整行宽 − 标签宽 − 间距"上，放不下时由它折行；
 *  · 中间的 `Spacer(weight(1f))` 删掉，改在 Row 上挂 `Arrangement.SpaceBetween`——
 *    权重 Spacer 正是把 trailing 挤成零宽的那一步（它与 trailing 抢同一份剩余，且排在前面）。
 *    多余空间由 SpaceBetween 落在"标签↔数值↔trailing"之间：trailing 因此恒在行尾，
 *    且**永远拿得到自己的固有宽度**。
 *
 * 无 trailing 的调用点行为不变：数值列照旧拿到"可用宽 − 标签宽 − 间距"，短数值显示完整；
 * 唯一差别是标签与数值之间不再有一个只会撑宽的 Spacer（它原本就是靠权重吃掉剩余、不画任何东西）。
 */
@Composable
fun KeyValueRow(
    label: String,
    value: String,
    valueTone: StatusTone = StatusTone.Neutral,
    valueIcon: ImageVector? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val valueColor = if (valueTone == StatusTone.Neutral) {
        MaterialTheme.colorScheme.onSurface
    } else {
        valueTone.accent()
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Size.rowMinHeight)
            .padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        // v1.0.90（批次 15）：SpaceBetween 取代原来的 `Spacer(weight(1f))`——见函数 KDoc
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            // v1.0.93：**只有存在 trailing 时**才给数值列权重。
            // v1.0.90 无条件加 `weight(1f)` 修好了"trailing 被压成零宽"，却把**短数值**也钉在了
            // 标签右侧（权重子项吃满剩余宽）——「体重 71.2 kg」「发作 0 次」这类原本靠中间弹性
            // Spacer 推到**右边缘**，改后紧贴标签，维护者真机截图反馈「全挤在一起了」。
            // 无 trailing 时不加权重：`Arrangement.SpaceBetween` 会把数值推到右边缘（原排版），
            // 长数值仍按"可用宽 − 标签宽 − 间距"折行，行为与 v1.0.90 之前一致。
            // 有 trailing 时加权重（且必须是默认的 `fill = true`——`fill = false` 不收紧约束，
            // 数值照样吃满整行、胶囊仍会被压）：胶囊因此永远拿得到自己的固有宽度。
            modifier = if (trailing != null) {
                Modifier.weight(1f).padding(start = Spacing.sm)
            } else {
                Modifier.padding(start = Spacing.sm)
            },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            if (valueIcon != null) {
                Icon(
                    imageVector = valueIcon,
                    contentDescription = null,   // 装饰性：value 文本已承载语义
                    modifier = Modifier.size(Size.iconSm),
                    tint = valueColor,
                )
            }
            Text(value, style = MaterialTheme.typography.bodyMedium, color = valueColor)
        }
        if (trailing != null) trailing()
    }
}
