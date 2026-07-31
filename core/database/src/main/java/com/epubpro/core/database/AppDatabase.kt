package com.epubpro.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.epubpro.core.database.dao.BookDao
import com.epubpro.core.database.dao.BookmarkDao
import com.epubpro.core.database.dao.SearchDao
import com.epubpro.core.database.entity.*

@Database(
    entities = [
        BookEntity::class,
        ReadingProgressEntity::class,
        BookmarkEntity::class,
        HighlightEntity::class,
        BookSearchEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun searchDao(): SearchDao
}
