package com.epubpro.feature.reader.filter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.epubpro.core.designsystem.R
import com.epubpro.domain.model.isJavaScriptCompatibleRegex

/**
 * BottomSheet nhập nhanh quy tắc thay thế từ ngữ khi người dùng chọn văn bản trong sách.
 *
 * @param initialPattern Chuỗi văn bản gốc được lấy từ đoạn chọn (selection).
 * @param onDismiss Callback khi đóng BottomSheet.
 * @param onSaveRule Callback lưu quy tắc với từ gốc, từ thay thế và cờ Regex.
 * @param modifier Modifier tùy chỉnh cho Composable.
 * @param sheetState State điều khiển BottomSheet của Material3.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReplaceTextBottomSheet(
    initialPattern: String,
    onDismiss: () -> Unit,
    onSaveRule: (pattern: String, replacement: String, isRegex: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
) {
    var pattern by remember(initialPattern) { mutableStateOf(initialPattern) }
    var replacement by remember { mutableStateOf("") }
    var isRegex by remember { mutableStateOf(false) }

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
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Tiêu đề căn giữa
            Text(
                text = stringResource(R.string.replace_text_title),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Serif
                ),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Trường 1: Từ gốc
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stringResource(R.string.replace_text_original_label),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium
                    ),
                    color = MaterialTheme.colorScheme.onSurface
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
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Trường 2: Thay thế bằng
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stringResource(R.string.replace_text_replacement_label),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                OutlinedTextField(
                    value = replacement,
                    onValueChange = { replacement = it },
                    placeholder = {
                        Text(
                            text = stringResource(R.string.replace_text_placeholder),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            fontSize = 14.sp
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Switch Regex
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.replace_text_regex_label),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Normal
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Switch(
                    checked = isRegex,
                    onCheckedChange = { isRegex = it }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Nút Lưu quy tắc
            Button(
                onClick = {
                    if (isInputValid) {
                        onSaveRule(pattern.trim(), replacement, isRegex)
                        onDismiss()
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
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }
        }
    }
}
