package com.ashkb.app.domain

/** 格式信息里的等级编码，顺序由规范表 12 固定（不是强度顺序：M=0、L=1、H=2、Q=3）。 */
private const val FORMAT_MEDIUM = 0
private const val FORMAT_LOW = 1
private const val FORMAT_HIGH = 2
private const val FORMAT_QUARTILE = 3

/** 规范附录 B 给出的大致纠错能力（百分比）。 */
private const val RECOVERY_LOW = 7
private const val RECOVERY_MEDIUM = 15
private const val RECOVERY_QUARTILE = 25
private const val RECOVERY_HIGH = 30

/**
 * 二维码的四个纠错等级（ISO/IEC 18004 表 12）。
 *
 * 枚举顺序按**纠错强度升序**排列（LOW < MEDIUM < QUARTILE < HIGH），`ordinal` 直接用作
 * [QrTables] 那张 `[等级][版本]` 表的下标。注意 [formatBits] 与 `ordinal` 不是一回事：
 * 写进图案的格式信息用的是规范里的 2 位编码（M 是 0、L 是 1、H 是 2、Q 是 3），
 * 这个反直觉的顺序是规范定的，不能按强度顺序推。
 *
 * 常量放在文件顶层而不是 companion：枚举条目的初始化早于 companion，
 * 在构造参数里引用 companion 成员会编译失败。
 */
internal enum class QrEcc(
    /** 写进格式信息的 2 位编码（规范表 12 的「纠错等级指示符」）。 */
    val formatBits: Int,
    /** 大致可承受的码字错误比例，只用于界面提示。 */
    val approximateRecoveryPercent: Int,
) {
    LOW(FORMAT_LOW, RECOVERY_LOW),
    MEDIUM(FORMAT_MEDIUM, RECOVERY_MEDIUM),
    QUARTILE(FORMAT_QUARTILE, RECOVERY_QUARTILE),
    HIGH(FORMAT_HIGH, RECOVERY_HIGH),
}
