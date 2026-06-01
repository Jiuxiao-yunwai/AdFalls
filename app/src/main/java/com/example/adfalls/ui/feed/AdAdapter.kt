package com.example.adfalls.ui.feed

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
        this.footerText = footerText
        submitList(items) {
            notifyDataSetChanged()
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
        private val media: View = itemView.findViewById(R.id.ad_media)
        private val title: TextView = itemView.findViewById(R.id.ad_title)
        private val brand: TextView = itemView.findViewById(R.id.ad_brand)
        private val summary: TextView = itemView.findViewById(R.id.ad_summary)
        private val tags: TextView = itemView.findViewById(R.id.ad_tags)
        private val stats: TextView = itemView.findViewById(R.id.ad_stats)
        private val like: TextView = itemView.findViewById(R.id.action_like)
        private val favorite: TextView = itemView.findViewById(R.id.action_favorite)
        private val share: TextView = itemView.findViewById(R.id.action_share)
        private val video: TextView? = itemView.findViewById(R.id.action_video)
        private val mute: TextView? = itemView.findViewById(R.id.action_mute)
        private val progressPanel: View? = itemView.findViewById(R.id.video_progress_panel)
        private val progress: ProgressBar? = itemView.findViewById(R.id.video_progress)
        private val time: TextView? = itemView.findViewById(R.id.video_time)
        private val playerView: PlayerView? = media as? PlayerView
        private var boundAd: AdItem? = null
        private val progressHandler = Handler(Looper.getMainLooper())
        private var progressRunnable: Runnable? = null

        fun bind(ad: AdItem) {
            stopProgressUpdates()
            boundAd = ad
            title.text = ad.title
            brand.text = ad.brand
            summary.text = ad.summary
            tags.text = ad.tags.joinToString("  ") { "#$it" }
            stats.text = "曝光 ${ad.impressions} · 点击 ${ad.clicks}"
            like.text = if (ad.liked) "已赞 ${ad.likes}" else "点赞 ${ad.likes}"
            favorite.text = if (ad.favorited) "已收藏" else "收藏"
            share.text = "分享 ${ad.shares}"
            video?.text = if (ad.playing) "暂停" else "播放"
            mute?.text = if (ad.muted) "静音" else "有声"
            video?.visibility = if (ad.type == AdCardType.VIDEO) View.VISIBLE else View.GONE
            mute?.visibility = if (ad.type == AdCardType.VIDEO) View.VISIBLE else View.GONE
            progressPanel?.visibility = if (ad.type == AdCardType.VIDEO) View.VISIBLE else View.GONE
            if (ad.type == AdCardType.VIDEO) startProgressUpdates(ad.id)

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

            itemView.setOnClickListener { onCardClick(ad) }
            media.setOnClickListener {
                if (ad.type == AdCardType.VIDEO) {
                    onVideoClick(ad)
                } else {
                    onCardClick(ad)
                }
            }
            like.setOnClickListener { onLikeClick(ad) }
            favorite.setOnClickListener { onFavoriteClick(ad) }
            share.setOnClickListener { onShareClick(ad) }
            tags.setOnClickListener { ad.tags.firstOrNull()?.let(onTagClick) }
            video?.setOnClickListener { onVideoClick(ad) }
            mute?.setOnClickListener { onMuteClick(ad) }
        }

        fun detachVideo() {
            stopProgressUpdates()
            playerView?.let(VideoPlaybackPool::detach)
        }

        fun getPlayerView(): PlayerView? = playerView

        fun getBoundAd(): AdItem? = boundAd

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

        private fun updateProgress(adId: Long) {
            val (position, duration) = VideoPlaybackPool.progress(adId)
            progress?.progress = if (duration > 0L) {
                ((position.coerceAtMost(duration) * VIDEO_PROGRESS_MAX) / duration).toInt()
            } else {
                0
            }
            time?.text = "${formatTime(position)} / ${formatTime(duration)}"
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
    }

    private companion object {
        private const val VIDEO_PROGRESS_INTERVAL_MS = 500L
        private const val VIDEO_PROGRESS_MAX = 1000L

        private fun formatTime(milliseconds: Long): String {
            val totalSeconds = (milliseconds.coerceAtLeast(0L) / 1000L)
            val minutes = totalSeconds / 60L
            val seconds = totalSeconds % 60L
            return "$minutes:${seconds.toString().padStart(2, '0')}"
        }
    }
}
