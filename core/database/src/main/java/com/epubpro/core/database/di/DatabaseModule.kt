package com.epubpro.core.database.di

import android.content.Context
import androidx.room.Room
import com.epubpro.core.database.AppDatabase
import com.epubpro.core.database.MIGRATION_2_3
import com.epubpro.core.database.MIGRATION_3_4
import com.epubpro.core.database.dao.AiChapterDao
import com.epubpro.core.database.dao.AiRuleDao
import com.epubpro.core.database.dao.BookDao
import com.epubpro.core.database.dao.BookmarkDao
import com.epubpro.core.database.dao.SearchDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context
    ): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "epubpro.db"
        ).addMigrations(MIGRATION_2_3, MIGRATION_3_4).fallbackToDestructiveMigration().build()
    }

    @Provides
    fun provideBookDao(db: AppDatabase): BookDao = db.bookDao()

    @Provides
    fun provideBookmarkDao(db: AppDatabase): BookmarkDao = db.bookmarkDao()

    @Provides
    fun provideSearchDao(db: AppDatabase): SearchDao = db.searchDao()

    @Provides
    fun provideAiRuleDao(db: AppDatabase): AiRuleDao = db.aiRuleDao()

    @Provides
    fun provideAiChapterDao(db: AppDatabase): AiChapterDao = db.aiChapterDao()
}
