package com.eyeguard.app

import android.app.Application
import com.eyeguard.app.core.billing.BillingRepository
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * EyeGuard 应用入口：启用 Hilt 依赖注入，并初始化 RevenueCat 计费。
 */
@HiltAndroidApp
class EyeGuardApp : Application() {

    @Inject
    lateinit var billingRepository: BillingRepository

    override fun onCreate() {
        super.onCreate()
        // RevenueCat 全局初始化（API Key 来自 local.properties，见 README）；
        // Key 缺失时静默降级，所有购买功能显示"未配置"占位
        billingRepository.init()
    }
}
