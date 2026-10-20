package com.pira.ccloud.navigation

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.pira.ccloud.screens.AboutScreen
import com.pira.ccloud.screens.MoviesScreen
import com.pira.ccloud.screens.SearchScreen
import com.pira.ccloud.screens.SeriesScreen
import com.pira.ccloud.screens.SettingsScreen
import com.pira.ccloud.screens.SingleMovieScreen
import com.pira.ccloud.screens.SingleSeriesScreen
import com.pira.ccloud.screens.SplashScreen
import com.pira.ccloud.screens.FavoritesScreen
import com.pira.ccloud.screens.CountryScreen
import com.pira.ccloud.ui.movies.MoviesViewModel
import com.pira.ccloud.ui.search.SearchViewModel
import com.pira.ccloud.ui.series.SeriesViewModel
import com.pira.ccloud.ui.country.CountryViewModel
import com.pira.ccloud.ui.theme.ThemeSettings
import com.pira.ccloud.ui.theme.ThemeManager
import com.pira.ccloud.data.model.FontSettings // Add this import
import androidx.compose.ui.platform.LocalContext

@Composable
fun AppNavigation(
    navController: NavHostController,
    onThemeSettingsChanged: (ThemeSettings) -> Unit = {},
    onFontSettingsChanged: (FontSettings) -> Unit = {} // Add this parameter
) {
    val context = LocalContext.current
    val themeManager = ThemeManager(context)
    val themeSettings = themeManager.loadThemeSettings()
    
    // Create ViewModels here to preserve their state across navigation
    val moviesViewModel = viewModel<MoviesViewModel>()
    val seriesViewModel = viewModel<SeriesViewModel>()
    val searchViewModel = viewModel<SearchViewModel>()
    val countryViewModel = viewModel<CountryViewModel>()
    val homeViewModel = viewModel<com.pira.ccloud.ui.home.HomeViewModel>()
    
    NavHost(
        navController = navController,
        startDestination = AppScreens.Splash.route
    ) {
        composable(route = AppScreens.Splash.route) {
            val isSystemInDarkMode = isSystemInDarkTheme()
            SplashScreen(
                onTimeout = {
                    navController.popBackStack()
                    navController.navigate(AppScreens.Home.route) {
                        // Prevent re-adding splash to back stack
                        launchSingleTop = true
                    }
                },
                backgroundColor = when (themeSettings.themeMode) {
                    com.pira.ccloud.ui.theme.ThemeMode.DARK -> {
                        androidx.compose.ui.graphics.Color(0xFF121212)
                    }
                    com.pira.ccloud.ui.theme.ThemeMode.LIGHT -> {
                        androidx.compose.ui.graphics.Color(0xFFFFFBFE)
                    }
                    com.pira.ccloud.ui.theme.ThemeMode.SYSTEM -> {
                        if (isSystemInDarkMode) {
                            androidx.compose.ui.graphics.Color(0xFF121212)
                        } else {
                            androidx.compose.ui.graphics.Color(0xFFFFFBFE)
                        }
                    }
                }
            )
        }
        
        composable(route = AppScreens.Home.route) {
            com.pira.ccloud.ui.home.HomeScreen(viewModel = homeViewModel, navController = navController)
        }
        composable(route = AppScreens.Movies.route) {
            MoviesScreen(viewModel = moviesViewModel, navController = navController)
        }
        composable(route = AppScreens.Series.route) {
            SeriesScreen(viewModel = seriesViewModel, navController = navController)
        }
        composable(route = AppScreens.Search.route) {
            SearchScreen(viewModel = searchViewModel, navController = navController)
        }
        composable(route = AppScreens.Settings.route) {
            SettingsScreen(onThemeSettingsChanged, onFontSettingsChanged, navController) // Pass font settings callback
        }
        composable(route = AppScreens.Favorites.route) {
            FavoritesScreen(navController)
        }
        composable(route = AppScreens.About.route) {
            AboutScreen(navController)
        }
        composable(
            route = AppScreens.SingleMovie.route,
            arguments = listOf(navArgument("movieId") { defaultValue = "0" })
        ) { backStackEntry ->            
            val movieId = backStackEntry.arguments?.getString("movieId")?.toIntOrNull() ?: 0
            SingleMovieScreen(movieId = movieId, navController = navController)
        }
        composable(
            route = AppScreens.SingleSeries.route,
            arguments = listOf(navArgument("seriesId") { defaultValue = "0" })
        ) { backStackEntry ->
            val seriesId = backStackEntry.arguments?.getString("seriesId")?.toIntOrNull() ?: 0
            SingleSeriesScreen(seriesId = seriesId, navController = navController)
        }
        composable(
            route = AppScreens.Country.route,
            arguments = listOf(navArgument("countryId") { defaultValue = "0" })
        ) { backStackEntry ->
            val countryId = backStackEntry.arguments?.getString("countryId")?.toIntOrNull() ?: 0
            CountryScreen(countryId = countryId, viewModel = countryViewModel, navController = navController)
        }
    }
}