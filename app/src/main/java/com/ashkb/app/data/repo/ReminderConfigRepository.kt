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
 * 唯一的例外是 v1.0.77（批次 3b）的**漏服补发去重标记**（[missedDoseAlertedDate]）：
 * 它不是开关，而是「今天这条通知发过没有」的一次性状态，与提醒层同处一个 prefs 最省事。
 */
class ReminderConfigRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * BASDAI 评估周期（天）。
     *
     * v1.2.4：候选档位从 7/14/28/56/84 收窄为 1/7/30。历史值（如默认的 28）已不在
     * [CYCLE_CHOICES] 里，直接返回会让设置页一个单选都不选中、也让排程按一个界面上
     * 看不到的周期走。这里把**不在候选清单里的值归一到默认档并写回**——
     * 归一结果对用户可见（单选会选中「每月」），不留隐形的旧值。
     */
    fun basdaiCycleDays(): Long {
        val stored = prefs.getLong(KEY_BASDAI_CYCLE, DEFAULT_BASDAI_CYCLE)
        if (stored in CYCLE_CHOICES) return stored
        setBasdaiCycleDays(DEFAULT_BASDAI_CYCLE)
        return DEFAULT_BASDAI_CYCLE
    }
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

    // ---- v1.0.77（批次 3b）：漏服补发的「已提醒过的日期」----

    /**
     * 漏服补发通知**已投递过的归属日**（null = 还没投递过）。
     *
     * 为什么放在这里、而不是新建一个 store：这个标记天然是「提醒层的一次性状态」，
     * 与 dnd / 各源开关同属一个关注点（都是「怎么提醒」），而 `reminder_config` 这份 prefs
     * 已经承担了这件事；为**一个 key** 再开一份 prefs 只会多一个「换机后要重新理解」的文件。
     * 与既有取舍一致：不跨备份恢复——换机后顶多多收到一条昨天的提醒，无损。
     *
     * 语义：记录**昨天那个日期**（而不是「今天提醒过了」）。于是：
     *  · 同一天内多次触发（冷启动、开机、改时钟）只会命中同一条通知；
     *  · 跨到第二天后，比较值自然是新日期，无需任何清理逻辑。
     */
    fun missedDoseAlertedDate(): String? = prefs.getString(KEY_MISSED_ALERT_DATE, null)

    /** 记下「[date] 这一天的漏服已经提醒过了」。 */
    fun setMissedDoseAlertedDate(date: String) =
        prefs.edit().putString(KEY_MISSED_ALERT_DATE, date).apply()

    companion object {
        const val PREFS_NAME = "reminder_config"

        /**
         * v1.2.4：默认周期由 28 天（每四周）改为 30 天（每月）。
         *
         * 维护者要求 BASDAI 自评间隔只留「每日 / 每周 / 每月」三档
         * （原来五档是 7/14/28/56/84 天，即周/双周/四周/八周/十二周）。
         * 语义上「每月」= 30 天；已落库的 28 天由 [basdaiCycleDays] 归一到 [DEFAULT_BASDAI_CYCLE]。
         */
        const val DEFAULT_BASDAI_CYCLE = 30L

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

        // v1.0.77（批次 3b）：漏服补发的去重标记（存「已提醒过的归属日」）
        const val KEY_MISSED_ALERT_DATE = "missed_dose_alert_date"

        /**
         * 候选周期清单（天）：每日 / 每周 / 每月。
         *
         * v1.2.4 之前是 7/14/28/56/84 五档。改动后**必须**经 [basdaiCycleDays] 读，
         * 否则历史存下的 14/28/56/84 会让「BASDAI 评估间隔」区一个单选都不选中
         * （`basdaiCycle == days` 永不成立），看起来就是「一排空心圆没人被选上」。
         */
        val CYCLE_CHOICES = listOf(1L, 7L, 30L)
    }
}
