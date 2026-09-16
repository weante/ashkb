package com.ashkb.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.ashkb.app.ui.theme.Size
import com.ashkb.app.ui.theme.Spacing

/**
 * 带分割线的纵向列表，**最后一项之后不画线**
 * （修此前 7 处 `forEach { Row(); HorizontalDivider() }` 的多余尾线）。
 */
@Composable
fun <T> DividerList(
    items: List<T>,
    key: ((T) -> Any)? = null,
    itemContent: @Composable RowScope.(T) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        items.forEachIndexed { i, item ->
            key(key?.invoke(item) ?: i) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = Size.rowMinHeight)
                        .padding(vertical = Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    itemContent(item)
                }
                if (i != items.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = Spacing.xs),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
            }
        }
    }
}
