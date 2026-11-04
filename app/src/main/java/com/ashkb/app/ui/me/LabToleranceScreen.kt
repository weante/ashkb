package com.ashkb.app.ui.me

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle

import com.ashkb.app.R
import com.ashkb.app.domain.LabBoundaryTolerance
import com.ashkb.app.ui.components.AshkbTextField
import com.ashkb.app.ui.components.ScreenTopBar
import com.ashkb.app.ui.components.SectionCard
import com.ashkb.app.ui.theme.Size
import com.ashkb.app.ui.theme.Spacing

/**
 * v1.2.13（医学缺口③）：按化验项配置「临界」容差。
 *
 * 为什么值得一个页面：全局 5%（[com.ashkb.app.domain.ClinicalThresholds.LAB_NEAR_BOUNDARY_RATIO]）
 * 对不同量纲的化验意义完全不同——ESR（0–20 mm/h）的 5% 量程只有 ±0.5，
 * 血糖（3.9–6.1 mmol/L）的 5% 量程则是 ±0.11；同一档容差在一项上几乎全报临界、
 * 在另一项上一次都不报。让用户按项微调，是这个第四态真正有用的前提。
 *
 * 为什么**不提供**「关掉临界」：那等于把 v1.2.10 裁决项 5 整个作废。
 * 想让某项不显示临界，把它调到最严（0.5%）即可——语义上仍是「规则在，只是很严」。
 */
@Composable
internal fun LabToleranceScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val stored by LabBoundaryTolerance.observe(context).collectAsStateWithLifecycle()
    var draft by remember(stored) { mutableStateOf(stored.toPercentRows()) }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(title = stringResource(R.string.lab_tolerance_title), onBack = onBack)
        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item { ToleranceWhatCard() }
            item {
                ToleranceAddCard(
                    onAdd = { name, value ->
                        draft = (draft.filterNot { it.first.equals(name, true) } +
                            (name to value)).sortedBy { it.first.lowercase() }
                    },
                )
            }
            item {
                ToleranceListCard(
                    draft = draft,
                    onRemove = { name -> draft = draft.filterNot { it.first == name } },
                )
            }
            item {
                Button(
                    onClick = {
                        val config = draft.mapNotNull { (name, percent) ->
                            LabBoundaryTolerance.ratioOfPercent(percent)?.let { name to it }
                        }.toMap()
                        LabBoundaryTolerance.save(context, config)
                        onBack()
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = Size.touchMin),
                ) { Text(stringResource(R.string.lab_tolerance_save)) }
            }
            item {
                Text(
                    stringResource(R.string.lab_tolerance_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item { Spacer(Modifier.height(Spacing.xxl)) }
        }
    }
}

