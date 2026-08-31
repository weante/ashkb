package com.ashkb.app.ui.backup

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import com.ashkb.app.AshkbApplication
import com.ashkb.app.data.backup.BackupEngine
import com.ashkb.app.data.entity.BackupLedger
import com.ashkb.app.data.repo.BackupRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

/** P4 R20 备份与数据自主 ViewModel。 */
class BackupViewModel(
    private val app: Context,
    private val repo: BackupRepository,
) : ViewModel() {

    val busy: StateFlow<Boolean> = MutableStateFlow(false)
    val message: StateFlow<String?> = MutableStateFlow(null)

    val ledger: StateFlow<List<BackupLedger>> = repo.observeLedger()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ---- 恢复流程状态（协议 §6 五步） ----
    /** 已旁路解密 + 自校验通过的备份（尚未写入主库）。 */
    val pendingRestore: StateFlow<BackupRepository.DecryptedFile?> = MutableStateFlow(null)
    val pendingFileName: StateFlow<String?> = MutableStateFlow(null)
    val restoreResult: StateFlow<BackupEngine.VerifyResult?> = MutableStateFlow(null)

    fun clearMessage() { (message as MutableStateFlow).value = null }
    private fun info(msg: String) { (message as MutableStateFlow).value = msg }
    private fun fail(msg: String) { (message as MutableStateFlow).value = msg }

    // ======================= 本机备份 =======================

    fun backupLocal(password: String, onShare: (Intent) -> Unit) {
        if (password.length < 6) { fail("备份口令至少 6 位——恢复时唯一解密依据，请牢记"); return }
        viewModelScope.launch {
            (busy as MutableStateFlow).value = true
            try {
                val out = repo.backupLocal(password.toCharArray())
                info("本机备份完成：${out.file.name}（${out.tableCount} 表 ${out.rowTotal} 行，" +
                    "SHA-256 ${out.sha256.take(12)}…）。文件已存 app 私有目录，可通过分享另存。")
                onShare(shareFile(out.file, "application/octet-stream"))
            } catch (e: Exception) {
                fail("备份失败：${e.message}")
            } finally { (busy as MutableStateFlow).value = false }
        }
    }

    // ======================= WebDAV =======================

    fun saveAndProbeWebdav(url: String, user: String, pass: String) {
        if (url.isBlank() || user.isBlank()) { fail("请填写 WebDAV 服务器地址与用户名"); return }
        viewModelScope.launch {
            (busy as MutableStateFlow).value = true
            try {
                val msg = repo.probeWebdav(url.trim(), user.trim(), pass)
                repo.saveWebdavConfig(url.trim(), user.trim(), pass)
                info(msg)
            } catch (e: Exception) {
                fail("连接失败：${e.message}")
            } finally { (busy as MutableStateFlow).value = false }
        }
    }

    fun backupWebdav(password: String) {
        if (!repo.webdavConfigured()) { fail("请先配置并测试 WebDAV 连接"); return }
        if (password.length < 6) { fail("备份口令至少 6 位"); return }
        viewModelScope.launch {
            (busy as MutableStateFlow).value = true
            try {
                info(repo.backupWebdav(password.toCharArray()))
            } catch (e: Exception) {
                fail("WebDAV 备份失败：${e.message}")
            } finally { (busy as MutableStateFlow).value = false }
        }
    }

    // ======================= 恢复（协议 §6 五步） =======================

    fun verifyBackup(fileName: String, bytes: ByteArray, password: String) {
        viewModelScope.launch {
            (busy as MutableStateFlow).value = true
            try {
                val d = repo.decryptAndSelfCheck(bytes, password.toCharArray())
                (pendingRestore as MutableStateFlow).value = d
                (pendingFileName as MutableStateFlow).value = fileName
                (restoreResult as MutableStateFlow).value = null
                val rows = org.json.JSONObject(d.payload).getJSONObject("manifest")
                    .keys().asSequence().map { k ->
                    org.json.JSONObject(d.payload).getJSONObject("tables")
                        .getJSONArray(k).length() }.sum()
                info("解密与文件自校验通过（schema v$d.schemaVersion，备份于 ${d.createdAt.take(19)}，" +
                    "约 $rows 行）。下一步将先做 pre-restore 快照再覆盖写入。")
            } catch (e: Exception) {
                fail(e.message ?: "备份文件校验失败")
            } finally { (busy as MutableStateFlow).value = false }
        }
    }

    fun doRestore() {
        val d = pendingRestore.value ?: return
        viewModelScope.launch {
            (busy as MutableStateFlow).value = true
            try {
                val v = repo.restore(d)
                (restoreResult as MutableStateFlow).value = v
                if (v.rowsOk) info("恢复完成：双校验（行数 + SHA-256）全部通过，共 ${v.totalRows} 行。" +
                    "误恢复退路快照已留存。请重启应用以刷新界面数据。")
                else info("恢复已写入，但双校验未全部通过：${v.rowDetails.take(5).joinToString("；")}")
            } catch (e: Exception) {
                fail("恢复失败：${e.message}")
            } finally { (busy as MutableStateFlow).value = false }
        }
    }

    fun cancelRestore() {
        (pendingRestore as MutableStateFlow).value = null
        (pendingFileName as MutableStateFlow).value = null
        (restoreResult as MutableStateFlow).value = null
    }

    /** 一键恢复演练（协议 §7 首次恢复演练）。 */
    fun drill() {
        viewModelScope.launch {
            (busy as MutableStateFlow).value = true
            try {
                val r = repo.drill()
                info(if (r.roundtripOk)
                    "恢复演练通过：$r.detail。备份→加密→解密→恢复→双校验全链路可用。"
                else "演练未完全通过：$r.detail")
            } catch (e: Exception) {
                fail("恢复演练失败：${e.message}")
            } finally { (busy as MutableStateFlow).value = false }
        }
    }

    // ======================= 档案 JSON =======================

    fun exportProfileJson(onShare: (Intent) -> Unit) {
        viewModelScope.launch {
            (busy as MutableStateFlow).value = true
            try {
                val json = repo.exportProfileJson()
                val f = File(File(app.filesDir, "exports"),
                    "ashkb-profile-${java.time.LocalDate.now()}.json")
                f.parentFile?.mkdirs()
                f.writeText(json)
                repo.logExportJson(f.name, 0)
                info("档案 JSON 已导出（${f.length()} 字节，明文）——用于换机建档，不含打卡日志。")
                onShare(shareFile(f, "application/json"))
            } catch (e: Exception) {
                fail("导出失败：${e.message}")
            } finally { (busy as MutableStateFlow).value = false }
        }
    }

    fun importProfileJson(bytes: ByteArray) {
        viewModelScope.launch {
            (busy as MutableStateFlow).value = true
            try {
                val (name, meds) = repo.importProfileJson(String(bytes, Charsets.UTF_8))
                info("档案导入完成：$name（${meds} 种在用药）。已导入项与现有数据并存，请检查重复。")
            } catch (e: Exception) {
                fail("导入失败：${e.message}")
            } finally { (busy as MutableStateFlow).value = false }
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
