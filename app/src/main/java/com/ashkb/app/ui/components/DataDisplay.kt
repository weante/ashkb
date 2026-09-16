package com.ashkb.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f).width(Spacing.sm))
        Row(
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
