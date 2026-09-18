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
import com.ashkb.app.data.repo.nowIso
import java.time.Duration
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
class SymptomViewModel(private val repo: HealthRepository) : ViewModel() {

    private val _date = MutableStateFlow(LocalDate.now())
    val today: LocalDate get() = _date.value

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

    /** 自评记录日期：今天 / 昨天（补写漏记） */
    private val _selectedDate = MutableStateFlow(today)
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    fun selectDate(date: LocalDate) {
        if (date == today || date == today.minusDays(1)) _selectedDate.value = date
    }

    private val dateStr: String get() = _selectedDate.value.toString()

    val profile: StateFlow<Profile?> =
        repo.observeProfile().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** null = 当日未记录（与「实际为 0」严格区分） */
    val symptom: StateFlow<SymptomDaily?> =
        _date
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
        }
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

    fun ackAlert(id: String) {
        viewModelScope.launch { repo.ackAlert(id) }
    }

    /** 警报「查看依据」：取关联知识条目 */
    suspend fun kbEntry(id: String): KbEntry? = repo.kbEntry(id)

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as AshkbApplication
                SymptomViewModel(app.healthRepository)
            }
        }
    }
}
