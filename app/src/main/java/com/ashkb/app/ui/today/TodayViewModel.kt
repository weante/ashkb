package com.ashkb.app.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import com.ashkb.app.AshkbApplication
import com.ashkb.app.data.entity.Alert
import com.ashkb.app.data.entity.Medication
import com.ashkb.app.data.entity.Profile
import com.ashkb.app.data.repo.HealthRepository
import com.ashkb.app.data.repo.MedicationRepository
import com.ashkb.app.data.repo.MinimalPromptStore
import com.ashkb.app.data.repo.TodayItem
import com.ashkb.app.data.repo.nowIso
import com.ashkb.app.domain.MinimalMode
import com.ashkb.app.reminder.ReminderScheduler
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModel(
    private val repo: MedicationRepository,
    private val healthRepo: HealthRepository,
    private val app: AshkbApplication,
) : ViewModel() {

    private val _date = MutableStateFlow(LocalDate.now())
    val date: LocalDate get() = _date.value

    init {
        viewModelScope.launch {
            while (true) {
                val now = LocalDateTime.now()
                val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay()
                delay(Duration.between(now, nextMidnight).toMillis() + 1_000L)
                _date.value = LocalDate.now()
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
        }
    }

    fun skip(item: TodayItem, reason: String, note: String?) {
        viewModelScope.launch {
            repo.skip(item.med, item.slotKey, item.slotTime, reason, note)
        }
    }

    /** 注射顺延：锚点移至新日期（提醒由调用方重排） */
    fun postpone(item: TodayItem, date: LocalDate) {
        viewModelScope.launch {
            repo.postponeInjection(item.med, date)
        }
    }

    /** 打卡或药单变化后重排提醒（v1.0.43：只取消「已打卡槽位」的后续升级，其余槽位的升级链保留） */
    fun reschedule(context: android.content.Context) {
        viewModelScope.launch {
            val meds = com.ashkb.app.data.db.AppDatabase.get(context).medicationDao().listActive()
            ReminderScheduler.rescheduleAll(context, meds, repo.doneSlotRefs(LocalDate.now()))
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
