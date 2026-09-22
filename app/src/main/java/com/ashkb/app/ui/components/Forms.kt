package com.ashkb.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

import com.ashkb.app.R
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
 *
 * 未作答态（unrecorded=true）数字位显示「—」：null = 未记录 与 0 = 无 在
 * BASDAI 语义上严格不同，不能让未作答看起来像已选 0。
 */
@Composable
fun ScoreInput(
    value: Int,
    onValueChange: (Int) -> Unit,
    range: IntRange = 0..10,
    label: String,
    unrecorded: Boolean = false,
    lowLabel: String = stringResource(R.string.common_none),
    highLabel: String = stringResource(R.string.symptom_scale_worst),
    tone: (Int) -> StatusTone,
) {
    val cs = MaterialTheme.colorScheme
    val accent = if (unrecorded) cs.onSurfaceVariant else tone(value).accent()
    // M3 Slider 对落在当前值上的点按不产生任何回调（dispatchRawDelta 值相等即丢弃，
    // onValueChangeFinished 也只在拖动收尾必发）——未作答态滑杆停在 0 时「直接点 0」永远无响应。
    // 两层配合：
    // ① 记录手势期最近原始值，onValueChangeFinished 按它兜底提交（点轨道跳转收尾）。
    // ② **父 Box（祖先）上的非消费点按旁听**：真点按且落点换算值恰等于当前值时，显式补一次提交，
    //    把该题标记为已作答。
    // ⚠️ v1.0.17–v1.0.46 的 ② 是把旁听层叠在滑杆**同级**（matchParentSize 覆盖层），
    // 这会让滑杆**完全收不到指针事件**、拖动彻底失效（详见下方 Box 处注释）。
    var gestureValue by remember { mutableFloatStateOf(value.toFloat()) }
    LaunchedEffect(value) { gestureValue = value.toFloat() }
    val currentValue by rememberUpdatedState(value)
    val sliderDesc = stringResource(R.string.forms_slider_a11y, label, value, range.last)
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
            if (unrecorded) {
                Text("—", style = DataLarge, color = accent)
            } else {
                Text("$value", style = DataLarge, color = accent)
            }
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
                Icon(Icons.Rounded.Remove, contentDescription = stringResource(R.string.symptom_decrease_one))
            }
            // 点按旁听挂在滑杆的**父 Box（祖先）**上，而不是像 v1.0.17–v1.0.46 那样在滑杆**同级**
            // 再叠一层 matchParentSize 透明覆盖层。原因（Compose 官方「事件调度和点击测试」）：
            // 同一层级上若有多个可参与手势的可组合项，**只有 z 序最高的那个算命中**；
            // 覆盖层挂了 pointerInput 就独占命中，滑杆自身收不到任何指针事件，拖动因此彻底失效。
            // 「从不 consume」救不回来——消费与否发生在命中测试**之后**。祖先与子节点同处一条命中链，
            // 子节点先拿到事件，父节点旁听不干扰子节点，这才是旁听手势的正确写法。
            Box(
                Modifier
                    .weight(1f)
                    .pointerInput(range) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val downX = down.position.x
                            var moved = false
                            while (true) {
                                val event = awaitPointerEvent()
                                // 按「相对按下点的累计位移」判定拖动，而不是逐事件位移：
                                // 慢拖时单个事件的位移可能始终小于触摸阈值，逐事件判会把拖动误判成点按。
                                if (event.changes.any {
                                        (it.position - down.position).getDistance() >
                                            viewConfiguration.touchSlop
                                    }
                                ) moved = true
                                if (event.changes.all { !it.pressed }) break
                            }
                            if (!moved && size.width > 0) {
                                val fraction = (downX / size.width).coerceIn(0f, 1f)
                                val tapped = (range.first + (range.last - range.first) * fraction)
                                    .roundToInt().coerceIn(range.first, range.last)
                                // 只补 M3 滑杆「不产生任何回调」的那一种情形：点按落在当前值上
                                // （dispatchRawDelta 值相等即丢弃）。落点值不同时 M3 自带的点轨道跳转
                                // 已经提交过，这里再写一次会与它的坐标换算打架（可能差一格），故不写。
                                // 此处读的是**本次手势前**的值：子节点（滑杆）在同一事件分发中先执行，
                                // 此刻重组尚未发生，因此 currentValue 正是旧值——恰好用作判据。
                                if (tapped == currentValue) {
                                    gestureValue = tapped.toFloat()
                                    onValueChange(tapped)
                                }
                            }
                        }
                    },
            ) {
                Slider(
                    value = value.toFloat(),
                    onValueChange = {
                        gestureValue = it
                        onValueChange(it.roundToInt().coerceIn(range.first, range.last))
                    },
                    onValueChangeFinished = {
                        onValueChange(gestureValue.roundToInt().coerceIn(range.first, range.last))
                    },
                    valueRange = range.first.toFloat()..range.last.toFloat(),
                    steps = (range.last - range.first - 1).coerceAtLeast(0),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(Size.touchMin)
                        .semantics { contentDescription = sliderDesc },
                )
            }
            FilledTonalIconButton(
                onClick = { onValueChange((value + 1).coerceAtMost(range.last)) },
                modifier = Modifier.size(Size.touchMin),
            ) {
                Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.symptom_increase_one))
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // W3：两端标签可点——滑杆对「点当前值」零回调的手势缺陷两轮修复后真机仍不可靠，
            // 「点「无」= 显式答 0」提供一条 100% 命中的可靠路径（点「最重」对称设 range.last）。
            Text(
                lowLabel,
                style = MaterialTheme.typography.labelSmall,
                color = if (value == range.first) cs.primary else cs.onSurfaceVariant,
                modifier = Modifier
                    .clickable { onValueChange(range.first) }
                    .padding(horizontal = Spacing.xs, vertical = Spacing.xxs),
            )
            Text(
                highLabel,
                style = MaterialTheme.typography.labelSmall,
                color = if (value == range.last) cs.primary else cs.onSurfaceVariant,
                modifier = Modifier
                    .clickable { onValueChange(range.last) }
                    .padding(horizontal = Spacing.xs, vertical = Spacing.xxs),
            )
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
                    Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }
}
