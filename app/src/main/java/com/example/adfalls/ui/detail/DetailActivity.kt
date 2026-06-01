package com.example.adfalls.ui.detail

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.ui.PlayerView
import com.example.adfalls.R
import com.example.adfalls.cache.VideoPlaybackPool
import com.example.adfalls.data.model.AdCardType
import com.example.adfalls.data.model.AdItem
import com.example.adfalls.viewmodel.DetailUiState
import com.example.adfalls.viewmodel.DetailViewModel
import kotlinx.coroutines.launch

class DetailActivity : ComponentActivity() {
    private lateinit var viewModel: DetailViewModel
    private lateinit var playerView: PlayerView
    private val progressHandler = Handler(Looper.getMainLooper())
    private var progressRunnable: Runnable? = null
    private val controlsHandler = Handler(Looper.getMainLooper())
    private var hideControlsRunnable: Runnable? = null
    private var keepControlsVisibleOnNextRender = false
    private var lastRenderedAd: AdItem? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        setContentView(R.layout.activity_detail)
        viewModel = ViewModelProvider.create(this)[DetailViewModel::class]
        viewModel.loadAd(intent.getLongExtra(EXTRA_AD_ID, -1L))
        playerView = findViewById(R.id.detail_media)

        findViewById<View>(R.id.back_button).setOnClickListener { finish() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { renderState(it) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.playVideo()
    }

    override fun onPause() {
        stopProgressUpdates()
        stopPendingControlHide()
        viewModel.pauseVideo()
        VideoPlaybackPool.detach(playerView)
        super.onPause()
    }

    private fun renderState(state: DetailUiState) {
        if (state.loading) return
        val ad = state.ad ?: run {
            finish()
            return
        }
        if (lastRenderedAd?.isOnlyVideoStateChanged(ad) == true) {
            renderVideoState(ad)
            lastRenderedAd = ad
            return
        }
        render(ad)
        lastRenderedAd = ad
    }

    private fun render(ad: AdItem) {
        findViewById<TextView>(R.id.detail_channel).text = ad.channel.title
        findViewById<TextView>(R.id.detail_title).text = ad.title
        findViewById<TextView>(R.id.detail_brand).text = ad.brand
        findViewById<TextView>(R.id.detail_summary).text = ad.summary
        findViewById<TextView>(R.id.detail_body).text = ad.detail
        findViewById<TextView>(R.id.detail_tags).text = ad.tags.joinToString("  ") { "#$it" }
        findViewById<TextView>(R.id.detail_stats).text =
            "曝光 ${ad.impressions} · 点击 ${ad.clicks} · 点赞 ${ad.likes} · 分享 ${ad.shares}"

        playerView.background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(ad.mediaColor, darken(ad.mediaColor))
        ).apply { cornerRadius = 22f }
        resizeMedia(ad.type)
        playerView.useController = false
        if (ad.type == AdCardType.VIDEO) {
            VideoPlaybackPool.attach(playerView, ad.id, ad.videoUrl, ad.playing, ad.muted)
            startProgressUpdates(ad.id)
            findViewById<View>(R.id.detail_mute).apply {
                animate().cancel()
                alpha = 1f
                visibility = View.VISIBLE
            }
            if (ad.playing) {
                if (keepControlsVisibleOnNextRender) {
                    keepControlsVisibleOnNextRender = false
                    showPlaybackControls(scheduleHide = true)
                } else {
                    hidePlaybackControls(animate = false)
                }
            } else {
                keepControlsVisibleOnNextRender = false
                showPlaybackControls(scheduleHide = true)
            }
        } else {
            stopProgressUpdates()
            keepControlsVisibleOnNextRender = false
            hidePlaybackControls(animate = false)
            findViewById<View>(R.id.detail_mute).visibility = View.GONE
            VideoPlaybackPool.detach(playerView)
        }

        val like = findViewById<TextView>(R.id.detail_like)
        val favorite = findViewById<TextView>(R.id.detail_favorite)
        val share = findViewById<TextView>(R.id.detail_share)
        val video = findViewById<ImageButton>(R.id.detail_video)
        val mute = findViewById<ImageButton>(R.id.detail_mute)
        val progressPanel = findViewById<View>(R.id.detail_progress_panel)

        like.text = if (ad.liked) "已赞 ${ad.likes}" else "点赞 ${ad.likes}"
        favorite.text = if (ad.favorited) "已收藏" else "收藏"
        share.text = "分享"
        video.setImageResource(if (ad.playing) R.drawable.ic_video_pause else R.drawable.ic_video_play)
        mute.setImageResource(if (ad.muted) R.drawable.ic_volume_off else R.drawable.ic_volume_on)
        video.contentDescription = if (ad.playing) "暂停" else "播放"
        mute.contentDescription = if (ad.muted) "取消静音" else "静音"
        if (ad.type != AdCardType.VIDEO) {
            video.visibility = View.GONE
            mute.visibility = View.GONE
            progressPanel.visibility = View.GONE
        }

        like.setOnClickListener { viewModel.toggleLike() }
        favorite.setOnClickListener { viewModel.toggleFavorite() }
        share.setOnClickListener { viewModel.share() }
        playerView.setOnClickListener {
            if (ad.type == AdCardType.VIDEO) {
                toggleVideoFromUser(ad)
            }
        }
        video.setOnClickListener {
            toggleVideoFromUser(ad)
        }
        mute.setOnClickListener {
            keepControlsVisibleOnNextRender = true
            showPlaybackControls(scheduleHide = true)
            viewModel.toggleMute()
        }
    }

