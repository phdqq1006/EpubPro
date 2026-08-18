package com.epubpro.core.database

import androidx.sqlite.db.SupportSQLiteDatabase
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify

class BookBibleMigrationTest {

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
