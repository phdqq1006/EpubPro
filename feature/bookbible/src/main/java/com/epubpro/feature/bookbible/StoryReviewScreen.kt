package com.epubpro.feature.bookbible

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.epubpro.core.designsystem.R
import com.epubpro.domain.model.BookBibleReviewEvent
import com.epubpro.domain.model.BookBibleReviewEventEdit
import kotlinx.coroutines.launch

/**
 * Hiển thị hàng đợi event tiến trình của một cuốn sách.
 *
 * @param onNavigateBack Callback quay lại danh sách sách.
 * @param viewModel ViewModel tải và xử lý các event review.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoryReviewScreen(
    onNavigateBack: () -> Unit,
    viewModel: StoryReviewViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var editingEvent by remember { mutableStateOf<BookBibleReviewEvent?>(null) }
    var showApproveAllDialog by remember { mutableStateOf(false) }
    val messageText = uiState.message?.let { message ->
        when (message) {
            StoryReviewMessage.APPROVED -> stringResource(R.string.story_review_action_approved)
            StoryReviewMessage.REJECTED -> stringResource(R.string.story_review_action_rejected)
            StoryReviewMessage.UPDATED -> stringResource(R.string.story_review_action_updated)
            StoryReviewMessage.APPROVED_ALL -> stringResource(R.string.story_review_action_approved_all)
        }
    }

    LaunchedEffect(messageText) {
        if (messageText != null) {
            coroutineScope.launch { snackbarHostState.showSnackbar(messageText) }
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.story_review_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.story_review_back)
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showApproveAllDialog = true },
                        enabled = uiState.events.isNotEmpty() && !uiState.isApprovingAll
                    ) {
                        Icon(
                            imageVector = Icons.Default.DoneAll,
                            contentDescription = stringResource(R.string.story_review_approve_all)
                        )
                    }
                    IconButton(onClick = viewModel::retry, enabled = !uiState.isLoading) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.book_bible_refresh)
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                uiState.isLoading && uiState.events.isEmpty() -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                uiState.errorMessage != null && uiState.events.isEmpty() -> {
                    StoryReviewError(onRetry = viewModel::retry)
                }
                uiState.events.isEmpty() -> {
                    StoryReviewEmptyState()
                }
                else -> {
                    StoryReviewList(
                        events = uiState.events,
                        busyEventIds = uiState.busyEventIds,
                        onApprove = viewModel::approve,
                        onReject = viewModel::reject,
                        onEdit = { editingEvent = it }
                    )
                }
            }
            if (uiState.isApprovingAll) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 12.dp)
                        .size(28.dp)
                )
            }
        }
    }

    editingEvent?.let { event ->
        StoryReviewEditDialog(
            event = event,
            onDismiss = { editingEvent = null },
            onConfirm = { edit ->
                viewModel.update(event, edit)
                editingEvent = null
            }
        )
    }

    if (showApproveAllDialog) {
        AlertDialog(
            onDismissRequest = { showApproveAllDialog = false },
            title = { Text(stringResource(R.string.story_review_approve_all_title)) },
            text = { Text(stringResource(R.string.story_review_approve_all_desc)) },
            confirmButton = {
                Button(
                    onClick = {
                        showApproveAllDialog = false
                        viewModel.approveAll()
                    }
                ) {
                    Text(stringResource(R.string.story_review_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showApproveAllDialog = false }) {
                    Text(stringResource(R.string.story_review_cancel))
                }
            }
        )
    }
}

/**
 * Hiển thị trạng thái lỗi khi không thể tải hàng đợi duyệt.
 *
 * @param onRetry Callback thử tải lại.
 */
@Composable
private fun StoryReviewError(onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.story_review_load_error),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error,
            fontWeight = FontWeight.SemiBold
        )
        Button(onClick = onRetry) {
            Text(stringResource(R.string.book_bible_retry))
        }
    }
}

/**
 * Hiển thị trạng thái không còn event pending.
 */
@Composable
private fun StoryReviewEmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.story_review_empty_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = stringResource(R.string.story_review_empty_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Hiển thị danh sách các event cần duyệt.
 *
 * @param events Các event pending.
 * @param busyEventIds Các event đang xử lý request.
 * @param onApprove Callback duyệt event.
 * @param onReject Callback từ chối event.
 * @param onEdit Callback mở hộp thoại chỉnh sửa.
 */
@Composable
private fun StoryReviewList(
    events: List<BookBibleReviewEvent>,
    busyEventIds: Set<String>,
    onApprove: (BookBibleReviewEvent) -> Unit,
    onReject: (BookBibleReviewEvent) -> Unit,
    onEdit: (BookBibleReviewEvent) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(events, key = { it.eventId }) { event ->
            StoryReviewEventCard(
                event = event,
                isBusy = event.eventId in busyEventIds,
                onApprove = { onApprove(event) },
                onReject = { onReject(event) },
                onEdit = { onEdit(event) }
            )
        }
    }
}

