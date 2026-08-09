package com.epubpro.core.reader.tts

import com.epubpro.core.reader.engine.EpubChapterHeader
import com.epubpro.core.reader.engine.EpubEngine
import com.epubpro.core.storage.EpubStorageManager
import com.epubpro.domain.model.AiChapterStatus
import com.epubpro.domain.model.Book
import com.epubpro.domain.model.ReadingProgress
import com.epubpro.domain.model.TtsChunk
import com.epubpro.domain.repository.AiChapterRepository
import com.epubpro.domain.repository.BookRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject

data class TtsChapterContent(
    val chapterIndex: Int,
    val chapterTitle: String,
    val chunks: List<TtsChunk>,
    val usesAiContent: Boolean
)

class TtsChapterPlaybackCoordinator @Inject constructor(
    private val bookRepository: BookRepository,
    private val aiChapterRepository: AiChapterRepository,
    private val storageManager: EpubStorageManager,
    private val epubEngine: EpubEngine
) {
    private data class Session(
        val book: Book,
        val file: File,
        val headers: List<EpubChapterHeader>,
        val preferAiContent: Boolean
    )

    private val preparationGeneration = AtomicLong(0L)

    @Volatile
    private var session: Session? = null

    suspend fun prepare(bookId: String, preferAiContent: Boolean) {
        val requestGeneration = preparationGeneration.incrementAndGet()
        withContext(Dispatchers.IO) {
            val current = session
            if (current?.book?.id == bookId && current.preferAiContent == preferAiContent) {
                return@withContext
            }
            val book = bookRepository.getBookById(bookId)
                ?: error("Không tìm thấy sách để tiếp tục TTS")
            val file = storageManager.getBookFile(book.filePath)
            require(file.isFile) { "Tệp EPUB không còn tồn tại" }
            val preparedSession = Session(
                book = book,
                file = file,
                headers = epubEngine.extractChapterHeaders(file),
                preferAiContent = preferAiContent
            )
            if (requestGeneration == preparationGeneration.get()) {
                session = preparedSession
            }
        }
    }

    suspend fun loadChapter(chapterIndex: Int): TtsChapterContent? = withContext(Dispatchers.IO) {
        val activeSession = session ?: error("Phiên TTS chưa được chuẩn bị")
        val header = activeSession.headers.getOrNull(chapterIndex) ?: return@withContext null
        val sourceHtml = epubEngine.loadChapterHtml(activeSession.file, header.entryName)
        val aiHtml = if (activeSession.preferAiContent) {
            loadValidAiCache(activeSession.book.id, chapterIndex, sourceHtml)
        } else {
            null
        }
        val chunks = TtsTextParser.parseHtmlToChunks(aiHtml ?: sourceHtml)
        TtsChapterContent(
            chapterIndex = chapterIndex,
            chapterTitle = header.title,
            chunks = chunks,
            usesAiContent = aiHtml != null
        )
    }

    suspend fun saveChapterProgress(chapterIndex: Int) {
        val activeSession = session ?: return
        val totalChapters = activeSession.headers.size.coerceAtLeast(1)
        bookRepository.saveReadingProgress(
            ReadingProgress(
                bookId = activeSession.book.id,
                currentCfi = "",
                chapterIndex = chapterIndex,
                pageIndex = 1,
                progressPercentage =
                    ((chapterIndex + 1f) / totalChapters).coerceIn(0f, 1f)
            )
        )
    }
    fun clear() {
        preparationGeneration.incrementAndGet()
        session = null
    }

    private suspend fun loadValidAiCache(
        bookId: String,
        chapterIndex: Int,
        sourceHtml: String
    ): String? {
        val cache = aiChapterRepository.getChapterCache(bookId, chapterIndex)
            ?.takeIf { it.status == AiChapterStatus.COMPLETE }
            ?.takeIf { it.sourceHash == sha256(sourceHtml) }
            ?: return null
        return storageManager.readAiChapter(cache.filePath)
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
