package com.eyeguard.app.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.provider.Settings
import android.content.Intent
import android.net.Uri
import com.eyeguard.app.R
import com.eyeguard.app.ui.SettingsViewModel

/**
 * 设置页：距离检测开关、采样间隔、距离阈值（实时写入 SettingsRepository，
 * 前台服务下一轮循环自动生效，无需重启）。
 *
 * @param onBack 返回上一页
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigateToPaywall: () -> Unit = {},
    onNavigateToDnd: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val distanceEnabled by viewModel.distanceAlertEnabled.collectAsStateWithLifecycle()
    val samplingSeconds by viewModel.distanceSamplingIntervalSeconds.collectAsStateWithLifecycle()
    val thresholdCm by viewModel.distanceThresholdCm.collectAsStateWithLifecycle()
    val lightEnabled by viewModel.lightAlertEnabled.collectAsStateWithLifecycle()
    val lightLux by viewModel.lightThresholdLux.collectAsStateWithLifecycle()
    val usageEnabled by viewModel.usageAlertEnabled.collectAsStateWithLifecycle()
    val usageMinutes by viewModel.usageThresholdMinutes.collectAsStateWithLifecycle()
    val filterEnabled by viewModel.filterEnabled.collectAsStateWithLifecycle()
    val filterIntensity by viewModel.filterIntensity.collectAsStateWithLifecycle()
    val filterSmart by viewModel.filterSmartEnabled.collectAsStateWithLifecycle()
    val nightStart by viewModel.nightStartMinutes.collectAsStateWithLifecycle()
    val nightEnd by viewModel.nightEndMinutes.collectAsStateWithLifecycle()
    val isPro by viewModel.isPro.collectAsStateWithLifecycle()
    val appLanguage by viewModel.appLanguage.collectAsStateWithLifecycle()
    val overlayGranted = Settings.canDrawOverlays(LocalContext.current)
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── 语言切换 ─────────────────────────────
            Text(stringResource(R.string.language_section), style = MaterialTheme.typography.titleMedium)

            Card(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("zh" to R.string.language_zh, "en" to R.string.language_en)
                        .forEach { (code, labelRes) ->
                            androidx.compose.material3.FilterChip(
                                selected = appLanguage == code,
                                onClick = {
                                    if (appLanguage != code) {
                                        // 先同步写入内存（recreate 后 attachBaseContext 立即生效），
                                        // 再异步持久化——避免 recreate 时 DataStore 尚未落盘读到旧值
                                        viewModel.setAppLanguage(code)
                                        com.eyeguard.app.ui.LocaleHelper.setLanguage(code, userInitiated = true)
                                        (context as? android.app.Activity)?.recreate()
                                    }
                                },
                                label = { Text(stringResource(labelRes)) }
                            )
                        }
                }
            }

            Text(stringResource(R.string.distance_section), style = MaterialTheme.typography.titleMedium)

            Card(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.distance_alert_switch))
                    Switch(
                        checked = distanceEnabled,
                        onCheckedChange = viewModel::setDistanceAlertEnabled
                    )
                }

                Column(Modifier.padding(horizontal = 16.dp)) {
                    Text(
                        stringResource(R.string.sampling_interval_label, samplingSeconds.toInt()),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Slider(
                        value = samplingSeconds.toFloat(),
                        onValueChange = { viewModel.setDistanceSamplingIntervalSeconds(it.toLong()) },
                        valueRange = 10f..120f,
                        steps = 10,
                        enabled = distanceEnabled && isPro
                    )

                    Text(
                        stringResource(R.string.distance_threshold_label, thresholdCm),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Slider(
                        value = thresholdCm.toFloat(),
                        onValueChange = { viewModel.setDistanceThresholdCm(it.toInt()) },
                        valueRange = 20f..50f,
                        enabled = distanceEnabled && isPro
                    )
                }
            }

            Text(
                stringResource(R.string.distance_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // ── 环境光检测 ─────────────────────────────
            Text(stringResource(R.string.light_section), style = MaterialTheme.typography.titleMedium)

            Card(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.light_alert_switch))
                    Switch(
                        checked = lightEnabled,
                        onCheckedChange = viewModel::setLightAlertEnabled
                    )
                }

                Column(Modifier.padding(horizontal = 16.dp)) {
                    Text(
                        stringResource(R.string.light_threshold_label, lightLux),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Slider(
                        value = lightLux.toFloat(),
                        onValueChange = { viewModel.setLightThresholdLux(it.toInt()) },
                        valueRange = 5f..200f,
                        enabled = lightEnabled && isPro
                    )
                }
            }

            // ── 用眼时长提醒（20-20-20）────────────────
            Text(stringResource(R.string.usage_section), style = MaterialTheme.typography.titleMedium)

            Card(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.usage_alert_switch))
                    Switch(
                        checked = usageEnabled,
                        onCheckedChange = viewModel::setUsageAlertEnabled
                    )
                }

                Column(Modifier.padding(horizontal = 16.dp)) {
                    Text(
                        stringResource(R.string.usage_threshold_label, usageMinutes),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Slider(
                        value = usageMinutes.toFloat(),
                        onValueChange = { viewModel.setUsageThresholdMinutes(it.toInt()) },
                        valueRange = 5f..60f,
                        enabled = usageEnabled && isPro
                    )
                }
            }

            Text(
                stringResource(
                    R.string.usage_hint,
                    if (!isPro) stringResource(R.string.usage_hint_pro_note) else ""
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // 非 Pro 用户点击 Pro 功能的统一入口
            if (!isPro) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onNavigateToPaywall
                ) {
                    Text(
                        stringResource(R.string.pro_gate_card),
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            // ── 护眼遮罩 ─────────────────────────────
            Text(stringResource(R.string.filter_section), style = MaterialTheme.typography.titleMedium)

            Card(Modifier.fillMaxWidth()) {
                // 护眼遮罩为 Pro 功能：免费用户点击跳付费墙
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (!isPro) stringResource(R.string.filter_switch_pro) else stringResource(R.string.filter_section),
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = filterEnabled && isPro,
                        onCheckedChange = { enabled ->
                            if (!isPro) onNavigateToPaywall()
                            else viewModel.setFilterEnabled(enabled)
                        }
                    )
                }

                // 未授权悬浮窗时显示引导状态
                if (!overlayGranted) {
                    androidx.compose.material3.TextButton(
                        onClick = {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                )
                            )
                        },
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        Text(stringResource(R.string.filter_overlay_needed))
                    }
                }

                Column(Modifier.padding(horizontal = 16.dp)) {
                    Text(stringResource(R.string.intensity_label), style = MaterialTheme.typography.bodyMedium)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        listOf(stringResource(R.string.intensity_light), stringResource(R.string.intensity_medium), stringResource(R.string.intensity_deep)).forEachIndexed { index, label ->
                            androidx.compose.material3.FilterChip(
                                selected = filterIntensity == index,
                                onClick = { viewModel.setFilterIntensity(index) },
                                label = { Text(label) },
                                enabled = filterEnabled && isPro
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.smart_mode_switch), modifier = Modifier.weight(1f))
                        Switch(
                            checked = filterSmart,
                            onCheckedChange = viewModel::setFilterSmartEnabled,
                            enabled = filterEnabled && isPro
                        )
                    }

                    if (filterSmart) {
                        // 滑杆标签直接带当前值，避免"看不出滑到几点"
                        Text(stringResource(R.string.night_start_value, formatHour(nightStart)), style = MaterialTheme.typography.bodyMedium)
                        Slider(
                            value = (nightStart / 60).toFloat(),
                            onValueChange = {
                                viewModel.setNightStartMinutes(it.toInt() * 60)
                            },
                            valueRange = 18f..29f, // 18:00 – 次日 05:00
                            enabled = filterEnabled && isPro
                        )
                        Text(stringResource(R.string.night_end_value, formatHour(nightEnd)), style = MaterialTheme.typography.bodyMedium)
                        Slider(
                            value = (nightEnd / 60).toFloat(),
                            onValueChange = {
                                viewModel.setNightEndMinutes(it.toInt() * 60)
                            },
                            valueRange = 4f..12f, // 04:00 – 12:00
                            enabled = filterEnabled && isPro
                        )
                    }
                }
            }

            Text(
                stringResource(R.string.filter_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // ── 免打扰时段（Pro）──────────────────────
            Text(stringResource(R.string.reminder_section), style = MaterialTheme.typography.titleMedium)

            Card(modifier = Modifier.fillMaxWidth(), onClick = {
                if (isPro) onNavigateToDnd() else onNavigateToPaywall()
            }) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(if (!isPro) stringResource(R.string.dnd_entry_pro) else stringResource(R.string.dnd_entry))
                    Text(
                        stringResource(if (isPro) R.string.dnd_go_settings else R.string.dnd_upgrade),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

/** 当天分钟数转本地化 "HH:00" 文案。 */
@Composable
private fun formatHour(minutes: Int): String =
    stringResource(R.string.hour_format, (minutes / 60) % 24)
