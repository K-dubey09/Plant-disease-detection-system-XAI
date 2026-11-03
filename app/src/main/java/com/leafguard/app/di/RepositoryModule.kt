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
import kotlinx.coroutines.Dispatchers
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

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
}
