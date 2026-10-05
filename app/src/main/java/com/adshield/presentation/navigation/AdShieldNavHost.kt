package com.adshield.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.adshield.presentation.screens.about.AboutScreen
import com.adshield.presentation.screens.activity.ActivityScreen
import com.adshield.presentation.screens.applications.ApplicationsScreen
import com.adshield.presentation.screens.dashboard.DashboardScreen
import com.adshield.presentation.screens.diagnostics.DiagnosticsScreen
import com.adshield.presentation.screens.lists.DomainListScreen
import com.adshield.presentation.screens.protection.ProtectionScreen
import com.adshield.presentation.screens.settings.SettingsScreen
import com.adshield.presentation.screens.splash.SplashScreen
import com.adshield.presentation.screens.statistics.StatisticsScreen
import com.adshield.presentation.viewmodel.DomainListViewModel
import com.adshield.presentation.viewmodel.appViewModel

@Composable
fun AdShieldNavHost() {
    val navController = rememberNavController()
    val back: () -> Unit = { navController.popBackStack() }

    NavHost(navController = navController, startDestination = AppDestination.SPLASH.route) {
        composable(AppDestination.SPLASH.route) {
            SplashScreen(
                onFinished = {
                    navController.navigate(AppDestination.DASHBOARD.route) {
                        popUpTo(AppDestination.SPLASH.route) { inclusive = true }
                    }
                }
            )
        }
        composable(AppDestination.DASHBOARD.route) {
            DashboardScreen(
                onNavigate = { destination ->
                    navController.navigate(destination.route) { launchSingleTop = true }
                }
            )
        }
        composable(AppDestination.STATISTICS.route) { StatisticsScreen(onBack = back) }
        composable(AppDestination.APPLICATIONS.route) { ApplicationsScreen(onBack = back) }
        composable(AppDestination.ACTIVITY.route) { ActivityScreen(onBack = back) }
        composable(AppDestination.BLACKLIST.route) {
            DomainListScreen(
                title = "Lista negra",
                description = "Estos dominios y sus subdominios se bloquean siempre, salvo que también estén en la lista blanca.",
                placeholder = "ads.example.com",
                viewModel = appViewModel(key = "blacklist") {
                    DomainListViewModel(it.blacklistRepository, it.saveBlacklistRule)
                },
                onBack = back
            )
        }
        composable(AppDestination.WHITELIST.route) {
            DomainListScreen(
                title = "Lista blanca",
                description = "Estos dominios y sus subdominios nunca se bloquean. La lista blanca tiene prioridad sobre todas las demás reglas.",
                placeholder = "banco-ejemplo.com",
                viewModel = appViewModel(key = "whitelist") {
                    DomainListViewModel(it.whitelistRepository, it.saveWhitelistRule)
                },
                onBack = back
            )
        }
        composable(AppDestination.PROTECTION.route) { ProtectionScreen(onBack = back) }
        composable(AppDestination.SETTINGS.route) { SettingsScreen(onBack = back) }
        composable(AppDestination.DIAGNOSTICS.route) { DiagnosticsScreen(onBack = back) }
        composable(AppDestination.ABOUT.route) { AboutScreen(onBack = back) }
    }
}
