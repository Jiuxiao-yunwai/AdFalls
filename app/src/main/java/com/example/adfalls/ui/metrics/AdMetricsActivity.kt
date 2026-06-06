package com.example.adfalls.ui.metrics

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.adfalls.R
import com.example.adfalls.viewmodel.AdMetricsUiState
import com.example.adfalls.viewmodel.AdMetricsViewModel
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToInt

class AdMetricsActivity : ComponentActivity() {
    private lateinit var viewModel: AdMetricsViewModel
    private lateinit var adapter: AdMetricsAdapter
    private lateinit var status: TextView
    private lateinit var summaryExposures: TextView
    private lateinit var summaryCtr: TextView
    private lateinit var summaryClicks: TextView
    private lateinit var summaryInteractions: TextView
    private lateinit var emptyState: TextView
    private lateinit var list: RecyclerView
    private val numberFormat = NumberFormat.getIntegerInstance(Locale.CHINA)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = getColor(R.color.app_bg)
        window.navigationBarColor = getColor(R.color.app_bg)
        setContentView(R.layout.activity_ad_metrics)
        findViewById<View>(R.id.metrics_root).applyMetricsResponsiveHorizontalPadding()

        viewModel = ViewModelProvider.create(this)[AdMetricsViewModel::class]
        adapter = AdMetricsAdapter()

        status = findViewById(R.id.metrics_status)
        summaryExposures = findViewById(R.id.summary_exposures)
        summaryCtr = findViewById(R.id.summary_ctr)
        summaryClicks = findViewById(R.id.summary_clicks)
        summaryInteractions = findViewById(R.id.summary_interactions)
        emptyState = findViewById(R.id.metrics_empty_state)
        list = findViewById(R.id.metrics_list)
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter

        findViewById<View>(R.id.metrics_back).setOnClickListener { finish() }
        findViewById<View>(R.id.metrics_refresh).setOnClickListener { viewModel.load() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::render)
            }
        }
        viewModel.load()
    }

    private fun render(state: AdMetricsUiState) {
        adapter.submitList(state.metrics)
        summaryExposures.text = numberFormat.format(state.summary.totalExposures)
        summaryCtr.text = state.summary.averageCtr.toPercent()
        summaryClicks.text = "点击 ${numberFormat.format(state.summary.totalClicks)}"
        summaryInteractions.text = "互动 ${numberFormat.format(state.summary.totalInteractions)}"

        val showEmpty = !state.loading && state.metrics.isEmpty()
        list.visibility = if (showEmpty) View.GONE else View.VISIBLE
        emptyState.visibility = if (showEmpty) View.VISIBLE else View.GONE
        emptyState.text = state.error ?: "暂无广告指标"
        status.text = when {
            state.loading -> "正在同步广告指标..."
            state.error != null -> state.error
            else -> "已同步 ${state.metrics.size} 条广告指标"
        }
    }

    private fun View.applyMetricsResponsiveHorizontalPadding() {
        val density = resources.displayMetrics.density
        val horizontal = (resources.displayMetrics.widthPixels * 0.10f)
            .roundToInt()
            .coerceIn((32f * density).roundToInt(), (56f * density).roundToInt())
        setPaddingRelative(horizontal, paddingTop, horizontal, paddingBottom)
    }
}
