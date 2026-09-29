package com.eyeguard.app.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * 每日使用统计表的 Room DAO，提供按日读写、原子自增与近期统计查询。
 */
@Dao
interface DailyUsageDao {

    /** 插入或按日期覆盖一条统计记录。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DailyUsageEntity)

    /** 查询某一天的统计记录。 */
    @Query("SELECT * FROM daily_usage WHERE date = :date")
    suspend fun getByDate(date: String): DailyUsageEntity?

    /** 订阅某一天的统计记录。 */
    @Query("SELECT * FROM daily_usage WHERE date = :date")
    fun observeByDate(date: String): Flow<DailyUsageEntity?>

    /** 查询最近 [limit] 天的统计记录，按日期升序。 */
    @Query("SELECT * FROM daily_usage ORDER BY date DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<DailyUsageEntity>>

    /** 为某日累计亮屏时长追加 [deltaMs] 毫秒（无记录时影响 0 行，调用方需先 upsert）。 */
    @Query("UPDATE daily_usage SET totalScreenTimeMs = totalScreenTimeMs + :deltaMs WHERE date = :date")
    suspend fun addScreenTime(date: String, deltaMs: Long)

    /** 某日距离提醒次数 +1。 */
    @Query("UPDATE daily_usage SET distanceReminderCount = distanceReminderCount + 1 WHERE date = :date")
    suspend fun incrementDistanceReminders(date: String)

    /** 某日暗光提醒次数 +1。 */
    @Query("UPDATE daily_usage SET darkReminderCount = darkReminderCount + 1 WHERE date = :date")
    suspend fun incrementDarkReminders(date: String)

    /** 某日用眼休息提醒次数 +1。 */
    @Query("UPDATE daily_usage SET usageReminderCount = usageReminderCount + 1 WHERE date = :date")
    suspend fun incrementUsageReminders(date: String)

    /** 某日距离过近累计时长追加 [deltaMs] 毫秒（按采样间隔估算）。 */
    @Query("UPDATE daily_usage SET tooCloseDurationMs = tooCloseDurationMs + :deltaMs WHERE date = :date")
    suspend fun addTooCloseDuration(date: String, deltaMs: Long)

    /** 某日最长连续用眼时长：仅当 [sessionMs] 更大时更新。 */
    @Query("UPDATE daily_usage SET longestSessionMs = MAX(longestSessionMs, :sessionMs) WHERE date = :date")
    suspend fun updateLongestSession(date: String, sessionMs: Long)

    /** 某日"提醒后完成休息"次数 +1。 */
    @Query("UPDATE daily_usage SET restCompletedCount = restCompletedCount + 1 WHERE date = :date")
    suspend fun incrementRestCompleted(date: String)

    /** 全历史累计完成休息次数（成就用）。 */
    @Query("SELECT IFNULL(SUM(restCompletedCount), 0) FROM daily_usage")
    fun observeTotalRestCompleted(): Flow<Long>
}
