package com.eyeguard.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableIntStateOf
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

    private val focusRequest = mutableIntStateOf(0)

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
        handleNotificationIntent(intent)
        setContent {
            EyeGuardTheme {
                EyeGuardNavHost(focusRequest = focusRequest.intValue)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
    }

    private fun handleNotificationIntent(intent: Intent?) {
        when (intent?.action) {
            Notifier.ACTION_START_GUARD -> EyeGuardService.start(this)
            Notifier.ACTION_OPEN_FOCUS -> focusRequest.intValue++
        }
    }
}
