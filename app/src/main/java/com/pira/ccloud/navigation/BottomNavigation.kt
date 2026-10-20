package com.pira.ccloud.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState

private val PillShape = RoundedCornerShape(32.dp)
private val ItemShape = RoundedCornerShape(26.dp)

/** Floating translucent pill navigation, overlaid on content (see MainActivity). */
@Composable
fun BottomNavigationBar(navController: NavController, modifier: Modifier = Modifier) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Row(
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .fillMaxWidth()
            .clip(PillShape)
            .background(Color(0xE61B1C22))
            .border(1.dp, Color.White.copy(alpha = 0.10f), PillShape)
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppScreens.screens.filter { it.showBottomBar }.forEach { screen ->
            val isSelected = currentRoute == screen.route
            val bg by animateColorAsState(
                if (isSelected) Color.White.copy(alpha = 0.16f) else Color.Transparent, tween(200), label = "bg")
            val fg by animateColorAsState(
                if (isSelected) Color.White else Color.White.copy(alpha = 0.6f), tween(200), label = "fg")
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .clip(ItemShape)
                    .background(bg)
                    .selectable(selected = isSelected, role = Role.Tab) {
                        if (currentRoute != screen.route) {
                            navController.navigate(screen.route) {
                                launchSingleTop = true
                                restoreState = true
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            }
                        }
                    }
                    .padding(vertical = 8.dp),
            ) {
                Icon(
                    imageVector = screen.icon ?: Icons.Default.Movie,
                    contentDescription = null,
                    tint = fg,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(screen.resourceId),
                    color = fg,
                    fontSize = 11.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                )
            }
        }
    }
}