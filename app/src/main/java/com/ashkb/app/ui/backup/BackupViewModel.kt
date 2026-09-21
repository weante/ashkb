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
import com.ashkb.app.data.backup.WebDavClient
import com.ashkb.app.data.db.AttachmentSyncCounts
import com.ashkb.app.data.entity.BackupLedger
import com.ashkb.app.data.repo.BackupRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

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

    /** 恢复口令（内存暂存，用毕清零；pre-restore 快照复用同一口令）。 */
    private var restorePassword: CharArray? = null

    private fun wipeRestorePassword() {
        restorePassword?.fill('0')
        restorePassword = null
    }

    fun clearMessage() { _message.value = null }
    private fun info(msg: String) { _message.value = msg }
    private fun fail(msg: String) { _message.value = msg }

    // ======================= 本机备份 =======================

    fun backupLocal(password: String, onShare: (Intent) -> Unit) {
        if (password.length < 6) { fail(app.getString(R.string.vm_backup_password_min_note)); return }
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
                fail(app.getString(R.string.vm_backup_failed, e.message))
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
                fail(app.getString(R.string.vm_dav_connect_failed, e.message))
            } finally { _busy.value = false }
        }
    }

    fun backupWebdav(password: String) {
        if (!repo.webdavConfigured()) { fail(app.getString(R.string.vm_dav_not_configured)); return }
        if (password.length < 6) { fail(app.getString(R.string.vm_backup_password_min)); return }
        viewModelScope.launch {
            _busy.value = true
            try {
                info(repo.backupWebdav(password.toCharArray()) { stage -> _stage.value = stage })
            } catch (e: Exception) {
                fail(app.getString(R.string.vm_dav_backup_failed, e.message))
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
            } catch (e: Exception) {
                fail(app.getString(R.string.vm_dav_list_failed, e.message))
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
            } catch (e: Exception) {
                fail(app.getString(R.string.vm_dav_download_failed, e.message))
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
                fail(app.getString(R.string.vm_recovery_generate_failed, e.message))
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
        viewModelScope.launch {
            _busy.value = true
            try {
                val d = repo.decryptAndSelfCheck(bytes, password.toCharArray())
                _pendingRestore.value = d
                _pendingFileName.value = fileName
                _restoreResult.value = null
                wipeRestorePassword()
                restorePassword = password.toCharArray()
                val rows = org.json.JSONObject(d.payload).getJSONObject("manifest")
                    .keys().asSequence().map { k ->
                    org.json.JSONObject(d.payload).getJSONObject("tables")
                        .getJSONArray(k).length() }.sum()
                info(app.getString(
                    R.string.vm_restore_verify_ok,
                    d.schemaVersion, d.createdAt.take(19), rows,
                ))
            } catch (e: Exception) {
                fail(e.message ?: app.getString(R.string.vm_restore_verify_failed))
            } finally { _busy.value = false }
        }
    }

    fun doRestore() {
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
                if (v.rowsOk) info(app.getString(R.string.vm_restore_done, modeNote, v.totalRows))
                else info(app.getString(R.string.vm_restore_incomplete, v.rowDetails.take(5).joinToString("；")))
            } catch (e: Exception) {
                fail(app.getString(R.string.vm_restore_failed, e.message))
            } finally { _busy.value = false }
        }
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
                fail(app.getString(R.string.vm_drill_failed, e.message))
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
                fail(app.getString(R.string.vm_profile_export_failed, e.message))
            } finally { _busy.value = false }
        }
    }

    fun importProfileJson(bytes: ByteArray) {
        viewModelScope.launch {
            _busy.value = true
            try {
                val (name, meds) = repo.importProfileJson(String(bytes, Charsets.UTF_8))
                info(app.getString(R.string.vm_profile_import_done, name, meds))
            } catch (e: Exception) {
                fail(app.getString(R.string.vm_profile_import_failed, e.message))
            } finally { _busy.value = false }
        }
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
                _attachSyncMsg.value = false to (e.message ?: "")
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
                _attachVerifyMsg.value = false to (e.message ?: "")
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
        val Factory: ViewModelProvider.Factory = androidx.lifecycle.viewmodel.viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as AshkbApplication
                BackupViewModel(app.applicationContext, app.backupRepository)
            }
        }
    }
}
