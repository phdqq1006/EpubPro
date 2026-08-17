package com.epubpro.feature.library.online

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.epubpro.domain.model.OnlineChapterContent
import com.epubpro.domain.repository.OnlineNovelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChapterReaderUiState(
    val novelId: String = "",
    val chapterIndex: Int = 1,
    val version: String = "translated",
    val content: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class OnlineChapterReaderViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val onlineNovelRepository: OnlineNovelRepository
) : ViewModel() {

    private val novelId: String = checkNotNull(savedStateHandle["novelId"])
    private val chapterIndex: Int = checkNotNull(savedStateHandle.get<String>("chapterIndex")?.toIntOrNull() ?: 1)

    private val _uiState = MutableStateFlow(
        ChapterReaderUiState(novelId = novelId, chapterIndex = chapterIndex, isLoading = true)
    )
    val uiState: StateFlow<ChapterReaderUiState> = _uiState.asStateFlow()

    init {
        loadContent("translated")
    }

    fun loadContent(version: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, version = version, errorMessage = null) }
            onlineNovelRepository.getChapterContent(novelId, chapterIndex, version)
                .onSuccess { contentDto ->
                    _uiState.update {
                        it.copy(
                            content = contentDto.content,
                            isLoading = false,
                            errorMessage = null
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = err.message ?: "Không thể tải nội dung chương"
                        )
                    }
                }
        }
    }

    fun toggleVersion() {
        val nextVersion = if (_uiState.value.version == "translated") "original" else "translated"
        loadContent(nextVersion)
    }
}
