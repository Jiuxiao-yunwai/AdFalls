package com.example.adfalls.ui.feed

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Parcelable
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.adfalls.R
import com.example.adfalls.data.model.AdChannel
import com.example.adfalls.data.model.AdCardType
import com.example.adfalls.data.model.AdItem
import com.example.adfalls.ui.aichat.AiChatActivity
import com.example.adfalls.ui.common.applyResponsiveHorizontalPadding
import com.example.adfalls.ui.detail.DetailActivity
import com.example.adfalls.ui.search.SearchActivity
import com.example.adfalls.viewmodel.FeedUiState
import com.example.adfalls.viewmodel.FeedViewModel
import kotlin.math.abs
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
    private lateinit var swipeDetector: GestureDetector
    private val listStates = mutableMapOf<AdChannel, Parcelable?>()
    private var pendingListCommitChannel: AdChannel? = null
    private var pendingListCommit: (() -> Unit)? = null
    private var currentTabIndex = -1
    private var pendingSwitchDirection = 0
    private var outgoingSnapshotReady = false
    private var scheduledFeedVideoId: Long? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = getColor(R.color.app_bg)
        window.navigationBarColor = getColor(R.color.app_bg)
        setContentView(R.layout.activity_main)
        findViewById<View>(R.id.main_root).applyResponsiveHorizontalPadding()
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
        findViewById<View>(R.id.tag_filter_clear).setOnClickListener { viewModel.clearTag() }

        adapter = createAdapter()
        outgoingAdapter = createAdapter()
        layoutManager = LinearLayoutManager(this)
        outgoingLayoutManager = LinearLayoutManager(this)

        outgoingRecyclerView = findViewById(R.id.ad_list_outgoing)
        outgoingRecyclerView.layoutManager = outgoingLayoutManager
        outgoingRecyclerView.adapter = outgoingAdapter
        (outgoingRecyclerView.itemAnimator as? DefaultItemAnimator)?.supportsChangeAnimations = false
        outgoingRecyclerView.isEnabled = false

        recyclerView = findViewById(R.id.ad_list)
        recyclerView.layoutManager = layoutManager
        recyclerView.adapter = adapter
        (recyclerView.itemAnimator as? DefaultItemAnimator)?.supportsChangeAnimations = false
        swipeDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                val start = e1 ?: return false
                val dx = e2.x - start.x
                val dy = e2.y - start.y
                if (abs(dx) < SWIPE_DISTANCE || abs(dx) < abs(dy) * 1.25f || abs(velocityX) < SWIPE_VELOCITY) {
                    return false
                }
                val currentIndex = AdChannel.entries.indexOf(viewModel.uiState.value.activeChannel)
                val nextIndex = if (dx < 0) currentIndex + 1 else currentIndex - 1
                if (nextIndex !in AdChannel.entries.indices) return false
                selectTab(AdChannel.entries[nextIndex])
                return true
            }
        })
        recyclerView.setOnTouchListener { _, event ->
            swipeDetector.onTouchEvent(event)
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
            viewModel.refresh()
            swipeRefresh.isRefreshing = false
            recyclerView.scrollToPosition(0)
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
        if (::layoutManager.isInitialized) {
            listStates[currentChannel] = layoutManager.onSaveInstanceState()
        }
        pendingSwitchDirection = AdChannel.entries.indexOf(channel) - AdChannel.entries.indexOf(currentChannel)
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
            registerVisibleImpressions()
            recyclerView.post { scheduleFeedVideoAutoplay() }
        }
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
            tagFilterText.text = "正在查看 #$tag"
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
        outgoingAdapter.submitAds(viewModel.uiState.value.ads, footerTextFor(viewModel.uiState.value)) {
            outgoingLayoutManager.onRestoreInstanceState(layoutManager.onSaveInstanceState())
            outgoingSnapshotReady = true
            outgoingRecyclerView.visibility = View.VISIBLE
            onReady()
        }
        recyclerView.alpha = 1f
    }

    private fun animatePageSwitch(direction: Int) {
        if (direction == 0 || !::recyclerView.isInitialized || recyclerView.width == 0) return
        recyclerView.animate().cancel()
        outgoingRecyclerView.animate().cancel()
        if (!outgoingSnapshotReady) return
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
                outgoingSnapshotReady = false
                outgoingAdapter.submitAds(emptyList(), footerText = null)
            }
            .start()
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

    companion object {
        private const val SWIPE_DISTANCE = 90
        private const val SWIPE_VELOCITY = 120
        private const val PAGE_SWITCH_DURATION = 320L
        private const val PAGE_SWITCH_GAP_DP = 10
    }
}
