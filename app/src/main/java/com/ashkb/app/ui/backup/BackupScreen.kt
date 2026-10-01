package com.ashkb.app.ui.backup

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
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
import androidx.compose.material3.Switch
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.lifecycle.compose.collectAsStateWithLifecycle

import com.ashkb.app.R
import com.ashkb.app.data.backup.BackupEngine
import com.ashkb.app.data.backup.WebDavClient
import com.ashkb.app.data.db.AttachmentSyncCounts
import com.ashkb.app.data.entity.BackupLedger
import com.ashkb.app.data.entity.LedgerStatus
import com.ashkb.app.data.entity.LedgerType
import com.ashkb.app.data.repo.BackupRepository
import com.ashkb.app.ui.GlobalMessages
import com.ashkb.app.ui.components.DividerList
import com.ashkb.app.ui.components.LoadingBlock
import com.ashkb.app.ui.components.ScreenTopBar
import com.ashkb.app.ui.components.SecureWindow
import com.ashkb.app.ui.components.SectionCard
import com.ashkb.app.ui.theme.Clinical
import com.ashkb.app.ui.theme.Size
import com.ashkb.app.ui.theme.Spacing

/** U6：口令框统一密码键盘——Password 类型让输入法（含微信输入法）关闭候选词/联想。 */
private val PassKeyboard = KeyboardOptions(
    keyboardType = KeyboardType.Password,
    autoCorrect = false,
)

/**
 * v1.0.43：口令输入框统一封装。
 * 默认掩码显示；右侧「眼睛」图标**长按**才显示明文（松手立即恢复掩码），
 * 避免误触泄露，同时让输错口令的用户能当场核对。
 */
@Composable
private fun SecretField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    supportingText: (@Composable () -> Unit)? = null,
) {
    var revealed by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        visualTransformation = if (revealed) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = PassKeyboard,
        singleLine = true,
        supportingText = supportingText,
        trailingIcon = {
            Box(
                modifier = Modifier
                    .size(Size.touchMin)
                    .pointerInput(Unit) {
                        detectTapGestures(onPress = {
                            revealed = true
                            tryAwaitRelease()
                            revealed = false
                        })
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (revealed) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                    contentDescription = stringResource(R.string.backup_password_longpress_hint),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        modifier = modifier,
    )
}

/**
 * P4 R20 备份与数据自主页（协议 §3–§6）。
 *
 * v1.0.85（批次 10）：本页原先在**根级**收集 19 条 Flow，其中附件同步的
 * `attachSyncStage` / `attachSyncMsg` / `attachBytes` 在同步期间每秒发射多次——因为读点在根级，
 * 每个进度 tick 都会把整屏（两个口令输入区、台账列表、WebDAV/恢复区）一起拖进重组，
 * 用户正在输口令时会感到输入被打断。现在改为**按分区收口**：每个 section 在自己内部收集
 * 它渲染的状态，根部只留整屏骨架级的 3 条：`busy`（8 处共用：6 个分区的禁用条件 + 演练 / 档案卡片 +
 * 底部进度块 + 远程列表 sheet）、`message`（全局 Snackbar 通道）、`davPicked`
 * （跨分区胶水：远程下载完 → 喂给恢复区 + 关远程列表 sheet）。
 *
 * 维护约定（后续加状态时请沿用）：
 *  · 某个状态只被一个 section 渲染 → 在那个 section 里 collect，不要拿回根部；
 *  · section 的入参只给"父级自己也要用"的值（如 busy / 选好的文件），其余进去自己收；
 *  · 不要在 section 里顺手读与自己无关的 VM 状态——那等于把重组范围又扩大回来。
 */
@Composable
fun BackupScreen(vm: BackupViewModel, onBack: () -> Unit) {
    // v1.0.73：本页含备份口令 / 恢复码输入——禁截屏与最近任务缩略图（维护者口径：只加在口令与恢复码类页面）
    SecureWindow()

    // ---- 根级仅存的 3 条 Flow（整屏骨架 / 跨分区胶水）----
    val busy by vm.busy.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val davPicked by vm.davPicked.collectAsStateWithLifecycle()

    val context = LocalContext.current

    // 选好的待恢复备份：本机文件选择与 WebDAV 下载两条来源都写它，恢复区只读
    var pickedBytes by remember { mutableStateOf<ByteArray?>(null) }
    var pickedName by remember { mutableStateOf<String?>(null) }
    var showDavSheet by remember { mutableStateOf(false) }
    var showDavPickSheet by remember { mutableStateOf(false) }

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

    // 附件同步摘要是只读快照（不是 Flow）：进页面拉一次，同步结束后由 VM 自己再刷新。
    // 留在根级是因为它是**页面进入**这一事件（不是渲染状态）；LaunchedEffect(Unit) 不读状态，
    // 所以放在根部零重组代价。
    LaunchedEffect(Unit) { vm.refreshAttachStats() }

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
            RecoveryCodeSection(vm = vm, busy = busy)

            // ---- 本机全量备份 ----
            LocalBackupSection(vm = vm, busy = busy)

            // ---- WebDAV 配置（表单收进 sheet，页面只留状态与入口） ----
            WebDavSection(vm = vm, busy = busy, onOpenSheet = { showDavSheet = true })

            // ---- 附件同步（v1.0.35）：逐附件加密上传，与 DB 备份各自独立 ----
            AttachSyncSection(vm = vm)

            // ---- 恢复（协议 §6 五步） ----
            RestoreSection(
                vm = vm,
                busy = busy,
                pickedBytes = pickedBytes,
                pickedName = pickedName,
                onPickFile = { pickRestoreFile.launch(arrayOf("*/*")) },
                onOpenDavPicker = {
                    showDavPickSheet = true
                    vm.loadDavBackups()
                },
            )

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
            LedgerSection(vm = vm)

            // ---- 长操作进度（WebDAV 备份阶段文案，仅在 busy 时出节点） ----
            BusyFooter(vm = vm, busy = busy)
            Spacer(Modifier.height(Spacing.xxl))
        }
    }

    if (showDavSheet) {
        WebDavSheet(vm = vm, onDismiss = { showDavSheet = false })
    }

    if (showDavPickSheet) {
        DavBackupPickerSheet(vm = vm, busy = busy, onDismiss = { showDavPickSheet = false })
    }
}

