package com.ashkb.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow

import com.ashkb.app.R
import com.ashkb.app.ui.theme.Size
import com.ashkb.app.ui.theme.Spacing
import com.ashkb.app.ui.theme.StatusTone
import com.ashkb.app.ui.theme.colors

/**
 * 导航行：取代 3 份重复实现（`HealthEntryCard` / 紧急卡导航行 / `KbListCard`）。
 * `subtitle` 用来放实时摘要（"今日已记 3 项 · 体重 62.4kg"），让用户不点进去也知道状态。
 *
 * v1.2.10（UI 2.0 批次 C「换件」）：左侧图标从**裸 22dp**升级为 **42dp 圆角瓦片**
 * （`primaryContainer` 底 + `primary` 图标）。
 *
 * 为什么瓦片：裸图标在瓷白卡片上是一枚"浮着的符号"，扫读时只能靠标题认行；
 * 瓦片给了它一块固定面积的底色，五行排下来眼睛能靠**色块位置**快速定位。
 * 为什么 42：图标 22 + 四边各 10 → 42，底色占比 ≈ 70%，图标仍是视觉主体（见 [Size.iconTile]）。
 * 为什么用 `primaryContainer` 而不是 `primary.copy(alpha)`：语义容器一律实心，
 * 禁止半透明叠加（红线 1）；且瓦片是**装饰性**提示，不承载状态语义——
 * 状态一律走 [StatusChip] 的三重编码。
 *
 * [tone] 允许调用方把瓦片换成别的语义色 soft 底（**仅**用于紧急卡这类"入口本身有语义"的场合，
 * 与设计稿「danger-soft 瓦片」一致）；默认 `null` = 品牌蓝。
 */
@Composable
fun NavRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    badge: (@Composable () -> Unit)? = null,
    tone: StatusTone? = null,
    onClick: () -> Unit,
) {
    val (tileBg, tileFg) = if (tone == null) {
        MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        tone.colors()
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(Size.divider, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .heightIn(min = Size.navRowHeight)
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Box(
                modifier = Modifier
                    .size(Size.iconTile)
                    .clip(MaterialTheme.shapes.small)
                    .background(tileBg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,   // 装饰性：标题即语义
                    modifier = Modifier.size(Size.iconMd),
                    tint = tileFg,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (badge != null) badge()
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,   // 装饰性：整行可点，语义在标题
                modifier = Modifier.size(Size.iconMd),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 统一的二级页顶栏。`onBack` 非空时**强制**渲染返回箭头
 * （修 `WellnessScreen` 无顶栏、`Emergency/Checkup/Knowledge` 无 navigationIcon）。
 *
 * `windowInsets` 默认为 0：外层 `Scaffold` 已预留状态栏区域，
 * 内层再叠一次系统栏内边距会导致顶部出现双倍空白。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack, modifier = Modifier.size(Size.touchMin)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.common_back),
                        modifier = Modifier.size(Size.iconMd),
                    )
                }
            }
        },
        actions = actions,
        windowInsets = WindowInsets(0, 0, 0, 0),
        colors = TopAppBarDefaults.topAppBarColors(
            // v1.2.10（UI 2.0 批次 C「换件」）：顶栏**去底色**——原 containerColor 用
            // surface、滚动后 scrolledContainerColor 用 surfaceContainer，会在页面顶部
            // 压出一条与页面底不同的色带（批次 A 把页面底改成瓷白后，这条色带尤其明显）。
            // 设计稿 §5.7 要求顶栏与页面同底，标题靠字号/字重拉开层级，不靠底色块。
            containerColor = MaterialTheme.colorScheme.background,
            scrolledContainerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
        ),
    )
}
