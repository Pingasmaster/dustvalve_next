package com.dustvalve.next.android.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

@Composable
fun BottomNavBar(
    currentTab: BottomNavItem,
    onItemSelected: (NavDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBar(modifier = modifier) {
        BottomNavItem.entries.forEach { item ->
            val selected = item == currentTab
            NavigationBarItem(
                selected = selected,
                onClick = { onItemSelected(item.destination) },
                icon = {
                    Icon(
                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                        contentDescription = item.label,
                    )
                },
                label = { Text(item.label) },
            )
        }
    }
}

@Composable
fun SideNavRail(
    currentTab: BottomNavItem,
    onItemSelected: (NavDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationRail(modifier = modifier) {
        BottomNavItem.entries.forEach { item ->
            val selected = item == currentTab
            NavigationRailItem(
                selected = selected,
                onClick = { onItemSelected(item.destination) },
                icon = {
                    Icon(
                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                        contentDescription = item.label,
                    )
                },
                label = { Text(item.label) },
            )
        }
    }
}

private val BottomNavItem.selectedIcon: ImageVector
    get() = when (this) {
        BottomNavItem.HOME -> Icons.Filled.Home
        BottomNavItem.LIBRARY -> Icons.Filled.LibraryMusic
        BottomNavItem.SETTINGS -> Icons.Filled.Settings
    }

private val BottomNavItem.unselectedIcon: ImageVector
    get() = when (this) {
        BottomNavItem.HOME -> Icons.Outlined.Home
        BottomNavItem.LIBRARY -> Icons.Outlined.LibraryMusic
        BottomNavItem.SETTINGS -> Icons.Outlined.Settings
    }
