package com.example.adfalls.ui.feed

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Parcelable
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.adfalls.R
import com.example.adfalls.data.model.AdChannel
import com.example.adfalls.data.model.AdCardType
import com.example.adfalls.data.model.AdItem
import com.example.adfalls.ui.aichat.AiChatActivity
import com.example.adfalls.ui.detail.DetailActivity
import com.example.adfalls.ui.search.SearchActivity
import com.example.adfalls.viewmodel.FeedUiState
import com.example.adfalls.viewmodel.FeedViewModel
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var tabs: List<TextView>
    private lateinit var tabIndicator: View
    private lateinit var recyclerView: RecyclerView
    private lateinit var outgoingRecyclerView: RecyclerView
    private lateinit var layoutManager: LinearLayoutManager
    private lateinit var outgoingLayoutManager: LinearLayoutManager
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var tagFilterBar: View
    private lateinit var tagFilterText: TextView
    private lateinit var emptyState: TextView
    private lateinit var adapter: AdAdapter
    private lateinit var outgoingAdapter: AdAdapter
    private lateinit var viewModel: FeedViewModel
    private val listStates = mutableMapOf<AdChannel, Parcelable?>()
    private var pendingListCommitChannel: AdChannel? = null
    private var pendingListCommit: (() -> Unit)? = null
    private var currentTabIndex = -1
    private var pendingSwitchDirection = 0
    private var outgoingSnapshotReady = false
    private var scheduledFeedVideoId: Long? = null
    private var pendingRefreshScrollChannel: AdChannel? = null
    private var handledRefreshVersion = 0
    private var pageSwitchInProgress = false
    private var refreshFadePending = false
    private var nextPageSwitchAllowedAt = 0L
    private var touchDownX = 0f
    private var touchDownY = 0f
    private var horizontalSwipeActive = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = getColor(R.color.app_bg)
        window.navigationBarColor = getColor(R.color.app_bg)
        setContentView(R.layout.activity_main)
        findViewById<View>(R.id.main_root).applyMainResponsiveHorizontalPadding()
        viewModel = ViewModelProvider.create(this)[FeedViewModel::class]

        tabs = listOf(
            findViewById(R.id.tab_featured),
            findViewById(R.id.tab_commerce),
            findViewById(R.id.tab_local)
        )
        tabs.forEachIndexed { index, tab ->
            tab.setOnClickListener { selectTab(AdChannel.entries[index]) }
        }
        tabIndicator = findViewById(R.id.tab_indicator)
        findViewById<View>(R.id.ai_chat_button).setOnClickListener { openAiChatPage() }
        findViewById<View>(R.id.search_button).setOnClickListener { openSearchPage() }
        tagFilterBar = findViewById(R.id.tag_filter_bar)
        tagFilterText = findViewById(R.id.tag_filter_text)
        emptyState = findViewById(R.id.feed_empty_state)
        tagFilterBar.setOnClickListener { viewModel.clearTag() }
        tagFilterText.setOnClickListener { viewModel.clearTag() }

        adapter = createAdapter()
        outgoingAdapter = createAdapter()
        layoutManager = LinearLayoutManager(this)
        outgoingLayoutManager = LinearLayoutManager(this)

        outgoingRecyclerView = findViewById(R.id.ad_list_outgoing)
        outgoingRecyclerView.layoutManager = outgoingLayoutManager
        outgoingRecyclerView.adapter = outgoingAdapter
        configureRecyclerViewForFeed(outgoingRecyclerView)
        outgoingRecyclerView.isEnabled = false

        recyclerView = findViewById(R.id.ad_list)
        recyclerView.layoutManager = layoutManager
        recyclerView.adapter = adapter
        configureRecyclerViewForFeed(recyclerView)
        recyclerView.setOnTouchListener { view, event ->
            handleFeedSwipeTouch(view, event)
            false
        }
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                registerVisibleImpressions()
                scheduleFeedVideoAutoplay()
                val state = viewModel.uiState.value
                val lastVisible = layoutManager.findLastVisibleItemPosition()
                if (!state.loadingMore &&
                    !state.endReached &&
                    state.searchText.isBlank() &&
                    state.selectedTag == null &&
                    dy > 0 &&
                    lastVisible >= adapter.itemCount - 2
                ) {
                    viewModel.loadMore()
                }
            }
        })

        swipeRefresh = findViewById(R.id.swipe_refresh)
        swipeRefresh.setColorSchemeColors(getColor(R.color.app_text_on_dark_primary), getColor(R.color.app_refresh_blue))
        swipeRefresh.setProgressBackgroundColorSchemeColor(getColor(R.color.app_surface_dark_pressed))
        swipeRefresh.setOnRefreshListener {
            val channel = viewModel.uiState.value.activeChannel
            pendingRefreshScrollChannel = channel
            listStates.remove(channel)
            recyclerView.stopScroll()
            scheduledFeedVideoId = null
            startRefreshFadeOut()
            viewModel.refresh(channel)
            swipeRefresh.isRefreshing = false
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    updateTabs(state.activeChannel)
                    submitAds(state)
                }
            }
        }

        selectTab(AdChannel.FEATURED, restorePosition = false)
    }

    override fun onResume() {
        super.onResume()
        if (::recyclerView.isInitialized) {
            recyclerView.post { scheduleFeedVideoAutoplay() }
        }
    }

    override fun onPause() {
        pauseScheduledFeedVideo()
        super.onPause()
    }

    private fun createAdapter(): AdAdapter {
        return AdAdapter(
            onCardClick = { ad ->
                viewModel.registerClick(ad.id)
                startActivity(Intent(this, DetailActivity::class.java).putExtra(DetailActivity.EXTRA_AD_ID, ad.id))
            },
            onLikeClick = { ad -> viewModel.toggleLike(ad.id) },
            onFavoriteClick = { ad -> viewModel.toggleFavorite(ad.id) },
            onShareClick = { ad -> viewModel.share(ad.id) },
            onVideoClick = { ad -> viewModel.toggleVideoPlay(ad) },
            onMuteClick = { ad -> viewModel.toggleMute(ad.id) },
            onTagClick = { tag -> viewModel.selectTag(tag) }
        )
    }

    private fun selectTab(channel: AdChannel, restorePosition: Boolean = true) {
        val currentChannel = viewModel.uiState.value.activeChannel
        if (restorePosition && channel == currentChannel) return
        if (pageSwitchInProgress) return
        val now = SystemClock.elapsedRealtime()
        if (restorePosition && now < nextPageSwitchAllowedAt) return
        if (::layoutManager.isInitialized) {
            listStates[currentChannel] = layoutManager.onSaveInstanceState()
        }
        pendingSwitchDirection = AdChannel.entries.indexOf(channel) - AdChannel.entries.indexOf(currentChannel)
        pageSwitchInProgress = pendingSwitchDirection != 0
        prepareOutgoingList(pendingSwitchDirection) {
            recyclerView.translationX = offscreenOffset(pendingSwitchDirection)
            scheduledFeedVideoId = null
            viewModel.selectChannel(channel)
            updateTabs(channel)
            pendingListCommitChannel = channel
            pendingListCommit = {
                if (restorePosition) {
                    listStates[channel]?.let(layoutManager::onRestoreInstanceState) ?: recyclerView.scrollToPosition(0)
                } else {
                    recyclerView.scrollToPosition(0)
                }
                registerVisibleImpressions()
            }
        }
    }

    private fun updateTabs(activeChannel: AdChannel) {
        val activeIndex = AdChannel.entries.indexOf(activeChannel)
        tabs.forEachIndexed { index, tab ->
            tab.setTextColor(
                if (index == activeIndex) {
                    getColor(R.color.app_text_on_dark_primary)
                } else {
                    getColor(R.color.app_text_on_dark_muted)
                }
            )
            tab.setBackgroundColor(Color.TRANSPARENT)
        }
        moveTabIndicator(activeIndex)
    }

    private fun submitAds(state: FeedUiState) {
        updateFilterAndEmptyState(state)
        adapter.submitAds(state.ads, footerTextFor(state)) {
            if (pendingListCommitChannel == null || pendingListCommitChannel == state.activeChannel) {
                pendingListCommit?.invoke()
                animatePageSwitch(pendingSwitchDirection)
                pendingSwitchDirection = 0
                pendingListCommit = null
                pendingListCommitChannel = null
            }
            scrollToTopAfterRefreshIfNeeded(state)
            registerVisibleImpressions()
            recyclerView.post { scheduleFeedVideoAutoplay() }
        }
    }

    private fun configureRecyclerViewForFeed(target: RecyclerView) {
        target.setHasFixedSize(true)
        target.setItemViewCacheSize(FEED_ITEM_CACHE_SIZE)
        target.itemAnimator = null
    }

    private fun handleFeedSwipeTouch(view: View, event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchDownX = event.x
                touchDownY = event.y
                horizontalSwipeActive = false
                swipeRefresh.isEnabled = true
            }

            MotionEvent.ACTION_MOVE -> {
                if (horizontalSwipeActive || isHorizontalSwipeIntent(event)) {
                    horizontalSwipeActive = true
                    swipeRefresh.isEnabled = false
                    view.parent.requestDisallowInterceptTouchEvent(true)
                }
            }

            MotionEvent.ACTION_UP -> {
                if (horizontalSwipeActive) {
                    switchChannelForSwipe(event.x - touchDownX)
                }
                horizontalSwipeActive = false
                swipeRefresh.isEnabled = true
                view.parent.requestDisallowInterceptTouchEvent(false)
            }

            MotionEvent.ACTION_CANCEL -> {
                horizontalSwipeActive = false
                swipeRefresh.isEnabled = true
                view.parent.requestDisallowInterceptTouchEvent(false)
            }
        }
    }

    private fun isHorizontalSwipeIntent(event: MotionEvent): Boolean {
        val dx = event.x - touchDownX
        val dy = event.y - touchDownY
        return abs(dx) >= SWIPE_DISTANCE * SWIPE_INTENT_DISTANCE_RATIO &&
            abs(dx) > abs(dy) * SWIPE_DIRECTION_RATIO
    }

    private fun switchChannelForSwipe(dx: Float) {
        if (abs(dx) < SWIPE_DISTANCE) return
        val currentIndex = AdChannel.entries.indexOf(viewModel.uiState.value.activeChannel)
        val nextIndex = if (dx < 0) currentIndex + 1 else currentIndex - 1
        if (nextIndex !in AdChannel.entries.indices) return
        selectTab(AdChannel.entries[nextIndex])
    }

    private fun scrollToTopAfterRefreshIfNeeded(state: FeedUiState) {
        if (state.refreshVersion <= handledRefreshVersion) return
        handledRefreshVersion = state.refreshVersion
        val refreshChannel = pendingRefreshScrollChannel
        pendingRefreshScrollChannel = null
        if (refreshChannel != state.activeChannel) {
            resetRefreshFade()
            return
        }

        listStates.remove(state.activeChannel)
        recyclerView.stopScroll()
        layoutManager.scrollToPositionWithOffset(0, 0)
        scheduledFeedVideoId = null
        playRefreshFadeIn()
    }

    private fun startRefreshFadeOut() {
        refreshFadePending = true
        recyclerView.animate().cancel()
        recyclerView.animate()
            .alpha(REFRESH_FADE_OUT_ALPHA)
            .setDuration(REFRESH_FADE_OUT_DURATION)
            .start()
    }

    private fun playRefreshFadeIn() {
        if (!refreshFadePending) return
        refreshFadePending = false
        recyclerView.animate().cancel()
        recyclerView.alpha = 0f
        recyclerView.animate()
            .alpha(1f)
            .setDuration(REFRESH_FADE_IN_DURATION)
            .start()
    }

    private fun resetRefreshFade() {
        refreshFadePending = false
        recyclerView.animate().cancel()
        recyclerView.alpha = 1f
    }

    private fun footerTextFor(state: FeedUiState): String? {
        return when {
            state.loadingMore -> "加载中..."
            state.endReached -> "到底了"
            else -> null
        }
    }

    private fun updateFilterAndEmptyState(state: FeedUiState) {
        val tag = state.selectedTag
        tagFilterBar.visibility = if (tag == null) View.GONE else View.VISIBLE
        if (tag != null) {
            tagFilterText.text = "已按标签筛选：#$tag · 点击清除"
        }
        val filtering = state.searchText.isNotBlank() || tag != null
        emptyState.visibility = if (filtering && state.ads.isEmpty()) View.VISIBLE else View.GONE
        emptyState.text = if (tag != null) {
            "没有找到 #$tag 相关广告"
        } else {
            "没有找到匹配的广告"
        }
    }

    private fun registerVisibleImpressions() {
        if (adapter.itemCount == 0) return
        val first = layoutManager.findFirstVisibleItemPosition().coerceAtLeast(0)
        val last = layoutManager.findLastVisibleItemPosition().coerceAtMost(adapter.itemCount - 1)
        if (last < first) return
        val visibleAdIds = mutableListOf<Long>()
        for (position in first..last) {
            adapter.currentList.getOrNull(position)?.let {
                visibleAdIds.add(it.id)
            }
        }
        viewModel.registerImpressions(visibleAdIds)
        viewModel.pauseVideosOutside(visibleAdIds)
    }

    private fun scheduleFeedVideoAutoplay() {
        val candidate = findFirstFullyVisibleVideo()
        if (candidate != null) {
            val (ad, holder) = candidate
            val playerView = holder.getPlayerView() ?: return
            if (scheduledFeedVideoId != ad.id || !ad.playing) {
                viewModel.autoPlayVisibleVideo(ad, playerView)
            }
            scheduledFeedVideoId = ad.id
            return
        }

        val playingId = scheduledFeedVideoId ?: return
        if (isAdFullyGone(playingId)) {
            viewModel.pauseVideoIfGone(playingId)
            scheduledFeedVideoId = null
        }
    }

    private fun pauseScheduledFeedVideo() {
        scheduledFeedVideoId?.let(viewModel::pauseVideoIfGone)
        scheduledFeedVideoId = null
    }

    private fun findFirstFullyVisibleVideo(): Pair<AdItem, AdAdapter.AdViewHolder>? {
        if (adapter.itemCount == 0) return null
        val first = layoutManager.findFirstVisibleItemPosition().coerceAtLeast(0)
        val last = layoutManager.findLastVisibleItemPosition().coerceAtMost(adapter.itemCount - 1)
        if (last < first) return null

        for (position in first..last) {
            val ad = adapter.getAdAtAdapterPosition(position) ?: continue
            if (ad.type != AdCardType.VIDEO) continue
            if (!isItemFullyVisible(position)) continue
            val holder = recyclerView.findViewHolderForAdapterPosition(position) as? AdAdapter.AdViewHolder
            if (holder?.getBoundAd()?.id == ad.id) return ad to holder
        }
        return null
    }

    private fun isItemFullyVisible(position: Int): Boolean {
        val child = layoutManager.findViewByPosition(position) ?: return false
        val rvTop = recyclerView.paddingTop
        val rvBottom = recyclerView.height - recyclerView.paddingBottom
        return child.top >= rvTop && child.bottom <= rvBottom
    }

    private fun isAdFullyGone(adId: Long): Boolean {
        val position = adapter.currentList.indexOfFirst { it.id == adId }
        if (position == -1) return true
        val child = layoutManager.findViewByPosition(position) ?: return true
        return child.bottom <= recyclerView.paddingTop ||
            child.top >= recyclerView.height - recyclerView.paddingBottom
    }

    private fun moveTabIndicator(activeIndex: Int) {
        if (activeIndex < 0 || tabs.isEmpty()) return
        val activeTab = tabs[activeIndex]
        if (activeTab.width == 0) {
            tabs.first().post { moveTabIndicator(activeIndex) }
            return
        }

        val layoutParams = tabIndicator.layoutParams
        val extraWidth = (16 * resources.displayMetrics.density).toInt()
        val textWidth = activeTab.paint.measureText(activeTab.text.toString()).toInt()
        val indicatorWidth = (textWidth + extraWidth).coerceAtMost(activeTab.width)
        if (layoutParams.width != indicatorWidth) {
            layoutParams.width = indicatorWidth
            tabIndicator.layoutParams = layoutParams
        }

        val target = activeTab.left + (activeTab.width - indicatorWidth) / 2f
        if (currentTabIndex == -1) {
            tabIndicator.translationX = target
        } else if (currentTabIndex != activeIndex) {
            tabIndicator.animate()
                .translationX(target)
                .setDuration(180L)
                .start()
        }
        currentTabIndex = activeIndex
    }

    private fun prepareOutgoingList(direction: Int, onReady: () -> Unit) {
        if (direction == 0 || !::outgoingRecyclerView.isInitialized || recyclerView.width == 0) {
            onReady()
            return
        }
        outgoingRecyclerView.animate().cancel()
        recyclerView.animate().cancel()
        outgoingRecyclerView.translationX = 0f
        outgoingRecyclerView.alpha = 1f
        outgoingRecyclerView.visibility = View.INVISIBLE
        outgoingSnapshotReady = false
        val snapshot = visibleSnapshotItems()
        outgoingAdapter.submitAds(snapshot.items, footerText = null) {
            outgoingLayoutManager.scrollToPositionWithOffset(0, snapshot.firstItemOffset)
            outgoingSnapshotReady = true
            outgoingRecyclerView.visibility = View.VISIBLE
            onReady()
        }
        recyclerView.alpha = 1f
    }

    private fun visibleSnapshotItems(): VisibleSnapshot {
        val ads = viewModel.uiState.value.ads
        if (ads.isEmpty()) return VisibleSnapshot(emptyList(), recyclerView.paddingTop)
        val first = layoutManager.findFirstVisibleItemPosition().coerceAtLeast(0)
        val last = layoutManager.findLastVisibleItemPosition().coerceAtLeast(first)
        val start = first.coerceAtMost(ads.lastIndex)
        val end = (last + SNAPSHOT_EXTRA_ITEMS).coerceAtMost(ads.lastIndex)
        val offset = layoutManager.findViewByPosition(first)?.top ?: recyclerView.paddingTop
        return VisibleSnapshot(ads.subList(start, end + 1), offset)
    }

    private fun animatePageSwitch(direction: Int) {
        if (direction == 0 || !::recyclerView.isInitialized || recyclerView.width == 0) {
            pageSwitchInProgress = false
            startPageSwitchCooldown()
            return
        }
        recyclerView.animate().cancel()
        outgoingRecyclerView.animate().cancel()
        if (!outgoingSnapshotReady) {
            pageSwitchInProgress = false
            startPageSwitchCooldown()
            return
        }
        recyclerView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        outgoingRecyclerView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        recyclerView.translationX = offscreenOffset(direction)
        recyclerView.alpha = 1f
        outgoingRecyclerView.translationX = 0f
        outgoingRecyclerView.alpha = 1f
        recyclerView.animate()
            .translationX(0f)
            .alpha(1f)
            .setDuration(PAGE_SWITCH_DURATION)
            .start()
        outgoingRecyclerView.animate()
            .translationX(-offscreenOffset(direction))
            .alpha(1f)
            .setDuration(PAGE_SWITCH_DURATION)
            .withEndAction {
                outgoingRecyclerView.visibility = View.GONE
                outgoingRecyclerView.translationX = 0f
                outgoingRecyclerView.setLayerType(View.LAYER_TYPE_NONE, null)
                recyclerView.setLayerType(View.LAYER_TYPE_NONE, null)
                outgoingSnapshotReady = false
                pageSwitchInProgress = false
                startPageSwitchCooldown()
                outgoingAdapter.submitAds(emptyList(), footerText = null)
            }
            .start()
    }

    private fun startPageSwitchCooldown() {
        nextPageSwitchAllowedAt = SystemClock.elapsedRealtime() + PAGE_SWITCH_COOLDOWN_MS
    }

    private fun offscreenOffset(direction: Int): Float {
        return (recyclerView.width + pageGapPx()) * direction.toFloat()
    }

    private fun pageGapPx(): Int {
        return (PAGE_SWITCH_GAP_DP * resources.displayMetrics.density).toInt()
    }

    private fun openSearchPage() {
        startActivity(
            Intent(this, SearchActivity::class.java)
                .putExtra(SearchActivity.EXTRA_CHANNEL, viewModel.uiState.value.activeChannel.name)
        )
    }

    private fun openAiChatPage() {
        startActivity(Intent(this, AiChatActivity::class.java))
    }

    private fun View.applyMainResponsiveHorizontalPadding() {
        val density = resources.displayMetrics.density
        val horizontal = (resources.displayMetrics.widthPixels * 0.04f)
            .roundToInt()
            .coerceIn((12f * density).roundToInt(), (22f * density).roundToInt())
        setPaddingRelative(horizontal, paddingTop, horizontal, paddingBottom)
    }

    companion object {
        private const val SWIPE_DISTANCE = 90
        private const val SWIPE_INTENT_DISTANCE_RATIO = 0.45f
        private const val SWIPE_DIRECTION_RATIO = 1.25f
        private const val PAGE_SWITCH_COOLDOWN_MS = 500L
        private const val PAGE_SWITCH_DURATION = 320L
        private const val PAGE_SWITCH_GAP_DP = 10
        private const val FEED_ITEM_CACHE_SIZE = 6
        private const val SNAPSHOT_EXTRA_ITEMS = 2
        private const val REFRESH_FADE_OUT_ALPHA = 0.35f
        private const val REFRESH_FADE_OUT_DURATION = 120L
        private const val REFRESH_FADE_IN_DURATION = 240L
    }

    private data class VisibleSnapshot(
        val items: List<AdItem>,
        val firstItemOffset: Int
    )
}
