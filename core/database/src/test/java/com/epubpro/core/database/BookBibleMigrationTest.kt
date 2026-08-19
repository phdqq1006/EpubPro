package com.epubpro.core.database

import androidx.sqlite.db.SupportSQLiteDatabase
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify

class BookBibleMigrationTest {

    /**
     * Kiểm tra migration 3 lên 4 bổ sung tổng số chapter cho sách và tiến độ đọc.
     */
    @Test
    fun migration3To4AddsTotalChaptersColumns() {
        val db = mock(SupportSQLiteDatabase::class.java)

        MIGRATION_3_4.migrate(db)

        verify(db).execSQL("ALTER TABLE books ADD COLUMN totalChapters INTEGER NOT NULL DEFAULT 0")
        verify(db).execSQL("ALTER TABLE reading_progress ADD COLUMN totalChapters INTEGER NOT NULL DEFAULT 0")
    }

    @Test
    fun testMigration4To5ExecutesExpectedSql() {
        val db = mock(SupportSQLiteDatabase::class.java)

        MIGRATION_4_5.migrate(db)

        // Verify that CREATE TABLE for book_bible_editions was called
        verify(db).execSQL(org.mockito.ArgumentMatchers.contains("CREATE TABLE IF NOT EXISTS book_bible_editions"))

        // Verify that CREATE TABLE for book_bible_submissions was called
        verify(db).execSQL(org.mockito.ArgumentMatchers.contains("CREATE TABLE IF NOT EXISTS book_bible_submissions"))

        // Verify that CREATE TABLE for book_bible_snapshots was called
        verify(db).execSQL(org.mockito.ArgumentMatchers.contains("CREATE TABLE IF NOT EXISTS book_bible_snapshots"))

        // Verify that CREATE TABLE for book_bible_timelines was called
        verify(db).execSQL(org.mockito.ArgumentMatchers.contains("CREATE TABLE IF NOT EXISTS book_bible_timelines"))
    }
}
