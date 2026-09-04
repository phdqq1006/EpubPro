package com.epubpro.core.storage.sync

import com.epubpro.domain.sync.SyncCoordinator
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Wiring Hilt cho public sync coordinator. */
@Module
@InstallIn(SingletonComponent::class)
abstract class SyncModule {
    /**
     * Bind implementation vào domain contract để UI không phụ thuộc data source.
     *
     * @param implementation Coordinator production.
     * @return Coordinator public.
     */
    @Binds
    abstract fun bindSyncCoordinator(implementation: SyncCoordinatorImpl): SyncCoordinator
}
