package com.ashkb.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

import com.ashkb.app.R
import com.ashkb.app.ui.theme.DataLarge
import com.ashkb.app.ui.theme.Size
import com.ashkb.app.ui.theme.Spacing
import com.ashkb.app.ui.theme.StatusTone
import com.ashkb.app.ui.theme.accent
import kotlin.math.roundToInt
import androidx.compose.material3.OutlinedButton

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
    // v1.0.87（批次 12）：确认按钮的文案可覆盖。默认仍是「删除」，但**语义不是删除**的动作
    // （例如复诊项目只有「停用」、历史记录保留）必须传自己的动词——否则标题问「停用该项目？」
    // 而按钮写「删除」，用户会以为整条项目连同历史都被删掉（维护者真机反馈）。
    confirmLabel: String? = null,
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
                    Text(confirmLabel ?: stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }
}


/**
 * 输入框四种视觉状态解析出的四个值（见 [AshkbTextField]）。
 *
 * 单独抽出来有两个原因：① 状态优先级只在一处表达，改配色不会漏掉某个分支；
 * ② detekt `CyclomaticComplexMethod` 阈值 15，`AshkbTextField` 本身已经因为
 * 「两处 when + 三处 CompositionLocalProvider + 装饰盒」压到 19，把这三组 when
 * 挪出来是最小改动且不改变任何行为。
 */
private data class FieldVisuals(
    val border: Color,
    val borderWidth: Dp,
    val container: Color,
    val content: Color,
)

/**
 * 描边：错误态优先于聚焦态（错误必须始终看得见，不能"聚焦时被品牌蓝盖掉"）。
 * 未聚焦时画一条**透明**边而不是不画：两态边框宽度不同（1 vs 1.5dp），
 * 若未聚焦时完全不画，聚焦瞬间容器会因多出 0.5dp 边框而整体挪半像素。
 *
 * 底：未聚焦 surface-2（凹陷），聚焦升白。禁用态用 onSurface 4% —— 与 M3 的禁用容器
 * 同源，避免"灰得比 enabled 还显眼"。
 */
private fun fieldVisuals(
    cs: ColorScheme,
    enabled: Boolean,
    focused: Boolean,
    isError: Boolean,
): FieldVisuals = FieldVisuals(
    border = when {
        isError -> cs.error
        focused -> cs.primary
        else -> Color.Transparent
    },
    borderWidth = if (isError || focused) 1.5.dp else 1.dp,
    container = when {
        !enabled -> cs.onSurface.copy(alpha = 0.04f)
        focused -> cs.surface
        else -> cs.surfaceVariant
    },
    content = when {
        !enabled -> cs.onSurface.copy(alpha = 0.38f)
        isError -> cs.error
        else -> cs.onSurface
    },
)

/**
 * v1.2.10（UI 2.0 批次 C「换件」）：全 App **唯一的文本输入框形态**——「凹陷填充」。
 *
 * ### 为什么要换掉 `OutlinedTextField`
 *
 * 旧形态（M3 OutlinedTextField）是"描边 + 浮动 label"：未聚焦时只有一圈灰线，
 * 聚焦后 label 从框内**弹到框边**。两个后果：① 表单看上去是一摞空框，凹陷/层级全无；
 * ② 聚焦瞬间 label 位移 → 整行版式跳动，长表单里连续填十几个字段时尤其晃眼。
 * 设计稿 §5.9 的裁决：换成「凹陷填充」——**未聚焦 = surface-2 实心底、无边框**，
 * **聚焦 = 升白 + 1.5dp brand 描边**，label **常驻顶部不再浮动**。
 *
 * ### 与设计稿的两处刻意偏差（都有据可查，勿"顺手改回"）
 *
 * 1. **圆角取 12（`shapes.medium`）而不是交接包 §3.9 写的 8（`shapes.small`）**：
 *    交接包自身结尾写着「如有疑问对照设计文档 `ashkb-ui2-clarity.html` 相应章节」，
 *    而设计稿有两处都指到 12——§4.2 圆角刻度表「12 · 按钮/输入框/行」、
 *    §5.9 示例说明「高 56、圆角 12」。两处对一处，且刻度表是体系级定义，故以设计稿为准。
 * 2. **label 取 12sp 而不是示例说明写的 11.5sp**：设计稿 §4.4 自己给的 label 系列是
 *    `15 / 13 / 12`，且 `ui/theme/Type.kt` 有一条硬规则「12sp 是硬下限，禁止再小」。
 *    11.5sp 与该文档自身的字阶、与项目既有红线都不相容，故取 labelSmall（12sp）。
 *
 * ### 为什么不是"包一层 `TextField`"
 *
 * M3 的 `TextField` 自带下划线指示器、自带浮动 label、自带容器色，要改造成本形态得同时
 * 关掉三样并再叠 `decorationBox`——绕一圈还是自己画。这里直接用 `BasicTextField`
 * （纯文本编辑能力，零容器装饰），容器、边框、label、placeholder 全部由本组件画，
 * 读数、光标色、无障碍语义仍然走 Compose 的既有实现。
 *
 * ### 参数是**按调用点实际用到的子集**裁的
 *
 * 迁移时统计过 18 个文件 99 处调用，只用到：`value / onValueChange / label / modifier /
 * enabled / readOnly / singleLine / maxLines / minLines / isError / placeholder /
 * trailingIcon / supportingText / visualTransformation / keyboardOptions`。
 * 没有一处传 `colors / shape / contentPadding / leadingIcon / prefix / suffix /
 * keyboardActions / interactionSource / cursorBrush`，故这些**不收**——少一个参数就少一种
 * 让调用点写出不一致形态的可能。
 *
 * ⚠️ 调用点一律用本组件；**禁止**再直接 `import androidx.compose.material3.OutlinedTextField`
 * （`OutlinedTextField` 在本包内不存在同名遮蔽，重复引入会退回旧形态且不留痕迹）。
 */
