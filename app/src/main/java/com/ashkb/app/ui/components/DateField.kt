package com.ashkb.app.ui.components

import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource

import com.ashkb.app.R
import com.ashkb.app.domain.DateInput

/**
 * v1.0.78（批次 4 收尾）：自由文本日期输入的**表单侧校验**（唯一入口，纯函数、可单测）。
 *
 * 背景（第三份审查报告 §六）：`DateInput` 已经能拒绝 `2026-13-45` / `2026-02-31` 这类**不存在**的日期，
 * 但表单此前把它当普通文本直接落库——非法日期要么静默写进去（此后趋势图 / PDF / 依从统计都带着
 * 一个假日期继续跑），要么只靠用户自己发现。这里把「能不能存」的判定与 UI 分开：
 * 判定归本对象，UI 只负责把结论画成红框与提示，两边**共用同一套规则**，
 * 不会出现「提示说格式不对、保存按钮却还能点」。
 */
internal object DateFieldRules {

    /** 必填日期：留空也算非法——日期列是 NOT NULL，空串会变成一条「没有日期的记录」。 */
    fun requiredOk(raw: String): Boolean = DateInput.normalizeOrNull(raw) != null

    /** 可选日期：**留空合法**（= 未填），一旦填了就必须是真实存在的日期。 */
    fun optionalOk(raw: String): Boolean = raw.isBlank() || DateInput.normalizeOrNull(raw) != null

    /** 落库值：合法 → 规范化成 ISO（`2026/7/31` → `2026-07-31`）；留空 / 非法 → null。 */
    fun toIsoOrNull(raw: String): String? = DateInput.normalizeOrNull(raw)
}

/**
 * 带即时校验的日期输入框：非法时红框 + 下方一行说明。
 *
 * 提示是**即时**的（不是「点了保存才报」）：这几个表单的日期初值都是今天或库里的既有值，
 * 只有用户自己改坏才会非法，此时立刻反馈最不容易让人困惑。
 */
@Composable
internal fun DateTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    required: Boolean = true,
) {
    val ok = if (required) DateFieldRules.requiredOk(value) else DateFieldRules.optionalOk(value)
    val hint: String? = when {
        ok -> null // 合法就不占一行提示（可选字段留空也走这里）
        value.isBlank() -> stringResource(R.string.date_field_required)
        else -> stringResource(R.string.date_field_invalid)
    }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier,
        singleLine = true,
        isError = hint != null,
        supportingText = if (hint != null) { { Text(hint) } } else null,
    )
}
