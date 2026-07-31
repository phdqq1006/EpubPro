package com.epubpro.app.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.epubpro.feature.bookmark.BookmarkScreen
import com.epubpro.feature.library.LibraryScreen
import com.epubpro.feature.profile.ProfileScreen
import com.epubpro.feature.reader.ReaderScreen
import com.epubpro.feature.search.SearchScreen

@Composable
fun AppNavHost(
    navController: NavHostController
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val topLevelDestinations = TopLevelDestination.values().toList()
    val isTopLevelDestination = topLevelDestinations.any { it.route == currentDestination?.route }

    Scaffold(
        bottomBar = {
            if (isTopLevelDestination) {
                NavigationBar {
                    topLevelDestinations.forEach { destination ->
                        val selected =
                            currentDestination?.hierarchy?.any { it.route == destination.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                                    contentDescription = destination.iconTextId
                                )
                            },
                            label = { Text(destination.titleTextId) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Library.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Library.route) {
                LibraryScreen(
                    onBookClick = { bookId ->
                        navController.navigate(Screen.Reader.createRoute(bookId))
                    },
                    onNavigateToBookmarks = {
                        navController.navigate(Screen.Bookmark.createRoute("global"))
                    },
                    onNavigateToSearch = {
                        navController.navigate(Screen.Search.createRoute("global"))
                    }
                )
            }

            composable(Screen.Browse.route) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Duyệt (Coming Soon)")
                }
            }

            composable(Screen.Bookshelf.route) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Thư viện (Coming Soon)")
                }
            }

            composable(Screen.Notebook.route) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Sổ tay (Coming Soon)")
                }
            }

            composable(Screen.Profile.route) {
                ProfileScreen(
                    onNavigateToAudioSettings = { navController.navigate(Screen.AudioSettings.route) }
                )
            }

            composable(Screen.AudioSettings.route) {
                com.epubpro.feature.profile.audio.AudioSettingsScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(
                route = Screen.Reader.route,
                arguments = listOf(navArgument("bookId") { type = NavType.StringType })
            ) {
                ReaderScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(
                route = Screen.Bookmark.route,
                arguments = listOf(navArgument("bookId") { type = NavType.StringType })
            ) {
                BookmarkScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onJumpToCfi = { chapterIndex, cfi ->
                        navController.popBackStack()
                    }
                )
            }

            composable(
                route = Screen.Search.route,
                arguments = listOf(navArgument("bookId") { type = NavType.StringType })
            ) {
                SearchScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onSearchResultClick = { chapterIndex, snippet ->
                        navController.popBackStack()
                    }
                )
            }
        }
    }
}

sealed class Screen(val route: String) {
    object Library : Screen("library")
    object Reader : Screen("reader/{bookId}") {
        fun createRoute(bookId: String) = "reader/$bookId"
    }

    object Bookmark : Screen("bookmark/{bookId}") {
        fun createRoute(bookId: String) = "bookmark/$bookId"
    }

    object Search : Screen("search/{bookId}") {
        fun createRoute(bookId: String) = "search/$bookId"
    }

    object Browse : Screen("browse")
    object Bookshelf : Screen("bookshelf")
    object Notebook : Screen("notebook")
    object Profile : Screen("profile")
    object AudioSettings : Screen("audio_settings")
}
