package com.ashkb.app.domain

/**
 * v1.2.11（缺陷工单 A05）：**恢复前先判断「本机与备份是否同一个密钥域」**。
 *
 * 背景：v3 备份文件的附件是用**随备份携带的 vault key** 加密的。恢复路径此前直接
 * `adoptIfAbsent(key)` 并**丢弃返回值**——本机已有可用密钥（典型场景：这台手机上已经
 * 建过附件，用的是它自己的 key）时它返回 false，恢复却照常继续。结果是库被覆盖成备份内容，
 * 而本机拿自己的 key 去解备份的附件，每张都必然失败；用户要等到点开某张附件才发现，
 * 那时唯一的退路只剩 pre-restore 快照。
 *
 * 这里只做**判定**，不碰密钥、不碰库：三种结论交给调用方各自处理。
 * 抽成纯函数是为了能纯单测——真机密钥在 AndroidKeyStore 里，JVM 单测造不出来。
 *
 * 为什么不「静默换 key」：换掉本机 key 会让本机**已上传到云端的附件**立刻全部解不开，
 * 与 `VaultKeyStore` 的「永不更换」设计承诺直接冲突。两个方向的静默损坏都不可接受，
 * 所以唯一正确的动作是在写入前中止，并明确告诉用户原因。
 */
internal object VaultKeyDomain {

    enum class Decision {
        /** 本机没有可用密钥（换机 / 首次恢复）——采纳备份的 key，现有换机路径保持不变 */
        ADOPT,

        /** 两侧 key 相同——直接恢复 */
        PROCEED,

        /** 本机有可用密钥但**与备份不同**——阻止恢复，不得改动任何数据或密钥 */
        MISMATCH,
    }

    /**
     * @param localKey 本机当前可用密钥（`VaultKeyStore.current()`；读不出 / 从未生成时为 null）
     * @param backupKey 备份携带的 v3 vault key（v1 / v2 备份为 null）
     */
    fun decide(localKey: ByteArray?, backupKey: ByteArray?): Decision = when {
        // 备份没带 key（v1 / v2）：无从比对，保持旧行为——这类文件不含附件密钥域信息
        backupKey == null -> Decision.PROCEED
        // 本机没有可用 key：换机恢复的正常路径，采纳备份的 key
        localKey == null -> Decision.ADOPT
        // 两侧都有：必须一致，否则附件必然解不开
        java.security.MessageDigest.isEqual(localKey, backupKey) -> Decision.PROCEED
        else -> Decision.MISMATCH
    }
}
