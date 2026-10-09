package com.ashkb.app.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// ============================================================
// ASHKB 设计 token · 整表来自「澄序 CLARITY · ASHKB UI 2.0」设计稿（批次 A：换色）
//
// 生成口径（设计稿 §1 的重生成参数，替换 v1.2.7 之前的旧的暖色口径）：
//   primary h199 C0.075 · neutral h210 C0.008
//   success h158 C0.065 · warning h72 C0.095 · danger h355 C0.155
//
// ⚠️ 本仓库里**没有**这个生成器（全盘搜过 .ps1/.js/.py/.sh/.mjs 无命中）。上面这行
//    是**口径记录**，不是"别手改"的命令：日后若用外部生成器重生成，必须用这一组参数，
//    否则会静默回到旧的暖色调色板（正是 v1.2.8 换掉的东西）。改色值请直接改下面的 hex，
//    并用设计稿 §1 的表逐条校对。
//
// 校验（v1.2.8 实测，脚本 E:\ASHKB\verify-palette-contrast.ps1 可复跑，只读）：
//   · 48 个角色 × 浅/深两套 **与设计稿 §1 的表逐条一致**（0 缺失 / 0 不符 / 0 多余）
//   · 18 组文本对（onX vs X、onXContainer vs XContainer、onSurfaceVariant vs surfaceVariant …）
//     **全部 ≥ AA**：最低 **4.85:1**（浅色 onSurfaceVariant / surfaceVariant），深色最低 6.67:1
// ============================================================

// ---------- Light ----------
val primaryLight = Color(0xFF0B6E9E)          // 品牌蓝，白字 5.6:1
val onPrimaryLight = Color(0xFFFFFFFF)
val primaryContainerLight = Color(0xFFC7E4F5)
val onPrimaryContainerLight = Color(0xFF062A3D)
val inversePrimaryLight = Color(0xFF8FC6DF)
val secondaryLight = Color(0xFF4C6A7A)
val onSecondaryLight = Color(0xFFFFFFFF)
val secondaryContainerLight = Color(0xFFDCEEF7)   // 选中胶囊底（= 设计稿 brand-soft）
val onSecondaryContainerLight = Color(0xFF07374F) // 压在 brand-soft 上的文字，8.2:1
val tertiaryLight = Color(0xFF5A6B78)
val onTertiaryLight = Color(0xFFFFFFFF)
val tertiaryContainerLight = Color(0xFFE2E8ED)
val onTertiaryContainerLight = Color(0xFF16232E)
val errorLight = Color(0xFFC0283C)
val onErrorLight = Color(0xFFFFFFFF)
val errorContainerLight = Color(0xFFFBDDE2)
val onErrorContainerLight = Color(0xFF5C0A18)
val successLight = Color(0xFF157A52)
val onSuccessLight = Color(0xFFFFFFFF)
val successContainerLight = Color(0xFFD8F0E5)
val onSuccessContainerLight = Color(0xFF0A3D28)
val warningLight = Color(0xFF96610A)
val onWarningLight = Color(0xFFFFFFFF)
val warningContainerLight = Color(0xFFFBEED3)
val onWarningContainerLight = Color(0xFF3F2A04)
val dangerLight = Color(0xFFC0283C)
val onDangerLight = Color(0xFFFFFFFF)
val dangerContainerLight = Color(0xFFFBDDE2)
val onDangerContainerLight = Color(0xFF5C0A18)
val backgroundLight = Color(0xFFF3F6F8)       // 冷调瓷白（原暖米 0xFFFFF8F4）
val onBackgroundLight = Color(0xFF16232E)
val surfaceLight = Color(0xFFFFFFFF)
val onSurfaceLight = Color(0xFF16232E)
val surfaceVariantLight = Color(0xFFEDF1F4)
val onSurfaceVariantLight = Color(0xFF5A6B78)
val surfaceContainerLowestLight = Color(0xFFFFFFFF)
val surfaceContainerLowLight = Color(0xFFF8FAFB)
val surfaceContainerLight = Color(0xFFF3F6F8)
val surfaceContainerHighLight = Color(0xFFEDF1F4)
val surfaceContainerHighestLight = Color(0xFFE2E8ED)
val surfaceDimLight = Color(0xFFDDE4E9)
val surfaceBrightLight = Color(0xFFFFFFFF)
val surfaceTintLight = Color(0xFF0B6E9E)
val outlineLight = Color(0xFF8B99A4)
val outlineVariantLight = Color(0xFFE2E8ED)   // 卡片发线（1px hairline 承担层级）
val inverseSurfaceLight = Color(0xFF2A3742)
val inverseOnSurfaceLight = Color(0xFFEAF2F7)
val scrimLight = Color(0xFF000000)

