package com.ashkb.app.domain

/**
 * v1.2.11（缺陷工单 B01）：AI 报告导入的结果。
 *
 * 为什么要有这个返回类型而不是 `Unit`：重复导入必须**让用户看见**「此前已导入」。
 * 旧实现导入后一律弹「已导入 N 项」，用户把同一张报告粘两次会看到两条一模一样的成功提示，
 * 无从判断第二次到底写没写——而真实情况是写了，数据翻倍。
 *
 * 放在 `domain` 而不是仓库内部类：`CheckupViewModel` 要用它决定回显哪条文案，
 * 纯 Kotlin 数据（不依赖 Android）才能在普通单测里断言。
 */
sealed interface ImportResult {

    /** 本次新写入的行数（化验 = 报告里的行数，影像 = 1）。 */
    data class Imported(val count: Int) : ImportResult

    /** 该指纹此前已导入过：本次**一行都没写**。 */
    data object AlreadyImported : ImportResult
}
