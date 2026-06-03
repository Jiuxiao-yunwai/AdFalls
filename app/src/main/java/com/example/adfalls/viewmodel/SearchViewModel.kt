package com.example.adfalls.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.adfalls.cache.VideoPlaybackPool
import com.example.adfalls.data.model.AdChannel
import com.example.adfalls.data.model.AdItem
import com.example.adfalls.data.repository.AdRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchUiState(
    val activeChannel: AdChannel = AdChannel.FEATURED,
    val searchText: String = "",
    val ads: List<AdItem> = emptyList(),
    val loading: Boolean = false
)

class SearchViewModel : ViewModel() {
    private val mutableUiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = mutableUiState.asStateFlow()
    private var searchJob: Job? = null

    fun selectChannel(channel: AdChannel) {
        mutableUiState.update { it.copy(activeChannel = channel, ads = emptyList(), loading = false) }
    }

    fun updateSearchText(text: String) {
        val query = text.trim()
        searchJob?.cancel()
        mutableUiState.update { it.copy(searchText = text, loading = query.isNotEmpty(), ads = if (query.isEmpty()) emptyList() else it.ads) }
        if (query.isEmpty()) return

        searchJob = viewModelScope.launch {
            val channel = mutableUiState.value.activeChannel
            val results = AdRepository.searchAds(channel, query)
            mutableUiState.update { state ->
                if (state.searchText.trim() == query && state.activeChannel == channel) {
                    state.copy(ads = results, loading = false)
                } else {
                    state
                }
            }
        }
    }

    fun registerClick(adId: Long) {
        viewModelScope.launch { AdRepository.registerClick(adId) }
    }

    fun toggleLike(adId: Long) {
        viewModelScope.launch {
            AdRepository.toggleLike(adId)
            syncAd(adId)
        }
    }

    fun toggleFavorite(adId: Long) {
        viewModelScope.launch {
            AdRepository.toggleFavorite(adId)
            syncAd(adId)
        }
    }

    fun share(adId: Long) {
        viewModelScope.launch {
            AdRepository.share(adId)
            syncAd(adId)
        }
    }

    fun toggleVideoPlay(adId: Long) {
        viewModelScope.launch { VideoPlaybackPool.togglePlay(adId) }
    }

    fun toggleMute(adId: Long) {
        viewModelScope.launch { VideoPlaybackPool.toggleMute(adId) }
    }

    private suspend fun syncAd(adId: Long) {
        val updated = AdRepository.findAd(adId) ?: return
        mutableUiState.update { state ->
            state.copy(ads = state.ads.map { if (it.id == adId) updated else it })
        }
    }
}
