package com.example.adfalls

import android.app.Application
import com.example.adfalls.data.repository.AdRepository

class AdFallsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AdRepository.initialize(this)
    }
}
