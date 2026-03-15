package com.dustvalve.next.android.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Settings
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
        BottomNavItem.HOME -> Icons.Rounded.Home
        BottomNavItem.LIBRARY -> Icons.Rounded.LibraryMusic
        BottomNavItem.SETTINGS -> Icons.Rounded.Settings
    }

private val BottomNavItem.unselectedIcon: ImageVector
    get() = when (this) {
        BottomNavItem.HOME -> Icons.Rounded.Home
        BottomNavItem.LIBRARY -> Icons.Rounded.LibraryMusic
        BottomNavItem.SETTINGS -> Icons.Rounded.Settings
    }