/**
 * Hiển thị chi tiết một event cùng ba thao tác duyệt, sửa và từ chối.
 *
 * @param event Event cần hiển thị.
 * @param isBusy Cờ event đang được cập nhật.
 * @param onApprove Callback duyệt.
 * @param onReject Callback từ chối.
 * @param onEdit Callback sửa event.
 */
@Composable
private fun StoryReviewEventCard(
    event: BookBibleReviewEvent,
    isBusy: Boolean,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onEdit: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = event.characterOriginalName.ifBlank { event.characterId },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.story_review_chapter_format, event.canonicalChapter),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = stringResource(R.string.story_review_category_format, event.category),
                style = MaterialTheme.typography.bodySmall
            )
            if (event.attributeKey.isNotBlank()) {
                Text(
                    text = stringResource(R.string.story_review_attribute_format, event.attributeKey),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Text(
                text = stringResource(R.string.story_review_operation_format, event.operation),
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(10.dp))
            ReviewField(
                label = stringResource(R.string.story_review_value_label),
                value = event.displayValue ?: event.valueJson
                    ?: stringResource(R.string.story_review_no_value)
            )
            ReviewField(
                label = stringResource(R.string.story_review_evidence_label),
                value = event.evidence?.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.story_review_no_evidence)
            )
            event.confidence?.let { confidence ->
                Text(
                    text = stringResource(R.string.story_review_confidence_format, confidence),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalButton(onClick = onApprove, enabled = !isBusy) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(modifier = Modifier.size(4.dp))
                    Text(stringResource(R.string.story_review_approve))
                }
                OutlinedButton(onClick = onEdit, enabled = !isBusy) {
                    Icon(Icons.Default.Edit, contentDescription = null)
                    Spacer(modifier = Modifier.size(4.dp))
                    Text(stringResource(R.string.story_review_edit))
                }
                IconButton(onClick = onReject, enabled = !isBusy) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.story_review_reject),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
                if (isBusy) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

/**
 * Hiển thị một trường thông tin của event.
 *
 * @param label Nhãn trường.
 * @param value Giá trị trường.
 */
@Composable
private fun ReviewField(label: String, value: String) {
    Column(modifier = Modifier.padding(bottom = 6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Hộp thoại chỉnh sửa value, evidence và confidence của event.
 *
 * @param event Event đang chỉnh sửa.
 * @param onDismiss Callback đóng hộp thoại.
 * @param onConfirm Callback lưu dữ liệu mới.
 */
@Composable
private fun StoryReviewEditDialog(
    event: BookBibleReviewEvent,
    onDismiss: () -> Unit,
    onConfirm: (BookBibleReviewEventEdit) -> Unit
) {
    var valueText by remember(event.eventId) {
        mutableStateOf(event.valueJson ?: event.displayValue.orEmpty())
    }
    var evidenceText by remember(event.eventId) { mutableStateOf(event.evidence.orEmpty()) }
    var confidenceText by remember(event.eventId) {
        mutableStateOf(event.confidence?.toString().orEmpty())
    }
    val confidence = confidenceText.toDoubleOrNull()
    val confidenceValid = confidenceText.isBlank() || (confidence != null && confidence in 0.0..1.0)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.story_review_edit_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = valueText,
                    onValueChange = { valueText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.story_review_value_json_label)) },
                    placeholder = { Text(stringResource(R.string.story_review_value_hint)) },
                    minLines = 2
                )
                OutlinedTextField(
                    value = evidenceText,
                    onValueChange = { evidenceText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.story_review_evidence_input_label)) },
                    placeholder = { Text(stringResource(R.string.story_review_evidence_hint)) },
                    minLines = 3
                )
                OutlinedTextField(
                    value = confidenceText,
                    onValueChange = { confidenceText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.story_review_confidence_input_label)) },
                    placeholder = { Text(stringResource(R.string.story_review_confidence_hint)) },
                    singleLine = true,
                    isError = !confidenceValid
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        BookBibleReviewEventEdit(
                            valueJson = valueText.ifBlank { null },
                            evidence = evidenceText.ifBlank { null },
                            confidence = confidence
                        )
                    )
                },
                enabled = confidenceValid
            ) {
                Text(stringResource(R.string.story_review_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.story_review_cancel))
            }
        }
    )
}
