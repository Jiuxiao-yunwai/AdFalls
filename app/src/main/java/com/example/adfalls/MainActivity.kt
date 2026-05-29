package com.example.adfalls

import android.content.Intent
import android.graphics.Color
import android.app.Activity
import android.os.Bundle
import android.os.Parcelable
import android.text.Editable
import android.text.TextWatcher
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

class MainActivity : Activity() {
    private lateinit var tabs: List<TextView>
    private lateinit var recyclerView: RecyclerView
    private lateinit var layoutManager: LinearLayoutManager
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var adapter: AdAdapter
    private var activeChannel = AdChannel.FEATURED
    private var searchText = ""
    private var loadingMore = false
    private val listStates = mutableMapOf<AdChannel, Parcelable?>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        setContentView(R.layout.activity_main)

        tabs = listOf(
            findViewById(R.id.tab_featured),
            findViewById(R.id.tab_commerce),
            findViewById(R.id.tab_local)
        )
        tabs.forEachIndexed { index, tab ->
            tab.setOnClickListener { selectTab(index) }
        }

        layoutManager = LinearLayoutManager(this)
        adapter = AdAdapter(
            onCardClick = { ad ->
                AdRepository.registerClick(ad.id)
                refreshList()
                startActivity(Intent(this, DetailActivity::class.java).putExtra(DetailActivity.EXTRA_AD_ID, ad.id))
            },
            onLikeClick = { ad -> AdRepository.toggleLike(ad.id); refreshList() },
            onFavoriteClick = { ad -> AdRepository.toggleFavorite(ad.id); refreshList() },
            onShareClick = { ad -> AdRepository.share(ad.id); refreshList() },
            onVideoClick = { ad -> VideoPlaybackPool.togglePlay(ad.id); refreshList() },
            onMuteClick = { ad -> VideoPlaybackPool.toggleMute(ad.id); refreshList() }
        )
        recyclerView = findViewById(R.id.ad_list)
        recyclerView.layoutManager = layoutManager
        recyclerView.adapter = adapter
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                registerVisibleImpressions()
                val lastVisible = layoutManager.findLastVisibleItemPosition()
                if (!loadingMore && searchText.isBlank() && dy > 0 && lastVisible >= adapter.itemCount - 2) {
                    loadingMore = true
                    AdRepository.loadMore(activeChannel)
                    refreshList { loadingMore = false }
                }
            }
        })

        swipeRefresh = findViewById(R.id.swipe_refresh)
        swipeRefresh.setColorSchemeColors(Color.WHITE, Color.rgb(78, 164, 255))
        swipeRefresh.setProgressBackgroundColorSchemeColor(Color.rgb(28, 28, 28))
        swipeRefresh.setOnRefreshListener {
            AdRepository.refresh(activeChannel)
            swipeRefresh.isRefreshing = false
            refreshList()
            recyclerView.scrollToPosition(0)
        }

        findViewById<TextView>(R.id.search_input).addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchText = s?.toString().orEmpty()
                refreshList()
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })

        selectTab(0, restorePosition = false)
    }

    override fun onResume() {
        super.onResume()
        if (::adapter.isInitialized) refreshList()
    }

    private fun selectTab(selectedIndex: Int, restorePosition: Boolean = true) {
        if (::layoutManager.isInitialized) {
            listStates[activeChannel] = layoutManager.onSaveInstanceState()
        }
        activeChannel = AdChannel.entries[selectedIndex]
        tabs.forEachIndexed { index, tab ->
            val selected = index == selectedIndex
            tab.setTextColor(if (selected) Color.BLACK else Color.rgb(210, 210, 210))
            tab.setBackgroundResource(
                if (selected) R.drawable.bg_tab_selected else R.drawable.bg_tab_unselected
            )
        }
        refreshList {
            if (restorePosition) {
                listStates[activeChannel]?.let(layoutManager::onRestoreInstanceState) ?: recyclerView.scrollToPosition(0)
            } else {
                recyclerView.scrollToPosition(0)
            }
            registerVisibleImpressions()
        }
    }

    private fun refreshList(onCommitted: (() -> Unit)? = null) {
        val data = AdRepository.search(activeChannel, searchText)
        adapter.submitList(data) {
            onCommitted?.invoke()
            registerVisibleImpressions()
        }
    }

    private fun registerVisibleImpressions() {
        if (adapter.itemCount == 0) return
        val first = layoutManager.findFirstVisibleItemPosition().coerceAtLeast(0)
        val last = layoutManager.findLastVisibleItemPosition().coerceAtMost(adapter.itemCount - 1)
        if (last < first) return
        var changed = false
        for (position in first..last) {
            adapter.currentList.getOrNull(position)?.let {
                changed = AdRepository.registerImpression(it.id) || changed
            }
        }
        if (changed) adapter.submitList(AdRepository.search(activeChannel, searchText))
    }
}
