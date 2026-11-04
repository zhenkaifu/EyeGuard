package com.eyeguard.app.feature.focus

import android.app.Activity
import android.app.Application
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.ServiceCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.eyeguard.app.R
import com.eyeguard.app.core.notification.Notifier
import com.eyeguard.app.feature.distance.DistanceSampler
import com.eyeguard.app.feature.focus.data.FocusSessionEntity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** 今日计划计时、真实在席采样、严格应用限制与休息结算。 */
@AndroidEntryPoint
class FocusService : Service(), LifecycleOwner {
    @Inject lateinit var repository: FocusRepository
    @Inject lateinit var notifier: Notifier
    @Inject lateinit var distanceSampler: DistanceSampler
    @Inject lateinit var learningAppManager: LearningAppManager
    @Inject lateinit var musicManager: FocusMusicManager

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loopStarted = false
    private var presenceStarted = false
    private var startedActivities = 0
    private var disallowedSince = 0L
    private var noFaceSince = 0L
    private var faceSeenSince = 0L

    override val lifecycle: Lifecycle get() = lifecycleRegistry

    private val activityCallbacks = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityStarted(activity: Activity) { startedActivities++ }
        override fun onActivityStopped(activity: Activity) {
            startedActivities = (startedActivities - 1).coerceAtLeast(0)
        }
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
        override fun onActivityResumed(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
    }

