package com.eyeguard.app.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "eyeguard_settings"
)

/**
 * 基于 Preferences DataStore 的用户设置存储。
 *
 * 提供各护眼功能的开关与提醒间隔读写，以 Flow 形式向上层暴露。
 */
@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private object Keys {
        val DISTANCE_ALERT_ENABLED = booleanPreferencesKey("distance_alert_enabled")
        val LIGHT_ALERT_ENABLED = booleanPreferencesKey("light_alert_enabled")
        val USAGE_ALERT_ENABLED = booleanPreferencesKey("usage_alert_enabled")
        val FILTER_ENABLED = booleanPreferencesKey("filter_enabled")
        val REMIND_INTERVAL_MINUTES = longPreferencesKey("remind_interval_minutes")
        val DISTANCE_SAMPLING_INTERVAL_SECONDS = longPreferencesKey("distance_sampling_interval_seconds")
        val DISTANCE_THRESHOLD_CM = intPreferencesKey("distance_threshold_cm")
        val LIGHT_THRESHOLD_LUX = intPreferencesKey("light_threshold_lux")
        val USAGE_THRESHOLD_MINUTES = intPreferencesKey("usage_threshold_minutes")
        val FILTER_INTENSITY = intPreferencesKey("filter_intensity")
        val FILTER_SMART_ENABLED = booleanPreferencesKey("filter_smart_enabled")
        val NIGHT_START_MINUTES = intPreferencesKey("night_start_minutes")
        val NIGHT_END_MINUTES = intPreferencesKey("night_end_minutes")
        val DND_ENABLED = booleanPreferencesKey("dnd_enabled")
        val DND_START_MINUTES = intPreferencesKey("dnd_start_minutes")
        val DND_END_MINUTES = intPreferencesKey("dnd_end_minutes")
        val DEMO_PRO_UNLOCKED = booleanPreferencesKey("demo_pro_unlocked")
        val APP_LANGUAGE = stringPreferencesKey("app_language")
        val GUARD_ENABLED = booleanPreferencesKey("guard_enabled")
    }

    /** 距离过近提醒开关。 */
    val distanceAlertEnabled: Flow<Boolean> =
        context.settingsDataStore.data.map { it[Keys.DISTANCE_ALERT_ENABLED] ?: true }

    /** 环境光线提醒开关。 */
    val lightAlertEnabled: Flow<Boolean> =
        context.settingsDataStore.data.map { it[Keys.LIGHT_ALERT_ENABLED] ?: false }

    /** 用眼时长提醒开关。 */
    val usageAlertEnabled: Flow<Boolean> =
        context.settingsDataStore.data.map { it[Keys.USAGE_ALERT_ENABLED] ?: true }

    /** 护眼滤色开关。 */
    val filterEnabled: Flow<Boolean> =
        context.settingsDataStore.data.map { it[Keys.FILTER_ENABLED] ?: false }

    /** 提醒间隔（分钟）。 */
    val remindIntervalMinutes: Flow<Long> =
        context.settingsDataStore.data.map { it[Keys.REMIND_INTERVAL_MINUTES] ?: 30L }

    /** 距离检测采样间隔（秒），默认 30 秒。 */
    val distanceSamplingIntervalSeconds: Flow<Long> =
        context.settingsDataStore.data.map { it[Keys.DISTANCE_SAMPLING_INTERVAL_SECONDS] ?: 30L }

    /** 距离过近判定阈值（厘米），默认 30cm。 */
    val distanceThresholdCm: Flow<Int> =
        context.settingsDataStore.data.map { it[Keys.DISTANCE_THRESHOLD_CM] ?: 30 }

    /** 环境光过暗判定阈值（lux），默认 50 lux。 */
    val lightThresholdLux: Flow<Int> =
        context.settingsDataStore.data.map { it[Keys.LIGHT_THRESHOLD_LUX] ?: 50 }

    /** 连续用眼提醒阈值（分钟），默认 20 分钟（20-20-20 法则）。 */
    val usageThresholdMinutes: Flow<Int> =
        context.settingsDataStore.data.map { it[Keys.USAGE_THRESHOLD_MINUTES] ?: 20 }

    /** 护眼遮罩强度档位（0=浅 1=中 2=深），默认中档。 */
    val filterIntensity: Flow<Int> =
        context.settingsDataStore.data.map { it[Keys.FILTER_INTENSITY] ?: 1 }

    /** 遮罩智能模式：按环境光 / 夜间时段自动调节。 */
    val filterSmartEnabled: Flow<Boolean> =
        context.settingsDataStore.data.map { it[Keys.FILTER_SMART_ENABLED] ?: true }

    /** 夜间时段开始（当天分钟数），默认 22:00。 */
    val nightStartMinutes: Flow<Int> =
        context.settingsDataStore.data.map { it[Keys.NIGHT_START_MINUTES] ?: 22 * 60 }

    /** 夜间时段结束（当天分钟数），默认 07:00。 */
    val nightEndMinutes: Flow<Int> =
        context.settingsDataStore.data.map { it[Keys.NIGHT_END_MINUTES] ?: 7 * 60 }

    /** 免打扰开关（Pro 功能：时段内所有提醒静音）。 */
    val dndEnabled: Flow<Boolean> =
        context.settingsDataStore.data.map { it[Keys.DND_ENABLED] ?: false }

    /** 免打扰开始（当天分钟数），默认 23:00。 */
    val dndStartMinutes: Flow<Int> =
        context.settingsDataStore.data.map { it[Keys.DND_START_MINUTES] ?: 23 * 60 }

    /** 免打扰结束（当天分钟数），默认 07:00。 */
    val dndEndMinutes: Flow<Int> =
        context.settingsDataStore.data.map { it[Keys.DND_END_MINUTES] ?: 7 * 60 }

    /**
     * 演示模式 Pro 解锁标记（黑客松演示用）：
     * BuildConfig.DEMO_BILLING 开启时，点"恢复购买"即置为 true 并持久化，
     * 与 RevenueCat entitlement 做"或"运算得到 isPro。
     */
    val demoProUnlocked: Flow<Boolean> =
        context.settingsDataStore.data.map { it[Keys.DEMO_PRO_UNLOCKED] ?: false }

    suspend fun setDistanceAlertEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.DISTANCE_ALERT_ENABLED] = enabled }
    }

    suspend fun setLightAlertEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.LIGHT_ALERT_ENABLED] = enabled }
    }

    suspend fun setUsageAlertEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.USAGE_ALERT_ENABLED] = enabled }
    }

    suspend fun setFilterEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.FILTER_ENABLED] = enabled }
    }

    suspend fun setRemindIntervalMinutes(minutes: Long) {
        context.settingsDataStore.edit { it[Keys.REMIND_INTERVAL_MINUTES] = minutes }
    }

    suspend fun setDistanceSamplingIntervalSeconds(seconds: Long) {
        context.settingsDataStore.edit { it[Keys.DISTANCE_SAMPLING_INTERVAL_SECONDS] = seconds }
    }

    suspend fun setDistanceThresholdCm(cm: Int) {
        context.settingsDataStore.edit { it[Keys.DISTANCE_THRESHOLD_CM] = cm }
    }

    suspend fun setLightThresholdLux(lux: Int) {
        context.settingsDataStore.edit { it[Keys.LIGHT_THRESHOLD_LUX] = lux }
    }

    suspend fun setUsageThresholdMinutes(minutes: Int) {
        context.settingsDataStore.edit { it[Keys.USAGE_THRESHOLD_MINUTES] = minutes }
    }

    suspend fun setFilterIntensity(level: Int) {
        context.settingsDataStore.edit { it[Keys.FILTER_INTENSITY] = level.coerceIn(0, 2) }
    }

    suspend fun setFilterSmartEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.FILTER_SMART_ENABLED] = enabled }
    }

    suspend fun setNightStartMinutes(minutes: Int) {
        context.settingsDataStore.edit { it[Keys.NIGHT_START_MINUTES] = minutes }
    }

    suspend fun setNightEndMinutes(minutes: Int) {
        context.settingsDataStore.edit { it[Keys.NIGHT_END_MINUTES] = minutes }
    }

    suspend fun setDndEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.DND_ENABLED] = enabled }
    }

    suspend fun setDndStartMinutes(minutes: Int) {
        context.settingsDataStore.edit { it[Keys.DND_START_MINUTES] = minutes }
    }

    suspend fun setDndEndMinutes(minutes: Int) {
        context.settingsDataStore.edit { it[Keys.DND_END_MINUTES] = minutes }
    }

    suspend fun setDemoProUnlocked(unlocked: Boolean) {
        context.settingsDataStore.edit { it[Keys.DEMO_PRO_UNLOCKED] = unlocked }
    }

    /** 应用语言："zh" 中文 / "en" 英文。 */
    val appLanguage: Flow<String> =
        context.settingsDataStore.data.map { it[Keys.APP_LANGUAGE] ?: "zh" }

    suspend fun setAppLanguage(language: String) {
        context.settingsDataStore.edit { it[Keys.APP_LANGUAGE] = language }
    }

    /**
     * 守护开关的用户意图（持久化）：开机自启兜底通知、首页"服务被杀"引导卡片
     * 都以它为准——只有用户开过守护，服务却不在运行，才需要引导修复。
     */
    val guardEnabled: Flow<Boolean> =
        context.settingsDataStore.data.map { it[Keys.GUARD_ENABLED] ?: false }

    suspend fun setGuardEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.GUARD_ENABLED] = enabled }
    }
}
