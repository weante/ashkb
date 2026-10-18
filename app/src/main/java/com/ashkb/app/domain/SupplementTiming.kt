package com.ashkb.app.domain

import com.ashkb.app.data.entity.Medication
import com.ashkb.app.data.entity.Supplement
import kotlin.math.abs

/**
 * B11（v1.0.38）：营养素与用药「时间错开」提醒。
 *
 * 依据（药理学常识，非个体用药决策）：钙等二价矿物会与**左甲状腺素**、
 * **四环素类 / 喹诺酮类抗生素**、**铁剂**等在肠道发生螯合、降低吸收，
 * 一般建议**间隔 2 小时以上**服用。
 *
 * App 只提示「同一时间窗内同服」，不给具体用药调整建议。
 * 纯函数、无 Android 依赖，可单测。
 */
object SupplementTiming {

    /** 建议最小间隔（分钟）。 */
    const val MIN_GAP_MINUTES = 120

    /** 一天的分钟数：环形距离的模。 */
    private const val MINUTES_PER_DAY = 24 * 60

    /**
     * v1.1.1（M2）：**环形距离**——两个「当日分钟数」之间最短的那一侧间隔。
     *
     * 为什么不能用 `abs(a - b)`：时刻是**环**上的点，23:50 与 00:15 的真实间隔是 25 分钟
     * （左甲状腺素 23:50 + 钙剂 00:15，典型的螯合冲突），而线性差给出 1415 分钟 → 120 分钟规则
     * 不触发 → **跨午夜的螯合提醒静默失效**。补剂/用药的服用时刻由用户自填，跨零点组合完全可能。
     *
     * 返回 [0, 720]：超过半天的那一侧永远不是"最短间隔"。
     */
    fun circularGapMinutes(a: Int, b: Int): Int {
        val linear = abs(a - b)
        return minOf(linear, MINUTES_PER_DAY - linear)
    }

    /** 会与钙 / 矿物螯合的通用名或中文名片段（小写包含匹配）。 */
    private val CHELATION_KEYS = listOf(
        "levothyroxine", "euthyrox", "左甲状腺素", "优甲乐",
        "tetracycline", "doxycycline", "minocycline", "四环素", "多西环素", "米诺环素",
        "ciprofloxacin", "levofloxacin", "moxifloxacin", "环丙沙星", "左氧氟沙星", "莫西沙星",
        "ferrous", "ferric", "iron", "铁剂", "硫酸亚铁",
    )

    data class Conflict(val supplementName: String, val medName: String, val gapMinutes: Int)

    /** `HH:mm` → 当日分钟数；非法返回 null。 */
    fun minutesOf(hhmm: String): Int? {
        val parts = hhmm.trim().split(':')
        if (parts.size != 2) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        if (h !in 0..23 || m !in 0..59) return null
        return h * 60 + m
    }

    /**
     * `times` / `take_times` → 当日分钟数列表。
     *
     * v1.2.10（审查 CRITICAL-1）：**实现换掉了**。此前是 `raw?.split(',')?.mapNotNull { minutesOf(it) }`
     * —— 而写入侧（`MedEditScreen` 的 `JSONArray(times).toString()`、`WellnessScreen` 同理）存的是
     * **JSON 数组**，`["08:00"]` 按 `,` 切只有一段、`minutesOf` 要求 `HH:mm` 两段 → 全部 null
     * → 此函数恒返回空表 → [conflicts] 恒空 → **「矿物补剂与螯合类药物同服」的提示从未生效过**。
     * 现在走 [ScheduleCalc.parseTimes]，与排程、营养页时间轴同一份解析。
     *
     * 注意 `minutesOf` 仍然保留：它服务于**用户手输单值**的场景（例如表单里正在编辑的字符串），
     * 与"库里那串 JSON"是两件事。
     */
    private fun timesOf(raw: String?): List<Int> =
        ScheduleCalc.parseTimes(raw).mapNotNull { minutesOf(it) }

    /** 钙 / 矿物类补剂（需要与上述药物错开）。 */
    fun isMineral(category: String): Boolean {
        val c = category.lowercase()
        return c == "calcium" || c.contains("钙") || c.contains("mineral") ||
            c.contains("iron") || c.contains("锌") || c.contains("镁")
    }

    /** 该药是否属于已知「与矿物螯合」类。 */
    fun isChelatingMed(med: Medication): Boolean {
        val key = "${med.nameKey} ${med.name}".lowercase()
        return CHELATION_KEYS.any { key.contains(it) }
    }

    /**
     * 找出「矿物类补剂与螯合类用药同服、且间隔 < 2 小时」的组合（同名去重）。
     *
     * v1.2.10（审查 L4）：去重时**取最短的那个间隔**，而不是第一次碰上的那个。
     *
     * 多槽位交叉会命中同一对名字若干次（钙 08:00/20:00 × 优甲乐 07:30/09:00 就有 4 种组合），
     * 旧的 `distinctBy { … }` 保留**遍历顺序里的第一条**——迭代是「药时刻在外、补剂时刻在内」，
     * 于是报出来的可能是 90 分钟，而真实最短间隔是 30 分钟。提示文案与「差多少」的判读
     * 都建立在这个数字上，取错等于把最严重的那次同服说轻了。
     */
    fun conflicts(supplements: List<Supplement>, medications: List<Medication>): List<Conflict> {
        val mineralSupps = supplements.filter { isMineral(it.category) }
        val chelating = medications.filter { isChelatingMed(it) }
        val worst = linkedMapOf<Pair<String, String>, Conflict>()
        mineralSupps.forEach { s ->
            val sTimes = timesOf(s.times)
            chelating.forEach { m ->
                timesOf(m.takeTimes).forEach { mt ->
                    sTimes.forEach { st ->
                        // v1.1.1（M2）：环形距离——23:50 与 00:15 的间隔是 25 分钟，不是 1415
                        val gap = circularGapMinutes(st, mt)
                        if (gap >= MIN_GAP_MINUTES) return@forEach
                        val key = s.name to m.name
                        val prev = worst[key]
                        if (prev == null || gap < prev.gapMinutes) worst[key] = Conflict(s.name, m.name, gap)
                    }
                }
            }
        }
        return worst.values.toList()
    }
}
