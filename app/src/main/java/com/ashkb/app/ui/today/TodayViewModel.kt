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
import com.ashkb.app.data.entity.Reaction
import com.ashkb.app.data.entity.Supplement
import com.ashkb.app.data.entity.SupplementLog
import com.ashkb.app.data.repo.HealthRepository
import com.ashkb.app.data.repo.MedicationRepository
import com.ashkb.app.data.repo.MinimalPromptStore
import com.ashkb.app.data.repo.TodayItem
import com.ashkb.app.data.repo.nowIso
import com.ashkb.app.domain.AdherenceCalc
import com.ashkb.app.domain.DateProvider
import com.ashkb.app.domain.MinimalMode
import com.ashkb.app.domain.PendingDoses
import com.ashkb.app.reminder.NotificationHelper
import com.ashkb.app.reminder.ReminderScheduler
import com.ashkb.app.reminder.SupplementReminderScheduler
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
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
    /** v1.0.86（批次 11）：全应用唯一的「今天」来源，取代此前的跨零点 ticker。 */
    private val dateProvider: DateProvider,
) : ViewModel() {

    /**
     * 「今天」的**可观察**日期流。
     *
     * v1.0.84（批次 9）：[date] 只是普通 getter，UI 读它读不到跨零点的变化。`TodayScreen`
     * 于是自己用 `remember { LocalDate.now()… }`（**无 key**）取了一次日期，首次组合后永不重算：
     * 卡片里的 N 剂已按新「昨天」重算，标题上的日期却还指着前天。
     *
     * v1.0.86（批次 11）：底层换成 [DateProvider] 的进程级日期流（系统跨日广播驱动）。
     * 公开语义不变——仍是「今天」，仍是可观察的 `StateFlow<LocalDate>`。
     */
    val todayDate: StateFlow<LocalDate> = dateProvider.today

    /** [date] 保持普通 getter（跨零点不重组的调用点用它，与批次 9 同口径）。 */
    private val date: LocalDate get() = dateProvider.today.value

    // ---- v1.0.74：跨零点补记（「昨天还有 N 剂未记录」） ----

    /**
     * 昨天已到点、却既未「已服」也未「跳过」的剂量；今日页顶部据此显示补记卡。
     *
     * ⚠️ 必须声明在 [init] 之前（Kotlin 按声明顺序初始化属性）：`init` 里的
     * `viewModelScope.launch { refreshYesterdayPending() }` 在 `Dispatchers.Main.immediate`
     * 上同步内联到第一句 `withContext(Dispatchers.IO)` 便派发 IO 协程并返回；若本字段此刻
     * 尚未初始化，IO 线程读到 null，会在 `_yesterdayPending.value = …` 处抛 NPE。
     * 2026-10-18 真机间歇崩溃（`TodayViewModel.kt:255`）即由此而来，v1.2.10 修正声明顺序。
     */
    private val _yesterdayPending = MutableStateFlow<List<PendingDoses.Pending>>(emptyList())

    /** 对外只读视图：UI 用它渲染补记卡。 */
    val yesterdayPending: StateFlow<List<PendingDoses.Pending>> = _yesterdayPending

    /**
     * 日期源是**进程级**单例，监听器必须随 VM 一起摘掉，否则被销毁的 VM 会被它一直持有。
     * 声明在 init 之前：Kotlin 按声明顺序初始化属性，反过来会在构造期读到未初始化的字段。
     */
    private var unregisterDateListener: (() -> Unit)? = null

    init {
        // v1.0.74：进页面即算一次「昨天未记录」（跨零点补记卡的初值）
        viewModelScope.launch { refreshYesterdayPending() }
        // v1.0.86（批次 11）：跨日后「昨天」变了，补记卡必须重算。此前这段逻辑写在各自的
        // ticker 循环里（深睡不触发）；现在挂在日期源上——**只在日期真的变了时**回调一次。
        unregisterDateListener = dateProvider.addOnDateChangedListener {
            viewModelScope.launch { refreshYesterdayPending() }
        }
    }

    override fun onCleared() {
        unregisterDateListener?.invoke()
        unregisterDateListener = null
        super.onCleared()
    }

    val profile: StateFlow<Profile?> =
        repo.observeProfile().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val today: StateFlow<List<TodayItem>> =
        todayDate.flatMapLatest { repo.observeToday(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** P2：未读警报（红旗三通道 / BASDAI / 发作 / 复核到期） */
    val alerts: StateFlow<List<Alert>> =
        healthRepo.observeUnackedAlerts().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 今日症状是否已记录（false = 未记录，区分实际为 0） */
    val symptomRecorded: StateFlow<Boolean> =
        todayDate.flatMapLatest { healthRepo.observeSymptom(it.toString()) }.map { it != null }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /** 今日运动打卡数 */
    val exerciseDone: StateFlow<Int> =
        todayDate.flatMapLatest { healthRepo.observeExerciseLogs(it.toString()) }.map { it.size }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // ---- v1.0.65 B12：极简模式状态机 ----

    /**
     * 是否该弹「连续未记录」原因询问。
     *
     * 三个前置条件缺一不可：① 已建档；② 当前**不是**极简态（极简期间不再追问）；
     * ③ 今天还没问过。满足后再看近 [MinimalMode.THRESHOLD_DAYS] 天的症状记录是否连续缺失。
     */
    /** 回答询问后自增，仅用于驱动 [minimalPrompt] 重算（答案落在 prefs，不是 Observable）。 */
    private val answeredTick = MutableStateFlow(0)

    val minimalPrompt: StateFlow<Boolean> =
        // v1.1.4：`answeredTick` 必须进 combine——`answerMinimalPrompt` 把答案写进
        // **SharedPreferences**（不在任何 Observable 里），而这条流的其余输入是 todayDate 与
        // profile。缺了它，点任意选项都只写库、**不触发重算**，弹窗当场不消失（维护者真机反馈
        // 「点击其他原因没有反应」——实际已写入，下次进页面才生效）。
        // ⚠️ 三个选项共用这个回调，所以「身体不适 / 住院」此前也有同样的"看着没反应"。
        combine(todayDate, healthRepo.observeProfile(), answeredTick) { d, p, _ -> d to p }
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
     * 日期标题、昨日待补卡都读这条流）。
     *
     * v1.0.86（批次 11）：跨零点检测已交给 [DateProvider] 的**系统日期变更广播**（深睡时系统
     * 照发，醒来即达），这里退化为第二道防线——跟系统时钟对一次；日期没变时是空操作
     * （StateFlow 同值不重复发射，也不会多余地重查库）。
     */
    fun refreshDateIfStale() {
        dateProvider.refreshIfStale()
    }

    /** 回答询问：无论选什么都记「今天问过」；身体不适 / 住院才切极简。 */
    fun answerMinimalPrompt(reason: String) {
        viewModelScope.launch {
            MinimalPromptStore.markAsked(app, date.toString())
            if (MinimalMode.entersMinimal(reason)) {
                healthRepo.setMinimalMode(true, nowIso())
            }
            // 主动让 minimalPrompt 重算（答案写在 prefs 里，不在流的依赖中——见该流的注释）
            answeredTick.value = answeredTick.value + 1
        }
    }

    /** 退出极简模式（清 uiMode + minimalSince）。 */
    fun exitMinimalMode() {
        viewModelScope.launch { healthRepo.setMinimalMode(false, nowIso()) }
    }

    /**
     * 今日页打卡。
     *
     * v1.2.10（审查 §6.4）：`reaction` 的默认值由字面量 `"none"` 改为 [Reaction.NONE].name。
     * 两种拼写都落过库（补记路径 `repo.checkIn(…)` 走的是仓库侧的大写默认值），今天没有读取方
     * 按它分支，但等接上判读时就会变成两个不同的值。用 `Reaction.NONE.name` 而不是再写字面量，
     * 保证与仓库侧的默认值同源。
     */
    fun checkIn(item: TodayItem, injSite: String? = null, reaction: String = Reaction.NONE.name) {
        viewModelScope.launch {
            // v1.2.10（审查 L6 同口径）：把今日页的归属日显式传下去——跨零点时 `LocalDate.now()`
            // 已是次日，会把日志记到次日，原槽位永远空缺。按需药「每天只记一次」的判定也依赖
            // 这个日期：页面显示的是哪一天，就应当记在哪一天（`findPrn(med, date)` 按同一天找）。
            repo.checkIn(item.med, item.slotKey, item.slotTime, reaction, injSite, date = todayDate.value)
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
            // v1.2.10（审查 L6）：把今日页的归属日显式传下去（与 checkIn 一样）。
            // 默认值仍是 LocalDate.now()，但跨零点时 value 里的 date 才是槽位那天。
            repo.skip(item.med, item.slotKey, item.slotTime, reason, note, date = todayDate.value)
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

        // v1.2.9（审查答复裁决项 2 = A）：传**已结算**集合（done / partial / skipped）——
        // 用户记过「部分完成 / 跳过」的槽位不该被重排重建升级重查。
        ReminderScheduler.rescheduleAll(app, meds, repo.settledSlotRefs(today))
        // v1.0.77（批次 3b）：与提醒同批补物化——打卡 / 跳过 / 顺延之后计划可能变（注射顺延会挪槽位），
        // 窗口里缺的行在这里补齐；已有行由唯一索引 + IGNORE 挡住，重复调用不会重复插入。
        val win = repo.plannedSlotWindow(today)
        repo.materializePlannedSlots(meds, win.from, win.to)
    }

    // ---- v1.0.74：跨零点补记（字段声明见文件上方 init 之前） ----

    /**
     * 重算「昨天未记录」。凡是可能改变昨天记录的操作（打卡 / 跳过 / 跨日）后都要调一次。
     *
     * 判定口径走纯函数 [PendingDoses.unsettledOn] + 仓库的**已结算**槽位集合——
     * **done / partial / skipped 三态都算结算**（`AdherenceCalc.SETTLED_STATUSES`），
     * 避免用户昨天明确跳过（写了原因）或记了部分完成的剂量天天挂卡催补。
     *
     * v1.2.10（审查 L5）：这行此前只写「done 与 skipped」——代码从 v1.0.77 起就含 partial，
     * 是注释漂移。照旧注释改代码会退回「记了部分完成仍被催补」的老 bug（裁决项 2）。
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

    // ---- v1.2.9：今日补剂（维护者 m02219 反馈：补剂提醒只能在「营养与骨健康」里打卡） ----

    /**
     * 在用的补剂档案。
     *
     * 维护者原话（m02219）：「设置了补剂用药提示时间，但是在首页总是看不到，需要到营养与骨健康中打卡 /
     * 之前的方案只是提供了提醒时刻」。
     *
     * 数据直接取自 `SupplementDao`（而不是复用根级 `WellnessViewModel`）：今日页与营养页是两个
     * 独立可进入的界面，今日页不该因为「营养页没被创建过」而拿不到数据；而 `WellnessViewModel`
     * 的 `supplements` 也只是同一个 DAO 的同名流，重复订阅一条 Room `Flow` 的成本由 Room 的
     * InvalidationTracker 摊薄，不新增查询。
     */
    val supplements: StateFlow<List<Supplement>> =
        AppDatabase.get(app).supplementDao().observeActive()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 今日的补剂打卡行（可能同一补剂多行，取最后表态者由 `SupplementLogStatus.loggedToday` 决定）。 */
    val supplementLogsToday: StateFlow<List<SupplementLog>> =
        todayDate.flatMapLatest { AppDatabase.get(app).supplementLogDao().observeByDate(it.toString()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * 今日补剂打卡 / 跳过。
     *
     * 写库是同一条路径（`HealthRepository.checkInSupplement`，按「天 × 补剂」幂等），
     * **必须跟着重排补剂提醒**——补剂打卡按天记，记过当天其余时刻的提醒要立刻撤下，
     * 否则用户刚打完卡还会被后面几个时刻连着提醒（这正是「打卡了还响」的老坑）。
     * 重排入口与营养页共用 [SupplementReminderScheduler.rescheduleAll]，参数一致。
     */
    fun checkInSupplement(sup: Supplement, status: String) {
        viewModelScope.launch {
            healthRepo.checkInSupplement(
                SupplementLog(
                    id = "",
                    date = date.toString(),
                    recordedAt = nowIso(),
                    backfill = false,
                    supId = sup.id,
                    // 与营养页逐字一致（`WellnessViewModel.checkInSupplement`）：补剂没有通用名键
                    // 概念，`supKey` 直接落 `sup.id`
                    supKey = sup.id,
                    supName = sup.name,
                    doseSnapshot = sup.dose,
                    // 打卡不绑定具体时刻（slotKey 恒 NULL，见 SupplementLog 的按天口径）
                    scheduledTime = null,
                    slotKey = null,
                    status = status,
                    reason = null,
                    takenAt = if (status == AdherenceCalc.DONE) nowIso() else null,
                    notes = null,
                ),
            )
            // 撤掉今天该补剂已挂的通知（若有），再整体重排未来 7 天：
            // 补剂打卡按天记，记过当天其余时刻的提醒要立刻撤下，否则用户刚打完卡还会被
            // 后面几个时刻连着提醒（v1.2.4 修过的「打卡了还响」）。
            // C05（缺陷工单）：取消也要在 IO——它同样是「补剂 × 时刻」量级的 binder 循环。
            withContext(Dispatchers.IO) {
                SupplementReminderScheduler.cancelAllFuture(app, supplements.value, date)
            }
            rescheduleSupplementReminders()
        }
    }

    /** 撤销今日补剂打卡：删掉当天全部行，并把提醒重新排回来。 */
    fun undoSupplementCheckIn(sup: Supplement) {
        viewModelScope.launch {
            val today = date.toString()
            AppDatabase.get(app).supplementLogDao().byDate(today)
                .filter { it.supId == sup.id }
                .forEach { healthRepo.deleteSupplementLog(it.id) }
            rescheduleSupplementReminders()
        }
    }

    /**
     * 重排补剂提醒（与 `WellnessViewModel.rescheduleSupplementReminders` 同一口径）。
     *
     * 整段在 IO 线程：`rescheduleAll` 是「补剂 × 7 天 × 时刻」量级的 AlarmManager binder 调用。
     */
    private suspend fun rescheduleSupplementReminders() = withContext(Dispatchers.IO) {
        runCatching {
            val db = AppDatabase.get(app)
            val today = date
            SupplementReminderScheduler.rescheduleAll(
                app,
                db.supplementDao().listActive(),
                db.supplementLogDao().byDate(today.toString()).mapNotNull { it.supId }.toSet(),
                today,
                LocalDateTime.now(),
            )
        }
        Unit
    }

    companion object {
        val Factory: ViewModelProvider.Factory = androidx.lifecycle.viewmodel.viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as AshkbApplication
                TodayViewModel(app.medicationRepository, app.healthRepository, app, app.dateProvider)
            }
        }
    }
}
