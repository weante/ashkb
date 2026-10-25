package com.ashkb.app.ui.backup

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import com.ashkb.app.AshkbApplication
import com.ashkb.app.R
import com.ashkb.app.data.backup.BackupEngine
import com.ashkb.app.data.backup.VaultCipher
import com.ashkb.app.data.backup.WebDavClient
import com.ashkb.app.data.db.AppDatabase
import com.ashkb.app.data.db.AttachmentSyncCounts
import com.ashkb.app.data.entity.BackupLedger
import com.ashkb.app.data.repo.BackupRepository
import com.ashkb.app.data.repo.PreRestoreSnapshot
import com.ashkb.app.data.repo.PreRestoreStore
import com.ashkb.app.domain.BackupPasswordRules
import com.ashkb.app.reminder.ReminderScheduler
import com.ashkb.app.ui.ReminderNotice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * P4 R20 备份与数据自主 ViewModel。
 * v1.0.22：状态流统一为私有 MutableStateFlow + 只读 StateFlow 暴露，
 * 清除 `as MutableStateFlow` 强转（审查报告 P2，运行时非受检向下转型）。
 */
class BackupViewModel(
    private val app: Context,
    private val repo: BackupRepository,
) : ViewModel() {

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    /** W1：长操作（WebDAV 备份）的阶段文案，busy 期间 UI 实时显示当前步骤。 */
    private val _stage = MutableStateFlow("")
    val stage: StateFlow<String> = _stage

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    val ledger: StateFlow<List<BackupLedger>> = repo.observeLedger()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ---- WebDAV 已存配置（密码不回显；sheet 预填地址与用户名） ----
    private val _davUrl = MutableStateFlow("")
    private val _davUser = MutableStateFlow("")
    val davUrl: StateFlow<String> = _davUrl
    val davUser: StateFlow<String> = _davUser

    // ---- W4：WebDAV 远程恢复（列表 + 下载） ----
    private val _davBackups = MutableStateFlow<List<WebDavClient.DavBackupFile>>(emptyList())
    val davBackups: StateFlow<List<WebDavClient.DavBackupFile>> = _davBackups

    /** 一次性事件：远程备份下载完成（Screen 消费后清空，等价本地选好文件）。 */
    private val _davPicked = MutableStateFlow<Pair<String, ByteArray>?>(null)
    val davPicked: StateFlow<Pair<String, ByteArray>?> = _davPicked

    // ---- A2：备份恢复码（v1.0.27）----
    private val _recoveryCodeSet = MutableStateFlow(repo.hasRecoveryCode())
    val recoveryCodeSet: StateFlow<Boolean> = _recoveryCodeSet

    /** 一次性事件：恢复码明文待展示（生成 / 查看后，Screen 弹窗展示并消费）。 */
    private val _recoveryReveal = MutableStateFlow<String?>(null)
    val recoveryReveal: StateFlow<String?> = _recoveryReveal

    init {
        val (u, usr) = repo.webdavConfig()
        _davUrl.value = u
        _davUser.value = usr
        // 附件统计不在这里拉：页面用 LaunchedEffect 拉，既满足"进页面就有数"，
        // 又能在每次回到该页时刷新，避免 init 只跑一次的陈旧快照
        // v1.2.11（缺陷工单 B07）：撤销入口的快照同样交给页面的 LaunchedEffect 刷新，
        // 这样「上次恢复留下的快照」不会因为 VM 存活时间而陈旧（应用重启、恢复完成都会变）。
    }

    // ---- 恢复流程状态（协议 §6 五步） ----
    /** 已旁路解密 + 自校验通过的备份（尚未写入主库）。 */
    private val _pendingRestore = MutableStateFlow<BackupRepository.DecryptedFile?>(null)
    val pendingRestore: StateFlow<BackupRepository.DecryptedFile?> = _pendingRestore
    private val _pendingFileName = MutableStateFlow<String?>(null)
    val pendingFileName: StateFlow<String?> = _pendingFileName
    private val _restoreResult = MutableStateFlow<BackupEngine.VerifyResult?>(null)
    val restoreResult: StateFlow<BackupEngine.VerifyResult?> = _restoreResult

    /** R2：恢复语义二选一，默认完整回滚（用户拍板）。 */
    private val _restoreMode = MutableStateFlow(BackupEngine.RestoreMode.FULL_ROLLBACK)
    val restoreMode: StateFlow<BackupEngine.RestoreMode> = _restoreMode
    fun setRestoreMode(mode: BackupEngine.RestoreMode) { _restoreMode.value = mode }

    // ---- v1.2.11（缺陷工单 B07）：pre-restore 快照的应用内撤销 ----
    /**
     * 最近一次恢复前的自动快照。null = 没有可用快照 ⇒ UI **不渲染**撤销入口
     * （工单：「无快照时不显示空入口」）。
     */
    private val _preRestoreSnapshot = MutableStateFlow<PreRestoreSnapshot?>(null)
    val preRestoreSnapshot: StateFlow<PreRestoreSnapshot?> = _preRestoreSnapshot

    private val preRestoreStore = PreRestoreStore(app)

    /** 恢复口令（内存暂存，用毕清零；pre-restore 快照复用同一口令）。 */
    private var restorePassword: CharArray? = null

    private fun wipeRestorePassword() {
        restorePassword?.fill('0')
        restorePassword = null
    }

    fun clearMessage() { _message.value = null }
    private fun info(msg: String) { _message.value = msg }
    private fun fail(msg: String) { _message.value = msg }

    /**
     * v1.2.5：把异常里的资源化文案解析成当前语言。
     * data 层（VaultCipher / BackupEngine）没有 Context，异常同时带中文 msg 与 resId/resArgs；
     * resId == 0（或非这两类异常）时回落 msg。[resArgs] 里的 `List<*>` 用本地化枚举分隔符拼接。
     */
    private fun errText(e: Throwable?): String {
        val resId = when (e) {
            is VaultCipher.VaultException -> e.resId
            is BackupEngine.BackupException -> e.resId
            else -> 0
        }
        if (resId == 0) {
            // v1.2.7（批次 14 / R5）：以前这里是 `return e?.message.orEmpty()`——data 层那些没带
            // resId 的异常（IO、JSON、共享库）会把 `java.io.FileNotFoundException: /data/user/0/...`
            // 原样糊到提示条上：英文类名 + 绝对私有路径，对用户零信息量，还像是应用坏了。
            // 现在主文案是一句人话，原始文本降级成第二行「供排查」（仍可截图反馈）。
            val raw = e?.message?.trim().orEmpty()
            if (raw.isEmpty()) return app.getString(R.string.vm_err_unknown)
            return app.getString(R.string.vm_err_unknown) + "\n" +
                app.getString(R.string.vm_err_unknown_detail, raw)
        }
        val args = when (e) {
            is VaultCipher.VaultException -> e.resArgs
            is BackupEngine.BackupException -> e.resArgs
            else -> emptyList()
        }
        return text(resId, localizedArgs(args))
    }

    /** 资源文案。resArgs 最多 3 个（见 `strings_backup.xml`）：按元数显式分派，避免 vararg 展开数组。 */
    private fun text(resId: Int, args: List<Any>): String = when (args.size) {
        0 -> app.getString(resId)
        1 -> app.getString(resId, args[0])
        2 -> app.getString(resId, args[0], args[1])
        else -> app.getString(resId, args[0], args[1], args[2])
    }

    /** 参数里的列表用资源分隔符拼接（data 层拿不到 Context，故推迟到这里）。 */
    private fun localizedArgs(args: List<Any>): List<Any> = args.map {
        if (it is List<*>) it.joinToString(app.getString(R.string.backup_list_separator_enum)) else it
    }

    /** 恢复/校验差异文案：优先资源化条目（rowIssues），无条目时回落中文原文 rowDetails。 */
    private fun verifyIssues(v: BackupEngine.VerifyResult): String {
        val sep = app.getString(R.string.backup_list_separator)
        val localized = v.rowIssues.take(5).map { it.text(app) }
        return localized.ifEmpty { v.rowDetails.take(5) }.joinToString(sep)
    }

    // ======================= 本机备份 =======================

    fun backupLocal(password: String, onShare: (Intent) -> Unit) {
        if (!BackupPasswordRules.newBackupOk(password)) { fail(app.getString(R.string.vm_backup_password_min_note)); return }
        viewModelScope.launch {
            _busy.value = true
            try {
                val out = repo.backupLocal(password.toCharArray())
                info(app.getString(
                    R.string.vm_backup_local_done,
                    out.file.name, out.tableCount, out.rowTotal, out.sha256.take(12),
                ))
                onShare(shareFile(out.file, "application/octet-stream"))
            } catch (e: Exception) {
                fail(app.getString(R.string.vm_backup_failed, errText(e)))
            } finally { _busy.value = false }
        }
    }

    // ======================= WebDAV =======================

    fun saveAndProbeWebdav(url: String, user: String, pass: String) {
        if (url.isBlank() || user.isBlank()) { fail(app.getString(R.string.vm_dav_need_url_user)); return }
        viewModelScope.launch {
            _busy.value = true
            try {
                val msg = repo.probeWebdav(url.trim(), user.trim(), pass)
                repo.saveWebdavConfig(url.trim(), user.trim(), pass)
                _davUrl.value = url.trim()
                _davUser.value = user.trim()
                info(msg)
            } catch (e: Exception) {
                fail(app.getString(R.string.vm_dav_connect_failed, errText(e)))
            } finally { _busy.value = false }
        }
    }

    fun backupWebdav(password: String) {
        if (!repo.webdavConfigured()) { fail(app.getString(R.string.vm_dav_not_configured)); return }
        if (!BackupPasswordRules.newBackupOk(password)) { fail(app.getString(R.string.vm_backup_password_min)); return }
        viewModelScope.launch {
            _busy.value = true
            try {
                info(repo.backupWebdav(password.toCharArray()) { stage -> _stage.value = stage })
            } catch (e: Exception) {
                fail(app.getString(R.string.vm_dav_backup_failed, errText(e)))
            } finally {
                _stage.value = ""
                _busy.value = false
            }
        }
    }

    /** W4：拉取远程备份列表（sheet 打开与手动刷新时调用）。 */
    fun loadDavBackups() {
        if (!repo.webdavConfigured()) { fail(app.getString(R.string.vm_dav_not_configured)); return }
        viewModelScope.launch {
            _busy.value = true
            try {
                _davBackups.value = repo.listWebdavBackups()
            } catch (e: WebDavClient.DavResponseTooLargeException) {
                // v1.0.86（批次 11 / D6）：目录列表超限与"拉取失败"分开报——正常目录列表
                // 只有约 200 KB，撞上 1 MiB 上限说明远端目录本身异常，笼统文案会让人以为是网络问题。
                fail(app.getString(R.string.vm_dav_list_too_large, WebDavClient.sizeMb(e.limitBytes)))
            } catch (e: Exception) {
                fail(app.getString(R.string.vm_dav_list_failed, errText(e)))
            } finally { _busy.value = false }
        }
    }

    /** W4：下载选中的远程备份，完成后经 davPicked 交给 Screen（等价本地选好文件）。 */
    fun downloadDavBackup(name: String) {
        viewModelScope.launch {
            _busy.value = true
            _stage.value = app.getString(R.string.vm_dav_downloading, name)
            try {
                val bytes = repo.downloadWebdavBackup(name)
                _davPicked.value = name to bytes
                info(app.getString(R.string.vm_dav_downloaded, name, bytes.size))
            } catch (e: WebDavClient.DavResponseTooLargeException) {
                // v1.0.86（批次 11 / D6）：超限单独报——文案走 strings.xml（含上限 MB 与
                // 服务器声明的大小），用户据此能判断是"远端那份被换了"还是"上限该调"。
                // 刻意**不**退回笼统的"下载失败"：那样用户只会反复重试同一个必然失败的下载。
                fail(app.getString(R.string.vm_dav_download_too_large, WebDavClient.sizeMb(e.limitBytes), name))
            } catch (e: Exception) {
                fail(app.getString(R.string.vm_dav_download_failed, errText(e)))
            } finally {
                _stage.value = ""
                _busy.value = false
            }
        }
    }

    fun consumeDavPicked() { _davPicked.value = null }

    // ======================= 备份恢复码（A2） =======================

    /** 生成（或重新生成）恢复码：落盘 Keystore 加密存储，并经 recoveryReveal 展示一次。 */
    fun generateRecoveryCode() {
        viewModelScope.launch {
            _busy.value = true
            try {
                val code = repo.generateRecoveryCode()
                _recoveryCodeSet.value = true
                _recoveryReveal.value = code
            } catch (e: Exception) {
                fail(app.getString(R.string.vm_recovery_generate_failed, errText(e)))
            } finally { _busy.value = false }
        }
    }

    /** 查看已存恢复码（自用 App 可再查看：Keystore 解密后经 recoveryReveal 展示）。 */
    fun revealRecoveryCode() {
        val code = repo.recoveryCode() ?: run {
            fail(app.getString(R.string.vm_recovery_not_set))
            return
        }
        _recoveryReveal.value = code
    }

    fun consumeRecoveryReveal() { _recoveryReveal.value = null }

    // ======================= 恢复（协议 §6 五步） =======================

    fun verifyBackup(fileName: String, bytes: ByteArray, password: String) {
        // v1.2.10（审查 CRITICAL-2）：**最后一道体积守卫**。文件选择器那条路径已经拿
        // `OpenableColumns.SIZE` 挡过一次，但 `verifyBackup` 是公开入口（WebDAV 下载、
        // 将来别的调用方都会走到这里），解密前先在这儿看字节数组本身多大，
        // 免得把"上游忘了检查"变成一次 OOM。
        if (bytes.size.toLong() > BackupEngine.PICK_LIMIT_BYTES) {
            fail(app.getString(
                R.string.backup_file_too_large,
                fileName,
                BackupEngine.sizeMb(bytes.size.toLong()),
                BackupEngine.sizeMb(BackupEngine.PICK_LIMIT_BYTES),
            ))
            return
        }
        viewModelScope.launch {
            _busy.value = true
            try {
                verifyBytes(fileName, bytes, password)
            } catch (e: Exception) {
                fail(errText(e).ifEmpty { app.getString(R.string.vm_restore_verify_failed) })
            } finally { _busy.value = false }
        }
    }

    /**
     * 解密 + 自校验 + 落到 `_pendingRestore`（恢复链路的公共前半段）。
     *
     * 抽出来是为了让**撤销快照**走完全同一条路径（工单第 2 条「复用 `verifyBackup` 的
     * 解密、自校验和 A05 密钥域检查」）：撤销与普通恢复的差别只在「字节从哪来」，
     * 后面的解密、自校验、口令暂存、待确认块都必须逐字一致，否则就出现了第二条恢复链。
     */
    private suspend fun verifyBytes(fileName: String, bytes: ByteArray, password: String) {
        val d = repo.decryptAndSelfCheck(bytes, password.toCharArray())
        _pendingRestore.value = d
        _pendingFileName.value = fileName
        _restoreResult.value = null
        wipeRestorePassword()
        restorePassword = password.toCharArray()
        val payload = org.json.JSONObject(d.payload)
        val rows = payload.getJSONObject("manifest").keys().asSequence().map { k ->
            payload.getJSONObject("tables").getJSONArray(k).length() }.sum()
        info(app.getString(
            R.string.vm_restore_verify_ok,
            d.schemaVersion, d.createdAt.take(19), rows,
        ))
    }

    /** 重新读一次「最近一次恢复前快照」（进页面、以及每次恢复结束后都要刷新）。 */
    fun refreshPreRestoreSnapshot() {
        viewModelScope.launch {
            _preRestoreSnapshot.value = withContext(Dispatchers.IO) { preRestoreStore.latest() }
        }
    }

    /**
     * v1.2.11（缺陷工单 B07）：用最近一次 pre-restore 快照撤销。
     *
     * 三件事按顺序做，缺一不可：
     *  1. **读**快照字节（有界读；文件缺失/损坏/超限都在这里抛，走 `errText` 报给用户）。
     *  2. 交给 [verifyBytes] —— 与普通恢复同一条解密/自校验/密钥域检查路径。
     *  3. 之后的确认与执行完全复用 [doRestore]；`repo.restore()` 本身会先为**当前**状态
     *     再留一份快照，于是工单第 3 条「撤销本身也是一次恢复」天然成立，
     *     而且「撤销的撤销」也有退路。
     *
     * 口令：**不保存**。快照是用当时那次恢复的口令加密的，用户必须重新输入；
     * 这里只把口令传进内存态 `restorePassword`（`doRestore` 用毕清零）。
     */
    fun undoFromPreRestoreSnapshot(password: String) {
        if (_busy.value) return
        val snapshot = _preRestoreSnapshot.value ?: run {
            fail(app.getString(R.string.backup_snapshot_missing))
            return
        }
        if (!snapshot.available) {
            fail(app.getString(R.string.backup_snapshot_missing))
            return
        }
        viewModelScope.launch {
            _busy.value = true
            try {
                val bytes = withContext(Dispatchers.IO) { preRestoreStore.read(snapshot) }
                verifyBytes(snapshot.name, bytes, password)
            } catch (e: Exception) {
                fail(errText(e).ifEmpty { app.getString(R.string.vm_restore_verify_failed) })
            } finally { _busy.value = false }
        }
    }

    fun doRestore() {
        // v1.1.1（HIGH-4）：**重入守卫**。UI 的门是 `Button(enabled = !busy)`，而 `enabled` 在组合期
        // 读取、重组异步——大文件恢复时双击「执行恢复」会进两次。此前这里没有任何守卫，
        // 两次 `repo.restore()` 会并发对同一个 `ashkb.db` 做「清表 + 重写」。
        // 仓库层本批次也加了 Mutex（真正的保证在那里），这一行只是让第二次点击干脆不排队、
        // 也不重复设置 busy/结果状态。
        if (_busy.value) return
        val d = pendingRestore.value ?: return
        val pass = restorePassword ?: run {
            fail(app.getString(R.string.vm_restore_password_expired))
            return
        }
        viewModelScope.launch {
            _busy.value = true
            try {
                val mode = _restoreMode.value
                val modeNote = if (mode == BackupEngine.RestoreMode.FULL_ROLLBACK)
                    app.getString(R.string.vm_restore_mode_full)
                else app.getString(R.string.vm_restore_mode_merge)
                val v = repo.restore(d, pass, mode)
                wipeRestorePassword()
                _restoreResult.value = v
                // v1.2.11（缺陷工单 B05）：**报成功之前**把运行态刷成恢复后的库。
                //
                // 缺陷原状：恢复把整库换成了另一份数据，但闹钟、`planned_slots`、锁屏急救卡
                // 全是恢复前按旧库排的，而刷新只在冷启动跑。用户看到「恢复成功」回到首页，
                // 药单与提醒仍是旧的，直到下次冷启动才对齐（锁屏急救卡同理——那是急救场景，
                // 显示恢复前的诊断/联系人后果最重）。
                //
                // 顺序与理由见 `AshkbApplication.rescheduleAllChains`：与启动期同一份实现，
                // 不复制第二份调度。只在 `rowsOk` 时刷新——校验没过的事务已整体回滚，
                // 库还是恢复前的样子，重排一遍纯属多余（工单：「恢复失败/回滚路径不得触发
                // 不必要的重排」）。
                if (v.rowsOk) refreshRuntimeAfterRestore()
                if (v.rowsOk) info(app.getString(R.string.vm_restore_done, modeNote, v.totalRows))
                else info(app.getString(R.string.vm_restore_incomplete, verifyIssues(v)))
            } catch (e: Exception) {
                fail(app.getString(R.string.vm_restore_failed, errText(e)))
            } finally {
                // v1.2.10（审查 HIGH-3）：**两条路径都要丢下明文**。此前只有 `cancelRestore()`
                // 会清 `_pendingRestore`，于是「解密后的整库明文 String（DecryptedFile.payload）
                // + 恢复口令 CharArray」在恢复成功之后仍然常驻到 ViewModel 销毁为止——
                // 恢复完已经是"上一份数据"了，留着它没有任何用途，只有风险。
                // 失败路径同样清：失败时库已由 `repo.restore()` 的 pre-restore 快照复位，
                // 重试要重新选文件、重新输口令，这个代价值得。
                _pendingRestore.value = null
                _pendingFileName.value = null
                wipeRestorePassword()
                // v1.2.11（缺陷工单 B07）：这一次恢复刚刚写出了**新的** pre-restore 快照，
                // 撤销入口要指向它（而不是上一次的那份），否则用户想撤的其实是更早的历史。
                refreshPreRestoreSnapshot()
                _busy.value = false
            }
        }
    }

    /**
     * v1.2.10（审查 HIGH-3）：**兜底**清内存里的敏感载体。
     *
     * 正常路径上 `cancelRestore()` 与 `doRestore()` 都已各清一次，这里防的是"用户直接
     * 返回/退出，一次都没走那两条路径"——比如选了文件、解密成功、看着待恢复块，然后按返回键
     * 离开。没有这个兜底，明文 payload 与恢复码会一直活到这个 VM 被回收。
     */
    override fun onCleared() {
        _pendingRestore.value = null
        _pendingFileName.value = null
        _restoreResult.value = null
        _davPicked.value = null
        _recoveryReveal.value = null
        wipeRestorePassword()
        super.onCleared()
    }

    fun cancelRestore() {
        wipeRestorePassword()
        _pendingRestore.value = null
        _pendingFileName.value = null
        _restoreResult.value = null
        _restoreMode.value = BackupEngine.RestoreMode.FULL_ROLLBACK
    }

    /** 一键恢复演练（协议 §7 首次恢复演练）。 */
    fun drill() {
        viewModelScope.launch {
            _busy.value = true
            try {
                val r = repo.drill()
                info(if (r.roundtripOk)
                    app.getString(R.string.vm_drill_pass, r.detail)
                else app.getString(R.string.vm_drill_incomplete, r.detail))
            } catch (e: Exception) {
                fail(app.getString(R.string.vm_drill_failed, errText(e)))
            } finally { _busy.value = false }
        }
    }

    // ======================= 档案 JSON =======================

    fun exportProfileJson(onShare: (Intent) -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            try {
                val json = repo.exportProfileJson()
                val f = File(File(app.filesDir, "exports"),
                    "ashkb-profile-${java.time.LocalDate.now()}.json")
                f.parentFile?.mkdirs()
                f.writeText(json)
                repo.logExportJson(f.name, 0)
                info(app.getString(R.string.vm_profile_export_done, f.length()))
                onShare(shareFile(f, "application/json"))
            } catch (e: Exception) {
                fail(app.getString(R.string.vm_profile_export_failed, errText(e)))
            } finally { _busy.value = false }
        }
    }

    fun importProfileJson(bytes: ByteArray) {
        viewModelScope.launch {
            _busy.value = true
            try {
                val (name, meds) = repo.importProfileJson(String(bytes, Charsets.UTF_8))
                // v1.2.11（缺陷工单 A04）：导入成功后**按新药单重建计划槽位并重排提醒**。
                //
                // 缺陷原状：导入只写 profile 与 medications 两张表就报「导入完成」。用户回到
                // 首页看到的仍是导入前的今日用药与提醒——因为 `planned_slots` 是另一张表，
                // 而 `ReminderScheduler` 的闹钟也是导入前按旧药单排的。用户以为导入失败了。
                // 工单要求「提示成功前按新药单重建计划槽位并取消/重排提醒」，故放在 info() 之前。
                rematerializeAfterImport()
                info(app.getString(R.string.vm_profile_import_done, name, meds))
            } catch (e: Exception) {
                fail(app.getString(R.string.vm_profile_import_failed, errText(e)))
            } finally { _busy.value = false }
        }
    }

    /**
     * v1.2.11（缺陷工单 A04）：导入后重建计划槽位 + 重排提醒。
     *
     * 两件事必须一起做，顺序也不能换：
     * 1. **先物化**——`materializePlannedSlots` 按新药单把「今天-1 .. 今天+7」的槽位写进
     *    `planned_slots`。今日卡与完成度统计都读这张表，不物化就等于导入没生效。
     * 2. **再重排**——`ReminderScheduler.rescheduleAll` 内部先 `cancelAllFuture` 再按
     *    `settledSlotRefs` 排；它读的正是上一步刚写好的槽位，所以顺序不能反。
     *
     * 整段在 IO 线程：物化是 N 支药 × 9 天的插入，重排是「药 × 天 × 槽位」量级的
     * AlarmManager binder 调用，都远超声明的 16ms。
     *
     * v1.2.11（缺陷工单 B09）：**这里改口径了**。原先「失败只记日志、不阻断导入成功提示」，
     * 理由是「用户重进应用时启动期重排会补上」——但启动期重排同样可能失败，且恢复档案
     * 恰恰是提醒最容易全丢的时点（旧行被清、新行刚物化）。现在：物化失败仍只记日志
     * （数据问题另行处理），**重排失败则明确告诉用户**，并说清可以重进应用补排。
     */
    private suspend fun rematerializeAfterImport() = withContext(Dispatchers.IO) {
        val db = AppDatabase.get(app)
        val meds = db.medicationDao().listActive()
        val today = LocalDate.now()
        // `app` 声明的类型是 Context（构造时传的是 applicationContext），仓库挂在
        // AshkbApplication 上，故此处要转型——applicationContext 的实例本身就是它。
        val medRepo = (app as AshkbApplication).medicationRepository
        runCatching {
            medRepo.materializePlannedSlots(
                meds,
                today.minusDays(MATERIALIZE_LOOKBACK_DAYS),
                today.plusDays(MATERIALIZE_LOOKAHEAD_DAYS),
            )
        }
        val report = ReminderScheduler.rescheduleAll(app, meds, medRepo.settledSlotRefs(today))
        ReminderNotice.postIfFailed(app, report)
    }

    /**
     * v1.2.11（缺陷工单 B05）：恢复成功后把**全部运行态**刷成恢复后的库。
     *
     * 与 A04 的 [rematerializeAfterImport] 的区别：那条只管「用药提醒链 + 计划槽位」，
     * 因为档案 JSON 导入只改 profile / medications 两张表。恢复换的是整库——复诊、BASDAI、
     * 运动、补剂四条提醒链的输入（各自表）、锁屏急救卡（诊断 / 联系人 / 用药）、久坐链
     * 全部可能变了，故必须走 [AshkbApplication.rescheduleAllChains] 这份完整实现。
     *
     * 工单的三条硬要求对应的实现选择：
     *  1. 「优先抽取并复用现有启动刷新逻辑，不要复制第二份调度实现」⇒ 调启动期那个函数本身。
     *  2. 「恢复失败/回滚路径不得触发不必要的重排」⇒ 调用点已用 `v.rowsOk` 把关。
     *  3. 「使提醒、计划槽位、锁屏急救卡和用户可见数据与恢复后的 DB 一致」⇒
     *     前三个由 rescheduleAllChains 覆盖；「用户可见数据」靠 Room 的失效通知：
     *     恢复走 `supportDb()` 裸 SQL（不经过 Room 的写入包装），Room 的 InvalidationTracker
     *     未必感知，故这里**主动** `invalidate()` 一次，让所有 `observeXxx` Flow 立刻重发。
     *     少了这一步，页面会一直停在恢复前的旧列表上——这正是缺陷的可见症状。
     */
    private suspend fun refreshRuntimeAfterRestore() = withContext(Dispatchers.IO) {
        val db = AppDatabase.get(app)
        // 恢复走 `supportDb()` 裸 SQL（不经过 Room 的写入包装）。Room 在每张表上装的触发器
        // 仍会记下「该表已变」，但 `InvalidationTracker` 是**惰性**消费的——要等下一次查询
        // 或轮询才刷新版本号。这里主动推一次，让所有 `observeXxx` Flow 立刻重发。
        // 少了这一步，页面会停在恢复前的旧列表上，而这正是缺陷的可见症状。
        runCatching { db.invalidationTracker.refreshVersionsAsync() }
        val report = (app as AshkbApplication).rescheduleAllChains(
            db = db,
            meds = db.medicationDao().listActive(),
            today = LocalDate.now(),
            now = LocalDateTime.now(),
            purgeGhostSlots = true,
        )
        ReminderNotice.postIfFailed(app, report)
    }

    private fun shareFile(file: File, mime: String): Intent {
        val uri = FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    // ======================= 附件同步（v1.0.35） =======================

    private val _attachSyncEnabled = MutableStateFlow(repo.attachmentSyncEnabled())
    val attachSyncEnabled: StateFlow<Boolean> = _attachSyncEnabled.asStateFlow()

    private val _attachCounts = MutableStateFlow(AttachmentSyncCounts())
    val attachCounts: StateFlow<AttachmentSyncCounts> = _attachCounts.asStateFlow()

    /** 进度文案；null = 未在跑（按钮据此显示"正在同步"并禁用）。 */
    private val _attachSyncStage = MutableStateFlow<String?>(null)
    val attachSyncStage: StateFlow<String?> = _attachSyncStage.asStateFlow()

    /**
     * 结果文案：(是否成功, 明细)；null = 尚无结果。
     *
     * 与 message 不同，这里刻意不 getString 成型：成功/失败是两条不同模板，
     * 把成败位和原始 detail 一起传出，模板与配色都由 Screen 一次决定，避免 VM 里塞 UI 语义。
     */
    private val _attachSyncMsg = MutableStateFlow<Pair<Boolean, String>?>(null)
    val attachSyncMsg: StateFlow<Pair<Boolean, String>?> = _attachSyncMsg.asStateFlow()

    private val _attachBytes = MutableStateFlow(0L)
    val attachBytes: StateFlow<Long> = _attachBytes.asStateFlow()

    /** 远端校验结果：(是否成功, 明细)；null = 尚无结果（v1.0.36）。 */
    private val _attachVerifyMsg = MutableStateFlow<Pair<Boolean, String>?>(null)
    val attachVerifyMsg: StateFlow<Pair<Boolean, String>?> = _attachVerifyMsg.asStateFlow()

    fun setAttachSyncEnabled(on: Boolean) {
        repo.setAttachmentSyncEnabled(on)
        _attachSyncEnabled.value = on
    }

    /** 批量补传 + 清理待删远端。UI 侧应在开启开关且已配置 WebDAV 时才可点。 */
    fun syncAttachments() {
        viewModelScope.launch {
            _attachSyncMsg.value = null
            _attachSyncStage.value = null
            try {
                val r = repo.syncAttachments { p ->
                    // onProgress 在 IO 线程回调：只写 MutableStateFlow（线程安全），不要碰 UI
                    _attachSyncStage.value = if (p.total > 0) "${p.phase} ${p.done}/${p.total}" else p.phase
                }
                _attachSyncMsg.value = true to r.detail
            } catch (e: Exception) {
                _attachSyncMsg.value = false to errText(e)
            } finally {
                _attachSyncStage.value = null
                refreshAttachStats()
            }
        }
    }

    /**
     * v1.0.36：远端校验与补传。比对服务器文件集与本地记录：
     * 远端缺失的补传、远端多余的清理（本地已无记录）。与同步共用进度流，两者互斥。
     */
    fun verifyAttachments() {
        viewModelScope.launch {
            _attachVerifyMsg.value = null
            _attachSyncStage.value = null
            try {
                val r = repo.verifyAttachments { p ->
                    _attachSyncStage.value = if (p.total > 0) "${p.phase} ${p.done}/${p.total}" else p.phase
                }
                _attachVerifyMsg.value = true to r.detail
            } catch (e: Exception) {
                _attachVerifyMsg.value = false to errText(e)
            } finally {
                _attachSyncStage.value = null
                refreshAttachStats()
            }
        }
    }

    fun refreshAttachStats() {
        viewModelScope.launch {
            _attachCounts.value = repo.observeAttachmentSyncCounts().first()
            _attachBytes.value = repo.attachmentLocalBytes()
        }
    }

    companion object {
        /**
         * v1.2.11（缺陷工单 A04）：导入后重建槽位的窗口。
         *
         * 与 `AshkbApplication` 启动期物化同一口径：回看 1 天（今天的槽位可能因为导入发生在
         * 午夜后而缺失）、前瞻 7 天（提醒最长只排 7 天，更远的槽位排了也没人看）。
         * 提成常量是因为 detekt `MagicNumber` 会把 `minusDays(1)` / `plusDays(7)` 里的字面量
         * 当魔法数字——而这两个数的含义恰恰值得命名。
         */
        const val MATERIALIZE_LOOKBACK_DAYS = 1L
        const val MATERIALIZE_LOOKAHEAD_DAYS = 7L

        val Factory: ViewModelProvider.Factory = androidx.lifecycle.viewmodel.viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as AshkbApplication
                BackupViewModel(app.applicationContext, app.backupRepository)
            }
        }
    }
}
