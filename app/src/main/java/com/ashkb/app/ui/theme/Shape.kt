package com.ashkb.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * 全 App 唯一圆角来源。禁止在页面里写 `RoundedCornerShape(...)`。
 *
 * v1.2.10（UI 2.0 批次 B「换形」）：四档整体收小 2–4dp——圆角越大越像"消费级 App"，
 * 而本应用是长期记录的医疗工具，收小后更接近"器械感"。extraSmall 6dp 不动
 * （badge / chip 已在下限，再小就看不出圆角）。
 */
val AshkbShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),    // badge、chip（不变）
    small = RoundedCornerShape(8.dp),         // 输入框、小按钮、图标瓦片
    medium = RoundedCornerShape(12.dp),       // 列表项、次级卡
    large = RoundedCornerShape(16.dp),        // SectionCard —— 全 App 唯一卡片圆角
    extraLarge = RoundedCornerShape(24.dp),   // BottomSheet、hero 卡
)
