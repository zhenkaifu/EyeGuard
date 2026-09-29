package com.eyeguard.app.feature.dnd

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eyeguard.app.R
import com.eyeguard.app.ui.SettingsViewModel

/**
 * 免打扰时段设置页（Pro 功能）：时段内所有护眼提醒静音
 *（距离 / 暗光 / 用眼休息提醒均不再弹出，常驻守护与数据记录不受影响）。
 *
 * @param onBack 返回上一页
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DndScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val dndEnabled by viewModel.dndEnabled.collectAsStateWithLifecycle()
    val dndStart by viewModel.dndStartMinutes.collectAsStateWithLifecycle()
    val dndEnd by viewModel.dndEndMinutes.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.dnd_title)) },
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.dnd_switch))
                    Switch(
                        checked = dndEnabled,
                        onCheckedChange = viewModel::setDndEnabled
                    )
                }

                Column(Modifier.padding(horizontal = 16.dp)) {
                    Text(
                        stringResource(
                            R.string.dnd_period_label,
                            formatHour(dndStart),
                            formatHour(dndEnd)
                        ),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(stringResource(R.string.night_start_label), style = MaterialTheme.typography.labelSmall)
                    Slider(
                        value = (dndStart / 60).toFloat(),
                        onValueChange = { viewModel.setDndStartMinutes(it.toInt() * 60) },
                        valueRange = 18f..29f,
                        enabled = dndEnabled
                    )
                    Text(stringResource(R.string.night_end_label), style = MaterialTheme.typography.labelSmall)
                    Slider(
                        value = (dndEnd / 60).toFloat(),
                        onValueChange = { viewModel.setDndEndMinutes(it.toInt() * 60) },
                        valueRange = 4f..12f,
                        enabled = dndEnabled
                    )
                }
            }

            Text(
                stringResource(R.string.dnd_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 当天分钟数转本地化 "HH:00" 文案。 */
@Composable
private fun formatHour(minutes: Int): String =
    stringResource(R.string.hour_format, (minutes / 60) % 24)
