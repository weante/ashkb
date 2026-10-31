package com.ashkb.app.ui.backup

// v1.2.13（C1-①）：从原来的 1325 行 ui/backup/BackupScreen.kt 按分区拆出。
// 本文件只放：LedgerSection / LedgerRow / BusyFooter。
// 拆分是纯结构动作：签名、文案、状态来源一律未改。


import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ashkb.app.data.entity.BackupLedger
import com.ashkb.app.data.entity.LedgerStatus
import com.ashkb.app.data.entity.LedgerType
import com.ashkb.app.R
import com.ashkb.app.ui.components.DividerList
import com.ashkb.app.ui.components.LoadingBlock
import com.ashkb.app.ui.components.SectionCard
import com.ashkb.app.ui.theme.Clinical
import com.ashkb.app.ui.theme.Size
import com.ashkb.app.ui.theme.Spacing

/**
 * 台账区（默认折叠前 5 条，可展开全部；v1.0.85 批次 10：从根级抽为 section）。
 *
 * ledger 与"展开全部"开关都只被本区使用，收进来后新增一条备份记录只重组这张卡片。
 */
@Composable
internal fun LedgerSection(vm: BackupViewModel) {
    val ledger by vm.ledger.collectAsStateWithLifecycle()
    var showAllLedger by remember { mutableStateOf(false) }

    SectionCard(title = stringResource(R.string.backup_ledger_count, ledger.size)) {
        if (ledger.isEmpty()) {
            Text(stringResource(R.string.common_no_records), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            val shown = if (showAllLedger) ledger else ledger.take(5)
            DividerList(items = shown, key = { it.id }) { l ->
                LedgerRow(l)
            }
            if (ledger.size > 5) {
                TextButton(onClick = { showAllLedger = !showAllLedger }) {
                    val label = if (showAllLedger) {
                        stringResource(R.string.common_collapse)
                    } else {
                        stringResource(R.string.backup_view_all_count, ledger.size)
                    }
                    Text(label)
                }
            }
        }
    }
}

@Composable
private fun LedgerRow(l: BackupLedger) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${stringResource(LedgerType.fromKey(l.ledgerType).labelRes)} · ${l.target}",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                val ok = l.status == LedgerStatus.SUCCESS.name
                Icon(
                    imageVector = if (ok) Icons.Rounded.CheckCircle else Icons.Rounded.Cancel,
                    contentDescription = null,   // 装饰性：右侧文字已承载语义
                    modifier = Modifier.size(Size.iconSm),
                    tint = if (ok) Clinical.colors.success else Clinical.colors.danger,
                )
                Text(
                    if (ok) stringResource(R.string.common_success) else stringResource(R.string.common_failed),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (ok) Clinical.colors.success else Clinical.colors.danger,
                )
            }
        }
        Text(
            buildString {
                append(l.createdAt.take(19).replace("T", " "))
                l.fileName?.let { append(" $it") }
                l.rowTotal?.let { append(stringResource(R.string.backup_row_count, it)) }
                l.verifyOk?.let { if (it) append(stringResource(R.string.backup_dual_verify_passed)) }
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 长操作进度块（busy 期间出节点；v1.0.85 批次 10：stage 收进本块）。
 *
 * stage 是 WebDAV 备份的阶段文案，更新频率不如附件同步，但同样不必让整屏跟着重组；
 * busy 仍留在根部——它是整屏骨架共用的状态（8 处：6 个分区 + 演练 / 档案卡片 + 本块 + 远程列表 sheet）。
 */
@Composable
internal fun BusyFooter(vm: BackupViewModel, busy: Boolean) {
    val stage by vm.stage.collectAsStateWithLifecycle()
    if (busy) {
        LoadingBlock(label = if (stage.isNotBlank()) stage else stringResource(R.string.backup_processing))
    }
}
