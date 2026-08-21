package com.epubpro.feature.library

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.epubpro.core.designsystem.R
import com.epubpro.feature.library.components.GeneratedBookCover

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onBookClick: (bookId: String) -> Unit,
    onNavigateToBookmarks: () -> Unit,
    onNavigateToOnlineLibrary: () -> Unit,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbarHostState = remember { SnackbarHostState() }
    var showAddBookBottomSheet by rememberSaveable { mutableStateOf(false) }
    var pendingDeleteBookId by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(viewModel, lifecycleOwner, snackbarHostState) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.events.collect { message ->
                Log.d("TAG", "LibraryScreen: "+context.getString(
                    message.textRes,
                    *message.formatArgs.toTypedArray()
                ))
                snackbarHostState.showSnackbar(
                    context.getString(
                        message.textRes,
                        *message.formatArgs.toTypedArray()
                    )
                )
            }
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.importEpub(it, null) }
    }

    val uploadPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.uploadEpubToServer(it, null) }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        uploadPickerLauncher.launch("application/epub+zip")
    }

    val onStartUpload = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            uploadPickerLauncher.launch("application/epub+zip")
        }
    }

    if (showAddBookBottomSheet) {
        AddBookBottomSheet(
            onDismissRequest = { showAddBookBottomSheet = false },
            onNavigateToOnlineLibrary = onNavigateToOnlineLibrary,
            onPickLocalEpub = { filePickerLauncher.launch("application/epub+zip") },
            onUploadEpubToServer = onStartUpload
        )
    }

    pendingDeleteBookId?.let { bookId ->
            uiState.books.firstOrNull { it.book.id == bookId }?.let { item ->
                AlertDialog(
                    onDismissRequest = { pendingDeleteBookId = null },
                    title = {
                        Text(
                            text = stringResource(R.string.library_delete_confirm_title),
                            fontWeight = FontWeight.Bold
                        )
                    },
                    text = {
                        Text(
                            text = stringResource(
                                R.string.library_delete_confirm_message,
                                item.book.title
                            )
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                pendingDeleteBookId = null
                                viewModel.deleteBook(item)
                            }
                        ) {
                            Text(
                                text = stringResource(R.string.library_delete_book),
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { pendingDeleteBookId = null }) {
                            Text(stringResource(R.string.action_cancel))
                        }
                    }
                )
            }
        }

    uiState.uploadJobStatus?.let { status ->
        AlertDialog(
            onDismissRequest = {
                viewModel.dismissUploadDialog()
            },
            title = {
                Text(
                    text = stringResource(R.string.epub_import_dialog_title),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = status.title ?: stringResource(R.string.tts_default_book_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    val progress = (status.progressPercentage / 100f).coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = status.currentStep?.let { it.substringAfter("): ", it) }
                            ?: stringResource(R.string.epub_import_notification_starting),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val errorMsg = status.errorMessage
                    if (status.isFailed && !errorMsg.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = errorMsg,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                if (status.isFailed || status.isCompleted) {
                    TextButton(onClick = { viewModel.dismissUploadDialog() }) {
                        Text(stringResource(R.string.action_close))
                    }
                } else {
                    TextButton(onClick = { viewModel.dismissUploadDialog() }) {
                        Text(stringResource(R.string.action_background))
                    }
                }
            },
            dismissButton = {
                if (!status.isFailed && !status.isCompleted) {
                    TextButton(onClick = { viewModel.cancelUploadWork() }) {
                        Text(stringResource(R.string.action_cancel), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.app_name),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.semantics { heading() }
                    )
                },
                actions = {
                    IconButton(onClick = onNavigateToOnlineLibrary) {
                        Icon(Icons.Default.CloudDownload, contentDescription = stringResource(R.string.add_book_online))
                    }
                    IconButton(onClick = onNavigateToBookmarks) {
                        Icon(Icons.Default.Bookmark, contentDescription = stringResource(R.string.library_bookmarks_highlights))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddBookBottomSheet = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.library_add_book))
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = viewModel::onSearchQueryChanged,
                placeholder = { Text(stringResource(R.string.library_search_placeholder)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 80.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = stringResource(R.string.library_loading),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
                uiState.totalBookCount == 0 -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 80.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.AutoMirrored.Filled.MenuBook,
                                contentDescription = null,
                                modifier = Modifier.size(72.dp),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = stringResource(R.string.library_empty_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.semantics { heading() }
                            )
                            Text(
                                text = stringResource(R.string.library_empty_subtitle),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                uiState.books.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 80.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.SearchOff,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = stringResource(
                                    R.string.library_search_empty_title,
                                    uiState.searchQuery
                                ),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.semantics { heading() }
                            )
                            Text(
                                text = stringResource(R.string.library_search_empty_subtitle),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 144.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(bottom = 88.dp, top = 8.dp)
                    ) {
                        items(uiState.books, key = { it.book.id }) { item ->
                            BookCard(
                                item = item,
                                onClick = { onBookClick(item.book.id) },
                                onDelete = { pendingDeleteBookId = item.book.id }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BookCard(
    item: BookItemUiState,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            ) {
                GeneratedBookCover(
                    title = item.book.title,
                    author = item.book.author
                )
                // Smooth top gradient scrim for icon legibility without ugly background shapes
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .background(
                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                colors = listOf(
                                    androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.25f),
                                    androidx.compose.ui.graphics.Color.Transparent
                                )
                            )
                        )
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                ) {
                    IconButton(
                        onClick = { showMenu = true }
                    ) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = stringResource(
                                R.string.library_book_menu_format,
                                item.book.title
                            ),
                            tint = androidx.compose.ui.graphics.Color.White
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.library_delete_book), color = MaterialTheme.colorScheme.error) },
                            onClick = {
                                showMenu = false
                                onDelete()
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        )
                    }
                }
            }

            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = item.book.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                    maxLines = 2,
                    minLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = item.book.author,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    minLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(10.dp))

                val progress = item.progressPercentage.coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )

                Spacer(modifier = Modifier.height(6.dp))

                val progressText = when {
                    item.currentChapter > 0 && item.totalChapters > 0 -> stringResource(
                        R.string.library_progress_format,
                        item.currentChapter,
                        item.totalChapters,
                        (progress * 100).toInt()
                    )
                    item.totalChapters > 0 -> stringResource(
                        R.string.library_progress_format,
                        0,
                        item.totalChapters,
                        0
                    )
                    else -> stringResource(R.string.library_not_started)
                }

                Text(
                    text = progressText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (item.currentChapter > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
