package com.ashkb.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ashkb.app.ui.theme.Motion
import com.ashkb.app.ui.theme.Overline
import com.ashkb.app.ui.theme.Size
import com.ashkb.app.ui.theme.Spacing

/**
 * 全 App 唯一的卡片容器（取代此前 5 份同名实现）。
 *
 * 层级靠表面色阶而非阴影：普通卡片放 `surfaceContainerLowest`（浅色下为纯白），
 * 在 `surface`（暖米底）上自然浮起，elevation 恒为 0。
 *
 * v1.0.87（批次 13）：新增可点的 [onClick]。[onClickLabel] 是读屏用的动作名——
 * 卡片可点时正文交给读屏当动作提示（与 `NavRow` 的「整行可点，语义在标题」同一约定）。
 * 点击挂在内层 Column 上（Surface 之内）：涟漪被卡片圆角裁掉，动作槽里的按钮照旧吃自己的点击。
 *
 * v1.2.10（UI 2.0 批次 C「换件」）：容器从「纯色阶浮起」改为「色阶 + 1dp hairline 描边」。
 * 为什么：换色后页面底与卡片底的明度差在浅色下只剩 ~4%（`#F3F6F8` vs `#FFFFFF`），
 * 深色下更小；大屏上连续几张卡片会糊成一片。补一道发线后卡片边界重新"读得出来"，
 * 又不必引入阴影（v1.1.6 的横带教训：`Modifier.shadow` 在真机上会在 dock 下方投出渐变灰带）。
 * 描边用 `outlineVariant` 而不是 `primary.copy(alpha)`——批次 A 的裁决是语义色不得承载容器装饰。
 *
 * [eyebrow] 是区块标题上方的眉题（12sp 拉字距小字），可选；给了才占一行。
 */
@Composable
fun SectionCard(
    title: String,
    subtitle: String? = null,
    eyebrow: String? = null,
    action: (@Composable RowScope.() -> Unit)? = null,
    container: Color = MaterialTheme.colorScheme.surfaceContainerLowest,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = container,
        border = BorderStroke(Size.divider, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            Modifier
                .then(
                    if (onClick != null) {
                        Modifier.clickable(onClickLabel = onClickLabel, onClick = onClick)
                    } else {
                        Modifier
                    },
                )
                .padding(Spacing.lg),
        ) {
            if (eyebrow != null) {
                Text(
                    eyebrow,
                    style = Overline,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Spacing.xxs))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    if (subtitle != null) {
                        Spacer(Modifier.height(Spacing.xxs))
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (action != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        content = action,
                    )
                }
            }
            Spacer(Modifier.height(Spacing.md))
            content()
        }
    }
}

/**
 * 空状态统一形态：图标 + 标题 + 一句引导 + 一个动作按钮。
 * 文案里不写导航路径（"到「我的 → 药单管理」添加"这类），直接给按钮。
 *
 * v1.2.10（UI 2.0 批次 C「换件」）：裸图标升级为 **brand-soft 圆形托底**（56dp）。
 * 为什么：空状态是"页面没内容"的场合，一个裸图标在瓷白底上太轻，撑不住版心；
 * 托底后图标有了分量，又不像插画那样需要额外资源。
 * 用 `primaryContainer`（= 设计稿 brand-soft）而不是 `primary.copy(alpha)`——
 * 语义色容器一律实心，禁止半透明叠加（红线 1）。
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Box(
            modifier = Modifier
                .size(Size.emptyIconTile)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,   // 装饰性：标题已承载语义
                modifier = Modifier.size(Size.iconLg),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (body != null) {
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (actionLabel != null && onAction != null) {
            if (body == null) Spacer(Modifier.height(Spacing.xs)) else Spacer(Modifier.height(Spacing.xs))
            FilledTonalButton(onClick = onAction, modifier = Modifier.heightIn(min = Size.touchMin)) {
                Text(actionLabel)
            }
        }
    }
}

/**
 * 加载态统一形态：替代 `Text("统计加载中…")` 与排在屏外的 spinner。
 *
 * v1.2.10（UI 2.0 批次 C「换件」）：由 spinner 改为**骨架屏**（surface-2 圆角条 + 呼吸动画）。
 * 为什么：数据页等待时版式会跳变（先是一个居中的转圈，数据到了再换成整页版式），
 * 骨架屏把"即将出现的版式"先占住，视觉上不抖。
 *
 * API 向后兼容：`label` 仍被接受，但只在 `minHeight` 之外多渲染一行说明文字——
 * 既有调用点（BackupScreen / ReportScreen）无需改动。`bars` 控制骨架条数量。
 *
 * ⚠️ 动画只改 alpha（`Motion.SlowMs` = 400ms 往返）：不做位移、不做缩放——
 * 骨架屏只是"占位"，动得越少越不像加载失败。
 */
@Composable
fun LoadingBlock(
    minHeight: Dp = 120.dp,
    label: String? = null,
    bars: Int = 3,
) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(Motion.SlowMs),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "skeletonAlpha",
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .padding(vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        repeat(bars) { i ->
            // 最后一条短一些：模拟"正文最后一行写不满"的自然形态，比等长条更像真实版式
            val fraction = if (i == bars - 1) 0.62f else 1f
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .height(Size.skeletonBar)
                    .clip(MaterialTheme.shapes.small)
                    .background(
                        MaterialTheme.colorScheme.surfaceContainer
                            .copy(alpha = alpha),
                    ),
            )
        }
        if (label != null) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
