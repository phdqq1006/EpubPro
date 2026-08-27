package com.epubpro.feature.profile.filter

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.epubpro.core.designsystem.R
import com.epubpro.core.storage.ReaderPreferencesManager
import com.epubpro.domain.model.ContentFilterPreferences
import com.epubpro.domain.model.ContentFilterRule
import com.epubpro.domain.model.isJavaScriptCompatibleRegex
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ContentFilterSettingsUiState(
    val preferences: ContentFilterPreferences = ContentFilterPreferences(),
    val newPatternInput: String = "",
    val newReplacementInput: String = "",
    val isRegexMode: Boolean = false,
    val errorMessage: String? = null,
    val editingRule: ContentFilterRule? = null
)

/**
 * ViewModel quản lý màn hình cài đặt Lọc & Thay thế nội dung.
 *
 * @param preferencesManager Quản lý cấu hình đọc sách và bộ lọc nội dung.
 * @param context Context ứng dụng dùng để đọc String Resource cho UI state.
 */
@HiltViewModel
class ContentFilterSettingsViewModel @Inject constructor(
    private val preferencesManager: ReaderPreferencesManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        ContentFilterSettingsUiState(
            preferences = preferencesManager.getFilterPreferences()
        )
    )
    val uiState: StateFlow<ContentFilterSettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            preferencesManager.filterPreferences.collect { filterPrefs ->
                _uiState.update { it.copy(preferences = filterPrefs) }
            }
        }
    }

    /**
     * Bật hoặc tắt tính năng Lọc & Thay thế nội dung toàn cục.
     *
     * @param enabled `true` để bật, `false` để tắt.
     */
    fun onToggleFilter(enabled: Boolean) {
        preferencesManager.updateFilterPreferences { prefs ->
            prefs.copy(isFilterEnabled = enabled)
        }
    }

    /**
     * Cập nhật chuỗi văn bản từ gốc khi người dùng nhập vào ô nhập liệu.
     *
     * @param input Chuỗi từ khóa hoặc mẫu Regex mới.
     */
    fun onPatternInputChanged(input: String) {
        _uiState.update {
            it.copy(
                newPatternInput = input,
                errorMessage = validatePattern(input, it.isRegexMode)
            )
        }
    }

    /**
     * Cập nhật chuỗi văn bản thay thế khi người dùng nhập vào ô nhập liệu.
     *
     * @param input Chuỗi mới thay thế (để trống nếu muốn xóa).
     */
    fun onReplacementInputChanged(input: String) {
        _uiState.update {
            it.copy(newReplacementInput = input)
        }
    }

    /**
     * Chuyển đổi chế độ biểu thức chính quy (Regex) khi thêm quy tắc mới.
     *
     * @param isRegex `true` nếu áp dụng chế độ Regex.
     */
    fun onRegexModeChanged(isRegex: Boolean) {
        _uiState.update {
            it.copy(
                isRegexMode = isRegex,
                errorMessage = validatePattern(it.newPatternInput, isRegex)
            )
        }
    }

    /**
     * Bắt đầu chỉnh sửa một quy tắc lọc/thay thế bằng cách mở BottomSheet.
     *
     * @param rule Quy tắc được chọn để chỉnh sửa.
     */
    fun onStartEditRule(rule: ContentFilterRule) {
        _uiState.update { it.copy(editingRule = rule) }
    }

    /**
     * Đóng BottomSheet chỉnh sửa quy tắc.
     */
    fun onDismissEditRule() {
        _uiState.update { it.copy(editingRule = null) }
    }

    /**
     * Lưu thông tin quy tắc sau khi chỉnh sửa vào cấu hình bộ lọc.
     *
     * @param ruleId Định danh duy nhất của quy tắc cần sửa.
     * @param pattern Chuỗi từ gốc hoặc mẫu Regex mới.
     * @param replacement Chuỗi mới thay thế (để trống để xóa).
     * @param isRegex `true` nếu quy tắc sử dụng Regex.
     */
    fun onSaveEditedRule(
        ruleId: String,
        pattern: String,
        replacement: String,
        isRegex: Boolean
    ) {
        val trimmedPattern = pattern.trim()
        val error = validatePattern(trimmedPattern, isRegex)
        if (error != null || trimmedPattern.isEmpty()) return

        preferencesManager.updateFilterPreferences { prefs ->
            val updatedRules = prefs.rules.map { rule ->
                if (rule.id == ruleId) {
                    rule.copy(
                        pattern = trimmedPattern,
                        replacement = replacement,
                        isRegex = isRegex
                    )
                } else rule
            }
            prefs.copy(rules = updatedRules)
        }
        onDismissEditRule()
    }

    /**
     * Thêm quy tắc lọc & thay thế mới từ thông tin người dùng đã nhập.
     */
    fun onAddRule() {
        val currentState = _uiState.value
        val pattern = currentState.newPatternInput.trim()
        val replacement = currentState.newReplacementInput

        val error = validatePattern(pattern, currentState.isRegexMode)
        if (error != null || pattern.isEmpty()) {
            _uiState.update { it.copy(errorMessage = error ?: "Từ khóa không được để trống") }
            return
        }

        val newRule = ContentFilterRule(
            pattern = pattern,
            replacement = replacement,
            isRegex = currentState.isRegexMode,
            isEnabled = true
        )

        preferencesManager.updateFilterPreferences { prefs ->
            prefs.copy(rules = prefs.rules + newRule)
        }

        _uiState.update {
            it.copy(
                newPatternInput = "",
                newReplacementInput = "",
                isRegexMode = false,
                errorMessage = null
            )
        }
    }

    /**
     * Bật hoặc tắt trạng thái kích hoạt của một quy tắc theo ID.
     *
     * @param ruleId Định danh duy nhất của quy tắc cần đổi trạng thái.
     */
    fun onToggleRule(ruleId: String) {
        preferencesManager.updateFilterPreferences { prefs ->
            val updatedRules = prefs.rules.map { rule ->
                if (rule.id == ruleId) rule.copy(isEnabled = !rule.isEnabled) else rule
            }
            prefs.copy(rules = updatedRules)
        }
    }

    /**
     * Xóa một quy tắc khỏi danh sách theo ID.
     *
     * @param ruleId Định danh duy nhất của quy tắc cần xóa.
     */
    fun onDeleteRule(ruleId: String) {
        preferencesManager.updateFilterPreferences { prefs ->
            val updatedRules = prefs.rules.filterNot { it.id == ruleId }
            prefs.copy(rules = updatedRules)
        }
    }

    /**
     * Kiểm tra tính hợp lệ của chuỗi từ khóa hoặc cú pháp biểu thức Regex.
     *
     * @param pattern Chuỗi mẫu cần kiểm tra.
     * @param isRegex `true` nếu là biểu thức Regex.
     * @return Thông báo lỗi nếu cú pháp không hợp lệ, hoặc `null` nếu hợp lệ.
     */
    private fun validatePattern(pattern: String, isRegex: Boolean): String? {
        if (pattern.isBlank()) return null
        return if (isRegex && !isJavaScriptCompatibleRegex(pattern)) {
            context.getString(R.string.content_filter_invalid_regex)
        } else {
            null
        }
    }
}
