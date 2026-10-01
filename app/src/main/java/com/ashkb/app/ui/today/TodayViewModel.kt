package com.ashkb.app.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import com.ashkb.app.AshkbApplication
import com.ashkb.app.data.db.AppDatabase
import com.ashkb.app.data.entity.Alert
import com.ashkb.app.data.entity.Medication
import com.ashkb.app.data.entity.Profile
import com.ashkb.app.data.repo.HealthRepository
import com.ashkb.app.data.repo.MedicationRepository
import com.ashkb.app.data.repo.MinimalPromptStore
import com.ashkb.app.data.repo.TodayItem
import com.ashkb.app.data.repo.nowIso
import com.ashkb.app.domain.MinimalMode
import com.ashkb.app.domain.PendingDoses
import com.ashkb.app.reminder.NotificationHelper
import com.ashkb.app.reminder.ReminderScheduler
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModel(
    private val repo: MedicationRepository,
    private val healthRepo: HealthRepository,
    private val app: AshkbApplication,
) : ViewModel() {

    private val _date = MutableStateFlow(LocalDate.now())
    val date: LocalDate get() = _date.value

    /**
     * 「今天」的**可观察**日期流——由 init 里既有的跨零点 ticker 推进（不新起 ticker）。
     *
     * v1.0.84（批次 9）：[date] 只是普通 getter，UI 读它读不到跨零点的变化。`TodayScreen`
     * 于是自己用 `remember { LocalDate.now()… }`（**无 key**）取了一次日期，首次组合后永不重算：
     * 卡片里的 N 剂已按新「昨天」重算，标题上的日期却还指着前天。
     */
    val todayDate: StateFlow<LocalDate> = _date.asStateFlow()

    init {
        viewModelScope.launch {
            // v1.0.74：进页面即算一次「昨天未记录」（跨零点补记卡的初值）
            refreshYesterdayPending()
            while (true) {
                val now = LocalDateTime.now()
                val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay()
                delay(Duration.between(now, nextMidnight).toMillis() + 1_000L)
                _date.value = LocalDate.now()
                // 跨日后「昨天」变了，补记卡必须重算
                refreshYesterdayPending()
            }
        }
    }

    val profile: StateFlow<Profile?> =
        repo.observeProfile().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val today: StateFlow<List<TodayItem>> =
        _date.flatMapLatest { repo.observeToday(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** P2：未读警报（红旗三通道 / BASDAI / 发作 / 复核到期） */
    val alerts: StateFlow<List<Alert>> =
        healthRepo.observeUnackedAlerts().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 今日症状是否已记录（false = 未记录，区分实际为 0） */
    val symptomRecorded: StateFlow<Boolean> =
        _date.flatMapLatest { healthRepo.observeSymptom(it.toString()) }.map { it != null }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /** 今日运动打卡数 */
    val exerciseDone: StateFlow<Int> =
        _date.flatMapLatest { healthRepo.observeExerciseLogs(it.toString()) }.map { it.size }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // ---- v1.0.65 B12：极简模式状态机 ----

    /**
     * 是否该弹「连续未记录」原因询问。
     *
     * 三个前置条件缺一不可：① 已建档；② 当前**不是**极简态（极简期间不再追问）；
     * ③ 今天还没问过。满足后再看近 [MinimalMode.THRESHOLD_DAYS] 天的症状记录是否连续缺失。
     */
    val minimalPrompt: StateFlow<Boolean> =
        combine(_date, healthRepo.observeProfile()) { d, p -> d to p }
            .map { (d, p) ->
                if (p == null || p.uiMode == MinimalMode.MODE_MINIMAL) return@map false
                if (MinimalPromptStore.lastAskedDate(app) == d.toString()) return@map false
                val from = d.minusDays(MinimalMode.THRESHOLD_DAYS - 1L).toString()
                MinimalMode.shouldPrompt(healthRepo.symptomDatesBetween(from, d.toString()), d)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /**
     * 回到前台时跟系统时钟对一次「今天」——补上深睡期间错过的跨零点。
     *
     * v1.0.84（批次 9）：init 里的 ticker 用协程 `delay`，在 Android 上落到主线程 Handler 的
     * `postDelayed`，而它按 **uptimeMillis** 计时——**深睡不计时**。夜里手机睡着时跨零点那一刻
     * 不会触发，ticker 可能要到早上醒来才补跑；期间「今天 / 昨天」都会落后一天（本页的
     * 日期标题、昨日待补卡都读这条流）。这里只做「拉一次」的补齐，**不新起 ticker**；
     * 日期没变时是空操作（StateFlow 同值不重复发射，也不会多余地重查库）。
     */
    fun refreshDateIfStale() {
        val now = LocalDate.now()
        if (_date.value == now) return
        _date.value = now
        viewModelScope.launch { refreshYesterdayPending() }
    }

    /** 回答询问：无论选什么都记「今天问过」；身体不适 / 住院才切极简。 */
    fun answerMinimalPrompt(reason: String) {
        viewModelScope.launch {
            MinimalPromptStore.markAsked(app, date.toString())
            if (MinimalMode.entersMinimal(reason)) {
                healthRepo.setMinimalMode(true, nowIso())
            }
        }
    }

    /** 退出极简模式（清 uiMode + minimalSince）。 */
    fun exitMinimalMode() {
        viewModelScope.launch { healthRepo.setMinimalMode(false, nowIso()) }
    }

    fun checkIn(item: TodayItem, injSite: String? = null, reaction: String = "none") {
        viewModelScope.launch {
            repo.checkIn(item.med, item.slotKey, item.slotTime, reaction, injSite)
            // v1.0.73：应用内打卡后撤掉该槽位已显示的通知——此前只有「通知动作」与「全屏页」
            // 会撤，从今日页打卡会让通知一直挂到下一次升级触发才被清掉。
            NotificationHelper.cancel(app, item.med.id, item.slotKey)
            // v1.0.73（P1-17）：重排**紧随写入之后在同一协程内**完成——原先是 UI 侧另起一个协程
            // 调 reschedule，`doneSlotRefs` 可能在打卡落库前读到，于是给刚打卡的槽位重排 +30/+60。
            rescheduleInternal()
            refreshYesterdayPending()
        }
    }

    fun skip(item: TodayItem, reason: String, note: String?) {
        viewModelScope.launch {
            repo.skip(item.med, item.slotKey, item.slotTime, reason, note)
            // 主动跳过＝已结算：撤通知并重排（配合 ReminderReceiver 的 skipped 判定，
            // 不再对用户明确跳过的剂量继续加急）
            NotificationHelper.cancel(app, item.med.id, item.slotKey)
            rescheduleInternal()
            refreshYesterdayPending()
        }
    }

    /** 注射顺延：锚点移至新日期，随后重排（提醒不再指向旧日期） */
    fun postpone(item: TodayItem, date: LocalDate) {
        viewModelScope.launch {
            repo.postponeInjection(item.med, date)
            rescheduleInternal()
        }
    }

    /**
     * 打卡 / 跳过 / 顺延后的重排。
     *
     * v1.0.73（P1-17）：整段移出主线程——重排是「药 × 槽 × 3 次 binder 调用」量级
     * （约 8 天窗口），在主线程上会造成保存/打卡卡顿甚至 ANR。
     */
    private suspend fun rescheduleInternal() = withContext(Dispatchers.IO) {
        val meds = AppDatabase.get(app).medicationDao().listActive()
        val today = LocalDate.now()

        ReminderScheduler.rescheduleAll(app, meds, repo.doneSlotRefs(today))
        // v1.0.77（批次 3b）：与提醒同批补物化——打卡 / 跳过 / 顺延之后计划可能变（注射顺延会挪槽位），
        // 窗口里缺的行在这里补齐；已有行由唯一索引 + IGNORE 挡住，重复调用不会重复插入。
        val win = repo.plannedSlotWindow(today)
        repo.materializePlannedSlots(meds, win.from, win.to)
    }

    // ---- v1.0.74：跨零点补记（「昨天还有 N 剂未记录」） ----

    private val _yesterdayPending = MutableStateFlow<List<PendingDoses.Pending>>(emptyList())

    /** 昨天已到点、却既未「已服」也未「跳过」的剂量；今日页顶部据此显示补记卡。 */
    val yesterdayPending: StateFlow<List<PendingDoses.Pending>> = _yesterdayPending

    /**
     * 重算「昨天未记录」。凡是可能改变昨天记录的操作（打卡 / 跳过 / 跨日）后都要调一次。
     *
     * 判定口径走纯函数 [PendingDoses.unsettledOn] + 仓库的**已结算**槽位集合——
     * done 与 skipped 都算结算，避免用户昨天明确跳过（写了原因）的剂量天天挂卡催补。
     */
    suspend fun refreshYesterdayPending() = withContext(Dispatchers.IO) {
        // v1.0.84（批次 9）：用**与 UI 同一个**日期源（ticker 推进的 date），不再各读一次系统时钟——
        // 否则卡里的剂量按「系统时钟的昨天」算、卡上标题按「日期流的昨天」显示，跨零点前后会差一天。
        val yesterday = date.minusDays(1)
        val settled = repo.settledSlotRefs(yesterday)
        val meds = AppDatabase.get(app).medicationDao().listActive()
        _yesterdayPending.value = PendingDoses.unsettledOn(
            date = yesterday,
            meds = meds,
            now = LocalDateTime.now(),
            isSettled = { medId, slotKey -> ReminderScheduler.slotRef(medId, slotKey) in settled },
        )
    }

    /**
     * 补记昨天那一剂为「已服」——**写入槽位所属日**（昨天），不会算作今天。
     *
     * 这是 2026-09-30 真机实测暴露的缺口：用户在跨零点追问后回到应用打卡时，今日页只能给
     * 「今天」的槽位打卡，于是昨天那剂永远补不上、而今天的剂量被提前 23 小时记录。
     */
    fun checkInYesterday(p: PendingDoses.Pending) {
        viewModelScope.launch {
            val med = repo.medicationById(p.medId) ?: return@launch
            repo.checkIn(med, p.slotKey, p.slotTime, date = p.date)
            // 该槽位若还有升级提醒挂着，一并撤掉
            NotificationHelper.cancel(app, p.medId, p.slotKey)
            rescheduleInternal()
            refreshYesterdayPending()
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = androidx.lifecycle.viewmodel.viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as AshkbApplication
                TodayViewModel(app.medicationRepository, app.healthRepository, app)
            }
        }
    }
}
