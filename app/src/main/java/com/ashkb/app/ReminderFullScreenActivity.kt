package com.ashkb.app

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ashkb.app.ui.theme.AshkbTheme
import com.ashkb.app.ui.theme.Spacing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * v1.0.61 B9：升级链末级「强提醒」的全屏界面。
 *
 * 由 [com.ashkb.app.reminder.NotificationHelper.postMedReminder] 以
 * `fullScreenIntent` 拉起——设备锁屏时可直接唤醒亮屏并覆盖锁屏，
 * 让「连续两次未确认」的用药提醒真正落到用户眼前（漏服的最后一道防线）。
 *
 * 设计要点：
 *  - `showWhenLocked` + `turnScreenOn`：锁屏可见 + 点亮屏幕（minSdk 26 → 需 API 27+ 的
 *    编程接口；27 以下由 Manifest 属性兜底，实际影响可忽略）
 *  - `taskAffinity=""` + `excludeFromRecents`：独立任务栈、不进最近任务，
 *    处理完 `finish()` 直接回到用户原本的界面
 *  - 不做「稍后」的二次闹钟——升级链已在排程时全部预排，此处只需结束本界面
 */
class ReminderFullScreenActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyLockScreenFlags()
        renderFromIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        renderFromIntent(intent)
    }

    private fun applyLockScreenFlags() {
        // setShowWhenLocked / setTurnScreenOn 为 API 27 引入；minSdk 26 需守卫
        // （27 以下由 Manifest 的 android:showWhenLocked / turnScreenOn 属性兜底，且仅在 27+ 生效）
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
    }

    private fun renderFromIntent(intent: Intent?) {
        val medId = intent?.getStringExtra(EXTRA_MED_ID)
        val slotKey = intent?.getStringExtra(EXTRA_SLOT_KEY)
        val slotTime = intent?.getStringExtra(EXTRA_SLOT_TIME)

        setContent {
            AshkbTheme {
                StrongReminderScreen(
                    medId = medId,
                    slotKey = slotKey,
                    slotTime = slotTime,
                    onTaken = { finish() },
                    onLater = { finish() },
                )
            }
        }
    }

    companion object {
        const val EXTRA_MED_ID = "med_id"
        const val EXTRA_SLOT_KEY = "slot_key"
        const val EXTRA_SLOT_TIME = "slot_time"
    }
}

@Composable
private fun StrongReminderScreen(
    medId: String?,
    slotKey: String?,
    slotTime: String?,
    onTaken: () -> Unit,
    onLater: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var medName by remember { mutableStateOf("") }
    var medDose by remember { mutableStateOf("") }

    LaunchedEffect(medId) {
        if (medId == null) return@LaunchedEffect
        runCatching {
            val app = context.applicationContext as? AshkbApplication ?: return@runCatching
            val med = app.medicationRepository.medicationById(medId)
            medName = med?.name ?: ""
            medDose = med?.dose ?: ""
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(Spacing.xl),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(R.string.strong_reminder_heading),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Spacing.xl))
            Text(
                listOf(medName, medDose).filter { it.isNotBlank() }.joinToString(" "),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            if (!slotTime.isNullOrBlank()) {
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    stringResource(R.string.strong_reminder_time_hint, slotTime),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(Spacing.xl))
            Button(
                onClick = {
                    val app = context.applicationContext as? AshkbApplication
                    if (app != null && medId != null) {
                        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                            runCatching { app.medicationRepository.checkInByMedId(medId, slotKey, slotTime) }
                            withContext(Dispatchers.Main) {
                                com.ashkb.app.reminder.NotificationHelper.cancel(context, medId, slotKey)
                            }
                        }
                    }
                    onTaken()
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) {
                Text(stringResource(R.string.strong_reminder_taken), style = MaterialTheme.typography.titleMedium, color = Color.White)
            }
            Spacer(Modifier.height(Spacing.md))
            OutlinedButton(
                onClick = onLater,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(28.dp),
            ) {
                Text(stringResource(R.string.strong_reminder_later), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
