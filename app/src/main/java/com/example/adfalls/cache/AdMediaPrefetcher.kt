package com.example.adfalls.cache

import android.content.Context
import com.example.adfalls.data.model.AdItem
import com.example.adfalls.ui.common.RemoteImageLoader
import kotlinx.coroutines.CoroutineScope

object AdMediaPrefetcher {
    fun prefetch(scope: CoroutineScope, context: Context, ads: List<AdItem>) {
        val appContext = context.applicationContext
        ads.forEach { ad ->
            RemoteImageLoader.prefetch(scope, appContext, ad.coverUrl)
            RemoteVideoCache.prefetch(scope, appContext, ad.videoUrl)
        }
    }
}
