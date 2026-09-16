package com.ashkb.app.ui.theme

import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.dp

/**
 * 间距刻度。布局代码只允许使用这里的值；
 * `3 / 5 / 7 / 9 / 11 / 13 / 15 / 18 / 22` dp 属禁用值。
 */
object Spacing {
    val xxs = 2.dp    // 仅组件内部：图标与文字之间
    val xs = 4.dp     // 紧凑元素内边距、chip 间距
    val sm = 8.dp     // 行内元素间距、列表项内部
    val md = 12.dp    // 卡片之间、区块内小节之间
    val lg = 16.dp    // 页面左右边距、卡片内边距（唯一值）
    val xl = 20.dp    // 区块之间
    val xxl = 24.dp   // hero 与首个区块之间、页面顶部
    val xxxl = 32.dp  // 大分组之间、页面底部收尾
}

object Size {
    val touchMin = 48.dp        // 触摸目标硬下限
    val touchComfort = 56.dp    // 主操作推荐
    val iconSm = 18.dp
    val iconMd = 22.dp
    val iconLg = 28.dp
    val chipHeight = 28.dp
    val rowMinHeight = 44.dp    // 键值行
    val navRowHeight = 68.dp    // 带副标题的导航行
    val heroMinHeight = 120.dp
    val chartHeight = 200.dp    // 图表：需容纳轴标签
    val chartDot = 5.dp         // 趋势图数据点半径
    val chartStroke = 1.5.dp    // 趋势图网格线宽
    val emergencyCallHeight = 64.dp  // 紧急卡页 120 快拨大按钮
    val divider = 1.dp
    val statusBar = 4.dp        // AlertBanner 左侧色条宽度
}

object Motion {
    val Fast = tween<Float>(150, easing = EaseOut)
    val Normal = tween<Float>(250, easing = EaseOut)
    val Slow = tween<Float>(400, easing = EaseInOut)

    const val FastMs = 150
    const val NormalMs = 250
    const val SlowMs = 400
}
