package com.ashkb.app.ui.backup

// v1.2.13（C1-①）：从原来的 1325 行 ui/backup/BackupScreen.kt 按分区拆出。
// 本文件只放：PassKeyboard / SecretField / LocalBackupSection。
// 拆分是纯结构动作：签名、文案、状态来源一律未改。


import android.content.Context
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.ashkb.app.domain.BackupPasswordRules
import com.ashkb.app.R
import com.ashkb.app.ui.components.AshkbTextField
import com.ashkb.app.ui.components.SectionCard
import com.ashkb.app.ui.components.shareOrReport
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
internal fun SecretField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    supportingText: (@Composable () -> Unit)? = null,
) {
    var revealed by remember { mutableStateOf(false) }
    AshkbTextField(
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
 * 本机全量备份区（v1.0.85 批次 10：从根级抽为 section）。
 *
 * 备份口令的输入状态收在本 section 内：口令是**逐字输入**的状态，摆在根部时每敲一个字
 * 都会让整屏重组（含旁边的台账与恢复区），既费帧也伤输入手感。
 */
@Composable
internal fun LocalBackupSection(vm: BackupViewModel, busy: Boolean) {
    val context = LocalContext.current
    var backupPass by remember { mutableStateOf("") }

    SectionCard(title = stringResource(R.string.backup_local_full)) {
        SecretField(
            value = backupPass, onValueChange = { backupPass = it },
            label = stringResource(R.string.backup_password_field, BackupPasswordRules.MIN_LENGTH),
            modifier = Modifier.fillMaxWidth(),
            // v1.2.12（C01 遗留）：此前标签写死「≥6 位」而新建下限已是 12 位——输入 8 位时
            // 按钮静默变灰，用户得不到任何解释。标签与提示都从同一个常量取，杜绝再次漂移。
            supportingText = if (backupPass.isNotEmpty() && !BackupPasswordRules.newBackupOk(backupPass)) {
                {
                    Text(
                        stringResource(R.string.backup_password_too_short, BackupPasswordRules.MIN_LENGTH),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            } else {
                null
            },
        )
        Spacer(Modifier.height(Spacing.sm))
        val shareTitle = stringResource(R.string.backup_save_share)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Button(
                onClick = {
                    // v1.2.10（审查第二批 M5）：以前这里是 `runCatching { startActivity(…) }` 且
                    // **没有 onFailure**——分享面板起不来时用户只会觉得"生成了但什么都没发生"。
                    vm.backupLocal(backupPass) { intent -> shareOrReport(context, intent, shareTitle) }
                },
                enabled = !busy && BackupPasswordRules.newBackupOk(backupPass),
                modifier = Modifier.heightIn(min = Size.touchMin),
            ) { Text(stringResource(R.string.backup_local_generate)) }
            OutlinedButton(
                onClick = { vm.backupWebdav(backupPass) },
                enabled = !busy && BackupPasswordRules.newBackupOk(backupPass),
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
