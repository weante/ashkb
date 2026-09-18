package com.ashkb.app.ui.backup

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.lifecycle.compose.collectAsStateWithLifecycle

import com.ashkb.app.R
import com.ashkb.app.data.backup.BackupEngine
import com.ashkb.app.data.backup.WebDavClient
import com.ashkb.app.data.entity.BackupLedger
import com.ashkb.app.data.entity.LedgerStatus
import com.ashkb.app.data.entity.LedgerType
import com.ashkb.app.ui.GlobalMessages
import com.ashkb.app.ui.components.DividerList
import com.ashkb.app.ui.components.LoadingBlock
import com.ashkb.app.ui.components.ScreenTopBar
import com.ashkb.app.ui.components.SectionCard
import com.ashkb.app.ui.theme.Clinical
import com.ashkb.app.ui.theme.Size
import com.ashkb.app.ui.theme.Spacing

/** U6：口令框统一密码键盘——Password 类型让输入法（含微信输入法）关闭候选词/联想。 */
private val PassKeyboard = KeyboardOptions(
    keyboardType = KeyboardType.Password,
    autoCorrect = false,
)

/** P4 R20 备份与数据自主页（协议 §3–§6）。 */
@Composable
fun BackupScreen(vm: BackupViewModel, onBack: () -> Unit) {
    val busy by vm.busy.collectAsStateWithLifecycle()
    val stage by vm.stage.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val pending by vm.pendingRestore.collectAsStateWithLifecycle()
    val pendingName by vm.pendingFileName.collectAsStateWithLifecycle()
    val restoreResult by vm.restoreResult.collectAsStateWithLifecycle()
    val restoreMode by vm.restoreMode.collectAsStateWithLifecycle()
    val ledger by vm.ledger.collectAsStateWithLifecycle()
    val davUrl by vm.davUrl.collectAsStateWithLifecycle()
    val davUser by vm.davUser.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var backupPass by remember { mutableStateOf("") }
    var restorePass by remember { mutableStateOf("") }
    var pickedBytes by remember { mutableStateOf<ByteArray?>(null) }
    var pickedName by remember { mutableStateOf<String?>(null) }
    var showDavSheet by remember { mutableStateOf(false) }
    var showDavPickSheet by remember { mutableStateOf(false) }
    var showAllLedger by remember { mutableStateOf(false) }
    val davPicked by vm.davPicked.collectAsStateWithLifecycle()

    // ---- A2：备份恢复码 ----
    val recoveryCodeSet by vm.recoveryCodeSet.collectAsStateWithLifecycle()
    val recoveryReveal by vm.recoveryReveal.collectAsStateWithLifecycle()
    var showRecoveryDialog by remember { mutableStateOf<String?>(null) }
    var showRegenConfirm by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current

    val pickRestoreFile = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }.getOrNull()?.let { bytes ->
                pickedBytes = bytes
                pickedName = queryName(context, uri)
                vm.cancelRestore()
            }
        }
    }
    val pickImportJson = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }.getOrNull()?.let { vm.importProfileJson(it) }
        }
    }

    // 恢复/备份是耗时操作，阻塞式弹窗尤其伤手感——改走全局 Snackbar（自带无障碍播报）
    LaunchedEffect(message) {
        message?.let {
            GlobalMessages.post(it)
            vm.clearMessage()
        }
    }

    // W4：远程备份下载完成 → 等价本地选好文件，关 sheet 走五步恢复
    LaunchedEffect(davPicked) {
        davPicked?.let { (name, bytes) ->
            pickedBytes = bytes
            pickedName = name
            vm.cancelRestore()
            vm.consumeDavPicked()
            showDavPickSheet = false
        }
    }

    // A2：恢复码生成/查看完成 → 弹窗展示（分组等宽字体 + 抄写提示）
    LaunchedEffect(recoveryReveal) {
        recoveryReveal?.let {
            showRecoveryDialog = it
            vm.consumeRecoveryReveal()
        }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(title = stringResource(R.string.me_backup_section), onBack = onBack)

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Spacer(Modifier.height(Spacing.xs))

            // ---- 加密说明 ----
            SectionCard(title = stringResource(R.string.backup_encryption_title)) {
                Text(
                    stringResource(R.string.backup_file_note) +
                        stringResource(R.string.backup_encryption_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // ---- 备份恢复码（A2）----
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
                    AlertDialog(
                        onDismissRequest = { showRecoveryDialog = null },
                        title = { Text(stringResource(R.string.backup_recovery_dialog_title)) },
                        text = {
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
                            TextButton(onClick = { showRecoveryDialog = null }) {
                                Text(stringResource(R.string.backup_recovery_dialog_saved))
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { clipboard.setText(AnnotatedString(code)) }) {
                                Text(stringResource(R.string.backup_recovery_dialog_copy))
                            }
                        },
                    )
                }
                if (showRegenConfirm) {
                    AlertDialog(
                        onDismissRequest = { showRegenConfirm = false },
                        title = { Text(stringResource(R.string.backup_recovery_regenerate)) },
                        text = { Text(stringResource(R.string.backup_recovery_regen_confirm)) },
                        confirmButton = {
                            TextButton(onClick = {
                                showRegenConfirm = false
                                vm.generateRecoveryCode()
                            }) { Text(stringResource(R.string.backup_recovery_regenerate)) }
                        },
                        dismissButton = {
                            TextButton(onClick = { showRegenConfirm = false }) {
                                Text(stringResource(R.string.common_cancel))
                            }
                        },
                    )
                }
            }

            // ---- 本机全量备份 ----
            SectionCard(title = stringResource(R.string.backup_local_full)) {
                OutlinedTextField(
                    value = backupPass, onValueChange = { backupPass = it },
                    label = { Text(stringResource(R.string.backup_password_field)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = PassKeyboard,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(Spacing.sm))
                val shareTitle = stringResource(R.string.backup_save_share)
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Button(
                        onClick = {
                            vm.backupLocal(backupPass) { intent ->
                                runCatching {
                                    context.startActivity(Intent.createChooser(intent, shareTitle))
                                }
                            }
                        },
                        enabled = !busy && backupPass.length >= 6,
                        modifier = Modifier.heightIn(min = Size.touchMin),
                    ) { Text(stringResource(R.string.backup_local_generate)) }
                    OutlinedButton(
                        onClick = { vm.backupWebdav(backupPass) },
                        enabled = !busy && backupPass.length >= 6,
                        modifier = Modifier.heightIn(min = Size.touchMin),
                    ) { Text(stringResource(R.string.backup_to_dav)) }
                }
                Text(
                    stringResource(R.string.backup_local_storage_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // ---- WebDAV 配置（表单收进 sheet，页面只留状态与入口） ----
            SectionCard(
                title = stringResource(R.string.backup_dav_section_title),
                subtitle = if (davUrl.isNotBlank()) stringResource(R.string.backup_dav_configured, davUrl, davUser) else null,
            ) {
                OutlinedButton(
                    onClick = { showDavSheet = true },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = Size.touchMin),
                ) { Text(if (davUrl.isNotBlank()) stringResource(R.string.backup_modify_dav) else stringResource(R.string.backup_configure_dav)) }
            }

            // ---- 恢复（协议 §6 五步） ----
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
                        onClick = { pickRestoreFile.launch(arrayOf("*/*")) },
                        enabled = !busy,
                        modifier = Modifier.heightIn(min = Size.touchMin),
                    ) { Text(stringResource(R.string.backup_select_file)) }
                    OutlinedButton(
                        onClick = {
                            showDavPickSheet = true
                            vm.loadDavBackups()
                        },
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
                OutlinedTextField(
                    value = restorePass, onValueChange = { restorePass = it },
                    label = { Text(stringResource(R.string.backup_file_password)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = PassKeyboard,
                    singleLine = true,
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
                    Spacer(Modifier.height(Spacing.sm))
                    Surface(
                        Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Column(Modifier.padding(Spacing.md)) {
                            Text(
                                "已通过文件自校验：${pendingName ?: ""}",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                "备份于 ${pending!!.createdAt.take(19)}（schema v${pending!!.schemaVersion}）",
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
                                    onClick = { vm.setRestoreMode(BackupEngine.RestoreMode.FULL_ROLLBACK) },
                                    label = { Text(stringResource(R.string.backup_restore_mode_full)) },
                                )
                                FilterChip(
                                    selected = restoreMode == BackupEngine.RestoreMode.MERGE_TABLES,
                                    onClick = { vm.setRestoreMode(BackupEngine.RestoreMode.MERGE_TABLES) },
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
                                Button(onClick = { vm.doRestore() }, enabled = !busy) {
                                    Text(stringResource(R.string.backup_restore_execute))
                                }
                                OutlinedButton(onClick = { vm.cancelRestore() }) { Text(stringResource(R.string.common_cancel)) }
                            }
                        }
                    }
                }

                restoreResult?.let { r ->
                    Spacer(Modifier.height(Spacing.sm))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        Icon(
                            if (r.rowsOk) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
                            contentDescription = null,
                            tint = if (r.rowsOk) Clinical.colors.success else Clinical.colors.danger,
                            modifier = Modifier.size(Size.iconSm),
                        )
                        Text(
                            if (r.rowsOk) stringResource(R.string.backup_verify_pass, r.totalRows)
                            else "校验未全部通过：${r.rowDetails.take(5).joinToString("；")}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (r.rowsOk) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            // ---- 恢复演练 ----
            SectionCard(title = stringResource(R.string.backup_drill_title)) {
                Text(
                    stringResource(R.string.backup_drill_flow_note) +
                        stringResource(R.string.backup_drill_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Spacing.sm))
                Button(onClick = { vm.drill() }, enabled = !busy) { Text(stringResource(R.string.backup_run_drill)) }
            }

            // ---- 档案 JSON ----
            SectionCard(title = stringResource(R.string.backup_profile_json_title)) {
                Text(
                    stringResource(R.string.backup_profile_json_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Spacing.sm))
                val shareTitle = stringResource(R.string.backup_profile_save_share)
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    OutlinedButton(
                        onClick = {
                            vm.exportProfileJson { intent ->
                                runCatching {
                                    context.startActivity(Intent.createChooser(intent, shareTitle))
                                }
                            }
                        },
                        enabled = !busy,
                    ) { Text(stringResource(R.string.backup_export_profile)) }
                    OutlinedButton(
                        onClick = { pickImportJson.launch(arrayOf("*/*")) },
                        enabled = !busy,
                    ) { Text(stringResource(R.string.backup_import_profile)) }
                }
            }

            // ---- 台账（默认折叠前 5 条，可展开全部） ----
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
                            Text(if (showAllLedger) stringResource(R.string.common_collapse) else stringResource(R.string.backup_view_all_count, ledger.size))
                        }
                    }
                }
            }

            if (busy) {
                LoadingBlock(label = if (stage.isNotBlank()) stage else stringResource(R.string.backup_processing))
            }
            Spacer(Modifier.height(Spacing.xxl))
        }
    }

    if (showDavSheet) {
        WebDavSheet(
            vm = vm,
            initialUrl = davUrl,
            initialUser = davUser,
            onDismiss = { showDavSheet = false },
        )
    }

    if (showDavPickSheet) {
        DavBackupPickerSheet(vm = vm, busy = busy, onDismiss = { showDavPickSheet = false })
    }
}

@Composable
private fun LedgerRow(l: BackupLedger) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${LedgerType.fromKey(l.ledgerType).label} · ${l.target}",
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
                l.fileName?.let { append("　$it") }
                l.rowTotal?.let { append(stringResource(R.string.backup_row_count, it)) }
                l.verifyOk?.let { if (it) append(stringResource(R.string.backup_dual_verify_passed)) }
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ===== W4：WebDAV 远程备份选择 sheet =====
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DavBackupPickerSheet(vm: BackupViewModel, busy: Boolean, onDismiss: () -> Unit) {
    val backups by vm.davBackups.collectAsStateWithLifecycle()
    val stage by vm.stage.collectAsStateWithLifecycle()
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg)
                .padding(bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(stringResource(R.string.backup_dav_pick_title), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.backup_dav_pick_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = { vm.loadDavBackups() }, enabled = !busy) {
                Text(stringResource(R.string.backup_dav_pick_refresh))
            }
            if (busy) {
                LoadingBlock(
                    label = if (stage.isNotBlank()) stage
                    else stringResource(R.string.backup_dav_pick_loading),
                )
            }
            if (!busy && backups.isEmpty()) {
                Text(
                    stringResource(R.string.backup_dav_pick_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (backups.isNotEmpty()) {
                DividerList(items = backups, key = { it.name }) { f ->
                    DavBackupRow(f) { vm.downloadDavBackup(f.name) }
                }
            }
        }
    }
}

@Composable
private fun RowScope.DavBackupRow(f: WebDavClient.DavBackupFile, onClick: () -> Unit) {
    Column(
        Modifier
            .weight(1f)
            .clickable(onClick = onClick)
            .padding(vertical = Spacing.xs),
    ) {
        Text(f.name, style = MaterialTheme.typography.titleSmall)
        if (f.size >= 0) {
            Text(
                formatFileSize(f.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 字节大小格式化（固定 Locale.US，避免部分 locale 小数点变逗号）。 */
private fun formatFileSize(bytes: Long): String = when {
    bytes >= 1_048_576L -> String.format(java.util.Locale.US, "%.1f MB", bytes / 1_048_576.0)
    bytes >= 1_024L -> String.format(java.util.Locale.US, "%.0f KB", bytes / 1_024.0)
    else -> "$bytes B"
}

// ===== WebDAV 配置 sheet（密码不回显，重新输入） =====
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WebDavSheet(vm: BackupViewModel, initialUrl: String, initialUser: String, onDismiss: () -> Unit) {
    var davUrl by remember { mutableStateOf(initialUrl) }
    var davUser by remember { mutableStateOf(initialUser) }
    var davPass by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg)
                .padding(bottom = Spacing.xl)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(stringResource(R.string.backup_dav_config_title), style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = davUrl, onValueChange = { davUrl = it },
                label = { Text(stringResource(R.string.backup_dav_url_field)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = davUser, onValueChange = { davUser = it },
                label = { Text(stringResource(R.string.backup_dav_username_field)) }, modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = davPass, onValueChange = { davPass = it },
                label = { Text(stringResource(R.string.backup_dav_password_field)) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = PassKeyboard,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    vm.saveAndProbeWebdav(davUrl, davUser, davPass)
                    onDismiss()
                },
                enabled = davUrl.isNotBlank() && davUser.isNotBlank() && davPass.isNotBlank(),
                modifier = Modifier.fillMaxWidth().heightIn(min = Size.touchMin),
            ) { Text(stringResource(R.string.backup_dav_test_save)) }
            Text(
                stringResource(R.string.backup_dav_probe_note) +
                    stringResource(R.string.backup_dav_retention_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun queryName(context: android.content.Context, uri: Uri): String? = runCatching {
    context.contentResolver.query(uri, null, null, null, null)?.use { c ->
        val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (idx >= 0 && c.moveToFirst()) c.getString(idx) else uri.lastPathSegment
    }
}.getOrNull()
