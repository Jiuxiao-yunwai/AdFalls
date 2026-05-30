package com.example.adfalls.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.adfalls.cache.VideoPlaybackPool
import com.example.adfalls.data.model.AdChannel
import com.example.adfalls.data.model.AdItem
import com.example.adfalls.data.repository.AdRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class FeedUiState(
    val activeChannel: AdChannel = AdChannel.FEATURED,
    val searchText: String = "",
    val ads: List<AdItem> = emptyList(),
    val loadingMore: Boolean = false,
    val endReached: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
class FeedViewModel : ViewModel() {
    private val activeChannel = MutableStateFlow(AdChannel.FEATURED)
    private val searchText = MutableStateFlow("")
    private val loadingMore = MutableStateFlow(false)
    private val endReached = MutableStateFlow(false)

    private val channelAds = activeChannel.flatMapLatest { channel ->
        AdRepository.observeAdsByChannel(channel).map { ads -> channel to ads }
    }

    val uiState: StateFlow<FeedUiState> = combine(
        searchText,
        channelAds,
        loadingMore,
        endReached
    ) { query, channelAndAds, loading, reached ->
        val (channel, ads) = channelAndAds
        FeedUiState(
            activeChannel = channel,
            searchText = query,
            ads = filterAds(ads, query),
            loadingMore = loading,
            endReached = query.isBlank() && reached
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = FeedUiState()
    )

    fun selectChannel(channel: AdChannel) {
        activeChannel.value = channel
        endReached.value = false
    }

    fun updateSearchText(text: String) {
        searchText.value = text
        endReached.value = false
    }

    fun refresh() {
        viewModelScope.launch {
            AdRepository.refresh(activeChannel.value)
            endReached.value = false
        }
    }

    fun loadMore() {
        val state = uiState.value
        if (state.loadingMore || state.searchText.isNotBlank()) return

        viewModelScope.launch {
            loadingMore.value = true
            try {
                endReached.value = !AdRepository.loadMore(activeChannel.value)
            } finally {
                loadingMore.value = false
            }
        }
    }

    fun registerClick(adId: Long) {
        viewModelScope.launch { AdRepository.registerClick(adId) }
    }

    fun registerImpressions(adIds: List<Long>) {
        if (adIds.isEmpty()) return
        viewModelScope.launch { AdRepository.registerImpressions(adIds) }
    }

    fun toggleLike(adId: Long) {
        viewModelScope.launch { AdRepository.toggleLike(adId) }
    }

    fun toggleFavorite(adId: Long) {
        viewModelScope.launch { AdRepository.toggleFavorite(adId) }
    }

    fun share(adId: Long) {
        viewModelScope.launch { AdRepository.share(adId) }
    }

    fun toggleVideoPlay(adId: Long) {
        viewModelScope.launch { VideoPlaybackPool.togglePlay(adId) }
    }

    fun toggleMute(adId: Long) {
        viewModelScope.launch { VideoPlaybackPool.toggleMute(adId) }
    }

    private fun filterAds(ads: List<AdItem>, query: String): List<AdItem> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return ads
        return ads.filter { ad ->
            ad.title.contains(trimmed, ignoreCase = true) ||
                ad.summary.contains(trimmed, ignoreCase = true) ||
                ad.brand.contains(trimmed, ignoreCase = true) ||
                ad.tags.any { it.contains(trimmed, ignoreCase = true) }
        }
    }
}
