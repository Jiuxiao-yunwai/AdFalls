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
import com.example.adfalls.data.remote.AdFallsApiClient
import com.example.adfalls.data.remote.BackendUserSession
import com.example.adfalls.data.remote.FakeAdRemoteDataSource
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
    private const val PAGE_SIZE = 10
    private const val FIXED_AD_COUNT = 50
    private const val FIRST_AD_TITLE = "城市夜跑能量补给"
    private const val OLD_SAMPLE_VIDEO_URL = "https://storage.googleapis.com/exoplayer-test-media-0/BigBuckBunny_320x180.mp4"
    private const val REMOTE_SAMPLE_VIDEO_URL = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4"
    private const val LOCAL_VIDEO_URL_PREFIX = "file:"

    private var adDao: AdDao? = null
    private var appContext: Context? = null
    private var userSession: BackendUserSession? = null
    private var sampleVideoUrls: List<String> = emptyList()
    private val operationMutex = Mutex()
    private val visibleRevision = MutableStateFlow(0)
    private val visibleIds = mutableMapOf<AdChannel, MutableList<Long>>()
    private val requestedIds = mutableMapOf<AdChannel, MutableSet<Long>>()
    private val nextCursors = mutableMapOf<AdChannel, String>()
    private val hasMoreByChannel = mutableMapOf<AdChannel, Boolean>()
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
        appContext = context.applicationContext
        adDao = AppDatabase.getInstance(context).adDao()
    }

    internal fun isInitialized(): Boolean = adDao != null

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

    suspend fun refreshAdDetail(id: Long) = withContext(Dispatchers.IO) {
        seedIfNeeded()
        runCatching {
            val session = ensureUserSession()
            val localChannel = dao().getAdById(id)?.toModel()?.channel ?: AdChannel.FEATURED
            val ad = AdFallsApiClient.fetchAdDetail(id, session.userId).toModel(localChannel)
            upsertPreservingLocalState(listOf(ad))
            AdFallsApiClient.recordBehavior(session.userId, id, ad.channel, "DETAIL_VIEW")
        }
    }

    suspend fun findAds(ids: List<Long>): List<AdItem> = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext emptyList()
        seedIfNeeded()
        val adsById = dao().getAdsByIds(ids.distinct()).associate { it.id to it.toModel() }
        ids.mapNotNull(adsById::get)
    }

    suspend fun listAds(channel: AdChannel): List<AdItem> = withContext(Dispatchers.IO) {
        seedIfNeeded()
        dao().getAdsByChannel(channel.name).map { it.toModel() }
    }

    suspend fun searchAds(channel: AdChannel, query: String): List<AdItem> = withContext(Dispatchers.IO) {
        seedIfNeeded()
        runCatching {
            val session = ensureUserSession()
            val page = AdFallsApiClient.searchAds(
                keyword = query,
                cursor = null,
                size = 50,
                userId = session.userId
            )
            upsertPreservingLocalState(page.items.map { it.toModel(channel) })
        }.getOrElse {
            FakeAdRemoteDataSource.searchAds(
                dao = dao(),
                channel = channel,
                query = query
            )
        }
    }

    suspend fun chatSearch(query: String): Pair<String, List<AdItem>> = withContext(Dispatchers.IO) {
        seedIfNeeded()
        runCatching {
            val session = ensureUserSession()
            val (reply, page) = AdFallsApiClient.chatSearch(
                userId = session.userId,
                message = query,
                cursor = null,
                size = 5
            )
            reply to upsertPreservingLocalState(page.items.map { it.toModel(AdChannel.FEATURED) })
        }.getOrElse {
            val fallback = FakeAdRemoteDataSource.searchAds(
                dao = dao(),
                channel = AdChannel.FEATURED,
                query = query
            ).take(5)
            "已为你找到相关广告。" to fallback
        }
    }

    suspend fun analyzeAd(adId: Long, query: String): String = withContext(Dispatchers.IO) {
        seedIfNeeded()
        val ad = findAd(adId)
        runCatching {
            val session = ensureUserSession()
            AdFallsApiClient.analyzeAd(
                userId = session.userId,
                adId = adId,
                message = query
            )
        }.getOrElse {
            if (ad == null) {
                "没有找到这条广告的详情，暂时无法完成分析。"
            } else {
                buildLocalAdAnalysis(ad)
            }
        }
    }

    suspend fun refresh(channel: AdChannel): Boolean = withContext(Dispatchers.IO) {
        seedIfNeeded()
        operationMutex.withLock {
            requestFirstPageLocked(channel).also { changed ->
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
            runCatching {
                val session = ensureUserSession()
                if (liked) {
                    AdFallsApiClient.like(session.userId, id)
                } else {
                    AdFallsApiClient.unlike(session.userId, id)
                }
            }
        }
    }

    suspend fun toggleFavorite(id: Long) = withContext(Dispatchers.IO) {
        findAd(id)?.let { ad ->
            val favorited = !ad.favorited
            runCatching {
                val session = ensureUserSession()
                if (favorited) {
                    AdFallsApiClient.favorite(session.userId, id)
                } else {
                    AdFallsApiClient.unfavorite(session.userId, id)
                }
            }
            dao().updateFavorite(id, favorited)
        }
    }

    suspend fun share(id: Long) = withContext(Dispatchers.IO) {
        seedIfNeeded()
        dao().addShare(id)
    }

    suspend fun registerClick(id: Long) = withContext(Dispatchers.IO) {
        seedIfNeeded()
        dao().addClick(id)
        findAd(id)?.let { ad ->
            runCatching {
                AdFallsApiClient.recordBehavior(
                    userId = ensureUserSession().userId,
                    adId = id,
                    channel = ad.channel,
                    behaviorType = "CLICK"
                )
            }
        }
    }

    suspend fun registerImpressions(ids: List<Long>) = withContext(Dispatchers.IO) {
        seedIfNeeded()
        operationMutex.withLock {
            ids.distinct().forEach { id ->
                if (exposedIds.add(id)) {
                    dao().addImpression(id)
                    dao().getAdById(id)?.toModel()?.let { ad ->
                        runCatching {
                            AdFallsApiClient.recordBehavior(
                                userId = ensureUserSession().userId,
                                adId = id,
                                channel = ad.channel,
                                behaviorType = "EXPOSURE"
                            )
                        }
                    }
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
            if (playing == true) {
                runCatching {
                    AdFallsApiClient.recordBehavior(
                        userId = ensureUserSession().userId,
                        adId = id,
                        channel = ad.channel,
                        behaviorType = "VIDEO_PLAY"
                    )
                }
            }
        }
    }

    suspend fun setAllVideoMuted(muted: Boolean) = withContext(Dispatchers.IO) {
        seedIfNeeded()
        dao().updateAllVideoMuted(muted)
    }

    suspend fun clearLocalAdData() = withContext(Dispatchers.IO) {
        operationMutex.withLock {
            userSession = null
            visibleIds.clear()
            requestedIds.clear()
            nextCursors.clear()
            hasMoreByChannel.clear()
            exposedIds.clear()
            dao().deleteAllAds()
            bumpVisibleRevision()
        }
    }

    private fun dao(): AdDao {
        return checkNotNull(adDao) { "AdRepository must be initialized from AdFallsApp before use." }
    }

    private suspend fun seedIfNeeded() {
        operationMutex.withLock {
            sampleVideoUrls = MockVideoGenerator.ensureVideos(checkNotNull(appContext))
            if (dao().countAds() == 0) {
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
        if (!replaceVisible && hasMoreByChannel[channel] == false) return false

        val candidates = runCatching {
            val session = ensureUserSession()
            val page = AdFallsApiClient.fetchFeed(
                channel = channel,
                cursor = if (replaceVisible) null else nextCursors[channel],
                size = PAGE_SIZE,
                userId = session.userId
            )
            nextCursors[channel] = page.nextCursor
            hasMoreByChannel[channel] = page.hasMore
            upsertPreservingLocalState(page.items.map { it.toModel(channel) })
        }.getOrElse {
            val requested = requestedIds.getOrPut(channel) { mutableSetOf() }
            val response = FakeAdRemoteDataSource.fetchAdPage(
                dao = dao(),
                channel = channel,
                requestedIds = requested,
                pageSize = PAGE_SIZE
            )
            hasMoreByChannel[channel] = response.hasMore
            response.ads
        }
        if (candidates.isEmpty()) return false

        requestedIds.getOrPut(channel) { mutableSetOf() }.addAll(candidates.map { it.id })
        val visible = visibleIds.getOrPut(channel) { mutableListOf() }
        if (replaceVisible) visible.clear()
        visible.addAll(candidates.map { it.id })
        return true
    }

    private suspend fun requestFirstPageLocked(channel: AdChannel): Boolean {
        requestedIds[channel]?.clear()
        visibleIds[channel]?.clear()
        nextCursors[channel] = ""
        hasMoreByChannel[channel] = true
        return requestNextPageLocked(channel, replaceVisible = true)
    }

    private suspend fun visibleSnapshot(channel: AdChannel): List<Long> {
        return operationMutex.withLock { visibleIds[channel].orEmpty().toList() }
    }

    private fun bumpVisibleRevision() {
        visibleRevision.value = visibleRevision.value + 1
    }

    private suspend fun ensureUserSession(): BackendUserSession {
        userSession?.let { return it }
        return AdFallsApiClient.login().also { userSession = it }
    }

    private suspend fun upsertPreservingLocalState(ads: List<AdItem>): List<AdItem> {
        if (ads.isEmpty()) return emptyList()
        val merged = ads.map { remote ->
            val local = dao().getAdById(remote.id)?.toModel()
            remote.copy(
                liked = local?.liked ?: remote.liked,
                favorited = remote.favorited,
                playing = local?.playing ?: false,
                muted = local?.muted ?: true,
                likes = local?.likes ?: remote.likes,
                shares = local?.shares ?: remote.shares,
                impressions = local?.impressions ?: remote.impressions,
                clicks = local?.clicks ?: remote.clicks
            )
        }
        dao().insertAds(merged.map { it.toEntity() })
        return merged
    }

    private fun fixedAds(): List<AdItem> {
        return listOf(
            ad(1, AdChannel.FEATURED, AdCardType.LARGE_IMAGE, "城市夜跑能量补给", "PulseRun", listOf("运动", "夜跑", "年轻人"), 42, 8),
            ad(2, AdChannel.FEATURED, AdCardType.SMALL_IMAGE, "周末露营轻装套装", "CampGo", listOf("户外", "露营", "周末"), 37, 7),
            ad(3, AdChannel.FEATURED, AdCardType.VIDEO, "通勤咖啡订阅计划", "BrewNow", listOf("咖啡", "通勤", "订阅"), 48, 10),
            ad(4, AdChannel.FEATURED, AdCardType.LARGE_IMAGE, "高效办公桌面改造", "Deskly", listOf("办公", "效率", "桌搭"), 31, 5),
            ad(5, AdChannel.FEATURED, AdCardType.SMALL_IMAGE, "轻运动护肤组合", "GlowFit", listOf("护肤", "运动", "清爽"), 52, 11),
            ad(6, AdChannel.FEATURED, AdCardType.VIDEO, "城市短途骑行体验", "VoltBike", listOf("骑行", "低碳", "通勤"), 39, 7),
            ad(7, AdChannel.FEATURED, AdCardType.LARGE_IMAGE, "家庭观影氛围升级", "CineHome", listOf("影音", "家庭", "沉浸"), 45, 9),
            ad(8, AdChannel.FEATURED, AdCardType.SMALL_IMAGE, "晨间效率习惯卡", "Mornly", listOf("效率", "晨间", "习惯"), 28, 4),
            ad(9, AdChannel.FEATURED, AdCardType.VIDEO, "旅行收纳灵感清单", "PackPro", listOf("旅行", "收纳", "轻便"), 34, 6),
            ad(10, AdChannel.FEATURED, AdCardType.LARGE_IMAGE, "城市快闪试香活动", "PopSpot", listOf("快闪", "香氛", "体验"), 41, 8),
            ad(11, AdChannel.FEATURED, AdCardType.SMALL_IMAGE, "无负担轻食便当", "FreshBox", listOf("轻食", "健康", "午餐"), 36, 6),
            ad(12, AdChannel.FEATURED, AdCardType.VIDEO, "智能睡眠唤醒灯", "SleepLab", listOf("睡眠", "智能", "家居"), 43, 8),
            ad(13, AdChannel.FEATURED, AdCardType.LARGE_IMAGE, "学生党数码精简包", "PixelBox", listOf("数码", "学生", "性价比"), 47, 9),
            ad(14, AdChannel.FEATURED, AdCardType.SMALL_IMAGE, "办公室零食补给站", "SnackHub", listOf("零食", "办公", "补给"), 33, 5),
            ad(15, AdChannel.FEATURED, AdCardType.VIDEO, "夏日防晒出门三件套", "Sunly", listOf("防晒", "夏日", "护肤"), 50, 12),
            ad(16, AdChannel.FEATURED, AdCardType.LARGE_IMAGE, "音乐节轻装备指南", "LiveKit", listOf("音乐节", "穿搭", "户外"), 44, 9),
            ad(17, AdChannel.COMMERCE, AdCardType.LARGE_IMAGE, "春季衣橱焕新", "ModeLab", listOf("服饰", "电商", "满减"), 46, 9),
            ad(18, AdChannel.COMMERCE, AdCardType.SMALL_IMAGE, "智能清洁套装", "HomeBot", listOf("家居", "清洁", "新品"), 40, 7),
            ad(19, AdChannel.COMMERCE, AdCardType.VIDEO, "学生党数码精选", "PixelBox", listOf("数码", "学生", "性价比"), 55, 12),
            ad(20, AdChannel.COMMERCE, AdCardType.LARGE_IMAGE, "轻奢香氛礼盒", "AromaBox", listOf("礼盒", "香氛", "节日"), 38, 7),
            ad(21, AdChannel.COMMERCE, AdCardType.SMALL_IMAGE, "厨房小家电组合", "CookMate", listOf("厨房", "组合", "省心"), 35, 6),
            ad(22, AdChannel.COMMERCE, AdCardType.VIDEO, "户外鞋服限时购", "TrailWear", listOf("户外", "限时", "鞋服"), 49, 10),
            ad(23, AdChannel.COMMERCE, AdCardType.LARGE_IMAGE, "宠物智能喂养器", "PawPlus", listOf("宠物", "智能", "日常"), 32, 5),
            ad(24, AdChannel.COMMERCE, AdCardType.SMALL_IMAGE, "通勤降噪耳机", "QuietPods", listOf("耳机", "通勤", "降噪"), 57, 13),
            ad(25, AdChannel.COMMERCE, AdCardType.VIDEO, "便携筋膜放松仪", "FlexGo", listOf("运动", "放松", "便携"), 41, 8),
            ad(26, AdChannel.COMMERCE, AdCardType.LARGE_IMAGE, "夏季冰感床品", "CoolNest", listOf("床品", "夏季", "家居"), 44, 9),
            ad(27, AdChannel.COMMERCE, AdCardType.SMALL_IMAGE, "新手咖啡器具套装", "BeanCraft", listOf("咖啡", "器具", "入门"), 39, 7),
            ad(28, AdChannel.COMMERCE, AdCardType.VIDEO, "儿童学习平板", "KidTab", listOf("教育", "儿童", "数码"), 48, 10),
            ad(29, AdChannel.COMMERCE, AdCardType.LARGE_IMAGE, "轻量双肩通勤包", "MetroBag", listOf("通勤", "背包", "轻便"), 36, 6),
            ad(30, AdChannel.COMMERCE, AdCardType.SMALL_IMAGE, "桌面绿植订阅", "LeafDesk", listOf("绿植", "办公", "订阅"), 29, 4),
            ad(31, AdChannel.COMMERCE, AdCardType.VIDEO, "运动水杯限量色", "HydroFit", listOf("运动", "水杯", "新品"), 43, 8),
            ad(32, AdChannel.COMMERCE, AdCardType.LARGE_IMAGE, "居家投影升级包", "CineHome", listOf("影音", "家居", "沉浸"), 51, 11),
            ad(33, AdChannel.LOCAL, AdCardType.LARGE_IMAGE, "附近新开轻食店", "GreenBite", listOf("本地", "餐饮", "午餐"), 36, 6),
            ad(34, AdChannel.LOCAL, AdCardType.SMALL_IMAGE, "城市艺术展早鸟票", "ArtLoop", listOf("展览", "周末", "早鸟"), 42, 8),
            ad(35, AdChannel.LOCAL, AdCardType.VIDEO, "社区健身体验课", "FitBlock", listOf("健身", "附近", "体验"), 47, 10),
            ad(36, AdChannel.LOCAL, AdCardType.LARGE_IMAGE, "亲子手作工作坊", "HandyKid", listOf("亲子", "手作", "周末"), 31, 5),
            ad(37, AdChannel.LOCAL, AdCardType.SMALL_IMAGE, "街区咖啡地图", "BeanWalk", listOf("咖啡", "街区", "探店"), 45, 9),
            ad(38, AdChannel.LOCAL, AdCardType.VIDEO, "夜间市集攻略", "NightBazaar", listOf("市集", "夜生活", "本地"), 53, 12),
            ad(39, AdChannel.LOCAL, AdCardType.LARGE_IMAGE, "城市徒步路线", "WalkMap", listOf("徒步", "城市", "路线"), 34, 6),
            ad(40, AdChannel.LOCAL, AdCardType.SMALL_IMAGE, "周末音乐小现场", "LiveCorner", listOf("音乐", "周末", "本地"), 49, 11),
            ad(41, AdChannel.LOCAL, AdCardType.VIDEO, "社区旧物交换日", "SwapDay", listOf("社区", "环保", "交换"), 28, 4),
            ad(42, AdChannel.LOCAL, AdCardType.LARGE_IMAGE, "附近火锅双人套餐", "HotPotGo", listOf("火锅", "餐饮", "本地"), 56, 13),
            ad(43, AdChannel.LOCAL, AdCardType.SMALL_IMAGE, "书店夜读活动", "PageLight", listOf("书店", "夜读", "文化"), 37, 7),
            ad(44, AdChannel.LOCAL, AdCardType.VIDEO, "城市骑行打卡线", "RideMap", listOf("骑行", "打卡", "城市"), 46, 10),
            ad(45, AdChannel.LOCAL, AdCardType.LARGE_IMAGE, "社区烘焙体验课", "BakeRoom", listOf("烘焙", "体验", "附近"), 33, 5),
            ad(46, AdChannel.LOCAL, AdCardType.SMALL_IMAGE, "独立设计师快闪店", "DesignPop", listOf("快闪", "设计", "本地"), 40, 8),
            ad(47, AdChannel.LOCAL, AdCardType.VIDEO, "周边露营地预约", "CampNear", listOf("露营", "周边", "周末"), 52, 12),
            ad(48, AdChannel.LOCAL, AdCardType.LARGE_IMAGE, "宠物友好咖啡馆", "PawCafe", listOf("宠物", "咖啡", "本地"), 43, 9),
            ad(49, AdChannel.LOCAL, AdCardType.SMALL_IMAGE, "家庭摄影限时约拍", "FrameDay", listOf("摄影", "家庭", "限时"), 35, 6),
            ad(50, AdChannel.LOCAL, AdCardType.VIDEO, "城市艺术夜游路线", "NightArt", listOf("艺术", "夜游", "路线"), 48, 10)
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
            videoUrl = if (type == AdCardType.VIDEO) sampleVideoUrl(id) else null,
            summary = "AI 摘要：$brand 适合关注${tags.joinToString("、")}的用户，卖点清晰，适合信息流快速决策。",
            detail = "详情页展示更完整的图文或视频广告内容，并与信息流共享点赞、收藏、分享、点击和曝光状态。推荐人群：${tags.joinToString("、")}。",
            tags = tags,
            mediaColor = palette[((id - 1) + channel.ordinal).toInt() % palette.size],
            likes = likes,
            shares = shares
        )
    }

    private fun sampleVideoUrl(id: Long): String? {
        val urls = sampleVideoUrls
        if (urls.isEmpty()) return null
        return urls[((id - 1) % urls.size).toInt()]
    }

    private fun buildLocalAdAnalysis(ad: AdItem): String {
        val scenarios = ad.tags.joinToString("、").ifBlank { ad.channel.title }
        return "${ad.title} 是 ${ad.brand} 带来的一个围绕${scenarios}展开的产品或服务。\n\n" +
            "它主要想解决的是用户在相关场景里的选择和体验问题：${ad.summary}\n\n" +
            "从当前信息看，它的亮点在于把${scenarios}相关需求集中到一个更清晰、方便理解的使用场景里。"
    }
}
