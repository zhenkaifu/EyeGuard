package com.eyeguard.app.core.database

import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/**
 * 每日使用统计仓库：封装"先确保当日记录存在再原子自增"的写入模式，
 * 并为 UI 提供今日与最近 7 天数据的订阅入口。
 */
@Singleton
class DailyUsageRepository @Inject constructor(
    private val dailyUsageDao: DailyUsageDao
) {

    private fun today(): String = LocalDate.now().toString()

    /** 今日统计（无记录时为空记录，不产生写库）。 */
    val todayStats: Flow<DailyUsageEntity?> = dailyUsageDao.observeByDate(today())

    /** 最近 [days] 天统计，按日期升序，供柱状图使用。 */
    fun recentStats(days: Int = 7): Flow<List<DailyUsageEntity>> =
        dailyUsageDao.observeRecent(days)

    /** 为今日追加亮屏时长。 */
    suspend fun addScreenTimeToday(deltaMs: Long) {
        ensureTodayExists()
        dailyUsageDao.addScreenTime(today(), deltaMs)
    }

    suspend fun incrementDistanceRemindersToday() {
        ensureTodayExists()
        dailyUsageDao.incrementDistanceReminders(today())
    }

    suspend fun incrementDarkRemindersToday() {
        ensureTodayExists()
        dailyUsageDao.incrementDarkReminders(today())
    }

    suspend fun incrementUsageRemindersToday() {
        ensureTodayExists()
        dailyUsageDao.incrementUsageReminders(today())
    }

    /** 为今日追加"距离过近"累计时长。 */
    suspend fun addTooCloseDurationToday(deltaMs: Long) {
        if (deltaMs <= 0) return
        ensureTodayExists()
        dailyUsageDao.addTooCloseDuration(today(), deltaMs)
    }

    /** 更新今日最长连续用眼时长（仅当更长时生效）。 */
    suspend fun updateLongestSessionToday(sessionMs: Long) {
        if (sessionMs <= 0) return
        ensureTodayExists()
        dailyUsageDao.updateLongestSession(today(), sessionMs)
    }

    /** 今日"提醒后完成休息"次数 +1（遵守率分子）。 */
    suspend fun incrementRestCompletedToday() {
        ensureTodayExists()
        dailyUsageDao.incrementRestCompleted(today())
    }

    /** 全历史累计完成休息次数（成就用）。 */
    val totalRestCompleted: Flow<Long> = dailyUsageDao.observeTotalRestCompleted()

    /** 保证当日记录存在（自增 UPDATE 依赖已有行）。 */
    private suspend fun ensureTodayExists() {
        val date = today()
        if (dailyUsageDao.getByDate(date) == null) {
            dailyUsageDao.upsert(DailyUsageEntity.empty(date))
        }
    }
}
