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
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** M4 运动模块 UI 状态：当日处方（红榜）+ 黑榜拦截区 + 颈椎受累标志 */
data class ExerciseUiState(
    val plan: List<ExerciseEngine.ExerciseCard> = emptyList(),
    val blocked: List<ExerciseEngine.ExerciseCard> = emptyList(),
    val stage: String = "unknown",
    val cervicalInvolved: Boolean = false,
)

class ExerciseViewModel(private val repo: HealthRepository) : ViewModel() {

    val date: LocalDate = LocalDate.now()
    private val yesterday = date.minusDays(1).toString()

    private val library = MutableStateFlow<List<KbEntry>>(emptyList())
    private val pendingFeedback = MutableStateFlow<List<ExerciseLog>>(emptyList())

    init {
        viewModelScope.launch {
            library.value = repo.exerciseLibrary()
            pendingFeedback.value = repo.pendingFeedbackLogs(yesterday)
        }
    }

    val profile: StateFlow<Profile?> =
        repo.observeProfile().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** R27 矩阵：分期切换（active↔stable）当日处方即时重算 */
    val uiState: StateFlow<ExerciseUiState> =
        combine(library, profile) { lib, p ->
            val (plan, blocked) = ExerciseEngine.todayPlan(lib, p?.diseaseStage, p?.spineMobility)
            ExerciseUiState(
                plan = plan, blocked = blocked,
                stage = p?.diseaseStage ?: "unknown",
                cervicalInvolved = ExerciseEngine.cervicalInvolved(p?.spineMobility),
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ExerciseUiState())

    val todayLogs: StateFlow<List<ExerciseLog>> =
        repo.observeExerciseLogs(date.toString())
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** R21：昨日已完成但未反馈的打卡 */
    val feedbackPending: StateFlow<List<ExerciseLog>> = pendingFeedback

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
            pendingFeedback.value = repo.pendingFeedbackLogs(yesterday)
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
