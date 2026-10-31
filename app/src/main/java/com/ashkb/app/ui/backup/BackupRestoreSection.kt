package com.ashkb.app.ui.backup

// v1.2.13（C1-①）：从原来的 1325 行 ui/backup/BackupScreen.kt 按分区拆出。
// 本文件只放：RestoreSection / UndoSnapshotBlock / RestorePendingBlock / verifyIssueText / RestoreResultBlock。
// 拆分是纯结构动作：签名、文案、状态来源一律未改。


import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ashkb.app.data.backup.BackupEngine
import com.ashkb.app.data.repo.BackupRepository
import com.ashkb.app.data.repo.PreRestoreSnapshot
import com.ashkb.app.R
import com.ashkb.app.ui.components.SectionCard
import com.ashkb.app.ui.theme.Clinical
import com.ashkb.app.ui.theme.Size
import com.ashkb.app.ui.theme.Spacing

/**
 * 恢复区（协议 §6 五步；v1.0.85 批次 10：从根级抽为 section）。
 *
 * 内部收集 `pendingRestore` / `pendingFileName` / `restoreResult` / `restoreMode` / `davUrl` 五条 Flow，
 * 并把**恢复口令**的输入状态收进来：口令同属逐字输入状态，留在根部等于每敲一个字重组整屏。
 * 入参只保留父级自己也要用的值（busy / 选好的文件）与两个"打开别处 UI"的回调
 * （文件选择器与远程列表 sheet 都在根级注册，见 [BackupScreen]）。
 */
