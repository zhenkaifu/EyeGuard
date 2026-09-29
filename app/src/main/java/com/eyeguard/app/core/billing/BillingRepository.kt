package com.eyeguard.app.core.billing

import android.app.Activity
import android.content.Context
import com.eyeguard.app.BuildConfig
import com.eyeguard.app.R
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.LogLevel
import com.revenuecat.purchases.Offering
import com.revenuecat.purchases.Offerings
import com.revenuecat.purchases.Package
import com.revenuecat.purchases.PurchaseParams
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration
import com.revenuecat.purchases.PurchasesError
import com.revenuecat.purchases.PurchasesErrorCode
import com.revenuecat.purchases.interfaces.PurchaseCallback
import com.revenuecat.purchases.interfaces.ReceiveCustomerInfoCallback
import com.revenuecat.purchases.interfaces.ReceiveOfferingsCallback
import com.revenuecat.purchases.models.StoreTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * RevenueCat 订阅仓库：封装初始化、offerings 获取、购买、恢复购买与
 * entitlement（"pro"）状态监听。
 *
 * 测试说明（TODO）：
 * - Google Play 测试：把测试账号加入 Play Console「许可测试」，使用
 *   RevenueCat 控制台创建的测试商品 / 测试订阅（Subs 会自动续费、可被撤销）。
 * - iOS StoreKit 测试需在 Xcode 配置 StoreKit 配置文件（本仓库为 Android，
 *   多平台共用业务代码时注意 entitlement id 一致即可）。
 * - 控制台「Test Store」开关打开后，沙盒购买不会计费。
 *
 * API Key 通过 BuildConfig 注入（来源 local.properties，见 README），代码内不硬编码。
 */
