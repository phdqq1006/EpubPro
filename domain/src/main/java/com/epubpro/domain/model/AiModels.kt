package com.epubpro.domain.model

data class AiModelOption(
    val id: String,
    val displayName: String
)

data class AiSettings(
    val modelId: String = DEFAULT_AI_MODEL_ID,
    val hasApiKey: Boolean = false
)

enum class AiRuleScope {
    GLOBAL,
    BOOK
}

enum class AiRuleAction {
    KEEP,
    REPLACE
}

data class AiRule(
    val id: String,
    val scope: AiRuleScope,
    val bookId: String?,
    val source: String,
    val action: AiRuleAction,
    val replacement: String?,
    val caseSensitive: Boolean,
    val updatedAt: Long
)

enum class AiChapterStatus {
    PARTIAL,
    COMPLETE
}

data class AiChapterCache(
    val id: String,
    val bookId: String,
    val chapterIndex: Int,
    val sourceHash: String,
    val configHash: String,
    val status: AiChapterStatus,
    val filePath: String?,
    val modelId: String,
    val completedParts: Int,
    val totalParts: Int,
    val updatedAt: Long
)

const val DEFAULT_AI_MODEL_ID = "gemini-2.5-flash"

val SUPPORTED_GEMINI_MODELS = listOf(
    AiModelOption(DEFAULT_AI_MODEL_ID, "Gemini 2.5 Flash"),
    AiModelOption("gemini-2.5-flash-lite", "Gemini 2.5 Flash Lite"),
    AiModelOption("gemini-2.5-pro", "Gemini 2.5 Pro")
)
