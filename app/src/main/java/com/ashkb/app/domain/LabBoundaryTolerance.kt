package com.ashkb.app.domain

import android.content.Context
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * v1.2.13（医学缺口③）：**按化验项**配置「临界」容差。
 *
 * 背景：此前 [ClinicalThresholds.LAB_NEAR_BOUNDARY_RATIO] 是全局唯一的 5%，而不同化验的
 * 量纲与临床习惯差得远——同样是 5%，对 0–20 mm/h 的 ESR 约等于 ±0.5 个单位，对 3.9–6.1
 * mmol/L 的血糖则约等于 ±0.11，前者几乎每条都报临界、后者一次都不报。5% 只是
 * [ClinicalThresholds] 里那个「量程百分比」的通用坑位，不该当成所有指标的唯一真值。
 *
 * 存哪儿、为什么不进库：
 *  - 这是**展示口径**，不是临床数据：它不参与任何诊断、不进 PDF 异常计数、不影响
 *    `lab_results.abnormal` 列（那一列仍只有 high/low/normal 三态，临界是实时算的）。
 *  - 项目已有的「用户偏好」全部走 SharedPreferences（提醒设置、补剂闹钟登记），
 *    没有通用设置表；为几条容差加一张表要连带一次库迁移，代价与收益不成比例。
 *  - 代价必须说明白：**SharedPreferences 不随备份走**（`BackupEngine` 只遍历
 *    `sqlite_master`），换机恢复后容差会回到默认 5%。这是刻意取舍，因为容差配错
 *    只会让某一项多报/少报「临界」，不会让数据出错。
 */
object LabBoundaryTolerance {

    /** 没配过某项时用的容差（= 全局默认，保持 v1.2.10 裁决项 5 的行为）。 */
    const val DEFAULT_RATIO = ClinicalThresholds.LAB_NEAR_BOUNDARY_RATIO

    /** 容差下限：0.5% 量程。再小等于「永远不报临界」，那应该直接不配而不是填 0。 */
    const val MIN_RATIO = 0.005f

    /** 容差上限：20% 量程。再大等于「整段参考范围都算临界」，警示会被自己淹没。 */
    const val MAX_RATIO = 0.2f

    private const val PREFS = "lab_boundary_tolerance"
    private const val KEY = "ratios"

    private const val LINE_SEPARATOR = "\n"
    private const val PAIR_SEPARATOR = "="

    private const val PERCENT_SCALE = 100f

    /**
     * 解析落盘的 `名称=百分比` 多行文本。
     *
     * 容忍脏值（历史配置被手工改过、旧版本写的格式不同）：解析不出数字、越界、名字为空
     * 的行一律丢弃，**不因为一行坏而整份配置作废**——否则用户会在毫不知情的情况下
     * 回到默认 5%，还以为自己配过。
     */
    fun parse(raw: String?): Map<String, Float> {
        if (raw.isNullOrBlank()) return emptyMap()
        val out = LinkedHashMap<String, Float>()
        raw.split(LINE_SEPARATOR).forEach { line ->
            val idx = line.indexOf(PAIR_SEPARATOR)
            if (idx <= 0) return@forEach
            val name = line.substring(0, idx).trim()
            if (name.isEmpty()) return@forEach
            val percent = line.substring(idx + 1).trim().toFloatOrNull() ?: return@forEach
            val ratio = percent / PERCENT_SCALE
            if (ratio < MIN_RATIO || ratio > MAX_RATIO) return@forEach
            out[name] = ratio
        }
        return out
    }

    /** 序列化为 [parse] 能读回的文本；越界值在这里就被丢弃，保证「存进去的都能读出来」。 */
    fun serialize(config: Map<String, Float>): String = config.entries
        .filter { it.key.isNotBlank() && it.value in MIN_RATIO..MAX_RATIO }
        .joinToString(LINE_SEPARATOR) { (name, ratio) ->
            "$name$PAIR_SEPARATOR${formatPercent(ratio)}"
        }

