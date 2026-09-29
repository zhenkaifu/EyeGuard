package com.eyeguard.app.feature.light

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 环境光监测器：以 SensorManager 常开监听 TYPE_LIGHT（低功耗，微瓦级），
 * 事件驱动判断，不做轮询。
 *
 * 判定：当前 lux 低于 [thresholdLux] 且屏幕亮屏时，回调 [onTooDark]。
 * 由服务层做 10 分钟不重复提醒的节流（见 [lastAlertAtMs]）。
 *
 * 无光线传感器的设备（部分低端机 / 模拟器）onStart 返回 false，服务降级跳过。
 */
@Singleton
class LightMonitor @Inject constructor(
    @ApplicationContext context: Context
) {

    companion object {
        /** 同一"环境光线太暗"提醒的最小间隔（10 分钟）。 */
        const val ALERT_THROTTLE_MS = 10 * 60 * 1000L
    }

    @Volatile
    var thresholdLux: Int = 50

    @Volatile
    var enabled: Boolean = true

    /** 上次触发提醒的时间戳，服务据此节流。 */
    @Volatile
    var lastAlertAtMs: Long = 0L

    /** 最近一次光照读数（lux），尚未读到时为负。 */
    @Volatile
    var currentLux: Float = -1f
        private set

    /** 由服务注入：当前是否亮屏（由 UsageMonitor 维护）。 */
    @Volatile
    var isScreenOnProvider: (() -> Boolean)? = null

    /** 触发"环境光线太暗"判定回调。 */
    var onTooDark: (() -> Unit)? = null

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val lightSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val lux = event.values[0]
            currentLux = lux
            if (!enabled) return
            val now = System.currentTimeMillis()
            val throttled = now - lastAlertAtMs < ALERT_THROTTLE_MS
            if (lux < thresholdLux && isScreenOnProvider?.invoke() != false && !throttled) {
                lastAlertAtMs = now
                onTooDark?.invoke()
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    /**
     * 注册光线传感器监听。
     *
     * @return 设备是否具备光线传感器
     */
    fun start(): Boolean {
        val sensor = lightSensor ?: return false
        sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        return true
    }

    fun stop() {
        sensorManager.unregisterListener(listener)
    }
}
