package com.example.adfalls.ui.feed

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Parcelable
import android.text.Editable
import android.text.TextWatcher
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.adfalls.R
import com.example.adfalls.data.model.AdChannel
import com.example.adfalls.ui.detail.DetailActivity
import com.example.adfalls.viewmodel.FeedViewModel

class MainActivity : ComponentActivity() {
    private lateinit var tabs: List<TextView>
    private lateinit var recyclerView: RecyclerView
    private lateinit var layoutManager: LinearLayoutManager
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var adapter: AdAdapter
    private lateinit var viewModel: FeedViewModel
    private val listStates = mutableMapOf<AdChannel, Parcelable?>()

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
                renderList()
                startActivity(Intent(this, DetailActivity::class.java).putExtra(DetailActivity.EXTRA_AD_ID, ad.id))
            },
            onLikeClick = { ad -> viewModel.toggleLike(ad.id); renderList() },
            onFavoriteClick = { ad -> viewModel.toggleFavorite(ad.id); renderList() },
            onShareClick = { ad -> viewModel.share(ad.id); renderList() },
            onVideoClick = { ad -> viewModel.toggleVideoPlay(ad.id); renderList() },
            onMuteClick = { ad -> viewModel.toggleMute(ad.id); renderList() }
        )
        recyclerView = findViewById(R.id.ad_list)
        recyclerView.layoutManager = layoutManager
        recyclerView.adapter = adapter
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                registerVisibleImpressions()
                val lastVisible = layoutManager.findLastVisibleItemPosition()
                if (!viewModel.loadingMore &&
                    viewModel.searchText.isBlank() &&
                    dy > 0 &&
                    lastVisible >= adapter.itemCount - 2
                ) {
                    viewModel.loadMore()
                    renderList()
                }
            }
        })

        swipeRefresh = findViewById(R.id.swipe_refresh)
        swipeRefresh.setColorSchemeColors(Color.WHITE, Color.rgb(78, 164, 255))
        swipeRefresh.setProgressBackgroundColorSchemeColor(Color.rgb(28, 28, 28))
        swipeRefresh.setOnRefreshListener {
            viewModel.refresh()
            swipeRefresh.isRefreshing = false
            renderList()
            recyclerView.scrollToPosition(0)
        }

        findViewById<TextView>(R.id.search_input).addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.updateSearchText(s?.toString().orEmpty())
                renderList()
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })

        selectTab(AdChannel.FEATURED, restorePosition = false)
    }

    override fun onResume() {
        super.onResume()
        if (::adapter.isInitialized) {
            viewModel.sync()
            renderList()
        }
    }

    private fun selectTab(channel: AdChannel, restorePosition: Boolean = true) {
        if (::layoutManager.isInitialized) {
            listStates[viewModel.activeChannel] = layoutManager.onSaveInstanceState()
        }
        viewModel.selectChannel(channel)
        tabs.forEachIndexed { index, tab ->
            val selected = AdChannel.entries[index] == viewModel.activeChannel
            tab.setTextColor(if (selected) Color.BLACK else Color.rgb(210, 210, 210))
            tab.setBackgroundResource(
                if (selected) R.drawable.bg_tab_selected else R.drawable.bg_tab_unselected
            )
        }
        renderList {
            if (restorePosition) {
                listStates[viewModel.activeChannel]?.let(layoutManager::onRestoreInstanceState) ?: recyclerView.scrollToPosition(0)
            } else {
                recyclerView.scrollToPosition(0)
            }
            registerVisibleImpressions()
        }
    }

    private fun renderList(onCommitted: (() -> Unit)? = null) {
        adapter.submitList(viewModel.ads) {
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
                changed = viewModel.registerImpression(it.id) || changed
            }
        }
        if (changed) renderList()
    }
}