    /** 读设备上的配置；读失败（个别 ROM 的存储异常）按「没配过」处理，不让展示层崩。 */
    fun load(context: Context): Map<String, Float> = runCatching {
        parse(prefs(context).getString(KEY, null))
    }.getOrDefault(emptyMap())

    /**
     * 进程内缓存的配置。
     *
     * 为什么需要它：临界判据在列表里**逐行**被调用（一次抽血几十上百行），而
     * `getSharedPreferences` 虽已由系统缓存，每次仍要过一遍 XML 解析出来的 map 与
     * `parse()` 的字符串切分。Compose 每次重组都会重跑这些判定，逐行读盘是白费。
     *
     * 用 `StateFlow` 而不是普通变量：改配置后列表要立刻刷新（用户刚把 ESR 从 5% 调到 2%，
     * 还看着满屏「临界」会以为没保存成功）。
     */
    private val ratios = MutableStateFlow<Map<String, Float>>(emptyMap())

    private val loaded = java.util.concurrent.atomic.AtomicBoolean(false)

    /** UI 侧观察用；首次调用时按需从磁盘装载一次。 */
    fun observe(context: Context): StateFlow<Map<String, Float>> {
        ensureLoaded(context)
        return ratios
    }

    /** 当前生效的快照（不装载、不触发 IO）：给非 Compose 侧（PDF 导出）用。 */
    fun snapshot(): Map<String, Float> = ratios.value

    /** 装载一次；之后的 [save] 直接改内存并落盘。 */
    fun ensureLoaded(context: Context) {
        if (loaded.compareAndSet(false, true)) {
            ratios.value = load(context)
        }
    }

    fun save(context: Context, config: Map<String, Float>) {
        val clean = config.filterKeys { it.isNotBlank() }
            .filterValues { it.isFinite() && it in MIN_RATIO..MAX_RATIO }
        ratios.value = clean
        loaded.set(true)
        prefs(context).edit().putString(KEY, serialize(clean)).apply()
    }

    /** 测试用：把缓存清空，下一次 [ensureLoaded] 会重新读盘。 */
    internal fun resetForTest() {
        ratios.value = emptyMap()
        loaded.set(false)
    }

    /**
     * 取某一项生效的容差。
     *
     * 指标名比较**忽略首尾空白与大小写**：化验单里同一项会被写成 `ESR` / `Esr` / `esr`
     * （不同医院模板、AI 导入、手工录入都会出现），按字面查会让「配了却不生效」。
     * 返回 [DEFAULT_RATIO] 表示这一项没单独配过，不是「容差为 0」。
     */
    fun ratioFor(config: Map<String, Float>, testName: String): Float {
        val key = normalize(testName)
        if (key.isEmpty()) return DEFAULT_RATIO
        val hit = config.entries.firstOrNull { normalize(it.key) == key } ?: return DEFAULT_RATIO
        return hit.value
    }

    /** 百分比展示（配置界面用）。 */
    fun percentOf(ratio: Float): Float = ratio * PERCENT_SCALE

    /** 百分数 → 内部比例；越界返回 null，交给调用方报错而不是悄悄夹到边界上。 */
    fun ratioOfPercent(percent: Float?): Float? {
        if (percent == null || percent.isNaN()) return null
        val ratio = percent / PERCENT_SCALE
        return if (ratio in MIN_RATIO..MAX_RATIO) ratio else null
    }

    private fun normalize(raw: String): String = raw.trim().lowercase(Locale.US)

    /** 百分比只留一位小数：`5` 写 `5`、`5.5` 写 `5.5`，避免 `5.0000000001` 这类浮点尾巴。 */
    private fun formatPercent(ratio: Float): String {
        val percent = ratio * PERCENT_SCALE
        val rounded = Math.round(percent * 10f) / 10f
        return if (rounded % 1f == 0f) rounded.toInt().toString() else rounded.toString()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
