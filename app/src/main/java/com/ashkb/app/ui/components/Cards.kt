package com.ashkb.app.ui.components

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ashkb.app.ui.theme.Size
import com.ashkb.app.ui.theme.Spacing

/**
 * 全 App 唯一的卡片容器（取代此前 5 份同名实现）。
 *
 * 层级靠表面色阶而非阴影：普通卡片放 `surfaceContainerLowest`（浅色下为纯白），
 * 在 `surface`（暖米底）上自然浮起，elevation 恒为 0。
 */
@Composable
fun SectionCard(
    title: String,
    subtitle: String? = null,
    action: (@Composable RowScope.() -> Unit)? = null,
    container: Color = MaterialTheme.colorScheme.surfaceContainerLowest,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = container,
    ) {
        Column(Modifier.padding(Spacing.lg)) {
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
        Icon(
            imageVector = icon,
            contentDescription = null,   // 装饰性：标题已承载语义
            modifier = Modifier.size(Size.iconLg * 1.5f),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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

/** 加载态统一形态：替代 `Text("统计加载中…")` 与排在屏外的 spinner。 */
@Composable
fun LoadingBlock(
    minHeight: Dp = 120.dp,
    label: String? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = minHeight),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        if (label != null) {
            Spacer(Modifier.height(Spacing.md))
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
