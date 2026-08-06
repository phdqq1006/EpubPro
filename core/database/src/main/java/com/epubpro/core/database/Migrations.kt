package com.epubpro.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS ai_rules (
                id TEXT NOT NULL PRIMARY KEY,
                scope TEXT NOT NULL,
                bookId TEXT,
                source TEXT NOT NULL,
                action TEXT NOT NULL,
                replacement TEXT,
                caseSensitive INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL,
                FOREIGN KEY(bookId) REFERENCES books(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_ai_rules_bookId ON ai_rules(bookId)"
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS ai_chapter_cache (
                id TEXT NOT NULL PRIMARY KEY,
                bookId TEXT NOT NULL,
                chapterIndex INTEGER NOT NULL,
                sourceHash TEXT NOT NULL,
                configHash TEXT NOT NULL,
                status TEXT NOT NULL,
                filePath TEXT,
                modelId TEXT NOT NULL,
                completedParts INTEGER NOT NULL,
                totalParts INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL,
                FOREIGN KEY(bookId) REFERENCES books(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE UNIQUE INDEX IF NOT EXISTS index_ai_chapter_cache_bookId_chapterIndex
            ON ai_chapter_cache(bookId, chapterIndex)
            """.trimIndent()
        )
    }
}
