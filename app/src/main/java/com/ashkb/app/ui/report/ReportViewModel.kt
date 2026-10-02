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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    /**
     * v1.0.86（批次 11）：**最近一次成功加载**的周月报窗口（`Int.MIN_VALUE` = 还没加载过）。
     * 与 [_periodDays] 分开是有意的：前者是"用户现在选的窗口"，后者是"手上这份数据是哪段"——
     * 二者在加载进行中/失败时会不一致，去重必须按后者判定。
     */
    private var loadedPeriodDays: Int = Int.MIN_VALUE
    private val _periodic = MutableStateFlow<ReportRepository.PeriodicReport?>(null)
    val periodic: StateFlow<ReportRepository.PeriodicReport?> = _periodic
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    /**
     * v1.0.86（批次 11）：**进入报表页时才首次取数**（此前是 `init { refresh() }`，而 `AppShell`
     * 在应用启动时就创建本 VM → 哪怕从不看报表，冷启动也要查一次库）。
     *
     * v1.1.1（HIGH-3）：改为 **[onEnterReport]** ——"只取一次"这个前提是错的。
     *
     * 为什么错：本 VM 由 `AppShell` 的 `composable<Report>` 持有（作用域 = 该路由的返回栈条目），
     * 用户「报表页 → 备份页恢复数据 → 返回报表页」时**VM 还活着**，而 v1.0.86 的守卫是
     * `if (_overview.value != null || _busy.value) return`——返回本页时它直接空操作，
     * 于是周月报仍是**恢复前**的数字，且界面上没有任何提示。打卡写入同理（去今日页打卡再回来）。
     * 原 KDoc 说"数据的新鲜度由用户切窗口 / 重进页面时的参数变化自然触发"，与它自己的立项理由
     * （"重进页面会以同一参数重复调本方法"）正好矛盾——同参数重进恰恰是它跳过的那个场景。
     *
     * 现口径：**每次重新进入报表页都重查**（进入本页正是数据可能已经变了的时刻）；
     * 同一次访问内的重复查询（`HorizontalPager` 把页签滑出视口后释放、再滑回来）仍由
     * [loadPeriodic] / [setTrendDays] 的参数去重挡住——那才是 v1.0.86 想去掉的 IO。
     *
     * 残余（明说）：页面**停留在前台**期间由后台写入的数据（例如通知栏直接打卡）不会自动刷新，
     * 需要一次重新进入或下拉/重试触发 [refresh]。做到"写入即推送"需要跨模块的数据代次广播，
     * 本批次不做（详见批次说明的取舍）。
     */
    fun onEnterReport() {
        // 并发双击 / 快速来回导航时不必放两次查询进来（busy 期间的结果随后就到）
        if (_busy.value) return
        // 周月报的"参数级去重"键必须一并失效：它的 LaunchedEffect(Unit) 在本页重新进入时
        // 会再跑一次，若 loadedPeriodDays 还留着上次的值，那一页就会继续显示旧数字。
        loadedPeriodDays = Int.MIN_VALUE
        refresh()
    }

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
     *
     * v1.0.86（批次 11）：去重下沉到 [loadPeriodic]（它掌握"手上这份数据是哪段窗口"），
     * 这里只表达用户意图——点同一个 chip 不必再走一遍判重。
     */
    fun setPeriodDays(days: Int) {
        // v1.1.1（HIGH-3）：本方法不再自己判 `_periodic.value != null`——去重只认
        // [loadPeriodic] 手里的「已加载窗口」。否则"重新进入本页后点同一个 chip"会被这里提前
        // return，而那时数据可能已经变了（恢复备份、打卡），点 chip 却看不到任何变化。
        loadPeriodic(days)
    }

    /**
     * B4：加载指定窗口（7 / 30 天）的周报 / 月报小结。
     * 口径与 overview() 完全一致（部分完成计 0.5、症状空值不计入均值），文案由 UI 侧组装。
     * 失败时保留上一次结果而不是清空成空态，只走全局 Snackbar 提示——与 refresh() 同口径。
     *
     * v1.0.86（批次 11）：**参数级去重**。周月报页每次重新进入组合都会调一次本方法
     * （`LaunchedEffect(Unit)`），而 `HorizontalPager` 在页签滑出视口后会释放页面——
     * 于是「概览 → 周月报 → 概览 → 周月报」来回横跳会重复查同一窗口的库。
     *
     * 去重口径（刻意只用**参数**做键，不做时间窗缓存）：
     *   · 上次成功加载的窗口 == 本次请求的窗口 → 跳过（数据仍是这份窗口的）；
     *   · 参数变了（7 ↔ 30）→ **一定重载**，不返回旧窗口的数据；
     *   · 上次失败（[_loadedPeriodDays] 未推进）→ 下次仍会重试，不会把失败缓存住。
     * 也就是说这里省掉的是"同一份数据的重复查询"，不是"数据的新鲜度"——后者由用户切窗口 /
     * 重进页面时的参数变化自然触发。
     */
    fun loadPeriodic(days: Int) {
        if (days == loadedPeriodDays) return
        _periodDays.value = days
        viewModelScope.launch {
            _busy.value = true
            try {
                val fresh = repo.periodicReport(days)
                // v1.0.86（批次 11）：慢查询结果后到时不回写——与 setTrendDays 同款保护。
                // 快速连点 7→30 时，若 7 天的响应比 30 天的更晚返回，会把小结换成旧窗口的数据，
                // 而副标题读的是 p.days，届时"chip 显示 30 天、正文是 7 天"。
                if (_periodDays.value == days) {
                    _periodic.value = fresh
                    loadedPeriodDays = days
                }
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
                // v1.0.73（D3）：PDF 组装是多页 Canvas 绘制 + 文件写入，**必须离开主线程**——
                // 原实现直接在 viewModelScope(=Main.immediate) 里调用，化验项多时会卡顿甚至 ANR。
                val pdf = withContext(Dispatchers.IO) { ReportPdfWriter.writeCheckupReport(app, snapshot) }
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
                // v1.0.73（D3）：同上，急救卡 PDF 也在 IO 线程组装
                val pdf = withContext(Dispatchers.IO) { ReportPdfWriter.writeEmergencyCard(app, card) }
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
