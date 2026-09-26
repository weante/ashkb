package com.ashkb.app.ui.me

import android.app.TimePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.ashkb.app.R
import com.ashkb.app.data.db.AppDatabase
import com.ashkb.app.data.repo.ReminderConfigRepository
import com.ashkb.app.reminder.BasdaiReminderScheduler
import com.ashkb.app.reminder.CheckupReminderScheduler
import com.ashkb.app.reminder.ExerciseReminderScheduler
import com.ashkb.app.ui.theme.Spacing
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * v1.0.59 B5：多源提醒设置面板。
 *
 * 三源开关（复诊 / 运动）+ BASDAI 评估周期单选。每次变更即时写入
 * [ReminderConfigRepository] 并在 IO 协程内重排对应源提醒——与 BootReceiver /
 * AshkbApplication 的 reschedule 口径完全一致（开关关闭则 cancelAllFuture）。
 *
 * 不做跨源联动：三源独立，互不影响。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderSettingsSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cfg = remember { ReminderConfigRepository(context) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var checkup by remember { mutableStateOf(cfg.checkupEnabled()) }
    var exercise by remember { mutableStateOf(cfg.exerciseEnabled()) }
    var basdaiCycle by remember { mutableStateOf(cfg.basdaiCycleDays()) }
    // v1.0.60 B8：免打扰时段
    var dndEnabled by remember { mutableStateOf(cfg.dndEnabled()) }
    var dndStart by remember { mutableStateOf(cfg.dndStart()) }
    var dndEnd by remember { mutableStateOf(cfg.dndEnd()) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                stringResource(R.string.reminder_settings_title),
                style = MaterialTheme.typography.titleMedium,
            )

            // ---- 复诊提醒开关 ----
            SettingSwitchRow(
                title = stringResource(R.string.reminder_checkup_switch),
                checked = checkup,
                onCheckedChange = { v ->
                    checkup = v
                    cfg.setCheckupEnabled(v)
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            val db = AppDatabase.get(context)
                            val records = db.checkupRecordDao().listAll()
                            if (v) {
                                runCatching {
                                    CheckupReminderScheduler.rescheduleAll(
                                        context, records, LocalDate.now(), LocalDateTime.now(),
                                    )
                                }
                            } else {
                                CheckupReminderScheduler.cancelAllFuture(context, records)
                            }
                        }
                    }
                },
            )

            // ---- 运动提醒开关 ----
            SettingSwitchRow(
                title = stringResource(R.string.reminder_exercise_switch),
                checked = exercise,
                onCheckedChange = { v ->
                    exercise = v
                    cfg.setExerciseEnabled(v)
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            if (v) {
                                val db = AppDatabase.get(context)
                                runCatching {
                                    val plan = db.exercisePlanDao().active()
                                    val hasLogged = db.exerciseLogDao()
                                        .byDate(LocalDate.now().toString()).isNotEmpty()
                                    ExerciseReminderScheduler.rescheduleAll(
                                        context, plan, hasLogged, LocalDate.now(), LocalDateTime.now(),
                                    )
                                }
                            } else {
                                ExerciseReminderScheduler.cancelAllFuture(context, LocalDate.now())
                            }
                        }
                    }
                },
            )

            // ---- v1.0.60 B8：免打扰时段 ----
            SettingSwitchRow(
                title = stringResource(R.string.reminder_dnd_switch),
                subtitle = stringResource(R.string.reminder_dnd_subtitle),
                checked = dndEnabled,
                onCheckedChange = { v ->
                    dndEnabled = v
                    cfg.setDndEnabled(v)
                    // 只改 prefs，无需重排闹钟——Receiver 触发时实时读 DND 配置
                },
            )
            if (dndEnabled) {
                DndTimeRow(
                    label = stringResource(R.string.reminder_dnd_start),
                    value = dndStart,
                    onPicked = { dndStart = it; cfg.setDndStart(it) },
                )
                DndTimeRow(
                    label = stringResource(R.string.reminder_dnd_end),
                    value = dndEnd,
                    onPicked = { dndEnd = it; cfg.setDndEnd(it) },
                )
            }

            // ---- BASDAI 评估周期 ----
            Text(
                stringResource(R.string.reminder_basdai_cycle_label),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = Spacing.sm),
            )
            Column(Modifier.selectableGroup()) {
                ReminderConfigRepository.CYCLE_CHOICES.forEach { days ->
                    val labelRes = when (days) {
                        7L -> R.string.reminder_basdai_cycle_7
                        14L -> R.string.reminder_basdai_cycle_14
                        28L -> R.string.reminder_basdai_cycle_28
                        56L -> R.string.reminder_basdai_cycle_56
                        else -> R.string.reminder_basdai_cycle_84
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    ) {
                        RadioButton(
                            selected = basdaiCycle == days,
                            onClick = {
                                basdaiCycle = days
                                cfg.setBasdaiCycleDays(days)
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        val db = AppDatabase.get(context)
                                        runCatching {
                                            BasdaiReminderScheduler.rescheduleAll(
                                                context, db.basdaiDao().latest(), days,
                                                LocalDate.now(), LocalDateTime.now(),
                                            )
                                        }
                                    }
                                }
                            },
                        )
                        Text(stringResource(labelRes), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            Spacer(Modifier.height(Spacing.sm))
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.common_close)) }
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall)
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** v1.0.60 B8：免打扰起止时间选择行，点击弹出系统 TimePickerDialog。 */
@Composable
private fun DndTimeRow(label: String, value: String, onPicked: (String) -> Unit) {
    val context = LocalContext.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xs)
            .clickable {
                val parts = value.split(":")
                val h = parts.getOrNull(0)?.toIntOrNull() ?: 22
                val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
                TimePickerDialog(
                    context,
                    { _, hour, minute ->
                        onPicked(String.format(Locale.US, "%02d:%02d", hour, minute))
                    },
                    h, m, true,
                ).show()
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
