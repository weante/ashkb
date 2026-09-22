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
 * P4 M9 报表 ViewModel：统计概览 + 趋势序列 + 周报 / 月报小结 + 复诊报告 / 紧急卡 PDF 生成与分享。
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
    // v1.0.45：趋势页的时间范围（7 / 30 / 90 天），默认 30 天
    private val _trendDays = MutableStateFlow(30)
    val trendDays: StateFlow<Int> = _trendDays
    // B4：周报 / 月报的统计窗口（默认 7 天）与小结数据
    private val _periodDays = MutableStateFlow(7)
    val periodDays: StateFlow<Int> = _periodDays
    private val _periodic = MutableStateFlow<ReportRepository.PeriodicReport?>(null)
    val periodic: StateFlow<ReportRepository.PeriodicReport?> = _periodic
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
                _trends.value = repo.trends(_trendDays.value)
            } catch (e: Exception) {
                _message.value = app.getString(R.string.vm_report_stats_failed, e.message)
            } finally {
                _busy.value = false
            }
        }
    }

    /**
     * B4：切换周报 / 月报的统计窗口并重新加载。
     * 先点同一个窗口且已有数据时不重复查询（避免列表页反复横跳导致的无效 IO）。
     */
    fun setPeriodDays(days: Int) {
        if (days == _periodDays.value && _periodic.value != null) return
        loadPeriodic(days)
    }

    /**
     * B4：加载指定窗口（7 / 30 天）的周报 / 月报小结。
     * 口径与 overview() 完全一致（部分完成计 0.5、症状空值不计入均值），文案由 UI 侧组装。
     * 失败时保留上一次结果而不是清空成空态，只走全局 Snackbar 提示——与 refresh() 同口径。
     */
    fun loadPeriodic(days: Int) {
        _periodDays.value = days
        viewModelScope.launch {
            _busy.value = true
            try {
                _periodic.value = repo.periodicReport(days)
            } catch (e: Exception) {
                _message.value = app.getString(R.string.vm_report_stats_failed, e.message)
            } finally {
                _busy.value = false
            }
        }
    }

    /**
     * v1.0.45：切换趋势页的时间范围（7 / 30 / 90 天）并重载。
     * 与 [setPeriodDays] 同口径：点同一窗口且已有数据时不重复查询；失败保留上次结果、只走全局提示。
     */
    fun setTrendDays(days: Int) {
        if (days == _trendDays.value && _trends.value != null) return
        _trendDays.value = days
        viewModelScope.launch {
            _busy.value = true
            try {
                val fresh = repo.trends(days)
                // v1.0.46：慢查询结果后到时不回写——快速连点 7→30 时，若 7 天的响应
                // 比 30 天的更晚返回，会把图换成旧窗口的数据而 chip 仍显示 30 天。
                if (_trendDays.value == days) _trends.value = fresh
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
