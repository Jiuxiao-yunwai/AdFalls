package com.example.adfalls.cache

import android.content.Context
import android.view.View
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.adfalls.data.repository.AdRepository
import java.util.WeakHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object VideoPlaybackPool {
    private var appContext: Context? = null
    private var player: ExoPlayer? = null
    private var activeVideoId: Long? = null
    private var activeVideoUrl: String? = null
    private var attachedView: PlayerView? = null
    private var sharedMuted: Boolean? = null
    private var activeFrameReady = false
    private val pausedFrameIds = mutableSetOf<Long>()
    private val playbackStates = mutableMapOf<Long, PlaybackState>()
    private val viewBindings = WeakHashMap<PlayerView, ViewBinding>()

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    fun hasActiveFrame(id: Long, videoUrl: String?): Boolean {
        return !videoUrl.isNullOrBlank() &&
            activeVideoId == id &&
            activeVideoUrl == videoUrl &&
            player != null &&
            attachedView != null &&
            activeFrameReady
    }

    fun isPlaybackActive(id: Long, videoUrl: String?): Boolean {
        if (videoUrl.isNullOrBlank() || activeVideoId != id || activeVideoUrl != videoUrl) return false
        if (id in pausedFrameIds) return false
        val currentPlayer = player ?: return false
        return currentPlayer.isPlaying || currentPlayer.playWhenReady
    }

    fun pauseFrameNow(id: Long): Boolean {
        if (activeVideoId != id) return false
        saveActiveState()
        pausedFrameIds.add(id)
        player?.pause()
        return true
    }

    fun attach(playerView: PlayerView, id: Long, videoUrl: String?, playing: Boolean, muted: Boolean) {
        if (videoUrl.isNullOrBlank()) {
            detach(playerView)
            return
        }
        viewBindings[playerView] = ViewBinding(id, videoUrl, muted)
        val isActive = activeVideoId == id && activeVideoUrl == videoUrl && player != null
        if (!isActive) {
            if (attachedView == playerView) {
                playerView.player = null
                attachedView = null
            }
            return
        }
        attachActivePlayerTo(playerView)
        playerView.visibility = if (activeFrameReady) View.VISIBLE else View.INVISIBLE
        player?.volume = if (resolveMuted(muted)) 0f else 1f
        if (playing && id !in pausedFrameIds) {
            player?.playWhenReady = true
            player?.play()
        } else if (id in pausedFrameIds) {
            player?.pause()
        }
    }

    fun detach(playerView: PlayerView) {
        viewBindings.remove(playerView)
        if (attachedView == playerView) {
            saveActiveState()
            releaseActivePlayer()
            activeVideoId = null
            activeVideoUrl = null
        }
    }

    fun progress(id: Long): Pair<Long, Long> {
        if (activeVideoId == id) saveActiveState()
        val state = playbackStates[id]
        return (state?.positionMs ?: 0L) to (state?.durationMs ?: 0L)
    }

    suspend fun togglePlay(id: Long) {
        val ad = AdRepository.findAd(id) ?: return
        togglePlay(ad.id, ad.videoUrl, ad.playing, ad.muted)
    }

    suspend fun togglePlay(id: Long, videoUrl: String?, playing: Boolean, muted: Boolean) {
        if (willPauseOnToggle(id, playing)) {
            pause(id)
        } else {
            play(id, videoUrl, muted)
        }
    }

    suspend fun play(id: Long) {
        val ad = AdRepository.findAd(id) ?: return
        play(ad.id, ad.videoUrl, ad.muted)
    }

    suspend fun play(id: Long, videoUrl: String?, muted: Boolean) {
        if (videoUrl.isNullOrBlank()) return
        val targetView = withContext(Dispatchers.Main) {
            viewBindings.entries.firstOrNull { (_, binding) ->
                binding.id == id && binding.url == videoUrl
            }?.key
        } ?: return
        playInView(id, videoUrl, muted, targetView)
    }

    suspend fun playInFeed(id: Long, videoUrl: String?, muted: Boolean, playerView: PlayerView) {
        playInView(id, videoUrl, muted, playerView)
    }

    suspend fun playInView(id: Long, videoUrl: String?, muted: Boolean, playerView: PlayerView) {
        if (videoUrl.isNullOrBlank()) return
        val effectiveMuted = resolveMuted(muted)
        val previousVideoId = withContext(Dispatchers.Main) {
            viewBindings[playerView] = ViewBinding(id, videoUrl, effectiveMuted)
            val previous = activeVideoId?.takeIf { it != id }
            val switchingVideo = activeVideoId != id || activeVideoUrl != videoUrl || player == null
            if (switchingVideo) {
                saveActiveState()
                rebuildPlayer(id, videoUrl, effectiveMuted)
            }
            activeVideoId = id
            activeVideoUrl = videoUrl
            pausedFrameIds.remove(id)
            attachActivePlayerTo(playerView)
            val currentPlayer = requirePlayer()
            currentPlayer.repeatMode = Player.REPEAT_MODE_ONE
            currentPlayer.volume = if (effectiveMuted) 0f else 1f
            playerView.visibility = if (activeFrameReady) View.VISIBLE else View.INVISIBLE
            if (switchingVideo || !currentPlayer.isPlaying && !currentPlayer.playWhenReady) {
                currentPlayer.seekTo(playbackStates[id]?.positionMs ?: 0L)
            }
            currentPlayer.playWhenReady = true
            currentPlayer.play()
            if (currentPlayer.playbackState == Player.STATE_READY) {
                activeFrameReady = true
                playerView.visibility = View.VISIBLE
            }
            previous
        }
        previousVideoId?.let { AdRepository.setVideoState(it, playing = false) }
        AdRepository.setVideoState(id, playing = true, muted = effectiveMuted)
    }

    suspend fun pause(id: Long) {
        withContext(Dispatchers.Main) {
            pauseFrameNow(id)
        }
        AdRepository.setVideoState(id, playing = false)
    }

    suspend fun pauseFromFeed(id: Long) {
        withContext(Dispatchers.Main) {
            if (activeVideoId == id) {
                saveActiveState()
                releaseActivePlayer()
                activeVideoId = null
                activeVideoUrl = null
            }
            pausedFrameIds.remove(id)
        }
        AdRepository.setVideoState(id, playing = false)
    }

    suspend fun pauseActiveAndRelease() {
        val pausedVideoId = withContext(Dispatchers.Main) {
            val id = activeVideoId ?: return@withContext null
            saveActiveState()
            releaseActivePlayer()
            activeVideoId = null
            activeVideoUrl = null
            pausedFrameIds.remove(id)
            id
        }
        pausedVideoId?.let { AdRepository.setVideoState(it, playing = false) }
    }

    suspend fun toggleMute(id: Long) {
        val muted = !(sharedMuted ?: AdRepository.findAd(id)?.muted ?: true)
        sharedMuted = muted
        withContext(Dispatchers.Main) {
            if (activeVideoId == id) {
                player?.volume = if (muted) 0f else 1f
                saveActiveState(mutedOverride = muted)
            }
        }
        AdRepository.setAllVideoMuted(muted)
    }

    fun release() {
        saveActiveState()
        releaseActivePlayer()
        activeVideoId = null
        activeVideoUrl = null
        pausedFrameIds.clear()
        playbackStates.clear()
        viewBindings.clear()
        sharedMuted = null
    }

    fun willPauseOnToggle(id: Long, modelPlaying: Boolean): Boolean {
        val url = activeVideoUrl
        if (activeVideoId == id && url != null) return isPlaybackActive(id, url)
        return modelPlaying
    }

    private fun rebuildPlayer(id: Long, videoUrl: String, muted: Boolean) {
        releaseActivePlayer()
        player = ExoPlayer.Builder(checkNotNull(appContext) { "VideoPlaybackPool is not initialized." })
            .build()
            .apply {
                activeFrameReady = false
                addListener(
                    object : Player.Listener {
                        override fun onPlaybackStateChanged(playbackState: Int) {
                            if (playbackState == Player.STATE_READY) {
                                activeFrameReady = true
                                attachedView?.visibility = View.VISIBLE
                            }
                        }
                    }
                )
                repeatMode = Player.REPEAT_MODE_ONE
                volume = if (resolveMuted(muted)) 0f else 1f
                setMediaItem(MediaItem.fromUri(playableVideoUrl(videoUrl)))
                prepare()
                seekTo(playbackStates[id]?.positionMs ?: 0L)
            }
    }

    private fun attachActivePlayerTo(playerView: PlayerView) {
        val currentPlayer = requirePlayer()
        if (attachedView == playerView && playerView.player === currentPlayer) return
        attachedView?.player = null
        playerView.player = currentPlayer
        attachedView = playerView
    }

    private fun releaseActivePlayer() {
        attachedView?.player = null
        attachedView = null
        player?.release()
        player = null
        activeFrameReady = false
    }

    private fun saveActiveState(mutedOverride: Boolean? = null) {
        val id = activeVideoId ?: return
        val url = activeVideoUrl ?: return
        val currentPlayer = player ?: return
        val duration = currentPlayer.duration
        val durationMs = if (duration != C.TIME_UNSET && duration > 0L) {
            duration
        } else {
            playbackStates[id]?.durationMs ?: 0L
        }
        val position = currentPlayer.currentPosition.coerceAtLeast(0L)
        playbackStates[id] = PlaybackState(
            id = id,
            url = url,
            positionMs = position,
            durationMs = durationMs,
            framePositionMs = position,
            muted = mutedOverride ?: sharedMuted ?: playbackStates[id]?.muted ?: true,
            paused = !currentPlayer.isPlaying && !currentPlayer.playWhenReady
        )
    }

    private fun requirePlayer(): ExoPlayer {
        return player ?: ExoPlayer.Builder(checkNotNull(appContext) { "VideoPlaybackPool is not initialized." })
            .build()
            .also { player = it }
    }

    private fun resolveMuted(fallback: Boolean): Boolean {
        val muted = sharedMuted ?: fallback
        sharedMuted = muted
        return muted
    }

    private fun playableVideoUrl(videoUrl: String): String {
        val context = appContext ?: return videoUrl
        return RemoteVideoCache.playableUrl(context, videoUrl) ?: videoUrl
    }

    private data class ViewBinding(
        val id: Long,
        val url: String,
        val muted: Boolean
    )

    private data class PlaybackState(
        val id: Long,
        val url: String,
        val positionMs: Long,
        val durationMs: Long,
        val framePositionMs: Long,
        val muted: Boolean,
        val paused: Boolean
    )
}
