package com.eyeguard.app.core.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 统一通知封装：创建并管理"护眼提醒"高优先级通知渠道，
 * 提供 Heads-up 样式护眼提醒的发送入口。
 */
@Singleton
class Notifier @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        const val CHANNEL_ID_REMINDER = "eye_reminder"
        const val DEFAULT_NOTIFICATION_ID = 1001

        /** 提醒通知自增 ID 基数（每条提醒全新 ID，避免更新旧通知不弹横幅）。 */
        const val REMINDER_ID_BASE = 3000

        /** 前台服务常驻状态通知渠道与通知 ID。 */
        const val CHANNEL_ID_STATUS = "eye_status"
        const val STATUS_NOTIFICATION_ID = 1000

        /** "暂停 1 小时"通知操作的 broadcast action。 */
        const val ACTION_PAUSE_MONITORING = "com.eyeguard.app.action.PAUSE_MONITORING"
        const val PAUSE_DURATION_MS = 60 * 60 * 1000L

        /** "暂停 15 分钟"通知操作的 broadcast action。 */
        const val ACTION_PAUSE_MONITORING_15M = "com.eyeguard.app.action.PAUSE_MONITORING_15M"
        const val PAUSE_15M_DURATION_MS = 15 * 60 * 1000L

        /** "立即恢复"（取消暂停）通知操作的 broadcast action。 */
        const val ACTION_RESUME_MONITORING = "com.eyeguard.app.action.RESUME_MONITORING"

        /** 开机兜底通知点击后拉起 MainActivity 并自动重启守护的 action。 */
        const val ACTION_START_GUARD = "com.eyeguard.app.action.START_GUARD"

        /** "临时关闭护眼遮罩 5 分钟"通知操作的 broadcast action。 */
        const val ACTION_SNOOZE_FILTER = "com.eyeguard.app.action.SNOOZE_FILTER"
        const val FILTER_SNOOZE_MS = 5 * 60 * 1000L
    }

    private val notificationManager: NotificationManagerCompat =
        NotificationManagerCompat.from(context)

    /** 提醒通知自增 ID（每条提醒全新 ID，避免"更新旧通知不弹横幅"问题）。 */
    private val reminderIdCounter = java.util.concurrent.atomic.AtomicInteger(0)

    init {
        createReminderChannel()
        createStatusChannel()
    }

    /**
     * 创建"护眼提醒"通知渠道（IMPORTANCE_HIGH，支持 Heads-up 弹出）。
     */
    private fun createReminderChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID_REMINDER,
            context.getString(com.eyeguard.app.R.string.channel_reminder_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(com.eyeguard.app.R.string.channel_reminder_desc)
            enableVibration(true)
        }
        notificationManager.createNotificationChannel(channel)
    }

    /**
     * 创建低优先级常驻状态渠道（前台服务通知必须使用，避免每次采样都弹出横幅）。
     */
    private fun createStatusChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID_STATUS,
            context.getString(com.eyeguard.app.R.string.channel_status_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(com.eyeguard.app.R.string.channel_status_desc)
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(channel)
    }

    /**
     * 构建前台服务常驻状态通知。
     *
     * 操作按钮随暂停状态变化：
     * 未暂停：[暂停 15 分钟][暂停 1 小时][遮罩关 5 分钟]；
     * 已暂停：[立即恢复][遮罩关 5 分钟]（通知按钮上限 3 个）。
     *
     * @param statusText 当前状态文案，如"守护中 · 距离正常"
     * @param paused 当前是否处于"暂停"状态
     */
    @JvmOverloads
    fun buildStatusNotification(statusText: String, paused: Boolean = false): NotificationCompat.Builder {
        fun actionIntent(action: String, requestCode: Int) = android.app.PendingIntent.getBroadcast(
            context,
            requestCode,
            android.content.Intent(action).setPackage(context.packageName),
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID_STATUS)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("EyeGuard")
            .setContentText(statusText)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)

        if (paused) {
            builder.addAction(
                0,
                context.getString(com.eyeguard.app.R.string.resume_action),
                actionIntent(ACTION_RESUME_MONITORING, 3)
            )
        } else {
            builder.addAction(
                0,
                context.getString(com.eyeguard.app.R.string.pause_15m_action),
                actionIntent(ACTION_PAUSE_MONITORING_15M, 2)
            )
            builder.addAction(
                0,
                context.getString(com.eyeguard.app.R.string.pause_1h_action),
                actionIntent(ACTION_PAUSE_MONITORING, 0)
            )
        }
        builder.addAction(
            0,
            context.getString(com.eyeguard.app.R.string.snooze_filter_action),
            actionIntent(ACTION_SNOOZE_FILTER, 1)
        )
        return builder
    }

    /**
     * 更新常驻状态通知的文案。
     *
     * @param paused 当前是否处于暂停状态（决定操作按钮显示"立即恢复"还是"暂停"档）
     */
    @JvmOverloads
    fun updateStatusNotification(statusText: String, paused: Boolean = false) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        notificationManager.notify(STATUS_NOTIFICATION_ID, buildStatusNotification(statusText, paused).build())
    }

    /**
     * 发送一条护眼提醒通知。
     *
     * 默认使用高优先级渠道以 Heads-up 形式弹出；[vibrate] 为 true 时附加震动效果。
     * 调用前需确保已持有 POST_NOTIFICATIONS 权限（API 33+）。
     *
     * 注意：每条提醒使用【全新唯一 ID】。[notificationId] 传 0（默认）时自动分配。
     * 原因：用已存在的 ID 重复发通知只是"更新"旧通知，系统（尤其国产 ROM /
     * Android 14+）对更新已有通知不弹横幅、不响铃、不震动——表现为"只提醒一次"。
     */
    fun sendEyeReminder(
        title: String,
        content: String,
        vibrate: Boolean = false,
        notificationId: Int = 0
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val id = if (notificationId != 0) notificationId
        else REMINDER_ID_BASE + reminderIdCounter.incrementAndGet()

        val builder = NotificationCompat.Builder(context, CHANNEL_ID_REMINDER)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
        if (vibrate) {
            builder.setVibrate(longArrayOf(0, 300, 200, 300))
        }

        notificationManager.notify(id, builder.build())

        // 双保险：部分国产 ROM 会忽略通知里的震动参数，直接调系统振动器
        if (vibrate) vibrateDirectly()
    }

    /**
     * 开机后守护未自动恢复时发送的提醒：点击拉起 MainActivity 并自动重启守护服务。
     * （Android 12+ 对 BOOT_COMPLETED 后直接起前台服务限制严格，点击通知拉起是最稳妥路径。）
     */
    fun sendBootResumeReminder() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.apply {
                action = ACTION_START_GUARD
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            } ?: return
        val contentIntent = android.app.PendingIntent.getActivity(
            context,
            10,
            launchIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                android.app.PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(context, CHANNEL_ID_REMINDER)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(context.getString(com.eyeguard.app.R.string.notif_boot_title))
            .setContentText(context.getString(com.eyeguard.app.R.string.notif_boot_content))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
        notificationManager.notify(REMINDER_ID_BASE + reminderIdCounter.incrementAndGet(), builder.build())
    }

    /** 直接调用系统振动器（绕过 ROM 对通知震动的拦截）。 */
    private fun vibrateDirectly() {
        runCatching {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE)
                    as android.os.VibratorManager
                vm.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(
                    android.os.VibrationEffect.createWaveform(longArrayOf(0, 300, 200, 300), -1)
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(longArrayOf(0, 300, 200, 300), -1)
            }
        }
    }
}
