package com.eyeguard.app.ui

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * 应用语言切换辅助：按用户选择（zh/en）包装 Context 配置。
 * 不依赖 AppCompatDelegate，兼容 minSdk 26。
 */
object LocaleHelper {

    /** 当前生效的语言代码（从 DataStore 同步读取一次）。 */
    private var currentLanguage: String = "zh"

    /**
     * 用户本次会话内显式切换过语言。为 true 时，attachBaseContext 里从
     * DataStore 读到的（可能尚未落盘的）旧值不得覆盖用户刚选的语言。
     */
    private var userOverridden: Boolean = false

    /**
     * @param userInitiated 是否为用户在设置页主动切换（区别于启动时的持久化读取）
     */
    fun setLanguage(language: String, userInitiated: Boolean = false) {
        if (userInitiated) userOverridden = true
        if (userOverridden && !userInitiated) return
        currentLanguage = language
    }

    /** 用保存的语言包装 Context。 */
    fun wrap(context: Context): Context {
        val locale = Locale.forLanguageTag(currentLanguage)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }
}
