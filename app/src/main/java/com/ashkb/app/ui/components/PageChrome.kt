package com.ashkb.app.ui.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ashkb.app.ui.theme.Overline
import com.ashkb.app.ui.theme.Size
import com.ashkb.app.ui.theme.Spacing

/**
 * 页面级外壳（v1.2.3 引入标题卡片与胶囊页签；v1.2.10 UI 2.0 批次 C「换件」整体换代）。
 *
 * ### 这一版为什么又改
 * v1.2.3 的出发点是「一级页的裸标题浮在背景上，与下方白卡片不是一套视觉语言」，于是把标题
 * **装进卡片**。真机用下来出现了新问题：标题也是卡片、内容也是卡片，一屏里三张同款卡片
 * 平权排开，**页面名和区块名分不出来**。
 *
 * 设计稿 `澄序 CLARITY · ASHKB UI 2.0` 的 §5.13 给了正解：一级页（L1）用**直排页头**
 * （overline 日期 + 大标题 + 副题），卡片留给内容；二级页（L2）沿用 `ScreenTopBar`。
 * 所以 [ScreenTitleCard] 退役，换成 [PageHeader]。
 *
 * ### 页签为什么也拆
 * §5.7：**页面级**切换（报表四页签）用分段控件（surface-2 槽 + 白色滑块），
 * **筛选级**（知识库分类）保留胶囊但选中态改实心。
 * 原先两者共用一份渐变胶囊实现，结果是"换页"和"筛东西"长得一模一样。
 *
 * 渐变也一并退场：设计稿 §08 红线 8「功能控件一律实心，渐变只允许出现在纯装饰元素」。
 * 原 `TabChips` 的深青渐变属于功能控件承载装饰，本批拆开时顺手去掉。
 */

/**
 * 一级页页头（设计稿 §5.13 的 L1）。
 *
 * 三段式：`overline`（可选，12sp 拉字距小字，通常放日期）→ `title`（26sp Bold，即
 * `headlineLarge`）→ `subtitle`（可选，bodySmall）。`actions` 放页面级动作（刷新等）。
 *
 * 为什么不再装进卡片：见文件头 KDoc。这里**不加任何容器**——页头就是页面本身的一部分，
 * 底色直接用页面底（`surface`）。
 */
@Composable
fun PageHeader(
    title: String,
    modifier: Modifier = Modifier,
    overline: String? = null,
    subtitle: String? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            if (overline != null) {
                Text(
                    overline,
                    style = Overline,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box(Modifier.padding(top = Spacing.xxs))
            }
            Text(
                title,
                style = MaterialTheme.typography.headlineLarge,
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

/**
 * 分段控件（设计稿 §5.7）：页面级视图切换的标准形态。
 *
 * 槽底 `surfaceContainer`（= 设计稿 surface-2），滑块白底 + 1dp hairline。
 *
 * ⚠️ 为什么滑块用**描边**而不是设计稿写的"微投影"：v1.1.6 已经踩过一次
 * `Modifier.shadow` 的坑（真机上会在 dock 下方投出一条 y=3110..3140 的渐变灰带，
 * 见 `AppShell.kt` 的长注释）。同一台机器、同一套渲染路径，这里不再引入真阴影，
 * 改用「白底 + 发线」模拟抬起——在白底槽上，发线已经足够把滑块与槽分开。
 *
 * 各段**等宽**（`weight(1f)`）：段数固定（2–4 段），等宽比按文字宽度自适应更稳，
 * 切换时段宽不跳。段数多、文案长的场景请用 [FilterChips]（横滚、不等宽）。
 */
@Composable
fun SegmentedTabs(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val trackShape = MaterialTheme.shapes.small
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(trackShape)
            .background(cs.surfaceContainer)
            .padding(Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        labels.forEachIndexed { i, label ->
            val on = i == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(trackShape)
                    .then(
                        if (on) {
                            Modifier
                                .background(cs.surfaceContainerLowest)
                                .border(BorderStroke(Size.divider, cs.outlineVariant), trackShape)
                        } else {
                            Modifier
                        },
                    )
                    .clickable { onSelect(i) }
                    .heightIn(min = Size.touchMin)
                    .padding(horizontal = Spacing.sm),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (on) cs.onSurface else cs.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * 筛选胶囊（设计稿 §5.7 右半）：**筛东西**用的横滚 pill。
 *
 * 选中态是**实心 brand + 白字**（原 `TabChips` 是渐变底，按红线 8 去掉渐变）；
 * 未选中是白底细描边 + 深字。两者对比度都明显高于 v1.2.3 之前的「下划线式 Tab」。
 *
 * 横滚 + 不等宽：筛选标签的数量和字数都会随内容变（知识库分类），
 * 用 [SegmentedTabs] 的等宽会把长标签挤断行。
 *
 * ⚠️ 水平留白由**调用方**给：知识库页的标签在已经带 `horizontal = Spacing.lg` 的 Column 里，
 * 这里再给一次会变成双重缩进。故组件本身只负责「横滚 + 间距 + 胶囊样式」。
 */
@Composable
fun FilterChips(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.extraSmall
    Row(
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
                            Modifier.background(cs.primary)
                        } else {
                            Modifier
                                .background(cs.surfaceContainerLowest)
                                .border(BorderStroke(Size.divider, cs.outlineVariant), shape)
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
