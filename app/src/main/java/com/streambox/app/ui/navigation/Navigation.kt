package com.streambox.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.streambox.app.ui.screens.ExtensionsScreen
import com.streambox.app.ui.screens.HomeScreen
import com.streambox.app.ui.screens.InfoScreen
import com.streambox.app.ui.screens.PlayerScreen
import com.streambox.app.ui.screens.SearchScreen
import com.streambox.app.ui.screens.SettingsScreen
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Search : Screen("search")
    object Settings : Screen("settings")
    object Extensions : Screen("extensions")
    object Info : Screen("info/{link}?provider={provider}") {
        fun createRoute(link: String, provider: String? = null): String {
            val encodedLink = URLEncoder.encode(link, StandardCharsets.UTF_8.toString())
            return if (provider != null) {
                "info/$encodedLink?provider=$provider"
            } else {
                "info/$encodedLink"
            }
        }
    }
    object Player : Screen("player/{streamUrl}?title={title}") {
        fun createRoute(streamUrl: String, title: String? = null): String {
            val encodedUrl = URLEncoder.encode(streamUrl, StandardCharsets.UTF_8.toString())
            val encodedTitle = title?.let { URLEncoder.encode(it, StandardCharsets.UTF_8.toString()) } ?: ""
            return "player/$encodedUrl?title=$encodedTitle"
        }
    }
}

@Composable
fun StreamBoxNavHost(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToInfo = { link, provider ->
                    navController.navigate(Screen.Info.createRoute(link, provider))
                },
                onNavigateToSearch = {
                    navController.navigate(Screen.Search.route)
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                },
                onNavigateToExtensions = {
                    navController.navigate(Screen.Extensions.route)
                }
            )
        }
        
        composable(Screen.Search.route) {
            SearchScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToInfo = { link, provider ->
                    navController.navigate(Screen.Info.createRoute(link, provider))
                }
            )
        }
        
        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToExtensions = {
                    navController.navigate(Screen.Extensions.route)
                }
            )
        }
        
        composable(Screen.Extensions.route) {
            ExtensionsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        composable(
            route = Screen.Info.route,
            arguments = listOf(
                navArgument("link") { type = NavType.StringType },
                navArgument("provider") { 
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val encodedLink = backStackEntry.arguments?.getString("link") ?: ""
            val link = URLDecoder.decode(encodedLink, StandardCharsets.UTF_8.toString())
            val provider = backStackEntry.arguments?.getString("provider")
            
            InfoScreen(
                link = link,
                provider = provider,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToPlayer = { streamUrl, title ->
                    navController.navigate(Screen.Player.createRoute(streamUrl, title))
                }
            )
        }
        
        composable(
            route = Screen.Player.route,
            arguments = listOf(
                navArgument("streamUrl") { type = NavType.StringType },
                navArgument("title") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val encodedUrl = backStackEntry.arguments?.getString("streamUrl") ?: ""
            val streamUrl = URLDecoder.decode(encodedUrl, StandardCharsets.UTF_8.toString())
            val encodedTitle = backStackEntry.arguments?.getString("title")
            val title = encodedTitle?.let { URLDecoder.decode(it, StandardCharsets.UTF_8.toString()) }
            
            PlayerScreen(
                streamUrl = streamUrl,
                title = title,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