    private fun renderVideoState(ad: AdItem) {
        val video = findViewById<ImageButton>(R.id.detail_video)
        val mute = findViewById<ImageButton>(R.id.detail_mute)
        video.setImageResource(if (ad.playing) R.drawable.ic_video_pause else R.drawable.ic_video_play)
        mute.setImageResource(if (ad.muted) R.drawable.ic_volume_off else R.drawable.ic_volume_on)
        video.contentDescription = if (ad.playing) "暂停" else "播放"
        mute.contentDescription = if (ad.muted) "取消静音" else "静音"
        mute.animate().cancel()
        mute.alpha = 1f
        mute.visibility = View.VISIBLE

        if (ad.playing) {
            if (keepControlsVisibleOnNextRender) {
                keepControlsVisibleOnNextRender = false
                showPlaybackControls(scheduleHide = true)
            } else {
                hidePlaybackControls(animate = false)
            }
        } else {
            keepControlsVisibleOnNextRender = false
            showPlaybackControls(scheduleHide = true)
        }
    }

    private fun toggleVideoFromUser(ad: AdItem) {
        keepControlsVisibleOnNextRender = true
        findViewById<ImageButton>(R.id.detail_video).apply {
            setImageResource(if (ad.playing) R.drawable.ic_video_play else R.drawable.ic_video_pause)
            contentDescription = if (ad.playing) "播放" else "暂停"
        }
        showPlaybackControls(scheduleHide = true)
        viewModel.toggleVideoPlay()
    }

    private fun startProgressUpdates(adId: Long) {
        stopProgressUpdates()
        progressRunnable = object : Runnable {
            override fun run() {
                updateProgress(adId)
                progressHandler.postDelayed(this, VIDEO_PROGRESS_INTERVAL_MS)
            }
        }.also { it.run() }
    }

    private fun stopProgressUpdates() {
        progressRunnable?.let(progressHandler::removeCallbacks)
        progressRunnable = null
    }

    private fun showPlaybackControls(scheduleHide: Boolean) {
        stopPendingControlHide()
        playbackControlViews().forEach { control ->
            control.animate().cancel()
            control.visibility = View.VISIBLE
            control.animate()
                .alpha(1f)
                .setDuration(CONTROLS_FADE_DURATION_MS)
                .start()
        }
        if (scheduleHide) {
            hideControlsRunnable = Runnable { hidePlaybackControls(animate = true) }
            controlsHandler.postDelayed(hideControlsRunnable!!, CONTROLS_AUTO_HIDE_MS)
        }
    }

    private fun hidePlaybackControls(animate: Boolean) {
        stopPendingControlHide()
        playbackControlViews().forEach { control ->
            control.animate().cancel()
            if (animate && control.visibility == View.VISIBLE) {
                control.animate()
                    .alpha(0f)
                    .setDuration(CONTROLS_FADE_DURATION_MS)
                    .withEndAction { control.visibility = View.GONE }
                    .start()
            } else {
                control.alpha = 0f
                control.visibility = View.GONE
            }
        }
    }

    private fun stopPendingControlHide() {
        hideControlsRunnable?.let(controlsHandler::removeCallbacks)
        hideControlsRunnable = null
    }

    private fun playbackControlViews(): List<View> {
        return listOf(
            findViewById(R.id.detail_video),
            findViewById(R.id.detail_progress_panel)
        )
    }

    private fun updateProgress(adId: Long) {
        val progress = findViewById<ProgressBar>(R.id.detail_progress)
        val time = findViewById<TextView>(R.id.detail_time)
        val (position, duration) = VideoPlaybackPool.progress(adId)
        progress.progress = if (duration > 0L) {
            ((position.coerceAtMost(duration) * VIDEO_PROGRESS_MAX) / duration).toInt()
        } else {
            0
        }
        time.text = "${formatTime(position)} / ${formatTime(duration)}"
    }

    private fun resizeMedia(type: AdCardType) {
        val container = findViewById<View>(R.id.detail_media_container)
        container.post {
            val width = container.width.takeIf { it > 0 } ?: return@post
            val targetHeight = when (type) {
                AdCardType.VIDEO,
                AdCardType.LARGE_IMAGE -> (width * MEDIA_RATIO_9_16).toInt()
                AdCardType.SMALL_IMAGE -> width
            }
            if (container.layoutParams.height != targetHeight) {
                container.layoutParams = container.layoutParams.apply { height = targetHeight }
            }
        }
    }

    private fun darken(color: Int): Int {
        return Color.rgb(
            (Color.red(color) * 0.68f).toInt(),
            (Color.green(color) * 0.68f).toInt(),
            (Color.blue(color) * 0.68f).toInt()
        )
    }

    companion object {
        const val EXTRA_AD_ID = "extra_ad_id"
        private const val VIDEO_PROGRESS_INTERVAL_MS = 33L
        private const val VIDEO_PROGRESS_MAX = 10000L
        private const val MEDIA_RATIO_9_16 = 9f / 16f
        private const val CONTROLS_AUTO_HIDE_MS = 2_000L
        private const val CONTROLS_FADE_DURATION_MS = 500L

        private fun formatTime(milliseconds: Long): String {
            val totalSeconds = milliseconds.coerceAtLeast(0L) / 1000L
            val minutes = totalSeconds / 60L
            val seconds = totalSeconds % 60L
            return "$minutes:${seconds.toString().padStart(2, '0')}"
        }
    }
}

private fun AdItem.isOnlyVideoStateChanged(newAd: AdItem): Boolean {
    return copy(playing = newAd.playing, muted = newAd.muted) == newAd &&
        (playing != newAd.playing || muted != newAd.muted)
}
