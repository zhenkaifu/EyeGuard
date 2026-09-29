package com.eyeguard.app.feature.home

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.eyeguard.app.core.service.EyeGuardService
import com.eyeguard.app.feature.report.StatsLogic

/**
 * 首页：护眼状态总览 + 服务被杀修复引导 + 今日数据卡片（真实数据）+
 * 最近 7 天柱状图 + 快捷入口。
 *
 * @param onNavigateToSettings 跳转到设置页
 * @param onNavigateToPermissions 跳转到权限引导页
 * @param onNavigateToPaywall 跳转到付费墙页
 * @param onNavigateToWeeklyReport 跳转到每周用眼报告页
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToPermissions: () -> Unit,
    onNavigateToPaywall: () -> Unit,
    onNavigateToDebug: () -> Unit = {},
    onNavigateToWeeklyReport: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val today by viewModel.todayStats.collectAsStateWithLifecycle()
    val recent by viewModel.recentStats.collectAsStateWithLifecycle()
    val isPro by viewModel.isPro.collectAsStateWithLifecycle()
    val guardRunning by viewModel.guardRunning.collectAsStateWithLifecycle()
    val showRepairCard by viewModel.showRepairCard.collectAsStateWithLifecycle()
    val streakDays by viewModel.streakDays.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.home_title)) },
                actions = {
                    TextButton(onClick = onNavigateToSettings) { Text(stringResource(R.string.settings)) }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 护眼状态总览：开关直连前台服务，并把用户意图持久化（供开机自启与被杀检测）
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.guard_title), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(if (guardRunning) R.string.guard_running else R.string.guard_stopped))
                        Switch(
                            checked = guardRunning,
                            onCheckedChange = { enabled ->
                                viewModel.setGuardEnabled(enabled)
                                if (enabled) EyeGuardService.start(context)
                                else EyeGuardService.stop(context)
                            }
                        )
                    }
                }
            }

            // 服务被系统停止的修复引导（开关开着但服务没跑时显示）
            if (showRepairCard) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            stringResource(R.string.repair_card_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            stringResource(R.string.repair_card_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { openBatteryWhitelist(context) }) {
                                Text(stringResource(R.string.repair_battery))
                            }
                            TextButton(onClick = { openAutoStartSettings(context) }) {
                                Text(stringResource(R.string.repair_autostart))
                            }
                            TextButton(onClick = onNavigateToPermissions) {
                                Text(stringResource(R.string.permission_guide))
                            }
                        }
                    }
                }
            }

            // 今日数据卡片（真实数据）
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.today_data), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.today_screen_time, formatDuration(today?.totalScreenTimeMs ?: 0L)))
                    Text(stringResource(R.string.today_distance_count, today?.distanceReminderCount ?: 0))
                    Text(stringResource(R.string.today_dark_count, today?.darkReminderCount ?: 0))
                    Text(stringResource(R.string.today_usage_count, today?.usageReminderCount ?: 0))
                    Text(stringResource(R.string.today_too_close_duration, formatDuration(today?.tooCloseDurationMs ?: 0L)))
                    Text(stringResource(R.string.today_longest_session, formatDuration(today?.longestSessionMs ?: 0L)))
                    Text(stringResource(R.string.today_compliance, today?.let { StatsLogic.compliancePercent(it) } ?: 100))
                    if (streakDays > 0) {
                        Text(
                            stringResource(R.string.streak_days, streakDays),
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                }
            }

            // 最近 7 天柱状图（Pro 功能）
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                onClick = { if (!isPro) onNavigateToPaywall() }
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(if (!isPro) R.string.weekly_chart_pro_lock else R.string.weekly_chart_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    when {
                        !isPro -> Text(
                            stringResource(R.string.weekly_chart_upgrade_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        recent.isEmpty() -> Text(
                            stringResource(R.string.weekly_chart_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        else -> WeeklyBarChart(stats = recent.sortedBy { it.date })
                    }
                }
            }

            // 快捷入口
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.quick_entries), style = MaterialTheme.typography.titleMedium)
                    TextButton(
                        onClick = { if (isPro) onNavigateToWeeklyReport() else onNavigateToPaywall() }
                    ) {
                        Text(stringResource(if (isPro) R.string.weekly_report_entry else R.string.weekly_report_entry_pro))
                    }
                    TextButton(onClick = onNavigateToPermissions) { Text(stringResource(R.string.permission_guide)) }
                    TextButton(onClick = onNavigateToPaywall) { Text(stringResource(R.string.upgrade_pro)) }
                    TextButton(onClick = onNavigateToDebug) { Text(stringResource(R.string.debug_panel)) }
                }
            }
        }
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

/**
 * 跳系统"忽略电池优化"弹窗。
 *
 * 荣耀 / 部分国产 ROM 会静默拦截 ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
 * （startActivity 不抛异常但什么都不弹），因此先 resolveActivity 探测，
 * 逐级回退：系统弹窗 → 电池优化总列表 → 应用详情设置页。
 */
private fun openBatteryWhitelist(context: Context) {
    val pm = context.packageManager
    val candidates = listOf(
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${context.packageName}")),
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null))
    )
    for (intent in candidates) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (intent.resolveActivity(pm) != null &&
            runCatching { context.startActivity(intent) }.isSuccess
        ) return
    }
}

/**
 * 跳国产 ROM 的"自启动管理"页：按厂商逐个尝试已知组件，
 * 全部不可用时退到应用详情设置页让用户手动找。
 */
private fun openAutoStartSettings(context: Context) {
    val candidates = listOf(
        // 小米 MIUI
        ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
        // 华为 EMUI
        ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
        // 荣耀
        ComponentName("com.hihonor.systemmanager", "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
        // OPPO ColorOS
        ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
        ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
        // vivo
        ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"),
        ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")
    )
    for (component in candidates) {
        val intent = Intent().setComponent(component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { context.startActivity(intent) }.isSuccess) return
    }
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }
}
