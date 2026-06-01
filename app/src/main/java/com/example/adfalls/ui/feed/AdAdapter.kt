package com.example.adfalls.ui.feed

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
        private val playerView: PlayerView? = media as? PlayerView
        private var boundAd: AdItem? = null

        fun bind(ad: AdItem) {
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
            video?.visibility = if (ad.playing) View.VISIBLE else View.GONE
            mute?.visibility = if (ad.playing) View.VISIBLE else View.GONE

            media.background = mediaBackground(ad.mediaColor, ad.type)
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
                    val showControls = video?.visibility != View.VISIBLE
                    video?.visibility = if (showControls) View.VISIBLE else View.GONE
                    mute?.visibility = if (showControls) View.VISIBLE else View.GONE
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
            playerView?.let(VideoPlaybackPool::detach)
        }

        fun getPlayerView(): PlayerView? = playerView

        fun getBoundAd(): AdItem? = boundAd
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
}
