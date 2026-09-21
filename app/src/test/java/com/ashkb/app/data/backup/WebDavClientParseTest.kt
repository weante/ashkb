package com.ashkb.app.data.backup

import com.ashkb.app.domain.AttachmentPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** v1.0.36：PROPFIND 目录列举解析——远端校验的输入。纯 JVM，无需网络。 */
class WebDavClientParseTest {

    @Test
    fun `parses folders and files with directory detection`() {
        val xml = """
            <?xml version="1.0" encoding="utf-8"?>
            <D:multistatus xmlns:D="DAV:">
              <D:response>
                <D:href>/dav/ashkb/attachments/</D:href>
                <D:propstat><D:prop><D:resourcetype><D:collection/></D:resourcetype></D:prop></D:propstat>
              </D:response>
              <D:response>
                <D:href>/dav/ashkb/attachments/2026-09-21/</D:href>
                <D:propstat><D:prop><D:resourcetype><D:collection/></D:resourcetype></D:prop></D:propstat>
              </D:response>
              <D:response>
                <D:href>/dav/ashkb/attachments/2026-09-21/catt-abc123.enc</D:href>
                <D:propstat><D:prop><D:resourcetype/></D:prop></D:propstat>
              </D:response>
            </D:multistatus>
        """.trimIndent()

        val entries = WebDavClient.parseDavEntries(xml)
        assertEquals(2, entries.count { it.isDir })
        assertEquals("catt-abc123.enc", entries.single { !it.isDir }.name)
        assertTrue(entries.any { it.isDir && it.name == "2026-09-21" })
    }

    /** href 缺尾斜杠时，靠 resourcetype 的 collection 判定目录（服务器实现差异）。 */
    @Test
    fun `collection resourcetype marks directory without trailing slash`() {
        val xml = """
            <D:multistatus xmlns:D="DAV:">
              <D:response>
                <D:href>/dav/ashkb/attachments/2026-09-21</D:href>
                <D:propstat><D:prop><D:resourcetype><D:collection/></D:resourcetype></D:prop></D:propstat>
              </D:response>
            </D:multistatus>
        """.trimIndent()

        val e = WebDavClient.parseDavEntries(xml).single()
        assertEquals("2026-09-21", e.name)
        assertTrue(e.isDir)
    }

    /** 目录自身条目会解析出目录名——随后被 isValidFolder 挡掉（不是合法日期目录）。 */
    @Test
    fun `root self entry is not a valid folder`() {
        val xml = """
            <D:multistatus xmlns:D="DAV:">
              <D:response>
                <D:href>/dav/ashkb/attachments/</D:href>
                <D:propstat><D:prop><D:resourcetype><D:collection/></D:resourcetype></D:prop></D:propstat>
              </D:response>
            </D:multistatus>
        """.trimIndent()

        val e = WebDavClient.parseDavEntries(xml).single()
        assertEquals("attachments", e.name)
        assertFalse(AttachmentPath.isValidFolder(e.name))
    }

    /** href 带查询串（部分服务器加 ETag 参数）时应剥掉。 */
    @Test
    fun `query string is stripped from href`() {
        val xml = """
            <D:multistatus xmlns:D="DAV:">
              <D:response>
                <D:href>/dav/ashkb/attachments/2026-09-21/catt-a.enc?etag=1</D:href>
                <D:propstat><D:prop><D:resourcetype/></D:prop></D:propstat>
              </D:response>
            </D:multistatus>
        """.trimIndent()

        assertEquals("catt-a.enc", WebDavClient.parseDavEntries(xml).single().name)
    }

    @Test
    fun `empty xml yields no entries`() {
        assertTrue(WebDavClient.parseDavEntries("").isEmpty())
    }
}
