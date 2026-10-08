package com.ashkb.app.ui.me

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

import com.ashkb.app.R
import com.ashkb.app.domain.XiaomiCompat
import com.ashkb.app.reminder.ReminderHealth
import com.ashkb.app.reminder.ReminderTest
import com.ashkb.app.reminder.SystemSetupGuides
import com.ashkb.app.ui.GlobalMessages
import com.ashkb.app.ui.components.ScreenTopBar
import com.ashkb.app.ui.components.SectionCard
import com.ashkb.app.ui.theme.Size
import com.ashkb.app.ui.theme.Spacing

/**
 * v1.0.72：提醒可靠性自检的**二级页**。
 *
 * 「我的」页此前把整块自检摊平展示：4 行状态 + 最多 5 个跳转按钮 + 自启动引导 + 测试提醒
 * + 小米专属区块——属于「一次性设置」，却占着主页最显眼的位置（用户反馈：塞太多无用的一次性
 * 权限列表）。现在「我的」只保留**一行入口 + 状态摘要**，逐项处理与测试都在本页完成。
 *
 * 内容与行为与迁移前完全一致（含 v1.0.61 强提醒授权引导、v1.0.62 回前台刷新与测试提醒、
 * v1.0.71 的 `data=package:` 修复、v1.0.72 的小米专属区块），仅换了承载位置。
 */
@Composable
fun ReminderCheckScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val state = rememberReminderCheckState()
    var testScheduled by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(title = stringResource(R.string.reminder_selfcheck_title), onBack = onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Spacer(Modifier.height(Spacing.xs))
            SectionCard(title = stringResource(R.string.reminder_check_states)) {
                CheckRow(
                    stringResource(R.string.reminder_notification_permission),
                    if (state.notifOk) stringResource(R.string.permission_granted) else stringResource(R.string.reminder_no_permission),
                    state.notifOk,
                )
                CheckRow(
                    stringResource(R.string.reminder_exact_alarm),
                    if (state.exactOk) stringResource(R.string.reminder_exact_ok) else stringResource(R.string.reminder_no_exact),
                    state.exactOk,
                )
                CheckRow(
                    stringResource(R.string.reminder_fullscreen),
                    if (state.fullScreenOk) stringResource(R.string.reminder_fullscreen_ok) else stringResource(R.string.reminder_fullscreen_no),
                    state.fullScreenOk,
                )
                CheckRow(
                    stringResource(R.string.reminder_battery),
                    if (state.batteryOk) stringResource(R.string.reminder_battery_ok) else stringResource(R.string.reminder_battery_no),
                    state.batteryOk,
                )
                // v1.0.73（P0-1）：**闹钟注册实证行**——这是全页唯一能证明「提醒真的排上了」的一项。
                // 权限可以全绿而闹钟一个都没注册成功（小米 MIUIOP(10014) 默认 ignore），故必须显示。
                // v1.0.84（批次 9）：快照随 state 一起带回（原本在渲染这里**再读一次**——同一帧
                // 两次同步读 SharedPreferences，且都在主线程）。
                val health = state.health
                CheckRow(
                    stringResource(R.string.reminder_alarm_registration),
                    when {
                        health.attempted == 0 -> stringResource(R.string.reminder_alarm_none)
                        health.hasFailure -> stringResource(
                            R.string.reminder_alarm_failed,
                            health.attempted, health.failed, health.lastError ?: "—",
                        )
                        else -> stringResource(R.string.reminder_alarm_ok, health.attempted, health.at ?: "—")
                    },
                    state.alarmsOk,
                )

                Spacer(Modifier.height(Spacing.md))
                // v1.0.62 C11：动作按钮改纵向满宽——从 2 个增到最多 4 个后横向 Row 会溢出
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    if (!state.exactOk && Build.VERSION.SDK_INT >= 31) {
                        OutlinedButton(
                            // v1.0.71：带 package 数据直达**本应用**专属页（原实现不带 data → 只跳到
                            // 「全部应用」的闹钟列表，用户还得自己找 ASHKB）；跳不动时给文字提示，不静默
                            onClick = {
                                if (!SystemSetupGuides.openExactAlarmSettings(context)) {
                                    GlobalMessages.post(context.getString(R.string.settings_open_failed))
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.reminder_request_exact_alarm)) }
                    }
                    if (!state.fullScreenOk && Build.VERSION.SDK_INT >= 34) {
                        OutlinedButton(
                            // v1.0.71 修复 v1.0.70 的**死按钮**：原实现 `runCatching { startActivity(
                            // Intent(ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)) }` 既漏 data=package:
                            // 又把 ActivityNotFoundException 静默吞掉 → HyperOS 上点了毫无反应。
                            onClick = {
                                if (!SystemSetupGuides.openFullScreenIntentSettings(context)) {
                                    GlobalMessages.post(context.getString(R.string.settings_open_failed))
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.reminder_request_fullscreen)) }
                    }
                    if (!state.batteryOk) {
                        OutlinedButton(
                            onClick = { SystemSetupGuides.requestIgnoreBatteryOptimizations(context) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.reminder_request_battery)) }
                    }
                    OutlinedButton(
                        onClick = {
                            context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            })
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.reminder_notification_settings)) }
                }

                // v1.0.72：小米 / HyperOS 专属指引。
                // 官方依据（小米《适配常见问题》）：§9 MIUI **默认不允许应用在锁屏上显示 Activity**，需用户主动授予；
                // §12 自启动**默认不开放**（含开机自启动与接收系统广播）；§10 这些权限**没有查询接口**，
                // 只能引导用户去权限管理页手动开启。故此处只给文字 + 跳转按钮，**不做状态行**（不做假状态）。
                if (XiaomiCompat.isXiaomi(Build.BRAND, Build.MANUFACTURER)) {
                    Spacer(Modifier.height(Spacing.md))
                    Text(stringResource(R.string.reminder_miui_title), style = MaterialTheme.typography.titleSmall)
                    Text(
                        stringResource(
                            R.string.reminder_miui_hint,
                            XiaomiCompat.requiredSwitches().map { stringResource(it) }.joinToString(" / "),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(Spacing.sm))
                    OutlinedButton(
                        onClick = {
                            if (!SystemSetupGuides.openMiuiPermissionEditor(context)) {
                                GlobalMessages.post(context.getString(R.string.settings_open_failed))
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.reminder_miui_open)) }
                }

                // ---- v1.0.62 C11：自启动引导（无公开查询接口，只给入口 + 说明，不做状态行）----
                Spacer(Modifier.height(Spacing.md))
                Text(stringResource(R.string.reminder_autostart), style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(R.string.reminder_autostart_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.xxs),
                )
                Spacer(Modifier.height(Spacing.xs))
                OutlinedButton(
                    onClick = { SystemSetupGuides.openAutoStartSettings(context) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.reminder_open_autostart)) }

                // ---- v1.0.62 C11：测试提醒（端到端链路验证）----
                Spacer(Modifier.height(Spacing.md))
                Text(stringResource(R.string.reminder_test), style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(R.string.reminder_test_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.xxs),
                )
                Spacer(Modifier.height(Spacing.xs))
                OutlinedButton(
                    onClick = {
                        ReminderTest.schedule(context)
                        testScheduled = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.reminder_send_test)) }
                if (testScheduled) {
                    Text(
                        stringResource(R.string.reminder_test_scheduled),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = Spacing.xs),
                    )
                }

                Text(
                    stringResource(R.string.reminder_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.sm),
                )
            }
            Spacer(Modifier.height(Spacing.xxl))
        }
    }
}

