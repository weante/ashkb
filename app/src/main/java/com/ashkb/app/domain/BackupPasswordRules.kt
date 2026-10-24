package com.ashkb.app.domain

/**
 * v1.2.11（缺陷工单 C01）：备份口令的最短长度。
 *
 * 只约束**新建**备份（本机 / WebDAV）。恢复路径**不看这个常量**——旧备份可能是 6 位口令
 * 建的，拦下它们等于把用户已经存在的数据判死刑。这条区分是刻意的，不是遗漏：
 * 加密格式与 PBKDF2 迭代数都没变（工单明确不要求改），变的是「新口令的下限」。
 *
 * 为什么是 12：备份文件一旦泄露，攻击者拿到的是**离线**可爆破的密文，唯一成本就是
 * PBKDF2 迭代 × 口令熵。120k 次迭代在目标 SoC 上实测约 136 ms，比常见网站登录的
 * 限速弱得多——短口令（6 位数字/小写字母）在这个成本下并不构成实际门槛。
 *
 * 抽成常量是为了让 ViewModel 的校验与 UI 的按钮启用条件**同源**：
 * 两处各写一个 6/12 字面量，正是 C01 这种「校验与按钮不一致」缺陷的温床。
 */
internal object BackupPasswordRules {

    /** 新建备份口令的最短字符数（`String.length`，即 UTF-16 码元数）。 */
    const val MIN_LENGTH = 12

    /** 新建备份是否达到长度下限。恢复路径**不得**调用本函数。 */
    fun newBackupOk(password: String): Boolean = password.length >= MIN_LENGTH
}
