package com.epubpro.feature.profile.audio

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.epubpro.core.designsystem.R
import com.epubpro.core.storage.TtsBubblePowerMode
import com.epubpro.core.reader.tts.TtsService

private fun Context.hasTtsBubbleNotificationPermission(): Boolean {
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: AudioSettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var showLanguageMenu by remember { mutableStateOf(false) }
    var showVoiceMenu by remember { mutableStateOf(false) }
    var showPowerModeMenu by remember { mutableStateOf(false) }
    var hasNotificationPermission by remember {
        mutableStateOf(context.hasTtsBubbleNotificationPermission())
    }

    fun completePendingBubbleEnable() {
        val enabledNow = viewModel.onBubbleOverlayPermissionChecked(
            isGranted = Settings.canDrawOverlays(context)
        )
        if (enabledNow) {
            TtsService.syncBubbleState(context, enabled = true)
        }
    }

    val overlayPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        completePendingBubbleEnable()
    }

    fun continueBubbleEnableAfterNotificationPermission() {
        if (!viewModel.requestBubbleEnable()) return
        if (Settings.canDrawOverlays(context)) {
            completePendingBubbleEnable()
            return
        }

        val permissionIntent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        )
        runCatching { overlayPermissionLauncher.launch(permissionIntent) }
            .onFailure {
                viewModel.onBubbleOverlayPermissionChecked(isGranted = false)
            }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        hasNotificationPermission = context.hasTtsBubbleNotificationPermission()
        continueBubbleEnableAfterNotificationPermission()
    }

    fun beginBubbleEnable() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
            runCatching {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }.onFailure {
                continueBubbleEnableAfterNotificationPermission()
            }
        } else {
            continueBubbleEnableAfterNotificationPermission()
        }
    }

    fun syncOverlayPermissionOnResume() {
        val state = viewModel.uiState.value
        if (state.isBubbleEnablePending) {
            completePendingBubbleEnable()
        } else if (state.isBubbleEnabled && !Settings.canDrawOverlays(context)) {
            viewModel.disableBubble()
            TtsService.syncBubbleState(context, enabled = false)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasNotificationPermission = context.hasTtsBubbleNotificationPermission()
                syncOverlayPermissionOnResume()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            hasNotificationPermission = context.hasTtsBubbleNotificationPermission()
            syncOverlayPermissionOnResume()
        }
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.audio_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Thiết lập giọng đọc",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Chọn công nghệ, ngôn ngữ, giọng và cách EpubPro đọc nội dung.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("1. Công nghệ giọng đọc", fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        FilterChip(
                            selected = uiState.isAiVoice,
                            onClick = { viewModel.onAiVoiceToggled(true) },
                            label = { Text("Giọng AI offline") },
                            leadingIcon = { Icon(Icons.Default.AutoAwesome, null, Modifier.size(18.dp)) },
                            modifier = Modifier.weight(1f),
                            colors = FilterChipDefaults.filterChipColors()
                        )
                        FilterChip(
                            selected = !uiState.isAiVoice,
                            onClick = { viewModel.onAiVoiceToggled(false) },
                            label = { Text("Giọng hệ thống") },
                            leadingIcon = { Icon(Icons.Default.PhoneAndroid, null, Modifier.size(18.dp)) },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    HorizontalDivider()
                    Text("2. Ngôn ngữ và giọng", fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(modifier = Modifier.weight(0.4f)) {
                            SettingSelector(
                                text = if (uiState.language == "en") "English" else "Tiếng Việt",
                                enabled = !uiState.isAiVoice,
                                onClick = { showLanguageMenu = true }
                            )
                            DropdownMenu(
                                expanded = showLanguageMenu && !uiState.isAiVoice,
                                onDismissRequest = { showLanguageMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Tiếng Việt") },
                                    onClick = {
                                        viewModel.onLanguageChanged("vi")
                                        showLanguageMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("English") },
                                    onClick = {
                                        viewModel.onLanguageChanged("en")
                                        showLanguageMenu = false
                                    }
                                )
                            }
                        }

                        Box(modifier = Modifier.weight(0.6f)) {
                            val selectedVoiceName = if (uiState.isAiVoice) {
                                uiState.aiVoices.firstOrNull { it.id == uiState.selectedVoiceId }?.name
                                    ?: "Ch\u1ecdn gi\u1ecdng AI"
                            } else {
                                uiState.systemVoices.firstOrNull { it.id == uiState.selectedVoiceId }?.name
                                    ?: "Mặc định hệ thống"
                            }
                            SettingSelector(
                                text = selectedVoiceName,
                                enabled = uiState.isAiVoice || uiState.isSystemTtsReady,
                                onClick = { showVoiceMenu = true }
                            )
                            DropdownMenu(
                                expanded = showVoiceMenu,
                                onDismissRequest = { showVoiceMenu = false }
                            ) {
                                if (uiState.isAiVoice) {
                                    uiState.aiVoices.forEach { voice ->
                                        DropdownMenuItem(
                                            text = {
                                                Column {
                                                    Text(voice.name)
                                                    Text(
                                                        text = "${voice.size} · ${if (voice.isDownloaded) "Đã tải" else "Chưa tải"}",
                                                        fontSize = 12.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            },
                                            leadingIcon = if (uiState.selectedVoiceId == voice.id) {
                                                { Icon(Icons.Default.Check, contentDescription = null) }
                                            } else null,
                                            onClick = {
                                                viewModel.onVoiceSelected(voice.id)
                                                showVoiceMenu = false
                                            }
                                        )
                                    }
                                } else {
                                    DropdownMenuItem(
                                        text = { Text("Mặc định hệ thống") },
                                        onClick = {
                                            viewModel.onVoiceSelected(null)
                                            showVoiceMenu = false
                                        }
                                    )
                                    uiState.systemVoices.forEach { voice ->
                                        DropdownMenuItem(
                                            text = { Text(voice.name) },
                                            leadingIcon = if (uiState.selectedVoiceId == voice.id) {
                                                { Icon(Icons.Default.Check, contentDescription = null) }
                                            } else null,
                                            onClick = {
                                                viewModel.onVoiceSelected(voice.id)
                                                showVoiceMenu = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (uiState.isAiVoice) {
                        AiDownloadSection(uiState = uiState, onDownload = viewModel::downloadCurrentVoice)
                    }

                    HorizontalDivider()
                    Text("3. Điều chỉnh và nghe thử", fontWeight = FontWeight.SemiBold)
                    SettingSlider(
                        label = stringResource(R.string.audio_speech_rate),
                        valueLabel = String.format("%.1fx", uiState.speechSpeed),
                        value = uiState.speechSpeed,
                        range = 0.5f..2.0f,
                        steps = 15,
                        onValueChange = viewModel::onSpeedChanged
                    )

                    if (!uiState.isAiVoice) {
                        SettingSlider(
                            label = stringResource(R.string.audio_speech_pitch),
                            valueLabel = String.format("%.1f", uiState.speechPitch),
                            value = uiState.speechPitch,
                            range = 0.5f..1.5f,
                            steps = 10,
                            onValueChange = viewModel::onPitchChanged
                        )
                    }

                    OutlinedButton(
                        onClick = if (uiState.isAiVoice) viewModel::testVoice else viewModel::testSystemVoice,
                        enabled = if (uiState.isAiVoice) {
                            uiState.selectedVoiceId != null && uiState.isModelDownloaded && !uiState.isPlaying
                        } else {
                            uiState.isSystemTtsReady
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        if (uiState.isPlaying) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.VolumeUp, contentDescription = null)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(if (uiState.isPlaying) stringResource(R.string.audio_playing) else stringResource(R.string.audio_test_play))
                    }
                }
            }

            AudioBubbleSettingsCard(
                isEnabled = uiState.isBubbleEnabled,
                isEnablePending = uiState.isBubbleEnablePending,
                showNotificationWarning = uiState.isBubbleEnabled && !hasNotificationPermission,
                onEnabledChange = { shouldEnable ->
                    if (shouldEnable) {
                        beginBubbleEnable()
                    } else {
                        viewModel.disableBubble()
                        TtsService.syncBubbleState(context, enabled = false)
                    }
                }
            )

            BubblePowerModeCard(
                mode = uiState.bubblePowerMode,
                expanded = showPowerModeMenu,
                onExpandedChange = { showPowerModeMenu = it },
                onModeSelected = viewModel::setBubblePowerMode
            )

            Button(
                onClick = onNavigateBack,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(stringResource(R.string.audio_save_settings), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun AudioBubbleSettingsCard(
    isEnabled: Boolean,
    isEnablePending: Boolean,
    showNotificationWarning: Boolean,
    onEnabledChange: (Boolean) -> Unit
) {
    val title = stringResource(R.string.audio_bubble_settings_title)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = title,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(R.string.audio_bubble_settings_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(
                    checked = isEnabled || isEnablePending,
                    onCheckedChange = onEnabledChange,
                    modifier = Modifier.semantics { contentDescription = title }
                )
            }

            if (isEnablePending) {
                Text(
                    text = stringResource(R.string.audio_bubble_permission_pending),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (showNotificationWarning) {
                HorizontalDivider()
                Text(
                    text = stringResource(R.string.audio_bubble_notification_permission_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun BubblePowerModeCard(
    mode: TtsBubblePowerMode,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onModeSelected: (TtsBubblePowerMode) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(stringResource(R.string.audio_bubble_power_mode_title), fontWeight = FontWeight.SemiBold)
            Text(
                text = if (mode == TtsBubblePowerMode.ALWAYS_ON) {
                    stringResource(R.string.audio_bubble_power_mode_always_on_desc)
                } else {
                    stringResource(R.string.audio_bubble_power_mode_saver_desc)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Box {
                SettingSelector(
                    text = if (mode == TtsBubblePowerMode.ALWAYS_ON) {
                        stringResource(R.string.audio_bubble_power_mode_always_on)
                    } else {
                        stringResource(R.string.audio_bubble_power_mode_saver)
                    },
                    enabled = true,
                    onClick = { onExpandedChange(true) }
                )
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { onExpandedChange(false) }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.audio_bubble_power_mode_always_on)) },
                        onClick = {
                            onModeSelected(TtsBubblePowerMode.ALWAYS_ON)
                            onExpandedChange(false)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.audio_bubble_power_mode_saver)) },
                        onClick = {
                            onModeSelected(TtsBubblePowerMode.BATTERY_SAVER)
                            onExpandedChange(false)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingSelector(text: String, enabled: Boolean, onClick: () -> Unit) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text, maxLines = 1, modifier = Modifier.weight(1f))
            if (enabled) Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
        }
    }
}

@Composable
private fun AiDownloadSection(uiState: AudioSettingsUiState, onDownload: () -> Unit) {
    val selected = uiState.aiVoices.firstOrNull { it.id == uiState.selectedVoiceId }
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                enabled = selected != null && !selected.isDownloaded && !uiState.isDownloading,
                onClick = onDownload
            ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (selected?.isDownloaded == true) Icons.Default.Check else Icons.Default.CloudDownload,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = when {
                            selected == null -> "Chọn một giọng AI để tải"
                            selected.isDownloaded -> "${selected.name} đã sẵn sàng"
                            uiState.isDownloading -> "Đang tải ${selected.name}..."
                            else -> "Tải ${selected.name}"
                        },
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = selected?.let { "${it.size} · sử dụng ngoại tuyến" }
                            ?: "Không có model mặc định",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (uiState.isDownloading) {
                LinearProgressIndicator(
                    progress = { uiState.downloadProgress },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            uiState.downloadError?.let { error ->
                Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun SettingSlider(
    label: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label)
            Text(valueLabel, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps
        )
    }
}
