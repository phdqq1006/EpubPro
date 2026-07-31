package com.epubpro.app.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.vector.ImageVector

enum class TopLevelDestination(
    val route: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val iconTextId: String,
    val titleTextId: String
) {
    HOME(
        route = Screen.Library.route,
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home,
        iconTextId = "Trang chủ",
        titleTextId = "Trang chủ"
    ),
    BROWSE(
        route = Screen.Browse.route,
        selectedIcon = Icons.Filled.Search,
        unselectedIcon = Icons.Outlined.Search,
        iconTextId = "Duyệt",
        titleTextId = "Duyệt"
    ),
    LIBRARY(
        route = Screen.Bookshelf.route,
        selectedIcon = Icons.Filled.Book,
        unselectedIcon = Icons.Outlined.Book,
        iconTextId = "Thư viện",
        titleTextId = "Thư viện"
    ),
    NOTEBOOK(
        route = Screen.Notebook.route,
        selectedIcon = Icons.Filled.Edit,
        unselectedIcon = Icons.Outlined.Edit,
        iconTextId = "Sổ tay",
        titleTextId = "Sổ tay"
    ),
    PROFILE(
        route = Screen.Profile.route,
        selectedIcon = Icons.Filled.Person,
        unselectedIcon = Icons.Outlined.Person,
        iconTextId = "Cá nhân",
        titleTextId = "Cá nhân"
    )
}
