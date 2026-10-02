package com.ashkb.app.data.backup

import com.ashkb.app.data.repo.AttachmentRepository
import java.io.ByteArrayInputStream
import java.security.SecureRandom
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * v1.1.1（HIGH-2）：**附件读上限必须覆盖"传得上去的那些附件"**。
 *
 * 缺陷形态：上传侧闸门是 `AttachmentRepository.MAX_BYTES` = 20 MB，而 v1.0.86 把**所有**二进制
 * 读取（备份下载、附件懒下载、上传后回读）统一卡在 `BINARY_RESPONSE_LIMIT` = 6 MiB。
 * 于是一张 8 MB 的 MRI 报告照片**传得上去、列表里看得到，取回时必然抛 DavResponseTooLarge**，
 * 且用户没有任何途径提高上限——两个不同的上限被合并成了一条。
 *
 * 这里断言的是**两个上限之间的关系**（而不是某个魔法数字）：附件读上限必须大于上传闸门，
 * 且要装得下 `VaultCipher.encryptBlob` 的信封（8 字节 magic + 12 字节 IV + 16 字节 GCM tag）。
 * 真正的端到端证据是下面那条：造一个**恰好等于上传闸门**的明文，加密后必须能原样读回。
 *
 * 纯 JVM（`VaultCipher` 只用 javax.crypto），不碰网络、不碰 Android。
 */
class AttachmentDownloadLimitTest {

    @Test
    fun `附件读上限大于上传闸门且大于备份上限`() {
        assertTrue(
            "上传闸门是 20 MB（AttachmentRepository.MAX_BYTES），读上限必须比它大——" +
                "否则 6–20 MB 的附件就是一条只进不出的死路",
            WebDavClient.ATTACHMENT_RESPONSE_LIMIT > AttachmentRepository.MAX_BYTES,
        )
        assertTrue(
            "附件与备份不是同一条尺寸带：附件上限必须高于备份的 6 MiB",
            WebDavClient.ATTACHMENT_RESPONSE_LIMIT > WebDavClient.BINARY_RESPONSE_LIMIT,
        )
        assertEquals("备份读上限本身不变（v1.0.86 的取值不动）", 6L * 1024 * 1024, WebDavClient.BINARY_RESPONSE_LIMIT)
    }

    /** 恰好 20 MB（上传闸门允许的最大值）的附件：加密后的密文必须能被读回。 */
    @Test
    fun `恰好等于上传闸门的附件能加密后原样读回`() {
        val key = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val plain = ByteArray(AttachmentRepository.MAX_BYTES.toInt()) { (it % 251).toByte() }
        val blob = VaultCipher.encryptBlob(key, plain, "att-limit")

        assertTrue(
            "密文只比明文多一个信封（36 字节），必须落在附件读上限之内",
            blob.size <= WebDavClient.ATTACHMENT_RESPONSE_LIMIT,
        )

        val back = WebDavClient.readBounded(
            ByteArrayInputStream(blob), WebDavClient.ATTACHMENT_RESPONSE_LIMIT, "ashkb/attachments/x/y.enc",
        )
        assertEquals(blob.size, back.size)
        assertArrayEquals("读回的密文必须逐字节一致（不是截断的前半截）", blob, back)
    }

    /** 上限仍然是一堵墙：超过附件上限的响应整体拒绝，绝不静默截断。 */
    @Test
    fun `超过附件上限仍然整体拒绝`() {
        val over = ByteArray((WebDavClient.ATTACHMENT_RESPONSE_LIMIT + 1).toInt())
        try {
            WebDavClient.readBounded(
                ByteArrayInputStream(over), WebDavClient.ATTACHMENT_RESPONSE_LIMIT, "ashkb/attachments/x/y.enc",
            )
            fail("expected DavResponseTooLargeException")
        } catch (e: WebDavClient.DavResponseTooLargeException) {
            assertEquals(WebDavClient.ATTACHMENT_RESPONSE_LIMIT, e.limitBytes)
        }
    }
}
