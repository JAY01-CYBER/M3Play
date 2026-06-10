package com.jay.m3play.di

import android.content.Context
import android.util.Log
import androidx.media3.database.DatabaseProvider
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import com.jay.m3play.constants.MaxSongCacheSizeKey
import com.jay.m3play.db.InternalDatabase
import com.jay.m3play.db.MusicDatabase
import com.jay.m3play.utils.dataStore
import com.jay.m3play.utils.get
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class PlayerCache

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DownloadCache

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Singleton
    @Provides
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): MusicDatabase = InternalDatabase.newInstance(context)

    @Singleton
    @Provides
    fun provideDatabaseProvider(
        @ApplicationContext context: Context,
    ): DatabaseProvider = StandaloneDatabaseProvider(context)

    @Singleton
    @Provides
    @PlayerCache
    fun providePlayerCache(
        @ApplicationContext context: Context,
        databaseProvider: DatabaseProvider,
    ): SimpleCache {
        val cacheDir = context.filesDir.resolve("exoplayer")
        val constructor = {
            SimpleCache(
                cacheDir,
                when (val cacheSize = context.dataStore[MaxSongCacheSizeKey] ?: 1024) {
                    -1 -> NoOpCacheEvictor()
                    else -> LeastRecentlyUsedCacheEvictor(cacheSize * 1024 * 1024L)
                },
                databaseProvider,
            )
        }
        
        return try {
            constructor().release()
            constructor()
        } catch (e: Exception) {
            // Cache Corruption Fix: Agar ExoPlayer ka cache corrupt hoga toh app crash nahi karega, 
            // corrupt files ko safely delete karke naya cache bana lega.
            Log.e("AppModule", "Player cache corrupted, deleting and creating a new one", e)
            cacheDir.deleteRecursively()
            constructor()
        }
    }

    @Singleton
    @Provides
    @DownloadCache
    fun provideDownloadCache(
        @ApplicationContext context: Context,
        databaseProvider: DatabaseProvider,
    ): SimpleCache {
        val cacheDir = context.filesDir.resolve("download")
        val constructor = {
            SimpleCache(cacheDir, NoOpCacheEvictor(), databaseProvider)
        }
        
        return try {
            constructor().release()
            constructor()
        } catch (e: Exception) {
            // Download Cache Corruption Fix
            Log.e("AppModule", "Download cache corrupted, deleting and creating a new one", e)
            cacheDir.deleteRecursively()
            constructor()
        }
    }
}
