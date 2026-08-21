package com.epubpro.app.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.vector.ImageVector
import com.epubpro.core.designsystem.R

enum class TopLevelDestination(
    val route: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    @StringRes val iconTextId: Int,
    @StringRes val titleTextId: Int
) {
    LIBRARY(
        route = Screen.Bookshelf.route,
        selectedIcon = Icons.Filled.Book,
        unselectedIcon = Icons.Outlined.Book,
        iconTextId = R.string.nav_library,
        titleTextId = R.string.nav_library
    ),
    BROWSE(
        route = Screen.Browse.route,
        selectedIcon = Icons.Filled.Cloud,
        unselectedIcon = Icons.Outlined.Cloud,
        iconTextId = R.string.nav_browse,
        titleTextId = R.string.nav_browse
    ),
    STORY_PROGRESS(
        route = Screen.StoryProgress.route,
        selectedIcon = Icons.Filled.Timeline,
        unselectedIcon = Icons.Outlined.Timeline,
        iconTextId = R.string.nav_story_progress,
        titleTextId = R.string.nav_story_progress
    ),
    PROFILE(
        route = Screen.Profile.route,
        selectedIcon = Icons.Filled.Person,
        unselectedIcon = Icons.Outlined.Person,
        iconTextId = R.string.nav_profile,
        titleTextId = R.string.nav_profile
    )
}