@Singleton
class BillingRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: com.eyeguard.app.core.datastore.SettingsRepository
) {

    /** 当前 effective offering 中的月付 / 年付套餐。 */
    data class PaywallPackages(val monthly: Package?, val yearly: Package?)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _offerings = MutableStateFlow<Offerings?>(null)
    val offerings: StateFlow<Offerings?> = _offerings

    private val _customerInfo = MutableStateFlow<CustomerInfo?>(null)
    val customerInfo: StateFlow<CustomerInfo?> = _customerInfo

    private val _paywallPackages = MutableStateFlow<PaywallPackages?>(null)
    val paywallPackages: StateFlow<PaywallPackages?> = _paywallPackages

    /** 是否已初始化（API Key 为空时不初始化，所有购买功能静默降级）。 */
    @Volatile
    var initialized = false
        private set

    /** RevenueCat  entitlement 是否激活。 */
    private val _entitlementActive = MutableStateFlow(false)

    /**
     * 用户是否持有 Pro：
     * RevenueCat entitlement 激活 或 演示模式本地解锁（两者取或）。
     */
    val isPro: StateFlow<Boolean> = combine(
        _entitlementActive,
        settingsRepository.demoProUnlocked
    ) { entitlement, demo -> entitlement || demo }
        .stateIn(scope, SharingStarted.Eagerly, false)

    private fun publishCustomerInfo(info: CustomerInfo?) {
        _customerInfo.value = info
        _entitlementActive.value = info?.entitlements?.get(ENTITLEMENT_PRO)?.isActive == true
    }

    init {
        // 黑客松演示模式：未配置 RevenueCat Key 时仍可本地解锁 Pro 功能
        if (BuildConfig.REVENUECAT_API_KEY.isBlank() && BuildConfig.DEMO_BILLING) {
            scope.launch { settingsRepository.setDemoProUnlocked(true) }
        }
    }

    /**
     * 初始化 RevenueCat（在 Application.onCreate 调用一次）。
     *
     * 重复调用安全：已初始化时直接返回。API Key 缺失时打日志降级，
     * 付费墙显示"未配置"占位。
     */
    @Synchronized
    fun init() {
        if (initialized) return
        val apiKey = BuildConfig.REVENUECAT_API_KEY
        if (apiKey.isBlank()) {
            // TODO: 接入 Crashlytics 后在此上报"未配置 RevenueCat Key"
            return
        }
        Purchases.logLevel = LogLevel.DEBUG
        Purchases.configure(PurchasesConfiguration.Builder(context, apiKey).build())
        initialized = true

        // 监听 entitlement 变化（购买、续费、到期、恢复都会回调）
        Purchases.sharedInstance.updatedCustomerInfoListener =
            com.revenuecat.purchases.interfaces.UpdatedCustomerInfoListener { info ->
                publishCustomerInfo(info)
            }
        Purchases.sharedInstance.getCustomerInfo(object : ReceiveCustomerInfoCallback {
            override fun onReceived(customerInfo: CustomerInfo) {
                publishCustomerInfo(customerInfo)
            }

            override fun onError(error: PurchasesError) = Unit
        })
    }

    /** 拉取 offerings 并提取月付 / 年付套餐（失败静默，UI 显示加载态）。 */
    fun loadOfferings(onError: (String) -> Unit = {}) {
        if (!initialized) {
            onError(context.getString(R.string.billing_not_configured))
            return
        }
        Purchases.sharedInstance.getOfferings(object : ReceiveOfferingsCallback {
            override fun onReceived(offerings: Offerings) {
                _offerings.value = offerings
                val current: Offering? = offerings.current
                _paywallPackages.value = PaywallPackages(
                    monthly = current?.monthly,
                    yearly = current?.annual
                )
            }

            override fun onError(error: PurchasesError) {
                onError(error.message)
            }
        })
    }

    /**
     * 发起购买。
     *
     * @param activity 用于拉起 Play 结算页面
     * @param onResult (用户已持有该 entitlement, 错误信息 or null)
     */
    fun purchase(
        activity: Activity,
        packageToPurchase: Package,
        onResult: (isPro: Boolean, error: String?) -> Unit
    ) {
        if (!initialized) {
            onResult(false, context.getString(R.string.billing_not_configured))
            return
        }
        Purchases.sharedInstance.purchase(
            PurchaseParams.Builder(activity, packageToPurchase).build(),
            object : PurchaseCallback {
                override fun onCompleted(storeTransaction: StoreTransaction, customerInfo: CustomerInfo) {
                    publishCustomerInfo(customerInfo)
                    onResult(customerInfo.entitlements[ENTITLEMENT_PRO]?.isActive == true, null)
                }

                override fun onError(error: PurchasesError, userCancelled: Boolean) {
                    if (userCancelled || error.code == PurchasesErrorCode.PurchaseCancelledError) {
                        onResult(false, null) // 用户取消不算错误
                    } else {
                        onResult(false, error.message)
                    }
                }
            }
        )
    }

    /**
     * 恢复购买：刷新 CustomerInfo，若曾订阅自动恢复 entitlement。
     *
     * 演示模式（BuildConfig.DEMO_BILLING）下，点"恢复购买"直接本地解锁 Pro
     * （持久化到 DataStore，重启不丢），不发起网络请求。
     */
    fun restorePurchases(onResult: (restored: Boolean, error: String?) -> Unit) {
        if (BuildConfig.DEMO_BILLING) {
            scope.launch {
                settingsRepository.setDemoProUnlocked(true)
                onResult(true, null)
            }
            return
        }
        if (!initialized) {
            onResult(false, context.getString(R.string.billing_not_configured))
            return
        }
        Purchases.sharedInstance.restorePurchases(object : ReceiveCustomerInfoCallback {
            override fun onReceived(customerInfo: CustomerInfo) {
                publishCustomerInfo(customerInfo)
                onResult(customerInfo.entitlements[ENTITLEMENT_PRO]?.isActive == true, null)
            }

            override fun onError(error: PurchasesError) {
                onResult(false, error.message)
            }
        })
    }

    companion object {
        /** Pro entitlement id（RevenueCat 控制台创建，必须与控制台一致）。 */
        const val ENTITLEMENT_PRO = "pro"
    }
}
