package com.example.di

import com.example.data.firestore.FirestoreRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideFirestoreRepository(): FirestoreRepository {
        return com.example.EdgeTraderApp.instance.firestoreRepository
    }
}
