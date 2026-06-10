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
    ): DatabaseProvider {
        return try {
            val provider = StandaloneDatabaseProvider(context)
            // Force open to check for SQLite database corruption early
            provider.writableDatabase 
            provider
        } catch (e: Exception) {
            Log.e("AppModule", "ExoPlayer internal database corrupted. Deleting...", e)
            try {
                // Yehi tha wo hidden villain jo crash karwa raha tha!
                context.deleteDatabase("exoplayer_internal.db")
            } catch (ex: Exception) {
                Log.e("AppModule", "Failed to delete corrupted DB", ex)
            }
            StandaloneDatabaseProvider(context)
        }
    }

    @Singleton
    @Provides
    @PlayerCache
    fun providePlayerCache(
        @ApplicationContext context: Context,
        databaseProvider: DatabaseProvider,
    ): SimpleCache {
        val cacheDir = context.filesDir.resolve("exoplayer")
        
        fun createCache() = SimpleCache(
            cacheDir,
            when (val cacheSize = try { context.dataStore[MaxSongCacheSizeKey] ?: 1024 } catch(e: Exception) { 1024 }) {
                -1 -> NoOpCacheEvictor()
                else -> LeastRecentlyUsedCacheEvictor(cacheSize * 1024 * 1024L)
            },
            databaseProvider,
        )
        
        return try {
            val cache = createCache()
            cache.release()
            createCache()
        } catch (e: Exception) {
            Log.e("AppModule", "Player cache corrupted, creating a new one", e)
            cacheDir.deleteRecursively()
            try {
                context.deleteDatabase("exoplayer_internal.db")
            } catch (ex: Exception) {}
            
            try {
                createCache()
            } catch (e2: Exception) {
                // Absolute fallback to prevent splash screen crash
                val tempDir = context.filesDir.resolve("exoplayer_fallback_${System.currentTimeMillis()}")
                SimpleCache(tempDir, NoOpCacheEvictor(), databaseProvider)
            }
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
        
        fun createCache() = SimpleCache(cacheDir, NoOpCacheEvictor(), databaseProvider)
        
        return try {
            val cache = createCache()
            cache.release()
            createCache()
        } catch (e: Exception) {
            Log.e("AppModule", "Download cache corrupted, creating a new one", e)
            cacheDir.deleteRecursively()
            try {
                createCache()
            } catch (e2: Exception) {
                val tempDir = context.filesDir.resolve("download_fallback_${System.currentTimeMillis()}")
                SimpleCache(tempDir, NoOpCacheEvictor(), databaseProvider)
            }
        }
    }
}
