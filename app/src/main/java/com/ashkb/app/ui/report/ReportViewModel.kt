package com.ashkb.app.ui.report

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import com.ashkb.app.AshkbApplication
import com.ashkb.app.data.repo.ReportRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * P4 M9 报表 ViewModel：统计概览 + 趋势序列 + 复诊报告 / 紧急卡 PDF 生成与分享。
 */
class ReportViewModel(
    private val app: Context,
    private val repo: ReportRepository,
) : ViewModel() {

    val overview: StateFlow<ReportRepository.Overview?> = MutableStateFlow(null)
    val trends: StateFlow<ReportRepository.Trends?> = MutableStateFlow(null)
    val busy: StateFlow<Boolean> = MutableStateFlow(false)
    val message: StateFlow<String?> = MutableStateFlow(null)

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            (busy as MutableStateFlow).value = true
            try {
                (overview as MutableStateFlow).value = repo.overview()
                (trends as MutableStateFlow).value = repo.trends()
            } catch (e: Exception) {
                (message as MutableStateFlow).value = "统计加载失败：${e.message}"
            } finally {
                (busy as MutableStateFlow).value = false
            }
        }
    }

    fun clearMessage() { (message as MutableStateFlow).value = null }

    fun reportError(msg: String) { (message as MutableStateFlow).value = msg }

    /** 生成复诊报告 PDF 并返回分享 Intent；调用方负责 startActivity。 */
    fun generateReportPdf(onReady: (Intent) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            (busy as MutableStateFlow).value = true
            try {
                val snapshot = repo.checkupReport()
                val pdf = ReportPdfWriter.writeCheckupReport(app, snapshot)
                onReady(shareIntent(pdf, "application/pdf"))
            } catch (e: Exception) {
                onError("报告生成失败：${e.message}")
            } finally {
                (busy as MutableStateFlow).value = false
            }
        }
    }

    /** M7 紧急卡打印版 PDF。 */
    fun generateEmergencyCardPdf(onReady: (Intent) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            (busy as MutableStateFlow).value = true
            try {
                val card = repo.emergencyCard()
                val pdf = ReportPdfWriter.writeEmergencyCard(app, card)
                onReady(shareIntent(pdf, "application/pdf"))
            } catch (e: Exception) {
                onError("紧急卡 PDF 生成失败：${e.message}")
            } finally {
                (busy as MutableStateFlow).value = false
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
