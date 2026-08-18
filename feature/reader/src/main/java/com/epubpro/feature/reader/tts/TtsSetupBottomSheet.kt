package com.epubpro.feature.reader.tts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    var pitch by remember { mutableStateOf(if (currentSettings.isAiVoice) 1.0f else currentSettings.pitch) }
    var language by remember { mutableStateOf(if (currentSettings.isAiVoice) "vi" else currentSettings.language) }
    var selectedVoiceId by remember { mutableStateOf(currentSettings.voiceId) }
    var showLanguageMenu by remember { mutableStateOf(false) }
    var showVoiceMenu by remember { mutableStateOf(false) }

    val availableVoices = onGetAvailableVoices(isAiVoice, language)
    LaunchedEffect(isAiVoice, language, availableVoices) {
        if (isAiVoice && language != "vi") language = "vi"
        if (selectedVoiceId != null && availableVoices.none { it.id == selectedVoiceId }) {
            selectedVoiceId = null
        }
    }

    val selectedVoice = availableVoices.firstOrNull { it.id == selectedVoiceId }
    val canUseSelection = !isAiVoice || selectedVoice?.isDownloaded == true
    val draft = TtsSettings(
        isConfigured = true,
        isAiVoice = isAiVoice,
        language = if (isAiVoice) "vi" else language,
        voiceId = selectedVoiceId,
        speed = speed,
        pitch = if (isAiVoice) 1.0f else pitch
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Thiết lập giọng đọc", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("Chọn cách EpubPro đọc nội dung. Giọng AI cần được tải trước khi sử dụng.")

            Text("1. Công nghệ giọng đọc", fontWeight = FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilterChip(
                    selected = isAiVoice,
                    onClick = {
                        if (!isAiVoice) {
                            isAiVoice = true
                            language = "vi"
                            pitch = 1.0f
                            selectedVoiceId = null
                        }
                    },
                    label = { Text("Giọng AI") },
                    leadingIcon = { Icon(Icons.Default.AutoAwesome, null, Modifier.size(18.dp)) },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = !isAiVoice,
                    onClick = {
                        if (isAiVoice) {
                            isAiVoice = false
                            selectedVoiceId = null
                        }
                    },
                    label = { Text("Giọng hệ thống") },
                    leadingIcon = { Icon(Icons.Default.PhoneAndroid, null, Modifier.size(18.dp)) },
                    modifier = Modifier.weight(1f)
                )
            }

            HorizontalDivider()
            Text("2. Ngôn ngữ và giọng", fontWeight = FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f)) {
                    SetupSelector(
                        text = if (language == "en") "English" else "Tiếng Việt",
                        enabled = !isAiVoice,
                        onClick = { showLanguageMenu = true }
                    )
                    DropdownMenu(
                        expanded = showLanguageMenu && !isAiVoice,
                        onDismissRequest = { showLanguageMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Tiếng Việt") },
                            onClick = {
                                language = "vi"
                                selectedVoiceId = null
                                showLanguageMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("English") },
                            onClick = {
                                language = "en"
                                selectedVoiceId = null
                                showLanguageMenu = false
                            }
                        )
                    }
                }

                Box(Modifier.weight(1f)) {
                    SetupSelector(
                        text = selectedVoice?.name
                            ?: if (isAiVoice) "Chưa chọn giọng" else "Mặc định hệ thống",
                        enabled = true,
                        onClick = { showVoiceMenu = true }
                    )
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
                        }
                        availableVoices.forEach { voice ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(voice.name)
                                        if (isAiVoice) {
                                            Text(
                                                if (voice.isDownloaded) "Đã tải" else "Chưa tải · vào Cài đặt âm thanh để tải",
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                },
                                leadingIcon = if (selectedVoiceId == voice.id) {
                                    { Icon(Icons.Default.Check, contentDescription = null) }
                                } else null,
                                onClick = {
                                    selectedVoiceId = voice.id
                                    showVoiceMenu = false
                                }
                            )
                        }
                    }
                }
            }

            if (isAiVoice && selectedVoice != null && !selectedVoice.isDownloaded) {
                Text(
                    "Model này chưa được tải. Hãy mở Cài đặt âm thanh để tải trước khi nghe.",
                    fontSize = 12.sp
                )
            }

            HorizontalDivider()
            Text("3. Điều chỉnh và nghe thử", fontWeight = FontWeight.SemiBold)
            SetupSlider(
                label = "Tốc độ đọc",
                valueText = String.format("%.1fx", speed),
                value = speed,
                range = 0.5f..2.0f,
                steps = 15,
                onValueChange = { speed = it }
            )
            if (!isAiVoice) {
                SetupSlider(
                    label = "Cao độ",
                    valueText = String.format("%.1f", pitch),
                    value = pitch,
                    range = 0.5f..1.5f,
                    steps = 10,
                    onValueChange = { pitch = it }
                )
            }

            OutlinedButton(
                onClick = { onPreviewVoice(draft) },
                enabled = canUseSelection,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.VolumeUp, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Nghe thử")
            }
            Button(
                onClick = { onStartListening(draft) },
                enabled = canUseSelection,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors()
            ) {
                Text("Bắt đầu nghe", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SetupSelector(text: String, enabled: Boolean, onClick: () -> Unit) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text, maxLines = 1, modifier = Modifier.weight(1f))
            if (enabled) Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
        }
    }
}

@Composable
private fun SetupSlider(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label)
            Text(valueText, fontWeight = FontWeight.Bold)
        }
        Slider(value, onValueChange, valueRange = range, steps = steps)
    }
}