/**
 * 四项**可查询**状态。小米那几项（锁屏显示 / 后台弹出界面 / 自启动）官方明确「没有查询接口」，
 * 故刻意不进本结构——不做假状态行（见 `HANDOFF.md` §7）。
 */
internal data class ReminderCheckState(
    val notifOk: Boolean,
    val exactOk: Boolean,
    val fullScreenOk: Boolean,
    val batteryOk: Boolean,
    /** v1.0.73（P0-1）：最近一次闹钟重排是否出现过注册失败——「权限显示已授权」也可能一条都没排上。 */
    val alarmsOk: Boolean,
    /**
     * v1.0.84（批次 9）：闹钟台账**快照本体**，随状态一起带回。
     *
     * 原先 `alarmsOk` 在这里只留了一个布尔（由 `snapshot` 算出），页面渲染「闹钟注册实证行」
     * 时又单独调了一次 `ReminderHealth.snapshot(context)` 取 attempted / failed / lastError——
     * 同一帧两次同步读（SharedPreferences + AlarmManager binder），全在主线程。
     */
    val health: ReminderHealth.Snapshot,
) {
    val passed: Int get() = listOf(notifOk, exactOk, fullScreenOk, batteryOk, alarmsOk).count { it }
    val total: Int get() = 5
}

/**
 * 读四项状态，并在**每次回到前台**时重读。
 *
 * v1.0.62 的修复必须保留：本节原先只在首次组合时读一次，用户刚在系统设置里授权、切回来仍显示
 * 旧值（「已授权却显示未授予」）。入口行与二级页共用本函数，避免两处各写一遍而漂移。
 */
@Composable
internal fun rememberReminderCheckState(): ReminderCheckState {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var refreshTick by remember { mutableStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshTick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    // v1.0.84（批次 9）：台账快照**只读一次**，与四项状态共用同一份（页面渲染实证行不再重复读）。
    // 同步磁盘 / binder 调用留在组合期是既有事实，但一次组合只应发生一次。
    val health = remember(refreshTick) { ReminderHealth.snapshot(context) }
    return remember(refreshTick, health) { readReminderCheckState(context, health) }
}

private fun readReminderCheckState(context: Context, health: ReminderHealth.Snapshot): ReminderCheckState {
    val notifOk = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
    val exactOk = run {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= 31) am.canScheduleExactAlarms() else true
    }
    // v1.0.61 B9：Android 14+ 全屏 Intent 需用户显式授予；14 以下默认可用
    val fullScreenOk = run {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 34) nm.canUseFullScreenIntent() else true
    }
    // v1.0.62 C11：电池白名单（有官方查询接口，故可显示真实状态）
    val batteryOk = SystemSetupGuides.isIgnoringBatteryOptimizations(context)
    // v1.0.73（P0-1）：闹钟注册台账——权限全绿也可能「一条都没排上」（小米 MIUIOP(10014) 默认 ignore）
    val alarmsOk = !health.hasFailure
    return ReminderCheckState(notifOk, exactOk, fullScreenOk, batteryOk, alarmsOk, health)
}

/** 状态三重编码：图标 + 文字 + 颜色（此前是裸 `"✓"` / `"!"`）。 */
@Composable
private fun CheckRow(label: String, desc: String, ok: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        androidx.compose.material3.Icon(
            imageVector = if (ok) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
            contentDescription = if (ok) stringResource(R.string.common_passed) else stringResource(R.string.common_not_passed),
            modifier = Modifier.size(Size.iconMd),
            tint = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        )
    }
}
