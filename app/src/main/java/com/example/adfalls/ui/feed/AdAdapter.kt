package com.example.adfalls.ui.feed

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.adfalls.R
import com.example.adfalls.data.model.AdCardType
import com.example.adfalls.data.model.AdItem

class AdAdapter(
    private val onCardClick: (AdItem) -> Unit,
    private val onLikeClick: (AdItem) -> Unit,
    private val onFavoriteClick: (AdItem) -> Unit,
    private val onShareClick: (AdItem) -> Unit,
    private val onVideoClick: (AdItem) -> Unit,
    private val onMuteClick: (AdItem) -> Unit
) : ListAdapter<AdItem, RecyclerView.ViewHolder>(Diff) {
    private var showEndReached = false

    fun submitAds(items: List<AdItem>, endReached: Boolean, commitCallback: (() -> Unit)? = null) {
        showEndReached = endReached
        submitList(items) {
            notifyDataSetChanged()
            commitCallback?.invoke()
        }
    }

    override fun getItemCount(): Int = super.getItemCount() + if (showEndReached) 1 else 0

    override fun getItemViewType(position: Int): Int {
        if (showEndReached && position == currentList.size) return R.layout.item_feed_end
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
        if (holder is AdViewHolder) holder.bind(getItem(position))
    }

    private class EndViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

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

        fun bind(ad: AdItem) {
            title.text = ad.title
            brand.text = ad.brand
            summary.text = ad.summary
            tags.text = ad.tags.joinToString("  ") { "#$it" }
            stats.text = "曝光 ${ad.impressions} · 点击 ${ad.clicks}"
            like.text = if (ad.liked) "♥ ${ad.likes}" else "♡ ${ad.likes}"
            favorite.text = if (ad.favorited) "★" else "☆"
            share.text = "↗ ${ad.shares}"
            video?.text = if (ad.playing) "暂停" else "播放"
            mute?.text = if (ad.muted) "静音" else "有声"
            video?.visibility = if (ad.playing) View.VISIBLE else View.GONE
            mute?.visibility = if (ad.playing) View.VISIBLE else View.GONE

            media.background = mediaBackground(ad.mediaColor, ad.type)
            like.isSelected = ad.liked
            favorite.isSelected = ad.favorited
            like.contentDescription = if (ad.liked) "取消点赞" else "点赞"
            favorite.contentDescription = if (ad.favorited) "取消收藏" else "收藏"
            share.contentDescription = "分享"

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
            video?.setOnClickListener { onVideoClick(ad) }
            mute?.setOnClickListener { onMuteClick(ad) }
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
}
