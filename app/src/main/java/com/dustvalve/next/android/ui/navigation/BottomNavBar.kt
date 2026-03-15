package com.dustvalve.next.android.ui.navigation

import androidx.compose.ui.res.painterResource
import com.dustvalve.next.android.R
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.annotation.DrawableRes

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
                        painter = painterResource(if (selected) item.selectedIcon else item.unselectedIcon),
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
                        painter = painterResource(if (selected) item.selectedIcon else item.unselectedIcon),
                        contentDescription = item.label,
                    )
                },
                label = { Text(item.label) },
            )
        }
    }
}

private val BottomNavItem.selectedIcon: Int
    @DrawableRes get() = when (this) {
        BottomNavItem.HOME -> R.drawable.ic_home
        BottomNavItem.LIBRARY -> R.drawable.ic_library_music
        BottomNavItem.SETTINGS -> R.drawable.ic_settings
    }

private val BottomNavItem.unselectedIcon: Int
    @DrawableRes get() = when (this) {
        BottomNavItem.HOME -> R.drawable.ic_home
        BottomNavItem.LIBRARY -> R.drawable.ic_library_music
        BottomNavItem.SETTINGS -> R.drawable.ic_settings
    }
