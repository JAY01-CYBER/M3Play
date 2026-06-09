/*
 * Copyright (c) 2026 JAY01-CYBER
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.jay.m3play

import android.app.Application
import android.content.Context
import android.os.Build
import android.widget.Toast
import android.widget.Toast.LENGTH_SHORT
import androidx.datastore.preferences.core.edit
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.request.CachePolicy

import com.jay.innertube.YouTube
import com.jay.innertube.models.YouTubeLocale
import com.jay.kugou.KuGou 

import com.jay.m3play.constants.AccountChannelHandleKey
import com.jay.m3play.constants.AccountEmailKey
import com.jay.m3play.constants.AccountNameKey
import com.jay.m3play.constants.ContentCountryKey
import com.jay.m3play.constants.ContentLanguageKey
import com.jay.m3play.constants.CountryCodeToName
import com.jay.m3play.constants.DataSyncIdKey
import com.jay.m3play.constants.InnerTubeCookieKey
import com.jay.m3play.constants.LanguageCodeToName
import com.jay.m3play.constants.MaxImageCacheSizeKey
import com.jay.m3play.constants.ProxyEnabledKey
import com.jay.m3play.constants.ProxyTypeKey
import com.jay.m3play.constants.ProxyUrlKey
import com.jay.m3play.constants.SYSTEM_DEFAULT
import com.jay.m3play.constants.UseLoginForBrowse
import com.jay.m3play.constants.VisitorDataKey
import com.jay.m3play.extensions.toEnum
import com.jay.m3play.extensions.toInetSocketAddress
import com.jay.m3play.utils.dataStore
import com.jay.m3play.utils.get
import com.jay.m3play.utils.reportException

import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.net.Proxy
import java.util.Locale

@HiltAndroidApp
class App : Application(), ImageLoaderFactory {
    @OptIn(DelicateCoroutinesApi::class)
    override fun onCreate() {
        super.onCreate()
        instance = this
        Timber.plant(Timber.DebugTree())

        GlobalScope.launch(Dispatchers.IO) {
            val locale = Locale.getDefault()
            val languageTag = locale.toLanguageTag().replace("-Hant", "") 
            
            YouTube.locale = YouTubeLocale(
                gl = dataStore.data.first()[ContentCountryKey]?.takeIf { it != SYSTEM_DEFAULT }
                    ?: locale.country.takeIf { it in CountryCodeToName }
                    ?: "US",
                hl = dataStore.data.first()[ContentLanguageKey]?.takeIf { it != SYSTEM_DEFAULT }
                    ?: locale.language.takeIf { it in LanguageCodeToName }
                    ?: languageTag.takeIf { it in LanguageCodeToName }
                    ?: "en"
            )
            
            if (languageTag == "zh-TW") {
                KuGou.useTraditionalChinese = true
            }

            if (dataStore.data.first()[ProxyEnabledKey] == true) {
                try {
                    val proxyUrl = dataStore.data.first()[ProxyUrlKey]
                    if (!proxyUrl.isNullOrBlank()) {
                        YouTube.proxy = Proxy(
                            dataStore.data.first()[ProxyTypeKey].toEnum(defaultValue = Proxy.Type.HTTP),
                            proxyUrl.toInetSocketAddress()
                        )
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@App, "Failed to parse proxy url.", LENGTH_SHORT).show()
                    }
                    reportException(e)
                }
            }

            if (dataStore.data.first()[UseLoginForBrowse] != false) {
                YouTube.useLoginForBrowse = true
            }
        }

        GlobalScope.launch {
            dataStore.data
                .map { it[VisitorDataKey] }
                .distinctUntilChanged()
                .collect { visitorData ->
                    YouTube.visitorData = visitorData
                        ?.takeIf { it != "null" } 
                        ?: YouTube.visitorData().onFailure {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@App, "Failed to get visitorData.", LENGTH_SHORT).show()
                            }
                            reportException(it)
                        }.getOrNull()?.also { newVisitorData ->
                            dataStore.edit { settings ->
                                settings[VisitorDataKey] = newVisitorData
                            }
                        }
                }
        }
        
        GlobalScope.launch {
            dataStore.data
                .map { it[DataSyncIdKey] }
                .distinctUntilChanged()
                .collect { dataSyncId ->
                    YouTube.dataSyncId = dataSyncId?.let {
                        it.takeIf { !it.contains("||") }
                            ?: it.takeIf { it.endsWith("||") }?.substringBefore("||")
                            ?: it.substringAfter("||")
                    }
                }
        }
        
        GlobalScope.launch {
            dataStore.data
                .map { it[InnerTubeCookieKey] }
                .distinctUntilChanged()
                .collect { cookie ->
                    try {
                        YouTube.cookie = cookie
                    } catch (e: Exception) {
                        Timber.e("Could not parse cookie. Clearing existing cookie. %s", e.message)
                        forgetAccount(this@App)
                    }
                }
        }
    }

    override fun newImageLoader(): ImageLoader {
        val cacheSize = dataStore[MaxImageCacheSizeKey]

        if (cacheSize == 0) {
            return ImageLoader.Builder(this)
                .crossfade(true)
                .respectCacheHeaders(false)
                .allowHardware(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                .diskCachePolicy(CachePolicy.DISABLED)
                .build()
        }

        return ImageLoader.Builder(this)
            .crossfade(true)
            .respectCacheHeaders(false)
            .allowHardware(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            .diskCache(
                DiskCache.Builder()
                    .directory(cacheDir.resolve("coil"))
                    .maxSizeBytes((cacheSize ?: 512) * 1024 * 1024L)
                    .build()
            )
            .build()
    }

    companion object {
        lateinit var instance: App
            private set

        fun forgetAccount(context: Context) {
            runBlocking {
                context.dataStore.edit { settings ->
                    settings.remove(InnerTubeCookieKey)
                    settings.remove(VisitorDataKey)
                    settings.remove(DataSyncIdKey)
                    settings.remove(AccountNameKey)
                    settings.remove(AccountEmailKey)
                    settings.remove(AccountChannelHandleKey)
                }
            }
        }
    }
}
