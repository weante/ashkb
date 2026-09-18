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
import com.ashkb.app.data.entity.BackupLedger
import com.ashkb.app.data.repo.BackupRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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

    companion object {
        val Factory: ViewModelProvider.Factory = androidx.lifecycle.viewmodel.viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as AshkbApplication
                BackupViewModel(app.applicationContext, app.backupRepository)
            }
        }
    }
}
