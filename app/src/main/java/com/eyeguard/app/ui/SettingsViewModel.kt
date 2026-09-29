package com.eyeguard.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eyeguard.app.core.billing.BillingRepository
import com.eyeguard.app.core.datastore.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 设置相关状态的共享 ViewModel：把 SettingsRepository 的 Flow 转成 StateFlow，
 * 供设置页与权限引导页订阅与写入。
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val billingRepository: BillingRepository
) : ViewModel() {

    val distanceAlertEnabled: StateFlow<Boolean> = settingsRepository.distanceAlertEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val distanceSamplingIntervalSeconds: StateFlow<Long> =
        settingsRepository.distanceSamplingIntervalSeconds
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 30L)

    val distanceThresholdCm: StateFlow<Int> = settingsRepository.distanceThresholdCm
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 30)

    val remindIntervalMinutes: StateFlow<Long> = settingsRepository.remindIntervalMinutes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 30L)

    val lightAlertEnabled: StateFlow<Boolean> = settingsRepository.lightAlertEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val lightThresholdLux: StateFlow<Int> = settingsRepository.lightThresholdLux
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 50)

    val usageAlertEnabled: StateFlow<Boolean> = settingsRepository.usageAlertEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val usageThresholdMinutes: StateFlow<Int> = settingsRepository.usageThresholdMinutes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 20)

    val filterEnabled: StateFlow<Boolean> = settingsRepository.filterEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val filterIntensity: StateFlow<Int> = settingsRepository.filterIntensity
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1)

    val filterSmartEnabled: StateFlow<Boolean> = settingsRepository.filterSmartEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val nightStartMinutes: StateFlow<Int> = settingsRepository.nightStartMinutes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 22 * 60)

    val nightEndMinutes: StateFlow<Int> = settingsRepository.nightEndMinutes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 7 * 60)

    val dndEnabled: StateFlow<Boolean> = settingsRepository.dndEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val dndStartMinutes: StateFlow<Int> = settingsRepository.dndStartMinutes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 23 * 60)

    val dndEndMinutes: StateFlow<Int> = settingsRepository.dndEndMinutes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 7 * 60)

    /** 用户是否持有 Pro entitlement（由 BillingRepository 统一发布）。 */
    val isPro: StateFlow<Boolean> = billingRepository.isPro
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /** 应用语言（"zh"/"en"）。 */
    val appLanguage: StateFlow<String> = settingsRepository.appLanguage
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "zh")

    fun setAppLanguage(language: String) {
        viewModelScope.launch { settingsRepository.setAppLanguage(language) }
    }

    fun setDistanceAlertEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setDistanceAlertEnabled(enabled) }
    }

    fun setDistanceSamplingIntervalSeconds(seconds: Long) {
        viewModelScope.launch { settingsRepository.setDistanceSamplingIntervalSeconds(seconds) }
    }

    fun setDistanceThresholdCm(cm: Int) {
        viewModelScope.launch { settingsRepository.setDistanceThresholdCm(cm) }
    }

    fun setLightAlertEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setLightAlertEnabled(enabled) }
    }

    fun setLightThresholdLux(lux: Int) {
        viewModelScope.launch { settingsRepository.setLightThresholdLux(lux) }
    }

    fun setUsageAlertEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setUsageAlertEnabled(enabled) }
    }

    fun setUsageThresholdMinutes(minutes: Int) {
        viewModelScope.launch { settingsRepository.setUsageThresholdMinutes(minutes) }
    }

    fun setDndEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setDndEnabled(enabled) }
    }

    fun setDndStartMinutes(minutes: Int) {
        viewModelScope.launch { settingsRepository.setDndStartMinutes(minutes) }
    }

    fun setDndEndMinutes(minutes: Int) {
        viewModelScope.launch { settingsRepository.setDndEndMinutes(minutes) }
    }

    fun setFilterEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setFilterEnabled(enabled) }
    }

    fun setFilterIntensity(level: Int) {
        viewModelScope.launch { settingsRepository.setFilterIntensity(level) }
    }

    fun setFilterSmartEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setFilterSmartEnabled(enabled) }
    }

    fun setNightStartMinutes(minutes: Int) {
        viewModelScope.launch { settingsRepository.setNightStartMinutes(minutes) }
    }

    fun setNightEndMinutes(minutes: Int) {
        viewModelScope.launch { settingsRepository.setNightEndMinutes(minutes) }
    }

    /** 记录守护开关意图（权限引导页"开始守护"按钮同步写入）。 */
    fun setGuardEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setGuardEnabled(enabled) }
    }
}
