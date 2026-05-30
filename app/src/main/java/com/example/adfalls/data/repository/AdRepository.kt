package com.example.adfalls.data.repository

import android.content.Context
import android.graphics.Color
import com.example.adfalls.data.local.AdDao
import com.example.adfalls.data.local.AppDatabase
import com.example.adfalls.data.local.toEntity
import com.example.adfalls.data.local.toModel
import com.example.adfalls.data.model.AdCardType
import com.example.adfalls.data.model.AdChannel
import com.example.adfalls.data.model.AdItem

object AdRepository {
    private const val PAGE_SIZE = 6
    private const val FIXED_AD_COUNT = 20
    private var adDao: AdDao? = null
    private val visibleIds = mutableMapOf<AdChannel, MutableList<Long>>()
    private val requestedIds = mutableMapOf<AdChannel, MutableSet<Long>>()
    private val exposedIds = mutableSetOf<Long>()
    private val palette = listOf(
        Color.rgb(78, 164, 255),
        Color.rgb(255, 120, 104),
        Color.rgb(62, 196, 145),
        Color.rgb(245, 184, 74),
        Color.rgb(178, 132, 255),
        Color.rgb(58, 202, 214)
    )

    fun initialize(context: Context) {
        if (adDao != null) return
        val dao = AppDatabase.getInstance(context).adDao()
        adDao = dao
        if (dao.countAds() != FIXED_AD_COUNT) {
            dao.deleteAllAds()
            dao.insertAds(fixedAds().map { it.toEntity() })
        }
    }

    fun getAds(channel: AdChannel): List<AdItem> {
        ensureVisible(channel)
        return getVisibleAds(channel)
    }

