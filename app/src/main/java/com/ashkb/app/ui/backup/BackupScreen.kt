package com.ashkb.app.ui.backup

// v1.2.13（C1-①）：从原来的 1325 行 ui/backup/BackupScreen.kt 按分区拆出。
// 本文件只放：BackupScreen / queryName / querySize。
// 拆分是纯结构动作：签名、文案、状态来源一律未改。


import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ashkb.app.data.backup.BackupEngine
import com.ashkb.app.data.repo.PreRestoreSnapshot
import com.ashkb.app.R
import com.ashkb.app.ui.components.ScreenTopBar
import com.ashkb.app.ui.components.SectionCard
import com.ashkb.app.ui.components.SecureWindow
import com.ashkb.app.ui.components.shareOrReport
import com.ashkb.app.ui.GlobalMessages
import com.ashkb.app.ui.theme.Size
import com.ashkb.app.ui.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    // v1.2.11（缺陷工单 B07）：撤销入口的数据源；null ⇒ 不渲染撤销块
    val preRestoreSnapshot by vm.preRestoreSnapshot.collectAsStateWithLifecycle()

    val context = LocalContext.current
    // v1.2.10（审查 CRITICAL-2）：两个文件选择器的回调都在**主线程**上，而读入是纯 IO。
    // 挪进这个 scope 后主线程只做状态赋值；上限与超限文案统一由 BackupEngine 提供。
    val pickScope = rememberCoroutineScope()

    // 选好的待恢复备份：本机文件选择与 WebDAV 下载两条来源都写它，恢复区只读
    var pickedBytes by remember { mutableStateOf<ByteArray?>(null) }
    var pickedName by remember { mutableStateOf<String?>(null) }
    var showDavSheet by remember { mutableStateOf(false) }
    var showDavPickSheet by remember { mutableStateOf(false) }

    val pickRestoreFile = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val name = queryName(context, uri) ?: "backup"
            // 先看清体积：SAF 能报出大小时连读都不读（一份 200 MB 的文件没必要先搬进内存再后悔）。
            // 报不出大小（provider 不提供该列，返回 -1）时才落到 readBounded 的逐块累计兜底。
            val declaredSize = querySize(context, uri)
            if (declaredSize != null && !BackupEngine.withinPickLimit(declaredSize)) {
                GlobalMessages.post(
                    context.getString(
                        R.string.backup_file_too_large,
                        name,
                        declaredSize / (1024L * 1024L),
                        BackupEngine.PICK_LIMIT_BYTES / (1024L * 1024L),
                    )
                )
            } else {
                pickScope.launch {
                    val bytes = withContext(Dispatchers.IO) {
                        runCatching {
                            context.contentResolver.openInputStream(uri)?.let {
                                BackupEngine.readBounded(it, BackupEngine.PICK_LIMIT_BYTES, name)
                            }
                        }.getOrNull()
                    }
                    if (bytes == null) {
                        // v1.2.10：以前是 `runCatching{}.getOrNull()` 之后静默什么都不做——
                        // 用户点了文件、界面毫无反应，分不清"没选中"和"读失败"。
                        GlobalMessages.post(context.getString(R.string.backup_pick_read_failed))
                    } else {
                        pickedBytes = bytes
                        pickedName = name
                        vm.cancelRestore()
                    }
                }
            }
        }
    }
    val pickImportJson = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val name = queryName(context, uri) ?: "profile.json"
            val declaredSize = querySize(context, uri)
            if (declaredSize != null && !BackupEngine.withinPickLimit(declaredSize)) {
                GlobalMessages.post(
                    context.getString(
                        R.string.backup_file_too_large,
                        name,
                        declaredSize / (1024L * 1024L),
                        BackupEngine.PICK_LIMIT_BYTES / (1024L * 1024L),
                    )
                )
            } else {
                pickScope.launch {
                    val bytes = withContext(Dispatchers.IO) {
                        runCatching {
                            context.contentResolver.openInputStream(uri)?.let {
                                BackupEngine.readBounded(it, BackupEngine.PICK_LIMIT_BYTES, name)
                            }
                        }.getOrNull()
                    }
                    if (bytes == null) {
                        GlobalMessages.post(context.getString(R.string.backup_pick_read_failed))
                    } else {
                        vm.importProfileJson(bytes)
                    }
                }
            }
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

    // v1.2.11（缺陷工单 B07）：撤销入口要显示「最近一次恢复前快照」，那是只读的文件系统
    // 快照（不是 Flow），同样在**进入页面**这一事件上拉一次；恢复结束后由 VM 自己再刷新。
    LaunchedEffect(Unit) { vm.refreshPreRestoreSnapshot() }

    // v1.2.10（审查 HIGH-3）：**离开本页就把密文丢掉**。此前 `pickedBytes` 是个纯 `remember`，
    // 全文件没有一处把它置空——它活的是 NavBackStackEntry 的生命周期，而不是"这屏可见"的时间：
    // 用户选完备份、看一眼、返回主界面，几 MiB 的密文仍挂在内存里，直到该条目被回收。
    // 密文本身泄露不了内容（VaultCipher 加密），但没有任何理由让它多留一秒。
    // 副作用是"回到本页要重新选文件"——正确的代价：恢复本来就必须重新输口令。
    DisposableEffect(Unit) {
        onDispose {
            pickedBytes = null
            pickedName = null
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

            // ---- v1.2.11（缺陷工单 B07）：用最近一次恢复前快照撤销 ----
            // 没有快照就整块不渲染（工单第 6 条：不留空入口）。
            preRestoreSnapshot?.let { snap ->
                UndoSnapshotBlock(
                    snapshot = snap,
                    busy = busy,
                    onUndo = { pass -> vm.undoFromPreRestoreSnapshot(pass) },
                )
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
                            vm.exportProfileJson { intent -> shareOrReport(context, intent, shareTitle) }
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

private fun queryName(context: android.content.Context, uri: Uri): String? = runCatching {
    context.contentResolver.query(uri, null, null, null, null)?.use { c ->
        val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (idx >= 0 && c.moveToFirst()) c.getString(idx) else uri.lastPathSegment
    }
}.getOrNull()

/**
 * v1.2.10（审查 CRITICAL-2）：先问清体积，再决定要不要读。
 *
 * 返回 null 表示**问不出来**（provider 不提供 [OpenableColumns.SIZE]，或该列是
 * `Cursor.getLong` 在空值上给的 0/-1）。这时不能当"零字节"放过，也不能当超限拒绝，
 * 只能交给 [BackupEngine.readBounded] 的逐块累计兜底——两条路都通，只是后者
 * 多搬一次数据。
 */
private fun querySize(context: Context, uri: Uri): Long? = runCatching {
    context.contentResolver.query(uri, null, null, null, null)?.use { c ->
        val idx = c.getColumnIndex(OpenableColumns.SIZE)
        if (idx >= 0 && c.moveToFirst() && !c.isNull(idx)) c.getLong(idx).takeIf { it > 0 } else null
    }
}.getOrNull()
