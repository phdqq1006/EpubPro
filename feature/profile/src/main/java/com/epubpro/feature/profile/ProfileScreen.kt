package com.epubpro.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.epubpro.core.designsystem.R

import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun ProfileScreen(
    onNavigateToAudioSettings: () -> Unit = {},
    onNavigateToReadingDefaults: () -> Unit = {},
    onNavigateToContentFilter: () -> Unit = {},
    viewModel: ProfileViewModel = hiltViewModel()
) {
    var showServerSettingsDialog by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)
    ) {
        item {
            Text(
                text = stringResource(R.string.profile_title),
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 12.dp)
                    .semantics { heading() },
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }

        item {
            ProfileSectionHeader(
                title = stringResource(R.string.profile_section_sync),
                modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
            )
        }

        item {
            ProfileItem(
                title = stringResource(R.string.profile_drive_sync_title),
                subtitle = stringResource(R.string.profile_drive_sync_subtitle),
                icon = Icons.Default.CloudSync,
                onClick = { },
                enabled = false
            )
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
            ProfileSectionHeader(
                title = stringResource(R.string.profile_section_settings),
                modifier = Modifier.padding(vertical = 2.dp)
            )
        }

        item {
            ProfileItem(
                title = stringResource(R.string.profile_nav_custom_title),
                subtitle = stringResource(R.string.profile_nav_custom_subtitle),
                icon = Icons.Default.ViewCarousel,
                onClick = { },
                enabled = false
            )
        }

        item {
            ProfileItem(
                title = stringResource(R.string.profile_appearance_title),
                subtitle = stringResource(R.string.profile_appearance_subtitle),
                icon = Icons.Default.Palette,
                onClick = { },
                enabled = false
            )
        }

        item {
            ProfileItem(
                title = stringResource(R.string.profile_reader_defaults_title),
                subtitle = stringResource(R.string.profile_reader_defaults_subtitle),
                icon = Icons.AutoMirrored.Filled.MenuBook,
                onClick = onNavigateToReadingDefaults
            )
        }

        item {
            ProfileItem(
                title = stringResource(R.string.profile_audio_settings_title),
                subtitle = stringResource(R.string.profile_audio_settings_subtitle),
                icon = Icons.Default.VolumeUp,
                onClick = onNavigateToAudioSettings
            )
        }

        item {
            ProfileItem(
                title = stringResource(R.string.profile_highlight_tags_title),
                subtitle = stringResource(R.string.profile_highlight_tags_subtitle),
                icon = Icons.Default.Label,
                onClick = { },
                enabled = false
            )
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
            ProfileSectionHeader(
                title = stringResource(R.string.profile_section_advanced),
                modifier = Modifier.padding(vertical = 2.dp)
            )
        }

        item {
            ProfileItem(
                title = stringResource(R.string.profile_content_filter_title),
                subtitle = stringResource(R.string.profile_content_filter_subtitle),
                icon = Icons.Default.FilterAlt,
                onClick = onNavigateToContentFilter
            )
        }

        item {
            ProfileItem(
                title = stringResource(R.string.server_settings_title),
                subtitle = stringResource(R.string.server_settings_subtitle),
                icon = Icons.Default.Dns,
                onClick = { showServerSettingsDialog = true }
            )
        }
    }

    if (showServerSettingsDialog) {
        ServerSettingsDialog(
            onDismissRequest = { showServerSettingsDialog = false },
            serverPreferencesManager = viewModel.serverPreferencesManager,
            onlineNovelRepository = viewModel.onlineNovelRepository
        )
    }
}

@Composable
fun ProfileSectionHeader(
    title: String,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .semantics { heading() }
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(18.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.primary)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = title,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 0.5.sp
        )
        Spacer(modifier = Modifier.width(12.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
        )
    }
}

@Composable
fun ProfileItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    val contentColor = if (enabled) {
        MaterialTheme.colorScheme.onBackground
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
    }
    val iconColor = if (enabled) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                role = Role.Button
                if (!enabled) disabled()
            }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = contentColor
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (enabled) {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        } else {
            Text(
                text = stringResource(R.string.profile_coming_soon),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ProfileItemWithSwitch(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = isChecked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )
    }
}
