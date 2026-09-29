package com.eyeguard.app.feature.debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.eyeguard.app.R
import com.eyeguard.app.core.service.EyeGuardService

/**
 * 调试面板：开发 / 评审时逐项秒级验证功能，不用等 30 秒采样周期。
 *
 * - 三个按钮分别触发距离 / 暗光 / 用眼提醒（验证横幅 + 震动）
 * - "立即采样一次"验证相机 + ML Kit 测距链路，直接显示估算距离
 * - 显示当前环境光读数与服务运行状态
 *
 * 发布版本可隐藏本页入口。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugScreen(
    onBack: () -> Unit,
    viewModel: DebugViewModel = hiltViewModel()
) {
    val sampleResult by viewModel.sampleResult.collectAsState()
    val sampling by viewModel.sampling.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.debug_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                stringResource(R.string.debug_reminder_test),
                style = MaterialTheme.typography.titleMedium
            )
            RowOfButtons(
                first = {
                    Button(onClick = viewModel::triggerDistanceReminder) {
                        Text(stringResource(R.string.debug_trigger_distance))
                    }
                },
                second = {
                    OutlinedButton(onClick = viewModel::triggerDarkReminder) {
                        Text(stringResource(R.string.debug_trigger_dark))
                    }
                }
            )
            Button(
                onClick = viewModel::triggerUsageReminder,
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.debug_trigger_usage)) }

            Text(
                stringResource(R.string.debug_sensor_section),
                style = MaterialTheme.typography.titleMedium
            )
            Button(
                onClick = viewModel::sampleNow,
                enabled = !sampling,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (sampling) stringResource(R.string.debug_sampling)
                    else stringResource(R.string.debug_sample_now)
                )
            }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        stringResource(
                            R.string.debug_result_label,
                            sampleResult ?: stringResource(R.string.debug_result_idle)
                        )
                    )
                    Text(stringResource(R.string.debug_lux_label, viewModel.currentLuxText()))
                    Text(
                        stringResource(
                            if (EyeGuardService.isRunning) R.string.debug_service_running
                            else R.string.debug_service_stopped
                        )
                    )
                }
            }

            Text(
                stringResource(R.string.debug_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RowOfButtons(first: @Composable () -> Unit, second: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        androidx.compose.foundation.layout.Box(Modifier.weight(1f)) { first() }
        androidx.compose.foundation.layout.Box(Modifier.weight(1f)) { second() }
    }
}
