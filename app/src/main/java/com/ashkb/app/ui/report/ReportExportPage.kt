package com.ashkb.app.ui.report

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ashkb.app.R
import com.ashkb.app.ui.components.NavRow
import com.ashkb.app.ui.components.SectionCard

/**
 * v1.2.0：报表「导出 / 备份」页。
 *
 * ### 为什么单独成文件
 * 原先与概览、趋势、周月报同处ReportScreen.kt（809 行 ✅ 超过本项目约定的 ~500 行上限 ✅）。
 * 本页只依赖三个参数（[ReportViewModel] ✅ busy 标记 ✅ [android.content.Context] ✅），
 * 与其余三页**零耦合** ✅ 所以可以整页搬出 ✅ 不需要改任何调用点。
 *
 * ### 为什么没有为了"减小文件"而改函数结构
 * 本项目 detekt 基线按**文件 + 完整签名**记账 ✅ 搬动已登记的函数会让基线失配 ✅
 * 而本页原本**不在基线里** ✅ 所以搬它**零基线成本** ✅ —— 这是最省事的一刀。
 */
// ======================= 报告导出 =======================

@Composable
internal fun ExportPage(vm: ReportViewModel, busy: Boolean, context: android.content.Context, onOpenBackup: () -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { Spacer(Modifier.height(12.dp)) }

        item {
            SectionCard(title = stringResource(R.string.report_pdf_title)) {
                Text(
                    stringResource(R.string.report_pdf_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                val shareTitle = stringResource(R.string.report_share_action)
                Button(
                    onClick = {
                        vm.generateReportPdf(
                            onReady = { intent ->
                                runCatching { context.startActivity(Intent.createChooser(intent, shareTitle)) }
                                    .onFailure { context.getString(R.string.report_share_fail, it.message) }
                            },
                            onError = { vm.reportError(it) },
                        )
                    },
                    enabled = !busy,
                ) { Text(if (busy) stringResource(R.string.backup_generating_dots) else stringResource(R.string.report_generate_pdf)) }
            }
        }

        item {
            SectionCard(title = stringResource(R.string.emergency_card_pdf_title)) {
                Text(
                    stringResource(R.string.emergency_pdf_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                val shareTitle = stringResource(R.string.emergency_share_card)
                OutlinedButton(
                    onClick = {
                        vm.generateEmergencyCardPdf(
                            onReady = { intent ->
                                runCatching { context.startActivity(Intent.createChooser(intent, shareTitle)) }
                                    .onFailure { context.getString(R.string.report_share_fail, it.message) }
                            },
                            onError = { vm.reportError(it) },
                        )
                    },
                    enabled = !busy,
                ) { Text(stringResource(R.string.emergency_generate_pdf)) }
            }
        }

        item {
            NavRow(
                icon = Icons.Rounded.Backup,
                title = stringResource(R.string.backup_section_title),
                subtitle = stringResource(R.string.backup_section_subtitle),
                onClick = onOpenBackup,
            )
        }

        item { Spacer(Modifier.height(20.dp)) }
    }
}
