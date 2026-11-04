package com.eyeguard.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.eyeguard.app.feature.home.HomeScreen
import com.eyeguard.app.feature.paywall.PaywallScreen
import com.eyeguard.app.feature.settings.SettingsScreen
import com.eyeguard.app.feature.onboarding.OnboardingPermissionsScreen

/**
 * 全局导航宿主，管理首页 / 设置 / 权限引导 / 付费墙四个目的地。
 */
@Composable
fun EyeGuardNavHost(focusRequest: Int = 0) {
    val navController = rememberNavController()

    LaunchedEffect(focusRequest) {
        if (focusRequest > 0) {
            navController.navigate(EyeGuardDestinations.FOCUS) { launchSingleTop = true }
        }
    }

    NavHost(
        navController = navController,
        startDestination = EyeGuardDestinations.HOME
    ) {
        composable(EyeGuardDestinations.HOME) {
            HomeScreen(
                onNavigateToSettings = { navController.navigate(EyeGuardDestinations.SETTINGS) },
                onNavigateToPermissions = {
                    navController.navigate(EyeGuardDestinations.ONBOARDING_PERMISSIONS)
                },
                onNavigateToPaywall = { navController.navigate(EyeGuardDestinations.PAYWALL) },
                onNavigateToDebug = { navController.navigate(EyeGuardDestinations.DEBUG) },
                onNavigateToWeeklyReport = { navController.navigate(EyeGuardDestinations.WEEKLY_REPORT) },
                onNavigateToFocus = { navController.navigate(EyeGuardDestinations.FOCUS) }
            )
        }
        composable(EyeGuardDestinations.FOCUS) {
            com.eyeguard.app.feature.focus.FocusScreen(onBack = { navController.popBackStack() })
        }
        composable(EyeGuardDestinations.WEEKLY_REPORT) {
            com.eyeguard.app.feature.report.WeeklyReportScreen(onBack = { navController.popBackStack() })
        }
        composable(EyeGuardDestinations.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onNavigateToPaywall = { navController.navigate(EyeGuardDestinations.PAYWALL) },
                onNavigateToDnd = { navController.navigate(EyeGuardDestinations.DND) }
            )
        }
        composable(EyeGuardDestinations.DND) {
            com.eyeguard.app.feature.dnd.DndScreen(onBack = { navController.popBackStack() })
        }
        composable(EyeGuardDestinations.ONBOARDING_PERMISSIONS) {
            OnboardingPermissionsScreen(onBack = { navController.popBackStack() })
        }
        composable(EyeGuardDestinations.PAYWALL) {
            PaywallScreen(onBack = { navController.popBackStack() })
        }
        composable(EyeGuardDestinations.DEBUG) {
            com.eyeguard.app.feature.debug.DebugScreen(onBack = { navController.popBackStack() })
        }
    }
}
