package com.example.adfalls.ui.feed

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.adfalls.R
import com.example.adfalls.cache.VideoPlaybackPool
import com.example.adfalls.data.model.AdCardType
import com.example.adfalls.data.model.AdItem

class AdAdapter(
    private val onCardClick: (AdItem) -> Unit,
    private val onLikeClick: (AdItem) -> Unit,
    private val onFavoriteClick: (AdItem) -> Unit,
    private val onShareClick: (AdItem) -> Unit,
    private val onVideoClick: (AdItem) -> Unit,
    private val onMuteClick: (AdItem) -> Unit,
    private val onTagClick: (String) -> Unit
) : ListAdapter<AdItem, RecyclerView.ViewHolder>(Diff) {
    private var footerText: String? = null

    fun submitAds(items: List<AdItem>, footerText: String? = null, commitCallback: (() -> Unit)? = null) {
        val oldFooterText = this.footerText
        val oldFooterPosition = currentList.size
        this.footerText = footerText
        submitList(items) {
            notifyFooterChanged(oldFooterText, footerText, oldFooterPosition)
            commitCallback?.invoke()
        }
    }

    private fun notifyFooterChanged(oldFooterText: String?, newFooterText: String?, oldFooterPosition: Int) {
        val newFooterPosition = currentList.size
        when {
            oldFooterText == null && newFooterText != null -> notifyItemInserted(newFooterPosition)
            oldFooterText != null && newFooterText == null -> notifyItemRemoved(oldFooterPosition)
            oldFooterText != null && newFooterText != null && oldFooterText != newFooterText -> {
                notifyItemChanged(newFooterPosition)
            }
        }
    }

    fun getAdAtAdapterPosition(position: Int): AdItem? = currentList.getOrNull(position)

    override fun getItemCount(): Int = super.getItemCount() + if (footerText != null) 1 else 0

    override fun getItemViewType(position: Int): Int {
        if (footerText != null && position == currentList.size) return R.layout.item_feed_end
        return when (getItem(position).type) {
            AdCardType.LARGE_IMAGE -> R.layout.item_ad_large
            AdCardType.SMALL_IMAGE -> R.layout.item_ad_small
            AdCardType.VIDEO -> R.layout.item_ad_video
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(viewType, parent, false)
        return if (viewType == R.layout.item_feed_end) EndViewHolder(view) else AdViewHolder(view)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is AdViewHolder -> holder.bind(getItem(position))
            is EndViewHolder -> holder.bind(footerText.orEmpty())
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (holder is AdViewHolder && payloads.contains(VideoStatePayload)) {
            holder.updateVideoState(getItem(position))
            return
        }
        super.onBindViewHolder(holder, position, payloads)
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        if (holder is AdViewHolder) holder.detachVideo()
        super.onViewRecycled(holder)
    }

    private class EndViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun bind(text: String) {
            (itemView as TextView).text = text
        }
    }

    inner class AdViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val mediaContainer: View? = itemView.findViewById(R.id.ad_media_container)
        private val media: View = itemView.findViewById(R.id.ad_media)
        private val title: TextView = itemView.findViewById(R.id.ad_title)
        private val brand: TextView = itemView.findViewById(R.id.ad_brand)
        private val summary: TextView = itemView.findViewById(R.id.ad_summary)
        private val tags: TextView = itemView.findViewById(R.id.ad_tags)
        private val stats: TextView = itemView.findViewById(R.id.ad_stats)
        private val like: TextView = itemView.findViewById(R.id.action_like)
        private val favorite: TextView = itemView.findViewById(R.id.action_favorite)
        private val share: TextView = itemView.findViewById(R.id.action_share)
        private val video: ImageButton? = itemView.findViewById(R.id.action_video)
        private val mute: ImageButton? = itemView.findViewById(R.id.action_mute)
        private val progressPanel: View? = itemView.findViewById(R.id.video_progress_panel)
        private val progress: ProgressBar? = itemView.findViewById(R.id.video_progress)
        private val time: TextView? = itemView.findViewById(R.id.video_time)
        private val playerView: PlayerView? = media as? PlayerView
        private var boundAd: AdItem? = null
        private val progressHandler = Handler(Looper.getMainLooper())
        private var progressRunnable: Runnable? = null
        private val controlsHandler = Handler(Looper.getMainLooper())
        private var hideControlsRunnable: Runnable? = null
        private var keepControlsVisibleOnNextBind = false

        fun bind(ad: AdItem) {
            stopProgressUpdates()
            stopPendingControlHide()
            boundAd = ad
            title.text = ad.title
            brand.text = ad.brand
            summary.text = ad.summary
            tags.text = ad.tags.joinToString("  ") { "#$it" }
            stats.text = "曝光 ${ad.impressions} · 点击 ${ad.clicks}"
            like.text = if (ad.liked) "已赞 ${ad.likes}" else "点赞 ${ad.likes}"
            favorite.text = if (ad.favorited) "已收藏" else "收藏"
            share.text = "分享 ${ad.shares}"
            video?.setImageResource(if (ad.playing) R.drawable.ic_video_pause else R.drawable.ic_video_play)
            mute?.setImageResource(if (ad.muted) R.drawable.ic_volume_off else R.drawable.ic_volume_on)
            video?.contentDescription = if (ad.playing) "暂停" else "播放"
            mute?.contentDescription = if (ad.muted) "取消静音" else "静音"
            if (ad.type == AdCardType.VIDEO) {
                mute?.animate()?.cancel()
                mute?.alpha = 1f
                mute?.visibility = View.VISIBLE
                startProgressUpdates(ad.id)
                updateVideoState(ad)
            } else {
                keepControlsVisibleOnNextBind = false
                mute?.visibility = View.GONE
                hidePlaybackControls(animate = false)
            }

            resizeMedia(ad.type)
            media.background = mediaBackground(ad.mediaColor, ad.type)
            playerView?.useController = false
            playerView?.let {
                VideoPlaybackPool.attach(it, ad.id, ad.videoUrl, ad.playing, ad.muted)
            }
            like.isSelected = ad.liked
            favorite.isSelected = ad.favorited
            like.contentDescription = if (ad.liked) "取消点赞" else "点赞"
            favorite.contentDescription = if (ad.favorited) "取消收藏" else "收藏"
            share.contentDescription = "分享"
            tags.contentDescription = "按标签筛选"

            itemView.setOnClickListener { onCardClick(boundAd ?: ad) }
            media.setOnClickListener {
                val currentAd = boundAd ?: ad
                if (currentAd.type == AdCardType.VIDEO) {
                    toggleVideoFromUser(currentAd)
                } else {
                    onCardClick(currentAd)
                }
            }
            like.setOnClickListener { onLikeClick(boundAd ?: ad) }
            favorite.setOnClickListener { onFavoriteClick(boundAd ?: ad) }
            share.setOnClickListener { onShareClick(boundAd ?: ad) }
            tags.setOnClickListener { (boundAd ?: ad).tags.firstOrNull()?.let(onTagClick) }
            video?.setOnClickListener {
                toggleVideoFromUser(boundAd ?: ad)
            }
            mute?.setOnClickListener {
                keepControlsVisibleOnNextBind = true
                showPlaybackControls(scheduleHide = true)
                onMuteClick(boundAd ?: ad)
            }
        }

        fun detachVideo() {
            stopProgressUpdates()
            stopPendingControlHide()
            playerView?.let(VideoPlaybackPool::detach)
        }

        fun getPlayerView(): PlayerView? = playerView

        fun getBoundAd(): AdItem? = boundAd

        fun updateVideoState(ad: AdItem) {
            boundAd = ad
            video?.setImageResource(if (ad.playing) R.drawable.ic_video_pause else R.drawable.ic_video_play)
            mute?.setImageResource(if (ad.muted) R.drawable.ic_volume_off else R.drawable.ic_volume_on)
            video?.contentDescription = if (ad.playing) "暂停" else "播放"
            mute?.contentDescription = if (ad.muted) "取消静音" else "静音"
            mute?.animate()?.cancel()
            mute?.alpha = 1f
            mute?.visibility = if (ad.type == AdCardType.VIDEO) View.VISIBLE else View.GONE
            if (ad.type != AdCardType.VIDEO) {
                keepControlsVisibleOnNextBind = false
                hidePlaybackControls(animate = false)
                return
            }
            if (ad.playing) {
                if (keepControlsVisibleOnNextBind) {
                    keepControlsVisibleOnNextBind = false
                    showPlaybackControls(scheduleHide = true)
                } else {
                    hidePlaybackControls(animate = false)
                }
            } else {
                keepControlsVisibleOnNextBind = false
                showPlaybackControls(scheduleHide = true)
            }
        }

        private fun toggleVideoFromUser(ad: AdItem) {
            keepControlsVisibleOnNextBind = true
            video?.setImageResource(if (ad.playing) R.drawable.ic_video_play else R.drawable.ic_video_pause)
            video?.contentDescription = if (ad.playing) "播放" else "暂停"
            showPlaybackControls(scheduleHide = true)
            onVideoClick(ad)
        }

        private fun startProgressUpdates(adId: Long) {
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
            return listOfNotNull(video, progressPanel)
        }

        private fun updateProgress(adId: Long) {
            val (position, duration) = VideoPlaybackPool.progress(adId)
            progress?.progress = if (duration > 0L) {
                ((position.coerceAtMost(duration) * VIDEO_PROGRESS_MAX) / duration).toInt()
            } else {
                0
            }
            time?.text = "${formatTime(position)} / ${formatTime(duration)}"
        }

        private fun resizeMedia(type: AdCardType) {
            val target = mediaContainer ?: media
            target.post {
                val width = target.width.takeIf { it > 0 } ?: return@post
                val targetHeight = when (type) {
                    AdCardType.LARGE_IMAGE,
                    AdCardType.VIDEO -> (width * MEDIA_RATIO_9_16).toInt()
                    AdCardType.SMALL_IMAGE -> width
                }
                if (target.layoutParams.height != targetHeight) {
                    target.layoutParams = target.layoutParams.apply { height = targetHeight }
                }
            }
        }
    }

    private fun mediaBackground(color: Int, type: AdCardType): GradientDrawable {
        return GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(color, darken(color))
        ).apply {
            cornerRadius = 18f
            if (type == AdCardType.VIDEO) setStroke(3, Color.argb(160, 255, 255, 255))
        }
    }

    private fun darken(color: Int): Int {
        return Color.rgb(
            (Color.red(color) * 0.72f).toInt(),
            (Color.green(color) * 0.72f).toInt(),
            (Color.blue(color) * 0.72f).toInt()
        )
    }

    private object Diff : DiffUtil.ItemCallback<AdItem>() {
        override fun areItemsTheSame(oldItem: AdItem, newItem: AdItem): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: AdItem, newItem: AdItem): Boolean = oldItem == newItem

        override fun getChangePayload(oldItem: AdItem, newItem: AdItem): Any? {
            return if (
                oldItem.copy(playing = newItem.playing, muted = newItem.muted) == newItem &&
                (oldItem.playing != newItem.playing || oldItem.muted != newItem.muted)
            ) {
                VideoStatePayload
            } else {
                null
            }
        }
    }

    private object VideoStatePayload

    private companion object {
        private const val VIDEO_PROGRESS_INTERVAL_MS = 33L
        private const val VIDEO_PROGRESS_MAX = 10000L
        private const val MEDIA_RATIO_9_16 = 9f / 16f
        private const val CONTROLS_AUTO_HIDE_MS = 2_000L
        private const val CONTROLS_FADE_DURATION_MS = 500L

        private fun formatTime(milliseconds: Long): String {
            val totalSeconds = (milliseconds.coerceAtLeast(0L) / 1000L)
            val minutes = totalSeconds / 60L
            val seconds = totalSeconds % 60L
            return "$minutes:${seconds.toString().padStart(2, '0')}"
        }
    }
}
