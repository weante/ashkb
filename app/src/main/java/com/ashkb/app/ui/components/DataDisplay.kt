package com.ashkb.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
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
 * ## 宽度规则（v1.1.1 重写：前两版的注释都写错了机理）
 *
 * 结构：`外层 Row[ 主体 Row(weight(1f))[ 标签 , 数值列(weight(1f) + 末端对齐) ] , 胶囊 ]`——
 * **标签与数值一起被包进唯一的权重子项**，外层 Row 里只剩胶囊一个非权重子项。
 *
 * 机理（对照 Compose foundation-layout 1.7.3 `RowColumnMeasurePolicyKt.measure` 字节码）：
 *  ① `Row` 第一遍**只测非权重子项**，按声明顺序，每个拿到的 maxWidth 是**当时的剩余宽**
 *     （`mainAxisMax − fixedSpace`；`fixedSpace` 随已测非权重子项累加，含 arrangement 间距）。
 *     权重子项在这一遍里只被计数、**不测量**；
 *  ② 第二遍才把 `mainAxisMax − fixedSpace` 按权重分给权重子项：`fill = true`（默认）时约束被
 *     收紧成"分给它的那一份"，放不下由它自己折行。
 *
 * 由此得到两条必须遵守的结论：
 *  · **胶囊的固有宽度只在"它前面没有非权重子项"时才被保证。** v1.0.95 写的
 *    「trailing 的保底不靠权重：它是兄弟节点、非权重 → 按序测量时先拿到固有宽度」**是错的**：
 *    数值列排在胶囊**前面**且非权重，长数值在第一遍里就能吃掉全部剩余宽（它拿到的 maxWidth
 *    是"行宽 − 标签宽"），排在它后面的胶囊随后拿到 `maxWidth = 0` → 被压成零宽。
 *    这正是 v1.0.90 修好、v1.0.95 为"短值靠右"放开权重后**重新引入**的那次回归
 *    （急救卡 `EmergencyScreen.kt` 的「药名 + 剂量说明 + 免疫抑制胶囊」那一行就是当初立案的行）。
 *  · 所以本版改为：让胶囊成为外层 Row 的**唯一非权重子项**——它在第一遍拿到的 maxWidth 是
 *    整行宽，固有宽度**无条件**成立，与标签、数值多长都无关（标签再长也只影响主体那一份）。
 *
 * 短值靠右怎么同时成立：数值列仍是 `weight(1f)`（被钉在"主体宽 − 标签宽 − 16dp"上），
 * 但列内改用 `Arrangement.spacedBy(Spacing.xs, Alignment.End)` **末端对齐**——短值被推到列的
 * 右边缘（无胶囊时就是行的右边缘），长值填满整列并在列内折行（填满时首末端对齐等价）。
 * 这比 v1.0.95 的 `Arrangement.SpaceBetween` + 非权重数值列多一条保证：**长值不会再去挤别人**。
 * 间距：标签与数值列之间恒定 [Spacing.lg]，主体与胶囊之间恒定 [Spacing.sm]（外层 `spacedBy`）。
 *
 * 已知代价（v1.1.1 复算，三种情形的最窄宽度见批次说明）：
 * 320dp 窄屏 + 2.0× 字号下，「长标签 + 长数值 + 胶囊」三者不可能同时满足。本版的取舍是
 * **胶囊优先**（它是安全标记，压成 0 等于从急救卡上消失），代价是极端情况下数值列可能被很长的
 * 标签吃到 0 宽。v1.0.95/96 的取舍相反（牺牲胶囊），而实测那正是维护者截图里看到的坏界面。
 * 正常字号（1.0×）下三种情形都有正宽。
 *
 * 与 [WeightedTrailingRow] 的关系：那套规则的表述（**权重正文 + 固定间距 + 非权重尾部**）在本行
 * 同样成立，但它的 `leading` 是排在最前的非权重子项，只适合"短且有界"的前缀（大号百分比数字）；
 * 本行的标签是用户自填药名（长度无界），直接套用会把"标签吃掉胶囊"的同一个缺口重新打开。
 * 故宽度规则保持一处表述、两处实现，两处都写明为什么。
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
        // 固定间距（主体↔胶囊）：权重分配前先扣掉 arrangement 间距，故这里给的就是净间距。
        // 单子项（无胶囊）时 spacedBy 不产生任何间距。
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        // 主体 = 标签 + 数值列，整块作为一个权重子项：见 KDoc 机制②——它拿到的宽是
        // "行宽 − 胶囊固有宽 − 间距"，因此**它内部怎么排都不可能挤到胶囊**。
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                // v1.0.95：标签列最小宽度——短标签也占满，长值起点因此固定（见 KDoc）。
                // v1.0.96：**带 trailing 的行不加这个下限**——紧急卡「当前用药」的标签是用户自填
                // 药名（可能很长），右边还要放「免疫抑制」胶囊；再吃掉 128dp 后数值列会更窄。
                // 该行改为按标签固有宽度，把宽度让给真正需要折行的剂量说明；代价是该行数值起点
                // 不参与全局对齐（这一行本来就是"药名 + 剂量 + 风险标记"三元组，语义与其它行不同）。
                modifier = if (trailing != null) Modifier else Modifier.widthIn(min = Size.labelColumnMin),
            )
            Row(
                // 权重（fill = true）：约束被收紧成"主体宽 − 标签宽 − 间距"，数值在其中折行。
                // 不加权重则长值会吃掉整个主体宽、把标签挤到 0（v1.0.95 的写法）。
                modifier = Modifier.weight(1f).padding(start = Spacing.lg),
                verticalAlignment = Alignment.CenterVertically,
                // 末端对齐（v1.1.1）：短值贴列右缘 = 无胶囊时的行右缘；长值填满整列、原位折行。
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs, Alignment.End),
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
        }
        // 胶囊：外层 Row 里唯一的非权重子项 → 第一遍测量就拿整行宽，固有宽度无条件成立。
        if (trailing != null) trailing()
    }
}
