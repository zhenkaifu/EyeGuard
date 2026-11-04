package com.eyeguard.app.feature.paywall

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.eyeguard.app.R
import com.eyeguard.app.core.billing.BillingRepository
import com.revenuecat.purchases.Package

/**
 * 付费墙页：Compose 自绘，价格从 RevenueCat offerings 实时读取。
 * 月付 / 年付两档 + Pro 功能列表 + 恢复购买。
 *
 * @param onBack 返回上一页
 * @param onPurchased 购买成功后的回调（通常返回上一页）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaywallScreen(
    onBack: () -> Unit,
    onPurchased: () -> Unit = onBack,
    billingRepository: BillingRepository = hiltViewModel<PaywallViewModel>().billingRepository
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val packages by billingRepository.paywallPackages.collectAsState()
    val isPro by billingRepository.isPro.collectAsState()

    var selected by remember { mutableIntStateOf(1) } // 默认选年付
    var purchasing by remember { mutableIntStateOf(0) }

    // 进入页面时已是 Pro 不自动返回（否则"升级到 Pro"入口表现为"点不进去"——
    // 页面刚打开就被 LaunchedEffect 弹回）；只有本次会话内完成购买才回调退出
    val initialPro = remember { billingRepository.isPro.value }

    LaunchedEffect(Unit) { billingRepository.loadOfferings() }
    LaunchedEffect(isPro) { if (isPro && !initialPro) onPurchased() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.paywall_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(stringResource(R.string.paywall_headline), style = MaterialTheme.typography.headlineMedium)
            Text(
                stringResource(R.string.paywall_subtitle),
                style = MaterialTheme.typography.bodyLarge
            )
            ProFeatureList()

            if (isPro) {
                // 已是 Pro：展示解锁状态，不再显示购买按钮
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        stringResource(R.string.paywall_already_pro),
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.titleSmall
                    )
                }
            }

            val monthly = packages?.monthly
            val yearly = packages?.yearly
            if (monthly == null && yearly == null) {
                Text(
                    stringResource(R.string.paywall_loading),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                PackageOption(
                    label = stringResource(R.string.plan_monthly),
                    price = monthly?.product?.price?.formatted ?: stringResource(R.string.price_unknown),
                    selected = selected == 0,
                    onSelect = { selected = 0 },
                    enabled = monthly != null
                )
                PackageOption(
                    label = stringResource(R.string.plan_yearly),
                    price = yearly?.product?.price?.formatted ?: stringResource(R.string.price_unknown),
                    selected = selected == 1,
                    onSelect = { selected = 1 },
                    enabled = yearly != null
                )
            }

            Button(
                onClick = {
                    val target: Package? = if (selected == 0) monthly else yearly
                    if (activity != null && target != null) {
                        purchasing = selected + 1
                        billingRepository.purchase(activity, target) { pro, error ->
                            purchasing = 0
                            error?.let {
                                Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                            }
                            if (pro) onPurchased()
                        }
                    }
                },
                enabled = purchasing == 0 && (if (selected == 0) monthly else yearly) != null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    stringResource(
                        when (purchasing) {
                            1 -> R.string.purchasing_monthly
                            2 -> R.string.purchasing_yearly
                            else -> R.string.subscribe_now
                        }
                    )
                )
            }

            TextButton(
                onClick = {
                    billingRepository.restorePurchases { restored, error ->
                        when {
                            error != null -> Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
                            restored -> Toast.makeText(context, context.getString(R.string.restore_success), Toast.LENGTH_SHORT).show()
                            else -> Toast.makeText(context, context.getString(R.string.restore_none), Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text(stringResource(R.string.restore_purchase))
            }

            Text(
                stringResource(R.string.paywall_footer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ProFeatureList() {
    val features = listOf(
        stringResource(R.string.pro_feature_1),
        stringResource(R.string.pro_feature_2),
        stringResource(R.string.pro_feature_3),
        stringResource(R.string.pro_feature_4)
    )
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            features.forEach { Text("✓ $it", style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

@Composable
private fun PackageOption(
    label: String,
    price: String,
    selected: Boolean,
    onSelect: () -> Unit,
    enabled: Boolean
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, enabled = enabled, onClick = onSelect)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = selected, onClick = onSelect, enabled = enabled)
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleSmall)
            }
            Text(price, style = MaterialTheme.typography.titleMedium)
        }
    }
}
