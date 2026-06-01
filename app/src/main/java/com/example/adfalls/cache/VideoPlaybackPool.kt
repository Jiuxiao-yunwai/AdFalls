package com.example.adfalls.cache

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.adfalls.data.repository.AdRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object VideoPlaybackPool {
    private var appContext: Context? = null
    private var player: ExoPlayer? = null
    private var activeVideoId: Long? = null
    private var activeVideoUrl: String? = null
    private var attachedView: PlayerView? = null

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    // Reuses one shared ExoPlayer so only one video is active at a time.
    fun attach(playerView: PlayerView, id: Long, videoUrl: String?, playing: Boolean, muted: Boolean) {
        if (!playing || videoUrl.isNullOrBlank() || activeVideoId != id) {
            if (attachedView == playerView) playerView.player = null
            return
        }
        attachedView?.takeIf { it != playerView }?.player = null
        attachedView = playerView
        playerView.player = requirePlayer().apply {
            volume = if (muted) 0f else 1f
            playWhenReady = true
        }
    }

    // Only detaches the PlayerView currently owned by the pool.
    fun detach(playerView: PlayerView) {
        if (attachedView == playerView) {
            playerView.player = null
            attachedView = null
        }
    }

    suspend fun togglePlay(id: Long) {
        val ad = AdRepository.findAd(id) ?: return
        if (ad.playing) {
            pause(id)
        } else {
            play(id, ad.videoUrl, ad.muted)
        }
    }

    suspend fun play(id: Long) {
        val ad = AdRepository.findAd(id) ?: return
        play(id, ad.videoUrl, ad.muted)
    }

    // Play state is written through AdRepository so Room keeps list and detail in sync.
    suspend fun play(id: Long, videoUrl: String?, muted: Boolean) {
        if (videoUrl.isNullOrBlank()) return
        withContext(Dispatchers.Main) {
            val player = requirePlayer()
            if (activeVideoId != id || activeVideoUrl != videoUrl) {
                player.setMediaItem(MediaItem.fromUri(videoUrl))
                player.prepare()
            }
            activeVideoId?.takeIf { it != id }?.let {
                withContext(Dispatchers.IO) { AdRepository.setVideoState(it, playing = false) }
            }
            activeVideoId = id
            activeVideoUrl = videoUrl
            player.repeatMode = Player.REPEAT_MODE_ONE
            player.volume = if (muted) 0f else 1f
            player.playWhenReady = true
            player.play()
        }
        AdRepository.setVideoState(id, playing = true, muted = muted)
    }

    suspend fun pause(id: Long) {
        withContext(Dispatchers.Main) {
            if (activeVideoId == id) {
                requirePlayer().pause()
                activeVideoId = null
                activeVideoUrl = null
            }
        }
        AdRepository.setVideoState(id, playing = false)
    }

    suspend fun toggleMute(id: Long) {
        val muted = !(AdRepository.findAd(id)?.muted ?: true)
        withContext(Dispatchers.Main) {
            if (activeVideoId == id) {
                requirePlayer().volume = if (muted) 0f else 1f
            }
        }
        AdRepository.setVideoState(id, muted = muted)
    }

    // Call from a host's final teardown path when the shared player is no longer needed.
    fun release() {
        player?.pause()
        attachedView?.player = null
        attachedView = null
        activeVideoId = null
        activeVideoUrl = null
        player?.release()
        player = null
    }

    private fun requirePlayer(): ExoPlayer {
        return player ?: ExoPlayer.Builder(checkNotNull(appContext) { "VideoPlaybackPool is not initialized." })
            .build()
            .also { player = it }
    }
}
