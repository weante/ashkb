package com.ashkb.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

/**
 * 中文排版四条现实：
 * 1. M3 默认字号按西文定，对中文偏小一档 —— 整体上调一档；
 * 2. 行高给到 1.5–1.7（中文方块字需要更多呼吸）；
 * 3. 系统 CJK 字体通常只有 Regular / Bold —— 标题优先"加大字号 + Bold"；
 * 4. 不用全角空格或手动双空格排版。
 */
private const val Tabular = "tnum"

private val CjkLineHeight = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

fun ashkbTypography(): Typography = Typography().run {
    copy(
        // —— 标题：靠字号而非字重 ——
        // v1.2.10（UI 2.0 批次 B「换形」）：headlineLarge 28 → 26。一级页的标题由
        // PageHeader 直排（不再装进卡片），26sp 比 28sp 更贴近"页面名"而不是"区块标题"；
        // headlineMedium / headlineSmall 不动——它们是二级页顶栏与弹层标题，字号体系没变。
        headlineLarge = headlineLarge.copy(
            fontSize = 26.sp, lineHeight = 36.sp,
            fontWeight = FontWeight.Bold, lineHeightStyle = CjkLineHeight,
        ),
        headlineMedium = headlineMedium.copy(
            fontSize = 24.sp, lineHeight = 34.sp,
            fontWeight = FontWeight.Bold, lineHeightStyle = CjkLineHeight,
        ),
        headlineSmall = headlineSmall.copy(
            fontSize = 22.sp, lineHeight = 30.sp,
            fontWeight = FontWeight.SemiBold, lineHeightStyle = CjkLineHeight,
        ),
        titleLarge = titleLarge.copy(
            fontSize = 20.sp, lineHeight = 28.sp,
            fontWeight = FontWeight.SemiBold, lineHeightStyle = CjkLineHeight,
        ),
        titleMedium = titleMedium.copy(
            fontSize = 17.sp, lineHeight = 25.sp,
            fontWeight = FontWeight.SemiBold, lineHeightStyle = CjkLineHeight,
        ),
        titleSmall = titleSmall.copy(
            fontSize = 15.sp, lineHeight = 22.sp,
            fontWeight = FontWeight.Medium, lineHeightStyle = CjkLineHeight,
        ),
        // —— 正文：整体上调一档 ——
        bodyLarge = bodyLarge.copy(
            fontSize = 16.sp, lineHeight = 26.sp,
            fontWeight = FontWeight.Normal, lineHeightStyle = CjkLineHeight,
        ),
        bodyMedium = bodyMedium.copy(
            fontSize = 15.sp, lineHeight = 24.sp,
            fontWeight = FontWeight.Normal, lineHeightStyle = CjkLineHeight,
        ),
        bodySmall = bodySmall.copy(
            fontSize = 13.sp, lineHeight = 20.sp,
            fontWeight = FontWeight.Normal, lineHeightStyle = CjkLineHeight,
        ),
        // —— 标签：12sp 是硬下限，禁止再小 ——
        labelLarge = labelLarge.copy(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
        labelMedium = labelMedium.copy(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
        labelSmall = labelSmall.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    )
}

// 数据展示专用：不在 Typography 里，避免被误用于正文。
// 数字一律等宽对齐（tnum），避免指标跳动。
// v1.2.10（UI 2.0 批次 B「换形」）：DataHero 36 → 40、DataLarge 28 → 30。
// 为什么上调：Hero 里的完成度百分比/核心指标是"一眼看结论"的角色，40sp 才够分量；
// DataLarge 跟着 Hero 走一档，保持 40 / 30 / 22 / 17 的四级关系不断层。
val DataHero = TextStyle(
    fontSize = 40.sp, lineHeight = 46.sp,
    fontWeight = FontWeight.Bold, fontFeatureSettings = Tabular,
)
val DataLarge = TextStyle(
    fontSize = 30.sp, lineHeight = 36.sp,
    fontWeight = FontWeight.Bold, fontFeatureSettings = Tabular,
)
val DataMedium = TextStyle(
    fontSize = 22.sp, lineHeight = 28.sp,
    fontWeight = FontWeight.SemiBold, fontFeatureSettings = Tabular,
)
val DataSmall = TextStyle(
    fontSize = 17.sp, lineHeight = 22.sp,
    fontWeight = FontWeight.SemiBold, fontFeatureSettings = Tabular,
)

/**
 * 眉题（overline）：区块标题上方那一行**全大写/字距拉开的小字**，用来给区块"起个名"。
 *
 * 与 Data 系列同级导出（**不进 `Typography`**）：它只用在 PageHeader 与区块头部，
 * 混进 M3 的 15 档里容易被误用于正文。
 *
 * v1.2.10（UI 2.0 批次 B「换形」）：letterSpacing 0.12sp 是按**中文**给的——
 * 设计稿的 `0.12em` 在中文里约合 1.4sp 会散架（CJK 字面本身就是满格方块），
 * 故取一个"能看出字距、但字不成孤岛"的值；西文大写下观感同样成立。
 */
val Overline = TextStyle(
    fontSize = 12.sp, lineHeight = 16.sp,
    fontWeight = FontWeight.Medium, letterSpacing = 0.12.sp,
)

/**
 * 今日卡「时间轨」的计划时刻（DoseCard rail）。
 *
 * v1.2.10（UI 2.0 批次 D「换图」）：设计稿 §5.4 的 `.rail b` 是 `14px / 800 / tabular-nums`，
 * 右对齐、恒宽 52dp。这里取 **13sp** 而不是 DataSmall 的 17sp：52dp 的列宽刚好放下
 * 「07:30」五个字符，17sp 会顶格甚至折行；也不取 12sp —— 计划时刻是要「一眼扫到」的信息，
 * 比它下面那行提示字（`labelSmall`）大一档，主次才分得开。
 * 字重取 Bold（设计稿的 800 在中文系统里会回落到最重的可用字重），
 * 并保持 **tnum 等宽**，让不同卡片上的冒号落在同一列上。
 */
val TimeRail = TextStyle(
    fontSize = 13.sp, lineHeight = 18.sp,
    fontWeight = FontWeight.Bold, fontFeatureSettings = Tabular,
)
