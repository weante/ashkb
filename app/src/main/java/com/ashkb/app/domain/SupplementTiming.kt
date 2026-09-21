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

    private fun timesOf(raw: String?): List<Int> =
        raw?.split(',')?.mapNotNull { minutesOf(it) } ?: emptyList()

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

    /** 找出「矿物类补剂与螯合类用药同服、且间隔 < 2 小时」的组合（同名去重）。 */
    fun conflicts(supplements: List<Supplement>, medications: List<Medication>): List<Conflict> {
        val mineralSupps = supplements.filter { isMineral(it.category) }
        val chelating = medications.filter { isChelatingMed(it) }
        val out = mutableListOf<Conflict>()
        mineralSupps.forEach { s ->
            val sTimes = timesOf(s.times)
            chelating.forEach { m ->
                timesOf(m.takeTimes).forEach { mt ->
                    sTimes.forEach { st ->
                        val gap = abs(st - mt)
                        if (gap < MIN_GAP_MINUTES) out.add(Conflict(s.name, m.name, gap))
                    }
                }
            }
        }
        return out.distinctBy { it.supplementName to it.medName }
    }
}
