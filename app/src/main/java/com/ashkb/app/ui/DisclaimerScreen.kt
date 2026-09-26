package com.ashkb.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MedicalInformation
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.ashkb.app.R
import com.ashkb.app.domain.Disclaimer
import com.ashkb.app.ui.theme.Size
import com.ashkb.app.ui.theme.Spacing

/**
 * v1.0.63 C12：**首启免责声明门禁**。
 *
 * 首次启动时全屏展示，用户点「我已阅读并理解」前不进入应用本体——
 * 这是合规上的「显著位置声明」，此前只有条目级与 PDF 页脚声明
 * （那两处要脱离 App 被阅读，故保留为自足文本，不并入此处）。
 *
 * 要点清单文案来自 [Disclaimer.FIRST_LAUNCH_POINTS]（domain 唯一来源、有单测锁定）；
 * 本屏只负责标题 / 按钮 / 页脚等屏幕文案。
 */
@Composable
fun FirstLaunchDisclaimer(onAccept: () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.xl, vertical = Spacing.xxl),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Rounded.MedicalInformation,
                contentDescription = null,
                modifier = Modifier.size(Size.iconLg),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(Spacing.lg))
            Text(
                stringResource(R.string.disclaimer_gate_title),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Spacing.sm))
            Text(
                stringResource(R.string.disclaimer_gate_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(Spacing.xl))
            Disclaimer.FIRST_LAUNCH_POINTS.forEach { point ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Text("·", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                    Text(point, style = MaterialTheme.typography.bodyMedium)
                }
            }

            Spacer(Modifier.height(Spacing.lg))
            Text(
                stringResource(R.string.disclaimer_gate_footer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(Spacing.xl))
            Button(
                onClick = onAccept,
                modifier = Modifier.fillMaxWidth().height(Size.touchComfort),
            ) {
                Text(
                    stringResource(R.string.disclaimer_gate_accept),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}
