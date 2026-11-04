package com.eyeguard.app.feature.focus

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eyeguard.app.R
import com.eyeguard.app.feature.focus.data.FocusTaskEntity
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusScreen(onBack: () -> Unit, viewModel: FocusViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val completion by viewModel.completion.collectAsStateWithLifecycle()
    val recentSessions by viewModel.recentSessions.collectAsStateWithLifecycle()
    val todayTasks by viewModel.todayTasks.collectAsStateWithLifecycle()
    val taskReward by viewModel.taskReward.collectAsStateWithLifecycle()
    val availableApps by viewModel.availableApps.collectAsStateWithLifecycle()
    val names = realmNames()
    val level = FocusProgression.levelFor(state.xp)
    val context = LocalContext.current
    var targetMinutes by rememberSaveable { mutableIntStateOf(180) }
    var segmentMinutes by rememberSaveable { mutableIntStateOf(25) }
    var restMinutes by rememberSaveable { mutableIntStateOf(5) }
    var goal by rememberSaveable { mutableStateOf("") }
    var permissionRefresh by rememberSaveable { mutableIntStateOf(0) }
    var pendingStart by rememberSaveable { mutableStateOf(false) }
    var showAdjust by rememberSaveable { mutableStateOf(false) }
    var showApps by rememberSaveable { mutableStateOf(false) }
    var showAbandon by rememberSaveable { mutableStateOf(false) }
    var showEndPlan by rememberSaveable { mutableStateOf(false) }
    var showTasks by rememberSaveable { mutableStateOf(false) }
    var showMusicCredits by rememberSaveable { mutableStateOf(false) }
    var planningStart by rememberSaveable { mutableStateOf(false) }
    @Suppress("UNUSED_EXPRESSION") permissionRefresh
    val cameraGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    val notificationsGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    val presenceRequested = state.strictModePreferred || state.presencePreferred

    fun startNow(presence: Boolean = presenceRequested) {
        pendingStart = false
        viewModel.startPlan(targetMinutes, segmentMinutes, restMinutes, goal,
            state.strictModePreferred, state.strictModePreferred || presence)
    }

    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permissionRefresh++
        if (pendingStart) startNow()
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionRefresh++
        if (pendingStart) {
            when {
                granted -> if (!notificationsGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else startNow()
                state.strictModePreferred -> pendingStart = false
                else -> {
                    viewModel.setPresencePreferred(false)
                    startNow(presence = false)
                }
            }
        }
    }

    fun attemptStart() {
        if (state.strictModePreferred && !viewModel.hasUsageAccess()) {
            showApps = true
            viewModel.loadAvailableApps()
            return
        }
        pendingStart = true
        when {
            presenceRequested && !cameraGranted -> cameraLauncher.launch(Manifest.permission.CAMERA)
            !notificationsGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            else -> startNow()
        }
    }

    LaunchedEffect(showApps) {
        if (showApps) viewModel.loadAvailableApps()
    }
    val scrollState = rememberScrollState()
    LaunchedEffect(state.phase) {
        if (state.phase != FocusPhase.IDLE) scrollState.animateScrollTo(0)
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> viewModel.setMusicScreenVisible(true)
                Lifecycle.Event.ON_STOP -> viewModel.setMusicScreenVisible(false)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        viewModel.setMusicScreenVisible(true)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.setMusicScreenVisible(false)
        }
    }

    Scaffold(
        containerColor = CultivationPalette.Paper,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.focus_title), fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.focus_subtitle), style = MaterialTheme.typography.labelSmall,
                            color = CultivationPalette.InkSoft)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
                actions = {
                    FocusMusicToggle(
                        enabled = state.musicEnabled,
                        onToggle = { viewModel.setMusicEnabled(!state.musicEnabled) }
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = CultivationPalette.Paper,
                    navigationIconContentColor = CultivationPalette.Ink,
                    titleContentColor = CultivationPalette.Ink,
                    actionIconContentColor = CultivationPalette.Ink
                )
            )
        },
        bottomBar = {
            if (state.hasActivePlan) {
                FocusBottomBar(
                    state = state,
                    onPause = viewModel::pausePlan,
                    onResume = viewModel::resumePlan,
                    onStartNext = viewModel::startNextSegment,
                    onSkipRest = viewModel::skipRest,
                    onComplete = viewModel::endPlan,
                    onEnd = {
                        if (state.phase in listOf(FocusPhase.ACTIVE, FocusPhase.PRESENCE_PAUSED)) {
                            showAbandon = true
                        } else showEndPlan = true
                    }
                )
            }
        }
    ) { innerPadding ->
        Column(
            Modifier.fillMaxSize().padding(innerPadding).verticalScroll(scrollState).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CultivationHero(
                state = state,
                level = level,
                realmName = names[level.realmIndex],
                usageAccessAvailable = !state.activeStrictMode || viewModel.hasUsageAccess()
            )
            OutlinedButton(
                onClick = { planningStart = false; showTasks = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = CultivationPalette.Ink),
                border = BorderStroke(1.dp, CultivationPalette.Gold.copy(alpha = 0.40f))
            ) {
                Text(stringResource(R.string.focus_tasks_entry,
                    todayTasks.count { it.status == FocusTaskEntity.ACTIVE }))
            }
            if (state.timeAdjusted) {
                Text(stringResource(R.string.focus_time_adjusted), style = MaterialTheme.typography.bodySmall,
                    color = CultivationPalette.Cinnabar, modifier = Modifier.padding(horizontal = 8.dp))
            }

            Crossfade(targetState = state.phase, label = "cultivationPhase") { phase ->
                when (phase) {
                    FocusPhase.IDLE -> CultivationPanel {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
                            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text(stringResource(R.string.focus_daily_plan_title),
                                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Text(stringResource(R.string.focus_daily_plan_subtitle),
                                    color = CultivationPalette.InkSoft, style = MaterialTheme.typography.bodyMedium)
                            }
                            FocusMusicCreditsButton(onClick = { showMusicCredits = true })
                            OutlinedTextField(
                                value = goal,
                                onValueChange = { goal = it.take(40) },
                                label = { Text(stringResource(R.string.focus_goal_optional)) },
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            PlanSlider(
                                title = stringResource(R.string.focus_plan_target, targetMinutes),
                                value = targetMinutes,
                                range = 25f..480f,
                                onChange = { targetMinutes = (it / 5).roundToInt() * 5 }
                            )
                            PlanSlider(
                                title = stringResource(R.string.focus_plan_segment, segmentMinutes),
                                value = segmentMinutes,
                                range = 25f..120f,
                                onChange = { segmentMinutes = (it / 5).roundToInt() * 5 }
                            )
                            PlanSlider(
                                title = stringResource(R.string.focus_plan_rest, restMinutes),
                                value = restMinutes,
                                range = 1f..30f,
                                onChange = { restMinutes = it.roundToInt().coerceAtLeast(1) }
                            )
                            val preview = FocusPlanLogic.segmentsFor(targetMinutes, segmentMinutes)
                            Surface(shape = RoundedCornerShape(14.dp),
                                color = CultivationPalette.JadeLight.copy(alpha = 0.16f)) {
                                Text(
                                    stringResource(R.string.focus_plan_preview, preview.size, preview.joinToString(" / ")),
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = CultivationPalette.Jade,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            SettingSwitchRow(
                                title = stringResource(R.string.focus_strict_title),
                                description = stringResource(R.string.focus_strict_description),
                                checked = state.strictModePreferred,
                                onChecked = {
                                    viewModel.setStrictModePreferred(it)
                                    if (it) {
                                        showApps = true
                                        viewModel.loadAvailableApps()
                                    }
                                }
                            )
                            SettingSwitchRow(
                                title = stringResource(R.string.focus_presence_title),
                                description = stringResource(
                                    if (state.strictModePreferred) R.string.focus_presence_strict_description
                                    else R.string.focus_presence_description
                                ),
                                checked = presenceRequested,
                                enabled = !state.strictModePreferred,
                                onChecked = viewModel::setPresencePreferred
                            )
                            if (state.strictModePreferred) {
                                OutlinedButton(
                                    onClick = { showApps = true; viewModel.loadAvailableApps() },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    border = BorderStroke(1.dp, CultivationPalette.Gold.copy(alpha = 0.55f))
                                ) {
                                    Text(stringResource(R.string.focus_allowed_apps, state.allowedPackages.size))
                                }
                                if (!viewModel.hasUsageAccess()) {
                                    Text(stringResource(R.string.focus_usage_permission_hint),
                                        color = CultivationPalette.Cinnabar, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            SettingSwitchRow(
                                title = stringResource(R.string.focus_reduce_motion),
                                description = stringResource(R.string.focus_reduce_motion_description),
                                checked = state.reduceMotion,
                                onChecked = viewModel::setReduceMotion
                            )
                            CultivationPrimaryButton(
                                onClick = { planningStart = true; showTasks = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.focus_start_daily_plan), fontWeight = FontWeight.Bold)
                                }
                            }
                            Text(stringResource(R.string.focus_privacy_note), style = MaterialTheme.typography.bodySmall,
                                color = CultivationPalette.InkSoft)
                            if (!notificationsGranted) {
                                Text(stringResource(R.string.focus_notification_permission_hint),
                                    style = MaterialTheme.typography.bodySmall, color = CultivationPalette.Cinnabar)
                            }
                        }
                    }

                    FocusPhase.ACTIVE -> Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        DailyProgressCard(state)
                        CultivationPanel {
                            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                                Text(stringResource(R.string.focus_segment_progress, state.currentSegmentNumber,
                                    state.currentSegmentCount), style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold)
                                if (state.planGoal.isNotBlank()) {
                                    Text(stringResource(R.string.focus_current_goal, state.planGoal))
                                }
                                Text(stringResource(if (state.activePresenceEnabled) R.string.focus_presence_monitoring
                                else R.string.focus_presence_exempt), style = MaterialTheme.typography.bodySmall,
                                    color = CultivationPalette.InkSoft)
                                OutlinedButton(onClick = { showAdjust = true }, modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(15.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CultivationPalette.Ink)) {
                                    Text(stringResource(R.string.focus_adjust_segment))
                                }
                            }
                        }
                    }

                    FocusPhase.RESTING -> Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        DailyProgressCard(state)
                        RestGuidanceCard(state)
                    }

                    FocusPhase.REST_READY -> Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        DailyProgressCard(state)
                        StatusCard(stringResource(R.string.focus_rest_ready_title),
                            stringResource(R.string.focus_rest_ready_body, state.currentSegmentNumber,
                                state.currentSegmentCount))
                    }

                    FocusPhase.PLAN_PAUSED -> StatusCard(stringResource(R.string.focus_plan_paused_title),
                        stringResource(R.string.focus_plan_paused_body))

                    FocusPhase.PRESENCE_PAUSED -> {
                        val usageLost = state.activeStrictMode && !viewModel.hasUsageAccess()
                        StatusCard(stringResource(if (usageLost) R.string.focus_usage_access_lost_title
                        else R.string.focus_presence_paused_title),
                            stringResource(if (usageLost) R.string.focus_usage_access_lost_body
                            else R.string.focus_presence_paused_body))
                    }

                    FocusPhase.COMPLETE -> StatusCard(stringResource(R.string.focus_daily_complete_title),
                        stringResource(R.string.focus_daily_complete_body, state.planTargetMinutes))
                }
            }

            CultivationPanel {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.focus_current_stage, names[level.realmIndex], level.layer),
                            fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.focus_xp_clarity_value, state.xp, state.clarityPoints),
                            color = CultivationPalette.Jade, style = MaterialTheme.typography.labelMedium)
                    }
                    LinearProgressIndicator(
                        progress = { level.progress },
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                        color = CultivationPalette.Gold,
                        trackColor = CultivationPalette.JadeLight.copy(alpha = 0.22f)
                    )
                    Text(
                        if (level.nextThreshold == null) stringResource(R.string.focus_max_realm)
                        else stringResource(R.string.focus_next_stage, level.xpToNext,
                            if (level.layer == 10) names[level.realmIndex + 1] else names[level.realmIndex],
                            if (level.layer == 10) 1 else level.layer + 1),
                        style = MaterialTheme.typography.bodySmall, color = CultivationPalette.InkSoft
                    )
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FocusStatCard(stringResource(R.string.focus_minutes_value, state.todayMinutes),
                    stringResource(R.string.focus_today), Modifier.weight(1f))
                FocusStatCard(stringResource(R.string.focus_days_value, state.streakDays),
                    stringResource(R.string.focus_streak), Modifier.weight(1f))
                FocusStatCard(stringResource(R.string.focus_sessions_value, state.completedSessions),
                    stringResource(R.string.focus_completed), Modifier.weight(1f))
            }

            CultivationPanel {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(stringResource(R.string.focus_history_title), style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold)
                    if (recentSessions.isEmpty()) {
                        Text(stringResource(R.string.focus_history_empty), color = CultivationPalette.InkSoft)
                    } else recentSessions.take(5).forEach { FocusHistoryRow(it) }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.focus_realm_path), style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold, color = CultivationPalette.Ink)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    names.forEachIndexed { index, name ->
                        val selected = index == level.realmIndex
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (selected) CultivationPalette.Ink else CultivationPalette.Panel,
                                contentColor = if (selected) CultivationPalette.GoldLight else CultivationPalette.Ink
                            ),
                            border = BorderStroke(1.dp, if (selected) CultivationPalette.Gold
                            else CultivationPalette.Gold.copy(alpha = 0.20f))
                        ) {
                            Column(Modifier.padding(horizontal = 18.dp, vertical = 15.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(name, fontWeight = FontWeight.Bold)
                                Text(stringResource(R.string.focus_ten_layers), style = MaterialTheme.typography.bodySmall)
                                Text(stringResource(R.string.focus_realm_xp, FocusProgression.realmStartXp(index)),
                                    style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (showTasks) {
        FocusTaskDialog(
            tasks = todayTasks,
            startingPlan = planningStart,
            onAdd = viewModel::addTask,
            onStatus = viewModel::changeTaskStatus,
            rewardFeedback = taskReward,
            onRewardDismiss = viewModel::dismissTaskReward,
            onDismiss = { showTasks = false; planningStart = false; viewModel.dismissTaskReward() },
            onConfirm = {
                showTasks = false
                if (planningStart) {
                    planningStart = false
                    attemptStart()
                }
            },
            onSkip = {
                showTasks = false
                planningStart = false
                attemptStart()
            }
        )
    }

    if (showMusicCredits) {
        FocusMusicCreditsDialog(onDismiss = { showMusicCredits = false })
    }

    completion?.let { result ->
        BreakthroughCeremony(result, names, state.reduceMotion, viewModel::dismissCompletion)
    }

    if (showAdjust) {
        val completedMinutes = (state.planCompletedMs / 60_000L).toInt()
        val remainingTotal = state.planTargetMinutes - completedMinutes
        val options = (state.currentSegmentMinimum..remainingTotal).filter {
            val future = remainingTotal - it
            future == 0 || future >= 25
        }
        var selectedIndex by androidx.compose.runtime.remember(showAdjust, state.currentSegmentIndex) {
            mutableIntStateOf(options.indexOf(state.activeDurationMinutes).coerceAtLeast(0))
        }
        AlertDialog(
            onDismissRequest = { showAdjust = false },
            containerColor = CultivationPalette.Panel,
            title = { Text(stringResource(R.string.focus_adjust_segment)) },
            text = {
                Column {
                    Text(stringResource(R.string.focus_adjust_segment_hint, state.currentSegmentMinimum))
                    if (options.isNotEmpty()) {
                        Text(stringResource(R.string.focus_plan_segment, options[selectedIndex]),
                            style = MaterialTheme.typography.titleLarge)
                        Slider(value = selectedIndex.toFloat(),
                            onValueChange = { selectedIndex = it.roundToInt().coerceIn(options.indices) },
                            valueRange = 0f..(options.lastIndex.coerceAtLeast(0)).toFloat(),
                            steps = (options.size - 2).coerceAtLeast(0))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    options.getOrNull(selectedIndex)?.let(viewModel::adjustCurrentSegment)
                    showAdjust = false
                }, enabled = options.isNotEmpty()) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = { TextButton(onClick = { showAdjust = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    if (showApps) {
        AlertDialog(
            onDismissRequest = { showApps = false },
            containerColor = CultivationPalette.Panel,
            title = { Text(stringResource(R.string.focus_allowed_apps_title)) },
            text = {
                Column(Modifier.height(440.dp)) {
                    Text(stringResource(R.string.focus_allowed_apps_description),
                        style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { runCatching { context.startActivity(viewModel.usageSettingsIntent()) } }) {
                        Text(stringResource(if (viewModel.hasUsageAccess()) R.string.focus_usage_permission_granted
                        else R.string.focus_open_usage_settings))
                    }
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(availableApps, key = { it.packageName }) { app ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = app.packageName in state.allowedPackages,
                                    onCheckedChange = { viewModel.toggleAllowedPackage(app.packageName) })
                                Text(app.label, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showApps = false }) { Text(stringResource(R.string.done)) } }
        )
    }

    if (showAbandon) {
        ConfirmDialog(stringResource(R.string.focus_abandon_title), stringResource(R.string.focus_abandon_body),
            stringResource(R.string.focus_abandon_confirm),
            onConfirm = { showAbandon = false; viewModel.abandonSession() }, onDismiss = { showAbandon = false })
    }
    if (showEndPlan) {
        ConfirmDialog(stringResource(R.string.focus_end_daily_plan), stringResource(R.string.focus_end_daily_plan_body),
            stringResource(R.string.focus_abandon_confirm),
            onConfirm = { showEndPlan = false; viewModel.endPlan() }, onDismiss = { showEndPlan = false })
    }
}

@Composable
private fun FocusBottomBar(
    state: FocusUiState,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStartNext: () -> Unit,
    onSkipRest: () -> Unit,
    onComplete: () -> Unit,
    onEnd: () -> Unit
) {
    val primaryLabel = when (state.phase) {
        FocusPhase.ACTIVE, FocusPhase.RESTING -> stringResource(R.string.focus_plan_pause)
        FocusPhase.PLAN_PAUSED -> stringResource(R.string.focus_resume_plan)
        FocusPhase.REST_READY -> stringResource(R.string.focus_start_next_segment)
        FocusPhase.COMPLETE -> stringResource(R.string.focus_new_daily_plan)
        else -> stringResource(R.string.focus_presence_waiting)
    }
    val primaryAction = when (state.phase) {
        FocusPhase.ACTIVE, FocusPhase.RESTING -> onPause
        FocusPhase.PLAN_PAUSED -> onResume
        FocusPhase.REST_READY -> onStartNext
        FocusPhase.COMPLETE -> onComplete
        else -> ({})
    }
    val secondaryLabel = when (state.phase) {
        FocusPhase.RESTING -> stringResource(R.string.focus_skip_rest)
        FocusPhase.COMPLETE -> null
        FocusPhase.ACTIVE, FocusPhase.PRESENCE_PAUSED -> stringResource(R.string.focus_end_early)
        else -> stringResource(R.string.focus_end_daily_plan)
    }
    Surface(
        color = CultivationPalette.Panel,
        contentColor = CultivationPalette.Ink,
        shadowElevation = 10.dp,
        border = BorderStroke(1.dp, CultivationPalette.Gold.copy(alpha = 0.24f))
    ) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CultivationPrimaryButton(
                onClick = primaryAction,
                enabled = state.phase != FocusPhase.PRESENCE_PAUSED,
                modifier = Modifier.weight(1f)
            ) { Text(primaryLabel, fontWeight = FontWeight.Bold) }
            secondaryLabel?.let { label ->
                OutlinedButton(
                    onClick = if (state.phase == FocusPhase.RESTING) onSkipRest else onEnd,
                    modifier = Modifier.weight(0.82f).height(52.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = if (state.phase == FocusPhase.RESTING) CultivationPalette.Jade
                        else CultivationPalette.Cinnabar
                    )
                ) { Text(label) }
            }
        }
    }
}

@Composable
private fun RestGuidanceCard(state: FocusUiState) {
    val suggestedSeconds = FocusPlanLogic.recommendedRestSeconds(state.lastCompletedSegmentMinutes)
    CultivationPanel {
        Column(
            Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(stringResource(R.string.focus_resting_title), style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold)
            Text(formatFocusTime(state.restRemainingMs), style = MaterialTheme.typography.displaySmall,
                color = CultivationPalette.Jade, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.focus_resting_body, suggestedSeconds),
                color = CultivationPalette.InkSoft, style = MaterialTheme.typography.bodyMedium)
            Surface(shape = RoundedCornerShape(16.dp), color = CultivationPalette.JadeLight.copy(alpha = 0.14f)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text(stringResource(R.string.focus_rest_step_one), fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.focus_rest_step_two), fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.focus_rest_step_three), fontWeight = FontWeight.SemiBold)
                }
            }
            Text(stringResource(R.string.focus_rest_reward_note), style = MaterialTheme.typography.bodySmall,
                color = CultivationPalette.Jade)
        }
    }
}

@Composable
private fun PlanSlider(title: String, value: Int, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Slider(value = value.toFloat(), onValueChange = onChange, valueRange = range,
            colors = SliderDefaults.colors(thumbColor = CultivationPalette.Jade,
                activeTrackColor = CultivationPalette.Jade,
                inactiveTrackColor = CultivationPalette.JadeLight.copy(alpha = 0.25f)))
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean = true,
    onChecked: (Boolean) -> Unit
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(description, style = MaterialTheme.typography.bodySmall, color = CultivationPalette.InkSoft)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChecked, enabled = enabled,
            colors = SwitchDefaults.colors(checkedTrackColor = CultivationPalette.Jade,
                checkedThumbColor = CultivationPalette.Paper))
    }
}

@Composable
private fun DailyProgressCard(state: FocusUiState) {
    val progress = if (state.planTargetMinutes > 0) {
        (state.todayProgressMs / (state.planTargetMinutes * 60_000f)).coerceIn(0f, 1f)
    } else 0f
    CultivationPanel {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text(stringResource(R.string.focus_daily_progress,
                (state.todayProgressMs / 60_000L).toInt(), state.planTargetMinutes),
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(8.dp),
                color = CultivationPalette.Jade, trackColor = CultivationPalette.JadeLight.copy(alpha = 0.22f))
        }
    }
}

@Composable
private fun StatusCard(title: String, body: String) {
    CultivationPanel {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(body, color = CultivationPalette.InkSoft, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ConfirmDialog(title: String, body: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CultivationPalette.Panel,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm, color = CultivationPalette.Cinnabar) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}
