package com.ashkb.app.data.repo

import android.content.Context
import androidx.room.Room
import com.ashkb.app.data.db.AppDatabase
import com.ashkb.app.data.entity.CheckupAttachment
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * v1.2.20：附件**级联删除的磁盘分支**此前零测试（HANDOFF-STATUS 的已知缺口之一）。
 *
 * 这条分支为什么危险：级联删复诊记录时，行和字节要分两步走——先在一个事务里处理行，
 * 提交之后再删文件。顺序反了就是灾难：先删行，`file_name` 随之消失，磁盘上的孤儿文件
 * 永远找不回来（占空间且无法追溯）；先删文件再回滚事务，则恢复出「有行无文件」的坏数据。
 *
 * 同时钉住墓碑口径：有远端副本且同步开启的附件**只置墓碑不物理删**，否则远端会留下
 * 「本地已无、远端还在」的孤儿，且重试链断掉。
 *
 * 为什么不直接用 `AppDatabase.get(context)`：Robolectric 的 legacy SQLite 影子把连接
 * 绑定到线程，而 Room 默认查询执行器是多线程池——同一个语句在 A 线程 prepare、B 线程
 * 执行就会报 `Illegal connection pointer`。这里给测试库配**单线程**执行器，语义与真机
 * 完全一致（Room 在真机上也是串行访问），只是把不确定性去掉。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class AttachmentCascadeDeleteTest {

    private val context: Context = RuntimeEnvironment.getApplication()
    private lateinit var executor: ExecutorService
    private lateinit var db: AppDatabase
    private lateinit var repo: AttachmentRepository

    @Before
    fun setUp() {
        val name = "attachment-cascade-test.db"
        context.deleteDatabase(name)
        executor = Executors.newSingleThreadExecutor()
        db = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(*AppDatabase.ALL_MIGRATIONS)
            .setQueryExecutor(executor)
            .setTransactionExecutor(executor)
            .allowMainThreadQueries()
            .build()
        repo = AttachmentRepository(context, db)
    }

    @After
    fun tearDown() {
        db.close()
        executor.shutdown()
        dir().listFiles()?.forEach { it.delete() }
    }

    private fun dir(): File = File(context.filesDir, "checkup_attachments").apply { mkdirs() }

    private fun seedFile(name: String): File =
        File(dir(), name).apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }

    private fun attachment(
        id: String,
        fileName: String,
        checkupId: String? = "chk-1",
        remotePath: String? = null,
    ) = CheckupAttachment(
        id = id,
        checkupId = checkupId,
        kind = "PHOTO",
        fileName = fileName,
        mime = "image/jpeg",
        sizeBytes = 4,
        createdAt = "2026-11-21T10:00:00",
        remotePath = remotePath,
    )

    private fun insert(vararg rows: CheckupAttachment) = runBlocking {
        rows.forEach { db.checkupAttachmentDao().upsert(it) }
    }

    @Test
    fun `同步关闭时级联删除物理删行，随后删除磁盘文件`() = runBlocking {
        val f1 = seedFile("a.jpg")
        val f2 = seedFile("b.jpg")
        val rows = listOf(attachment("catt-1", "a.jpg"), attachment("catt-2", "b.jpg"))
        insert(*rows.toTypedArray())
        repo.setSyncEnabled(false)

        repo.deleteRowsForCascade(rows)

        assertNull("行必须被物理删除", db.checkupAttachmentDao().byId("catt-1"))
        assertNull("行必须被物理删除", db.checkupAttachmentDao().byId("catt-2"))
        assertTrue("删行阶段不得动磁盘（顺序由调用方保证）", f1.exists() && f2.exists())

        repo.deleteLocalFiles(rows)

        assertFalse("磁盘文件必须删掉，否则留下孤儿", f1.exists())
        assertFalse("磁盘文件必须删掉，否则留下孤儿", f2.exists())
    }

    @Test
    fun `有远端副本且同步开启时只置墓碑，行仍在但不对外可见`() = runBlocking {
        val f = seedFile("c.enc")
        val row = attachment("catt-3", "c.enc", remotePath = "2026-11-21/catt-3.enc")
        insert(row)
        repo.setSyncEnabled(true)

        repo.deleteRowsForCascade(listOf(row))

        val kept = db.checkupAttachmentDao().byId("catt-3")
        assertNotNull("墓碑行必须保留，供远端清理重试", kept)
        assertNotNull("deleted_at 必须非空（软删标记）", kept!!.deletedAt)
        assertTrue(
            "墓碑行不得出现在「某复诊记录名下附件」里",
            db.checkupAttachmentDao().listByCheckup("chk-1").isEmpty(),
        )

        repo.deleteLocalFiles(listOf(row))
        assertFalse("本地字节仍要在提交之后删掉（远端已有副本）", f.exists())
    }

    @Test
    fun `混合同步状态时按各自分支处理`() = runBlocking {
        val local = attachment("catt-4", "d.jpg")
        val remote = attachment("catt-5", "e.enc", remotePath = "2026-11-21/catt-5.enc")
        seedFile("d.jpg")
        seedFile("e.enc")
        insert(local, remote)
        repo.setSyncEnabled(true)

        repo.deleteRowsForCascade(listOf(local, remote))

        assertNull("无远端副本 → 物理删行", db.checkupAttachmentDao().byId("catt-4"))
        val kept = db.checkupAttachmentDao().byId("catt-5")
        assertNotNull("有远端副本 → 留墓碑", kept)
        assertNotNull(kept!!.deletedAt)
        assertEquals("可见列表只剩墓碑（不可见）", 0, db.checkupAttachmentDao().listAll().size)
    }

    @Test
    fun `文件已不存在时删除不抛异常`() = runBlocking {
        val row = attachment("catt-6", "missing.jpg")
        insert(row)
        repo.setSyncEnabled(false)

        repo.deleteRowsForCascade(listOf(row))
        repo.deleteLocalFiles(listOf(row)) // 文件从来没有存在过

        assertNull(db.checkupAttachmentDao().byId("catt-6"))
    }
}
