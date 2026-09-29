package com.eyeguard.app.feature.paywall

import androidx.lifecycle.ViewModel
import com.eyeguard.app.core.billing.BillingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * 付费墙 ViewModel：仅暴露 BillingRepository 供页面订阅状态。
 */
@HiltViewModel
class PaywallViewModel @Inject constructor(
    val billingRepository: BillingRepository
) : ViewModel()
