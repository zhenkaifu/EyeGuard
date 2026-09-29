package com.eyeguard.app.feature.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eyeguard.app.core.billing.BillingRepository
import com.eyeguard.app.core.database.DailyUsageEntity
import com.eyeguard.app.core.database.DailyUsageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * 周报 ViewModel：取最近 14 天数据切成"本周 / 上周"两段，
 * 计算总量、日均、环比、最佳护眼日、遵守率、连续达标与成就。
 */
@HiltViewModel
class WeeklyReportViewModel @Inject constructor(
    dailyUsageRepository: DailyUsageRepository,
    billingRepository: BillingRepository
) : ViewModel() {

    /** 是否 Pro（周报为 Pro 功能，入口已拦截，这里双保险）。 */
    val isPro: StateFlow<Boolean> = billingRepository.isPro
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val report: StateFlow<WeeklyReport?> = combine(
        dailyUsageRepository.recentStats(days = 14),
        dailyUsageRepository.totalRestCompleted
    ) { stats, totalRest ->
        buildReport(stats, totalRest)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private fun buildReport(stats: List<DailyUsageEntity>, totalRest: Long): WeeklyReport? {
        if (stats.isEmpty()) return null
        val today = LocalDate.now()
        val weekStart = today.minusDays(6)
        val prevWeekStart = today.minusDays(13)

        val thisWeek = stats.filter {
            val d = LocalDate.parse(it.date)
            !d.isBefore(weekStart) && !d.isAfter(today)
        }
        val prevWeek = stats.filter {
            val d = LocalDate.parse(it.date)
            !d.isBefore(prevWeekStart) && d.isBefore(weekStart)
        }
        if (thisWeek.isEmpty()) return null

        val totalMs = thisWeek.sumOf { it.totalScreenTimeMs }
        val prevTotalMs = prevWeek.sumOf { it.totalScreenTimeMs }
        val trendPercent: Int? = if (prevTotalMs > 0) {
            ((totalMs - prevTotalMs) * 100 / prevTotalMs).toInt()
        } else null

        val usageReminders = thisWeek.sumOf { it.usageReminderCount }
        val restCompleted = thisWeek.sumOf { it.restCompletedCount }
        val compliance = if (usageReminders > 0) {
            (restCompleted * 100 / usageReminders).coerceIn(0, 100)
        } else 100

        // 最佳护眼日：有用眼记录的日期里遵守率最高、提醒最少的一天
        val bestDay = thisWeek
            .filter { it.totalScreenTimeMs > 0 }
            .maxWithOrNull(
                compareBy<DailyUsageEntity> { StatsLogic.compliancePercent(it) }
                    .thenBy { -(it.distanceReminderCount + it.darkReminderCount + it.usageReminderCount) }
            )

        val streak = StatsLogic.computeStreak(stats)

        return WeeklyReport(
            thisWeek = thisWeek.sortedBy { it.date },
            totalScreenTimeMs = totalMs,
            avgDailyMs = totalMs / thisWeek.size,
            trendPercentVsLastWeek = trendPercent,
            bestDayDate = bestDay?.date,
            longestSessionMs = thisWeek.maxOf { it.longestSessionMs },
            tooCloseDurationMs = thisWeek.sumOf { it.tooCloseDurationMs },
            compliancePercent = compliance,
            streakDays = streak,
            achievements = StatsLogic.achievements(totalRest, streak)
        )
    }
}

/** 周报页面数据。 */
data class WeeklyReport(
    val thisWeek: List<DailyUsageEntity>,
    val totalScreenTimeMs: Long,
    val avgDailyMs: Long,
    /** 相对上周变化百分比；上周无数据时为 null。 */
    val trendPercentVsLastWeek: Int?,
    val bestDayDate: String?,
    val longestSessionMs: Long,
    val tooCloseDurationMs: Long,
    val compliancePercent: Int,
    val streakDays: Int,
    val achievements: List<StatsLogic.Achievement>
)
