package com.eyeguard.app.feature.focus

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eyeguard.app.feature.focus.data.FocusSessionEntity
import com.eyeguard.app.feature.focus.data.FocusTaskEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class FocusUiState(
    val xp: Int = 0,
    val clarityPoints: Int = 0,
    val totalMinutes: Int = 0,
    val completedSessions: Int = 0,
    val streakDays: Int = 0,
    val todayMinutes: Int = 0,
    val strictModePreferred: Boolean = false,
    val presencePreferred: Boolean = true,
    val reduceMotion: Boolean = false,
    val musicEnabled: Boolean = true,
    val allowedPackages: Set<String> = emptySet(),
    val phase: String = FocusPhase.IDLE,
    val planTargetMinutes: Int = 180,
    val planSegmentMinutes: Int = 25,
    val planRestMinutes: Int = 5,
    val planGoal: String = "",
    val planSegments: List<Int> = emptyList(),
    val currentSegmentIndex: Int = 0,
    val planCompletedMs: Long = 0L,
    val activeDurationMinutes: Int = 0,
    val remainingMs: Long = 0L,
    val restRemainingMs: Long = 0L,
    val activeStrictMode: Boolean = false,
    val activePresenceEnabled: Boolean = false,
    val timeAdjusted: Boolean = false
) {
    val isActive: Boolean get() = phase == FocusPhase.ACTIVE
    val hasActivePlan: Boolean get() = planSegments.isNotEmpty() && phase != FocusPhase.IDLE
    val currentSegmentNumber: Int get() = currentSegmentIndex + 1
    val currentSegmentCount: Int get() = planSegments.size
    val lastCompletedSegmentMinutes: Int
        get() = planSegments.getOrNull((currentSegmentIndex - 1).coerceAtLeast(0)) ?: planSegmentMinutes
    val elapsedFraction: Float
        get() = if (activeDurationMinutes > 0) {
            1f - (remainingMs.toFloat() / (activeDurationMinutes * 60_000f)).coerceIn(0f, 1f)
        } else 0f
    val currentSegmentElapsedMs: Long
        get() = (activeDurationMinutes * 60_000L - remainingMs).coerceAtLeast(0L)
    val todayProgressMs: Long
        get() = planCompletedMs + if (activeDurationMinutes > 0) currentSegmentElapsedMs else 0L
    val currentSegmentMinimum: Int
        get() = maxOf(25, ((currentSegmentElapsedMs + 59_999L) / 60_000L).toInt())
}

data class TaskRewardFeedback(
    val eventId: Long,
    val title: String,
    val xp: Int
)

