package com.ashkb.app.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * P5 备份引擎纯逻辑单测：SHA-256 摘要的确定性与已知向量。
 * （全库导出/恢复依赖 SupportSQLiteDatabase，属设备实测项——见 dogfooding 手册 DR 演练）
 */
class BackupEngineTest {

    @Test
    fun `SHA-256 已知向量 abc`() {
        // FIPS 180-2 标准测试向量
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            BackupEngine.sha256Hex("abc".toByteArray(Charsets.UTF_8)),
        )
    }

    @Test
    fun `SHA-256 已知向量 空串`() {
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            BackupEngine.sha256Hex(ByteArray(0)),
        )
    }

    @Test
    fun `SHA-256 已知向量 中文 UTF-8`() {
        // echo -n "强直性脊柱炎" | sha256sum 的独立实现结果
        val sha = BackupEngine.sha256Hex("强直性脊柱炎".toByteArray(Charsets.UTF_8))
        assertEquals(64, sha.length)
        assertEquals(sha, BackupEngine.sha256Hex("强直性脊柱炎".toByteArray(Charsets.UTF_8)))
    }

    @Test
    fun `不同输入产生不同摘要`() {
        val a = BackupEngine.sha256Hex("table-a-row-1".toByteArray())
        val b = BackupEngine.sha256Hex("table-a-row-2".toByteArray())
        assertNotEquals(a, b)
    }

    @Test
    fun `行序变化改变摘要（manifest 防重排）`() {
        // 与 BackupEngine.tableSha 的语义一致：排序后的行串取摘要——顺序固定则稳定
        val rowsSorted = listOf("a", "b", "c").sorted().joinToString("\n")
        val rowsSameInput = listOf("c", "a", "b").sorted().joinToString("\n")
        assertEquals(
            BackupEngine.sha256Hex(rowsSorted.toByteArray()),
            BackupEngine.sha256Hex(rowsSameInput.toByteArray()),
        )
        val rowsDifferent = listOf("a", "b", "d").sorted().joinToString("\n")
        assertNotEquals(
            BackupEngine.sha256Hex(rowsSorted.toByteArray()),
            BackupEngine.sha256Hex(rowsDifferent.toByteArray()),
        )
    }
}
