package com.example.adfalls.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.adfalls.cache.VideoPlaybackPool
import com.example.adfalls.data.model.AdCardType
import com.example.adfalls.data.model.AdItem
import com.example.adfalls.data.repository.AdRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DetailUiState(
    val ad: AdItem? = null,
    val loading: Boolean = true,
    val empty: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
class DetailViewModel : ViewModel() {
    private val adId = MutableStateFlow<Long?>(null)

    val uiState: StateFlow<DetailUiState> = adId
        .filterNotNull()
        .flatMapLatest { id ->
            AdRepository.observeAdById(id)
                .map { ad -> DetailUiState(ad = ad, loading = false, empty = ad == null) }
                .onStart { emit(DetailUiState(loading = true)) }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = DetailUiState()
        )

    fun loadAd(adId: Long) {
        this.adId.value = adId
    }

    fun toggleLike() {
        val id = adId.value ?: return
        viewModelScope.launch { AdRepository.toggleLike(id) }
    }

    fun toggleFavorite() {
        val id = adId.value ?: return
        viewModelScope.launch { AdRepository.toggleFavorite(id) }
    }

    fun share() {
        val id = adId.value ?: return
        viewModelScope.launch { AdRepository.share(id) }
    }

    fun playVideo() {
        withVideoAd { id -> VideoPlaybackPool.play(id) }
    }

    fun pauseVideo() {
        withVideoAd { id -> VideoPlaybackPool.pause(id) }
    }

    fun toggleVideoPlay() {
        withVideoAd { id -> VideoPlaybackPool.togglePlay(id) }
    }

    fun toggleMute() {
        withVideoAd { id -> VideoPlaybackPool.toggleMute(id) }
    }

    private fun withVideoAd(action: suspend (Long) -> Unit) {
        val id = adId.value ?: return
        viewModelScope.launch {
            val ad = uiState.value.ad ?: AdRepository.findAd(id)
            if (ad?.type == AdCardType.VIDEO) {
                action(id)
            }
        }
    }
}
