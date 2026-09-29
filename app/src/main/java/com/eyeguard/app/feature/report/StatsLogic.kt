package com.eyeguard.app.feature.report

import com.eyeguard.app.core.database.DailyUsageEntity
import java.time.LocalDate

/**
 * 用眼统计的派生指标计算：达标日判定、连续达标天数（streak）、休息遵守率。
 * 首页与周报共用同一套口径，避免两处数字不一致。
 */
object StatsLogic {

    /** 达标日遵守率门槛（百分比）。 */
    const val QUALIFIED_COMPLIANCE_PERCENT = 80

    /**
     * 达标日判定：当天有用眼记录，且休息遵守率 ≥ 80%。
     * 当天没有触发过用眼提醒（全靠自觉）视为达标。
     */
    fun isQualifiedDay(entity: DailyUsageEntity): Boolean {
        if (entity.totalScreenTimeMs <= 0) return false
        if (entity.usageReminderCount <= 0) return true
        return compliancePercent(entity) >= QUALIFIED_COMPLIANCE_PERCENT
    }

    /** 单日休息遵守率（%）：提醒后完成休息次数 / 用眼提醒次数；无提醒返回 100。 */
    fun compliancePercent(entity: DailyUsageEntity): Int {
        if (entity.usageReminderCount <= 0) return 100
        return (entity.restCompletedCount * 100 / entity.usageReminderCount)
            .coerceIn(0, 100)
    }

    /**
     * 连续达标天数：今天尚未达标时不打断 streak（当天还在进行中），
     * 从昨天往前数连续达标天数；今天已达标则 +1。
     *
     * @param stats 任意顺序的每日统计
     * @param today 今天日期（yyyy-MM-dd）
     */
    fun computeStreak(stats: List<DailyUsageEntity>, today: String = LocalDate.now().toString()): Int {
        val sorted = stats.sortedByDescending { it.date }
        var streak = 0
        for (entity in sorted) {
            if (entity.date == today && !isQualifiedDay(entity)) continue
            if (entity.date > today) continue
            if (isQualifiedDay(entity)) streak++ else break
        }
        return streak
    }

    /**
     * 成就徽章定义与解锁判定。
     * @param totalRestCompleted 全历史累计完成休息次数
     * @param streak 当前连续达标天数
     */
    fun achievements(totalRestCompleted: Long, streak: Int): List<Achievement> = listOf(
        Achievement("first_rest", totalRestCompleted >= 1),
        Achievement("rest_10", totalRestCompleted >= 10),
        Achievement("rest_50", totalRestCompleted >= 50),
        Achievement("streak_3", streak >= 3),
        Achievement("streak_7", streak >= 7),
    )

    data class Achievement(val id: String, val unlocked: Boolean)
}
