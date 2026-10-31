package com.ashkb.app.ui.backup

// v1.2.13（C1-①）：从原来的 1325 行 ui/backup/BackupScreen.kt 按分区拆出。
// 本文件只放：WebDavSheet / DavBackupPickerSheet / DavBackupRow。
// 拆分是纯结构动作：签名、文案、状态来源一律未改。


import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ashkb.app.data.backup.WebDavClient
import com.ashkb.app.R
import com.ashkb.app.ui.components.AshkbTextField
import com.ashkb.app.ui.components.DividerList
import com.ashkb.app.ui.components.LoadingBlock
import com.ashkb.app.ui.components.SecureWindow
import com.ashkb.app.ui.theme.Size
import com.ashkb.app.ui.theme.Spacing

// ===== WebDAV 配置 sheet（密码不回显，重新输入） =====
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WebDavSheet(vm: BackupViewModel, onDismiss: () -> Unit) {
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
            AshkbTextField(
                value = davUrl, onValueChange = { davUrl = it },
                label = { Text(stringResource(R.string.backup_dav_url_field)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            AshkbTextField(
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

// ===== W4：WebDAV 远程备份选择 sheet =====
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DavBackupPickerSheet(vm: BackupViewModel, busy: Boolean, onDismiss: () -> Unit) {
    val backups by vm.davBackups.collectAsStateWithLifecycle()
    val stage by vm.stage.collectAsStateWithLifecycle()
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                // v1.2.10（审查第二批 M7）：**这行 `verticalScroll` 是修 bug，不是修饰**。
                // 服务端列表上限是 `WebDavClient.TEXT_RESPONSE_LIMIT`（1 MiB），条目多起来时
                // 这个 Column 会长过屏幕；没有滚动容器，`ModalBottomSheet` 只会把溢出部分**裁掉**
                // ——排在后面的备份不是"需要滚一下"，而是**根本看不到也点不到**。
                // 与 `ui/checkup/CheckupLabImaging.kt` 的 `SheetColumn` 同一惯例。
                .verticalScroll(rememberScrollState())
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
                // v1.2.10（审查第二批 M7 附带项）：只把截断后的行交给 DividerList——
                // 宿主是 Column + verticalScroll（没有虚拟化），全量组合数百行会让 sheet 卡顿。
                // 与 MedsScreen / SupplementDetailSheet 的截断先例同一思路；头部「真实份数」
                // 由下面的截断说明承担，不能让用户以为服务器上只有这么多份。
                val shownBackups = remember(backups) { WebDavClient.visibleBackups(backups) }
                DividerList(items = shownBackups, key = { it.name }) { f ->
                    DavBackupRow(f) { vm.downloadDavBackup(f.name) }
                }
                if (WebDavClient.backupsTruncated(backups.size)) {
                    Text(
                        stringResource(R.string.backup_dav_pick_truncated, shownBackups.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