    override fun onCreate() {
        super.onCreate()
        musicManager.setServiceRunning(true)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        (application as Application).registerActivityLifecycleCallbacks(activityCallbacks)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startAsForeground()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        if (!loopStarted) {
            loopStarted = true
            serviceScope.launch {
                repository.migrateIfNeeded()
                timerLoop()
            }
        }
        if (!presenceStarted) {
            presenceStarted = true
            serviceScope.launch { presenceLoop() }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        musicManager.setServiceRunning(false)
        (application as Application).unregisterActivityLifecycleCallbacks(activityCallbacks)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startAsForeground() {
        val notification = notifier.buildFocusStatusNotification(getString(R.string.focus_status_initializing)).build()
        val cameraGranted = checkSelfPermission(android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            if (cameraGranted) ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            else ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else 0
        ServiceCompat.startForeground(this, Notifier.FOCUS_STATUS_NOTIFICATION_ID, notification, type)
    }

    private suspend fun timerLoop() {
        while (serviceScope.isActive) {
            repository.archiveExpiredPlan()
            val snapshot = repository.focusState.first()
            when (snapshot.phase) {
                FocusPhase.ACTIVE -> {
                    val timing = FocusTiming.resolve(snapshot)
                    if (timing.remainingMs <= 0L) {
                        repository.completeSession()?.let { result -> notifier.sendFocusCompletion(result) }
                    } else if (snapshot.activeStrictMode) {
                        if (!learningAppManager.hasUsageAccess()) {
                            if (repository.pauseForPresence()) notifier.sendFocusUsageAccessLost()
                        } else if (checkStrictMode(snapshot)) return
                    }
                }
                FocusPhase.RESTING -> {
                    if (FocusTiming.restRemainingMs(snapshot) <= 0L) {
                        if (repository.completeRest()) notifier.sendFocusRestCompleted()
                        stopSelf()
                        return
                    }
                }
                FocusPhase.PRESENCE_PAUSED -> {
                    if (repository.shelvePresencePause()) {
                        notifier.sendFocusShelved()
                        stopSelf()
                        return
                    }
                }
                FocusPhase.IDLE, FocusPhase.REST_READY, FocusPhase.COMPLETE, FocusPhase.PLAN_PAUSED -> {
                    stopSelf()
                    return
                }
            }
            val current = repository.focusState.first()
            notifier.updateFocusStatus(statusText(current))
            delay(1_000L)
        }
    }

    /** 返回 true 表示严格模式已结束，调用方应立即退出循环。 */
    private suspend fun checkStrictMode(snapshot: FocusSnapshot): Boolean {
        if (isCallOrScreenOff()) {
            disallowedSince = 0L
            return false
        }
        val foreground = if (startedActivities > 0) packageName else learningAppManager.currentForegroundPackage()
        val allowed = foreground == null || foreground == packageName ||
            foreground in snapshot.allowedPackages || learningAppManager.isSystemOverlay(foreground)
        if (allowed) {
            disallowedSince = 0L
            return false
        }
        val now = SystemClock.elapsedRealtime()
        if (disallowedSince == 0L) disallowedSince = now
        if (now - disallowedSince >= STRICT_GRACE_MS) {
            if (repository.abandonSession(FocusSessionEntity.DISTRACTED)) notifier.sendFocusInterrupted()
            stopSelf()
            return true
        }
        return false
    }

    private suspend fun presenceLoop() {
        while (serviceScope.isActive) {
            val snapshot = repository.focusState.first()
            val shouldSample = snapshot.activePresenceEnabled && snapshot.phase in listOf(
                FocusPhase.ACTIVE, FocusPhase.PRESENCE_PAUSED
            )
            if (!shouldSample) {
                noFaceSince = 0L
                faceSeenSince = 0L
                delay(2_000L)
                continue
            }
            val result = distanceSampler.sampleOnce(this)
            val faceVisible = result is DistanceSampler.SampleResult.Success && result.faceWidthPx != null
            val now = SystemClock.elapsedRealtime()
            when (snapshot.phase) {
                FocusPhase.ACTIVE -> {
                    if (faceVisible) {
                        noFaceSince = 0L
                    } else {
                        if (noFaceSince == 0L) noFaceSince = now
                        if (now - noFaceSince >= NO_FACE_GRACE_MS && repository.pauseForPresence()) {
                            notifier.sendFocusPresencePaused()
                        }
                    }
                }
                FocusPhase.PRESENCE_PAUSED -> {
                    val strictAccessReady = !snapshot.activeStrictMode || learningAppManager.hasUsageAccess()
                    if (faceVisible && strictAccessReady) {
                        if (faceSeenSince == 0L) faceSeenSince = now
                        if (now - faceSeenSince >= FACE_RESUME_MS) repository.resumeAfterPresence()
                    } else {
                        faceSeenSince = 0L
                    }
                }
            }
            delay(PRESENCE_SAMPLE_MS)
        }
    }

    private fun statusText(snapshot: FocusSnapshot): String {
        val remaining = if (snapshot.phase == FocusPhase.RESTING) FocusTiming.restRemainingMs(snapshot)
        else FocusTiming.resolve(snapshot).remainingMs
        return when (snapshot.phase) {
            FocusPhase.RESTING -> getString(R.string.focus_status_resting, formatMinutes(remaining))
            FocusPhase.PRESENCE_PAUSED -> if (snapshot.activeStrictMode && !learningAppManager.hasUsageAccess()) {
                getString(R.string.focus_usage_access_lost_title)
            } else {
                getString(R.string.focus_status_presence_paused)
            }
            FocusPhase.PLAN_PAUSED -> getString(R.string.focus_status_plan_paused)
            FocusPhase.ACTIVE -> if (snapshot.activeStrictMode) {
                getString(R.string.focus_status_strict_remaining, formatMinutes(remaining))
            } else {
                getString(R.string.focus_status_remaining, formatMinutes(remaining))
            }
            else -> getString(R.string.focus_status_remaining, formatMinutes(remaining))
        }
    }

    private fun isCallOrScreenOff(): Boolean {
        val power = getSystemService(POWER_SERVICE) as PowerManager
        val audio = getSystemService(AUDIO_SERVICE) as AudioManager
        return !power.isInteractive || audio.mode != AudioManager.MODE_NORMAL
    }

    private fun formatMinutes(milliseconds: Long): String {
        val seconds = (milliseconds / 1_000L).coerceAtLeast(0L)
        return "%02d:%02d".format(seconds / 60, seconds % 60)
    }

    companion object {
        private const val STRICT_GRACE_MS = 10_000L
        private const val NO_FACE_GRACE_MS = 30_000L
        private const val FACE_RESUME_MS = 3_000L
        private const val PRESENCE_SAMPLE_MS = 5_000L

        fun start(context: Context) {
            runCatching { context.startForegroundService(Intent(context, FocusService::class.java)) }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FocusService::class.java))
        }
    }
}