@Composable
fun AshkbTextField(
    value: String,
    onValueChange: (String) -> Unit,
    // `modifier` 放在**第一个可选参数**位（Compose 惯例，也让 lint 的 ModifierParameter 不报警）。
    // 99 处调用点里没有一处按位置传第 3 个实参，故调序零风险。
    modifier: Modifier = Modifier,
    label: @Composable (() -> Unit)? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    isError: Boolean = false,
    placeholder: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    // 设计稿 §5.9 的值字号是 15px，对应本项目的 `bodyMedium`（15sp）；旧 `OutlinedTextField`
    // 默认吃 `LocalTextStyle`（本 App 里是 16sp bodyLarge）。99 处调用**没有一处**自己传
    // `textStyle`，故这里定成 bodyMedium 即为设计稿口径，且全 App 输入值字号统一。
    textStyle: TextStyle = MaterialTheme.typography.bodyMedium,
) {
    val cs = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()

    // 四种视觉状态的优先级集中在 [fieldVisuals] 一处（错误 > 聚焦 > 常态，禁用压过全部）。
    val visuals = fieldVisuals(cs = cs, enabled = enabled, focused = focused, isError = isError)
    val shape = MaterialTheme.shapes.medium

    Column(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(visuals.container)
                .border(visuals.borderWidth, visuals.border, shape)
                // 高度下限 56（Size.touchComfort）落在 padding **之外**，即"整框"至少 56dp。
                // 实际高度由内容决定：label 一行 + 值一行 + 上下内边距，中文行高比设计稿的
                // 西文行高松（CJK 1.5–1.7 倍行高，见 Type.kt），因此浅色长表单里会比
                // 设计稿的 56 略高几 dp —— 这是下限不是上限，不为凑数去压行高。
                .heightIn(min = Size.touchComfort)
                .padding(start = Spacing.lg, end = Spacing.lg, top = 10.dp, bottom = Spacing.md),
        ) {
            if (label != null) {
                // label 常驻顶部：这里**不使用** BasicTextField 的 label 槽（那个槽的语义就是
                // "会浮动"）。用 LocalTextStyle / LocalContentColor 把调用点传进来的
                // `{ Text(...) }` 染成 labelSmall + ink-2 —— 调用点因此完全不用改文案写法。
                CompositionLocalProvider(
                    LocalTextStyle provides MaterialTheme.typography.labelSmall,
                    LocalContentColor provides cs.onSurfaceVariant,
                ) { label() }
                Spacer(Modifier.height(Spacing.xxs))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.weight(1f),
                    enabled = enabled,
                    readOnly = readOnly,
                    singleLine = singleLine,
                    maxLines = maxLines,
                    minLines = minLines,
                    textStyle = textStyle.copy(color = visuals.content),
                    cursorBrush = SolidColor(cs.primary),
                    visualTransformation = visualTransformation,
                    keyboardOptions = keyboardOptions,
                    interactionSource = interactionSource,
                    decorationBox = { innerTextField ->
                        Box {
                            if (value.isEmpty() && placeholder != null) {
                                // placeholder 与正文同字号同基线，只换色 —— 否则空框里的提示
                                // 会比用户真填进去的字小一圈，像另一种字段。
                                CompositionLocalProvider(
                                    LocalContentColor provides cs.onSurfaceVariant,
                                ) { placeholder() }
                            }
                            innerTextField()
                        }
                    },
                )
                if (trailingIcon != null) {
                    Spacer(Modifier.size(Spacing.sm))
                    trailingIcon()
                }
            }
        }
        if (supportingText != null) {
            Spacer(Modifier.height(Spacing.xs))
            CompositionLocalProvider(
                LocalTextStyle provides MaterialTheme.typography.bodySmall,
                LocalContentColor provides if (isError) cs.error else cs.onSurfaceVariant,
            ) { supportingText() }
        }
    }
}

/**
 * 一行宽按钮（v1.2.3）：用于「批量填一个值」这类**一次声明**动作。
 *
 * ⚠️ 与 `ScoreInput` 两端标签的分工要分清：
 *  · `ScoreInput` 底部的「无」「最严重」改的是**单题**的值（v1.0.18 起就有，本次未动）；
 *  · 本组件批量补**未答的题**（BASDAI 弹窗顶部）。
 * 二者语义不同 —— 合起来会让「系统替你答」与「你答了某题」在代码上无法区分。
 *
 * 抽成组件还有个现实原因：`BasdaiDialog` 已贴着 detekt `LongMethod` 阈值（78/80），
 * 调用点因此必须只占**一行**。
 */
@Composable
fun FillRemainingAction(label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = Size.touchMin),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}
