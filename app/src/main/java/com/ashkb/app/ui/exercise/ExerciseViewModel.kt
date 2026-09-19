package com.ashkb.app.ui.exercise

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ashkb.app.AshkbApplication
import com.ashkb.app.data.db.Ids
import com.ashkb.app.data.entity.ExerciseLog
import com.ashkb.app.data.entity.KbEntry
import com.ashkb.app.data.entity.Profile
import com.ashkb.app.data.repo.HealthRepository
import com.ashkb.app.data.repo.nowIso
import com.ashkb.app.domain.ExerciseEngine
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** M4 运动模块 UI 状态：当日处方（红榜）+ 黑榜拦截区 + 颈椎受累标志 */
data class ExerciseUiState(
    val plan: List<ExerciseEngine.ExerciseCard> = emptyList(),
    val blocked: List<ExerciseEngine.ExerciseCard> = emptyList(),
    val stage: String = "unknown",
    val cervicalInvolved: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class ExerciseViewModel(private val repo: HealthRepository) : ViewModel() {

    private val _date = MutableStateFlow(LocalDate.now())
    val date: LocalDate get() = _date.value

    private val library = MutableStateFlow<List<KbEntry>>(emptyList())
    private val feedbackRefresh = MutableStateFlow(0)

    init {
        viewModelScope.launch { library.value = repo.exerciseLibrary() }
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

    /** R27 矩阵：分期切换（active↔stable）当日处方即时重算 */
    // todayPlan 含 filter + sort，KB 扩容后放 Default 调度器避免阻塞主线程
    val uiState: StateFlow<ExerciseUiState> =
        combine(library, profile) { lib, p ->
            val (plan, blocked) = ExerciseEngine.todayPlan(lib, p?.diseaseStage, p?.spineMobility)
            ExerciseUiState(
                plan = plan, blocked = blocked,
                stage = p?.diseaseStage ?: "unknown",
                cervicalInvolved = ExerciseEngine.cervicalInvolved(p?.spineMobility),
            )
        }.flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ExerciseUiState())

    val todayLogs: StateFlow<List<ExerciseLog>> =
        _date.flatMapLatest { repo.observeExerciseLogs(it.toString()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 昨日症状（疼痛 / 晨僵 / 体温）——处方 hero 的判读依据 */
    val yesterdaySymptom: StateFlow<com.ashkb.app.data.entity.SymptomDaily?> =
        _date.flatMapLatest { repo.observeSymptom(it.minusDays(1).toString()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** R21：昨日已完成但未反馈的打卡（combine 取昨日日期，单层 flatMapLatest 更直白） */
    val feedbackPending: StateFlow<List<ExerciseLog>> =
        combine(_date, feedbackRefresh) { d, _ -> d.minusDays(1).toString() }
            .flatMapLatest { yesterday -> flow { emit(repo.pendingFeedbackLogs(yesterday)) } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** M4 打卡：写入当日 exercise_logs（exc 快照字段齐备，可自持） */
    fun checkIn(card: ExerciseEngine.ExerciseCard, durationMin: Int?, intensity: String?, notes: String?) {
        viewModelScope.launch {
            repo.checkInExercise(
                ExerciseLog(
                    id = Ids.new("elog"), date = date.toString(), recordedAt = nowIso(),
                    excId = card.entry.id, excKey = card.entry.id, excName = card.entry.title,
                    grade = card.grade, durationMin = durationMin, intensity = intensity,
                    status = "done", notes = notes,
                )
            )
        }
    }

    fun saveFeedback(
        logId: String,
        painChange: String?,
        stiffnessChange: String?,
        isMuscleSoreness: Boolean?,
        note: String?,
    ) {
        viewModelScope.launch {
            repo.saveExerciseFeedback(logId, painChange, stiffnessChange, isMuscleSoreness, note)
            feedbackRefresh.value += 1
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as AshkbApplication
                ExerciseViewModel(app.healthRepository)
            }
        }
    }
}
