package com.example.adfalls

import android.graphics.Color

object AdRepository {
    private const val PAGE_SIZE = 6
    private var nextId = 100L
    private val ads = mutableMapOf<AdChannel, MutableList<AdItem>>()
    private val exposedIds = mutableSetOf<Long>()
    private val palette = listOf(
        Color.rgb(78, 164, 255),
        Color.rgb(255, 120, 104),
        Color.rgb(62, 196, 145),
        Color.rgb(245, 184, 74),
        Color.rgb(178, 132, 255),
        Color.rgb(58, 202, 214)
    )

    init {
        AdChannel.entries.forEach { channel ->
            ads[channel] = seedAds(channel).toMutableList()
        }
    }

    fun getAds(channel: AdChannel): List<AdItem> = ads[channel].orEmpty()

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
        return ads.values.asSequence().flatten().firstOrNull { it.id == id }
    }

    fun refresh(channel: AdChannel): List<AdItem> {
        val refreshed = seedAds(channel).mapIndexed { index, ad ->
            ad.copy(id = channel.ordinal * 1000L + index + nextId++)
        }
        ads[channel] = refreshed.toMutableList()
        return getAds(channel)
    }

    fun loadMore(channel: AdChannel): List<AdItem> {
        val more = List(PAGE_SIZE) { index ->
            val type = AdCardType.entries[(index + ads[channel].orEmpty().size) % AdCardType.entries.size]
            val id = nextId++
            AdItem(
                id = id,
                channel = channel,
                type = type,
                title = "${channel.title}广告灵感 ${id}",
                brand = listOf("Luma", "Orbit", "Haven", "NOVA")[index % 4],
                summary = "AI 摘要：面向${channel.title}人群，突出即时权益、使用场景和转化理由。",
                detail = "这是一条追加加载的本地 mock 广告。它用于验证上拉加载、卡片复用、详情跳转和状态同步。",
                tags = listOf(channel.title, "AI标签", if (type == AdCardType.VIDEO) "视频" else "图文"),
                mediaColor = palette[(index + channel.ordinal) % palette.size],
                likes = 12 + index,
                shares = 3 + index
            )
        }
        ads.getValue(channel).addAll(more)
        return getAds(channel)
    }

    fun toggleLike(id: Long) {
        update(id) { ad ->
            val liked = !ad.liked
            ad.copy(liked = liked, likes = (ad.likes + if (liked) 1 else -1).coerceAtLeast(0))
        }
    }

    fun toggleFavorite(id: Long) {
        update(id) { it.copy(favorited = !it.favorited) }
    }

    fun share(id: Long) {
        update(id) { it.copy(shares = it.shares + 1) }
    }

    fun registerClick(id: Long) {
        update(id) { it.copy(clicks = it.clicks + 1) }
    }

    fun registerImpression(id: Long): Boolean {
        return if (exposedIds.add(id)) {
            update(id) { it.copy(impressions = it.impressions + 1) }
            true
        } else {
            false
        }
    }

    fun setVideoState(id: Long, playing: Boolean? = null, muted: Boolean? = null) {
        update(id) { ad ->
            ad.copy(
                playing = playing ?: ad.playing,
                muted = muted ?: ad.muted
            )
        }
    }

    private fun update(id: Long, transform: (AdItem) -> AdItem) {
        ads.values.forEach { list ->
            val index = list.indexOfFirst { it.id == id }
            if (index >= 0) {
                list[index] = transform(list[index])
                return
            }
        }
    }

    private fun seedAds(channel: AdChannel): List<AdItem> {
        val base = when (channel) {
            AdChannel.FEATURED -> listOf(
                Triple("城市夜跑能量补给", "PulseRun", listOf("运动", "年轻人", "高转化")),
                Triple("周末露营轻装备", "CampGo", listOf("户外", "轻量", "周末")),
                Triple("通勤咖啡订阅", "BrewNow", listOf("咖啡", "白领", "订阅"))
            )
            AdChannel.COMMERCE -> listOf(
                Triple("春季衣橱焕新", "ModeLab", listOf("服饰", "电商", "满减")),
                Triple("智能清洁套装", "HomeBot", listOf("家居", "效率", "新品")),
                Triple("学生党数码精选", "PixelBox", listOf("数码", "学生", "性价比"))
            )
            AdChannel.LOCAL -> listOf(
                Triple("附近新开轻食店", "GreenBite", listOf("本地", "餐饮", "午餐")),
                Triple("城市艺术展早鸟票", "ArtLoop", listOf("展览", "周末", "早鸟")),
                Triple("社区健身体验课", "FitBlock", listOf("健身", "附近", "体验"))
            )
        }
        return List(PAGE_SIZE) { index ->
            val seed = base[index % base.size]
            val type = AdCardType.entries[index % AdCardType.entries.size]
            AdItem(
                id = channel.ordinal * 100L + index + 1,
                channel = channel,
                type = type,
                title = seed.first,
                brand = seed.second,
                summary = "AI 摘要：${seed.second}适合关注${seed.third.joinToString("、")}的用户，卖点清晰，适合信息流快速决策。",
                detail = "详情页展示更完整的图文/视频广告内容，并与信息流共享点赞、收藏、分享、点击和曝光状态。",
                tags = seed.third,
                mediaColor = palette[(index + channel.ordinal) % palette.size],
                likes = 24 + index * 3,
                shares = 5 + index
            )
        }
    }

}
