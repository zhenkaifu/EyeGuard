package com.eyeguard.app.feature.focus

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class LearningApp(val packageName: String, val label: String)

/** 仅读取用户授权的前台应用事件，不读取屏幕内容。 */
@Singleton
class LearningAppManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var lastEventTime = 0L
    private var lastForegroundPackage: String? = null

    fun installedApps(): List<LearningApp> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            .mapNotNull { info ->
                val packageName = info.activityInfo?.packageName ?: return@mapNotNull null
                if (packageName == context.packageName) return@mapNotNull null
                LearningApp(packageName, info.loadLabel(context.packageManager).toString())
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun usageSettingsIntent(): Intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)

    @Synchronized
    fun currentForegroundPackage(now: Long = System.currentTimeMillis()): String? {
        if (!hasUsageAccess()) return null
        val usage = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val from = if (lastEventTime == 0L) now - 15_000L else lastEventTime + 1L
        val events = usage.queryEvents(from, now)
        var foreground = lastForegroundPackage
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                foreground = event.packageName
            }
            lastEventTime = maxOf(lastEventTime, event.timeStamp)
        }
        lastForegroundPackage = foreground
        return foreground
    }

    fun isSystemOverlay(packageName: String): Boolean = packageName in setOf(
        "com.android.systemui",
        "com.android.settings",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller",
        "com.android.dialer",
        "com.google.android.dialer"
    )
}
