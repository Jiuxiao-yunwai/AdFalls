package com.example.adfalls.ui.search

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
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
import com.example.adfalls.R
import com.example.adfalls.data.model.AdChannel
import com.example.adfalls.ui.common.applyResponsiveHorizontalPadding
import com.example.adfalls.ui.detail.DetailActivity
import com.example.adfalls.ui.feed.AdAdapter
import com.example.adfalls.viewmodel.SearchViewModel
import kotlinx.coroutines.launch

class SearchActivity : ComponentActivity() {
    private lateinit var viewModel: SearchViewModel
    private lateinit var adapter: AdAdapter
    private lateinit var input: EditText
    private lateinit var emptyState: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = getColor(R.color.app_bg)
        window.navigationBarColor = getColor(R.color.app_bg)
        setContentView(R.layout.activity_search)
        findViewById<View>(R.id.search_root).applyResponsiveHorizontalPadding()

        val channel = intent.getStringExtra(EXTRA_CHANNEL)
            ?.let { runCatching { AdChannel.valueOf(it) }.getOrNull() }
            ?: AdChannel.FEATURED

        viewModel = ViewModelProvider.create(this)[SearchViewModel::class]
        viewModel.selectChannel(channel)

        input = findViewById(R.id.search_input)
        emptyState = findViewById(R.id.search_empty_state)
        findViewById<View>(R.id.search_back).setOnClickListener { finish() }
        findViewById<TextView>(R.id.search_scope).text = "当前频道：${channel.title} · 可搜索标题、品牌、摘要和标签"

        adapter = AdAdapter(
            onCardClick = { ad ->
                viewModel.registerClick(ad.id)
                startActivity(Intent(this, DetailActivity::class.java).putExtra(DetailActivity.EXTRA_AD_ID, ad.id))
            },
            onLikeClick = { ad -> viewModel.toggleLike(ad.id) },
            onFavoriteClick = { ad -> viewModel.toggleFavorite(ad.id) },
            onShareClick = { ad -> viewModel.share(ad.id) },
            onVideoClick = { ad -> viewModel.toggleVideoPlay(ad.id) },
            onMuteClick = { ad -> viewModel.toggleMute(ad.id) },
            onTagClick = { tag ->
                input.setText(tag)
                input.setSelection(input.text.length)
            }
        )

        findViewById<RecyclerView>(R.id.search_results).apply {
            layoutManager = LinearLayoutManager(this@SearchActivity)
            adapter = this@SearchActivity.adapter
        }

        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.updateSearchText(s?.toString().orEmpty())
            }

            override fun afterTextChanged(s: Editable?) = Unit
        })

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    adapter.submitAds(state.ads)
                    emptyState.visibility = when {
                        state.loading -> View.VISIBLE
                        state.searchText.isBlank() -> View.VISIBLE
                        state.ads.isEmpty() -> View.VISIBLE
                        else -> View.GONE
                    }
                    emptyState.text = when {
                        state.loading -> "搜索中..."
                        state.searchText.isBlank() -> "输入关键词，搜索标题、品牌、摘要或标签"
                        else -> "没有找到“${state.searchText}”相关广告"
                    }
                }
            }
        }

        input.post {
            input.requestFocus()
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    companion object {
        const val EXTRA_CHANNEL = "extra_channel"
    }
}
