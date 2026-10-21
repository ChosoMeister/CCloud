package com.pira.ccloud

import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import com.pira.ccloud.navigation.AppNavigation
import com.pira.ccloud.navigation.AppScreens
import com.pira.ccloud.navigation.BottomNavigationBar
import com.pira.ccloud.navigation.SidebarNavigation
import com.pira.ccloud.ui.theme.CCloudTheme
import com.pira.ccloud.ui.theme.ThemeManager
import com.pira.ccloud.ui.theme.ThemeSettings
import com.pira.ccloud.utils.StorageUtils
import com.pira.ccloud.data.model.FontSettings
import com.pira.ccloud.utils.DeviceUtils

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Set default orientation to portrait for mobile/tablet
        // For TV, we don't set orientation as it's typically fixed
        if (!DeviceUtils.isTv(this)) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        
        // Only use edge-to-edge on supported versions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            enableEdgeToEdge()
        }
        
        setContent {
            MainApp()
        }
    }
}

@Composable
fun MainApp() {
    val themeManager = ThemeManager(androidx.compose.ui.platform.LocalContext.current)
    var themeSettings by remember { mutableStateOf(themeManager.loadThemeSettings()) }
    val context = androidx.compose.ui.platform.LocalContext.current
    var fontSettings by remember { mutableStateOf(StorageUtils.loadFontSettings(context)) }
    
    // Save theme settings when they change
    LaunchedEffect(themeSettings) {
        themeManager.saveThemeSettings(themeSettings)
    }
    
    CCloudTheme(themeSettings, fontSettings) {
        MainScreen(
            onThemeSettingsChanged = { themeSettings = it },
            onFontSettingsChanged = { fontSettings = it }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun MainScreen(
    onThemeSettingsChanged: (ThemeSettings) -> Unit = {},
    onFontSettingsChanged: (FontSettings) -> Unit = {}
) {
    val navController = rememberNavController()
    val themeManager = ThemeManager(androidx.compose.ui.platform.LocalContext.current)
    val themeSettings = themeManager.loadThemeSettings()
    val context = androidx.compose.ui.platform.LocalContext.current
    val fontSettings = StorageUtils.loadFontSettings(context)
    val configuration = LocalConfiguration.current
    val isTv = DeviceUtils.isTv(androidx.compose.ui.platform.LocalContext.current)
    
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route
    
    // Find the current screen, including the SingleMovie and SingleSeries screens
    val currentScreen = when {
        currentRoute?.startsWith("single_movie") == true -> AppScreens.SingleMovie
        currentRoute?.startsWith("single_series") == true -> AppScreens.SingleSeries
        currentRoute?.startsWith("country") == true -> AppScreens.Country
        currentRoute == "favorites" -> AppScreens.Favorites
        currentRoute == "about" -> AppScreens.About
        else -> AppScreens.screens.find { it.route == currentRoute } ?: AppScreens.Movies
    }
    
    var lastExitBack by remember { androidx.compose.runtime.mutableLongStateOf(0L) }
    BackHandler(enabled = isTv && currentRoute != null && currentRoute != AppScreens.Splash.route) {
        val detail = currentRoute?.startsWith("single_") == true || currentRoute?.startsWith("country/") == true || currentRoute == "about"
        when {
            detail && navController.popBackStack() -> Unit
            currentRoute != AppScreens.Home.route -> navController.navigate(AppScreens.Home.route) {
                popUpTo(AppScreens.Home.route) { inclusive = false }
                launchSingleTop = true
                restoreState = true
            }
            else -> {
                val now = android.os.SystemClock.uptimeMillis()
                if (now - lastExitBack < 2000L) (context as? android.app.Activity)?.finish()
                else {
                    lastExitBack = now
                    android.widget.Toast.makeText(context, "Press Back again to exit", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // System UI controller for edge-to-edge support
    val systemUiController = rememberSystemUiController()
    val useDarkIcons = when (themeSettings.themeMode) {
        com.pira.ccloud.ui.theme.ThemeMode.LIGHT -> true
        com.pira.ccloud.ui.theme.ThemeMode.DARK -> false
        com.pira.ccloud.ui.theme.ThemeMode.SYSTEM -> !isSystemInDarkTheme()
    }
    
    LaunchedEffect(themeSettings) {
        systemUiController.setStatusBarColor(
            color = androidx.compose.ui.graphics.Color.Transparent,
            darkIcons = useDarkIcons
        )
        systemUiController.setNavigationBarColor(
            color = androidx.compose.ui.graphics.Color.Transparent,
            darkIcons = useDarkIcons
        )
    }
    
    Scaffold(
        modifier = Modifier.fillMaxSize(),
    ) { innerPadding ->
        val showNav = (isTv || currentScreen.showBottomBar) && currentRoute != AppScreens.Splash.route
        val isHome = currentRoute == AppScreens.Home.route
        val cfg = androidx.compose.ui.platform.LocalConfiguration.current
        val landscape = cfg.screenWidthDp > cfg.screenHeightDp
        if (isTv || landscape) {
            // Side rail (30nama / Filimo pattern): sits beside the content, never over artwork.
            Row(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
                .padding(horizontal = if (isTv) (cfg.screenWidthDp * 0.025f).dp else 0.dp,
                    vertical = if (isTv) (cfg.screenHeightDp * 0.03f).dp else 0.dp)) {
                if (showNav) SidebarNavigation(navController, compact = true)
                Box(
                    modifier = Modifier.weight(1f).fillMaxSize().focusGroup()
                        .then(if (isHome || !showNav) Modifier else Modifier.padding(innerPadding))
                ) {
                    AppNavigation(navController, onThemeSettingsChanged, onFontSettingsChanged)
                }
            }
        } else {
            // Portrait phone: content with a floating pill nav overlaid at the bottom.
            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = if (isHome) Modifier.fillMaxSize()
                    else Modifier.fillMaxSize().padding(innerPadding).padding(bottom = if (showNav) 84.dp else 0.dp)
                ) {
                    AppNavigation(navController, onThemeSettingsChanged, onFontSettingsChanged)
                }
                if (showNav) {
                    BottomNavigationBar(
                        navController,
                        Modifier.align(androidx.compose.ui.Alignment.BottomCenter).navigationBarsPadding(),
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    val themeManager = ThemeManager(androidx.compose.ui.platform.LocalContext.current)
    val themeSettings = themeManager.loadThemeSettings()
    
    CCloudTheme(themeSettings) {
        MainScreen()
    }
}
