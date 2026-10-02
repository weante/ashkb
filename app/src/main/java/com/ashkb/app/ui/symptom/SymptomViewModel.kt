package com.ashkb.app.ui.symptom

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ashkb.app.AshkbApplication
import com.ashkb.app.data.entity.Alert
import com.ashkb.app.data.entity.BasdaiRecord
import com.ashkb.app.data.entity.FlareAction
import com.ashkb.app.data.entity.FlareEvent
import com.ashkb.app.data.entity.FlareTrigger
import com.ashkb.app.data.entity.KbEntry
import com.ashkb.app.data.entity.Profile
import com.ashkb.app.data.entity.SymptomDaily
import com.ashkb.app.data.repo.HealthRepository
import com.ashkb.app.data.repo.ReminderConfigRepository
import com.ashkb.app.data.repo.nowIso
import com.ashkb.app.domain.DateProvider
import com.ashkb.app.reminder.BasdaiReminderScheduler
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class SymptomViewModel(
    private val repo: HealthRepository,
    private val app: AshkbApplication,
    /** v1.0.86（批次 11）：全应用唯一的「今天」来源，取代此前的跨零点 ticker。 */
    private val dateProvider: DateProvider,
) : ViewModel() {

    /** 语义与批次 10 完全一致：**今天**（不是窗口起点），只是数据源换成了进程级日期流。 */
    val today: LocalDate get() = dateProvider.today.value

    /** 自评记录日期：今天 / 昨天（补写漏记） */
    private val _selectedDate = MutableStateFlow(dateProvider.today.value)
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    /**
     * 日期源是**进程级**单例，监听器必须随 VM 一起摘掉，否则被销毁的 VM 会被它一直持有。
     * 声明在 init 之前：Kotlin 按声明顺序初始化属性，反过来会在构造期读到未初始化的字段。
     */
    private var unregisterDateListener: (() -> Unit)? = null

    init {
        // v1.0.86（批次 11）：跨零点后「所选日期」可能既不是今天也不是昨天
        // （如 23:50 选的"昨天"），回落今天防两个 Chip 都不选中。
        // 此前这段逻辑写在 ticker 循环的 delay 之后（深睡不触发）；现在挂在日期源上，
        // **只在日期真的变了时**执行一次——顺带也覆盖了「手动改系统日期 / 改时区」。
        unregisterDateListener = dateProvider.addOnDateChangedListener { d ->
            val cur = _selectedDate.value
            if (cur != d && cur != d.minusDays(1)) _selectedDate.value = d
        }
    }

    override fun onCleared() {
        unregisterDateListener?.invoke()
        unregisterDateListener = null
        super.onCleared()
    }

    fun selectDate(date: LocalDate) {
        if (date == today || date == today.minusDays(1)) _selectedDate.value = date
    }

    private val dateStr: String get() = _selectedDate.value.toString()

    val profile: StateFlow<Profile?> =
        repo.observeProfile().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** null = 当日未记录（与「实际为 0」严格区分） */
    val symptom: StateFlow<SymptomDaily?> =
        dateProvider.today
            .flatMapLatest { _selectedDate }
            .flatMapLatest { repo.observeSymptom(it.toString()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val basdaiHistory: StateFlow<List<BasdaiRecord>> =
        repo.observeBasdai().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeFlare: StateFlow<FlareEvent?> =
        repo.observeActiveFlare().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val flareHistory: StateFlow<List<FlareEvent>> =
        repo.observeFlareRecent().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val alerts: StateFlow<List<Alert>> =
        repo.observeUnackedAlerts().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun flareDays(): Long? =
        activeFlare.value?.let { ChronoUnit.DAYS.between(LocalDate.parse(it.startDate), today) + 1 }

    /** 保存每日症状：写入当前所选日期（编辑已有行时仓库层复用主键） */
    fun saveSymptom(
        morningStiffnessMin: Int?,
        nightPain: Int?,
        painScore: Int?,
        feverish: Boolean,
        feverTemp: Double?,
        eyeSymptom: Boolean,
        neuroRedFlag: Boolean,
        mood: Int?,
        sleep: Int?,
        fatigue: Int?,
        notes: String?,
    ) {
        viewModelScope.launch {
            repo.saveSymptom(
                SymptomDaily(
                    id = "", // 仓库层按日期复用/生成
                    date = dateStr, recordedAt = nowIso(),
                    morningStiffnessMin = morningStiffnessMin, nightPain = nightPain, painScore = painScore,
                    feverish = feverish, feverTemp = feverTemp, eyeSymptom = eyeSymptom,
                    neuroRedFlag = neuroRedFlag, mood = mood, sleep = sleep, fatigue = fatigue,
                    notes = notes,
                )
            )
        }
    }

    fun saveBasdai(q1: Int, q2: Int, q3: Int, q4: Int, q5: Int, q6: Int, note: String?) {
        viewModelScope.launch {
            repo.saveBasdai(dateStr, q1, q2, q3, q4, q5, q6, note, backfill = _selectedDate.value != today)
            rescheduleBasdaiReminder()
        }
    }

    /**
     * v1.0.80（批次 6）：删除一条 BASDAI 自评（误录）。
     *
     * 删完必须重排提醒：下次评估日期由**最近一条**记录推算（`BasdaiReminderScheduler` 取 `latest()`），
     * 删掉最近一条会让 dueDate 整体前移——不重排就会在一次「已删除的评估」之后才提醒。
     */
    fun deleteBasdai(id: String) {
        viewModelScope.launch {
            repo.deleteBasdai(id)
            rescheduleBasdaiReminder()
        }
    }

    /**
     * v1.0.59 B5：评估记录写入 / 删除后即时重排 BASDAI 提醒（dueDate 推算依赖 `latest()`）。
     * 抽成一处：写入与删除各写一遍必然漏改一处，而漏改的症状是「提醒日期与记录对不上」。
     */
    private suspend fun rescheduleBasdaiReminder() {
        val cfg = ReminderConfigRepository(app)
        val db = com.ashkb.app.data.db.AppDatabase.get(app)
        runCatching {
            BasdaiReminderScheduler.rescheduleAll(
                app, db.basdaiDao().latest(), cfg.basdaiCycleDays(),
                LocalDate.now(), LocalDateTime.now(),
            )
        }
    }

    /**
     * v1.0.80（批次 6）：删除**当天（或所选日期）**的症状记录（误录）。
     *
     * 仓库层会连带清掉该日派生出来的红旗警报（发热 / 眼 / 神经）——
     * 否则症状页顶部会一直挂着一条指向已删记录的 high 级提示。
     */
    fun deleteSymptom() {
        viewModelScope.launch { repo.deleteSymptom(dateStr) }
    }

    fun startFlare(trigger: FlareTrigger, actions: List<FlareAction>, severityPeak: Int?, notes: String?) {
        viewModelScope.launch {
            repo.startFlare(today.toString(), trigger, actions, severityPeak, notes)
            // 发作开始当日即查第 7 天规则（自愈性幂等）
            repo.checkFlareDayAlert(today)
        }
    }

    fun resolveFlare(notes: String?) {
        viewModelScope.launch { repo.resolveFlare(today.toString(), notes) }
    }

    /**
     * v1.0.80（批次 6）：保存（编辑）一次发作登记。
     *
     * 只改内容、不改 `status`：`status` 的迁移由「登记发作 / 标记缓解」两个动作负责，
     * 允许在编辑表单里切换状态，会让「同时只有一次活跃发作」这条不变量出现第二个入口
     * （症状页的发作卡只按 `status='active'` 取最近一条，多出来的那条会永远看不见）。
     */
    fun saveFlare(event: FlareEvent) {
        viewModelScope.launch { repo.saveFlare(event, today) }
    }

    /** v1.0.80（批次 6）：删除一次发作登记（连带清掉它派生的「第 7 天」警报）。 */
    fun deleteFlare(id: String) {
        viewModelScope.launch { repo.deleteFlare(id, today) }
    }

    fun ackAlert(id: String) {
        viewModelScope.launch { repo.ackAlert(id) }
    }

    /** 警报「查看依据」：取关联知识条目 */
    suspend fun kbEntry(id: String): KbEntry? = repo.kbEntry(id)

    /** v10（B2）：症状页警报依据里也能给知识条目写个人备注（与知识库同一列） */
    fun saveKbNote(id: String, note: String?) {
        viewModelScope.launch { repo.saveKbNote(id, note) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as AshkbApplication
                SymptomViewModel(app.healthRepository, app, app.dateProvider)
            }
        }
    }
}
