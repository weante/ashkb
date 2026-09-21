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

    // ---- v1.0.36：远端校验 ----

    @Test
    fun `valid folder accepts date or placeholder only`() {
        assertTrue(AttachmentPath.isValidFolder("2026-09-21"))
        assertTrue(AttachmentPath.isValidFolder("0000-00-00"))
        assertFalse(AttachmentPath.isValidFolder("2026-9-1"))
        assertFalse(AttachmentPath.isValidFolder("attachments"))
        assertFalse(AttachmentPath.isValidFolder(""))
    }

    @Test
    fun `managed remote path requires date folder and enc file`() {
        assertTrue(AttachmentPath.isManagedRemotePath("2026-09-21/catt-abc.enc"))
        assertTrue(AttachmentPath.isManagedRemotePath("0000-00-00/catt-x.enc"))
        assertFalse(AttachmentPath.isManagedRemotePath("misc/catt-abc.enc"))   // 非日期目录
        assertFalse(AttachmentPath.isManagedRemotePath("2026-09-21/readme.txt"))
        assertFalse(AttachmentPath.isManagedRemotePath("2026-09-21/"))
        assertFalse(AttachmentPath.isManagedRemotePath("../2026-09-21/x.enc"))
    }

    // ---- v1.0.43：段字符白名单（收紧百分号编码等绕过） ----

    /**
     * 百分号编码可绕过原先的 `..` 字面检查：`a%2f%2e%2e%2fx.enc` 只有 1 个字面 `/`、
     * 无字面 `..`、以 `.enc` 结尾 —— 旧实现会放行，服务端解码后即越出附件目录。
     */
    @Test
    fun `percent encoded traversal is rejected`() {
        assertFalse(AttachmentPath.isValidRemotePath("2026-09-21/a%2f%2e%2e%2fx.enc"))
        assertFalse(AttachmentPath.isManagedRemotePath("2026-09-21/a%2f%2e%2e%2fx.enc"))
        assertFalse(AttachmentPath.isValidRemotePath("2026-09-21/a%2fb.enc"))
    }

    /** `?` / `#` 会截断 URL，空白与控制字符同理——都在白名单之外。 */
    @Test
    fun `url delimiter and whitespace characters are rejected`() {
        assertFalse(AttachmentPath.isValidRemotePath("2026-09-21/x?.enc"))
        assertFalse(AttachmentPath.isValidRemotePath("2026-09-21/x#.enc"))
        assertFalse(AttachmentPath.isValidRemotePath("2026-09-21/x .enc"))
        assertFalse(AttachmentPath.isValidRemotePath("2026-09-21/x\ty.enc"))
    }

    /** 文件名主干不得为空（`.enc` 本身不是附件）。 */
    @Test
    fun `bare enc extension is rejected`() {
        assertFalse(AttachmentPath.isValidRemotePath("2026-09-21/.enc"))
    }

    @Test
    fun `reconcile splits missing and orphans`() {
        val expected = setOf("2026-09-21/catt-a.enc", "2026-09-21/catt-b.enc")
        val remote = setOf("2026-09-21/catt-b.enc", "2026-09-22/catt-c.enc")
        val r = AttachmentPath.reconcile(expected, remote)
        assertEquals(setOf("2026-09-21/catt-a.enc"), r.missing)
        assertEquals(setOf("2026-09-22/catt-c.enc"), r.orphans)
    }

    /** 远端目录可能被用户放进无关文件：绝不能进孤儿清理集。 */
    @Test
    fun `reconcile never treats unmanaged remote files as orphans`() {
        val r = AttachmentPath.reconcile(emptySet(), setOf("misc/x.enc", "2026-09-21/readme.txt"))
        assertTrue(r.orphans.isEmpty())
    }

    @Test
    fun `reconcile is empty when both sides agree`() {
        val s = setOf("2026-09-21/catt-a.enc")
        val r = AttachmentPath.reconcile(s, s)
        assertTrue(r.missing.isEmpty())
        assertTrue(r.orphans.isEmpty())
    }
}
