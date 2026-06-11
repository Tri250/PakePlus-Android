package com.digiguide.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.digiguide.ui.feature.home.HomeScreen
import com.digiguide.ui.feature.verify.VerifyScreen
import com.digiguide.ui.feature.report.ReportScreen
import com.digiguide.ui.feature.discover.DiscoverScreen
import com.digiguide.ui.feature.profile.ProfileScreen

/**
 * 主导航路由
 */
sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Verify : Screen("verify")
    object Report : Screen("report")
    object Discover : Screen("discover")
    object Profile : Screen("profile")

    // 子路由
    object SNQuery : Screen("verify/sn_query")
    object SNResult : Screen("verify/sn_result")
    object BatteryUpload : Screen("verify/battery_upload")
    object BatteryResult : Screen("verify/battery_result")
}

/**
 * 主导航组件
 */
@Composable
fun MainNavigation(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToVerify = { navController.navigate(Screen.Verify.route) },
                onNavigateToReport = { navController.navigate(Screen.Report.route) },
                onNavigateToDiscover = { navController.navigate(Screen.Discover.route) },
                onNavigateToProfile = { navController.navigate(Screen.Profile.route) }
            )
        }

        composable(Screen.Verify.route) {
            VerifyScreen(
                onNavigateToSNQuery = { navController.navigate(Screen.SNQuery.route) },
                onNavigateToBatteryUpload = { navController.navigate(Screen.BatteryUpload.route) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.SNQuery.route) {
            SNQueryScreen(
                onNavigateToResult = { navController.navigate(Screen.SNResult.route) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.SNResult.route) {
            SNResultScreen(
                onBack = { navController.popBackStack(Screen.SNQuery.route, inclusive = false) }
            )
        }

        composable(Screen.BatteryUpload.route) {
            BatteryUploadScreen(
                onNavigateToResult = { navController.navigate(Screen.BatteryResult.route) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.BatteryResult.route) {
            BatteryResultScreen(
                onBack = { navController.popBackStack(Screen.BatteryUpload.route, inclusive = false) }
            )
        }

        composable(Screen.Report.route) {
            ReportScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Discover.route) {
            DiscoverScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Profile.route) {
            ProfileScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}