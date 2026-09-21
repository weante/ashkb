package com.ashkb.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** v1.0.35：附件在 WebDAV 上的路径口径（日期文件夹 + 不可读文件名）。 */
class AttachmentPathTest {

    @Test
    fun `folder is the attachment content date`() {
        assertEquals("2026-09-21", AttachmentPath.folderOf("2026-09-21T10:30:00"))
        assertEquals("2026-09-21", AttachmentPath.folderOf("2026-09-21"))
    }

    /** 脏数据（空串 / 非法日期）退回占位目录而不是抛异常——上传流程不该因一行坏数据整体失败。 */
    @Test
    fun `malformed date falls back to placeholder folder`() {
        assertEquals("0000-00-00", AttachmentPath.folderOf(""))
        assertEquals("0000-00-00", AttachmentPath.folderOf("not-a-date"))
        assertEquals("0000-00-00", AttachmentPath.folderOf("2026-9-1T00:00:00"))
    }

    @Test
    fun `file name is opaque attachment id plus enc`() {
        assertEquals("catt-abc123.enc", AttachmentPath.fileNameOf("catt-abc123"))
    }

    @Test
    fun `remote path combines date folder and opaque name`() {
        assertEquals(
            "2026-09-21/catt-abc123.enc",
            AttachmentPath.remotePathOf("2026-09-21T10:30:00", "catt-abc123"),
        )
    }

    @Test
    fun `full path is under attachments dir`() {
        assertEquals(
            "ashkb/attachments/2026-09-21/catt-abc123.enc",
            AttachmentPath.fullPathOf("2026-09-21/catt-abc123.enc"),
        )
        assertEquals("ashkb/attachments", AttachmentPath.DIR)
    }

    @Test
    fun `valid remote paths are accepted`() {
        assertTrue(AttachmentPath.isValidRemotePath("2026-09-21/catt-abc.enc"))
        assertTrue(AttachmentPath.isValidRemotePath("0000-00-00/catt-x.enc"))
    }

    /** 备份里的 remote_path 是外部输入：必须挡住路径穿越与绝对路径。 */
    @Test
    fun `path traversal and absolute paths are rejected`() {
        assertFalse(AttachmentPath.isValidRemotePath(""))
        assertFalse(AttachmentPath.isValidRemotePath("../../etc/passwd"))
        assertFalse(AttachmentPath.isValidRemotePath("2026-09-21/../../x.enc"))
        assertFalse(AttachmentPath.isValidRemotePath("/abs/x.enc"))
        assertFalse(AttachmentPath.isValidRemotePath("a/b/c.enc"))          // 多一层
        assertFalse(AttachmentPath.isValidRemotePath("2026-09-21/x"))        // 无 .enc
        assertFalse(AttachmentPath.isValidRemotePath("2026-09-21\\x.enc"))  // 反斜杠
        assertFalse(AttachmentPath.isValidRemotePath("2026-09-21/"))         // 无文件名
    }
}
