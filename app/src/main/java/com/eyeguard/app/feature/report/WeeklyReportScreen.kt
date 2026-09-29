package com.eyeguard.app.feature.report

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eyeguard.app.R
import com.eyeguard.app.feature.home.WeeklyBarChart

/**
 * 每周用眼报告（Pro）：本周概览（总量 / 日均 / 环比 / 最佳日）、
 * 7 天柱状图、连续达标 streak 与成就徽章。
 *
 * @param onBack 返回上一页
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeeklyReportScreen(
    onBack: () -> Unit,
    viewModel: WeeklyReportViewModel = hiltViewModel()
) {
    val report by viewModel.report.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.report_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { innerPadding ->
        val data = report
        if (data == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp)
            ) {
                Text(
                    stringResource(R.string.report_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 本周概览
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.report_overview), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.report_total, formatDuration(data.totalScreenTimeMs)))
                    Text(stringResource(R.string.report_avg, formatDuration(data.avgDailyMs)))
                    Text(stringResource(R.string.report_vs_last_week, formatTrend(data.trendPercentVsLastWeek)))
                    Text(stringResource(R.string.report_longest_session, formatDuration(data.longestSessionMs)))
                    Text(stringResource(R.string.report_too_close, formatDuration(data.tooCloseDurationMs)))
                    Text(stringResource(R.string.report_compliance, data.compliancePercent))
                    Text(
                        stringResource(
                            R.string.report_best_day,
                            data.bestDayDate ?: stringResource(R.string.report_no_data)
                        )
                    )
                }
            }

            // 7 天柱状图
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.weekly_chart_title), style = MaterialTheme.typography.titleMedium)
                    WeeklyBarChart(stats = data.thisWeek)
                }
            }

            // 连续达标
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.report_streak, data.streakDays), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.report_qualified_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 成就徽章
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.report_achievements), style = MaterialTheme.typography.titleMedium)
                    data.achievements.forEach { achievement ->
                        AchievementRow(achievement)
                    }
                }
            }
        }
    }
}

@Composable
private fun AchievementRow(achievement: StatsLogic.Achievement) {
    val (titleRes, descRes) = when (achievement.id) {
        "first_rest" -> R.string.ach_first_rest to R.string.ach_first_rest_desc
        "rest_10" -> R.string.ach_rest_10 to R.string.ach_rest_10_desc
        "rest_50" -> R.string.ach_rest_50 to R.string.ach_rest_50_desc
        "streak_3" -> R.string.ach_streak_3 to R.string.ach_streak_3_desc
        else -> R.string.ach_streak_7 to R.string.ach_streak_7_desc
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(titleRes),
                style = MaterialTheme.typography.titleSmall,
                color = if (achievement.unlocked) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                stringResource(descRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            if (achievement.unlocked) "🏅" else stringResource(R.string.ach_locked),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 毫秒转本地化时长文案。 */
@Composable
private fun formatDuration(ms: Long): String {
    val totalMinutes = (ms / 60_000L).coerceAtLeast(0)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) stringResource(R.string.duration_hours_minutes, hours, minutes)
    else stringResource(R.string.duration_minutes, minutes)
}

/** 环比趋势文案：+% / -% / 持平 / 无上周数据。 */
@Composable
private fun formatTrend(trendPercent: Int?): String = when {
    trendPercent == null -> stringResource(R.string.report_no_data)
    trendPercent > 0 -> stringResource(R.string.trend_up, trendPercent)
    trendPercent < 0 -> stringResource(R.string.trend_down, -trendPercent)
    else -> stringResource(R.string.trend_flat)
}
