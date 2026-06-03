package com.example.adfalls.data.remote

import com.example.adfalls.data.local.AdDao
import com.example.adfalls.data.local.toModel
import com.example.adfalls.data.model.AdChannel
import com.example.adfalls.data.model.AdItem
import kotlinx.coroutines.delay

data class RemoteAdPage(
    val ads: List<AdItem>,
    val hasMore: Boolean
)

object FakeAdRemoteDataSource {
    private const val NETWORK_DELAY_MS = 450L

    suspend fun fetchAdPage(
        dao: AdDao,
        channel: AdChannel,
        requestedIds: Set<Long>,
        pageSize: Int
    ): RemoteAdPage {
        delay(NETWORK_DELAY_MS)
        val candidates = dao.getAdsByChannel(channel.name)
            .filterNot { it.id in requestedIds }
        return RemoteAdPage(
            ads = candidates.take(pageSize).map { it.toModel() },
            hasMore = candidates.size > pageSize
        )
    }

    suspend fun searchAds(
        dao: AdDao,
        channel: AdChannel,
        query: String
    ): List<AdItem> {
        delay(NETWORK_DELAY_MS)
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        return dao.getAdsByChannel(channel.name)
            .map { it.toModel() }
            .filter { ad ->
                ad.title.contains(trimmed, ignoreCase = true) ||
                    ad.brand.contains(trimmed, ignoreCase = true) ||
                    ad.summary.contains(trimmed, ignoreCase = true) ||
                    ad.tags.any { it.contains(trimmed, ignoreCase = true) }
            }
    }
}
