package com.example.adfalls.ui.detail

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
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
        render(ad)
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
        playerView.useController = false
        if (ad.type == AdCardType.VIDEO) {
            VideoPlaybackPool.attach(playerView, ad.id, ad.videoUrl, ad.playing, ad.muted)
            startProgressUpdates(ad.id)
        } else {
            stopProgressUpdates()
            VideoPlaybackPool.detach(playerView)
        }

        val like = findViewById<TextView>(R.id.detail_like)
        val favorite = findViewById<TextView>(R.id.detail_favorite)
        val share = findViewById<TextView>(R.id.detail_share)
        val video = findViewById<TextView>(R.id.detail_video)
        val mute = findViewById<TextView>(R.id.detail_mute)
        val progressPanel = findViewById<View>(R.id.detail_progress_panel)

        like.text = if (ad.liked) "已赞 ${ad.likes}" else "点赞 ${ad.likes}"
        favorite.text = if (ad.favorited) "已收藏" else "收藏"
        share.text = "分享"
        video.text = if (ad.playing) "暂停" else "播放"
        mute.text = if (ad.muted) "静音" else "有声"
        video.visibility = if (ad.type == AdCardType.VIDEO) View.VISIBLE else View.GONE
        mute.visibility = if (ad.type == AdCardType.VIDEO) View.VISIBLE else View.GONE
        progressPanel.visibility = if (ad.type == AdCardType.VIDEO) View.VISIBLE else View.GONE

        like.setOnClickListener { viewModel.toggleLike() }
        favorite.setOnClickListener { viewModel.toggleFavorite() }
        share.setOnClickListener { viewModel.share() }
        video.setOnClickListener { viewModel.toggleVideoPlay() }
        mute.setOnClickListener { viewModel.toggleMute() }
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

    private fun darken(color: Int): Int {
        return Color.rgb(
            (Color.red(color) * 0.68f).toInt(),
            (Color.green(color) * 0.68f).toInt(),
            (Color.blue(color) * 0.68f).toInt()
        )
    }

    companion object {
        const val EXTRA_AD_ID = "extra_ad_id"
        private const val VIDEO_PROGRESS_INTERVAL_MS = 500L
        private const val VIDEO_PROGRESS_MAX = 1000L

        private fun formatTime(milliseconds: Long): String {
            val totalSeconds = milliseconds.coerceAtLeast(0L) / 1000L
            val minutes = totalSeconds / 60L
            val seconds = totalSeconds % 60L
            return "$minutes:${seconds.toString().padStart(2, '0')}"
        }
    }
}
