package com.eyeguard.app.feature.onboarding

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.eyeguard.app.R
import com.eyeguard.app.core.notification.Notifier
import com.eyeguard.app.core.service.EyeGuardService
import com.eyeguard.app.ui.SettingsViewModel

/**
 * 权限引导页：依次引导用户授予距离检测所需的全部权限。
 *
 * 授权顺序：通知（Android 13+）→ 相机 → 通知渠道检查（可选，跳到系统通知设置）。
 * 全部就绪后可一键启动守护服务。国产 ROM 上还需用户在系统设置里手动
 * 允许"自启动"并把省电策略设为"无限制"，页面内提供跳转入口说明。
 *
 * @param onBack 返回上一页
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingPermissionsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    var refreshTick by remember { mutableStateOf(0) }

    fun checkPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    val notificationGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        checkPermission(Manifest.permission.POST_NOTIFICATIONS)
    val cameraGranted = checkPermission(Manifest.permission.CAMERA)
    val overlayGranted = Settings.canDrawOverlays(context)

    // 横幅（Heads-up）权限检测：渠道重要性为 HIGH(4) 才允许弹屏；
    // 荣耀/小米的"静默通知"开关会把渠道降级为 DEFAULT(3) 且不自动恢复
    val bannerGranted = run {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.getNotificationChannel(Notifier.CHANNEL_ID_REMINDER)?.importance ==
            NotificationManager.IMPORTANCE_HIGH
    }

    // 电池优化白名单：国产 ROM 默认冻结后台应用，导致前台服务被杀、提醒失效
    val batteryOptimized = run {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    // 系统"请求忽略电池优化"弹窗（REQUEST_IGNORE_BATTERY_OPTIMIZATIONS）返回后刷新
    val batteryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { refreshTick++ }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refreshTick++ }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refreshTick++ }

    // 强制重组以刷新权限状态
    @Suppress("UNUSED_EXPRESSION") refreshTick

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.permission_title)) },
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
            Text(
                stringResource(R.string.permission_intro),
                style = MaterialTheme.typography.bodyLarge
            )

            PermissionRow(
                title = stringResource(R.string.perm_notification_title),
                description = stringResource(R.string.perm_notification_desc),
                granted = notificationGranted,
                buttonText = stringResource(if (notificationGranted) R.string.granted else R.string.go_grant),
                enabled = !notificationGranted,
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            )

            PermissionRow(
                title = stringResource(R.string.perm_camera_title),
                description = stringResource(R.string.perm_camera_desc),
                granted = cameraGranted,
                buttonText = stringResource(if (cameraGranted) R.string.granted else R.string.go_grant),
                enabled = !cameraGranted,
                onClick = { cameraLauncher.launch(Manifest.permission.CAMERA) }
            )

            PermissionRow(
                title = stringResource(R.string.perm_banner_title),
                description = stringResource(R.string.perm_banner_desc),
                granted = bannerGranted,
                buttonText = stringResource(if (bannerGranted) R.string.granted_on else R.string.go_fix),
                enabled = true, // 始终可点：用户改完设置后回来刷新即可看到"已开启"
                onClick = {
                    // 深链到"护眼提醒"渠道的设置页（比 App 通知总页更直接）
                    runCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                putExtra(Settings.EXTRA_CHANNEL_ID, Notifier.CHANNEL_ID_REMINDER)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                        )
                    }.onFailure {
                        context.startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                        )
                    }
                }
            )

            PermissionRow(
                title = stringResource(R.string.perm_overlay_title),
                description = stringResource(R.string.perm_overlay_desc),
                granted = overlayGranted,
                buttonText = stringResource(if (overlayGranted) R.string.granted else R.string.go_grant),
                enabled = !overlayGranted,
                onClick = {
                    // 悬浮窗权限无法运行时申请，只能跳系统设置页引导用户开启
                    context.startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            )

            PermissionRow(
                title = stringResource(R.string.perm_dnd_title),
                description = stringResource(R.string.perm_dnd_desc),
                granted = false,
                buttonText = stringResource(R.string.go_settings),
                enabled = true,
                onClick = {
                    // 跳勿扰例外设置页（荣耀 / 华为在"允许例外"中添加应用）
                    runCatching {
                        context.startActivity(
                            Intent("android.settings.ZEN_MODE_PRIORITY_SETTINGS")
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }.onFailure {
                        context.startActivity(
                            Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }
            )

            PermissionRow(
                title = stringResource(R.string.perm_battery_title),
                description = stringResource(R.string.perm_battery_desc),
                granted = batteryOptimized,
                buttonText = stringResource(if (batteryOptimized) R.string.granted_on else R.string.go_grant),
                enabled = !batteryOptimized,
                onClick = {
                    // 荣耀 / 部分国产 ROM 会静默拦截该 Action：先探测可解析性，
                    // 逐级回退到电池优化总列表 / 应用详情页
                    val request = Intent(
                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:${context.packageName}")
                    )
                    if (request.resolveActivity(context.packageManager) != null) {
                        runCatching { batteryLauncher.launch(request) }.onFailure {
                            context.startActivity(
                                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    } else {
                        runCatching {
                            context.startActivity(
                                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }.onFailure {
                            context.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", context.packageName, null)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                            )
                        }
                    }
                }
            )

            Text(
                stringResource(R.string.perm_rom_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            TextButton(
                onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    )
                }
            ) {
                Text(stringResource(R.string.open_app_settings))
            }

            Button(
                onClick = {
                    viewModel.setDistanceAlertEnabled(true)
                    viewModel.setGuardEnabled(true)
                    EyeGuardService.start(context)
                    (context as? Activity)?.finish()
                },
                enabled = cameraGranted,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(if (cameraGranted) R.string.start_guard else R.string.grant_camera_first))
            }
        }
    }
}

@Composable
private fun PermissionRow(
    title: String,
    description: String,
    granted: Boolean,
    buttonText: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    androidx.compose.material3.Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(description, style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onClick, enabled = enabled) { Text(buttonText) }
        }
    }
}
