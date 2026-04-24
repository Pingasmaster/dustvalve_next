package com.dustvalve.next.android.ui.navigation

import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
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
    visibleTabs: List<BottomNavItem>,
    onItemSelected: (NavDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBar(modifier = modifier) {
        visibleTabs.forEach { item ->
            val selected = item == currentTab
            NavigationBarItem(
                selected = selected,
                onClick = { onItemSelected(item.destination) },
                icon = {
                    Icon(
                        painter = painterResource(if (selected) item.selectedIcon else item.unselectedIcon),
                        contentDescription = stringResource(item.labelRes),
                    )
                },
                label = { Text(stringResource(item.labelRes)) },
            )
        }
    }
}

@Composable
fun SideNavRail(
    currentTab: BottomNavItem,
    visibleTabs: List<BottomNavItem>,
    onItemSelected: (NavDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationRail(modifier = modifier) {
        visibleTabs.forEach { item ->
            val selected = item == currentTab
            NavigationRailItem(
                selected = selected,
                onClick = { onItemSelected(item.destination) },
                icon = {
                    Icon(
                        painter = painterResource(if (selected) item.selectedIcon else item.unselectedIcon),
                        contentDescription = stringResource(item.labelRes),
                    )
                },
                label = { Text(stringResource(item.labelRes)) },
            )
        }
    }
}

private val BottomNavItem.selectedIcon: Int
    @DrawableRes get() = when (this) {
        BottomNavItem.LOCAL -> R.drawable.ic_phone_android
        BottomNavItem.BANDCAMP -> R.drawable.ic_cloud
        BottomNavItem.YOUTUBE -> R.drawable.ic_play_circle
        BottomNavItem.LIBRARY -> R.drawable.ic_library_music
        BottomNavItem.SETTINGS -> R.drawable.ic_settings
    }

private val BottomNavItem.unselectedIcon: Int
    @DrawableRes get() = when (this) {
        BottomNavItem.LOCAL -> R.drawable.ic_phone_android_outlined
        BottomNavItem.BANDCAMP -> R.drawable.ic_cloud_outlined
        BottomNavItem.YOUTUBE -> R.drawable.ic_play_circle_outlined
        BottomNavItem.LIBRARY -> R.drawable.ic_library_music_outlined
        BottomNavItem.SETTINGS -> R.drawable.ic_settings_outlined
    }
