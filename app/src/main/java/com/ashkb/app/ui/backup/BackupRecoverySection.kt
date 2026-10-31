package com.ashkb.app.ui.backup

// v1.2.13（C1-①）：从原来的 1325 行 ui/backup/BackupScreen.kt 按分区拆出。
// 本文件只放：RecoveryCodeSection / RecoveryCodeDialog / RecoveryRegenDialog。
// 拆分是纯结构动作：签名、文案、状态来源一律未改。


import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ashkb.app.R
import com.ashkb.app.ui.components.SectionCard
import com.ashkb.app.ui.components.SecureWindow
import com.ashkb.app.ui.theme.Size
import com.ashkb.app.ui.theme.Spacing

/**
 * 备份恢复码区（v1.0.85 批次 10：从根级抽为 section）。
 *
 * 本 section 内部收集 `recoveryCodeSet` / `recoveryReveal`，并持有两个弹窗的可见性状态与
 * "生成/查看完成 → 弹窗展示"的那条 effect——这三样都只服务本区，原先摆在根部，
 * 让一次"恢复码明文出现"的事件也要带着整屏重组一次。
 */
@Composable
internal fun RecoveryCodeSection(vm: BackupViewModel, busy: Boolean) {
    val recoveryCodeSet by vm.recoveryCodeSet.collectAsStateWithLifecycle()
    val recoveryReveal by vm.recoveryReveal.collectAsStateWithLifecycle()
    var showRecoveryDialog by remember { mutableStateOf<String?>(null) }
    var showRegenConfirm by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current

    // A2：恢复码生成/查看完成 → 弹窗展示（分组等宽字体 + 抄写提示）
    LaunchedEffect(recoveryReveal) {
        recoveryReveal?.let {
            showRecoveryDialog = it
            vm.consumeRecoveryReveal()
        }
    }

    SectionCard(title = stringResource(R.string.backup_recovery_section)) {
        Text(
            stringResource(if (recoveryCodeSet) R.string.backup_recovery_set_note
            else R.string.backup_recovery_not_set_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            if (!recoveryCodeSet) {
                Button(
                    onClick = { vm.generateRecoveryCode() },
                    enabled = !busy,
                    modifier = Modifier.heightIn(min = Size.touchMin),
                ) { Text(stringResource(R.string.backup_recovery_generate)) }
            } else {
                OutlinedButton(
                    onClick = { vm.revealRecoveryCode() },
                    enabled = !busy,
                    modifier = Modifier.heightIn(min = Size.touchMin),
                ) { Text(stringResource(R.string.backup_recovery_view)) }
                OutlinedButton(
                    onClick = { showRegenConfirm = true },
                    enabled = !busy,
                    modifier = Modifier.heightIn(min = Size.touchMin),
                ) { Text(stringResource(R.string.backup_recovery_regenerate)) }
            }
        }

        // A2：恢复码展示弹窗（生成 / 查看共用）——等宽分组显示 + 可选中复制
        showRecoveryDialog?.let { code ->
            RecoveryCodeDialog(
                code = code,
                onDismiss = { showRecoveryDialog = null },
                onCopy = { clipboard.setText(AnnotatedString(code)) },
            )
        }
        if (showRegenConfirm) {
            RecoveryRegenDialog(
                onDismiss = { showRegenConfirm = false },
                onConfirm = {
                    showRegenConfirm = false
                    vm.generateRecoveryCode()
                },
            )
        }
    }
}

/** 恢复码明文弹窗：AlertDialog 是独立窗口，故窗口内再禁一次截屏（见 SecureWindow 注释）。 */
@Composable
private fun RecoveryCodeDialog(code: String, onDismiss: () -> Unit, onCopy: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_recovery_dialog_title)) },
        text = {
            // v1.0.73：恢复码是全库唯一的离线后门，展示期间禁截屏（AlertDialog 是独立窗口）
            SecureWindow()
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Text(
                    stringResource(R.string.backup_recovery_dialog_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    SelectionContainer {
                        Text(
                            code,
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(Spacing.md),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.backup_recovery_dialog_saved))
            }
        },
        dismissButton = {
            TextButton(onClick = onCopy) {
                Text(stringResource(R.string.backup_recovery_dialog_copy))
            }
        },
    )
}

/** 重新生成恢复码的二次确认弹窗（恢复码是全库后门，重建必须让人先停一下）。 */
@Composable
private fun RecoveryRegenDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_recovery_regenerate)) },
        text = { Text(stringResource(R.string.backup_recovery_regen_confirm)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.backup_recovery_regenerate))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        },
    )
}