/**
 * 备份恢复码区（v1.0.85 批次 10：从根级抽为 section）。
 *
 * 本 section 内部收集 `recoveryCodeSet` / `recoveryReveal`，并持有两个弹窗的可见性状态与
 * "生成/查看完成 → 弹窗展示"的那条 effect——这三样都只服务本区，原先摆在根部，
 * 让一次"恢复码明文出现"的事件也要带着整屏重组一次。
 */
@Composable
private fun RecoveryCodeSection(vm: BackupViewModel, busy: Boolean) {
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

/**
 * 本机全量备份区（v1.0.85 批次 10：从根级抽为 section）。
 *
 * 备份口令的输入状态收在本 section 内：口令是**逐字输入**的状态，摆在根部时每敲一个字
 * 都会让整屏重组（含旁边的台账与恢复区），既费帧也伤输入手感。
 */
@Composable
private fun LocalBackupSection(vm: BackupViewModel, busy: Boolean) {
    val context = LocalContext.current
    var backupPass by remember { mutableStateOf("") }

    SectionCard(title = stringResource(R.string.backup_local_full)) {
        SecretField(
            value = backupPass, onValueChange = { backupPass = it },
            label = stringResource(R.string.backup_password_field),
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
}

/**
 * WebDAV 配置入口区（v1.0.85 批次 10：从根级抽为 section）。
 *
 * 地址 / 用户名收在本 section：它们只用于这一行的副标题与"改配置还是首次配置"的按钮文案，
 * 根部不再需要为了这行字而持有 davUrl / davUser。
 */
@Composable
private fun WebDavSection(vm: BackupViewModel, busy: Boolean, onOpenSheet: () -> Unit) {
    val davUrl by vm.davUrl.collectAsStateWithLifecycle()
    val davUser by vm.davUser.collectAsStateWithLifecycle()

    SectionCard(
        title = stringResource(R.string.backup_dav_section_title),
        subtitle = if (davUrl.isNotBlank()) stringResource(R.string.backup_dav_configured, davUrl, davUser) else null,
    ) {
        OutlinedButton(
            onClick = onOpenSheet,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().heightIn(min = Size.touchMin),
        ) { Text(if (davUrl.isNotBlank()) stringResource(R.string.backup_modify_dav) else stringResource(R.string.backup_configure_dav)) }
    }
}

/**
 * 附件同步区（v1.0.35 引入；v1.0.85 批次 10 收口为独立 section，本批的主目标）。
 *
 * **为什么必须自己收**：`attachSyncStage` / `attachSyncMsg` / `attachBytes` 在附件同步期间
 * 每秒发射多次（进度 tick）。原先这三条在 [BackupScreen] 根级收集，读点也在根级，
 * 于是每个 tick 都让整屏重组——正在输备份口令的用户会被进度刷新打断。现在读点搬进本
 * section，tick 的失效范围收窄到本 section 自己。
 *
 * 本 section 在内部收集（共 7 条，全部只被本区渲染）：
 * `attachSyncEnabled` / `attachCounts` / `attachSyncStage` / `attachSyncMsg` / `attachBytes` /
 * `attachVerifyMsg` / `davUrl`（davUrl 只用来判断"按钮能不能点"与给缺配置提示）。
 * 入参只给 vm：这 7 条 Flow 与 3 个动作若逐条透传会超出 detekt 的 LongParameterList 阈值（8），
 * 而"状态一律在本 section 内读"这点由上面的清单与本函数的 collect 语句共同保证——
 * **往下加东西时请只读这里列出的状态**。
 */
@Composable
private fun AttachSyncSection(vm: BackupViewModel) {
    val attachSyncEnabled by vm.attachSyncEnabled.collectAsStateWithLifecycle()
    val attachCounts by vm.attachCounts.collectAsStateWithLifecycle()
    val attachSyncStage by vm.attachSyncStage.collectAsStateWithLifecycle()
    val attachSyncMsg by vm.attachSyncMsg.collectAsStateWithLifecycle()
    val attachBytes by vm.attachBytes.collectAsStateWithLifecycle()
    val attachVerifyMsg by vm.attachVerifyMsg.collectAsStateWithLifecycle()
    val davUrl by vm.davUrl.collectAsStateWithLifecycle()

    SectionCard(title = stringResource(R.string.attach_sync_section)) {
        AttachSyncToggle(
            enabled = attachSyncEnabled,
            onSetEnabled = { vm.setAttachSyncEnabled(it) },
        )
        Spacer(Modifier.height(Spacing.sm))
        AttachSyncStats(counts = attachCounts, bytes = attachBytes)
        Spacer(Modifier.height(Spacing.sm))
        AttachSyncActions(
            enabled = attachSyncEnabled,
            davUrl = davUrl,
            // 附件同步是一趟独立长任务（几百 MB），所以只看自身状态，不占用全局 busy
            running = attachSyncStage != null,
            onSync = { vm.syncAttachments() },
            onVerify = { vm.verifyAttachments() },
        )
        AttachSyncStatus(
            stage = attachSyncStage,
            davUrl = davUrl,
            enabled = attachSyncEnabled,
            msg = attachSyncMsg,
            verifyMsg = attachVerifyMsg,
        )
    }
}

/** 附件同步总闸：开关 + 一行说明。 */
@Composable
private fun AttachSyncToggle(enabled: Boolean, onSetEnabled: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.attach_sync_switch),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = enabled,
            onCheckedChange = onSetEnabled,
        )
    }
    Text(
        stringResource(R.string.attach_sync_switch_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** 附件同步摘要 + 本地占用。只吃两个值，所以进度 tick 不会重组它。 */
@Composable
private fun AttachSyncStats(counts: AttachmentSyncCounts, bytes: Long) {
    Text(
        stringResource(
            R.string.attach_sync_summary,
            counts.visible, counts.synced,
            counts.pending, counts.toDelete,
        ),
        style = MaterialTheme.typography.bodySmall,
    )
    Text(
        stringResource(R.string.attach_sync_size, formatBytes(bytes)),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * 同步 / 校验两个按钮 + 校验说明。
 *
 * `running` 由父级从 `attachSyncStage != null` 派生后传值：进度字符串每 tick 都在变，
 * 但"是否在跑"只在开始/结束时翻转，本块因此不会跟着 tick 重组。
 */
@Composable
private fun AttachSyncActions(
    enabled: Boolean,
    davUrl: String,
    running: Boolean,
    onSync: () -> Unit,
    onVerify: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Button(
            onClick = onSync,
            enabled = enabled && davUrl.isNotBlank() && !running,
            modifier = Modifier.heightIn(min = Size.touchMin),
        ) { Text(stringResource(R.string.attach_sync_button)) }
        OutlinedButton(
            onClick = onVerify,
            // 校验与同步共用进度流，两者互斥；同样不占用全局 busy
            enabled = enabled && davUrl.isNotBlank() && !running,
            modifier = Modifier.heightIn(min = Size.touchMin),
        ) { Text(stringResource(R.string.attach_verify_button)) }
    }
    Text(
        stringResource(R.string.attach_verify_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * 附件同步的进度 / 缺配置提示 / 结果文案。
 *
 * 这是进度 tick 的实际落点（`stage` 每 tick 都变），所以它只收三个值、只渲染这几行文字——
 * 旁边那些不会变的东西（开关、按钮、摘要）都在别的块里，tick 时会被跳过。
 */
@Composable
private fun AttachSyncStatus(
    stage: String?,
    davUrl: String,
    enabled: Boolean,
    msg: Pair<Boolean, String>?,
    verifyMsg: Pair<Boolean, String>?,
) {
    // 阶段文案自带动作（正在补传附件 / 正在清理远端多余文件），不再叠前缀
    stage?.let { progress ->
        Text(
            progress,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    // 禁用态按钮自己不会解释原因，缺哪一步就直说是缺哪一步
    if (davUrl.isBlank()) {
        Text(
            stringResource(R.string.attach_sync_need_webdav),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else if (!enabled) {
        Text(
            stringResource(R.string.attach_sync_need_enable),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    msg?.let { (ok, detail) ->
        Spacer(Modifier.height(Spacing.xs))
        Text(
            stringResource(
                if (ok) R.string.attach_sync_done else R.string.attach_sync_failed,
                detail,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = if (ok) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.error,
        )
    }
    verifyMsg?.let { (ok, detail) ->
        Spacer(Modifier.height(Spacing.xs))
        Text(
            stringResource(
                if (ok) R.string.attach_verify_done else R.string.attach_verify_failed,
                detail,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = if (ok) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.error,
        )
    }
}

/**
 * 恢复区（协议 §6 五步；v1.0.85 批次 10：从根级抽为 section）。
 *
 * 内部收集 `pendingRestore` / `pendingFileName` / `restoreResult` / `restoreMode` / `davUrl` 五条 Flow，
 * 并把**恢复口令**的输入状态收进来：口令同属逐字输入状态，留在根部等于每敲一个字重组整屏。
 * 入参只保留父级自己也要用的值（busy / 选好的文件）与两个"打开别处 UI"的回调
 * （文件选择器与远程列表 sheet 都在根级注册，见 [BackupScreen]）。
 */
@Composable
private fun RestoreSection(
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
                "已通过文件自校验：${pendingName ?: ""}",
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                "备份于 ${pending.createdAt.take(19)}（schema v${pending.schemaVersion}）",
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
            else "校验未全部通过：${result.rowDetails.take(5).joinToString("；")}",
            style = MaterialTheme.typography.bodyMedium,
            color = if (result.rowsOk) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.error,
        )
    }
}

/**
 * 台账区（默认折叠前 5 条，可展开全部；v1.0.85 批次 10：从根级抽为 section）。
 *
 * ledger 与"展开全部"开关都只被本区使用，收进来后新增一条备份记录只重组这张卡片。
 */
@Composable
private fun LedgerSection(vm: BackupViewModel) {
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
                    Text(if (showAllLedger) stringResource(R.string.common_collapse) else stringResource(R.string.backup_view_all_count, ledger.size))
                }
            }
        }
    }
}

/**
 * 长操作进度块（busy 期间出节点；v1.0.85 批次 10：stage 收进本块）。
 *
 * stage 是 WebDAV 备份的阶段文案，更新频率不如附件同步，但同样不必让整屏跟着重组；
 * busy 仍留在根部——它是整屏骨架共用的状态（8 处：6 个分区 + 演练 / 档案卡片 + 本块 + 远程列表 sheet）。
 */
@Composable
private fun BusyFooter(vm: BackupViewModel, busy: Boolean) {
    val stage by vm.stage.collectAsStateWithLifecycle()
    if (busy) {
        LoadingBlock(label = if (stage.isNotBlank()) stage else stringResource(R.string.backup_processing))
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

/** 附件本地占用：只分 MB / KB 两档（附件量级到不了 GB），同样锁 Locale 免小数点变逗号。 */
private fun formatBytes(bytes: Long): String =
    if (bytes >= 1_048_576L) String.format(java.util.Locale.US, "%.1f MB", bytes / 1_048_576.0)
    else String.format(java.util.Locale.US, "%.0f KB", bytes / 1_024.0)

// ===== WebDAV 配置 sheet（密码不回显，重新输入） =====
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WebDavSheet(vm: BackupViewModel, onDismiss: () -> Unit) {
    // v1.0.85（批次 10）：地址 / 用户名由本 sheet 自己收集。二者只用于**预填输入框**
    // （编辑中的值仍是下面这两个本地状态），这样根部就不必为了开 sheet 而持有它们。
    val savedUrl by vm.davUrl.collectAsStateWithLifecycle()
    val savedUser by vm.davUser.collectAsStateWithLifecycle()
    var davUrl by remember { mutableStateOf(savedUrl) }
    var davUser by remember { mutableStateOf(savedUser) }
    var davPass by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        // v1.0.73：Sheet 自身是独立窗口，必须在本窗口内再禁一次截屏（Activity 上的标志管不到它）；
        // 本 Sheet 含 WebDAV 口令输入框
        SecureWindow()
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
            SecretField(
                value = davPass, onValueChange = { davPass = it },
                label = stringResource(R.string.backup_dav_password_field),
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
