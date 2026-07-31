package com.epubpro.core.database.di

import com.epubpro.core.database.repository.BookRepositoryImpl
import com.epubpro.core.database.repository.BookmarkRepositoryImpl
import com.epubpro.core.database.repository.SearchRepositoryImpl
import com.epubpro.domain.repository.BookRepository
import com.epubpro.domain.repository.BookmarkRepository
import com.epubpro.domain.repository.SearchRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindBookRepository(
        impl: BookRepositoryImpl
    ): BookRepository

    @Binds
    @Singleton
    abstract fun bindBookmarkRepository(
        impl: BookmarkRepositoryImpl
    ): BookmarkRepository

    @Binds
    @Singleton
    abstract fun bindSearchRepository(
        impl: SearchRepositoryImpl
    ): SearchRepository
}
