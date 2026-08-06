package com.epubpro.feature.profile.filter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.epubpro.core.storage.ReaderPreferencesManager
import com.epubpro.domain.model.ContentFilterPreferences
import com.epubpro.domain.model.ContentFilterRule
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.regex.Pattern
import javax.inject.Inject

data class ContentFilterSettingsUiState(
    val preferences: ContentFilterPreferences = ContentFilterPreferences(),
    val newPatternInput: String = "",
    val isRegexMode: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class ContentFilterSettingsViewModel @Inject constructor(
    private val preferencesManager: ReaderPreferencesManager
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

    fun onToggleFilter(enabled: Boolean) {
        preferencesManager.updateFilterPreferences { prefs ->
            prefs.copy(isFilterEnabled = enabled)
        }
    }

    fun onPatternInputChanged(input: String) {
        _uiState.update {
            it.copy(
                newPatternInput = input,
                errorMessage = validatePattern(input, it.isRegexMode)
            )
        }
    }

    fun onRegexModeChanged(isRegex: Boolean) {
        _uiState.update {
            it.copy(
                isRegexMode = isRegex,
                errorMessage = validatePattern(it.newPatternInput, isRegex)
            )
        }
    }

    fun onAddRule() {
        val currentState = _uiState.value
        val pattern = currentState.newPatternInput.trim()

        val error = validatePattern(pattern, currentState.isRegexMode)
        if (error != null || pattern.isEmpty()) {
            _uiState.update { it.copy(errorMessage = error ?: "Từ khóa không được để trống") }
            return
        }

        val newRule = ContentFilterRule(
            pattern = pattern,
            isRegex = currentState.isRegexMode,
            isEnabled = true
        )

        preferencesManager.updateFilterPreferences { prefs ->
            prefs.copy(rules = prefs.rules + newRule)
        }

        _uiState.update {
            it.copy(
                newPatternInput = "",
                isRegexMode = false,
                errorMessage = null
            )
        }
    }

    fun onToggleRule(ruleId: String) {
        preferencesManager.updateFilterPreferences { prefs ->
            val updatedRules = prefs.rules.map { rule ->
                if (rule.id == ruleId) rule.copy(isEnabled = !rule.isEnabled) else rule
            }
            prefs.copy(rules = updatedRules)
        }
    }

    fun onDeleteRule(ruleId: String) {
        preferencesManager.updateFilterPreferences { prefs ->
            val updatedRules = prefs.rules.filterNot { it.id == ruleId }
            prefs.copy(rules = updatedRules)
        }
    }

    private fun validatePattern(pattern: String, isRegex: Boolean): String? {
        if (pattern.isBlank()) return null
        if (isRegex) {
            return runCatching {
                Pattern.compile(pattern)
                null
            }.getOrElse {
                "Cú pháp Regex không hợp lệ"
            }
        }
        return null
    }
}
