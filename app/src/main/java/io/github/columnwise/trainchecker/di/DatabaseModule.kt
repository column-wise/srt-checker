package io.github.columnwise.trainchecker.di

import android.content.Context
import androidx.room.Room
import io.github.columnwise.trainchecker.data.db.AppDatabase
import io.github.columnwise.trainchecker.data.db.WatchJobDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): AppDatabase =
        Room.databaseBuilder(ctx, AppDatabase::class.java, "trainchecker.db").build()

    @Provides
    fun provideWatchJobDao(db: AppDatabase): WatchJobDao = db.watchJobDao()
}
