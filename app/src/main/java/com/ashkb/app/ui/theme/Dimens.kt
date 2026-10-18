package com.ashkb.app.ui.theme

import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.dp

/**
 * 间距刻度。布局代码只允许使用这里的值；
 * `3 / 5 / 7 / 9 / 11 / 13 / 15 / 18` dp 属禁用值。
 *
 * v1.2.10（UI 2.0 批次 B）：`22` 从禁用值里移出——它现在只作为 [Size.dockCorner] 的
 * **组件内部度量**存在（与 `navRowHeight 68` / `labelColumnMin 128` 同类），
 * 不是间距刻度上的一档，布局代码仍不得直接用 `22.dp`。
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

    // v1.2.10（UI 2.0 批次 B「换形」）：比 xxxl 再大一档，留给「页头大标题 → 首个区块」
    // 这类需要明显"断气"的位置（PageHeader 用）。填在刻度末尾而不是插入中间，
    // 避免既有 8 档的语义发生位移。
    val huge = 40.dp
}

object Size {
    val touchMin = 48.dp        // 触摸目标硬下限
    val touchComfort = 56.dp    // 主操作推荐
    val iconSm = 18.dp
    val iconMd = 22.dp
    val iconLg = 28.dp
    val chipHeight = 28.dp
    val rowMinHeight = 44.dp    // 键值行

    // v1.2.10（UI 2.0 批次 B「换形」）：导航行左侧的**图标瓦片**边长。
    // 为什么 42：图标本体 22dp，四边各留 10dp 内边距 → 42dp；这个数同时满足
    // ① 瓦片是"底色块"而不是"大图标"（底色占比 ≈ 70%，图标仍是视觉主体）；
    // ② 与 [rowMinHeight] 44 接近，瓦片放进导航行后上下只各余 1dp，行高不再被撑大。
    // 与 [navRowHeight] 68 同类，属组件内部度量，不是间距刻度。
    val iconTile = 42.dp

    // v1.1.6：**悬浮 dock**（iOS 式圆角矩形）的两个度量。
    // 为什么 22：dock 的实际高度约 80dp，iOS 那种观感是**约高度的 1/4**。第一版取 28
    // （与 extraLarge 同值）在真机上显得像胶囊——维护者截图后改为 20。
    // v1.2.10（UI 2.0 批次 B「换形」）：20 → 22，配合批次 B 的圆角刻度上移一档；
    // 仍是"约高度 1/4"，仍明显小于 extraLarge（24），不会退化成胶囊。
    val dockCorner = 22.dp
    // 为什么 10：HTML 预览的 `--glass-shadow` 是 `0 8px 32px`，垂直偏移 8px →
    // Compose 的 elevation 约取 8–10dp 才有同等的"浮起来"观感；太小看不出悬浮，
    // 太大会在深色底上形成一圈灰晕。
    val dockShadow = 10.dp

    // v1.1.6：dock 内**选中态胶囊**的圆角。为什么 16：它是 dock（22）内部的一层，
    // 圆角必须明显小于外框，否则选中胶囊会与 dock 圆角"同弧"而看不出层次。
    // v1.2.10（UI 2.0 批次 B）：dockCorner 提到 22 后，这里保持 16 —— 差 6dp 仍够看出层次，
    // 且它正好等于批次 B 的 shapes.large，与卡片圆角同档。
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

    // v1.2.10（UI 2.0 批次 C「换件」）：
    // - emptyIconTile：空状态的圆形托底直径。为什么 56：图标本体 28dp，四周各留 14dp；
    //   同时 ≥ touchComfort（56），空状态那颗图标本身也落在"看得见、点得着"的尺度上。
    // - skeletonBar：骨架屏单条高度。为什么 14：约等于一行 bodyMedium 的行高（24sp）的
    //   一半多一点，三条叠起来正好占住一张 SectionCard 的内容区，版式不会跳变。
    val emptyIconTile = 56.dp
    val skeletonBar = 14.dp

    // v1.2.10（UI 2.0 批次 C「换件」）：StatusChip 前导**状态点**直径。
    // 为什么 6：与 labelMedium（13sp）搭配时约等于一个小写字母的 x 高度——
    // 能看清"有个点"，又不会把胶囊里的文字挤窄（原 18dp 图标的 1/9 面积）。
    val statusDot = 6.dp

    // v1.2.10（UI 2.0 批次 D「换图」）：用药卡左侧**时间轨**的三个度量。
    // 设计稿 §5.4 给的 CSS 是 `.rail{width:52px}` / `.spine{width:2px}` /
    // `.spine::before{width:8px;height:8px}`，这里逐一照搬。
    //
    // 为什么 52：轨道要竖排「07:30」这样的 5 字符时刻 + 下面一行提示（「早餐后」）。
    // 按本 App 的 13sp 数据字号（tnum），"07:30" 实测约 40dp，两侧各留 6dp 余量。
    // 时刻列**恒宽右对齐**——三张卡的冒号因此落在同一条竖线上，扫读时不用横向找位。
    //
    // 为什么 2：脊柱线是"轨迹"不是"分隔线"。1dp 在高密度屏上会被抗锯齿吃成浅灰虚线，
    // 2dp 才连成一条；再粗就会跟数据线（TrendChart 主线也是 2dp）抢视觉。
    //
    // 为什么 8：状态点要盖在 2dp 脊柱线上（直径 8 → 两侧各露出 3dp），
    // 是"线上的一颗珠子"而不是"线上的一个疙瘩"；与 StatusChip 的 6dp 状态点分开，
    // 因为这里是**唯一**承载用药状态的标记，需要更大一级的可辨度。
    val doseRailWidth = 52.dp
    val doseSpineWidth = 2.dp
    val doseDot = 8.dp
}

object Motion {
    val Fast = tween<Float>(150, easing = EaseOut)
    val Normal = tween<Float>(250, easing = EaseOut)
    val Slow = tween<Float>(400, easing = EaseInOut)

    const val FastMs = 150
    const val NormalMs = 250
    const val SlowMs = 400
}
