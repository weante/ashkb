package com.ashkb.app.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.ashkb.app.domain.Disclaimer
import com.ashkb.app.ui.theme.Spacing

/**
 * v1.0.63 C12：自动提示的**统一免责前缀**。
 *
 * 应用自动生成的健康提示（漏服处理 / 复诊准备 / 跨院化验等）一律用本组件在正文前后加声明，
 * 而不是各写各的「以…为准」——措辞由 [Disclaimer.PREFIX] 唯一提供，避免漂移。
 *
 * @param detailRes 可选的本提示专有补充（如「是否补服以药品说明书为准」）。
 *   刻意与统一前缀**同一段落**渲染，避免被当成两条互不相干的提示。
 */
@Composable
fun DisclaimerNote(
    detailRes: Int? = null,
    modifier: Modifier = Modifier,
) {
    val text = if (detailRes == null) {
        Disclaimer.PREFIX
    } else {
        Disclaimer.PREFIX + stringResource(detailRes)
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.fillMaxWidth().padding(top = Spacing.xs),
    )
}
