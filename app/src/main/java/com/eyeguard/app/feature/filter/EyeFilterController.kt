package com.eyeguard.app.feature.filter

import android.content.Context
import android.graphics.PixelFormat
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 护眼遮罩控制器：通过 WindowManager 添加全屏半透明暖色 View（TYPE_APPLICATION_OVERLAY）。
 *
 * 触摸穿透：布局参数带 [WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE] 与
 * [WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE]，遮罩不接收、不拦截任何触摸事件，
 * 不影响用户正常操作其他 App。
 *
 * 已知系统限制（不可避免，属系统行为而非 Bug）：
 * 1. 截图 / 录屏会带暖色调——遮罩是真实绘制在屏幕合成层之上的窗口；
 * 2. 部分银行 / 支付类 App 的防截屏安全页面（FLAG_SECURE）会阻止系统
 *    绘制非系统窗口，进入这些页面时遮罩会被系统强制隐藏，离开后恢复；
 * 3. 需要用户授予"悬浮窗"（SYSTEM_ALERT_WINDOW）权限。
 *
 * 强度三档：浅 / 中 / 深，对应不同橙色与透明度（见 [INTENSITY_COLORS] / [INTENSITY_ALPHAS]），
 * 智能模式下由 [applySmartMask] 计算实际参数。
 */
@Singleton
class EyeFilterController @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        /** 三档强度对应的暖色（ARGB）：浅、中、深。 */
        val INTENSITY_COLORS = intArrayOf(
            0xFFF5A623.toInt(),  // 浅：琥珀色
            0xFFFF9800.toInt(),  // 中：橙色
            0xFFFF6D00.toInt()   // 深：深橙
        )

        /** 三档强度对应的透明度（0–255）。 */
        val INTENSITY_ALPHAS = intArrayOf(38, 77, 128)

        /** 智能模式下环境光下限 / 上限（lux），低于下限用最暖，高于上限不加深。 */
        const val SMART_LUX_LOW = 10f
        const val SMART_LUX_HIGH = 150f
    }

    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    /** 当前遮罩 View；null 表示未显示。 */
    private var overlayView: View? = null

    /** 是否已授权悬浮窗。 */
    fun hasOverlayPermission(): Boolean = Settings.canDrawOverlays(context)

    /**
     * 显示或更新遮罩；未授权 / 参数无效时静默失败。
     *
     * @param color 暖色 ARGB
     * @param alpha 透明度 0–255
     */
    @Synchronized
    fun show(color: Int, alpha: Int) {
        if (!hasOverlayPermission()) return
        val safeAlpha = alpha.coerceIn(0, 255)
        val view = overlayView
        if (view == null) {
            val newView = View(context)
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                // 触摸完全穿透 + 不抢焦点
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            )
            newView.setBackgroundColor(applyAlpha(color, safeAlpha))
            runCatching { windowManager.addView(newView, params) }
            overlayView = newView
        } else {
            view.setBackgroundColor(applyAlpha(color, safeAlpha))
        }
    }

    /** 隐藏遮罩（View 移除并置空）。 */
    @Synchronized
    fun hide() {
        val view = overlayView ?: return
        overlayView = null
        runCatching { windowManager.removeView(view) }
    }

    /**
     * 计算智能模式参数：环境越暗颜色越暖，夜间时段自动在基础档上加深一档。
     *
     * @param baseIntensity 用户选择的基础档位（0–2）
     * @param lux 当前环境光读数（无传感器时为负，按上限处理）
     * @param isNight 当前是否处于夜间时段
     */
    fun computeSmartMask(baseIntensity: Int, lux: Float, isNight: Boolean): Pair<Int, Int> {
        var level = baseIntensity
        var alphaBoost = 0

        // 环境光暗 → 加深一档 + 提高透明度
        if (lux in 0f..SMART_LUX_LOW) {
            level = (level + 1).coerceAtMost(2)
            alphaBoost = 30
        } else if (lux in SMART_LUX_LOW..SMART_LUX_HIGH) {
            alphaBoost = 15
        }

        // 夜间时段 → 再加深一档
        if (isNight) {
            level = (level + 1).coerceAtMost(2)
            alphaBoost += 20
        }

        return INTENSITY_COLORS[level] to
            (INTENSITY_ALPHAS[level] + alphaBoost).coerceAtMost(200)
    }

    private fun applyAlpha(color: Int, alpha: Int): Int = (color and 0x00FFFFFF) or (alpha shl 24)

    /** 判断当前分钟数是否落在 [startMinutes, endMinutes] 夜间时段（支持跨零点）。 */
    fun isNightTime(nowMinutes: Int, startMinutes: Int, endMinutes: Int): Boolean {
        val now = nowMinutes % (24 * 60)
        return if (startMinutes <= endMinutes) {
            now in startMinutes until endMinutes
        } else {
            now >= startMinutes || now < endMinutes
        }
    }
}
