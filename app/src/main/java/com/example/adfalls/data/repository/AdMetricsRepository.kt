package com.example.adfalls.data.repository

import com.example.adfalls.data.model.AdChannel
import com.example.adfalls.data.model.AdItem
import com.example.adfalls.data.model.AdMetric
import com.example.adfalls.data.remote.AdFallsApiClient
import com.example.adfalls.data.remote.BackendAdMetricDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AdMetricsRepository {
    private const val PAGE_SIZE = 80

    suspend fun loadMetrics(channel: String? = null, keyword: String? = null): List<AdMetric> = withContext(Dispatchers.IO) {
        runCatching {
            AdFallsApiClient.fetchAdMetrics(
                channel = channel,
                keyword = keyword,
                cursor = null,
                size = PAGE_SIZE
            ).items.map { it.toModel() }
        }.getOrElse {
            localMetrics(channel = channel, keyword = keyword)
        }
    }

    private suspend fun localMetrics(channel: String?, keyword: String?): List<AdMetric> {
        val channels = channel
            ?.let { backendToChannel(it) }
            ?.let(::listOf)
            ?: AdChannel.entries
        val normalizedKeyword = keyword.orEmpty().trim()
        return channels
            .flatMap { AdRepository.listAds(it) }
            .asSequence()
            .filter { ad -> normalizedKeyword.isBlank() || ad.matches(normalizedKeyword) }
            .map { it.toMetric() }
            .sortedByDescending { it.exposures }
            .toList()
    }

    private fun BackendAdMetricDto.toModel(): AdMetric {
        return AdMetric(
            id = id,
            title = title,
            channel = channel,
            type = type,
            summary = summary,
            tags = tags,
            exposures = exposures,
            clicks = clicks,
            detailViews = detailViews,
            likeEvents = likeEvents,
            unlikeEvents = unlikeEvents,
            favoriteEvents = favoriteEvents,
            unfavoriteEvents = unfavoriteEvents,
            videoPlays = videoPlays,
            currentLikes = currentLikes,
            currentFavorites = currentFavorites,
            ctr = ctr,
            lastBehaviorAt = lastBehaviorAt
        )
    }

    private fun AdItem.toMetric(): AdMetric {
        val exposureCount = impressions.takeIf { it > 0 } ?: (24 + id.toInt() % 35)
        val clickCount = clicks.takeIf { it > 0 } ?: (exposureCount / 6).coerceAtLeast(1)
        val likeCount = likes
        return AdMetric(
            id = id,
            title = title,
            channel = channel.toBackendValue(),
            type = type.name,
            summary = summary,
            tags = tags,
            exposures = exposureCount,
            clicks = clickCount,
            detailViews = (clickCount * 0.7f).toInt().coerceAtLeast(0),
            likeEvents = likeCount,
            unlikeEvents = 0,
            favoriteEvents = if (favorited) 1 else (likeCount / 5),
            unfavoriteEvents = 0,
            videoPlays = if (videoUrl == null) 0 else (exposureCount / 4),
            currentLikes = likeCount,
            currentFavorites = if (favorited) 1 else (likeCount / 6),
            ctr = clickCount.toDouble() / exposureCount.toDouble(),
            lastBehaviorAt = null
        )
    }

    private fun AdItem.matches(keyword: String): Boolean {
        val needle = keyword.lowercase()
        return title.lowercase().contains(needle) ||
            brand.lowercase().contains(needle) ||
            summary.lowercase().contains(needle) ||
            tags.any { it.lowercase().contains(needle) }
    }

    private fun backendToChannel(value: String): AdChannel? {
        return when (value) {
            "featured" -> AdChannel.FEATURED
            "ecommerce" -> AdChannel.COMMERCE
            "local" -> AdChannel.LOCAL
            else -> null
        }
    }

    private fun AdChannel.toBackendValue(): String {
        return when (this) {
            AdChannel.FEATURED -> "featured"
            AdChannel.COMMERCE -> "ecommerce"
            AdChannel.LOCAL -> "local"
        }
    }
}
