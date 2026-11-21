package com.ashkb.app.data.repo

import android.content.Context
import android.content.SharedPreferences
import com.ashkb.app.R
import com.ashkb.app.data.backup.WebDavClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * v1.2.20：`webdav_config` **明文凭据迁移**此前零测试（HANDOFF-STATUS 明确记着这条缺口）。
 *
 * 被保护的语义有三条，坏任何一条都是安全问题或数据丢失：
 *  1. v1.0.5 及以前的明文 `url`/`user`/`pass` → 加密写入 `*_v2`，**然后删掉旧键**
 *     （不删 = 明文永久留在 prefs 里，等于没加密）。
 *  2. R3：只有 `https://` 的旧配置值得迁移；`http://`、`ftp://`、畸形 scheme **一律丢弃**
 *     （迁过去等于把 Basic 凭据继续发给不安全端点）。
 *  3. 损坏 / 不可解的密文不许炸：读成空串，配置视为未配置。
 *
 * 为什么构造时要注入假加解密：`KeystoreCipher` 依赖 AndroidKeyStore，JVM 上直接
 * `KeyStoreException: AndroidKeyStore not found`（同 RestoreGate 抽出去的原因）。
 * 注入一个可逆假实现后，迁移逻辑本身才第一次真正跑起来；生产路径不传参、仍用 Keystore。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class BackupWebdavConfigMigrationTest {

    private val context: Context = RuntimeEnvironment.getApplication()

    /** 可逆假密文：能证明「落盘的不是明文」，也能对坏密文抛异常。 */
    private fun fakeEncrypt(plain: String): String = "F:" + plain.reversed()

    private fun fakeDecrypt(blob: String): String {
        require(blob.startsWith("F:")) { "bad ciphertext" }
        return blob.removePrefix("F:").reversed()
    }

    private fun repo() = BackupRepository(
        context = context,
        encryptCredential = ::fakeEncrypt,
        decryptCredential = ::fakeDecrypt,
    )

    private fun prefs(): SharedPreferences =
        context.getSharedPreferences("webdav_config", Context.MODE_PRIVATE)

    private fun seedPlaintext(url: String?, user: String?, pass: String?) {
        prefs().edit().apply {
            url?.let { putString("url", it) }
            user?.let { putString("user", it) }
            pass?.let { putString("pass", it) }
        }.commit()
    }

    @Test
    fun `https 明文配置会被加密迁移并删除旧键`() {
        seedPlaintext("https://dav.example.com/dav", "alice", "s3cret")

        val (url, user, pass) = repo().webdavConfig()

        assertEquals("https://dav.example.com/dav", url)
        assertEquals("alice", user)
        assertEquals("s3cret", pass)

        val p = prefs()
        assertFalse("旧明文 url 必须删除", p.contains("url"))
        assertFalse("旧明文 user 必须删除", p.contains("user"))
        assertFalse("旧明文 pass 必须删除", p.contains("pass"))

        val encUrl = p.getString("url_v2", null)
        assertNotNull("迁移后必须写出加密键", encUrl)
        assertTrue("落盘的必须是密文而不是明文", encUrl!!.startsWith("F:"))
        assertEquals("且必须能解回原值", "https://dav.example.com/dav", fakeDecrypt(encUrl))
    }

    @Test
    fun `http 明文配置一律丢弃且旧键删除`() {
        seedPlaintext("http://dav.example.com/dav", "alice", "s3cret")

        val (url, user, pass) = repo().webdavConfig()

        assertEquals("", url)
        assertEquals("", user)
        assertEquals("", pass)
        val p = prefs()
        assertFalse(p.contains("url"))
        assertFalse(p.contains("user"))
        assertFalse(p.contains("pass"))
        assertNull("不安全 scheme 不允许留下任何 v2 密文", p.getString("url_v2", null))
    }

    @Test
    fun `ftp 与畸形 scheme 的明文配置同样丢弃`() {
        seedPlaintext("ftp://dav.example.com/dav", "alice", "s3cret")
        assertEquals(Triple("", "", ""), repo().webdavConfig())

        seedPlaintext("dav.example.com", "alice", "s3cret")
        assertEquals(Triple("", "", ""), repo().webdavConfig())
    }

    @Test
    fun `只残留部分明文键时也按整组处理并清理`() {
        seedPlaintext(null, null, "s3cret")

        assertEquals(Triple("", "", ""), repo().webdavConfig())
        assertFalse(prefs().contains("pass"))
    }

    @Test
    fun `已加密配置可往返读写并覆盖`() {
        val r = repo()
        r.saveWebdavConfig("https://a.example.com/dav", "alice", "pw1")
        assertEquals(Triple("https://a.example.com/dav", "alice", "pw1"), r.webdavConfig())

        r.saveWebdavConfig("https://b.example.com/dav", "bob", "pw2")
        assertEquals(Triple("https://b.example.com/dav", "bob", "pw2"), r.webdavConfig())
        assertTrue(r.webdavConfigured())
    }

    @Test
    fun `损坏的密文不会抛异常，配置视为空`() {
        prefs().edit()
            .putString("url_v2", "this-is-not-a-cipher")
            .putString("user_v2", fakeEncrypt("alice"))
            .putString("pass_v2", fakeEncrypt("pw"))
            .commit()

        val (url, user, _) = repo().webdavConfig()
        assertEquals("坏密文读成空串", "", url)
        assertEquals("好密文照常解出", "alice", user)
        assertFalse("URL 读空即视为未配置", repo().webdavConfigured())
    }

    @Test
    fun `保存 http 地址会被 HTTPS 白名单拦下且不落盘`() {
        try {
            repo().saveWebdavConfig("http://dav.example.com/dav", "alice", "pw")
            fail("http:// 必须被拒绝")
        } catch (e: WebDavClient.DavException) {
            assertEquals(context.getString(R.string.backup_err_https_only), e.message)
        }
        assertNull(prefs().getString("url_v2", null))
    }
}