/** 「这是什么」说明卡：纯静态文案，抽出来只为让 [LabToleranceScreen] 不超函数长度上限。 */
@Composable
private fun ToleranceWhatCard() {
    SectionCard(title = stringResource(R.string.lab_tolerance_section_what)) {
        Text(
            stringResource(R.string.lab_tolerance_what_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 添加卡：**校验留在这里**，回调只收已校验的（名称, 百分比）。
 *
 * 这样 [LabToleranceScreen] 不必自己持有错误文案状态——错误是这张卡的局部现象，
 * 用户在别的卡片操作时它不该还挂着。
 *
 * v1.2.15（外部审查 §U-5）：内层回调改为**返回是否接收**。原来「清空输入」的判断在
 * [ToleranceAddRow] 里，只看百分比能不能解析成数字，于是名称留空（或百分比越界）这种
 * 「校验没过」的情况也会把用户填的两格一起擦掉——报错和丢稿同时发生，最坏的一种组合。
 * 现在清空的条件是这条路径**真的把条目加进去了**（返回值由本卡的 `else` 分支给出）。
 */
@Composable
private fun ToleranceAddCard(onAdd: (String, Float) -> Unit) {
    val context = LocalContext.current
    var error by remember { mutableStateOf<String?>(null) }
    SectionCard(title = stringResource(R.string.lab_tolerance_section_add)) {
        ToleranceAddRow(
            onAdd = { rawName, percent ->
                val name = rawName.trim()
                when {
                    name.isEmpty() -> {
                        error = context.getString(R.string.lab_tolerance_err_name)
                        false
                    }
                    LabBoundaryTolerance.ratioOfPercent(percent) == null -> {
                        error = context.getString(R.string.lab_tolerance_err_range)
                        false
                    }
                    else -> {
                        error = null
                        onAdd(name, percent ?: LabBoundaryTolerance.percentOf(LabBoundaryTolerance.DEFAULT_RATIO))
                        true
                    }
                }
            },
        )
        error?.let {
            Spacer(Modifier.height(Spacing.xs))
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

/** 已配置列表卡：空态说清楚「默认 5%」，避免用户以为默认值丢了。 */
@Composable
private fun ToleranceListCard(draft: List<Pair<String, Float>>, onRemove: (String) -> Unit) {
    SectionCard(title = stringResource(R.string.lab_tolerance_section_list)) {
        if (draft.isEmpty()) {
            Text(
                stringResource(R.string.lab_tolerance_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                draft.forEach { (name, percent) ->
                    ToleranceRow(
                        name = name,
                        percent = percent,
                        onRemove = { onRemove(name) },
                    )
                }
            }
        }
    }
}

/** 已保存配置 → 可编辑的 `名称 to 百分比` 列表（按名称排序，界面顺序稳定）。 */
private fun Map<String, Float>.toPercentRows(): List<Pair<String, Float>> =
    entries
        .map { (name, ratio) -> name to LabBoundaryTolerance.percentOf(ratio) }
        .sortedBy { it.first.lowercase() }

/**
 * 名称 + 百分比两格和「添加」按钮。
 *
 * `onAdd` 返回**是否接收**：只有真的加进去了才清空两格。这个返回值不能省成
 * 「在父级清空」——父级拿不到这两个 `rememberSaveable` 状态的写入口。
 *
 * 两格用 `rememberSaveable`（v1.2.15，外部审查 §U-5）：原来用 `remember`，转屏 /
 * 分屏 / 系统回收进程后就丢稿，而这一页正好是要「慢慢对照化验单一项项录入」的场景。
 */
@Composable
private fun ToleranceAddRow(onAdd: (String, Float?) -> Boolean) {
    var name by rememberSaveable { mutableStateOf("") }
    var percent by rememberSaveable { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        AshkbTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(R.string.lab_tolerance_field_name)) },
            singleLine = true,
        )
        AshkbTextField(
            value = percent,
            onValueChange = { percent = it },
            label = { Text(stringResource(R.string.lab_tolerance_field_percent)) },
            singleLine = true,
            supportingText = { Text(stringResource(R.string.lab_tolerance_field_percent_hint)) },
        )
        TextButton(
            onClick = {
                val parsed = percent.trim().toFloatOrNull()
                // 清空的条件是**父级接收了**，不是「百分比能解析」——后者在名称留空、
                // 百分比越界这两种校验失败的情况下同样成立，会把用户刚填的两格擦掉。
                if (onAdd(name, parsed)) {
                    name = ""
                    percent = ""
                }
            },
            modifier = Modifier.heightIn(min = Size.touchMin),
        ) { Text(stringResource(R.string.lab_tolerance_add)) }
    }
}

@Composable
private fun ToleranceRow(name: String, percent: Float, onRemove: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(
            "${trimPercent(percent)}%",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        IconButton(onClick = onRemove, modifier = Modifier.size(Size.touchMin)) {
            Icon(
                Icons.Rounded.Close,
                contentDescription = stringResource(R.string.lab_tolerance_remove),
                modifier = Modifier.size(Size.iconSm),
            )
        }
    }
}

/** 一位小数展示：5 显示 `5`，5.5 显示 `5.5`（避免浮点尾巴印成 `5.0000000001`）。 */
private fun trimPercent(percent: Float): String {
    val rounded = Math.round(percent * 10f) / 10f
    return if (rounded % 1f == 0f) rounded.toInt().toString() else rounded.toString()
}