    fun search(channel: AdChannel, query: String): List<AdItem> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return getAds(channel)
        return getAds(channel).filter { ad ->
            ad.title.contains(trimmed, ignoreCase = true) ||
                ad.summary.contains(trimmed, ignoreCase = true) ||
                ad.brand.contains(trimmed, ignoreCase = true) ||
                ad.tags.any { it.contains(trimmed, ignoreCase = true) }
        }
    }

    fun findAd(id: Long): AdItem? {
        return dao().getAdById(id)?.toModel()
    }

    fun refresh(channel: AdChannel): List<AdItem> {
        requestNextPage(channel, replaceVisible = true)
        return getAds(channel)
    }

    fun loadMore(channel: AdChannel): Boolean {
        ensureVisible(channel)
        return requestNextPage(channel, replaceVisible = false)
    }

    fun toggleLike(id: Long) {
        findAd(id)?.let { ad ->
            val liked = !ad.liked
            val likes = (ad.likes + if (liked) 1 else -1).coerceAtLeast(0)
            dao().updateLike(id, liked, likes)
        }
    }

    fun toggleFavorite(id: Long) {
        findAd(id)?.let { dao().updateFavorite(id, !it.favorited) }
    }

    fun share(id: Long) {
        dao().addShare(id)
    }

    fun registerClick(id: Long) {
        dao().addClick(id)
    }

    fun registerImpression(id: Long): Boolean {
        return if (exposedIds.add(id)) {
            dao().addImpression(id)
            true
        } else {
            false
        }
    }

    fun setVideoState(id: Long, playing: Boolean? = null, muted: Boolean? = null) {
        findAd(id)?.let { ad ->
            dao().updateVideoState(
                id = id,
                playing = playing ?: ad.playing,
                muted = muted ?: ad.muted
            )
        }
    }

    private fun dao(): AdDao {
        return checkNotNull(adDao) { "AdRepository must be initialized from AdFallsApp before use." }
    }

    private fun ensureVisible(channel: AdChannel) {
        if (visibleIds[channel].isNullOrEmpty()) {
            requestNextPage(channel, replaceVisible = true)
        }
    }

    private fun requestNextPage(channel: AdChannel, replaceVisible: Boolean): Boolean {
        val requested = requestedIds.getOrPut(channel) { mutableSetOf() }
        val candidates = dao().getAdsByChannel(channel.name)
            .filterNot { it.id in requested }
            .take(PAGE_SIZE)
        if (candidates.isEmpty()) return false

        requested.addAll(candidates.map { it.id })
        val visible = visibleIds.getOrPut(channel) { mutableListOf() }
        if (replaceVisible) visible.clear()
        visible.addAll(candidates.map { it.id })
        return true
    }

    private fun getVisibleAds(channel: AdChannel): List<AdItem> {
        val ids = visibleIds[channel].orEmpty()
        if (ids.isEmpty()) return emptyList()
        val order = ids.withIndex().associate { it.value to it.index }
        return dao().getAdsByIds(ids)
            .sortedBy { order[it.id] }
            .map { it.toModel() }
    }

    private fun fixedAds(): List<AdItem> {
        return listOf(
            ad(1, AdChannel.FEATURED, AdCardType.LARGE_IMAGE, "城市夜跑能量补给", "PulseRun", listOf("运动", "年轻人", "高转化"), 24, 5),
            ad(2, AdChannel.FEATURED, AdCardType.SMALL_IMAGE, "周末露营轻装备", "CampGo", listOf("户外", "轻量", "周末"), 27, 6),
            ad(3, AdChannel.FEATURED, AdCardType.VIDEO, "通勤咖啡订阅", "BrewNow", listOf("咖啡", "白领", "订阅"), 30, 7),
            ad(4, AdChannel.FEATURED, AdCardType.LARGE_IMAGE, "高效办公桌搭", "Deskly", listOf("办公", "效率", "质感"), 18, 4),
            ad(5, AdChannel.FEATURED, AdCardType.SMALL_IMAGE, "轻运动护肤计划", "GlowFit", listOf("护肤", "运动", "清爽"), 32, 8),
            ad(6, AdChannel.FEATURED, AdCardType.VIDEO, "城市短途骑行", "VoltBike", listOf("骑行", "低碳", "通勤"), 21, 5),
            ad(7, AdChannel.FEATURED, AdCardType.LARGE_IMAGE, "家庭观影升级", "CineHome", listOf("影音", "家庭", "沉浸"), 29, 6),
            ad(8, AdChannel.COMMERCE, AdCardType.LARGE_IMAGE, "春季衣橱焕新", "ModeLab", listOf("服饰", "电商", "满减"), 24, 5),
            ad(9, AdChannel.COMMERCE, AdCardType.SMALL_IMAGE, "智能清洁套装", "HomeBot", listOf("家居", "效率", "新品"), 27, 6),
            ad(10, AdChannel.COMMERCE, AdCardType.VIDEO, "学生党数码精选", "PixelBox", listOf("数码", "学生", "性价比"), 30, 7),
            ad(11, AdChannel.COMMERCE, AdCardType.LARGE_IMAGE, "轻奢香氛礼盒", "AromaBox", listOf("礼盒", "香氛", "节日"), 16, 4),
            ad(12, AdChannel.COMMERCE, AdCardType.SMALL_IMAGE, "厨房小家电组合", "CookMate", listOf("厨房", "组合", "省心"), 22, 5),
            ad(13, AdChannel.COMMERCE, AdCardType.VIDEO, "户外鞋服限时购", "TrailWear", listOf("户外", "限时", "鞋服"), 25, 6),
            ad(14, AdChannel.COMMERCE, AdCardType.LARGE_IMAGE, "宠物智能喂养", "PawPlus", listOf("宠物", "智能", "日常"), 19, 4),
            ad(15, AdChannel.LOCAL, AdCardType.LARGE_IMAGE, "附近新开轻食店", "GreenBite", listOf("本地", "餐饮", "午餐"), 24, 5),
            ad(16, AdChannel.LOCAL, AdCardType.SMALL_IMAGE, "城市艺术展早鸟票", "ArtLoop", listOf("展览", "周末", "早鸟"), 27, 6),
            ad(17, AdChannel.LOCAL, AdCardType.VIDEO, "社区健身体验课", "FitBlock", listOf("健身", "附近", "体验"), 30, 7),
            ad(18, AdChannel.LOCAL, AdCardType.LARGE_IMAGE, "亲子手作工作坊", "HandyKid", listOf("亲子", "手作", "周末"), 17, 4),
            ad(19, AdChannel.LOCAL, AdCardType.SMALL_IMAGE, "街区咖啡地图", "BeanWalk", listOf("咖啡", "街区", "探店"), 23, 5),
            ad(20, AdChannel.LOCAL, AdCardType.VIDEO, "夜间市集攻略", "NightBazaar", listOf("市集", "夜生活", "本地"), 26, 6)
        )
    }

    private fun ad(
        id: Long,
        channel: AdChannel,
        type: AdCardType,
        title: String,
        brand: String,
        tags: List<String>,
        likes: Int,
        shares: Int
    ): AdItem {
        return AdItem(
            id = id,
            channel = channel,
            type = type,
            title = title,
            brand = brand,
            summary = "AI 摘要：${brand}适合关注${tags.joinToString("、")}的用户，卖点清晰，适合信息流快速决策。",
            detail = "详情页展示更完整的图文/视频广告内容，并与信息流共享点赞、收藏、分享、点击和曝光状态。",
            tags = tags,
            mediaColor = palette[((id - 1) + channel.ordinal).toInt() % palette.size],
            likes = likes,
            shares = shares
        )
    }

}
