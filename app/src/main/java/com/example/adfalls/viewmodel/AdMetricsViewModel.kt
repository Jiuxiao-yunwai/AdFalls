package com.example.adfalls.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.adfalls.data.model.AdMetric
import com.example.adfalls.data.repository.AdMetricsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AdMetricsSummary(
    val totalExposures: Int = 0,
    val totalClicks: Int = 0,
    val averageCtr: Double = 0.0,
    val totalInteractions: Int = 0
)

data class AdMetricsUiState(
    val metrics: List<AdMetric> = emptyList(),
    val summary: AdMetricsSummary = AdMetricsSummary(),
    val loading: Boolean = true,
    val error: String? = null,
    val keyword: String = "",
    val channel: String? = null
)

class AdMetricsViewModel : ViewModel() {
    private val mutableUiState = MutableStateFlow(AdMetricsUiState())
    val uiState: StateFlow<AdMetricsUiState> = mutableUiState.asStateFlow()
    private var loadJob: Job? = null

    fun load(channel: String? = mutableUiState.value.channel, keyword: String = mutableUiState.value.keyword) {
        loadJob?.cancel()
        mutableUiState.update { it.copy(loading = true, error = null, channel = channel, keyword = keyword) }
        loadJob = viewModelScope.launch {
            runCatching {
                AdMetricsRepository.loadMetrics(channel = channel, keyword = keyword)
            }.onSuccess { metrics ->
                mutableUiState.update {
                    it.copy(
                        metrics = metrics.sortedByDescending(AdMetric::exposures),
                        summary = metrics.toSummary(),
                        loading = false,
                        error = null
                    )
                }
            }.onFailure { throwable ->
                mutableUiState.update {
                    it.copy(
                        metrics = emptyList(),
                        summary = AdMetricsSummary(),
                        loading = false,
                        error = throwable.message ?: "指标加载失败"
                    )
                }
            }
        }
    }

    private fun List<AdMetric>.toSummary(): AdMetricsSummary {
        val exposures = sumOf { it.exposures }
        val clicks = sumOf { it.clicks }
        return AdMetricsSummary(
            totalExposures = exposures,
            totalClicks = clicks,
            averageCtr = if (exposures == 0) 0.0 else clicks.toDouble() / exposures.toDouble(),
            totalInteractions = sumOf { it.interactionCount }
        )
    }
}
