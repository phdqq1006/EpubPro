package com.epubpro.feature.profile.filter

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.epubpro.core.designsystem.R
import com.epubpro.domain.model.ContentFilterRule
import com.epubpro.domain.model.isJavaScriptCompatibleRegex

/**
 * Màn hình quản lý Cài đặt Lọc & Thay thế nội dung.
 *
 * @param onNavigateBack Callback khi bấm quay lại.
 * @param viewModel ViewModel quản lý trạng thái màn hình.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContentFilterSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: ContentFilterSettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.content_filter_screen_title),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.content_filter_action_back)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Master Switch Toggle
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FilterAlt,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = stringResource(R.string.content_filter_master_title),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                Text(
                                    text = stringResource(R.string.content_filter_master_desc),
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = uiState.preferences.isFilterEnabled,
                            onCheckedChange = viewModel::onToggleFilter
                        )
                    }
                }
            }

            // Input Section to add new rule
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.content_filter_add_title),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )

                        OutlinedTextField(
                            value = uiState.newPatternInput,
                            onValueChange = viewModel::onPatternInputChanged,
                            label = { Text(stringResource(R.string.content_filter_pattern_label)) },
                            placeholder = { Text(stringResource(R.string.content_filter_pattern_placeholder)) },
                            isError = uiState.errorMessage != null,
                            supportingText = uiState.errorMessage?.let { error ->
                                { Text(text = error, color = MaterialTheme.colorScheme.error) }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )

                        OutlinedTextField(
                            value = uiState.newReplacementInput,
                            onValueChange = viewModel::onReplacementInputChanged,
                            label = { Text(stringResource(R.string.replace_text_replacement_label)) },
                            placeholder = { Text(stringResource(R.string.replace_text_placeholder)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = stringResource(R.string.replace_text_regex_label),
                                fontSize = 14.sp
                            )
                            Switch(
                                checked = uiState.isRegexMode,
                                onCheckedChange = viewModel::onRegexModeChanged
                            )
                        }

                        Button(
                            onClick = viewModel::onAddRule,
                            enabled = uiState.newPatternInput.isNotBlank() && uiState.errorMessage == null,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.content_filter_add_button),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // Rules List Header
            item {
                Text(
                    text = stringResource(R.string.content_filter_list_title, uiState.preferences.rules.size),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            // Empty state
            if (uiState.preferences.rules.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.content_filter_empty_list),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            // Active Rules Items
            items(
                items = uiState.preferences.rules,
                key = { it.id }
            ) { rule ->
                RuleItemRow(
                    rule = rule,
                    onToggle = { viewModel.onToggleRule(rule.id) },
                    onDelete = { viewModel.onDeleteRule(rule.id) },
                    onEdit = { viewModel.onStartEditRule(rule) }
                )
            }
        }
    }

    // BottomSheet chỉnh sửa quy tắc
    uiState.editingRule?.let { ruleToEdit ->
        EditFilterRuleBottomSheet(
            rule = ruleToEdit,
            onDismiss = viewModel::onDismissEditRule,
            onSaveRule = { pattern, replacement, isRegex ->
                viewModel.onSaveEditedRule(ruleToEdit.id, pattern, replacement, isRegex)
            }
        )
    }
}

/**
 * Hàng hiển thị một quy tắc lọc/thay thế trong danh sách.
 *
 * @param rule Quy tắc lọc/thay thế.
 * @param onToggle Callback bật/tắt quy tắc.
 * @param onDelete Callback xóa quy tắc.
 * @param onEdit Callback mở chỉnh sửa quy tắc.
 */
@Composable
private fun RuleItemRow(
    rule: ContentFilterRule,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit),
        colors = CardDefaults.cardColors(
            containerColor = if (rule.isEnabled) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            }
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (rule.replacement.isNotBlank()) {
                        Text(
                            text = stringResource(R.string.content_filter_rule_format, rule.pattern, rule.replacement),
                            fontWeight = FontWeight.Medium,
                            fontSize = 15.sp,
                            color = if (rule.isEnabled) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    } else {
                        Text(
                            text = rule.pattern,
                            fontWeight = FontWeight.Medium,
                            fontSize = 15.sp,
                            color = if (rule.isEnabled) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
                        ) {
                            Text(
                                text = stringResource(R.string.content_filter_rule_delete_tag),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                if (rule.isRegex) {
                    Text(
                        text = stringResource(R.string.content_filter_regex_badge),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onEdit) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = stringResource(R.string.content_filter_action_edit),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Switch(
                    checked = rule.isEnabled,
                    onCheckedChange = { onToggle() },
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.content_filter_action_delete),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

/**
 * BottomSheet chỉnh sửa một quy tắc lọc/thay thế nội dung.
 *
 * @param rule Quy tắc cần chỉnh sửa.
 * @param onDismiss Callback khi đóng BottomSheet.
 * @param onSaveRule Callback lưu quy tắc sau khi chỉnh sửa.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditFilterRuleBottomSheet(
    rule: ContentFilterRule,
    onDismiss: () -> Unit,
    onSaveRule: (pattern: String, replacement: String, isRegex: Boolean) -> Unit
) {
    var pattern by remember(rule.pattern) { mutableStateOf(rule.pattern) }
    var replacement by remember(rule.replacement) { mutableStateOf(rule.replacement) }
    var isRegex by remember(rule.isRegex) { mutableStateOf(rule.isRegex) }

    val invalidRegexMessage = stringResource(R.string.content_filter_invalid_regex)
    val errorMessage = remember(pattern, isRegex) {
        if (pattern.isBlank()) null
        else if (isRegex && !isJavaScriptCompatibleRegex(pattern)) {
            invalidRegexMessage
        } else null
    }

    val isInputValid = pattern.isNotBlank() && errorMessage == null

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.replace_text_edit_title),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stringResource(R.string.replace_text_original_label),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                )
                OutlinedTextField(
                    value = pattern,
                    onValueChange = { pattern = it },
                    isError = errorMessage != null,
                    supportingText = errorMessage?.let { error ->
                        { Text(text = error, color = MaterialTheme.colorScheme.error) }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stringResource(R.string.replace_text_replacement_label),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                )
                OutlinedTextField(
                    value = replacement,
                    onValueChange = { replacement = it },
                    placeholder = {
                        Text(
                            text = stringResource(R.string.replace_text_placeholder),
                            fontSize = 14.sp
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.replace_text_regex_label),
                    style = MaterialTheme.typography.bodyMedium
                )
                Switch(
                    checked = isRegex,
                    onCheckedChange = { isRegex = it }
                )
            }

            Button(
                onClick = {
                    if (isInputValid) {
                        onSaveRule(pattern.trim(), replacement, isRegex)
                    }
                },
                enabled = isInputValid,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text(
                    text = stringResource(R.string.replace_text_save_button),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )
            }
        }
    }
}
