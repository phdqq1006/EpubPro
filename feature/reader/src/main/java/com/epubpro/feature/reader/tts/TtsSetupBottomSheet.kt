package com.epubpro.feature.reader.tts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.epubpro.core.designsystem.R
import com.epubpro.domain.model.TtsSettings
import com.epubpro.domain.model.TtsVoice

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TtsSetupBottomSheet(
    currentSettings: TtsSettings,
    onGetAvailableVoices: (isAiVoice: Boolean, language: String) -> List<TtsVoice>,
    onDismiss: () -> Unit,
    onPreviewVoice: (settings: TtsSettings) -> Unit,
    onStartListening: (newSettings: TtsSettings) -> Unit
) {
    var isAiVoice by remember { mutableStateOf(currentSettings.isAiVoice) }
    var speed by remember { mutableStateOf(currentSettings.speed) }
    var pitch by remember { mutableStateOf(currentSettings.pitch) }
    var language by remember { mutableStateOf(currentSettings.language) }
    var selectedVoiceId by remember { mutableStateOf(currentSettings.voiceId) }

    var showLanguageMenu by remember { mutableStateOf(false) }
    var showVoiceMenu by remember { mutableStateOf(false) }

    val availableVoices = remember(isAiVoice, language) {
        onGetAvailableVoices(isAiVoice, language)
    }

    LaunchedEffect(isAiVoice, language, availableVoices) {
        if (isAiVoice) {
            if (selectedVoiceId == null || availableVoices.none { it.id == selectedVoiceId }) {
                selectedVoiceId = availableVoices.firstOrNull()?.id
            }
        } else {
            if (selectedVoiceId != null && availableVoices.none { it.id == selectedVoiceId }) {
                selectedVoiceId = null
            }
        }
    }

    val currentSettingsDraft = TtsSettings(
        isConfigured = true,
        isAiVoice = isAiVoice,
        language = language,
        voiceId = selectedVoiceId,
        speed = speed,
        pitch = pitch
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.RecordVoiceOver,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Thiết lập giọng đọc",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF212121)
                    )
                    Text(
                        text = "Chọn cách EpubPro đọc nội dung. Bạn có thể thay đổi lại bất cứ lúc nào.",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        lineHeight = 16.sp
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = stringResource(R.string.action_close), tint = Color.Gray)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Step 1: Voice Engine Selection
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Giọng AI (Đề xuất)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .border(
                            width = if (isAiVoice) 2.dp else 1.dp,
                            color = if (isAiVoice) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(16.dp)
                        )
                        .background(if (isAiVoice) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
                        .clickable { isAiVoice = true }
                        .padding(16.dp)
                ) {
                    Column {
                        Text(
                            text = "ĐỀ XUẤT",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Giọng AI",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF212121)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Tự nhiên, dùng được ngoại tuyến",
                            fontSize = 11.sp,
                            color = Color.Gray,
                            lineHeight = 14.sp
                        )
                    }
                }

                // Giọng Hệ thống
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .border(
                            width = if (!isAiVoice) 2.dp else 1.dp,
                            color = if (!isAiVoice) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(16.dp)
                        )
                        .background(if (!isAiVoice) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
                        .clickable { isAiVoice = false }
                        .padding(16.dp)
                ) {
                    Column {
                        Text(
                            text = "Giọng hệ thống",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF212121)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Dùng ngay, không cần tải",
                            fontSize = 11.sp,
                            color = Color.Gray,
                            lineHeight = 14.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Step 2: Language & Voice Dropdowns
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "2", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Chọn ngôn ngữ và giọng",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF212121)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Language Dropdown
                Box(modifier = Modifier.weight(1f)) {
                    OutlinedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showLanguageMenu = true },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Box(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = if (language == "vi") "Tiếng Việt" else "English",
                                fontSize = 14.sp,
                                color = Color(0xFF212121)
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = showLanguageMenu,
                        onDismissRequest = { showLanguageMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Tiếng Việt") },
                            onClick = {
                                language = "vi"
                                showLanguageMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("English") },
                            onClick = {
                                language = "en"
                                showLanguageMenu = false
                            }
                        )
                    }
                }

                // Voice Selection Dropdown
                Box(modifier = Modifier.weight(1f)) {
                    val fallbackName = if (isAiVoice) "Chọn giọng đọc" else "Mặc định hệ thống"
                    val selectedVoiceName = availableVoices.find { it.id == selectedVoiceId }?.name ?: fallbackName
                    OutlinedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showVoiceMenu = true },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Box(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = selectedVoiceName,
                                fontSize = 14.sp,
                                color = Color(0xFF212121),
                                maxLines = 1
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = showVoiceMenu,
                        onDismissRequest = { showVoiceMenu = false }
                    ) {
                        if (!isAiVoice) {
                            DropdownMenuItem(
                                text = { Text("Mặc định hệ thống") },
                                onClick = {
                                    selectedVoiceId = null
                                    showVoiceMenu = false
                                }
                            )
                        } else if (availableVoices.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("Chưa có giọng AI. Vào Cài đặt để tải.") },
                                onClick = { showVoiceMenu = false }
                            )
                        }
                        availableVoices.forEach { voice ->
                            DropdownMenuItem(
                                text = { Text(voice.name) },
                                onClick = {
                                    selectedVoiceId = voice.id
                                    showVoiceMenu = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Step 3: Speed & Pitch Sliders
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "3", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Điều chỉnh và nghe thử",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF212121)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Speed Slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "Tốc độ đọc", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(text = String.format("%.1fx", speed), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            Slider(
                value = speed,
                onValueChange = { speed = it },
                valueRange = 0.5f..2.0f,
                steps = 15,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary
                )
            )

            // Pitch Slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "Cao độ", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(text = String.format("%.1f", pitch), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            Slider(
                value = pitch,
                onValueChange = { pitch = it },
                valueRange = 0.5f..1.5f,
                steps = 10,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            val isActionEnabled = !isAiVoice || selectedVoiceId != null

            // Preview Button
            OutlinedButton(
                onClick = { onPreviewVoice(currentSettingsDraft) },
                enabled = isActionEnabled,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(imageVector = Icons.Default.VolumeUp, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Nghe thử", fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Start Button
            Button(
                onClick = { onStartListening(currentSettingsDraft) },
                enabled = isActionEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text(text = "▶  Bắt đầu nghe", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
