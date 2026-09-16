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
        headlineLarge = headlineLarge.copy(
            fontSize = 28.sp, lineHeight = 38.sp,
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
val DataHero = TextStyle(
    fontSize = 36.sp, lineHeight = 42.sp,
    fontWeight = FontWeight.Bold, fontFeatureSettings = Tabular,
)
val DataLarge = TextStyle(
    fontSize = 28.sp, lineHeight = 34.sp,
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
