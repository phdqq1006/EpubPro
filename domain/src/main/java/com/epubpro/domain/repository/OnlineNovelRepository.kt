package com.epubpro.domain.repository

import com.epubpro.domain.model.*
import kotlinx.coroutines.flow.Flow

interface OnlineNovelRepository {
    suspend fun getNovels(): Result<List<OnlineNovelSummary>>
    
    suspend fun getNovelDetail(novelId: String): Result<OnlineNovelDetail>
    
    suspend fun getChapterContent(
        novelId: String,
        chapterIndex: Int,
        version: String = "translated"
    ): Result<OnlineChapterContent>
    
    fun downloadEpub(novelId: String, saveFileName: String): Flow<DownloadState>
    
    suspend fun translateChapter(
        novelId: String,
        chapterIndex: Int,
        apiKey: String,
        provider: String,
        model: String
    ): Result<TranslateChapterResult>
    
    suspend fun uploadEpub(
        filePath: String,
        isTranslated: Boolean
    ): Result<String>
    
    fun getBaseUrl(): Flow<String>
    
    suspend fun setBaseUrl(url: String)
    
    suspend fun testServerConnection(): Result<Boolean>
}
