package com.ashkb.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * v1.2.11（缺陷工单 A05）：恢复前的密钥域判定。
 *
 * 为什么值得单测：真机密钥在 AndroidKeyStore 里，JVM 单测造不出来；而这条判定的三种结论
 * 各自对应一个**后果完全不同**的动作（采纳 / 继续 / 阻止）。判反了不会有任何编译错误，
 * 只会在用户换机恢复后让全部附件静默解不开——只能靠断言钉住。
 */
class VaultKeyDomainTest {

    private fun key(seed: Int = 1): ByteArray = ByteArray(32) { (it + seed).toByte() }

    @Test
    fun `本机没有密钥时采纳备份的密钥（换机路径）`() {
        assertEquals(
            "本机从未生成密钥时必须采纳备份 key，否则换机恢复彻底走不通",
            VaultKeyDomain.Decision.ADOPT,
            VaultKeyDomain.decide(localKey = null, backupKey = key(3)),
        )
    }

    @Test
    fun `两侧密钥相同允许恢复`() {
        val k = key(7)
        assertEquals(
            VaultKeyDomain.Decision.PROCEED,
            VaultKeyDomain.decide(localKey = k.copyOf(), backupKey = k.copyOf()),
        )
    }

    @Test
    fun `本机有密钥但不同则阻止恢复`() {
        assertEquals(
            "这是 A05 的核心：不阻止就等于静默覆盖，备份里的附件恢复后必然解不开",
            VaultKeyDomain.Decision.MISMATCH,
            VaultKeyDomain.decide(localKey = key(1), backupKey = key(2)),
        )
    }

    @Test
    fun `只差一个字节也算不同`() {
        val a = key(5)
        val b = a.copyOf().also { it[31] = (it[31] + 1).toByte() }
        assertEquals(
            VaultKeyDomain.Decision.MISMATCH,
            VaultKeyDomain.decide(localKey = a, backupKey = b),
        )
    }

    @Test
    fun `备份不带密钥时保持旧行为`() {
        // v1 / v2 备份文件不含 vault key：无从比对，不应因为本机有 key 就拒绝
        assertEquals(
            VaultKeyDomain.Decision.PROCEED,
            VaultKeyDomain.decide(localKey = key(9), backupKey = null),
        )
    }

    @Test
    fun `两侧都没有密钥时也走旧行为`() {
        assertEquals(
            VaultKeyDomain.Decision.PROCEED,
            VaultKeyDomain.decide(localKey = null, backupKey = null),
        )
    }
}
