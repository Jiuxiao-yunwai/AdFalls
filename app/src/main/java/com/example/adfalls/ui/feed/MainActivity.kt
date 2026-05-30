package com.example.adfalls.ui.feed

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Parcelable
import android.text.Editable
import android.text.TextWatcher
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
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var tabs: List<TextView>
    private lateinit var recyclerView: RecyclerView
    private lateinit var layoutManager: LinearLayoutManager
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var adapter: AdAdapter
    private lateinit var viewModel: FeedViewModel
    private val listStates = mutableMapOf<AdChannel, Parcelable?>()
    private var pendingListCommitChannel: AdChannel? = null
    private var pendingListCommit: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
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
        swipeRefresh.setColorSchemeColors(Color.WHITE, Color.rgb(78, 164, 255))
        swipeRefresh.setProgressBackgroundColorSchemeColor(Color.rgb(28, 28, 28))
        swipeRefresh.setOnRefreshListener {
            viewModel.refresh()
            swipeRefresh.isRefreshing = false
            recyclerView.scrollToPosition(0)
        }

        findViewById<TextView>(R.id.search_input).addTextChangedListener(object : TextWatcher {
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
        if (::layoutManager.isInitialized) {
            listStates[viewModel.uiState.value.activeChannel] = layoutManager.onSaveInstanceState()
        }
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
        tabs.forEachIndexed { index, tab ->
            val selected = AdChannel.entries[index] == activeChannel
            tab.setTextColor(if (selected) Color.BLACK else Color.rgb(210, 210, 210))
            tab.setBackgroundResource(
                if (selected) R.drawable.bg_tab_selected else R.drawable.bg_tab_unselected
            )
        }
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
}
