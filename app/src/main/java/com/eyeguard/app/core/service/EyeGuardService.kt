package com.eyeguard.app.core.service

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.eyeguard.app.core.datastore.SettingsRepository
import com.eyeguard.app.core.billing.BillingRepository
import com.eyeguard.app.core.database.DailyUsageRepository
import com.eyeguard.app.core.notification.Notifier
import com.eyeguard.app.feature.distance.DistanceSampler
import com.eyeguard.app.feature.filter.EyeFilterController
import com.eyeguard.app.feature.light.LightMonitor
import com.eyeguard.app.feature.usage.UsageMonitor
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

/**
 * EyeGuard 常驻前台服务：按设置的间隔调度距离采样，
 * 单次采样低于阈值即发出"距离过近"提醒，常驻通知显示守护状态。
 *
 * 国产 ROM 后台限制保活注意事项（MIUI / EMUI / ColorOS 等）：
 * 1. 本服务同时声明 camera + specialUse 前台服务类型，但国产 ROM 的"省电策略 /
 *    自启动管理"默认可能禁止应用自启动或后台运行，必须在系统设置中手动允许：
 *    设置 → 应用管理 → EyeGuard → 省电策略 → 无限制；并允许"自启动"。
 * 2. 不要依赖闹钟精确唤醒做频繁采样（国产 ROM 会批量延迟 JobScheduler/
 *    AlarmManager 非精确闹钟）；30 秒级采样在服务存活期间用协程 delay 即可。
 * 3. 进程被杀后只能依赖用户重新打开应用（可提供桌面快捷方式 / 首页守护开关），
 *    不要尝试灰色保活手段（1 像素 Activity、双进程互拉），应用市场会拒审。
 * 4. 部分 ROM 对"相机"类前台服务额外敏感，若常驻通知被用户手动关闭，
 *    服务会随之被系统回收，需在首页提示用户重新开启守护。
 */
@AndroidEntryPoint
class EyeGuardService : Service(), LifecycleOwner {

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var notifier: Notifier
    @Inject lateinit var distanceSampler: DistanceSampler
    @Inject lateinit var lightMonitor: LightMonitor
    @Inject lateinit var usageMonitor: UsageMonitor
    @Inject lateinit var dailyUsageRepository: DailyUsageRepository
    @Inject lateinit var eyeFilterController: EyeFilterController
    @Inject lateinit var billingRepository: BillingRepository

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** 连续低于阈值的命中次数（要求 ≥2 才提醒，防抖）。 */
    private var consecutiveTooCloseHits = 0

    /** "暂停 1 小时"截止时间戳；0 表示未暂停。 */
    @Volatile
    private var pausedUntilMs: Long = 0L

    /** 遮罩"临时关闭 5 分钟"截止时间戳；0 表示未临时关闭。 */
    @Volatile
    private var filterSnoozedUntilMs: Long = 0L

