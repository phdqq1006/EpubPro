package com.epubpro.feature.bookbible

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.epubpro.core.designsystem.R
import com.epubpro.domain.model.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookBibleScreen(
    onNavigateBack: () -> Unit,
    viewModel: BookBibleViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // Xử lý nút Back hệ thống: nếu đang xem chi tiết nhân vật thì quay lại danh sách
    BackHandler(enabled = uiState.selectedCharacterId != null) {
        viewModel.selectCharacter(null)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = uiState.selectedCharacter?.name ?: stringResource(R.string.book_bible_title),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = stringResource(R.string.book_bible_current_chapter_pill, uiState.chapterNumber),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (uiState.selectedCharacterId != null) {
                                viewModel.selectCharacter(null)
                            } else {
                                onNavigateBack()
                            }
                        }
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refreshSnapshot) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.book_bible_refresh)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                uiState.isLoading && uiState.snapshot == null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
                uiState.errorMessage != null && uiState.snapshot == null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Text(
                                text = uiState.errorMessage ?: stringResource(R.string.book_bible_error_loading),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = viewModel::refreshSnapshot) {
                                Text(stringResource(R.string.book_bible_retry))
                            }
                        }
                    }
                }
                uiState.selectedCharacterId != null -> {
                    val character = uiState.selectedCharacter
                    if (character != null) {
                        CharacterDetailContent(
                            character = character,
                            selectedTab = uiState.selectedTab,
                            onTabSelected = viewModel::selectTab,
                            timeline = uiState.selectedTimeline,
                            isLoadingTimeline = uiState.isLoadingTimeline
                        )
                    }
                }
                else -> {
                    CharacterListContent(
                        uiState = uiState,
                        onCharacterClick = { viewModel.selectCharacter(it.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CharacterListContent(
    uiState: BookBibleUiState,
    onCharacterClick: (CharacterProfile) -> Unit
) {
    val snapshot = uiState.snapshot
    Column(modifier = Modifier.fillMaxSize()) {
        // Status & Coverage Banner
        if (snapshot != null) {
            StatusCoverageBanner(snapshot = snapshot, isPolling = uiState.isPolling)
        }

        if (uiState.sortedCharacters.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Outlined.PersonSearch,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.book_bible_status_empty),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.book_bible_empty_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(uiState.sortedCharacters, key = { it.id }) { character ->
                    CharacterCard(
                        character = character,
                        onClick = { onCharacterClick(character) }
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusCoverageBanner(
    snapshot: BookBibleSnapshot,
    isPolling: Boolean
) {
    Surface(
        color = when (snapshot.status) {
            SnapshotStatus.COMPLETE -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            SnapshotStatus.PARTIAL -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
            SnapshotStatus.PROCESSING -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
            else -> MaterialTheme.colorScheme.surfaceVariant
        },
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = when (snapshot.status) {
                        SnapshotStatus.COMPLETE -> Icons.Default.CheckCircle
                        SnapshotStatus.PARTIAL -> Icons.Default.WarningAmber
                        SnapshotStatus.PROCESSING -> Icons.Default.Sync
                        else -> Icons.Default.Info
                    },
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when (snapshot.status) {
                        SnapshotStatus.COMPLETE -> stringResource(R.string.book_bible_status_complete)
                        SnapshotStatus.PARTIAL -> stringResource(R.string.book_bible_status_partial)
                        SnapshotStatus.PROCESSING -> stringResource(R.string.book_bible_status_processing)
                        SnapshotStatus.EMPTY -> stringResource(R.string.book_bible_status_empty)
                        SnapshotStatus.FAILED -> stringResource(R.string.book_bible_error_loading)
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }

            if (snapshot.coverage.processedRanges.isNotEmpty() || snapshot.coverage.missingRanges.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                val processedStr = snapshot.coverage.processedRanges.joinToString(", ") { "c.${it.start}-${it.end}" }
                val missingStr = if (snapshot.coverage.missingRanges.isNotEmpty()) {
                    snapshot.coverage.missingRanges.joinToString(", ") { "c.${it.start}-${it.end}" }
                } else "Không có"

                Text(
                    text = stringResource(R.string.book_bible_coverage_format, processedStr, missingStr),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (isPolling) {
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(CircleShape)
                )
            }
        }
    }
}

@Composable
private fun CharacterCard(
    character: CharacterProfile,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Avatar Circle with initial
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = character.name.firstOrNull()?.uppercase() ?: "?",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = character.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val origName = character.originalName
                    if (!origName.isNullOrBlank()) {
                        Text(
                            text = origName,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            if (character.changedInCurrentChapter) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = stringResource(R.string.book_bible_changed_badge),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            val realm = character.cultivationRealm
            if (!realm.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = realm,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (character.affiliations.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = character.affiliations.joinToString(", "),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun CharacterDetailContent(
    character: CharacterProfile,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    timeline: CharacterTimeline?,
    isLoadingTimeline: Boolean
) {
    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.background
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { onTabSelected(0) },
                text = { Text(stringResource(R.string.book_bible_tab_profile), fontWeight = FontWeight.SemiBold) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { onTabSelected(1) },
                text = { Text(stringResource(R.string.book_bible_tab_timeline), fontWeight = FontWeight.SemiBold) }
            )
        }

        if (selectedTab == 0) {
            CharacterProfileTab(character = character)
        } else {
            CharacterTimelineTab(
                timeline = timeline,
                isLoading = isLoadingTimeline
            )
        }
    }
}

@Composable
private fun CharacterProfileTab(character: CharacterProfile) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Cảnh giới tu vi
        val realm = character.cultivationRealm
        if (!realm.isNullOrBlank()) {
            ProfileSectionCard(
                title = stringResource(R.string.book_bible_section_cultivation),
                icon = Icons.Outlined.Bolt
            ) {
                Text(
                    text = realm,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Công pháp
        if (character.techniques.isNotEmpty()) {
            ProfileSectionCard(
                title = stringResource(R.string.book_bible_section_techniques),
                icon = Icons.Outlined.AutoStories
            ) {
                FlowChips(items = character.techniques)
            }
        }

        // Kỹ năng
        if (character.skills.isNotEmpty()) {
            ProfileSectionCard(
                title = stringResource(R.string.book_bible_section_skills),
                icon = Icons.Outlined.Psychology
            ) {
                FlowChips(items = character.skills)
            }
        }

        // Trang bị & Pháp bảo
        if (character.items.isNotEmpty()) {
            ProfileSectionCard(
                title = stringResource(R.string.book_bible_section_items),
                icon = Icons.Outlined.Shield
            ) {
                FlowChips(items = character.items)
            }
        }

        // Quan hệ nhân vật
        if (character.relationships.isNotEmpty()) {
            ProfileSectionCard(
                title = stringResource(R.string.book_bible_section_relationships),
                icon = Icons.Outlined.People
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    character.relationships.forEach { rel ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = rel.targetName,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = rel.relationType,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            val relDesc = rel.description
                            if (!relDesc.isNullOrBlank()) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = relDesc,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // Thế lực
        if (character.affiliations.isNotEmpty()) {
            ProfileSectionCard(
                title = stringResource(R.string.book_bible_section_affiliations),
                icon = Icons.Outlined.AccountBalance
            ) {
                FlowChips(items = character.affiliations)
            }
        }

        // Danh hiệu
        if (character.titles.isNotEmpty()) {
            ProfileSectionCard(
                title = stringResource(R.string.book_bible_section_titles),
                icon = Icons.Outlined.MilitaryTech
            ) {
                FlowChips(items = character.titles)
            }
        }

        // Khác (Extra Attributes)
        if (character.extraAttributes.isNotEmpty()) {
            ProfileSectionCard(
                title = stringResource(R.string.book_bible_section_other),
                icon = Icons.Outlined.Category
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    character.extraAttributes.forEach { attr ->
                        Row {
                            Text(
                                text = "${attr.label}: ",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = attr.value,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileSectionCard(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun FlowChips(items: List<String>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items.forEach { item ->
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.padding(vertical = 2.dp)
            ) {
                Text(
                    text = item,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun CharacterTimelineTab(
    timeline: CharacterTimeline?,
    isLoading: Boolean
) {
    when {
        isLoading -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }
        timeline == null || timeline.events.isEmpty() -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.book_bible_timeline_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        else -> {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(timeline.events) { event ->
                    TimelineEventCard(event = event)
                }
            }
        }
    }
}

@Composable
private fun TimelineEventCard(event: CharacterTimelineEvent) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = stringResource(R.string.book_bible_timeline_chapter_badge, event.chapter),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "${event.category} • ${event.operation}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = event.displayValue,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp
            )

            if (!event.evidence.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            text = stringResource(R.string.book_bible_timeline_evidence_label),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "\"${event.evidence}\"",
                            fontSize = 12.sp,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}
