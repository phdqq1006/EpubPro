package com.epubpro.feature.bookbible

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.epubpro.core.designsystem.R
import com.epubpro.domain.model.BookBibleProgressSummary
import com.epubpro.domain.model.BookBibleSource
import com.epubpro.domain.model.BookBibleSourceType
import com.epubpro.domain.model.SnapshotStatus
import com.epubpro.domain.model.SubmissionState

/**
 * Hiển thị tab Tiến trình truyện, nơi người dùng duyệt các truyện đã có Book Bible và mở hồ sơ theo mốc chương gần nhất.
 *
 * @param onOpenReview Callback mở danh sách sự kiện cần duyệt của một truyện.
 * @param onOpenBookBible Callback mở Book Bible trực tiếp tại mốc chương gần nhất.
 * @param viewModel ViewModel cung cấp dữ liệu tiến trình từ Room cache.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoryProgressScreen(
    onOpenReview: (bookId: String) -> Unit,
    onOpenBookBible: (source: BookBibleSource, chapterNumber: Int) -> Unit,
    viewModel: StoryProgressViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.story_progress_title),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.story_progress_subtitle),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::retry) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.book_bible_refresh)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                uiState.isLoading && uiState.items.isEmpty() -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                uiState.errorMessage != null && uiState.items.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.story_progress_load_error),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Button(onClick = viewModel::retry) {
                            Text(stringResource(R.string.book_bible_retry))
                        }
                    }
                }
                uiState.items.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Timeline,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = stringResource(R.string.story_progress_empty_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = stringResource(R.string.story_progress_empty_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                else -> {
                    StoryProgressList(
                        items = uiState.items,
                        onOpenReview = onOpenReview,
                        onOpenBookBible = onOpenBookBible
                    )
                }
            }
        }
    }
}

/**
 * Hiển thị danh sách tóm tắt tiến trình Book Bible theo thứ tự cập nhật gần nhất.
 *
 * @param items Các truyện cần hiển thị.
 * @param onOpenReview Callback mở danh sách sự kiện cần duyệt của một truyện.
 * @param onOpenBookBible Callback mở Book Bible trực tiếp.
 */
@Composable
private fun StoryProgressList(
    items: List<BookBibleProgressSummary>,
    onOpenReview: (String) -> Unit,
    onOpenBookBible: (BookBibleSource, Int) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(items, key = { it.source.uniqueKey }) { item ->
            StoryProgressCard(
                item = item,
                onClick = {
                    onOpenReview(item.backendBookId ?: item.source.sourceId)
                },
                onOpenBookBible = {
                    onOpenBookBible(item.source, maxOf(1, item.latestChapterNumber))
                }
            )
        }
    }
}

/**
 * Hiển thị một dòng tiến trình truyện với số sự kiện đang chờ và hành động mở danh sách duyệt.
 *
 * @param item Dữ liệu tóm tắt của truyện.
 * @param onClick Callback khi người dùng chọn dòng truyện.
 * @param onOpenBookBible Callback mở Book Bible trực tiếp.
 */
@Composable
private fun StoryProgressCard(
    item: BookBibleProgressSummary,
    onClick: () -> Unit,
    onOpenBookBible: () -> Unit
) {
    val statusText = when (item.snapshotStatus) {
        SnapshotStatus.COMPLETE -> stringResource(R.string.book_bible_status_complete)
        SnapshotStatus.PARTIAL -> stringResource(R.string.book_bible_status_partial)
        SnapshotStatus.PROCESSING -> stringResource(R.string.book_bible_status_processing)
        SnapshotStatus.EMPTY -> stringResource(R.string.book_bible_status_empty)
        SnapshotStatus.FAILED -> stringResource(R.string.book_bible_status_failed)
        null -> when (item.submissionState) {
            SubmissionState.Pending -> stringResource(R.string.story_progress_submission_pending)
            SubmissionState.Submitting -> stringResource(R.string.story_progress_submission_submitting)
            SubmissionState.Accepted -> stringResource(R.string.story_progress_submission_accepted)
            SubmissionState.Processing -> stringResource(R.string.book_bible_status_processing)
            SubmissionState.Completed -> stringResource(R.string.story_progress_submission_completed)
            is SubmissionState.RetryableFailure -> stringResource(R.string.story_progress_submission_retryable)
            is SubmissionState.PermanentFailure -> stringResource(R.string.story_progress_submission_failed)
            null -> stringResource(R.string.story_progress_status_waiting)
        }
    }
    val statusContainerColor = when (item.snapshotStatus) {
        SnapshotStatus.COMPLETE -> MaterialTheme.colorScheme.primaryContainer
        SnapshotStatus.FAILED -> MaterialTheme.colorScheme.errorContainer
        SnapshotStatus.PROCESSING, SnapshotStatus.PARTIAL -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.secondaryContainer
    }
    val statusContentColor = when (item.snapshotStatus) {
        SnapshotStatus.FAILED -> MaterialTheme.colorScheme.onErrorContainer
        SnapshotStatus.PROCESSING, SnapshotStatus.PARTIAL -> MaterialTheme.colorScheme.onTertiaryContainer
        SnapshotStatus.COMPLETE -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSecondaryContainer
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
            Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(46.dp),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Icon(
                    imageVector = Icons.Outlined.Book,
                    contentDescription = null,
                    modifier = Modifier.padding(11.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title.ifBlank { stringResource(R.string.story_progress_untitled) },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.author.isNotBlank()) {
                    Text(
                        text = stringResource(R.string.story_progress_author_format, item.author),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = when (item.source.type) {
                        BookBibleSourceType.LOCAL_EPUB -> stringResource(R.string.story_progress_source_local)
                        BookBibleSourceType.ONLINE_NOVEL -> stringResource(R.string.story_progress_source_online)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.size(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        color = statusContainerColor,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = statusText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = statusContentColor,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                    Text(
                        text = if (item.latestChapterNumber > 0) {
                            if (item.totalChapters > 0) {
                                stringResource(
                                    R.string.story_progress_chapter_format,
                                    item.latestChapterNumber,
                                    item.totalChapters
                                )
                            } else {
                                stringResource(
                                    R.string.story_progress_latest_chapter_format,
                                    item.latestChapterNumber
                                )
                            }
                        } else {
                            stringResource(R.string.story_progress_not_started)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (item.eventCount > 0) {
                    Spacer(modifier = Modifier.size(6.dp))
                    Text(
                        text = stringResource(
                            R.string.story_progress_pending_events_format,
                            item.pendingEventCount
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (item.pendingEventCount > 0) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onOpenBookBible) {
                    Icon(
                        imageVector = Icons.Outlined.Book,
                        contentDescription = stringResource(R.string.story_progress_open_book_bible),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = stringResource(R.string.story_progress_open_review),
                    tint = Color.Gray,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        }
    }
}
