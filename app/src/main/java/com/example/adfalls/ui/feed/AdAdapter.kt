package com.example.adfalls.ui.feed

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
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
import kotlin.math.roundToInt

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
        submitList(items) {
            if (this.footerText != footerText) {
                this.footerText = footerText
                notifyDataSetChanged()
            }
            commitCallback?.invoke()
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
        private val stats: TextView? = itemView.findViewById(R.id.ad_stats)
        private val like: TextView = itemView.findViewById(R.id.action_like)
        private val favorite: TextView? = itemView.findViewById(R.id.action_favorite)
        private val share: TextView? = itemView.findViewById(R.id.action_share)
        private val video: ImageButton? = itemView.findViewById(R.id.action_video)
        private val mute: ImageButton? = itemView.findViewById(R.id.action_mute)
        private val progressPanel: View? = itemView.findViewById(R.id.video_progress_panel)
        private val progress: ProgressBar? = itemView.findViewById(R.id.video_progress)
        private val time: TextView? = itemView.findViewById(R.id.video_time)
        private var playerView: PlayerView? = null
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
            bindTags(ad.tags)
            stats?.text = "曝光 ${ad.impressions} · 点击 ${ad.clicks}"
            like.text = ad.likes.toString()
            favorite?.text = if (ad.favorited) "已存" else "收藏"
            share?.text = ad.shares.toString()
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
            media.background = mediaBackground(ad.mediaColor, ad.type, itemView.resources.displayMetrics.density)
            if (ad.type == AdCardType.VIDEO) {
                ensurePlayerView()?.let {
                    it.useController = false
                    VideoPlaybackPool.attach(it, ad.id, ad.videoUrl, ad.playing, ad.muted)
                }
            } else {
                playerView?.let(VideoPlaybackPool::detach)
            }
            like.isSelected = ad.liked
            favorite?.isSelected = ad.favorited
            like.contentDescription = if (ad.liked) "取消点赞" else "点赞"
            favorite?.contentDescription = if (ad.favorited) "取消收藏" else "收藏"
            share?.contentDescription = "分享"
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
            playerView?.setOnClickListener {
                toggleVideoFromUser(boundAd ?: ad)
            }
            like.setOnClickListener {
                animateLikeTap()
                onLikeClick(boundAd ?: ad)
            }
            favorite?.setOnClickListener { onFavoriteClick(boundAd ?: ad) }
            share?.setOnClickListener { onShareClick(boundAd ?: ad) }
            video?.setOnClickListener {
                toggleVideoFromUser(boundAd ?: ad)
            }
            mute?.setOnClickListener {
                keepControlsVisibleOnNextBind = true
                showPlaybackControls(scheduleHide = true)
                onMuteClick(boundAd ?: ad)
            }
        }

        private fun bindTags(values: List<String>) {
            if (values.isEmpty()) {
                tags.text = ""
                tags.movementMethod = null
                tags.setOnClickListener(null)
                return
            }

            val builder = SpannableStringBuilder()
            values.forEachIndexed { index, value ->
                if (index > 0) builder.append("  ")
                val start = builder.length
                builder.append("#").append(value)
                val end = builder.length
                builder.setSpan(
                    object : ClickableSpan() {
                        override fun onClick(widget: View) {
                            onTagClick(value)
                        }

                        override fun updateDrawState(ds: TextPaint) {
                            super.updateDrawState(ds)
                            ds.isUnderlineText = false
                            ds.color = tags.currentTextColor
                        }
                    },
                    start,
                    end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            tags.text = builder
            tags.linksClickable = true
            tags.isClickable = true
            tags.highlightColor = Color.TRANSPARENT
            tags.movementMethod = LinkMovementMethod.getInstance()
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

        private fun ensurePlayerView(): PlayerView? {
            if (itemView.isInEditMode) return null
            val existing = playerView
            if (existing != null) return existing
            val container = media as? ViewGroup ?: return null
            return PlayerView(itemView.context).apply {
                useController = false
                useArtwork = false
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                container.addView(this, 0)
                playerView = this
            }
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

        private fun animateLikeTap() {
            like.animate().cancel()
            like.scaleX = 0.9f
            like.scaleY = 0.9f
            like.animate()
                .scaleX(1.22f)
                .scaleY(1.22f)
                .setDuration(LIKE_POP_UP_MS)
                .setInterpolator(OvershootInterpolator(1.8f))
                .withEndAction {
                    like.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(LIKE_SETTLE_MS)
                        .start()
                }
                .start()
        }
    }

    private fun mediaBackground(color: Int, type: AdCardType, density: Float): GradientDrawable {
        return GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(color, darken(color))
        ).apply {
            cornerRadius = 10f * density
            if (type == AdCardType.VIDEO) {
                setStroke((1.5f * density).roundToInt().coerceAtLeast(1), Color.argb(160, 255, 255, 255))
            }
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
        private const val CONTROLS_AUTO_HIDE_MS = 1_000L
        private const val CONTROLS_FADE_DURATION_MS = 500L
        private const val LIKE_POP_UP_MS = 130L
        private const val LIKE_SETTLE_MS = 90L

        private fun formatTime(milliseconds: Long): String {
            val totalSeconds = (milliseconds.coerceAtLeast(0L) / 1000L)
            val minutes = totalSeconds / 60L
            val seconds = totalSeconds % 60L
            return "$minutes:${seconds.toString().padStart(2, '0')}"
        }
    }
}
