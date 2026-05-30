package com.example.adfalls.ui.feed

import android.content.Intent
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.os.Parcelable
import android.text.Editable
import android.text.TextWatcher
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
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
import com.example.adfalls.ui.detail.DetailActivity
import com.example.adfalls.viewmodel.FeedUiState
import com.example.adfalls.viewmodel.FeedViewModel
import kotlin.math.abs
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var tabs: List<TextView>
    private lateinit var tabIndicator: View
    private lateinit var searchPanel: View
    private lateinit var searchInput: EditText
    private lateinit var recyclerView: RecyclerView
    private lateinit var layoutManager: LinearLayoutManager
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var adapter: AdAdapter
    private lateinit var viewModel: FeedViewModel
    private lateinit var swipeDetector: GestureDetector
    private val listStates = mutableMapOf<AdChannel, Parcelable?>()
    private var pendingListCommitChannel: AdChannel? = null
    private var pendingListCommit: (() -> Unit)? = null
    private var currentTabIndex = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(244, 246, 248)
        window.navigationBarColor = Color.rgb(244, 246, 248)
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        setContentView(R.layout.activity_main)
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
        searchPanel = findViewById(R.id.search_panel)
        searchInput = findViewById(R.id.search_input)
        findViewById<View>(R.id.search_button).setOnClickListener { toggleSearchPanel() }

        layoutManager = LinearLayoutManager(this)
        adapter = AdAdapter(
            onCardClick = { ad ->
                viewModel.registerClick(ad.id)
                startActivity(Intent(this, DetailActivity::class.java).putExtra(DetailActivity.EXTRA_AD_ID, ad.id))
            },
            onLikeClick = { ad -> viewModel.toggleLike(ad.id) },
            onFavoriteClick = { ad -> viewModel.toggleFavorite(ad.id) },
            onShareClick = { ad -> viewModel.share(ad.id) },
            onVideoClick = { ad -> viewModel.toggleVideoPlay(ad.id) },
            onMuteClick = { ad -> viewModel.toggleMute(ad.id) }
        )
        recyclerView = findViewById(R.id.ad_list)
        recyclerView.layoutManager = layoutManager
        recyclerView.adapter = adapter
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
                val state = viewModel.uiState.value
                val lastVisible = layoutManager.findLastVisibleItemPosition()
                if (!state.loadingMore &&
                    !state.endReached &&
                    state.searchText.isBlank() &&
                    dy > 0 &&
                    lastVisible >= adapter.itemCount - 2
                ) {
                    viewModel.loadMore()
                }
            }
        })

        swipeRefresh = findViewById(R.id.swipe_refresh)
        swipeRefresh.setColorSchemeColors(Color.rgb(78, 164, 255), Color.rgb(31, 122, 104))
        swipeRefresh.setProgressBackgroundColorSchemeColor(Color.WHITE)
        swipeRefresh.setOnRefreshListener {
            viewModel.refresh()
            swipeRefresh.isRefreshing = false
            recyclerView.scrollToPosition(0)
        }

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.updateSearchText(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })

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

    private fun selectTab(channel: AdChannel, restorePosition: Boolean = true) {
        val currentChannel = viewModel.uiState.value.activeChannel
        if (restorePosition && channel == currentChannel) return
        if (::layoutManager.isInitialized) {
            listStates[currentChannel] = layoutManager.onSaveInstanceState()
        }
        animateListSwitch(AdChannel.entries.indexOf(channel) - AdChannel.entries.indexOf(currentChannel))
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

    private fun updateTabs(activeChannel: AdChannel) {
        val activeIndex = AdChannel.entries.indexOf(activeChannel)
        tabs.forEachIndexed { index, tab ->
            tab.setTextColor(if (index == activeIndex) Color.rgb(17, 17, 17) else Color.rgb(118, 118, 118))
            tab.setBackgroundColor(Color.TRANSPARENT)
        }
        moveTabIndicator(activeIndex)
    }

    private fun submitAds(state: FeedUiState) {
        adapter.submitAds(state.ads, state.endReached) {
            if (pendingListCommitChannel == null || pendingListCommitChannel == state.activeChannel) {
                pendingListCommit?.invoke()
                pendingListCommit = null
                pendingListCommitChannel = null
            }
            registerVisibleImpressions()
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
    }

    private fun moveTabIndicator(activeIndex: Int) {
        if (activeIndex < 0 || tabs.isEmpty()) return
        val tabWidth = tabs.first().width
        if (tabWidth == 0) {
            tabs.first().post { moveTabIndicator(activeIndex) }
            return
        }

        val layoutParams = tabIndicator.layoutParams
        val indicatorWidth = (tabWidth - 28).coerceAtLeast(0)
        if (layoutParams.width != indicatorWidth) {
            layoutParams.width = indicatorWidth
            tabIndicator.layoutParams = layoutParams
        }

        val target = activeIndex * tabWidth.toFloat()
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

    private fun animateListSwitch(direction: Int) {
        if (direction == 0 || !::recyclerView.isInitialized) return
        recyclerView.animate().cancel()
        recyclerView.translationX = 28f * direction
        recyclerView.alpha = 0.88f
        recyclerView.animate()
            .translationX(0f)
            .alpha(1f)
            .setDuration(180L)
            .start()
    }

    private fun toggleSearchPanel() {
        val shouldShow = searchPanel.visibility != View.VISIBLE
        searchPanel.visibility = if (shouldShow) View.VISIBLE else View.GONE
        if (shouldShow) {
            searchInput.requestFocus()
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .showSoftInput(searchInput, InputMethodManager.SHOW_IMPLICIT)
        } else {
            searchInput.text?.clear()
            searchInput.clearFocus()
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .hideSoftInputFromWindow(searchInput.windowToken, 0)
        }
    }

    companion object {
        private const val SWIPE_DISTANCE = 90
        private const val SWIPE_VELOCITY = 120
    }
}
