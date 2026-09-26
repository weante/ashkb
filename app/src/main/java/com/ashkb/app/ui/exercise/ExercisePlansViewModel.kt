package com.ashkb.app.ui.exercise

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ashkb.app.AshkbApplication
import com.ashkb.app.data.entity.ExerciseLog
import com.ashkb.app.data.entity.ExercisePlan
import com.ashkb.app.data.repo.HealthRepository
import com.ashkb.app.data.repo.ReminderConfigRepository
import com.ashkb.app.domain.ExercisePlanProgress
import com.ashkb.app.domain.ExercisePlanTemplates
import com.ashkb.app.reminder.ExerciseReminderScheduler
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * B7（v1.0.39）周期康复计划页状态：计划列表 + 在用计划进度 + 最近一次种子结果。
 *
 * 进度不另存：由 `exercise_logs` 中 `status="done"` 的日期去重后与计划周窗口比较得出
 * （纯函数 `domain/ExercisePlanProgress`），此处只负责取数与装配。
 */
data class ExercisePlansUiState(
    val plans: List<ExercisePlan> = emptyList(),
    /** 当前在用计划（同一时刻至多一个） */
    val active: ExercisePlan? = null,
    /** 仅在用计划的完成度；无在用计划时为 null */
    val progress: ExercisePlanProgress.Progress? = null,
    /** 最近一次「添加模板」的返回条数：null = 尚未操作；>0 新增；0 = 模板已齐全 */
    val seededCount: Int? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class ExercisePlansViewModel(
    private val repo: HealthRepository,
    private val app: AshkbApplication,
) : ViewModel() {

    /** 种子结果（页面常驻提示用，不弹 Snackbar） */
    private val seeded = MutableStateFlow<Int?>(null)

    /** 启用 / 停用后手动重取日志：日志是一次性查询，不随 Room 失效自动刷新 */
    private val logsRefresh = MutableStateFlow(0)

    val plans: StateFlow<List<ExercisePlan>> =
        repo.observeExercisePlans()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val active: StateFlow<ExercisePlan?> =
        repo.observeActiveExercisePlan()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /**
     * 在用计划窗口内的运动日志：区间取 `[startDate, startDate + weeks*7 天]`，
     * `startDate` 为空时按今天起算（与 `ExercisePlanProgress.of` 的兜底口径一致）。
     */
    private val windowLogs: Flow<List<ExerciseLog>> =
        combine(repo.observeActiveExercisePlan(), logsRefresh) { plan, _ -> plan }
            .flatMapLatest { plan ->
                if (plan == null) {
                    flowOf(emptyList<ExerciseLog>())
                } else {
                    val start = plan.startDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                        ?: LocalDate.now()
                    flow {
                        emit(
                            repo.exerciseLogsBetween(
                                start.toString(),
                                start.plusDays(plan.weeks * 7L).toString(),
                            )
                        )
                    }
                }
            }

    val uiState: StateFlow<ExercisePlansUiState> =
        combine(plans, active, windowLogs, seeded) { list, activePlan, logs, seededCount ->
            val spec = activePlan?.let { ExercisePlanTemplates.parse(it.weekStructure) } ?: emptyList()
            ExercisePlansUiState(
                plans = list,
                active = activePlan,
                progress = activePlan?.let { ExercisePlanProgress.of(it, spec, logs, LocalDate.now()) },
                seededCount = seededCount,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ExercisePlansUiState())

    /** 幂等种入 4 / 8 / 12 周模板；结果留存供页面常驻提示。 */
    fun seed() {
        viewModelScope.launch { seeded.value = repo.seedExercisePlans() }
    }

    /** 启用计划：周次从今天起算（仓储内先全部停用，故同时只有一个在用）。 */
    fun activate(id: String) {
        viewModelScope.launch {
            repo.activateExercisePlan(id, LocalDate.now().toString())
            logsRefresh.value += 1
            // v1.0.59 B5：启用新计划后即时重排运动提醒
            rescheduleExerciseReminders()
        }
    }

    /** 停用计划：保留历史起点，便于再次启用时重新起算。 */
    fun deactivate(id: String) {
        viewModelScope.launch {
            repo.deactivateExercisePlan(id)
            logsRefresh.value += 1
            // v1.0.59 B5：停用后无 active plan → 清掉所有未来运动提醒
            ExerciseReminderScheduler.cancelAllFuture(app, LocalDate.now())
        }
    }

    /** v1.0.59 B5：重排运动提醒（按当前 active plan + 今日打卡状态） */
    private suspend fun rescheduleExerciseReminders() {
        val cfg = ReminderConfigRepository(app)
        val db = com.ashkb.app.data.db.AppDatabase.get(app)
        if (cfg.exerciseEnabled()) {
            runCatching {
                val activePlan = db.exercisePlanDao().active()
                val hasLogged = db.exerciseLogDao().byDate(LocalDate.now().toString()).isNotEmpty()
                ExerciseReminderScheduler.rescheduleAll(
                    app, activePlan, hasLogged, LocalDate.now(), LocalDateTime.now(),
                )
            }
        } else {
            ExerciseReminderScheduler.cancelAllFuture(app, LocalDate.now())
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as AshkbApplication
                ExercisePlansViewModel(app.healthRepository, app)
            }
        }
    }
}
