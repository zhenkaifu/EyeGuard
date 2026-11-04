package com.eyeguard.app.feature.home

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eyeguard.app.R
import com.eyeguard.app.core.service.EyeGuardService
import com.eyeguard.app.feature.focus.CultivationPalette
import com.eyeguard.app.feature.focus.CultivationPanel
import com.eyeguard.app.feature.focus.CultivationScene
import com.eyeguard.app.feature.focus.FocusPhase
import com.eyeguard.app.feature.focus.FocusProgression
import com.eyeguard.app.feature.focus.realmNames
import com.eyeguard.app.feature.report.StatsLogic

/** 首页以动态洞府为视觉中心，护眼、专注与统计数据共用青绿仙侠主题。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToPermissions: () -> Unit,
    onNavigateToPaywall: () -> Unit,
    onNavigateToDebug: () -> Unit = {},
    onNavigateToWeeklyReport: () -> Unit = {},
    onNavigateToFocus: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val today by viewModel.todayStats.collectAsStateWithLifecycle()
    val recent by viewModel.recentStats.collectAsStateWithLifecycle()
    val isPro by viewModel.isPro.collectAsStateWithLifecycle()
    val guardRunning by viewModel.guardRunning.collectAsStateWithLifecycle()
    val showRepairCard by viewModel.showRepairCard.collectAsStateWithLifecycle()
    val streakDays by viewModel.streakDays.collectAsStateWithLifecycle()
    val focus by viewModel.focusState.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val realmNames = realmNames()
    val focusLevel = FocusProgression.levelFor(focus.xp)
    val dailyProgress = if (focus.targetMinutes > 0) {
        (focus.todayProgressMs / (focus.targetMinutes * 60_000f)).coerceIn(0f, 1f)
    } else focusLevel.progress
    val focusPrimary = when (focus.phase) {
        FocusPhase.ACTIVE, FocusPhase.RESTING -> formatFocusRemaining(
            if (focus.phase == FocusPhase.ACTIVE) focus.remainingMs else focus.restRemainingMs
        )
        FocusPhase.REST_READY -> stringResource(R.string.focus_home_rest_ready)
        FocusPhase.PLAN_PAUSED -> stringResource(R.string.focus_home_plan_paused)
        FocusPhase.PRESENCE_PAUSED -> stringResource(R.string.focus_status_presence_paused)
        FocusPhase.COMPLETE -> stringResource(R.string.focus_home_complete)
        else -> stringResource(R.string.focus_idle_hint)
    }
    val focusSecondary = when (focus.phase) {
        FocusPhase.ACTIVE -> stringResource(R.string.focus_home_active_title, formatFocusRemaining(focus.remainingMs))
        FocusPhase.RESTING -> stringResource(R.string.focus_resting_title)
        else -> if (focus.hasPlan) stringResource(R.string.focus_home_daily_progress,
            (focus.todayProgressMs / 60_000L).toInt(), focus.targetMinutes)
        else stringResource(R.string.focus_entry_subtitle)
    }

    Scaffold(
        containerColor = CultivationPalette.Paper,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.home_title), fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.focus_headline), style = MaterialTheme.typography.labelSmall,
                            color = CultivationPalette.InkSoft)
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Filled.Settings, stringResource(R.string.settings))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CultivationPalette.Paper,
                    titleContentColor = CultivationPalette.Ink, actionIconContentColor = CultivationPalette.Ink)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CultivationScene(
                realmIndex = focus.realmIndex,
                layer = focus.layer,
                phase = focus.phase,
                reduceMotion = focus.reduceMotion,
                stageTitle = stringResource(R.string.focus_current_stage, realmNames[focus.realmIndex], focus.layer),
                stageSubtitle = stringResource(R.string.focus_xp_clarity_value, focus.xp, focus.clarityPoints),
                primaryStatus = focusPrimary,
                secondaryStatus = focusSecondary,
                progress = dailyProgress,
                actionLabel = stringResource(if (focus.hasPlan) R.string.focus_continue else R.string.focus_entry_action),
                onClick = onNavigateToFocus
            )

            CultivationPanel {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(shape = CircleShape, color = CultivationPalette.JadeLight.copy(alpha = 0.20f)) {
                        Icon(Icons.Filled.HealthAndSafety, contentDescription = null,
                            tint = CultivationPalette.Jade, modifier = Modifier.padding(10.dp).size(25.dp))
                    }
                    Spacer(Modifier.width(13.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(stringResource(R.string.guard_title), style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold)
                        Text(stringResource(if (guardRunning) R.string.guard_running else R.string.guard_stopped),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (guardRunning) CultivationPalette.Jade else CultivationPalette.Cinnabar)
                    }
                    Switch(
                        checked = guardRunning,
                        onCheckedChange = { enabled ->
                            viewModel.setGuardEnabled(enabled)
                            if (enabled) EyeGuardService.start(context) else EyeGuardService.stop(context)
                        },
                        colors = SwitchDefaults.colors(checkedTrackColor = CultivationPalette.Jade,
                            checkedThumbColor = CultivationPalette.Paper)
                    )
                }
            }

            if (showRepairCard) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Text(stringResource(R.string.repair_card_title), style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.repair_card_desc), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TextButton(onClick = { openBatteryWhitelist(context) }) { Text(stringResource(R.string.repair_battery)) }
                            TextButton(onClick = { openAutoStartSettings(context) }) { Text(stringResource(R.string.repair_autostart)) }
                            TextButton(onClick = onNavigateToPermissions) { Text(stringResource(R.string.permission_guide)) }
                        }
                    }
                }
            }

            CultivationPanel {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.today_data), style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        if (streakDays > 0) {
                            Surface(shape = CircleShape, color = CultivationPalette.Gold.copy(alpha = 0.15f)) {
                                Text(stringResource(R.string.streak_days, streakDays),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    color = CultivationPalette.Cinnabar, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    HomeMetric(stringResource(R.string.today_screen_time, formatDuration(today?.totalScreenTimeMs ?: 0L)))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        HomeMetric(stringResource(R.string.today_distance_count, today?.distanceReminderCount ?: 0), Modifier.weight(1f))
                        HomeMetric(stringResource(R.string.today_dark_count, today?.darkReminderCount ?: 0), Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        HomeMetric(stringResource(R.string.today_usage_count, today?.usageReminderCount ?: 0), Modifier.weight(1f))
                        HomeMetric(stringResource(R.string.today_compliance,
                            today?.let { StatsLogic.compliancePercent(it) } ?: 100), Modifier.weight(1f))
                    }
                    HomeMetric(stringResource(R.string.today_too_close_duration,
                        formatDuration(today?.tooCloseDurationMs ?: 0L)))
                    HomeMetric(stringResource(R.string.today_longest_session,
                        formatDuration(today?.longestSessionMs ?: 0L)))
                }
            }

            CultivationPanel {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(if (!isPro) R.string.weekly_chart_pro_lock else R.string.weekly_chart_title),
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    when {
                        !isPro -> {
                            Text(stringResource(R.string.weekly_chart_upgrade_hint), style = MaterialTheme.typography.bodySmall,
                                color = CultivationPalette.InkSoft)
                            OutlinedButton(onClick = onNavigateToPaywall, shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, CultivationPalette.Gold.copy(alpha = 0.45f))) {
                                Text(stringResource(R.string.upgrade_pro))
                            }
                        }
                        recent.isEmpty() -> Text(stringResource(R.string.weekly_chart_empty),
                            style = MaterialTheme.typography.bodySmall, color = CultivationPalette.InkSoft)
                        else -> WeeklyBarChart(stats = recent.sortedBy { it.date })
                    }
                }
            }

            CultivationPanel {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.quick_entries), style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold)
                    TextButton(onClick = { if (isPro) onNavigateToWeeklyReport() else onNavigateToPaywall() },
                        modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(if (isPro) R.string.weekly_report_entry else R.string.weekly_report_entry_pro))
                    }
                    TextButton(onClick = onNavigateToPermissions, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.permission_guide))
                    }
                    TextButton(onClick = onNavigateToDebug, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.debug_panel))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun HomeMetric(text: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(14.dp),
        color = CultivationPalette.JadeLight.copy(alpha = 0.10f)) {
        Text(text, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            style = MaterialTheme.typography.bodySmall, color = CultivationPalette.InkSoft)
    }
}

@Composable
private fun formatDuration(ms: Long): String {
    val totalMinutes = (ms / 60_000L).coerceAtLeast(0)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) stringResource(R.string.duration_hours_minutes, hours, minutes)
    else stringResource(R.string.duration_minutes, minutes)
}

private fun formatFocusRemaining(milliseconds: Long): String {
    val seconds = (milliseconds / 1_000L).coerceAtLeast(0L)
    return String.format("%02d:%02d", seconds / 60, seconds % 60)
}

private fun openBatteryWhitelist(context: Context) {
    val pm = context.packageManager
    val candidates = listOf(
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")),
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
    )
    for (intent in candidates) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (intent.resolveActivity(pm) != null && runCatching { context.startActivity(intent) }.isSuccess) return
    }
}

private fun openAutoStartSettings(context: Context) {
    val candidates = listOf(
        ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
        ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
        ComponentName("com.hihonor.systemmanager", "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
        ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
        ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
        ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")
    )
    for (component in candidates) {
        val intent = Intent().setComponent(component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { context.startActivity(intent) }.isSuccess) return
    }
    runCatching {
        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }
}
