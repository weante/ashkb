package com.ashkb.app.domain

import java.time.LocalDateTime
import java.time.LocalTime

/**
 * v1.0.60 B8：免打扰时段判断。
 *
 * 纯函数，不依赖 Android。跨午夜支持（start=22:00, end=07:00 表示
 * 当晚 22:00 到次日 07:00）。
 *
 * 设计取舍：
 *  - `start == end` → 全天免打扰（极端配置，允许）
 *  - 采用「左闭右开」：start 时刻算在免打扰内，end 时刻不算
 *    （与 `[start, end)` 区间一致，避免 end 时刻的提醒既被静默又被正常投递的歧义）
 *
 * Receiver 侧用法：
 * ```
 * val silent = cfg.dndEnabled() && DndWindow.isInDnd(LocalDateTime.now(), cfg.dndStart(), cfg.dndEnd())
 * ```
 * DND 开关/时间变更只改 prefs，**无需重排闹钟**——Receiver 触发时实时读配置。
 *
 * v1.2.10（审查 L2）：两个时刻来自 SharedPreferences，**不是**构造函数参数——用户手改备份、
 * 旧版本写入的半截值、或将来某次迁移写坏一个字符，都会让 `LocalTime.parse` 抛
 * `DateTimeParseException`。抛出点在提醒链路上（[NotificationHelper] 在投递前调用），
 * 一条读不出的免打扰配置能让**所有**提醒在投递那一刻崩掉。读不出就当作「没有免打扰」：
 * 宁可多响一次（用户看得见、能去设置里改），不可静默不响。
 */
object DndWindow {

    /**
     * @param fire 待判断时刻
     * @param start "HH:mm" 免打扰开始
     * @param end "HH:mm" 免打扰结束
     * @return fire 是否落在免打扰窗口内；**任一时刻解析失败返回 false**（视为无免打扰）
     */
    fun isInDnd(fire: LocalDateTime, start: String, end: String): Boolean {
        val s = runCatching { LocalTime.parse(start) }.getOrNull() ?: return false
        val e = runCatching { LocalTime.parse(end) }.getOrNull() ?: return false
        val t = fire.toLocalTime()
        return if (s == e) {
            true
        } else if (s.isBefore(e)) {
            // 同日窗口（如 09:00-17:00）
            !t.isBefore(s) && t.isBefore(e)
        } else {
            // 跨午夜窗口（如 22:00-07:00）
            !t.isBefore(s) || t.isBefore(e)
        }
    }
}
