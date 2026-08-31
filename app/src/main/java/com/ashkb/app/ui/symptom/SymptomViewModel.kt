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
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SymptomViewModel(private val repo: HealthRepository) : ViewModel() {

    val date: LocalDate = LocalDate.now()
    private val dateStr = date.toString()

    val profile: StateFlow<Profile?> =
        repo.observeProfile().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** null = 今日未记录（与「实际为 0」严格区分） */
    val symptom: StateFlow<SymptomDaily?> =
        repo.observeSymptom(dateStr).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val basdaiHistory: StateFlow<List<BasdaiRecord>> =
        repo.observeBasdai().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeFlare: StateFlow<FlareEvent?> =
        repo.observeActiveFlare().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val flareHistory: StateFlow<List<FlareEvent>> =
        repo.observeFlareRecent().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val alerts: StateFlow<List<Alert>> =
        repo.observeUnackedAlerts().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun flareDays(): Long? =
        activeFlare.value?.let { ChronoUnit.DAYS.between(LocalDate.parse(it.startDate), date) + 1 }

    /** 保存每日症状：date 为今日（编辑已有行时仓库层复用主键） */
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

    fun saveBasdai(q1: Int, q2: Int, q3: Int, q4: Int, q5: Int, q6: Int, notes: String?) {
        viewModelScope.launch { repo.saveBasdai(dateStr, q1, q2, q3, q4, q5, q6, notes) }
    }

    fun startFlare(trigger: FlareTrigger, actions: List<FlareAction>, severityPeak: Int?, notes: String?) {
        viewModelScope.launch {
            repo.startFlare(dateStr, trigger, actions, severityPeak, notes)
            // 发作开始当日即查第 7 天规则（自愈性幂等）
            repo.checkFlareDayAlert(date)
        }
    }

    fun resolveFlare(notes: String?) {
        viewModelScope.launch { repo.resolveFlare(dateStr, notes) }
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
