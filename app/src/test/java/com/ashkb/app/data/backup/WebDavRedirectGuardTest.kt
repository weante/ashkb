package com.ashkb.app.data.backup

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.2.22（独立审查 S1）：**WebDAV 不跟随重定向**。
 *
 * ### 为什么有这条测试
 * 报告把它列为「待验证」：`HttpURLConnection` 默认 `instanceFollowRedirects = true`，而
 * [WebDavClient] 在发请求前就已经把 `Authorization: Basic` 挂到头上。于是服务器回一个 302，
 * 客户端就自己带着凭据去访问 `Location`。跨主机时 JDK/Android 会剥掉该头，但同主机的路径
 * 跳转仍带着凭据——而 WebDAV 基址是用户手填的，不能假设「跳过去的地方」就是「他填的地方」。
 *
 * ### 它能证明什么 / 不能证明什么
 * 能证明：① 状态码分类正确（3xx 才算重定向，不在边界上摇摆）；② **源码里确实把自动跟随关掉了**，
 * 且每个「读响应码」的方法都经过同一个闸门。不能证明：真实跳转服务器上的端到端行为——
 * 那需要一台会跳转的 WebDAV，单测环境没有（这项留给真机/联调）。
 * 之所以用源码守卫：本仓已有同类先例（`KbTierLabelGuardTest`、`VerticalScrollInventoryTest`），
 * 而「有人为了兼容某台服务器把 instanceFollowRedirects 改回 true」是最可能发生的回归。
 */
class WebDavRedirectGuardTest {

    private val sourceRoot: File =
        listOf(File("src/main/java"), File("app/src/main/java")).firstOrNull { it.isDirectory }
            ?: error("未找到主源码目录（工作目录=${File(".").absolutePath}）")

    private val webDavSource: String
        get() = File(sourceRoot, "com/ashkb/app/data/backup/WebDavClient.kt").readText()

    @Test
    fun `3xx 才算重定向_边界不摇摆`() {
        assertTrue(WebDavClient.isRedirectCode(300))
        assertTrue(WebDavClient.isRedirectCode(301))
        assertTrue(WebDavClient.isRedirectCode(302))
        assertTrue(WebDavClient.isRedirectCode(307))
        assertTrue(WebDavClient.isRedirectCode(308))
        assertTrue(WebDavClient.isRedirectCode(399))
        assertFalse("299 是成功，不是重定向", WebDavClient.isRedirectCode(299))
        assertFalse("400 是客户端错误，不是重定向", WebDavClient.isRedirectCode(400))
        assertFalse(WebDavClient.isRedirectCode(200))
        assertFalse(WebDavClient.isRedirectCode(0))
    }

    @Test
    fun `连接显式关闭了自动跟随重定向`() {
        val code = codeLines(webDavSource)
        assertTrue(
            "WebDavClient 必须显式设置 instanceFollowRedirects = false——" +
                "HttpURLConnection 默认会跟随 3xx，Basic 凭据会被带到 Location 指向的地址",
            code.contains("instanceFollowRedirects = false"),
        )
        assertFalse(
            "不能把自动跟随重定向打开（哪怕是为了兼容某台服务器）：凭据会随 3xx 外发",
            code.contains("instanceFollowRedirects = true"),
        )
    }

    /** 只留代码行，去掉 `//` 行注释与 KDoc 行——注释里出现 `= true` 不算打开了开关。 */
    private fun codeLines(src: String): String = src.lineSequence()
        .filterNot { it.trimStart().startsWith("//") || it.trimStart().startsWith("*") }
        .joinToString("\n")

    @Test
    fun `每个读响应码的方法都经过重定向闸门`() {
        val src = webDavSource
        // 闸门本身 + 至少覆盖 get/put/delete/mkcol/propfind 五条请求路径
        val calls = Regex("rejectRedirect\\(").findAll(src).count()
        assertTrue(
            "rejectRedirect 的调用点太少（实际 $calls 处）——新增读响应码的方法时必须同样过闸门",
            calls >= 5,
        )
        assertTrue(
            "3xx 必须复用既有文案 backup_err_dav_3xx（提示用户改填最终地址），不要另造一条",
            src.contains("R.string.backup_err_dav_3xx"),
        )
    }
}
