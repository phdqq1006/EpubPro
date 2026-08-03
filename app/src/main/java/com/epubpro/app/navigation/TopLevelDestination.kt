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
    HOME(
        route = Screen.Library.route,
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home,
        iconTextId = R.string.nav_home,
        titleTextId = R.string.nav_home
    ),
    BROWSE(
        route = Screen.Browse.route,
        selectedIcon = Icons.Filled.Search,
        unselectedIcon = Icons.Outlined.Search,
        iconTextId = R.string.nav_browse,
        titleTextId = R.string.nav_browse
    ),
    LIBRARY(
        route = Screen.Bookshelf.route,
        selectedIcon = Icons.Filled.Book,
        unselectedIcon = Icons.Outlined.Book,
        iconTextId = R.string.nav_library,
        titleTextId = R.string.nav_library
    ),
    NOTEBOOK(
        route = Screen.Notebook.route,
        selectedIcon = Icons.Filled.Edit,
        unselectedIcon = Icons.Outlined.Edit,
        iconTextId = R.string.nav_notebook,
        titleTextId = R.string.nav_notebook
    ),
    PROFILE(
        route = Screen.Profile.route,
        selectedIcon = Icons.Filled.Person,
        unselectedIcon = Icons.Outlined.Person,
        iconTextId = R.string.nav_profile,
        titleTextId = R.string.nav_profile
    )
}
