package com.eyeguard.app.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eyeguard.app.core.billing.BillingRepository
import com.eyeguard.app.core.database.DailyUsageEntity
import com.eyeguard.app.core.database.DailyUsageRepository
import com.eyeguard.app.core.datastore.SettingsRepository
import com.eyeguard.app.core.service.EyeGuardService
import com.eyeguard.app.feature.report.StatsLogic
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 首页数据 ViewModel：订阅今日与最近 7 天用眼统计、守护开关意图与运行状态。
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    dailyUsageRepository: DailyUsageRepository,
    billingRepository: BillingRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    /** 是否 Pro（7 天历史图表与周报为 Pro 功能）。 */
    val isPro: StateFlow<Boolean> = billingRepository.isPro
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /** 守护开关的用户意图（持久化）：开机自启与"服务被杀"检测都以它为准。 */
    val guardEnabled: StateFlow<Boolean> = settingsRepository.guardEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /** 守护服务运行状态（每 2 秒轮询一次，页面可见期间有效）。 */
    val guardRunning: StateFlow<Boolean> = flow {
        while (true) {
            emit(EyeGuardService.isRunning)
            kotlinx.coroutines.delay(2000)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), EyeGuardService.isRunning)

    /** 用户开了守护但服务没在运行 → 显示"服务被系统停止"修复引导卡片。 */
    val showRepairCard: StateFlow<Boolean> = combine(guardEnabled, guardRunning) { enabled, running ->
        enabled && !running
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /** 今日统计（无记录时为空记录）。 */
    val todayStats: StateFlow<DailyUsageEntity?> = dailyUsageRepository.todayStats
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** 最近 7 天统计（DAO 返回按日期倒序）。 */
    val recentStats: StateFlow<List<DailyUsageEntity>> =
        dailyUsageRepository.recentStats(days = 7)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 连续达标天数（与周报同口径）。 */
    val streakDays: StateFlow<Int> = combine(recentStats) { arr ->
        StatsLogic.computeStreak(arr.first())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /** 记录守护开关意图（由 UI 在启动 / 停止服务时同步调用）。 */
    fun setGuardEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setGuardEnabled(enabled) }
    }
}