@Composable
internal fun RestoreSection(
    vm: BackupViewModel,
    busy: Boolean,
    pickedBytes: ByteArray?,
    pickedName: String?,
    onPickFile: () -> Unit,
    onOpenDavPicker: () -> Unit,
) {
    val pending by vm.pendingRestore.collectAsStateWithLifecycle()
    val pendingName by vm.pendingFileName.collectAsStateWithLifecycle()
    val restoreResult by vm.restoreResult.collectAsStateWithLifecycle()
    val restoreMode by vm.restoreMode.collectAsStateWithLifecycle()
    val davUrl by vm.davUrl.collectAsStateWithLifecycle()
    var restorePass by remember { mutableStateOf("") }

    SectionCard(title = stringResource(R.string.backup_restore_title)) {
        Text(
            pickedName ?: stringResource(R.string.backup_no_file_selected),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.fillMaxWidth(),
            color = if (pickedName == null) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(Spacing.xs))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            OutlinedButton(
                onClick = onPickFile,
                enabled = !busy,
                modifier = Modifier.heightIn(min = Size.touchMin),
            ) { Text(stringResource(R.string.backup_select_file)) }
            OutlinedButton(
                // 远程列表的 sheet 开关与首次加载由父级一起做（见调用处）
                onClick = onOpenDavPicker,
                enabled = !busy && davUrl.isNotBlank(),
                modifier = Modifier.heightIn(min = Size.touchMin),
            ) { Text(stringResource(R.string.backup_restore_from_dav)) }
        }
        if (davUrl.isBlank()) {
            // X3：新机引导——无需先在本机生成任何备份，配置好 WebDAV 即可拉取服务器历史备份
            Text(
                stringResource(R.string.backup_dav_restore_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(Spacing.xs))
        SecretField(
            value = restorePass, onValueChange = { restorePass = it },
            label = stringResource(R.string.backup_file_password),
            supportingText = { Text(stringResource(R.string.backup_recovery_hint_for_restore)) },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(Spacing.sm))
        Button(
            onClick = {
                pickedBytes?.let { vm.verifyBackup(pickedName ?: "backup", it, restorePass) }
            },
            enabled = !busy && pickedBytes != null && restorePass.isNotBlank(),
        ) { Text(stringResource(R.string.backup_bypass_decrypt)) }

        if (pending != null) {
            RestorePendingBlock(
                pending = pending!!,
                pendingName = pendingName,
                restoreMode = restoreMode,
                busy = busy,
                onSetMode = { vm.setRestoreMode(it) },
                onRestore = { vm.doRestore() },
                onCancel = { vm.cancelRestore() },
            )
        }

        restoreResult?.let { r -> RestoreResultBlock(result = r) }
    }
}

/**
 * v1.2.11（缺陷工单 B07）：**用最近一次恢复前快照撤销**。
 *
 * 工单六条要求在这里逐条兑现：
 *  1. 只显示**最近一次**恢复留下的那份快照（[BackupViewModel.preRestoreSnapshot] 已是 `latest()`）。
 *  2. 撤销与普通恢复共用同一条链路（VM 里走 `verifyBytes` → `doRestore`），本块只负责
 *     「把口令交给 VM」+「确认」，不自己解密、不自己写库。
 *  3. 撤销本身也是一次恢复 ⇒ 它同样会先给当前状态留快照（`repo.restore()` 内建），
 *     所以「撤销的撤销」也有退路；界面上明说这一点，避免用户以为撤销不可逆。
 *  4. 口令**不保存**：快照用的是当时那次恢复的口令，用户必须重新输入；输入框只活在
 *     本块的 `remember` 里，离开即丢。
 *  5. 快照缺失/损坏/超限 ⇒ VM 报错，入口置灰或提示（`available` 为 false 时不给按钮）。
 *  6. 没有快照时**整块不渲染**（调用处 `if (snapshot != null)`），不留空入口。
 */
@Composable
internal fun UndoSnapshotBlock(
    snapshot: PreRestoreSnapshot,
    busy: Boolean,
    onUndo: (String) -> Unit,
) {
    var pass by remember { mutableStateOf("") }
    SectionCard(title = stringResource(R.string.backup_snapshot_title)) {
        // 先说清「撤销也是一次恢复」——这是工单第 3 条要求对用户明示的语义，
        // 否则用户会以为撤销是终局、不敢点。
        Text(
            stringResource(R.string.backup_snapshot_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.xs))
        // 快照的身份：文件名（含写盘时的日期时间）+ 大小。文件名是「什么时候恢复的」
        // 唯一可信来源（stamp 在写盘那一刻定下，不随后续时区/时钟变化）。
        Text(
            stringResource(R.string.backup_snapshot_meta, snapshot.name, formatFileSize(snapshot.sizeBytes)),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            stringResource(
                if (snapshot.available) R.string.backup_snapshot_status_ok
                else R.string.backup_snapshot_status_unavailable
            ),
            style = MaterialTheme.typography.bodySmall,
            color = if (snapshot.available) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.error,
        )
        if (!snapshot.available) {
            // 文件在、但读不了（损坏/权限/超限）：说清状态并**不给**按钮，
            // 而不是让用户点了才报错（工单第 6 条：不留空入口，也不给死按钮）。
            return@SectionCard
        }
        Spacer(Modifier.height(Spacing.xs))
        SecretField(
            value = pass, onValueChange = { pass = it },
            label = stringResource(R.string.backup_snapshot_password),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(Spacing.sm))
        OutlinedButton(
            onClick = { onUndo(pass) },
            enabled = !busy && pass.isNotBlank(),
            modifier = Modifier.heightIn(min = Size.touchMin),
        ) { Text(stringResource(R.string.backup_snapshot_undo)) }
    }
}

/**
 * 已通过自校验、等待用户拍板的备份块（原根级实现的 `pending != null` 分支，节点顺序不变）。
 *
 * 参数一律传值：口令与选择结果都不在这里，本块只负责"确认 → 选恢复语义 → 执行/取消"。
 */
@Composable
private fun RestorePendingBlock(
    pending: BackupRepository.DecryptedFile,
    pendingName: String?,
    restoreMode: BackupEngine.RestoreMode,
    busy: Boolean,
    onSetMode: (BackupEngine.RestoreMode) -> Unit,
    onRestore: () -> Unit,
    onCancel: () -> Unit,
) {
    Spacer(Modifier.height(Spacing.sm))
    Surface(
        Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Column(Modifier.padding(Spacing.md)) {
            Text(
                stringResource(R.string.backup_pending_selfcheck_ok, pendingName ?: ""),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                stringResource(R.string.backup_pending_created_at, pending.createdAt.take(19), pending.schemaVersion),
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(Spacing.sm))
            Text(
                stringResource(R.string.backup_prerestore_note) +
                    stringResource(R.string.backup_restore_overwrite_note),
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(Spacing.sm))
            // R2：恢复语义二选一（默认完整回滚，用户拍板）——文案把后果写清楚
            Text(
                stringResource(R.string.backup_restore_mode_title),
                style = MaterialTheme.typography.labelLarge,
            )
            Spacer(Modifier.height(Spacing.xs))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                FilterChip(
                    selected = restoreMode == BackupEngine.RestoreMode.FULL_ROLLBACK,
                    onClick = { onSetMode(BackupEngine.RestoreMode.FULL_ROLLBACK) },
                    label = { Text(stringResource(R.string.backup_restore_mode_full)) },
                )
                FilterChip(
                    selected = restoreMode == BackupEngine.RestoreMode.MERGE_TABLES,
                    onClick = { onSetMode(BackupEngine.RestoreMode.MERGE_TABLES) },
                    label = { Text(stringResource(R.string.backup_restore_mode_merge)) },
                )
            }
            Text(
                if (restoreMode == BackupEngine.RestoreMode.FULL_ROLLBACK)
                    stringResource(R.string.backup_restore_mode_full_desc)
                else stringResource(R.string.backup_restore_mode_merge_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Button(onClick = onRestore, enabled = !busy) {
                    Text(stringResource(R.string.backup_restore_execute))
                }
                OutlinedButton(onClick = onCancel) { Text(stringResource(R.string.common_cancel)) }
            }
        }
    }
}

/**
 * 校验差异的可读文案：优先用资源化条目（[BackupEngine.VerifyResult.rowIssues]），
 * 无资源条目时回落到中文原文 `rowDetails`。连接符走资源（中英不同）。
 */
@Composable
private fun verifyIssueText(result: BackupEngine.VerifyResult): String {
    val sep = stringResource(R.string.backup_list_separator)
    val context = LocalContext.current
    val localized = result.rowIssues.take(5).map { it.text(context) }
    return localized.ifEmpty { result.rowDetails.take(5) }.joinToString(sep)
}

/** 恢复执行结果行（行数是否全部对上）。 */
@Composable
private fun RestoreResultBlock(result: BackupEngine.VerifyResult) {
    Spacer(Modifier.height(Spacing.sm))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Icon(
            if (result.rowsOk) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
            contentDescription = null,
            tint = if (result.rowsOk) Clinical.colors.success else Clinical.colors.danger,
            modifier = Modifier.size(Size.iconSm),
        )
        Text(
            if (result.rowsOk) stringResource(R.string.backup_verify_pass, result.totalRows)
            else stringResource(R.string.backup_verify_incomplete, verifyIssueText(result)),
            style = MaterialTheme.typography.bodyMedium,
            color = if (result.rowsOk) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.error,
        )
    }
}
