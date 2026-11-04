package com.eyeguard.app.feature.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eyeguard.app.core.billing.BillingRepository
import com.eyeguard.app.core.database.DailyUsageEntity
import com.eyeguard.app.core.database.DailyUsageRepository
import com.eyeguard.app.core.datastore.SettingsRepository
import com.eyeguard.app.core.service.EyeGuardService
import com.eyeguard.app.feature.focus.FocusPhase
import com.eyeguard.app.feature.focus.FocusProgression
import com.eyeguard.app.feature.focus.FocusRepository
import com.eyeguard.app.feature.focus.FocusService
import com.eyeguard.app.feature.focus.FocusTiming
import com.eyeguard.app.feature.report.StatsLogic
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeFocusState(
    val phase: String = FocusPhase.IDLE,
    val hasPlan: Boolean = false,
    val remainingMs: Long = 0L,
    val restRemainingMs: Long = 0L,
    val realmIndex: Int = 0,
    val layer: Int = 1,
    val xp: Int = 0,
    val clarityPoints: Int = 0,
    val strictMode: Boolean = false,
    val reduceMotion: Boolean = false,
    val todayProgressMs: Long = 0L,
    val targetMinutes: Int = 0
) {
    val isActive: Boolean get() = phase == FocusPhase.ACTIVE
}

/** 首页数据 ViewModel：护眼统计与实时修炼状态。 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    dailyUsageRepository: DailyUsageRepository,
    billingRepository: BillingRepository,
    focusRepository: FocusRepository,
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository
) : ViewModel() {
    val isPro: StateFlow<Boolean> = billingRepository.isPro
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val guardEnabled: StateFlow<Boolean> = settingsRepository.guardEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val guardRunning: StateFlow<Boolean> = flow {
        while (true) {
            emit(EyeGuardService.isRunning)
            delay(2000)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), EyeGuardService.isRunning)

    val showRepairCard: StateFlow<Boolean> = combine(guardEnabled, guardRunning) { enabled, running ->
        enabled && !running
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val todayStats: StateFlow<DailyUsageEntity?> = dailyUsageRepository.todayStats
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val recentStats: StateFlow<List<DailyUsageEntity>> =
        dailyUsageRepository.recentStats(days = 7)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val streakDays: StateFlow<Int> = combine(recentStats) { arr ->
        StatsLogic.computeStreak(arr.first())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val ticker = flow {
        while (true) {
            emit(Unit)
            delay(1_000L)
        }
    }

    val focusState: StateFlow<HomeFocusState> = combine(focusRepository.focusState, ticker) { snapshot, _ ->
        val level = FocusProgression.levelFor(snapshot.xp)
        val activeElapsed = if (snapshot.activeDurationMinutes > 0) {
            snapshot.activeDurationMinutes * 60_000L - FocusTiming.resolve(snapshot).remainingMs
        } else 0L
        HomeFocusState(
            phase = snapshot.phase,
            hasPlan = snapshot.hasActivePlan,
            remainingMs = FocusTiming.resolve(snapshot).remainingMs,
            restRemainingMs = FocusTiming.restRemainingMs(snapshot),
            realmIndex = level.realmIndex,
            layer = level.layer,
            xp = snapshot.xp,
            clarityPoints = snapshot.clarityPoints,
            strictMode = snapshot.activeStrictMode,
            reduceMotion = snapshot.reduceMotion,
            todayProgressMs = snapshot.planCompletedMs + activeElapsed.coerceAtLeast(0L),
            targetMinutes = snapshot.planTargetMinutes
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeFocusState())

    init {
        viewModelScope.launch {
            focusRepository.migrateIfNeeded()
            focusRepository.archiveExpiredPlan()
            val snapshot = focusRepository.focusState.first()
            if (snapshot.phase in listOf(FocusPhase.ACTIVE, FocusPhase.RESTING, FocusPhase.PRESENCE_PAUSED)) {
                FocusService.start(context)
            }
        }
    }

    fun setGuardEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setGuardEnabled(enabled) }
    }
}
