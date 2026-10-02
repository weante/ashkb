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
 * v1.0.94：**标签列定宽（[Size.labelColumnMin]）+ 固定间距（[Spacing.lg]），数值列吃满剩余宽并折行。**
 *
 * 为什么还要改（维护者真机反馈 v1.0.93 之后：「周月报还是挤在一起了，观感上不够直观」）：
 * v1.0.90 与 v1.0.93 都在调"标签与数值怎么分剩余空间"，可**数值的起点始终跟着标签长短走**——
 * 「症状」（2 字 ≈ 31dp）后面的数值紧贴标签，「记录内完成度」（6 字 ≈ 92dp）后面的数值被推到 90dp
 * 开外。同一张卡里每条数值各起一行 x，这种参差才是"挤在一起、不直观"的根源，而不是间距本身：
 * 维护者说的「给一个固定的间隔」，实质是**固定的数值起点**。
 *
 * 改法（三步，缺一不可）：
 *  · 标签列挂 `widthIn(min = Size.labelColumnMin)`：短标签也占满 128dp，于是 [Spacing.lg] 之后的
 *    数值起点对全 App 的键值行是同一个 x；本应用最长的静态标签「骶髂关节影像分期」也在 128dp
 *    之内（估算见 [Size.labelColumnMin]），故它同样对齐；更长的标签（用户自填药名、2.0× 字号）
 *    会把数值起点往右推——预期降级；
 *  · `Arrangement.SpaceBetween` 删掉，数值列改为**无条件** `weight(1f)`：v1.0.93 只在有 trailing
 *    时才加权重、无 trailing 时靠 SpaceBetween 把短数值推到**右边缘**，于是同一张卡里"短数值贴右、
 *    长数值贴标签"两种对齐混用，而且是按标签长短随机二选一。删掉 SpaceBetween 后数值一律左对齐
 *    到固定起点，放不下就折行（多行显示）；
 *  · 间距落在数值列的 `padding(start = Spacing.lg)`：标签与数值之间恒定 16dp，与标签宽窄无关。
 *
 * 为什么 trailing 仍然安全（v1.0.90 修过「免疫抑制」胶囊被压成 2.5dp，不能复发）：
 * `Row` 先测量**非权重**子项（标签、trailing，各拿"当前可用宽 − 间距"），再把剩余分给权重子项；
 * `weight(1f)` 默认 `fill = true`，数值列的约束被**收紧成"分给它的那一份"**，因此它吃不下时自己
 * 折行，绝不会去挤 trailing 的固有宽度（`fill = false` 时约束仍是整行宽，等于没改——v1.0.82 踩过）。
 * 急救卡「当前用药」（`label = 药名, value = 剂量说明, trailing = 胶囊`）因此照旧拿到完整胶囊宽。
 * 已知代价：320dp 窄屏 + trailing 时，标签列下限(128) 与胶囊(≈90dp) 会吃掉大半行，数值列只剩
 * 约 40dp 而多折几行；这是"标签/胶囊本身就长"的既有降级，不是本次引入的回归。
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
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            // v1.0.95：标签列最小宽度——短标签也占满，长值起点因此固定（见函数 KDoc）。
            // v1.0.96：**带 trailing 的行不加这个下限**——紧急卡「当前用药」的标签是用户自填药名
            // （可能很长），右边还要放「免疫抑制」胶囊；再吃掉 128dp 后数值列只剩约 60dp，
            // 剂量说明被挤成 6 行（维护者截图可见）。trailing 行改为按标签固有宽度，
            // 把宽度让给真正需要折行的剂量说明；代价是该行数值起点不参与全局对齐，
            // 但这一行本来就与其它行语义不同（是"药名 + 剂量 + 风险标记"三元组）。
            modifier = if (trailing != null) Modifier else Modifier.widthIn(min = Size.labelColumnMin),
        )
        Row(
            // v1.0.95：**不加权重**——让"文本量"自己决定排版，两种诉求才能同时成立：
            //  · 短值（健康档案「曾庆城 / 阳性 / 未填」）：只占自身宽度 → 外层 SpaceBetween 把它
            //    推到**右边缘**，留白落在中间（维护者要的"协调"观感）；
            //  · 长值（周月报「100% (完成 2 · 部分 0 · 跳过 0, 共 2 条记录)」）：占满
            //    "行宽 − 标签 − 间距" → 起点固定在**同一 x**（标签列 `Size.labelColumnMin` +
            //    `Spacing.lg`）并在**列内折行**（多行显示）。
            // v1.0.90 曾无条件加 `weight(1f)` 救 trailing（成功），但权重子项吃满剩余宽 → 短值
            // 也被钉在标签右侧；v1.0.93 修回靠右、v1.0.94 又改成一律左对齐 → 维护者反馈健康档案
            // "不协调"。三种写法都不对，因为**同一个规则套了两种文本量**。
            // trailing 的保底不靠权重：它是**兄弟节点**、非权重 → 按序测量时先拿到固有宽度。
            modifier = Modifier.padding(start = Spacing.lg),
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
