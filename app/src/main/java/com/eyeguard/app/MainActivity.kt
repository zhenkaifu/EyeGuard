package com.eyeguard.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.eyeguard.app.core.datastore.SettingsRepository
import com.eyeguard.app.core.notification.Notifier
import com.eyeguard.app.core.service.EyeGuardService
import com.eyeguard.app.ui.LocaleHelper
import com.eyeguard.app.ui.navigation.EyeGuardNavHost
import com.eyeguard.app.ui.theme.EyeGuardTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

/**
 * 单 Activity 入口，承载全部 Compose 页面与 Navigation。
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    /** 在 super.attachBaseContext 前读取语言设置并包装 Context。 */
    override fun attachBaseContext(newBase: Context) {
        // attachBaseContext 早于注入完成，无法用 @Inject 的实例；
        // 用独立实例做一次同步读取（DataStore 有缓存，开销可忽略）
        val language = runBlocking {
            SettingsRepository(newBase).appLanguage.first()
        }
        LocaleHelper.setLanguage(language)
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleStartGuardIntent(intent)
        setContent {
            EyeGuardTheme {
                EyeGuardNavHost()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleStartGuardIntent(intent)
    }

    /**
     * 开机兜底通知（ACTION_START_GUARD）点击后自动重启守护服务：
     * 前台 Activity 内启动前台服务不受 Android 12+ 后台启动限制。
     */
    private fun handleStartGuardIntent(intent: Intent?) {
        if (intent?.action == Notifier.ACTION_START_GUARD) {
            EyeGuardService.start(this)
        }
    }
}