// ---------- Dark ----------
val primaryDark = Color(0xFF7BC8EE)           // 高明度蓝
val onPrimaryDark = Color(0xFF06334A)
val primaryContainerDark = Color(0xFF124F6E)
val onPrimaryContainerDark = Color(0xFFC7E4F5)
val inversePrimaryDark = Color(0xFF0B6E9E)
val secondaryDark = Color(0xFF9FC4D8)
val onSecondaryDark = Color(0xFF0A2E40)
val secondaryContainerDark = Color(0xFF1B4558)   // 深色选中胶囊底
val onSecondaryContainerDark = Color(0xFFC4E6F7)
val tertiaryDark = Color(0xFF9DADBA)
val onTertiaryDark = Color(0xFF16232E)
val tertiaryContainerDark = Color(0xFF27333F)
val onTertiaryContainerDark = Color(0xFFE7EEF3)
val errorDark = Color(0xFFF2A0A9)
val onErrorDark = Color(0xFF4A0010)
val errorContainerDark = Color(0xFF6E1020)
val onErrorContainerDark = Color(0xFFFBDDE2)
val successDark = Color(0xFF7CC9A5)
val onSuccessDark = Color(0xFF0A2E1E)
val successContainerDark = Color(0xFF14382A)
val onSuccessContainerDark = Color(0xFFBDE8D4)
val warningDark = Color(0xFFE5B760)
val onWarningDark = Color(0xFF2E1F00)
val warningContainerDark = Color(0xFF3D2E0C)
val onWarningContainerDark = Color(0xFFF3DCA8)
val dangerDark = Color(0xFFF2A0A9)
val onDangerDark = Color(0xFF4A0010)
val dangerContainerDark = Color(0xFF4A1220)
val onDangerContainerDark = Color(0xFFF8D3D8)
val backgroundDark = Color(0xFF0C1218)
val onBackgroundDark = Color(0xFFE7EEF3)
val surfaceDark = Color(0xFF151D26)           // 设计稿注：卡片比底亮 ~6%
val onSurfaceDark = Color(0xFFE7EEF3)
val surfaceVariantDark = Color(0xFF1B2631)
val onSurfaceVariantDark = Color(0xFF9DADBA)
val surfaceContainerLowestDark = Color(0xFF0A0F14)
val surfaceContainerLowDark = Color(0xFF10161D)
val surfaceContainerDark = Color(0xFF151D26)
val surfaceContainerHighDark = Color(0xFF1B2631)
val surfaceContainerHighestDark = Color(0xFF27333F)
val surfaceDimDark = Color(0xFF0C1218)
val surfaceBrightDark = Color(0xFF2E3B48)
val surfaceTintDark = Color(0xFF7BC8EE)
val outlineDark = Color(0xFF5A6B78)
val outlineVariantDark = Color(0xFF27333F)
val inverseSurfaceDark = Color(0xFFE7EEF3)
val inverseOnSurfaceDark = Color(0xFF1B2631)
val scrimDark = Color(0xFF000000)

// ============================================================
// 以下为手工装配区
// ============================================================

/**
 * v1.1.6：**半透明表层（"毛玻璃"替代方案）** 的两个颜色。
 *
 * ### 为什么不用真的 backdrop-blur
 * HTML 里的 `backdrop-filter` 在 Compose **没有等价物**：`Modifier.blur()` 模糊的是
 * **这个元素自己**，不是它背后的内容。真正模糊背后需要 `RenderEffect`（**API 31+**），
 * 而本应用 `minSdk = 26` —— 维护者明确要求**顾及其他机型**，所以不用它。
 * （v1.2.8 的批次 A 只改了下面这几个数值，这条理由不变。）
 *
 * ### 用的是什么
 * 「**半透明底 + 一道高光分隔线**」：视觉上非常接近毛玻璃，代价近零，**全机型一致**。
 * - [GlassSurfaceLight] / [GlassSurfaceDark]：表层底色（带 alpha，让下方内容微微透出来）
 * - [GlassHighlightLight] / [GlassHighlightDark]：顶部那道高光（**模拟光从上方打在玻璃边缘**，
 *   少了它，半透明底会显得"脏"而不是"玻璃"）
 *
 * ⚠️ **只在导航栏与弹层用**（维护者裁决）：这两处静止、面积小。**列表卡片不要用**——
 * 滚动时每帧都要重新合成半透明层，是纯亏。**安全类信息（发热 / 漏服 / 相互作用 /
 * 黑框警告 / 删除确认）绝对不用**：半透明会让对比度随背景变化，而医学警告不能
 * "看背景运气"。
 */
object Glass {
    // v1.2.8（设计稿批次 A）：**从暖调半透明改成冷调**。
    // 页面底从暖米 0xFFFFF8F4 换成了冷瓷白 0xFFF3F6F8，暖白玻璃压在冷底上会发黄，
    // 所以设计稿 §1.3 给的是「92% 冷白 + 一道冷调发线」：
    //   surfaceLight  52% 暖白 0x85FFF8F4 → 92% 冷白 0xEAFFFFFF
    //   surfaceDark   52% 暖深 0x85151210 → 94% 深底 0xF0151D26
    //   borderLight   75% 白高光 0xBFFFFFFF → 冷调发线 0xFFE2E8ED
    //                 （**语义变了**：不再是"玻璃边缘高光"，而是与卡片同款的发线）
    // borderDark 与两道 highlight 不变：深色下 18% 白边框本来就够淡，
    // 顶部高光属于光照模拟，与主题色温无关。
    val surfaceLight = Color(0xEAFFFFFF)   // 92% 冷白（0xEA = 234/255）
    val surfaceDark = Color(0xF0151D26)    // 94% 深底（0xF0 = 240/255）
    val borderLight = Color(0xFFE2E8ED)    // 冷调发线（= outlineVariantLight）
    val borderDark = Color(0x2EFFFFFF)     // 深色边框：不变
    val highlightLight = Color(0x66FFFFFF) // 顶部高光：不变
    val highlightDark = Color(0x1FFFFFFF)

