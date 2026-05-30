package com.example.adfalls.viewmodel

import androidx.lifecycle.ViewModel
import com.example.adfalls.cache.VideoPlaybackPool
import com.example.adfalls.data.model.AdChannel
import com.example.adfalls.data.model.AdItem
import com.example.adfalls.data.repository.AdRepository

class FeedViewModel : ViewModel() {
    var activeChannel: AdChannel = AdChannel.FEATURED
        private set

    var searchText: String = ""
        private set

    var loadingMore: Boolean = false
        private set

    var ads: List<AdItem> = emptyList()
        private set

    init {
        syncAds()
    }

    fun selectChannel(channel: AdChannel) {
        activeChannel = channel
        syncAds()
    }

    fun updateSearchText(text: String) {
        searchText = text
        syncAds()
    }

    fun refresh() {
        AdRepository.refresh(activeChannel)
        syncAds()
    }

    fun loadMore() {
        if (loadingMore || searchText.isNotBlank()) return
        loadingMore = true
        try {
            AdRepository.loadMore(activeChannel)
            syncAds()
        } finally {
            loadingMore = false
        }
    }

    fun registerClick(adId: Long) {
        AdRepository.registerClick(adId)
        syncAds()
    }

    fun registerImpression(adId: Long): Boolean {
        val changed = AdRepository.registerImpression(adId)
        if (changed) syncAds()
        return changed
    }

    fun toggleLike(adId: Long) {
        AdRepository.toggleLike(adId)
        syncAds()
    }

    fun toggleFavorite(adId: Long) {
        AdRepository.toggleFavorite(adId)
        syncAds()
    }

    fun share(adId: Long) {
        AdRepository.share(adId)
        syncAds()
    }

    fun toggleVideoPlay(adId: Long) {
        VideoPlaybackPool.togglePlay(adId)
        syncAds()
    }

    fun toggleMute(adId: Long) {
        VideoPlaybackPool.toggleMute(adId)
        syncAds()
    }

    fun sync() {
        syncAds()
    }

    private fun syncAds() {
        ads = AdRepository.search(activeChannel, searchText).toList()
    }
}
