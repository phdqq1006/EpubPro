package com.epubpro.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.PrimaryKey
import com.epubpro.domain.model.Book
import com.epubpro.domain.model.Bookmark
import com.epubpro.domain.model.Highlight
import com.epubpro.domain.model.ReadingProgress

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey val id: String,
    val title: String,
    val author: String,
    val coverPath: String?,
    val filePath: String,
    val addedAt: Long,
    val lastReadAt: Long
) {
    fun toDomain() = Book(id, title, author, coverPath, filePath, addedAt, lastReadAt)
    companion object {
        fun fromDomain(book: Book) = BookEntity(book.id, book.title, book.author, book.coverPath, book.filePath, book.addedAt, book.lastReadAt)
    }
}

@Entity(tableName = "reading_progress")
data class ReadingProgressEntity(
    @PrimaryKey val bookId: String,
    val currentCfi: String,
    val chapterIndex: Int,
    val pageIndex: Int = 1,
    val progressPercentage: Float
) {
    fun toDomain() = ReadingProgress(bookId, currentCfi, chapterIndex, pageIndex, progressPercentage)
    companion object {
        fun fromDomain(rp: ReadingProgress) = ReadingProgressEntity(rp.bookId, rp.currentCfi, rp.chapterIndex, rp.pageIndex, rp.progressPercentage)
    }
}

@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val chapterIndex: Int,
    val chapterTitle: String,
    val cfi: String,
    val createdAt: Long
) {
    fun toDomain() = Bookmark(id, bookId, chapterIndex, chapterTitle, cfi, createdAt)
    companion object {
        fun fromDomain(bm: Bookmark) = BookmarkEntity(bm.id, bm.bookId, bm.chapterIndex, bm.chapterTitle, bm.cfi, bm.createdAt)
    }
}

@Entity(tableName = "highlights")
data class HighlightEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val chapterIndex: Int,
    val startCfi: String,
    val endCfi: String,
    val selectedText: String,
    val colorHex: String,
    val note: String?,
    val createdAt: Long
) {
    fun toDomain() = Highlight(id, bookId, chapterIndex, startCfi, endCfi, selectedText, colorHex, note, createdAt)
    companion object {
        fun fromDomain(hl: Highlight) = HighlightEntity(hl.id, hl.bookId, hl.chapterIndex, hl.startCfi, hl.endCfi, hl.selectedText, hl.colorHex, hl.note, hl.createdAt)
    }
}

/**
 * Room FTS Virtual Table for Full-Text Search
 */
@Fts4
@Entity(tableName = "book_search_fts")
data class BookSearchEntity(
    @ColumnInfo(name = "rowid") @PrimaryKey val rowid: Int = 0,
    @ColumnInfo(name = "bookId") val bookId: String,
    @ColumnInfo(name = "chapterIndex") val chapterIndex: String,
    @ColumnInfo(name = "chapterTitle") val chapterTitle: String,
    @ColumnInfo(name = "textContent") val textContent: String
)
