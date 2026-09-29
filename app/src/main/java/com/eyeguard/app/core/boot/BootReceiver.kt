package com.eyeguard.app.core.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.eyeguard.app.core.datastore.SettingsRepository
import com.eyeguard.app.core.notification.Notifier
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 开机自启兜底：手机重启后前台服务不会自动恢复。
 *
 * Android 12+ 对"BOOT_COMPLETED 后直接 startForegroundService"限制严格
 * （camera 类型不在豁免清单内，直接起服务在多数机型会抛
 * ForegroundServiceStartNotAllowedException），因此这里不直接拉服务，
 * 而是发一条高优先级通知："守护未恢复，点击一键开启"，
 * 点击后拉起 MainActivity（action = ACTION_START_GUARD）由前台 Activity 启动服务。
 *
 * 仅当用户之前开启过守护（guardEnabled=true）才发通知，避免打扰未启用用户。
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var notifier: Notifier

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                if (settingsRepository.guardEnabled.first()) {
                    notifier.sendBootResumeReminder()
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
