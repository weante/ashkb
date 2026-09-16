package com.ashkb.app.ui.backup

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
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

/** P4 R20 备份与数据自主页（协议 §3–§6）。 */
@Composable
fun BackupScreen(vm: BackupViewModel, onBack: () -> Unit) {
    val busy by vm.busy.collectAsState()
    val message by vm.message.collectAsState()
    val pending by vm.pendingRestore.collectAsState()
    val pendingName by vm.pendingFileName.collectAsState()
    val restoreResult by vm.restoreResult.collectAsState()
    val ledger by vm.ledger.collectAsState()
    val davUrl by vm.davUrl.collectAsState()
    val davUser by vm.davUser.collectAsState()
    val context = LocalContext.current

    var backupPass by remember { mutableStateOf("") }
    var restorePass by remember { mutableStateOf("") }
    var pickedBytes by remember { mutableStateOf<ByteArray?>(null) }
    var pickedName by remember { mutableStateOf<String?>(null) }
    var showDavSheet by remember { mutableStateOf(false) }
    var showAllLedger by remember { mutableStateOf(false) }

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

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(title = "备份与数据", onBack = onBack)

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Spacer(Modifier.height(Spacing.xs))

            // ---- 加密说明 ----
            SectionCard(title = "备份加密（AES-256-GCM）") {
                Text(
                    "备份文件 = 全库 27 表快照 + 逐表 SHA-256 清单，经口令派生密钥加密。" +
                        "WebDAV 服务商或任何拿到文件者都无法读取明文。口令遗忘 = 备份不可恢复，请务必牢记。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // ---- 本机全量备份 ----
            SectionCard(title = "全量备份到本机") {
                OutlinedTextField(
                    value = backupPass, onValueChange = { backupPass = it },
                    label = { Text("备份口令（≥6 位）") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(Spacing.sm))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Button(
                        onClick = {
                            vm.backupLocal(backupPass) { intent ->
                                runCatching {
                                    context.startActivity(Intent.createChooser(intent, "保存 / 分享备份"))
                                }
                            }
                        },
                        enabled = !busy && backupPass.length >= 6,
                        modifier = Modifier.heightIn(min = Size.touchMin),
                    ) { Text("生成本机备份") }
                    OutlinedButton(
                        onClick = { vm.backupWebdav(backupPass) },
                        enabled = !busy && backupPass.length >= 6,
                        modifier = Modifier.heightIn(min = Size.touchMin),
                    ) { Text("备份到 WebDAV") }
                }
                Text(
                    "本机文件存于 app 私有目录（files/backups），可通过分享另存到下载 / 云盘 / 电脑。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // ---- WebDAV 配置（表单收进 sheet，页面只留状态与入口） ----
            SectionCard(
                title = "WebDAV 远程备份（可选）",
                subtitle = if (davUrl.isNotBlank()) "已配置：$davUrl · $davUser" else null,
            ) {
                OutlinedButton(
                    onClick = { showDavSheet = true },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = Size.touchMin),
                ) { Text(if (davUrl.isNotBlank()) "修改 WebDAV 配置" else "配置 WebDAV") }
            }

            // ---- 恢复（协议 §6 五步） ----
            SectionCard(title = "恢复（五步含退路）") {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Text(
                        pickedName ?: "未选择备份文件",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                        color = if (pickedName == null) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onSurface,
                    )
                    OutlinedButton(
                        onClick = { pickRestoreFile.launch(arrayOf("*/*")) },
                        enabled = !busy,
                    ) { Text("选择 .ashkb") }
                }
                Spacer(Modifier.height(Spacing.xs))
                OutlinedTextField(
                    value = restorePass, onValueChange = { restorePass = it },
                    label = { Text("该备份的口令") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(Spacing.sm))
                Button(
                    onClick = {
                        pickedBytes?.let { vm.verifyBackup(pickedName ?: "backup", it, restorePass) }
                    },
                    enabled = !busy && pickedBytes != null && restorePass.isNotBlank(),
                ) { Text("旁路解密与校验") }

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
                                "「执行恢复」将先对当前数据做 pre-restore 快照（可撤销退路），" +
                                    "然后覆盖写入主库并做行数 + SHA-256 双校验。",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Spacer(Modifier.height(Spacing.sm))
                            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                                Button(onClick = { vm.doRestore() }, enabled = !busy) {
                                    Text("执行恢复（覆盖当前数据）")
                                }
                                OutlinedButton(onClick = { vm.cancelRestore() }) { Text("取消") }
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
                            if (r.rowsOk) "双校验通过：${r.totalRows} 行与备份逐表一致"
                            else "校验未全部通过：${r.rowDetails.take(5).joinToString("；")}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (r.rowsOk) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            // ---- 恢复演练 ----
            SectionCard(title = "恢复演练（一键自证）") {
                Text(
                    "备份 → 加密 → 解密 → 覆盖恢复 → 行数+SHA 双校验 → 复核一致，全链路自动跑一遍。" +
                        "写入的正是刚导出的当前数据（可逆无损），无需口令、不落盘，结果记入台账（DRILL）。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Spacing.sm))
                Button(onClick = { vm.drill() }, enabled = !busy) { Text("执行恢复演练") }
            }

            // ---- 档案 JSON ----
            SectionCard(title = "健康档案 JSON（换机建档）") {
                Text(
                    "明文 JSON：档案 + 在用药清单（不含打卡日志）。新设备导入后可快速建档。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Spacing.sm))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    OutlinedButton(
                        onClick = {
                            vm.exportProfileJson { intent ->
                                runCatching {
                                    context.startActivity(Intent.createChooser(intent, "保存 / 分享档案"))
                                }
                            }
                        },
                        enabled = !busy,
                    ) { Text("导出档案") }
                    OutlinedButton(
                        onClick = { pickImportJson.launch(arrayOf("*/*")) },
                        enabled = !busy,
                    ) { Text("导入档案") }
                }
            }

            // ---- 台账（默认折叠前 5 条，可展开全部） ----
            SectionCard(title = "备份台账（${ledger.size}）") {
                if (ledger.isEmpty()) {
                    Text("暂无记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    val shown = if (showAllLedger) ledger else ledger.take(5)
                    DividerList(items = shown, key = { it.id }) { l ->
                        LedgerRow(l)
                    }
                    if (ledger.size > 5) {
                        TextButton(onClick = { showAllLedger = !showAllLedger }) {
                            Text(if (showAllLedger) "收起" else "查看全部 ${ledger.size} 条")
                        }
                    }
                }
            }

            if (busy) {
                LoadingBlock(label = "处理中…（加密 / 网络操作可能需要数十秒）")
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
                    if (ok) "成功" else "失败",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (ok) Clinical.colors.success else Clinical.colors.danger,
                )
            }
        }
        Text(
            buildString {
                append(l.createdAt.take(19).replace("T", " "))
                l.fileName?.let { append("　$it") }
                l.rowTotal?.let { append("　${it} 行") }
                l.verifyOk?.let { if (it) append("　双校验通过") }
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
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
            Text("WebDAV 远程备份配置", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = davUrl, onValueChange = { davUrl = it },
                label = { Text("服务器地址（https://…/dav）") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = davUser, onValueChange = { davUser = it },
                label = { Text("用户名") }, modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = davPass, onValueChange = { davPass = it },
                label = { Text("密码 / 应用密码（保存后加密存储，不回显）") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    vm.saveAndProbeWebdav(davUrl, davUser, davPass)
                    onDismiss()
                },
                enabled = davUrl.isNotBlank() && davUser.isNotBlank() && davPass.isNotBlank(),
                modifier = Modifier.fillMaxWidth().heightIn(min = Size.touchMin),
            ) { Text("测试连接并保存") }
            Text(
                "三步探针：目录可写 → 写入探针回读一致 → 清理。上传后自动回读比对 SHA-256，" +
                    "保留日 7 / 周 4 / 月 6 份，路径 /ashkb/backup/。",
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
