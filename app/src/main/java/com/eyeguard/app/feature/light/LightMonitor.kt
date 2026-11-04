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
 * 只负责维护最新 lux 读数，不自行触发提醒。
 *
 * 提醒判定由服务主循环按采样周期执行（与距离检测同节奏）：
 * 每轮检查一次"当前 lux 低于阈值且亮屏"，过暗即提醒——
 * 避免事件驱动 + 10 分钟节流导致的"提醒一次后长时间没动静"。
 *
 * 无光线传感器的设备（部分低端机 / 模拟器）start 返回 false，服务降级跳过。
 */
@Singleton
class LightMonitor @Inject constructor(
    @ApplicationContext context: Context
) {

    @Volatile
    var thresholdLux: Int = 50

    @Volatile
    var enabled: Boolean = true

    /** 最近一次光照读数（lux），尚未读到时为负。 */
    @Volatile
    var currentLux: Float = -1f
        private set

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val lightSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            currentLux = event.values[0]
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

    /** 当前是否"过暗"：有读数、低于阈值。亮屏与否由服务侧判断。 */
    fun isTooDark(): Boolean = currentLux >= 0f && currentLux < thresholdLux

    fun stop() {
        sensorManager.unregisterListener(listener)
    }
}
