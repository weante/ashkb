package com.ashkb.app.data.backup

import android.content.Context
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * v1.2.21（独立审查 F4）：vault key 的**采纳与回滚**。
 *
 * 为什么需要这一组：换机恢复会在写库**之前**采纳备份里的密钥（顺序不能反，否则 pre-restore
 * 快照没密钥可加密）。一旦随后写库失败，数据库事务整体回滚、密钥却留下了——本机从此被锁进
 * 备份那把 key 的密钥域：本机后续备份/附件都用新 key，原设备的备份再进来一律判 MISMATCH 被拒，
 * `generate()` 又拒绝覆盖，App 内没有清除出口，用户只能清应用数据。
 *
 * 注入假加解密是因为 [KeystoreCipher] 依赖 AndroidKeyStore，JVM 上必然抛
 * `KeyStoreException: AndroidKeyStore not found`（与 `BackupRepository` 的注入先例同理）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class VaultKeyStoreTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Before
    fun clearPrefs() {
        context.getSharedPreferences("vault_config", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    private fun store(): VaultKeyStore = VaultKeyStore(context, ::fakeEncrypt, ::fakeDecrypt)

    /** 假密文：可逆且明显不是真密文，测试只关心「存进去能读回来」与「读不出来时不炸」。 */
    private fun fakeEncrypt(plain: String): String = "F:" + plain.reversed()

    private fun fakeDecrypt(blob: String): String {
        require(blob.startsWith("F:")) { "bad ciphertext" }
        return blob.removePrefix("F:").reversed()
    }

    private fun key(seed: Int) = ByteArray(32) { seed.toByte() }

    @Test
    fun `adoptIfAbsent stores the key when the device has none`() {
        val store = store()
        assertNull("前置条件：本机无密钥", store.current())

        assertTrue(store.adoptIfAbsent(key(1)))

        assertArrayEquals(key(1), store.current())
        assertEquals(VaultKeyStore.KeyState.PRESENT, store.state())
    }

    @Test
    fun `adoptIfAbsent never overwrites an existing key`() {
        val store = store()
        assertTrue(store.adoptIfAbsent(key(1)))

        assertFalse("本机已有可用密钥时必须拒绝采纳", store.adoptIfAbsent(key(2)))

        assertArrayEquals(key(1), store.current())
    }

    @Test
    fun `adoptIfAbsent rejects a key of the wrong length`() {
        val store = store()

        assertFalse(store.adoptIfAbsent(ByteArray(16)))

        assertNull(store.current())
    }

    @Test
    fun `rollback removes exactly the key adopted in this run`() {
        val store = store()
        assertTrue(store.adoptIfAbsent(key(7)))

        assertTrue(store.rollbackAdoptionIfCurrent(key(7)))

        assertNull("回滚后必须回到「本机没有密钥」", store.current())
        assertEquals(VaultKeyStore.KeyState.ABSENT, store.state())
    }

    @Test
    fun `rollback never touches a different key`() {
        val store = store()
        assertTrue(store.adoptIfAbsent(key(9)))

        assertFalse("当前密钥不是这次采纳的那把，绝不能删", store.rollbackAdoptionIfCurrent(key(3)))

        assertArrayEquals(key(9), store.current())
    }

    @Test
    fun `rollback is a no-op when the device has no key`() {
        val store = store()

        assertFalse(store.rollbackAdoptionIfCurrent(key(1)))

        assertNull(store.current())
    }
}
