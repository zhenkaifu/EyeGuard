package com.eyeguard.app.feature.usage

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.eyeguard.app.core.database.DailyUsageRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 用眼时长监测器（20-20-20 法则）：
 *
 * - 监听 [Intent.ACTION_SCREEN_ON] / [Intent.ACTION_SCREEN_OFF]，维护亮屏状态并
 *   把每次亮屏段落累加进"连续用眼时长"；
 * - 连续用眼达到 [thresholdMinutes]（默认 20 分钟）→ 回调 [onUsageReminder]
 *   提醒"请看 6 米外 20 秒"，并把该次亮屏段落写入 Room 当日累计；
 * - 提醒后进入 20 秒休息窗口：屏幕熄灭、或亮屏但距提醒已满 [REST_DURATION_MS]，
 *   视为完成休息，连续用眼计时清零；若熄屏不足 20 秒又点亮，计时不重置、继续累计。
 *
 * 每日累计亮屏时长按"亮屏段落"粒度写入 Room（亮屏期间每 60 秒也会落库一次，
 * 防止进程被杀导致整段丢失）。
 */
@Singleton
class UsageMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dailyUsageRepository: DailyUsageRepository
) {

    companion object {
        /** 提醒后的休息时长：20 秒。 */
        const val REST_DURATION_MS = 20_000L

        /** 亮屏期间周期性落库的间隔。 */
        private const val PERIODIC_FLUSH_MS = 60_000L
    }

    @Volatile
    var thresholdMinutes: Int = 20

    @Volatile
    var enabled: Boolean = true

    /** 当前是否亮屏（同时供光线监测判断"暗光提醒仅在亮屏时触发"）。 */
    @Volatile
    var screenOn: Boolean = false
        private set

    /** 触发"请看 6 米外 20 秒"提醒回调。 */
    var onUsageReminder: (() -> Unit)? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var screenOnAtMs: Long = 0L
    private var accumulatedMs: Long = 0L
    private var lastFlushAtMs: Long = 0L
    private var awaitingRestSinceMs: Long = 0L

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_ON -> handleScreenOn()
                Intent.ACTION_SCREEN_OFF -> handleScreenOff()
            }
        }
    }

    /** 注册亮灭屏广播并同步当前亮屏状态。 */
    fun start() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        if (pm.isInteractive && !screenOn) handleScreenOn()
    }

    fun stop() {
        runCatching { context.unregisterReceiver(receiver) }
    }

    /**
     * 每轮服务循环调用：检查连续用眼是否达到阈值。
     * 达到 → 触发提醒、落库该段亮屏时长并进入休息窗口。
     */
    fun tick() {
        if (!enabled || !screenOn) return
        val now = System.currentTimeMillis()
        val sessionMs = if (awaitingRestSinceMs > 0) {
            // 提醒后：亮屏也视为"休息远眺"，满 20 秒即重置计时
            if (now - awaitingRestSinceMs >= REST_DURATION_MS) {
                flushAndReset(now)
            }
            0L
        } else {
            accumulatedMs + (now - screenOnAtMs)
        }
        if (awaitingRestSinceMs == 0L && screenOn &&
            sessionMs >= thresholdMinutes * 60_000L
        ) {
            flushSegment(now)
            awaitingRestSinceMs = now
            onUsageReminder?.invoke()
        } else if (awaitingRestSinceMs == 0L && now - lastFlushAtMs >= PERIODIC_FLUSH_MS) {
            flushSegment(now)
        }
    }

    /** 当前连续用眼时长（毫秒），仅供状态展示。 */
    fun currentSessionMs(): Long = if (screenOn) {
        accumulatedMs + (System.currentTimeMillis() - screenOnAtMs)
    } else {
        accumulatedMs
    }

    private fun handleScreenOn() {
        if (screenOn) return
        screenOn = true
        screenOnAtMs = System.currentTimeMillis()
        lastFlushAtMs = screenOnAtMs
    }

    private fun handleScreenOff() {
        if (!screenOn) return
        val now = System.currentTimeMillis()
        flushSegment(now)
        screenOn = false
        // 熄屏时当前连续用眼段落结束，参选"最长连续用眼"
        reportSessionEnd()
        // 熄屏即视为休息开始：若提醒后的休息窗口内熄屏，直接完成休息重置计时
        if (awaitingRestSinceMs > 0) {
            scope.launch { dailyUsageRepository.incrementRestCompletedToday() }
            awaitingRestSinceMs = 0L
            accumulatedMs = 0L
        } else if (accumulatedMs >= thresholdMinutes * 60_000L) {
            accumulatedMs = 0L
        }
    }

    private fun flushSegment(now: Long) {
        if (!screenOn) return
        val delta = now - screenOnAtMs
        if (delta <= 0) return
        screenOnAtMs = now
        accumulatedMs += delta
        lastFlushAtMs = now
        scope.launch { dailyUsageRepository.addScreenTimeToday(delta) }
    }

    /** 休息完成：把当前段落落库并清零连续用眼计时。 */
    private fun flushAndReset(now: Long) {
        flushSegment(now)
        // 提醒后满 20 秒休息窗口：计一次"完成休息"，并参选"最长连续用眼"
        reportSessionEnd()
        scope.launch { dailyUsageRepository.incrementRestCompletedToday() }
        awaitingRestSinceMs = 0L
        accumulatedMs = 0L
    }

    /** 把当前累计连续用眼时长写入"当日最长连续用眼"（仅更长时生效）。 */
    private fun reportSessionEnd() {
        val sessionMs = accumulatedMs
        if (sessionMs > 0) {
            scope.launch { dailyUsageRepository.updateLongestSessionToday(sessionMs) }
        }
    }
}