    /** `0 8px 32px rgba(44,50,56,.10)` —— 悬浮感来源；对应 HTML 的垂直偏移 8px。 */
    const val shadowDp = 8

    /** 与 `--glass-shadow` 的模糊半径对应：投影要够软才像玻璃浮起来。 */
    const val shadowSoftDp = 32
}
val LightColors = lightColorScheme(
    primary = primaryLight, onPrimary = onPrimaryLight,
    primaryContainer = primaryContainerLight, onPrimaryContainer = onPrimaryContainerLight,
    inversePrimary = inversePrimaryLight,
    secondary = secondaryLight, onSecondary = onSecondaryLight,
    secondaryContainer = secondaryContainerLight, onSecondaryContainer = onSecondaryContainerLight,
    tertiary = tertiaryLight, onTertiary = onTertiaryLight,
    tertiaryContainer = tertiaryContainerLight, onTertiaryContainer = onTertiaryContainerLight,
    error = errorLight, onError = onErrorLight,
    errorContainer = errorContainerLight, onErrorContainer = onErrorContainerLight,
    background = backgroundLight, onBackground = onBackgroundLight,
    surface = surfaceLight, onSurface = onSurfaceLight,
    surfaceVariant = surfaceVariantLight, onSurfaceVariant = onSurfaceVariantLight,
    surfaceContainerLowest = surfaceContainerLowestLight, surfaceContainerLow = surfaceContainerLowLight,
    surfaceContainer = surfaceContainerLight, surfaceContainerHigh = surfaceContainerHighLight,
    surfaceContainerHighest = surfaceContainerHighestLight,
    surfaceDim = surfaceDimLight, surfaceBright = surfaceBrightLight, surfaceTint = surfaceTintLight,
    outline = outlineLight, outlineVariant = outlineVariantLight,
    inverseSurface = inverseSurfaceLight, inverseOnSurface = inverseOnSurfaceLight, scrim = scrimLight,
)

val DarkColors = darkColorScheme(
    primary = primaryDark, onPrimary = onPrimaryDark,
    primaryContainer = primaryContainerDark, onPrimaryContainer = onPrimaryContainerDark,
    inversePrimary = inversePrimaryDark,
    secondary = secondaryDark, onSecondary = onSecondaryDark,
    secondaryContainer = secondaryContainerDark, onSecondaryContainer = onSecondaryContainerDark,
    tertiary = tertiaryDark, onTertiary = onTertiaryDark,
    tertiaryContainer = tertiaryContainerDark, onTertiaryContainer = onTertiaryContainerDark,
    error = errorDark, onError = onErrorDark,
    errorContainer = errorContainerDark, onErrorContainer = onErrorContainerDark,
    background = backgroundDark, onBackground = onBackgroundDark,
    surface = surfaceDark, onSurface = onSurfaceDark,
    surfaceVariant = surfaceVariantDark, onSurfaceVariant = onSurfaceVariantDark,
    surfaceContainerLowest = surfaceContainerLowestDark, surfaceContainerLow = surfaceContainerLowDark,
    surfaceContainer = surfaceContainerDark, surfaceContainerHigh = surfaceContainerHighDark,
    surfaceContainerHighest = surfaceContainerHighestDark,
    surfaceDim = surfaceDimDark, surfaceBright = surfaceBrightDark, surfaceTint = surfaceTintDark,
    outline = outlineDark, outlineVariant = outlineVariantDark,
    inverseSurface = inverseSurfaceDark, inverseOnSurface = inverseOnSurfaceDark, scrim = scrimDark,
)

val ClinicalLight = ClinicalColors(
    success = successLight, onSuccess = onSuccessLight,
    successContainer = successContainerLight, onSuccessContainer = onSuccessContainerLight,
    warning = warningLight, onWarning = onWarningLight,
    warningContainer = warningContainerLight, onWarningContainer = onWarningContainerLight,
    danger = dangerLight, onDanger = onDangerLight,
    dangerContainer = dangerContainerLight, onDangerContainer = onDangerContainerLight,
)

val ClinicalDark = ClinicalColors(
    success = successDark, onSuccess = onSuccessDark,
    successContainer = successContainerDark, onSuccessContainer = onSuccessContainerDark,
    warning = warningDark, onWarning = onWarningDark,
    warningContainer = warningContainerDark, onWarningContainer = onWarningContainerDark,
    danger = dangerDark, onDanger = onDangerDark,
    dangerContainer = dangerContainerDark, onDangerContainer = onDangerContainerDark,
)
