package com.leafguard.app.di

import android.content.Context
import com.leafguard.app.data.security.EncryptedPinStore
import com.leafguard.app.data.security.PinHasher
import com.leafguard.app.data.security.PinStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SecurityModule {

    @Provides
    @Singleton
    fun providePinHasher(): PinHasher = PinHasher()

    @Provides
    @Singleton
    fun providePinStore(
        @ApplicationContext context: Context,
        hasher: PinHasher,
    ): PinStore = EncryptedPinStore(context, hasher)
}
