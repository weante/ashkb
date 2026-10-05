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

    // v1.1.6：**悬浮 dock**（iOS 式圆角矩形）的两个度量。
    // 为什么 20：dock 的实际高度约 80dp，iOS 那种观感是**约高度的 1/4**。第一版取 28
    // （与 extraLarge 同值）在真机上显得像胶囊——维护者截图后改为 20。
    val dockCorner = 20.dp
    // 为什么 10：HTML 预览的 `--glass-shadow` 是 `0 8px 32px`，垂直偏移 8px →
    // Compose 的 elevation 约取 8–10dp 才有同等的"浮起来"观感；太小看不出悬浮，
    // 太大会在深色底上形成一圈灰晕。
    val dockShadow = 10.dp

    // v1.1.6：dock 内**选中态胶囊**的圆角。为什么 16：它是 dock（20）内部的一层，
    // 圆角必须明显小于外框，否则选中胶囊会与 dock 圆角"同弧"而看不出层次。
    val dockItemCorner = 16.dp
    // v1.0.94：键值行（KeyValueRow）**标签列**的最小宽度——短标签也占这么宽，数值因此从同一条 x 起排。
    //
    // 为什么是 128：本 App 把 bodyMedium 上调到 **15sp**、字距 0.25sp（见 `ui/theme/Type.kt`，
    // 不是 M3 默认的 14sp），中文方块字的字宽 ≈ 字号，本应用最长的静态标签是「骶髂关节影像分期」
    // （8 个 CJK 字）→ 8 × (15 + 0.25) ≈ 122dp；向上取 4dp 的整数倍，留 ~5% 余量兜住各家
    // 回退字体（Noto Sans CJK / 厂商字体）的字宽差异。
    // 再长的标签（用户自填的药名、系统字号放大到 2.0×）会按固有宽度继续把数值起点往右推——
    // 这是预期降级（标签本身不会溢出，Text 受整行宽约束会折行），不是缺陷。
    val labelColumnMin = 128.dp
    val navRowHeight = 68.dp    // 带副标题的导航行
    val heroMinHeight = 120.dp
    val chartHeight = 200.dp    // 图表：需容纳轴标签
    val chartMiniHeight = 92.dp // 小多图单元的画布高（标题与共享时间轴另计）
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
