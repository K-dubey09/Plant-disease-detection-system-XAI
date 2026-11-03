package com.leafguard.app.di

import android.content.Context
import androidx.room.Room
import com.leafguard.app.data.local.LeafGuardDatabase
import com.leafguard.app.data.local.PlantDao
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
    fun provideDatabase(@ApplicationContext context: Context): LeafGuardDatabase =
        Room.databaseBuilder(context, LeafGuardDatabase::class.java, LeafGuardDatabase.NAME)
            .build()

    @Provides
    fun providePlantDao(database: LeafGuardDatabase): PlantDao = database.plantDao()
}
