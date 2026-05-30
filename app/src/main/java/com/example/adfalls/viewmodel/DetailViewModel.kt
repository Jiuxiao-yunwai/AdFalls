package com.example.adfalls.viewmodel

import androidx.lifecycle.ViewModel
import com.example.adfalls.cache.VideoPlaybackPool
import com.example.adfalls.data.model.AdCardType
import com.example.adfalls.data.model.AdItem
import com.example.adfalls.data.repository.AdRepository

class DetailViewModel : ViewModel() {
    private var adId: Long = -1L

    var ad: AdItem? = null
        private set

    fun loadAd(adId: Long) {
        this.adId = adId
        sync()
    }

    fun getAd(adId: Long): AdItem? {
        this.adId = adId
        sync()
        return ad
    }

    fun toggleLike() {
        AdRepository.toggleLike(adId)
        sync()
    }

    fun toggleFavorite() {
        AdRepository.toggleFavorite(adId)
        sync()
    }

    fun share() {
        AdRepository.share(adId)
        sync()
    }

    fun playVideo() {
        if (ad?.type == AdCardType.VIDEO) {
            VideoPlaybackPool.play(adId)
            sync()
        }
    }

    fun pauseVideo() {
        if (ad?.type == AdCardType.VIDEO) {
            VideoPlaybackPool.pause(adId)
            sync()
        }
    }

    fun toggleVideoPlay() {
        if (ad?.type == AdCardType.VIDEO) {
            VideoPlaybackPool.togglePlay(adId)
            sync()
        }
    }

    fun toggleMute() {
        if (ad?.type == AdCardType.VIDEO) {
            VideoPlaybackPool.toggleMute(adId)
            sync()
        }
    }

    fun sync() {
        ad = AdRepository.findAd(adId)
    }
}
