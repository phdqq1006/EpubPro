package com.epubpro.app.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.epubpro.domain.model.BookBibleSource
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
    openBookRequests: Flow<TtsOpenBookRequest> = emptyFlow(),
    openLibraryRequests: Flow<Unit> = emptyFlow()
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

    LaunchedEffect(navController, openLibraryRequests) {
        openLibraryRequests.collect {
            navController.navigate(Screen.Bookshelf.route) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = false
                }
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
            startDestination = Screen.Bookshelf.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Browse.route) {
                com.epubpro.feature.library.online.OnlineLibraryScreen(
                    onNovelClick = { novelId ->
                        navController.navigate(Screen.OnlineNovelDetail.createRoute(novelId))
                    },
                    onNavigateToServerSettings = { navController.navigate(Screen.Profile.route) }
                )
            }

            composable(
                route = Screen.OnlineNovelDetail.route,
                arguments = listOf(navArgument("novelId") { type = NavType.StringType })
            ) {
                com.epubpro.feature.library.online.OnlineNovelDetailScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onReadChapter = { novelId, chapterIndex ->
                        navController.navigate(Screen.OnlineChapterReader.createRoute(novelId, chapterIndex))
                    }
                )
            }

            composable(
                route = Screen.OnlineChapterReader.route,
                arguments = listOf(
                    navArgument("novelId") { type = NavType.StringType },
                    navArgument("chapterIndex") { type = NavType.StringType }
                )
            ) {
                com.epubpro.feature.library.online.OnlineChapterReaderScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onOpenBookBible = { novelId, chapterNumber ->
                        navController.navigate(Screen.BookBible.createRoute("ONLINE_NOVEL", novelId, chapterNumber))
                    }
                )
            }

            composable(Screen.Bookshelf.route) {
                LibraryScreen(
                    onBookClick = { bookId ->
                        navController.navigate(Screen.Reader.createRoute(bookId))
                    },
                    onNavigateToBookmarks = {
                        navController.navigate(Screen.Bookmark.createRoute("global"))
                    },
                    onNavigateToOnlineLibrary = {
                        navController.navigate(Screen.Browse.route) {
                            launchSingleTop = true
                        }
                    }
                )
            }

            composable(Screen.StoryProgress.route) {
                com.epubpro.feature.bookbible.StoryProgressScreen(
                    onOpenBookBible = { source: BookBibleSource, chapterNumber: Int ->
                        navController.navigate(
                            Screen.BookBible.createRoute(
                                sourceType = source.type.name,
                                sourceId = source.sourceId,
                                chapterNumber = chapterNumber
                            )
                        )
                    }
                )
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
                    onNavigateBack = { navController.popBackStack() },
                    onOpenBookBible = { bookId, chapterNumber ->
                        navController.navigate(Screen.BookBible.createRoute("LOCAL_EPUB", bookId, chapterNumber))
                    }
                )
            }

            composable(
                route = Screen.BookBible.route,
                arguments = listOf(
                    navArgument("sourceType") { type = NavType.StringType },
                    navArgument("sourceId") { type = NavType.StringType },
                    navArgument("chapterNumber") { type = NavType.StringType }
                )
            ) {
                com.epubpro.feature.bookbible.BookBibleScreen(
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

    object BookBible : Screen("book_bible/{sourceType}/{sourceId}/{chapterNumber}") {
        fun createRoute(sourceType: String, sourceId: String, chapterNumber: Int): String {
            return "book_bible/${Uri.encode(sourceType)}/${Uri.encode(sourceId)}/$chapterNumber"
        }
    }

    object Bookmark : Screen("bookmark/{bookId}") {
        fun createRoute(bookId: String) = "bookmark/$bookId"
    }

    object Search : Screen("search/{bookId}") {
        fun createRoute(bookId: String) = "search/$bookId"
    }

    object Browse : Screen("browse")
    object OnlineNovelDetail : Screen("online_novel_detail/{novelId}") {
        fun createRoute(novelId: String) = "online_novel_detail/$novelId"
    }
    object OnlineChapterReader : Screen("online_chapter_reader/{novelId}/{chapterIndex}") {
        fun createRoute(novelId: String, chapterIndex: Int) = "online_chapter_reader/$novelId/$chapterIndex"
    }

    object Bookshelf : Screen("bookshelf")
    object StoryProgress : Screen("story_progress")
    object Profile : Screen("profile")
    object AudioSettings : Screen("audio_settings")
    object ReadingDefaults : Screen("reading_defaults")
    object PageTurnControl : Screen("page_turn_control")
    object ContentFilter : Screen("content_filter")
}
