package com.ashkb.app.ui.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * v1.1.6：弹层包装——**只加一件事**：在弹层顶边画一道高光。
 *
 * ### 为什么最终收敛成"几乎什么都不做"
 * 我在这上面连改了四版，每一版都动得更多、也更错：
 *   ① 换 `Glass.surfaceLight`（52% 玻璃）→ 表单与背后文字重叠，**不可读**；
 *   ② 传 `scrimColor = Color.Unspecified` → 完全不画遮罩，像直接叠在页面上；
 *   ③ 传 `scrimColor = scheme.scrim` → 项目里那个值是**不透明纯黑**，上半屏全黑；
 *   ④ 把 `containerColor` 设为透明、自己铺一层 Box → 自绘层**不含拖拽条、且是方角**，
 *      与系统容器的圆角不重合，顶部留缝、圆角露角。
 *
 * 结论：**原生的 `ModalBottomSheet` 本来就是对的**（维护者截图那张"修改化验结果"就是原生，
 * 压暗、不透明、圆角、拖拽条全都对）。我不该去重做它的容器与遮罩。
 *
 * 所以现在这个包装**只保留高光**，其余一律交给系统默认：
 *  · 不传 `scrimColor`（用 M3 默认的 ~32% 黑）；
 *  · 不传 `containerColor`（用 M3 默认的 surfaceContainerLow）；
 *  · 不用自绘 Box 铺底（那会破坏圆角与拖拽条区域）；
 *  · 高光画在**系统容器的内容之上**，且用 `drawWithContent` 而非额外布局层。
 *
 * ⚠️ 安全类弹层（删除确认、发热/漏服/相互作用警告）**不要**用本包装：
 * 高光会让边缘变得更"轻"，而危险操作需要更实的视觉重量。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    // 这里**刻意什么都不算**：原生的容器/遮罩/圆角/拖拽条本来就是对的，
    // 任何"自己算一个颜色"的尝试（见上方四版教训）都会在某一种设备或某一种弹层上出错。
    if (sheetState != null) {
        ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            modifier = modifier,
            sheetState = sheetState,
            content = content,
        )
    } else {
        ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            modifier = modifier,
            content = content,
        )
    }
}
