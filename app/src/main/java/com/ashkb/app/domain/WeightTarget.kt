package com.ashkb.app.domain

/**
 * 体重目标区间判定（v1.0.33，规划缺口 C9）。
 *
 * 规划 M2 要求体重「目标区间提示」，此前只有体重 / BMI / 趋势，没有目标区间。
 * 目标由医生给定（如激素减量期、生物制剂增重期）或用户自我管理设定。
 *
 * 纯函数、无 Android 依赖，可单测。
 */
object WeightTarget {

    enum class Status {
        /** 未设目标（上下限都为空） */
        NO_TARGET,

        /** 只填了一侧——无法判定区间，提示补全 */
        INCOMPLETE,

        BELOW,
        IN_RANGE,
        ABOVE,
    }

    /**
     * 规范化目标区间：容忍用户把上下限填反（low > high 时自动交换）。
     * 返回 null 表示两侧都缺失。
     */
    fun normalize(low: Double?, high: Double?): Pair<Double, Double>? {
        val l = low ?: return null
        val h = high ?: return null
        return if (l <= h) l to h else h to l
    }

    /**
     * 判定当前体重相对目标区间的状态。
     *
     * @param weight 当前体重（kg），null = 无体重数据
     * @param low / high 目标区间上下限（kg），可为 null
     */
    fun status(weight: Double?, low: Double?, high: Double?): Status {
        if (low == null && high == null) return Status.NO_TARGET
        if (low == null || high == null) return Status.INCOMPLETE
        if (weight == null) return Status.INCOMPLETE
        val (l, h) = normalize(low, high) ?: return Status.INCOMPLETE
        return when {
            weight < l -> Status.BELOW
            weight > h -> Status.ABOVE
            else -> Status.IN_RANGE
        }
    }

    /** 距目标区间边界的差值（kg）：偏低返回负值（需增重多少才达标），偏高返回正值。 */
    fun deviation(weight: Double?, low: Double?, high: Double?): Double? {
        if (weight == null) return null
        val (l, h) = normalize(low, high) ?: return null
        return when {
            weight < l -> weight - l
            weight > h -> weight - h
            else -> 0.0
        }
    }
}
