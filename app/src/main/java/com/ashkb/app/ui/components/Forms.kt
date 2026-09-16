package com.ashkb.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.ashkb.app.ui.theme.DataLarge
import com.ashkb.app.ui.theme.Size
import com.ashkb.app.ui.theme.Spacing
import com.ashkb.app.ui.theme.StatusTone
import com.ashkb.app.ui.theme.accent
import kotlin.math.roundToInt

/**
 * 临床评分输入（疼痛 0–10、BASDAI 六题）。
 *
 * 取代此前 66 个 32dp `FilterChip` 横滑条 —— 临床输入优先于视觉密度：
 * 大号数字 + 48dp 触摸目标 + 滑杆步进 + 语义描述。
 */
@Composable
fun ScoreInput(
    value: Int,
    onValueChange: (Int) -> Unit,
    range: IntRange = 0..10,
    label: String,
    lowLabel: String = "无",
    highLabel: String = "最严重",
    tone: (Int) -> StatusTone,
) {
    val cs = MaterialTheme.colorScheme
    val accent = tone(value).accent()
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text("$value", style = DataLarge, color = accent)
            Text(
                "/ ${range.last}",
                style = MaterialTheme.typography.labelMedium,
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(start = Spacing.xxs),
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            FilledTonalIconButton(
                onClick = { onValueChange((value - 1).coerceAtLeast(range.first)) },
                modifier = Modifier.size(Size.touchMin),
            ) {
                Icon(Icons.Rounded.Remove, contentDescription = "减少一分")
            }
            Slider(
                value = value.toFloat(),
                onValueChange = { onValueChange(it.roundToInt().coerceIn(range.first, range.last)) },
                valueRange = range.first.toFloat()..range.last.toFloat(),
                steps = (range.last - range.first - 1).coerceAtLeast(0),
                modifier = Modifier
                    .weight(1f)
                    .height(Size.touchMin)
                    .semantics { contentDescription = "$label 当前 $value 分，满分 ${range.last}" },
            )
            FilledTonalIconButton(
                onClick = { onValueChange((value + 1).coerceAtMost(range.last)) },
                modifier = Modifier.size(Size.touchMin),
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "增加一分")
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(lowLabel, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
            Text(highLabel, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
        }
    }
}

/**
 * 破坏性操作统一形态：一次点击先弹确认，确认文案必须说清后果
 * （"删除后可在备份中恢复"），按钮用「取消」/「删除」而非「确定」。
 */
@Composable
fun DestructiveAction(
    label: String,
    confirmTitle: String,
    confirmBody: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirming by remember { mutableStateOf(false) }
    TextButton(onClick = { confirming = true }, modifier = modifier) {
        Text(label, color = MaterialTheme.colorScheme.error)
    }
    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(confirmTitle) },
            text = { Text(confirmBody) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirming = false
                        onConfirm()
                    },
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) { Text("取消") }
            },
        )
    }
}
