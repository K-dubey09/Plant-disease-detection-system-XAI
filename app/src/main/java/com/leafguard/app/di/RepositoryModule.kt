package com.leafguard.app.di

import com.leafguard.app.data.local.PlantDao
import com.leafguard.app.data.local.ScanDao
import com.leafguard.app.data.repository.AuthRepositoryImpl
import com.leafguard.app.data.repository.PlantRepositoryImpl
import com.leafguard.app.data.repository.ScanRepositoryImpl
import com.leafguard.app.data.security.PinStore
import com.leafguard.app.domain.repository.AuthRepository
import com.leafguard.app.domain.repository.PlantRepository
import com.leafguard.app.domain.repository.ScanRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

/**
 * Marks the application-scoped [CoroutineScope] — a [SupervisorJob] tied to the singleton component,
 * used by long-lived background work (e.g. [com.leafguard.app.data.inference.SaliencyRunner]) that
 * must outlive any single ViewModel. Unlike `viewModelScope`, this scope is never cancelled while
 * the app process is alive, so a heatmap kicked off from the scan screen keeps running to completion
 * even after the user navigates away and the [ScanViewModel][com.leafguard.app.ui.scan.ScanViewModel]
 * is cleared.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideAuthRepository(
        pinStore: PinStore,
        @IoDispatcher io: CoroutineDispatcher,
    ): AuthRepository = AuthRepositoryImpl(pinStore, io)

    @Provides
    @Singleton
    fun providePlantRepository(
        plantDao: PlantDao,
        @IoDispatcher io: CoroutineDispatcher,
    ): PlantRepository = PlantRepositoryImpl(plantDao, io)

    @Provides
    @Singleton
    fun provideScanRepository(
        scanDao: ScanDao,
        @IoDispatcher io: CoroutineDispatcher,
    ): ScanRepository = ScanRepositoryImpl(scanDao, io)

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(@IoDispatcher io: CoroutineDispatcher): CoroutineScope =
        CoroutineScope(SupervisorJob() + io)
}
