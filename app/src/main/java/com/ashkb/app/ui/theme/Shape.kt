package com.ashkb.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** 全 App 唯一圆角来源。禁止在页面里写 `RoundedCornerShape(...)`。 */
val AshkbShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),    // badge、chip
    small = RoundedCornerShape(10.dp),        // 输入框、小按钮
    medium = RoundedCornerShape(14.dp),       // 列表项、次级卡
    large = RoundedCornerShape(18.dp),        // SectionCard —— 全 App 唯一卡片圆角
    extraLarge = RoundedCornerShape(28.dp),   // BottomSheet、hero 卡
)
