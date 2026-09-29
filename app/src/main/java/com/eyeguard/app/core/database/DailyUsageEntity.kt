package com.eyeguard.app.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 每日使用统计实体：以日期（yyyy-MM-dd）为主键，记录当日累计亮屏时长与
 * 各类护眼提醒的触发次数。
 */
@Entity(tableName = "daily_usage")
data class DailyUsageEntity(
    /** 日期，格式 yyyy-MM-dd。 */
    @PrimaryKey val date: String,
    /** 当日累计亮屏时长（毫秒）。 */
    val totalScreenTimeMs: Long,
    /** 当日"距离过近"提醒次数。 */
    val distanceReminderCount: Int,
    /** 当日"环境光线太暗"提醒次数。 */
    val darkReminderCount: Int,
    /** 当日"连续用眼休息"提醒次数。 */
    val usageReminderCount: Int,
    /** 当日最长连续用眼时长（毫秒）。 */
    val longestSessionMs: Long = 0L,
    /** 当日距离过近累计时长（毫秒，按采样间隔估算）。 */
    val tooCloseDurationMs: Long = 0L,
    /** 当日用眼提醒后完成休息的次数（休息遵守率分子，分母为 usageReminderCount）。 */
    val restCompletedCount: Int = 0
) {
    companion object {
        /** 空记录便捷构造。 */
        fun empty(date: String) = DailyUsageEntity(date, 0L, 0, 0, 0)
    }
}
