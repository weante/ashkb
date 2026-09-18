package com.ashkb.app.ui.report

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import com.ashkb.app.AshkbApplication
import com.ashkb.app.R
import com.ashkb.app.data.repo.ReportRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * P4 M9 报表 ViewModel：统计概览 + 趋势序列 + 复诊报告 / 紧急卡 PDF 生成与分享。
 * v1.0.22：状态流统一为私有 MutableStateFlow + 只读 StateFlow 暴露，
 * 清除 `as MutableStateFlow` 强转（审查报告 P2，运行时非受检向下转型）。
 */
class ReportViewModel(
    private val app: Context,
    private val repo: ReportRepository,
) : ViewModel() {

    private val _overview = MutableStateFlow<ReportRepository.Overview?>(null)
    val overview: StateFlow<ReportRepository.Overview?> = _overview
    private val _trends = MutableStateFlow<ReportRepository.Trends?>(null)
    val trends: StateFlow<ReportRepository.Trends?> = _trends
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _busy.value = true
            try {
                _overview.value = repo.overview()
                _trends.value = repo.trends()
            } catch (e: Exception) {
                _message.value = app.getString(R.string.vm_report_stats_failed, e.message)
            } finally {
                _busy.value = false
            }
        }
    }

    fun clearMessage() { _message.value = null }

    fun reportError(msg: String) { _message.value = msg }

    /** 生成复诊报告 PDF 并返回分享 Intent；调用方负责 startActivity。 */
    fun generateReportPdf(onReady: (Intent) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            try {
                val snapshot = repo.checkupReport()
                val pdf = ReportPdfWriter.writeCheckupReport(app, snapshot)
                onReady(shareIntent(pdf, "application/pdf"))
            } catch (e: Exception) {
                onError(app.getString(R.string.vm_report_pdf_failed, e.message))
            } finally {
                _busy.value = false
            }
        }
    }

    /** M7 紧急卡打印版 PDF。 */
    fun generateEmergencyCardPdf(onReady: (Intent) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            try {
                val card = repo.emergencyCard()
                val pdf = ReportPdfWriter.writeEmergencyCard(app, card)
                onReady(shareIntent(pdf, "application/pdf"))
            } catch (e: Exception) {
                onError(app.getString(R.string.vm_emergency_pdf_failed, e.message))
            } finally {
                _busy.value = false
            }
        }
    }

    private fun shareIntent(file: File, mime: String): Intent {
        val uri = FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.nameWithoutExtension)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = androidx.lifecycle.viewmodel.viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as AshkbApplication
                ReportViewModel(app.applicationContext, app.reportRepository)
            }
        }
    }
}
