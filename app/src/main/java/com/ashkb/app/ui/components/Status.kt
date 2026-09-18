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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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

import com.ashkb.app.R
import com.ashkb.app.ui.theme.Size
import com.ashkb.app.ui.theme.Spacing
import com.ashkb.app.ui.theme.StatusTone
import com.ashkb.app.ui.theme.accent
import com.ashkb.app.ui.theme.colors

/**
 * 胶囊标签。取代此前 3 份 `Card` 当胶囊的 badge 实现。
 * 状态一律三重编码：底色（tone）+ 图标 + 文字。
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
            .height(Size.chipHeight)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.sm),
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
