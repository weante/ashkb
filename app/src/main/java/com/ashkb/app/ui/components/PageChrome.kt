package com.ashkb.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ashkb.app.ui.theme.Size
import com.ashkb.app.ui.theme.Spacing

/**
 * 页面级外壳（v1.2.3）：**标题卡片**与**胶囊页签**。
 *
 * ### 为什么单独成文件
 * 这两件都是「页面级 chrome」，不属于某个业务页。原先标题各页各写（`ScreenTopBar` 直接铺在
 * 米色背景上、报表页自己拼一行 Row），页签各页各写（知识库用 `FilterChip`、报表用
 * `ScrollableTabRow`）——于是同一套设计在三个地方长出三种样子。
 *
 * ### 依据是维护者自己的设计稿
 * `ASHKB-毛玻璃UI预览.html` 里：
 * ```css
 * .h-title  { font-size:21px; font-weight:700 }
 * .tchip    { border:1px solid rgba(65,100,118,.3); background:rgba(255,255,255,.4);
 *             font-weight:600; color:#4f443e; padding:8px 16px; border-radius:999px }
 * .tchip.on { background:linear-gradient(135deg,#416476,#2c4a5a); color:#fff;
 *             border-color:transparent; box-shadow:0 6px 16px rgba(44,74,90,.3) }
 * ```
 * 维护者反馈「要和其他页面一样圆角矩形且颜色对比度要高」——指的就是把这两件统一到上面那套。
 */

/**
 * 标题卡片：把页面标题装进一张圆角卡片，与页面里其它卡片同一套容器。
 *
 * 为什么不是 `ScreenTopBar`：那个是**带返回箭头的二级页**顶栏（`TopAppBar`，直接铺在页面底色上）。
 * 一级页（知识库、报表）没有返回箭头，标题孤零零浮在米色背景上，与下方一排白卡片不是一套视觉语言。
 */
@Composable
fun ScreenTitleCard(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = Spacing.lg),
        // 与 SectionCard 同款：`shapes.large`（18dp）是本项目**唯一**的卡片圆角
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        Row(
            Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    // 对比度：标题用 onSurface（最高对比），不用 onSurfaceVariant
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            actions?.invoke(this)
        }
    }
}

/**
 * 胶囊页签（设计稿 `.tchip`）。
 *
 * 选中态是**深青渐变底 + 白字**，未选中是**白底细描边 + 深字**——两者对比度都明显高于
 * 原先的「下划线式 Tab」（未选中灰字几乎与背景同色）。
 *
 * 渐变末端没有新增色板项，而是由主题 primary 向黑 lerp 22% 得到：设计稿的 `--primary-deep`
 * 是 `#2c4a5a`，而 `#416476` 向黑 lerp 22% ≈ `#324E5C`，足够接近；这样做的好处是
 * **深色主题自动跟着变**，不需要为两套主题各维护一个常量。
 */
@Composable
fun TabChips(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    // 胶囊：999px 半径在 Compose 里用 50% 表达
    val shape = RoundedCornerShape(percent = 50)
    Row(
        // ⚠️ 水平留白由**调用方**给：知识库页的标签在已经带 `horizontal = Spacing.lg` 的 Column 里，
        // 这里再给一次会变成双重缩进。故组件本身只负责「横滚 + 间距 + 胶囊样式」。
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        labels.forEachIndexed { i, label ->
            val on = i == selectedIndex
            Box(
                modifier = Modifier
                    .clip(shape)
                    .then(
                        if (on) {
                            Modifier.background(
                                Brush.linearGradient(
                                    listOf(cs.primary, lerp(cs.primary, Color.Black, 0.22f)),
                                ),
                            )
                        } else {
                            Modifier
                                // 设计稿是 rgba(255,255,255,.4)；这里用 surface 而不是纯白，
                                // 深色主题下才不会变成一块刺眼的白斑
                                .background(cs.surface.copy(alpha = 0.55f))
                                .border(1.dp, cs.primary.copy(alpha = 0.30f), shape)
                        },
                    )
                    .clickable { onSelect(i) }
                    .heightIn(min = Size.touchMin)
                    .padding(horizontal = Spacing.lg),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (on) cs.onPrimary else cs.onSurface,
                )
            }
        }
    }
}
