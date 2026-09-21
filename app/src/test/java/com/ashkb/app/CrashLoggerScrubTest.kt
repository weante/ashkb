package com.ashkb.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * v1.0.44（S5）：崩溃留档的凭据脱敏。
 *
 * 崩溃堆栈里可能带出 WebDAV 地址内嵌的凭据，落盘前必须滤掉——医疗类应用不该把凭据写进
 * 任何持久化文件，即便该文件在 app 私有目录且不上报。
 */
class CrashLoggerScrubTest {

    @Test
    fun `url embedded user and password are masked`() {
        assertEquals(
            "https://***@dav.example.org/ashkb",
            scrubCredentials("https://alice:s3cret@dav.example.org/ashkb"),
        )
    }

    @Test
    fun `user only is masked too`() {
        assertEquals("https://***@host/dav", scrubCredentials("https://alice@host/dav"))
    }

    /** 无内嵌凭据的 URL 必须原样保留（主机名是定位问题需要的信息，不敏感）。 */
    @Test
    fun `plain url is untouched`() {
        val s = "https://dav.example.org/ashkb/backup/x.enc"
        assertEquals(s, scrubCredentials(s))
    }

    /** 路径里的 `@` 不是凭据，不能被误伤。 */
    @Test
    fun `at sign inside path is untouched`() {
        val s = "https://dav.example.org/ashkb/a@b.enc"
        assertEquals(s, scrubCredentials(s))
    }

    /** 真实形态：多行堆栈里夹一条带凭据的消息。 */
    @Test
    fun `credentials inside a stack trace are masked`() {
        val trace = """
            com.ashkb.app.data.backup.WebDavClient${'$'}DavException: 无法连接 https://bob:pw@dav.example.org/dav
                at com.ashkb.app.data.backup.WebDavClient.url(WebDavClient.kt:41)
        """.trimIndent()
        val out = scrubCredentials(trace)
        assertFalse(out.contains("bob"))
        assertFalse(out.contains("pw@"))
        assertEquals(true, out.contains("https://***@dav.example.org/dav"))
    }

    /** 不含 URL 的普通文本不受影响。 */
    @Test
    fun `text without url is unchanged`() {
        val s = "java.lang.IllegalStateException: boom\n\tat A.b(A.kt:1)"
        assertEquals(s, scrubCredentials(s))
    }
}