@HiltViewModel
class FocusViewModel @Inject constructor(
    private val repository: FocusRepository,
    private val learningAppManager: LearningAppManager,
    private val musicManager: FocusMusicManager,
    @ApplicationContext private val context: Context
) : ViewModel() {
    private val ticker = flow {
        while (true) {
            emit(Unit)
            delay(1_000L)
        }
    }

    private val _availableApps = MutableStateFlow<List<LearningApp>>(emptyList())
    val availableApps: StateFlow<List<LearningApp>> = _availableApps.asStateFlow()

    val recentSessions: StateFlow<List<FocusSessionEntity>> = repository.recentSessions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val todayTasks: StateFlow<List<FocusTaskEntity>> = repository.todayTasks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val completion: StateFlow<FocusCompletion?> = repository.focusState.map { it.pendingCompletion }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _taskReward = MutableStateFlow<TaskRewardFeedback?>(null)
    val taskReward: StateFlow<TaskRewardFeedback?> = _taskReward.asStateFlow()

    val uiState: StateFlow<FocusUiState> = combine(repository.focusState, ticker) { snapshot, _ ->
        val timing = FocusTiming.resolve(snapshot)
        FocusUiState(
            xp = snapshot.xp,
            clarityPoints = snapshot.clarityPoints,
            totalMinutes = snapshot.totalMinutes,
            completedSessions = snapshot.completedSessions,
            streakDays = snapshot.streakDays,
            todayMinutes = snapshot.todayMinutes,
            strictModePreferred = snapshot.strictModePreferred,
            presencePreferred = snapshot.presencePreferred,
            reduceMotion = snapshot.reduceMotion,
            musicEnabled = snapshot.musicEnabled,
            allowedPackages = snapshot.allowedPackages,
            phase = snapshot.phase,
            planTargetMinutes = snapshot.planTargetMinutes,
            planSegmentMinutes = snapshot.planSegmentMinutes,
            planRestMinutes = snapshot.planRestMinutes,
            planGoal = snapshot.planGoal,
            planSegments = snapshot.planSegments,
            currentSegmentIndex = snapshot.currentSegmentIndex,
            planCompletedMs = snapshot.planCompletedMs,
            activeDurationMinutes = snapshot.activeDurationMinutes,
            remainingMs = timing.remainingMs,
            restRemainingMs = FocusTiming.restRemainingMs(snapshot),
            activeStrictMode = snapshot.activeStrictMode,
            activePresenceEnabled = snapshot.activePresenceEnabled,
            timeAdjusted = timing.timeAdjusted
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FocusUiState())

    init {
        viewModelScope.launch {
            repository.migrateIfNeeded()
            repository.archiveExpiredPlan()
            val snapshot = repository.focusState.first()
            if (snapshot.hasActivePlan && snapshot.phase !in listOf(FocusPhase.COMPLETE, FocusPhase.REST_READY)) {
                FocusService.start(context)
            }
        }
    }

    fun hasUsageAccess(): Boolean = learningAppManager.hasUsageAccess()
    fun usageSettingsIntent() = learningAppManager.usageSettingsIntent()

    fun loadAvailableApps() {
        viewModelScope.launch {
            _availableApps.value = withContext(Dispatchers.IO) { learningAppManager.installedApps() }
        }
    }

    fun toggleAllowedPackage(packageName: String) {
        viewModelScope.launch {
            val updated = repository.focusState.first().allowedPackages.toMutableSet().apply {
                if (!add(packageName)) remove(packageName)
            }
            repository.setAllowedPackages(updated)
        }
    }

    fun addTask(title: String, rewardXp: Int) {
        viewModelScope.launch { repository.addTask(title, rewardXp) }
    }

    fun changeTaskStatus(id: Long, status: String) {
        viewModelScope.launch {
            val updated = repository.changeTaskStatus(id, status)
            if (updated != null && status == FocusTaskEntity.COMPLETED) {
                _taskReward.value = TaskRewardFeedback(System.nanoTime(), updated.title, updated.rewardXp)
            }
        }
    }

    fun dismissTaskReward() {
        _taskReward.value = null
    }

    fun startPlan(target: Int, segment: Int, rest: Int, goal: String, strict: Boolean, presence: Boolean) {
        viewModelScope.launch {
            if (repository.startPlan(target, segment, rest, goal, strict, presence)) FocusService.start(context)
        }
    }

    fun startNextSegment() {
        viewModelScope.launch {
            if (repository.startNextSegment()) FocusService.start(context)
        }
    }

    fun pausePlan() {
        viewModelScope.launch {
            if (repository.pausePlan()) FocusService.stop(context)
        }
    }

    fun resumePlan() {
        viewModelScope.launch {
            if (repository.resumePlan()) FocusService.start(context)
        }
    }

    fun adjustCurrentSegment(minutes: Int) {
        viewModelScope.launch { repository.adjustCurrentSegment(minutes) }
    }

    fun abandonSession() {
        viewModelScope.launch {
            repository.abandonSession()
            FocusService.stop(context)
        }
    }

    fun endPlan() {
        viewModelScope.launch {
            repository.endPlan()
            FocusService.stop(context)
        }
    }

    fun skipRest() {
        viewModelScope.launch {
            repository.skipRest()
            FocusService.stop(context)
        }
    }

    fun setStrictModePreferred(enabled: Boolean) {
        viewModelScope.launch { repository.setStrictModePreferred(enabled) }
    }

    fun setPresencePreferred(enabled: Boolean) {
        viewModelScope.launch { repository.setPresencePreferred(enabled) }
    }

    fun setReduceMotion(enabled: Boolean) {
        viewModelScope.launch { repository.setReduceMotion(enabled) }
    }

    fun setMusicEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setMusicEnabled(enabled) }
    }

    fun setMusicScreenVisible(visible: Boolean) {
        musicManager.setScreenVisible(visible)
    }

    fun dismissCompletion() {
        viewModelScope.launch { repository.dismissCompletion() }
    }
}
