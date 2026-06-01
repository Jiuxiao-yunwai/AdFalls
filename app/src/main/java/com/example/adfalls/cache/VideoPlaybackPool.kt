package com.example.adfalls.cache

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.C
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
    private var sharedMuted: Boolean? = null
    private val playbackPositions = mutableMapOf<Long, Long>()
    private val playbackDurations = mutableMapOf<Long, Long>()

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    // Reuses one shared ExoPlayer so only one video is active at a time.
    fun attach(playerView: PlayerView, id: Long, videoUrl: String?, playing: Boolean, muted: Boolean) {
        if (videoUrl.isNullOrBlank() || activeVideoId != id || activeVideoUrl != videoUrl) {
            if (attachedView == playerView) playerView.player = null
            return
        }
        attachedView?.takeIf { it != playerView }?.player = null
        attachedView = playerView
        playerView.player = requirePlayer().apply {
            volume = if (resolveMuted(muted)) 0f else 1f
            playWhenReady = playing
        }
    }

    // Only detaches the PlayerView currently owned by the pool.
    fun detach(playerView: PlayerView) {
        if (attachedView == playerView) {
            playerView.player = null
            attachedView = null
        }
    }

    fun progress(id: Long): Pair<Long, Long> {
        val currentPlayer = player
        if (activeVideoId == id && currentPlayer != null) {
            saveActiveProgress()
        }
        return (playbackPositions[id] ?: 0L) to (playbackDurations[id] ?: 0L)
    }

    suspend fun togglePlay(id: Long) {
        val ad = AdRepository.findAd(id) ?: return
        togglePlay(ad.id, ad.videoUrl, ad.playing, ad.muted)
    }

    suspend fun togglePlay(id: Long, videoUrl: String?, playing: Boolean, muted: Boolean) {
        if (playing) {
            pause(id)
        } else {
            play(id, videoUrl, muted)
        }
    }

    suspend fun play(id: Long) {
        val ad = AdRepository.findAd(id) ?: return
        play(id, ad.videoUrl, ad.muted)
    }

    // Play state is written through AdRepository so Room keeps list and detail in sync.
    suspend fun play(id: Long, videoUrl: String?, muted: Boolean) {
        if (videoUrl.isNullOrBlank()) return
        val effectiveMuted = resolveMuted(muted)
        withContext(Dispatchers.Main) {
            val player = requirePlayer()
            if (activeVideoId != id || activeVideoUrl != videoUrl) {
                saveActiveProgress()
                player.setMediaItem(MediaItem.fromUri(videoUrl))
                player.prepare()
                player.seekTo(playbackPositions[id] ?: 0L)
            }
            activeVideoId?.takeIf { it != id }?.let {
                withContext(Dispatchers.IO) { AdRepository.setVideoState(it, playing = false) }
            }
            activeVideoId = id
            activeVideoUrl = videoUrl
            player.repeatMode = Player.REPEAT_MODE_ONE
            player.volume = if (effectiveMuted) 0f else 1f
            player.playWhenReady = true
            player.play()
        }
        AdRepository.setVideoState(id, playing = true, muted = effectiveMuted)
    }

    suspend fun playInFeed(id: Long, videoUrl: String?, muted: Boolean, playerView: PlayerView) {
        if (videoUrl.isNullOrBlank()) return
        val effectiveMuted = resolveMuted(muted)
        var previousVideoId: Long? = null
        withContext(Dispatchers.Main) {
            val player = requirePlayer()
            val switchingVideo = activeVideoId != id || activeVideoUrl != videoUrl
            if (switchingVideo) {
                previousVideoId = activeVideoId
                saveActiveProgress()
                player.setMediaItem(MediaItem.fromUri(videoUrl))
                player.prepare()
                player.seekTo(playbackPositions[id] ?: 0L)
            }
            attachedView?.takeIf { it != playerView }?.player = null
            attachedView = playerView
            playerView.player = player
            activeVideoId = id
            activeVideoUrl = videoUrl
            player.repeatMode = Player.REPEAT_MODE_ONE
            player.volume = if (effectiveMuted) 0f else 1f
            player.playWhenReady = true
            player.play()
        }
        previousVideoId?.takeIf { it != id }?.let {
            AdRepository.setVideoState(it, playing = false)
        }
        AdRepository.setVideoState(id, playing = true, muted = effectiveMuted)
    }

    suspend fun pause(id: Long) {
        withContext(Dispatchers.Main) {
            if (activeVideoId == id) {
                saveActiveProgress()
                player?.pause()
            }
        }
        AdRepository.setVideoState(id, playing = false)
    }

    suspend fun pauseFromFeed(id: Long) {
        withContext(Dispatchers.Main) {
            if (activeVideoId == id) {
                saveActiveProgress()
                player?.pause()
                attachedView?.player = null
                attachedView = null
                activeVideoId = null
                activeVideoUrl = null
            }
        }
        AdRepository.setVideoState(id, playing = false)
    }

    suspend fun toggleMute(id: Long) {
        val muted = !(sharedMuted ?: AdRepository.findAd(id)?.muted ?: true)
        sharedMuted = muted
        withContext(Dispatchers.Main) {
            if (activeVideoId == id) {
                requirePlayer().volume = if (muted) 0f else 1f
            }
        }
        AdRepository.setAllVideoMuted(muted)
    }

    // Call from a host's final teardown path when the shared player is no longer needed.
    fun release() {
        player?.pause()
        attachedView?.player = null
        attachedView = null
        activeVideoId = null
        activeVideoUrl = null
        playbackPositions.clear()
        playbackDurations.clear()
        sharedMuted = null
        player?.release()
        player = null
    }

    private fun resolveMuted(fallback: Boolean): Boolean {
        val muted = sharedMuted ?: fallback
        sharedMuted = muted
        return muted
    }

    private fun saveActiveProgress() {
        val id = activeVideoId ?: return
        val currentPlayer = player ?: return
        playbackPositions[id] = currentPlayer.currentPosition
        val duration = currentPlayer.duration
        if (duration != C.TIME_UNSET && duration > 0L) {
            playbackDurations[id] = duration
        }
    }

    private fun requirePlayer(): ExoPlayer {
        return player ?: ExoPlayer.Builder(checkNotNull(appContext) { "VideoPlaybackPool is not initialized." })
            .build()
            .also { player = it }
    }
}
