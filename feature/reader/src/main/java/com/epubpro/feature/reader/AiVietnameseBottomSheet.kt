package com.epubpro.feature.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.epubpro.domain.model.AiRule
import com.epubpro.domain.model.AiRuleAction
import com.epubpro.domain.model.AiRuleScope
import com.epubpro.domain.model.SUPPORTED_GEMINI_MODELS

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiVietnameseBottomSheet(
    uiState: ReaderUiState,
    chapterTitle: String,
    onDismiss: () -> Unit,
    onSaveConfiguration: (apiKey: String?, modelId: String) -> Unit,
    onTestConnection: (apiKey: String?, modelId: String) -> Unit,
    onClearApiKey: () -> Unit,
    onSelectVersion: (ReaderContentVersion) -> Unit,
    onStartPolish: () -> Unit,
    onCancelPolish: () -> Unit,
    onDeleteChapter: () -> Unit,
    onSaveRule: (
        ruleId: String?,
        scope: AiRuleScope,
        source: String,
        action: AiRuleAction,
        replacement: String?,
        caseSensitive: Boolean
    ) -> Unit,
    onDeleteRule: (String) -> Unit
) {
    var apiKey by remember { mutableStateOf(BuildConfig.DEFAULT_GEMINI_API_KEY) }
    var modelExpanded by remember { mutableStateOf(false) }
    var selectedModel by remember(uiState.aiSettings.modelId) {
        mutableStateOf(uiState.aiSettings.modelId)
    }

    var editingRule by remember { mutableStateOf<AiRule?>(null) }
    var ruleSource by remember { mutableStateOf("") }
    var ruleReplacement by remember { mutableStateOf("") }
    var ruleScope by remember { mutableStateOf(AiRuleScope.BOOK) }
    var ruleAction by remember { mutableStateOf(AiRuleAction.KEEP) }
    var caseSensitive by remember { mutableStateOf(false) }

    fun editRule(rule: AiRule?) {
        editingRule = rule
        ruleSource = rule?.source.orEmpty()
        ruleReplacement = rule?.replacement.orEmpty()
        ruleScope = rule?.scope ?: AiRuleScope.BOOK
        ruleAction = rule?.action ?: AiRuleAction.KEEP
        caseSensitive = rule?.caseSensitive ?: false
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.SmartToy, contentDescription = null)
                Text(
                    text = "AI thuần Việt",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 10.dp)
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Đóng")
                }
            }
            Text(
                text = chapterTitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(20.dp))
            Text("Cấu hình Gemini", style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = {
                    Text(if (uiState.aiSettings.hasApiKey) "API key mới (không bắt buộc)" else "API key")
                },
                leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
            )

            ExposedDropdownMenuBox(
                expanded = modelExpanded,
                onExpandedChange = { modelExpanded = !modelExpanded },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
            ) {
                val selectedName = SUPPORTED_GEMINI_MODELS
                    .firstOrNull { it.id == selectedModel }
                    ?.displayName
                    ?: selectedModel
                OutlinedTextField(
                    value = selectedName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Model") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelExpanded)
                    },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = modelExpanded,
                    onDismissRequest = { modelExpanded = false }
                ) {
                    SUPPORTED_GEMINI_MODELS.forEach { model ->
                        DropdownMenuItem(
                            text = { Text(model.displayName) },
                            onClick = {
                                selectedModel = model.id
                                modelExpanded = false
                            }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        onTestConnection(apiKey.ifBlank { null }, selectedModel)
                    },
                    enabled = !uiState.isTestingAiConnection,
                    modifier = Modifier.weight(1f)
                ) {
                    if (uiState.isTestingAiConnection) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Kiểm tra")
                    }
                }
                Button(
                    onClick = {
                        onSaveConfiguration(apiKey.ifBlank { null }, selectedModel)
                        apiKey = ""
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Lưu", modifier = Modifier.padding(start = 6.dp))
                }
            }
            if (uiState.aiSettings.hasApiKey) {
                TextButton(onClick = onClearApiKey) {
                    Text("Xóa API key")
                }
            }

            uiState.aiConnectionMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.primary)
            }
            uiState.aiError?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            HorizontalDivider(Modifier.padding(vertical = 16.dp))
            Text("Nội dung chương", style = MaterialTheme.typography.titleMedium)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val originalSelected = uiState.contentVersion == ReaderContentVersion.ORIGINAL
                if (originalSelected) {
                    Button(
                        onClick = { onSelectVersion(ReaderContentVersion.ORIGINAL) },
                        modifier = Modifier.weight(1f)
                    ) { Text("Bản gốc") }
                } else {
                    OutlinedButton(
                        onClick = { onSelectVersion(ReaderContentVersion.ORIGINAL) },
                        modifier = Modifier.weight(1f)
                    ) { Text("Bản gốc") }
                }

                val aiSelected = uiState.contentVersion == ReaderContentVersion.AI
                if (aiSelected) {
                    Button(
                        onClick = { onSelectVersion(ReaderContentVersion.AI) },
                        enabled = uiState.aiChapterHtml != null,
                        modifier = Modifier.weight(1f)
                    ) { Text("AI thuần Việt") }
                } else {
                    OutlinedButton(
                        onClick = { onSelectVersion(ReaderContentVersion.AI) },
                        enabled = uiState.aiChapterHtml != null,
                        modifier = Modifier.weight(1f)
                    ) { Text("AI thuần Việt") }
                }
            }

            if (uiState.aiCreatedWithOldConfiguration) {
                Text(
                    "Được tạo bằng cấu hình cũ",
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            if (uiState.isAiProcessing) {
                val progress = if (uiState.aiTotalParts > 0) {
                    uiState.aiCompletedParts.toFloat() / uiState.aiTotalParts
                } else {
                    0f
                }
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp)
                )
                Text(
                    "Đang xử lý phần " + uiState.aiCompletedParts + "/" +
                        uiState.aiTotalParts.coerceAtLeast(1),
                    modifier = Modifier.padding(top = 6.dp)
                )
                OutlinedButton(
                    onClick = onCancelPolish,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    Text("Hủy")
                }
            } else {
                Button(
                    onClick = {
                        if (apiKey.isNotBlank() || selectedModel != uiState.aiSettings.modelId) {
                            onSaveConfiguration(apiKey.ifBlank { null }, selectedModel)
                            apiKey = ""
                        }
                        onStartPolish()
                    },
                    enabled = (uiState.aiSettings.hasApiKey || apiKey.isNotBlank()) && !uiState.isAiProcessing,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp)
                ) {
                    Text(if (uiState.aiChapterHtml == null) "Thuần Việt chương này" else "Tạo lại")
                }
            }

            if ((uiState.aiChapterHtml != null || uiState.aiTotalParts > 0) && !uiState.isAiProcessing) {
                TextButton(onClick = onDeleteChapter) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = null)
                    Text("Xóa bản AI", modifier = Modifier.padding(start = 6.dp))
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 16.dp))
            Text("Bộ quy tắc", style = MaterialTheme.typography.titleMedium)

            uiState.aiRules.forEach { rule ->
                ListItem(
                    headlineContent = { Text(rule.source) },
                    supportingContent = {
                        val scope = if (rule.scope == AiRuleScope.GLOBAL) "Chung" else "Sách này"
                        val action = if (rule.action == AiRuleAction.KEEP) {
                            "Giữ nguyên"
                        } else {
                            "Thay bằng: " + rule.replacement.orEmpty()
                        }
                        Text("$scope · $action")
                    },
                    trailingContent = {
                        Row {
                            IconButton(onClick = { editRule(rule) }) {
                                Icon(Icons.Default.Edit, contentDescription = "Sửa quy tắc")
                            }
                            IconButton(onClick = { onDeleteRule(rule.id) }) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "Xóa quy tắc")
                            }
                        }
                    }
                )
            }

            Text(
                if (editingRule == null) "Thêm quy tắc" else "Sửa quy tắc",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 12.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = ruleScope == AiRuleScope.GLOBAL,
                    onClick = { ruleScope = AiRuleScope.GLOBAL },
                    label = { Text("Dùng chung") }
                )
                FilterChip(
                    selected = ruleScope == AiRuleScope.BOOK,
                    onClick = { ruleScope = AiRuleScope.BOOK },
                    label = { Text("Sách này") }
                )
            }

            OutlinedTextField(
                value = ruleSource,
                onValueChange = { ruleSource = it },
                label = { Text("Thuật ngữ") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = ruleAction == AiRuleAction.KEEP,
                    onClick = { ruleAction = AiRuleAction.KEEP },
                    label = { Text("Giữ nguyên") }
                )
                FilterChip(
                    selected = ruleAction == AiRuleAction.REPLACE,
                    onClick = { ruleAction = AiRuleAction.REPLACE },
                    label = { Text("Thay bằng") }
                )
            }

            if (ruleAction == AiRuleAction.REPLACE) {
                OutlinedTextField(
                    value = ruleReplacement,
                    onValueChange = { ruleReplacement = it },
                    label = { Text("Nội dung thay thế") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Phân biệt hoa/thường", modifier = Modifier.weight(1f))
                Switch(checked = caseSensitive, onCheckedChange = { caseSensitive = it })
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (editingRule != null) {
                    OutlinedButton(
                        onClick = { editRule(null) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Hủy sửa")
                    }
                }
                Button(
                    onClick = {
                        onSaveRule(
                            editingRule?.id,
                            ruleScope,
                            ruleSource,
                            ruleAction,
                            ruleReplacement.ifBlank { null },
                            caseSensitive
                        )
                        editRule(null)
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Lưu quy tắc")
                }
            }

            Spacer(Modifier.height(28.dp))
        }
    }
}
