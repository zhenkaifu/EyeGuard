package com.eyeguard.app.feature.debug

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eyeguard.app.core.notification.Notifier
import com.eyeguard.app.feature.distance.DistanceSampler
import com.eyeguard.app.feature.light.LightMonitor
import android.content.Context
import com.eyeguard.app.R
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 调试面板 ViewModel：一键触发各提醒 / 立即采样测距 / 读取传感器与服务状态，
 * 让每项功能不用等采样周期即可秒级验证。
 */
@HiltViewModel
class DebugViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    val notifier: Notifier,
    private val distanceSampler: DistanceSampler,
    private val lightMonitor: LightMonitor
) : ViewModel() {

    /** 采样结果文案："约 32cm" / "未检测到人脸" / "相机不可用" / null=空闲。 */
    private val _sampleResult = MutableStateFlow<String?>(null)
    val sampleResult: StateFlow<String?> = _sampleResult

    /** 采样中标记。 */
    private val _sampling = MutableStateFlow(false)
    val sampling: StateFlow<Boolean> = _sampling

    /** 触发距离过近提醒（模拟服务判定后的通知，含震动）。 */
    fun triggerDistanceReminder() {
        notifier.sendEyeReminder(
            title = context.getString(R.string.notif_too_close_title),
            content = context.getString(R.string.debug_notif_too_close_content),
            vibrate = true,
        )
    }

    /** 触发暗光提醒。 */
    fun triggerDarkReminder() {
        notifier.sendEyeReminder(
            title = context.getString(R.string.notif_dark_title),
            content = context.getString(R.string.debug_notif_dark_content),
            vibrate = true,
        )
    }

    /** 触发用眼休息提醒。 */
    fun triggerUsageReminder() {
        notifier.sendEyeReminder(
            title = context.getString(R.string.debug_notif_usage_title),
            content = context.getString(R.string.debug_notif_usage_content),
            vibrate = true,
        )
    }

    /** 当前环境光读数文案。 */
    fun currentLuxText(): String {
        val lux = lightMonitor.currentLux
        return if (lux < 0) context.getString(R.string.debug_lux_none)
        else context.getString(R.string.debug_lux_format, lux)
    }

    /**
     * 立即执行一次距离采样并返回估算结果。
     * 用一个临时的 RESUMED LifecycleOwner 满足 CameraX 绑定要求。
     */
    fun sampleNow() {
        if (_sampling.value) return
        _sampling.value = true
        _sampleResult.value = context.getString(R.string.sample_result_sampling)
        viewModelScope.launch {
            val owner = object : LifecycleOwner {
                private val registry = LifecycleRegistry(this).apply {
                    handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
                    handleLifecycleEvent(Lifecycle.Event.ON_START)
                    handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
                }
                override val lifecycle: Lifecycle get() = registry
            }
            val result = distanceSampler.sampleOnce(owner)
            _sampleResult.value = when (result) {
                is DistanceSampler.SampleResult.Unavailable -> context.getString(R.string.debug_sampling_result_unavailable)
                is DistanceSampler.SampleResult.Success -> when {
                    result.distanceCm != null -> context.getString(R.string.debug_sampling_result_ok, result.distanceCm)
                    else -> context.getString(R.string.debug_sampling_result_none)
                }
            }
            _sampling.value = false
        }
    }
}
