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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.ashkb.app.data.entity.BackupLedger
import com.ashkb.app.data.entity.LedgerStatus
import com.ashkb.app.data.entity.LedgerType

/** P4 R20 备份与数据自主页（协议 §3–§6）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(vm: BackupViewModel, onBack: () -> Unit) {
    val busy by vm.busy.collectAsState()
    val message by vm.message.collectAsState()
    val pending by vm.pendingRestore.collectAsState()
    val pendingName by vm.pendingFileName.collectAsState()
    val restoreResult by vm.restoreResult.collectAsState()
    val ledger by vm.ledger.collectAsState()
    val context = LocalContext.current

    var backupPass by remember { mutableStateOf("") }
    var restorePass by remember { mutableStateOf("") }
    var davUrl by remember { mutableStateOf("") }
    var davUser by remember { mutableStateOf("") }
    var davPass by remember { mutableStateOf("") }
    var pickedBytes by remember { mutableStateOf<ByteArray?>(null) }
    var pickedName by remember { mutableStateOf<String?>(null) }

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

    message?.let { msg ->
        AlertDialog(
            onDismissRequest = { vm.clearMessage() },
            title = { Text("操作结果") },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = { vm.clearMessage() }) { Text("知道了") } },
        )
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("备份与数据") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
            },
        )

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Spacer(Modifier.height(4.dp))

            // ---- 加密说明 ----
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
                Column(Modifier.padding(14.dp)) {
                    Text("备份加密（AES-256-GCM）", fontWeight = FontWeight.Bold)
                    Text(
                        "备份文件 = 全库 27 表快照 + 逐表 SHA-256 清单，经口令派生密钥加密。" +
                            "WebDAV 服务商或任何拿到文件者都无法读取明文。口令遗忘 = 备份不可恢复，请务必牢记。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // ---- 本机全量备份 ----
            SectionCard(title = "全量备份到本机") {
                OutlinedTextField(
                    value = backupPass, onValueChange = { backupPass = it },
                    label = { Text("备份口令（≥6 位）") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            vm.backupLocal(backupPass) { intent ->
                                runCatching {
                                    context.startActivity(Intent.createChooser(intent, "保存 / 分享备份"))
                                }
                            }
                        },
                        enabled = !busy && backupPass.length >= 6,
                    ) { Text("生成本机备份") }
                    OutlinedButton(
                        onClick = {
                            vm.backupWebdav(backupPass)
                        },
                        enabled = !busy && backupPass.length >= 6,
                    ) { Text("备份到 WebDAV") }
                }
                Text(
                    "本机文件存于 app 私有目录（files/backups），可通过分享另存到下载 / 云盘 / 电脑。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // ---- WebDAV 配置 ----
            SectionCard(title = "WebDAV 远程备份（可选）") {
                OutlinedTextField(
                    value = davUrl, onValueChange = { davUrl = it },
                    label = { Text("服务器地址（https://…/dav）") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = davUser, onValueChange = { davUser = it },
                    label = { Text("用户名") }, modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = davPass, onValueChange = { davPass = it },
                    label = { Text("密码 / 应用密码") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { vm.saveAndProbeWebdav(davUrl, davUser, davPass) },
                    enabled = !busy,
                ) { Text("测试连接并保存") }
                Text(
                    "三步探针：目录可写 → 写入探针回读一致 → 清理。上传后自动回读比对 SHA-256，" +
                        "保留日 7 / 周 4 / 月 6 份，路径 /ashkb/backup/。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // ---- 恢复（协议 §6 五步） ----
            SectionCard(title = "恢复（五步含退路）") {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = restorePass, onValueChange = { restorePass = it },
                    label = { Text("该备份的口令") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        pickedBytes?.let { vm.verifyBackup(pickedName ?: "backup", it, restorePass) }
                    },
                    enabled = !busy && pickedBytes != null && restorePass.isNotBlank(),
                ) { Text("旁路解密与校验") }

                if (pending != null) {
                    Spacer(Modifier.height(10.dp))
                    Card(
                        Modifier.fillMaxWidth(),
                        colors = androidx.compose.material3.CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text("已通过文件自校验：${pendingName ?: ""}", fontWeight = FontWeight.Bold)
                            Text("备份于 ${pending!!.createdAt.take(19)}（schema v${pending!!.schemaVersion}）",
                                style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "「执行恢复」将先对当前数据做 pre-restore 快照（可撤销退路），" +
                                    "然后覆盖写入主库并做行数 + SHA-256 双校验。",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { vm.doRestore() }, enabled = !busy) {
                                    Text("执行恢复（覆盖当前数据）")
                                }
                                OutlinedButton(onClick = { vm.cancelRestore() }) { Text("取消") }
                            }
                        }
                    }
                }

                restoreResult?.let { r ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (r.rowsOk) "✓ 双校验通过：${r.totalRows} 行与备份逐表一致"
                        else "⚠ 校验未全部通过：${r.rowDetails.take(5).joinToString("；")}",
                        color = if (r.rowsOk) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            // ---- 档案 JSON ----
            SectionCard(title = "健康档案 JSON（换机建档）") {
                Text(
                    "明文 JSON：档案 + 在用药清单（不含打卡日志）。新设备导入后可快速建档。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

            // ---- 台账 ----
            SectionCard(title = "备份台账（${ledger.size}）") {
                if (ledger.isEmpty()) {
                    Text("暂无记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    ledger.take(20).forEach { l -> LedgerRow(l) }
                }
            }

            if (busy) {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(Modifier.size(20.dp))
                    Spacer(Modifier.padding(start = 8.dp))
                    Text("处理中…（加密 / 网络操作可能需要数十秒）",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun LedgerRow(l: BackupLedger) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${LedgerType.fromKey(l.ledgerType).label} · ${l.target}",
                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Text(
                if (l.status == LedgerStatus.SUCCESS.name) "✓" else "✗",
                color = if (l.status == LedgerStatus.SUCCESS.name) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Bold,
            )
        }
        Text(
            buildString {
                append(l.createdAt.take(19).replace("T", " "))
                l.fileName?.let { append("　$it") }
                l.rowTotal?.let { append("　${it} 行") }
                l.verifyOk?.let { if (it) append("　双校验✓") }
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
        Column(Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp))
            content()
        }
    }
}

private fun queryName(context: android.content.Context, uri: Uri): String? = runCatching {
    context.contentResolver.query(uri, null, null, null, null)?.use { c ->
        val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (idx >= 0 && c.moveToFirst()) c.getString(idx) else uri.lastPathSegment
    }
}.getOrNull()
