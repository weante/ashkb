package com.ashkb.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp

import com.ashkb.app.R
import com.ashkb.app.ui.theme.Size
import com.ashkb.app.ui.theme.Spacing
import com.ashkb.app.ui.theme.StatusTone
import com.ashkb.app.ui.theme.accent
import com.ashkb.app.ui.theme.colors

/**
 * 胶囊标签。取代此前 3 份 `Card` 当胶囊的 badge 实现。
 * 状态一律三重编码：底色（tone）+ 图标 + 文字。
 *
 * v1.0.82（批次 8）：高度由**固定**改为**下限**（`height` → `heightIn(min = Size.chipHeight)`）。
 * 原实现把高度钉死在 28dp，而 labelMedium 的行高是 18sp——提示类文案一旦换到第二行
 * （化验页「同一项目有多个单位…」就是），第二行直接被裁掉。改下限后：单行 chip 高度分毫不变
 * （内容 18dp + 上下 [Spacing.xs] 内边距 = 26dp，仍由 28dp 下限决定），多行则按内容长高。
 */
@Composable
fun StatusChip(
    text: String,
    tone: StatusTone = StatusTone.Neutral,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
) {
    val (bg, fg) = tone.colors()
    Surface(
        shape = CircleShape,
        color = bg,
        modifier = Modifier
            .heightIn(min = Size.chipHeight)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Row(
            // 纵向内边距只在换行时才看得见（单行仍由上面的 28dp 下限托底），避免两行字顶到胶囊边
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(Size.iconSm), tint = fg)
            }
            Text(text, style = MaterialTheme.typography.labelMedium, color = fg)
        }
    }
}

/**
 * 「数据行」统一排版：`前缀数字 + 弹性正文列 + 固定间距 + 尾部徽标`。
 *
 * v1.0.90（批次 15）：本模块**第 5 次**出现「某个 UI 元素被挤成零宽」之后，把这套已验证的宽度
 * 规则收成一个组件。四次的形状完全相同：一行里有一个**固有宽度可以很长的元素**（正文/名称/按钮）
 * 排在**末尾元素**（胶囊/徽标）前面，而中间用 `Spacer(weight(1f))` 撑开——达标徽标、补剂卡按钮、
 * 补剂卡名称、化验提示条都是这一个形状的变体。
 *
 * 机制（对照 Compose foundation-layout 1.7.3 的 `RowColumnMeasurePolicyKt.measure` 字节码）：
 * ① `Row` 先测量**非权重**子项，每个拿到"当前可用宽 − 间距"，然后才把剩余空间分给权重子项；
 * ② 权重子项拿到的宽**上限由 `fill` 决定**——`fill = true`（默认）时它的约束被收紧成
 *    "分给它的那一份"（`createRowConstraints(fill=true)` → 定宽）；`fill = false` 时约束仍是
 *    **父级传来的整行宽**，于是"固有宽度很长的正文"照样能吃掉整行、排在它后面的非权重子项
 *    maxWidth 掉到 0 → 字形竖排后被裁。
 *
 * 因此这里**必须用 `weight(1f)`（即 fill = true）**：正文列被钉在"整行宽 − 前缀 − 间距 − 尾部宽"
 * 上，放不下时由它折行，尾部元素恒拿得到自己的固有宽度。
 *
 * ⚠️ 反面教训（本模块第 5 次同形缺陷的成因）：v1.0.82 给报表页那一行加的是
 * `weight(1f, fill = false)`，**那一半是无效的**——`fill = false` 不收紧约束，正文照样吃满整行，
 * 真正救回徽标的是同一批次把 `Spacer(weight(1f))` 换成固定间距。把无效的那半当成"已验证写法"
 * 抄到别处（本轮审计的建议就是如此）会原样复发，故此处按字节码结论改用 `fill = true`。
 *
 * @param leading 前缀（通常是大号百分比数字），按固有宽度测量、不参与权重
 * @param trailing 尾部元素（胶囊/徽标），**保底拿到自己的固有宽度**
 * @param content 弹性正文列：被钉在剩余宽上，放不下时由它折行
 * @param gap leading 与 content 之间的固定间距（默认 [Spacing.lg]，大号数字与正文之间的呼吸）
 */
@Composable
fun WeightedTrailingRow(
    modifier: Modifier = Modifier,
    gap: Dp = Spacing.lg,
    leading: @Composable () -> Unit,
    content: @Composable () -> Unit,
    trailing: @Composable () -> Unit,
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        leading()
        Spacer(Modifier.width(gap))
        // weight(1f)（fill = true）：正文列的约束被收紧成"分给它的那一份"——见上方 KDoc，
        // 用 fill = false 时约束仍是整行宽，正文会吃满整行把尾部元素挤成零宽。
        Box(Modifier.weight(1f)) { content() }
        // 固定间距 + 徽标：见上方 KDoc（这里**不能**用 weight 撑开）
        Spacer(Modifier.padding(start = Spacing.sm))
        trailing()
    }
}

/**
 * 告警横幅：今日页告警必须成为视觉主角。
 * 左侧 4dp 色条 + 大图标 + 标题/正文 + 可选动作与关闭。
 * 紧急卡等"反向设计"场景用 `titleStyle = titleLarge` 把标题放大。
 */
@Composable
fun AlertBanner(
    tone: StatusTone,
    icon: ImageVector,
    title: String,
    body: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
    visible: Boolean = true,
    titleStyle: TextStyle = MaterialTheme.typography.titleSmall,
) {
    val (bg, fg) = tone.colors()
    val accent = tone.accent()
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = bg,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.height(IntrinsicSize.Min)) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .width(Size.statusBar)
                        .background(accent),
                )
                Row(
                    modifier = Modifier
                        .padding(Spacing.lg)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(Size.iconLg),
                        tint = accent,
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        Text(title, style = titleStyle, color = fg)
                        if (body != null) {
                            Text(body, style = MaterialTheme.typography.bodyMedium, color = fg)
                        }
                        if (actionLabel != null && onAction != null) {
                            TextButton(
                                onClick = onAction,
                                contentPadding = PaddingValues(horizontal = Spacing.sm),
                            ) {
                                Text(actionLabel, color = accent)
                                Icon(
                                    Icons.Rounded.ChevronRight,
                                    contentDescription = null,
                                    modifier = Modifier.size(Size.iconSm),
                                    tint = accent,
                                )
                            }
                        }
                    }
                    if (onDismiss != null) {
                        IconButton(onClick = onDismiss, modifier = Modifier.size(Size.touchMin)) {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.common_close_hint),
                                modifier = Modifier.size(Size.iconSm),
                                tint = fg,
                            )
                        }
                    }
                }
            }
        }
    }
}
