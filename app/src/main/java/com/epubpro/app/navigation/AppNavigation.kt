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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import android.net.Uri
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import androidx.compose.ui.res.stringResource
import com.epubpro.core.designsystem.R
import com.epubpro.core.reader.tts.TtsOpenBookContract
import com.epubpro.core.reader.tts.TtsOpenBookRequest
import com.epubpro.feature.bookmark.BookmarkScreen
import com.epubpro.feature.library.LibraryScreen
import com.epubpro.feature.profile.ProfileScreen
import com.epubpro.feature.profile.ReadingDefaultsScreen
import com.epubpro.feature.profile.PageTurnControlScreen
import com.epubpro.feature.reader.ReaderScreen
import com.epubpro.feature.search.SearchScreen
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emptyFlow

@Composable
fun AppNavHost(
    navController: NavHostController,
    openBookRequests: Flow<TtsOpenBookRequest> = emptyFlow()
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    LaunchedEffect(navController, openBookRequests) {
        openBookRequests.collect { request ->
            if (navController.currentDestination?.route == Screen.Reader.route) {
                navController.popBackStack()
            }
            navController.navigate(
                Screen.Reader.createRoute(
                    bookId = request.bookId,
                    chapterIndex = request.chapterIndex,
                    openTtsPlayer = request.openTtsPlayer
                )
            ) {
                launchSingleTop = true
            }
        }
    }

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
                                    contentDescription = stringResource(destination.iconTextId)
                                )
                            },
                            label = { Text(stringResource(destination.titleTextId)) }
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
                    Text(stringResource(R.string.coming_soon_suffix, stringResource(R.string.nav_browse)))
                }
            }

            composable(Screen.Bookshelf.route) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.coming_soon_suffix, stringResource(R.string.nav_library)))
                }
            }

            composable(Screen.Notebook.route) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.coming_soon_suffix, stringResource(R.string.nav_notebook)))
                }
            }

            composable(Screen.Profile.route) {
                ProfileScreen(
                    onNavigateToAudioSettings = { navController.navigate(Screen.AudioSettings.route) },
                    onNavigateToReadingDefaults = { navController.navigate(Screen.ReadingDefaults.route) },
                    onNavigateToContentFilter = { navController.navigate(Screen.ContentFilter.route) }
                )
            }

            composable(Screen.ContentFilter.route) {
                com.epubpro.feature.profile.filter.ContentFilterSettingsScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Screen.AudioSettings.route) {
                com.epubpro.feature.profile.audio.AudioSettingsScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Screen.ReadingDefaults.route) {
                ReadingDefaultsScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToPageTurnControl = { navController.navigate(Screen.PageTurnControl.route) }
                )
            }

            composable(Screen.PageTurnControl.route) {
                PageTurnControlScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(
                route = Screen.Reader.route,
                arguments = listOf(
                    navArgument("bookId") { type = NavType.StringType },
                    navArgument(TtsOpenBookContract.NAV_ARGUMENT_CHAPTER_INDEX) {
                        type = NavType.IntType
                        defaultValue = TtsOpenBookContract.NO_CHAPTER_OVERRIDE
                    },
                    navArgument(TtsOpenBookContract.NAV_ARGUMENT_OPEN_TTS_PLAYER) {
                        type = NavType.BoolType
                        defaultValue = false
                    }
                )
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
    object Reader : Screen(
        "reader/{bookId}?" +
            "${TtsOpenBookContract.NAV_ARGUMENT_CHAPTER_INDEX}=" +
            "{${TtsOpenBookContract.NAV_ARGUMENT_CHAPTER_INDEX}}&" +
            "${TtsOpenBookContract.NAV_ARGUMENT_OPEN_TTS_PLAYER}=" +
            "{${TtsOpenBookContract.NAV_ARGUMENT_OPEN_TTS_PLAYER}}"
    ) {
        fun createRoute(
            bookId: String,
            chapterIndex: Int? = null,
            openTtsPlayer: Boolean = false
        ): String {
            val baseRoute = "reader/${Uri.encode(bookId)}"
            if (chapterIndex == null && !openTtsPlayer) return baseRoute

            val safeChapterIndex = chapterIndex
                ?.takeIf { it >= 0 }
                ?: TtsOpenBookContract.NO_CHAPTER_OVERRIDE
            return "$baseRoute?" +
                "${TtsOpenBookContract.NAV_ARGUMENT_CHAPTER_INDEX}=$safeChapterIndex&" +
                "${TtsOpenBookContract.NAV_ARGUMENT_OPEN_TTS_PLAYER}=$openTtsPlayer"
        }
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
    object ReadingDefaults : Screen("reading_defaults")
    object PageTurnControl : Screen("page_turn_control")
    object ContentFilter : Screen("content_filter")
}