    private val controlReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Notifier.ACTION_PAUSE_MONITORING -> {
                    pausedUntilMs = System.currentTimeMillis() + Notifier.PAUSE_DURATION_MS
                    updateStatus(getString(com.eyeguard.app.R.string.status_paused_1h))
                }
                Notifier.ACTION_PAUSE_MONITORING_15M -> {
                    pausedUntilMs = System.currentTimeMillis() + Notifier.PAUSE_15M_DURATION_MS
                    updateStatus(getString(com.eyeguard.app.R.string.status_paused_15m))
                }
                Notifier.ACTION_RESUME_MONITORING -> {
                    pausedUntilMs = 0L
                    updateStatus(getString(com.eyeguard.app.R.string.status_resumed))
                }
                Notifier.ACTION_SNOOZE_FILTER -> {
                    filterSnoozedUntilMs = System.currentTimeMillis() + Notifier.FILTER_SNOOZE_MS
                    eyeFilterController.hide()
                    updateStatus(getString(com.eyeguard.app.R.string.filter_snoozed))
                }
            }
        }
    }

    override val lifecycle: Lifecycle get() = lifecycleRegistry

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        registerReceiver(
            controlReceiver,
            IntentFilter().apply {
                addAction(Notifier.ACTION_PAUSE_MONITORING)
                addAction(Notifier.ACTION_PAUSE_MONITORING_15M)
                addAction(Notifier.ACTION_RESUME_MONITORING)
                addAction(Notifier.ACTION_SNOOZE_FILTER)
            },
            Context.RECEIVER_NOT_EXPORTED
        )

        // 环境光：常开低功耗传感器监听；屏幕状态由 UsageMonitor 提供
        lightMonitor.isScreenOnProvider = { usageMonitor.screenOn }
        lightMonitor.onTooDark = {
            // 免打扰时段内所有提醒静音（DND 检查为挂起操作，切协程）
            if (!isPaused()) {
                serviceScope.launch {
                    if (inDndWindow()) return@launch
                    notifier.sendEyeReminder(
                        title = getString(com.eyeguard.app.R.string.notif_dark_title),
                        content = getString(com.eyeguard.app.R.string.notif_dark_content),
                        vibrate = true,
                    )
                    dailyUsageRepository.incrementDarkRemindersToday()
                    updateStatus(getString(com.eyeguard.app.R.string.status_reminded_light))
                }
            }
        }
        lightMonitor.start()

        // 用眼时长：亮灭屏广播 + 20-20-20 法则
        usageMonitor.onUsageReminder = {
            if (!isPaused()) {
                serviceScope.launch {
                    if (inDndWindow()) return@launch
                    notifier.sendEyeReminder(
                        title = getString(com.eyeguard.app.R.string.notif_usage_title, usageMonitor.thresholdMinutes),
                        content = getString(com.eyeguard.app.R.string.notif_usage_content),
                        vibrate = true,
                    )
                    dailyUsageRepository.incrementUsageRemindersToday()
                    updateStatus(getString(com.eyeguard.app.R.string.status_rest_20s))
                }
            }
        }
        usageMonitor.start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startAsForeground()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        serviceScope.launch { monitoringLoop() }
        // 被杀后不自动重启，避免国产 ROM 上引发频繁崩溃弹窗
        return START_NOT_STICKY
    }

    /**
     * 用户从"最近任务"划掉 App 时回调。
     *
     * 部分国产 ROM（含荣耀）会在划掉任务时连带杀掉前台服务，导致退后台后提醒失效。
     * 这里在服务仍存活的情况下自我重启一次，配合电池白名单 + 自启动设置实现常驻。
     * 注意：若守护已被用户关闭（服务已销毁），本回调不会触发，不会误启动。
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        if (isRunning) {
            startService(Intent(this, EyeGuardService::class.java))
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        isRunning = false
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        serviceScope.cancel()
        lightMonitor.stop()
        usageMonitor.stop()
        // 遮罩随服务销毁移除，避免残留悬浮窗
        eyeFilterController.hide()
        runCatching { unregisterReceiver(controlReceiver) }
        super.onDestroy()
    }

    /**
     * 以前台服务形式启动（API 34 需显式指定 foregroundServiceType=camera）。
     */
    private fun startAsForeground() {
        val notification = notifier.buildStatusNotification(getString(com.eyeguard.app.R.string.status_init)).build()
        val foregroundType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
        } else {
            0
        }
        ServiceCompat.startForeground(
            this,
            Notifier.STATUS_NOTIFICATION_ID,
            notification,
            foregroundType
        )
    }

    private fun isPaused(): Boolean = System.currentTimeMillis() < pausedUntilMs

    /**
     * 是否处于免打扰时段（Pro 功能；免费用户存储中即使有值也忽略）。
     * 支持跨零点时段。
     */
    private suspend fun inDndWindow(): Boolean {
        if (!billingRepository.isPro.value) return false
        if (!settingsRepository.dndEnabled.first()) return false
        val now = java.time.LocalTime.now()
        val nowMinutes = now.hour * 60 + now.minute
        val start = settingsRepository.dndStartMinutes.first()
        val end = settingsRepository.dndEndMinutes.first()
        return if (start <= end) {
            nowMinutes in start until end
        } else {
            nowMinutes >= start || nowMinutes < end
        }
    }

    /**
     * 主监控循环：按设置间隔（默认 30 秒）执行一次距离采样。
     * 相机权限被撤销或采样不可用时不崩溃，仅跳过本次。
     */
    private suspend fun monitoringLoop() {
        while (serviceScope.isActive) {
            val intervalSec = batteryAwareIntervalSeconds(
                settingsRepository.distanceSamplingIntervalSeconds.first()
            )
            val enabled = settingsRepository.distanceAlertEnabled.first()

            // 光线 / 用眼/距离阈值每轮同步，设置页修改后自动生效。
            // 自定义阈值仅 Pro 可用：免费用户固定使用默认值。
            val pro = billingRepository.isPro.value
            val thresholdCm = if (pro) settingsRepository.distanceThresholdCm.first() else DEFAULT_DISTANCE_CM
            lightMonitor.enabled = settingsRepository.lightAlertEnabled.first()
            lightMonitor.thresholdLux = if (pro) settingsRepository.lightThresholdLux.first() else DEFAULT_LUX_THRESHOLD
            usageMonitor.enabled = settingsRepository.usageAlertEnabled.first()
            usageMonitor.thresholdMinutes =
                if (pro) settingsRepository.usageThresholdMinutes.first() else DEFAULT_USAGE_MINUTES
            usageMonitor.tick()

            applyEyeFilter()

            when {
                !enabled -> updateStatus(getString(com.eyeguard.app.R.string.status_distance_off))
                isPaused() -> updateStatus(getString(com.eyeguard.app.R.string.status_paused_generic))
                !hasCameraPermission() -> updateStatus(getString(com.eyeguard.app.R.string.status_no_camera_perm))
                else -> runSampling(thresholdCm, intervalSec)
            }
            delay(intervalSec.coerceAtLeast(10L) * 1000L)
        }
    }

    /**
     * 低电量自适应采样：电量 ≤20% 且未充电时把采样间隔翻倍（省电），
     * 充电或电量正常时使用用户设定值。
     */
    private fun batteryAwareIntervalSeconds(baseSec: Long): Long {
        val batteryIntent: Intent? =
            registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1
        val status = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1) ?: -1
        if (level < 0 || scale <= 0) return baseSec
        val charging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING ||
            status == android.os.BatteryManager.BATTERY_STATUS_FULL
        val percent = level * 100 / scale
        return if (!charging && percent <= 20) baseSec * 2 else baseSec
    }

    private fun hasCameraPermission(): Boolean =
        checkSelfPermission(android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    /**
     * 应用护眼遮罩：读取设置（开关 / 档位 / 智能模式 / 夜间时段），
     * 智能模式下结合环境光与当前时间计算参数。"临时关闭 5 分钟"期间隐藏，
     * 到期后下一轮循环自动恢复。
     */
    private suspend fun applyEyeFilter() {
        val enabled = settingsRepository.filterEnabled.first()
        val snoozed = System.currentTimeMillis() < filterSnoozedUntilMs

        if (!enabled || snoozed) {
            if (!snoozed) eyeFilterController.hide()
            return
        }
        if (!eyeFilterController.hasOverlayPermission()) {
            eyeFilterController.hide()
            return
        }

        val intensity = settingsRepository.filterIntensity.first()
        val smart = settingsRepository.filterSmartEnabled.first()

        val (color, alpha) = if (smart) {
            val nowMinutes =
                java.time.LocalTime.now().hour * 60 + java.time.LocalTime.now().minute
            val night = eyeFilterController.isNightTime(
                nowMinutes,
                settingsRepository.nightStartMinutes.first(),
                settingsRepository.nightEndMinutes.first()
            )
            eyeFilterController.computeSmartMask(intensity, lightMonitor.currentLux, night)
        } else {
            EyeFilterController.INTENSITY_COLORS[intensity] to
                EyeFilterController.INTENSITY_ALPHAS[intensity]
        }
        eyeFilterController.show(color, alpha)
    }

    /**
     * 单次采样与命中判定：
     * 低于阈值 → 立即触发提醒（1 次命中即提醒，用户要求发现即提醒），并把
     * 本采样间隔累加进"当日距离过近时长"；高于阈值或采样不可用 → 命中清零。
     */
    private suspend fun runSampling(thresholdCm: Int, intervalSec: Long) {
        updateStatus(getString(com.eyeguard.app.R.string.status_detecting))
        val result = distanceSampler.sampleOnce(this)
        when (result) {
            is DistanceSampler.SampleResult.Unavailable -> {
                consecutiveTooCloseHits = 0
                updateStatus(getString(com.eyeguard.app.R.string.status_camera_busy))
            }
            is DistanceSampler.SampleResult.Success -> {
                val distanceCm = result.distanceCm
                if (distanceCm == null) {
                    // 未检测到人脸（脸不在画面内或超出可信范围），视为正常
                    consecutiveTooCloseHits = 0
                    updateStatus(getString(com.eyeguard.app.R.string.status_distance_normal))
                    return
                }
                if (distanceCm < thresholdCm) {
                    consecutiveTooCloseHits++
                    // 估算"距离过近"持续时长：命中一次约等于一个采样间隔
                    serviceScope.launch {
                        dailyUsageRepository.addTooCloseDurationToday(intervalSec * 1000L)
                    }
                    if (consecutiveTooCloseHits >= REQUIRED_HITS && !inDndWindow()) {
                        notifier.sendEyeReminder(
                            title = getString(com.eyeguard.app.R.string.notif_too_close_title),
                            content = getString(com.eyeguard.app.R.string.notif_too_close_content, distanceCm, thresholdCm),
                            vibrate = true,
                        )
                        serviceScope.launch { dailyUsageRepository.incrementDistanceRemindersToday() }
                        consecutiveTooCloseHits = 0
                        updateStatus(getString(com.eyeguard.app.R.string.status_reminded_rest))
                    } else {
                        updateStatus(getString(com.eyeguard.app.R.string.status_distance_close))
                    }
                } else {
                    consecutiveTooCloseHits = 0
                    updateStatus(getString(com.eyeguard.app.R.string.status_distance_value, distanceCm))
                }
            }
        }
    }

    private fun updateStatus(statusText: String) {
        // 暂停状态决定常驻通知显示"立即恢复"还是"暂停 15 分钟 / 1 小时"按钮
        notifier.updateStatusNotification(statusText, isPaused())
    }

    companion object {
        /** 服务是否运行中（首页开关状态显示用）。 */
        @Volatile
        var isRunning = false
            private set

        /** 判定"距离过近"所需的连续命中次数（防抖）。当前为 1：发现即提醒。 */
        const val REQUIRED_HITS = 1

        // 注意：提醒通知不要再用固定 ID——重复 ID 只是"更新"旧通知，
        // 系统不弹横幅不震动（表现为"只提醒一次"）。Notifier 现已为每条
        // 提醒分配自增唯一 ID，见 Notifier.REMINDER_ID_BASE。

        /** 免费用户固定阈值（自定义阈值仅 Pro 可调）。 */
        const val DEFAULT_DISTANCE_CM = 30
        const val DEFAULT_LUX_THRESHOLD = 50
        const val DEFAULT_USAGE_MINUTES = 20

        /** 启动守护服务。 */
        fun start(context: Context) {
            context.startForegroundService(Intent(context, EyeGuardService::class.java))
        }

        /** 停止守护服务。 */
        fun stop(context: Context) {
            context.stopService(Intent(context, EyeGuardService::class.java))
        }
    }
}
