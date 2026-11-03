package com.leafguard.app.di

import com.leafguard.app.data.repository.AuthRepositoryImpl
import com.leafguard.app.data.security.PinStore
import com.leafguard.app.domain.repository.AuthRepository
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
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO
}
