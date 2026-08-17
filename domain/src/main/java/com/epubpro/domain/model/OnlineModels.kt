package com.epubpro.domain.model

data class OnlineNovelSummary(
    val novelId: String,
    val title: String,
    val originalTitle: String? = null,
    val author: String,
    val genres: List<String> = emptyList(),
    val coverUrl: String? = null,
    val totalChapters: Int = 0,
    val translatedChapters: Int = 0,
    val status: String = "ongoing",
    val updatedAt: String? = null
) {
    val isCompleted: Boolean get() = status.equals("completed", ignoreCase = true)
    val translationProgress: Float
        get() = if (totalChapters > 0) translatedChapters.toFloat() / totalChapters else 0f
}

data class OnlineNovelDetail(
    val novelId: String,
    val title: String,
    val author: String,
    val description: String? = null,
    val coverUrl: String? = null,
    val totalChapters: Int = 0,
    val translatedChapters: Int = 0,
    val chapters: List<OnlineChapterSummary> = emptyList()
) {
    val translationProgress: Float
        get() = if (totalChapters > 0) translatedChapters.toFloat() / totalChapters else 0f
}

data class OnlineChapterSummary(
    val chapterIndex: Int,
    val chapterTitle: String,
    val status: String = "raw",
    val wordCount: Int = 0,
    val translatedTextPreview: String? = null
) {
    val isTranslated: Boolean get() = status.equals("completed", ignoreCase = true)
}

data class OnlineChapterContent(
    val novelId: String,
    val chapterIndex: Int,
    val version: String, // "translated" hoặc "original"
    val content: String
)

data class TranslateChapterResult(
    val message: String,
    val chapter: OnlineChapterSummary? = null
)

sealed class DownloadState {
    object Idle : DownloadState()
    data class Downloading(val progressPercent: Int) : DownloadState()
    data class Success(val filePath: String) : DownloadState()
    data class Error(val message: String) : DownloadState()
}
