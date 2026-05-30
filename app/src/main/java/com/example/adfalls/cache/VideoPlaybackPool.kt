package com.example.adfalls.cache

import com.example.adfalls.data.repository.AdRepository

object VideoPlaybackPool {
    private var activeVideoId: Long? = null

    suspend fun togglePlay(id: Long) {
        val isPlaying = AdRepository.findAd(id)?.playing == true
        if (isPlaying) {
            pause(id)
        } else {
            play(id)
        }
    }

    suspend fun play(id: Long) {
        activeVideoId?.takeIf { it != id }?.let { AdRepository.setVideoState(it, playing = false) }
        activeVideoId = id
        AdRepository.setVideoState(id, playing = true)
    }

    suspend fun pause(id: Long) {
        if (activeVideoId == id) activeVideoId = null
        AdRepository.setVideoState(id, playing = false)
    }

    suspend fun toggleMute(id: Long) {
        val muted = AdRepository.findAd(id)?.muted ?: true
        AdRepository.setVideoState(id, muted = !muted)
    }
}
