package com.ashkb.app.data.repo

import android.content.Context
import android.content.SharedPreferences
import com.ashkb.app.domain.SedentaryReminder

/**
 * 提醒配置（v1.0.59 B5）。
 *
 * 存普通 prefs（非密钥），与 [AttachmentRepository] 的 `attachment_sync_enabled` 同模式：
 * 不跨备份恢复——换机后用户需重新开关。CHANGELOG 已注明，符合既有取舍。
 *
 * 三源配置：
 *  - `basdai_cycle_days`：BASDAI 评估周期（默认 28，候选 7/14/28/56/84）
 *  - `checkup_reminder_enabled`：复诊提醒开关（默认 true）
 *  - `exercise_reminder_enabled`：运动提醒开关（默认 true）
 *
 * v1.0.60 B8 新增免打扰时段（DND）配置：
 *  - `dnd_enabled`：免打扰开关（默认 false）
 *  - `dnd_start`：免打扰开始时刻 "HH:mm"（默认 22:00）
 *  - `dnd_end`：免打扰结束时刻 "HH:mm"（默认 07:00）
 *  DND 时段内提醒静默投递（不响不震，通知栏仍可见），**不顺延、不重排**——
 *  Receiver 触发时实时读配置，开关/时间变更只写 prefs。
 *
 * 用药提醒不在此处——用药提醒由「药单本身」驱动，没有总开关（停药即归档，不再提醒）。
 */
class ReminderConfigRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun basdaiCycleDays(): Long = prefs.getLong(KEY_BASDAI_CYCLE, DEFAULT_BASDAI_CYCLE)
    fun setBasdaiCycleDays(days: Long) = prefs.edit().putLong(KEY_BASDAI_CYCLE, days).apply()

    fun checkupEnabled(): Boolean = prefs.getBoolean(KEY_CHECKUP_ENABLED, true)
    fun setCheckupEnabled(on: Boolean) = prefs.edit().putBoolean(KEY_CHECKUP_ENABLED, on).apply()

    fun exerciseEnabled(): Boolean = prefs.getBoolean(KEY_EXERCISE_ENABLED, true)
    fun setExerciseEnabled(on: Boolean) = prefs.edit().putBoolean(KEY_EXERCISE_ENABLED, on).apply()

    // ---- v1.0.60 B8：免打扰时段 ----
    fun dndEnabled(): Boolean = prefs.getBoolean(KEY_DND_ENABLED, false)
    fun setDndEnabled(on: Boolean) = prefs.edit().putBoolean(KEY_DND_ENABLED, on).apply()

    fun dndStart(): String = prefs.getString(KEY_DND_START, DEFAULT_DND_START) ?: DEFAULT_DND_START
    fun setDndStart(value: String) = prefs.edit().putString(KEY_DND_START, value).apply()

    fun dndEnd(): String = prefs.getString(KEY_DND_END, DEFAULT_DND_END) ?: DEFAULT_DND_END
    fun setDndEnd(value: String) = prefs.edit().putString(KEY_DND_END, value).apply()

    // ---- v1.0.68 C8a：久坐起身提醒 ----
    /** 默认 **关闭**——一天最多十几次提醒，必须由用户显式开启。 */
    fun sedentaryEnabled(): Boolean = prefs.getBoolean(KEY_SEDENTARY_ENABLED, false)
    fun setSedentaryEnabled(on: Boolean) = prefs.edit().putBoolean(KEY_SEDENTARY_ENABLED, on).apply()

    fun sedentaryIntervalMin(): Int =
        prefs.getInt(KEY_SEDENTARY_INTERVAL, SedentaryReminder.DEFAULT_INTERVAL_MIN)
    fun setSedentaryIntervalMin(min: Int) = prefs.edit().putInt(KEY_SEDENTARY_INTERVAL, min).apply()

    fun sedentaryStartHour(): Int =
        prefs.getInt(KEY_SEDENTARY_START, SedentaryReminder.DEFAULT_START_HOUR)
    fun setSedentaryStartHour(hour: Int) = prefs.edit().putInt(KEY_SEDENTARY_START, hour).apply()

    fun sedentaryEndHour(): Int =
        prefs.getInt(KEY_SEDENTARY_END, SedentaryReminder.DEFAULT_END_HOUR)
    fun setSedentaryEndHour(hour: Int) = prefs.edit().putInt(KEY_SEDENTARY_END, hour).apply()

    companion object {
        const val PREFS_NAME = "reminder_config"
        const val DEFAULT_BASDAI_CYCLE = 28L

        const val KEY_BASDAI_CYCLE = "basdai_cycle_days"
        const val KEY_CHECKUP_ENABLED = "checkup_reminder_enabled"
        const val KEY_EXERCISE_ENABLED = "exercise_reminder_enabled"

        // v1.0.60 B8
        const val KEY_DND_ENABLED = "dnd_enabled"
        const val KEY_DND_START = "dnd_start"
        const val KEY_DND_END = "dnd_end"
        const val DEFAULT_DND_START = "22:00"
        const val DEFAULT_DND_END = "07:00"

        // v1.0.68 C8a：久坐起身提醒
        const val KEY_SEDENTARY_ENABLED = "sedentary_reminder_enabled"
        const val KEY_SEDENTARY_INTERVAL = "sedentary_interval_min"
        const val KEY_SEDENTARY_START = "sedentary_start_hour"
        const val KEY_SEDENTARY_END = "sedentary_end_hour"

        /** 候选周期清单（天）：周 / 双周 / 月 / 双月 / 季。 */
        val CYCLE_CHOICES = listOf(7L, 14L, 28L, 56L, 84L)
    }
}
