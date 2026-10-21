package com.pira.ccloud.navigation

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import com.pira.ccloud.core.designsystem.liquidGlassSurface

/**
 * Cinematic side rail (30nama / Filimo / Namava pattern) for TV and landscape:
 * a slim dark column beside the content, so artwork is never covered.
 * D-pad friendly: every item has a single focus target and a clear white focus pill.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun SidebarNavigation(navController: NavController, modifier: Modifier = Modifier, compact: Boolean = false) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val screens = AppScreens.screens.filter { it.showSidebar }
    val requesters = remember { screens.associate { it.route to FocusRequester() } }
    var pendingRoute by rememberSaveable { mutableStateOf<String?>(null) }
    var activeSection by rememberSaveable { mutableStateOf(AppScreens.Home.route) }
    LaunchedEffect(currentRoute) {
        if (currentRoute in requesters) activeSection = currentRoute!!
        if (pendingRoute == currentRoute) {
            requesters[currentRoute]?.requestFocus()
            pendingRoute = null
        }
    }
    Column(
        modifier
            .focusProperties { enter = { requesters[currentRoute] ?: requesters[activeSection] ?: FocusRequester.Default } }
            .focusGroup()
            .fillMaxHeight()
            .width(if (compact) 76.dp else 96.dp)
            .liquidGlassSurface(RoundedCornerShape(0.dp), MaterialTheme.colorScheme.surface.copy(alpha = 0.96f))
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 12.dp, Alignment.CenterVertically),
    ) {
        screens.forEach { screen ->
            val selected = currentRoute == screen.route || (currentRoute !in requesters && activeSection == screen.route)
            val interaction = remember { MutableInteractionSource() }
            val focused by interaction.collectIsFocusedAsState()
            val bg = when {
                focused -> MaterialTheme.colorScheme.primary
                selected -> MaterialTheme.colorScheme.primaryContainer
                else -> Color.Transparent
            }
            val fg = when {
                focused -> MaterialTheme.colorScheme.onPrimary
                selected -> MaterialTheme.colorScheme.onPrimaryContainer
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            Column(
                Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(bg)
                    .then(if (focused) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp)) else Modifier)
                    .focusRequester(requesters.getValue(screen.route))
                    .clickable(interactionSource = interaction, indication = null) {
                        if (currentRoute != screen.route) {
                            pendingRoute = screen.route
                            navController.navigate(screen.route) {
                                launchSingleTop = true
                                restoreState = true
                                popUpTo(AppScreens.Home.route) { saveState = true }
                            }
                        }
                    }
                    .padding(horizontal = 8.dp, vertical = if (compact) 8.dp else 10.dp)
                    .width(if (compact) 56.dp else 72.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(screen.icon ?: Icons.Default.Movie, stringResource(screen.resourceId), tint = fg,
                        modifier = Modifier.size(if (compact) 24.dp else 28.dp))
                }
                Spacer(Modifier.height(4.dp))
                Text(stringResource(screen.resourceId), color = fg, fontSize = if (compact) 10.sp else 12.sp,
                    fontWeight = if (selected || focused) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
            }
        }
    }
}
