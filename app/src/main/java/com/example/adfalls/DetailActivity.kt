package com.example.adfalls

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.TextView

class DetailActivity : Activity() {
    private var adId: Long = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        setContentView(R.layout.activity_detail)
        adId = intent.getLongExtra(EXTRA_AD_ID, -1L)

        findViewById<View>(R.id.back_button).setOnClickListener { finish() }
        bind()
    }

    override fun onResume() {
        super.onResume()
        val ad = AdRepository.findAd(adId)
        if (ad?.type == AdCardType.VIDEO) {
            VideoPlaybackPool.play(adId)
            bind()
        }
    }

    override fun onPause() {
        val ad = AdRepository.findAd(adId)
        if (ad?.type == AdCardType.VIDEO) {
            VideoPlaybackPool.pause(adId)
        }
        super.onPause()
    }

    private fun bind() {
        val ad = AdRepository.findAd(adId) ?: run {
            finish()
            return
        }

        findViewById<TextView>(R.id.detail_channel).text = ad.channel.title
        findViewById<TextView>(R.id.detail_title).text = ad.title
        findViewById<TextView>(R.id.detail_brand).text = ad.brand
        findViewById<TextView>(R.id.detail_summary).text = ad.summary
        findViewById<TextView>(R.id.detail_body).text = ad.detail
        findViewById<TextView>(R.id.detail_tags).text = ad.tags.joinToString("  ") { "#$it" }
        findViewById<TextView>(R.id.detail_stats).text =
            "曝光 ${ad.impressions} · 点击 ${ad.clicks} · 点赞 ${ad.likes} · 分享 ${ad.shares}"
        findViewById<View>(R.id.detail_media).background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(ad.mediaColor, darken(ad.mediaColor))
        ).apply { cornerRadius = 22f }

        val like = findViewById<TextView>(R.id.detail_like)
        val favorite = findViewById<TextView>(R.id.detail_favorite)
        val share = findViewById<TextView>(R.id.detail_share)
        val video = findViewById<TextView>(R.id.detail_video)
        val mute = findViewById<TextView>(R.id.detail_mute)

        like.text = if (ad.liked) "已赞 ${ad.likes}" else "点赞 ${ad.likes}"
        favorite.text = if (ad.favorited) "已收藏" else "收藏"
        share.text = "分享"
        video.text = if (ad.playing) "暂停视频" else "播放视频"
        mute.text = if (ad.muted) "静音" else "有声"
        video.visibility = if (ad.type == AdCardType.VIDEO) View.VISIBLE else View.GONE
        mute.visibility = if (ad.type == AdCardType.VIDEO) View.VISIBLE else View.GONE

        like.setOnClickListener {
            AdRepository.toggleLike(adId)
            bind()
        }
        favorite.setOnClickListener {
            AdRepository.toggleFavorite(adId)
            bind()
        }
        share.setOnClickListener {
            AdRepository.share(adId)
            bind()
        }
        video.setOnClickListener {
            VideoPlaybackPool.togglePlay(adId)
            bind()
        }
        mute.setOnClickListener {
            VideoPlaybackPool.toggleMute(adId)
            bind()
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
    }
}
