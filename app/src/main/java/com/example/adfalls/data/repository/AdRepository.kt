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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

object AdRepository {
    private const val PAGE_SIZE = 6
    private const val FIXED_AD_COUNT = 50
    private var adDao: AdDao? = null
    private val operationMutex = Mutex()
    private val visibleRevision = MutableStateFlow(0)
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
        adDao = AppDatabase.getInstance(context).adDao()
    }

    fun observeAdsByChannel(channel: AdChannel): Flow<List<AdItem>> {
        return flow {
            seedIfNeeded()
            ensureVisible(channel)
            emitAll(
                combine(dao().observeAdsByChannel(channel.name), visibleRevision) { entities, _ ->
                    val visible = visibleSnapshot(channel)
                    if (visible.isEmpty()) {
                        emptyList()
                    } else {
                        val order = visible.withIndex().associate { it.value to it.index }
                        entities
                            .filter { it.id in order }
                            .sortedBy { order[it.id] }
                            .map { it.toModel() }
                    }
                }
            )
        }.flowOn(Dispatchers.IO)
    }

    fun observeAdById(id: Long): Flow<AdItem?> {
        return flow {
            seedIfNeeded()
            emitAll(dao().observeAdById(id).map { it?.toModel() })
        }.flowOn(Dispatchers.IO)
    }

    suspend fun findAd(id: Long): AdItem? = withContext(Dispatchers.IO) {
        seedIfNeeded()
        dao().getAdById(id)?.toModel()
    }

    suspend fun refresh(channel: AdChannel): Boolean = withContext(Dispatchers.IO) {
        seedIfNeeded()
        operationMutex.withLock {
            requestNextPageLocked(channel, replaceVisible = true).also { changed ->
                if (changed) bumpVisibleRevision()
            }
        }
    }

    suspend fun loadMore(channel: AdChannel): Boolean = withContext(Dispatchers.IO) {
        seedIfNeeded()
        operationMutex.withLock {
            val changed = if (visibleIds[channel].isNullOrEmpty()) {
                requestNextPageLocked(channel, replaceVisible = true)
            } else {
                requestNextPageLocked(channel, replaceVisible = false)
            }
            if (changed) bumpVisibleRevision()
            changed
        }
    }

    suspend fun toggleLike(id: Long) = withContext(Dispatchers.IO) {
        findAd(id)?.let { ad ->
            val liked = !ad.liked
            val likes = (ad.likes + if (liked) 1 else -1).coerceAtLeast(0)
            dao().updateLike(id, liked, likes)
        }
    }

    suspend fun toggleFavorite(id: Long) = withContext(Dispatchers.IO) {
        findAd(id)?.let { dao().updateFavorite(id, !it.favorited) }
    }

    suspend fun share(id: Long) = withContext(Dispatchers.IO) {
        seedIfNeeded()
        dao().addShare(id)
    }

    suspend fun registerClick(id: Long) = withContext(Dispatchers.IO) {
        seedIfNeeded()
        dao().addClick(id)
    }

    suspend fun registerImpressions(ids: List<Long>) = withContext(Dispatchers.IO) {
        seedIfNeeded()
        operationMutex.withLock {
            ids.distinct().forEach { id ->
                if (exposedIds.add(id)) {
                    dao().addImpression(id)
                }
            }
        }
    }

    suspend fun setVideoState(id: Long, playing: Boolean? = null, muted: Boolean? = null) = withContext(Dispatchers.IO) {
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

    private suspend fun seedIfNeeded() {
        operationMutex.withLock {
            if (dao().countAds() != FIXED_AD_COUNT) {
                visibleIds.clear()
                requestedIds.clear()
                exposedIds.clear()
                dao().deleteAllAds()
                dao().insertAds(fixedAds().map { it.toEntity() })
                bumpVisibleRevision()
            }
        }
    }

    private suspend fun ensureVisible(channel: AdChannel) {
        operationMutex.withLock {
            if (visibleIds[channel].isNullOrEmpty()) {
                if (requestNextPageLocked(channel, replaceVisible = true)) {
                    bumpVisibleRevision()
                }
            }
        }
    }

    private suspend fun requestNextPageLocked(channel: AdChannel, replaceVisible: Boolean): Boolean {
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

    private suspend fun visibleSnapshot(channel: AdChannel): List<Long> {
        return operationMutex.withLock { visibleIds[channel].orEmpty().toList() }
    }

    private fun bumpVisibleRevision() {
        visibleRevision.value = visibleRevision.value + 1
    }

    private fun fixedAds(): List<AdItem> {
        val baseAds = listOf(
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
        return baseAds + generatedAds(startId = baseAds.size + 1L, count = FIXED_AD_COUNT - baseAds.size)
    }

    private fun generatedAds(startId: Long, count: Int): List<AdItem> {
        val themes = listOf(
            AdSeed(AdChannel.FEATURED, "晨间效率计划", "Mornly", listOf("效率", "晨间", "习惯")),
            AdSeed(AdChannel.FEATURED, "旅行收纳灵感", "PackPro", listOf("旅行", "收纳", "轻便")),
            AdSeed(AdChannel.FEATURED, "城市快闪体验", "PopSpot", listOf("快闪", "体验", "年轻人")),
            AdSeed(AdChannel.COMMERCE, "夏日防晒套装", "Sunly", listOf("防晒", "夏日", "护肤")),
            AdSeed(AdChannel.COMMERCE, "智能睡眠枕", "SleepLab", listOf("睡眠", "智能", "家居")),
            AdSeed(AdChannel.COMMERCE, "办公零食补给", "SnackHub", listOf("零食", "办公", "补给")),
            AdSeed(AdChannel.COMMERCE, "儿童学习平板", "KidTab", listOf("教育", "儿童", "数码")),
            AdSeed(AdChannel.LOCAL, "周末音乐小现场", "LiveCorner", listOf("音乐", "周末", "本地")),
            AdSeed(AdChannel.LOCAL, "社区旧物交换", "SwapDay", listOf("社区", "环保", "交换")),
            AdSeed(AdChannel.LOCAL, "城市徒步路线", "WalkMap", listOf("徒步", "城市", "路线"))
        )
        return List(count) { index ->
            val id = startId + index
            val seed = themes[index % themes.size]
            val type = AdCardType.entries[index % AdCardType.entries.size]
            ad(
                id = id,
                channel = seed.channel,
                type = type,
                title = "${seed.title} ${index / themes.size + 1}",
                brand = seed.brand,
                tags = seed.tags,
                likes = 14 + index,
                shares = 3 + index % 8
            )
        }
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

    private data class AdSeed(
        val channel: AdChannel,
        val title: String,
        val brand: String,
        val tags: List<String>
    )

}
