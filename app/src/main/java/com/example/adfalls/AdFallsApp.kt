package com.example.adfalls

import android.app.Application
import com.example.adfalls.cache.VideoPlaybackPool
import com.example.adfalls.config.ServerConfig
import com.example.adfalls.data.repository.AdRepository
import com.example.adfalls.data.repository.AiChatRepository

class AdFallsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ServerConfig.initialize(this)
        AdRepository.initialize(this)
        AiChatRepository.initialize(this)
        VideoPlaybackPool.initialize(this)
    }
}
