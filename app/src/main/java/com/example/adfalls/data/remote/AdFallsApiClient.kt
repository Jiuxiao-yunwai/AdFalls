package com.example.adfalls.data.remote

import com.example.adfalls.BuildConfig
import com.example.adfalls.data.model.AdCardType
import com.example.adfalls.data.model.AdChannel
import com.example.adfalls.data.model.AdItem
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import kotlin.math.absoluteValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class BackendPage<T>(
    val items: List<T>,
    val nextCursor: String,
    val hasMore: Boolean
)

data class BackendUserSession(
    val userId: Long,
    val username: String,
    val token: String
)

data class BackendAdDto(
    val id: Long,
    val title: String,
    val content: String?,
    val summary: String,
    val tags: List<String>,
    val coverUrl: String?,
    val imageUrls: List<String>,
    val videoUrl: String?,
    val type: String,
    val targetUrl: String?,
    val isFavorite: Boolean
) {
    fun toModel(channel: AdChannel): AdItem {
        val normalizedType = when (type) {
            "IMAGE_SMALL" -> AdCardType.SMALL_IMAGE
            "VIDEO" -> if (videoUrl.isNullOrBlank()) AdCardType.LARGE_IMAGE else AdCardType.VIDEO
            else -> AdCardType.LARGE_IMAGE
        }
        return AdItem(
            id = id,
            channel = channel,
            type = normalizedType,
            title = title,
            brand = "广告推荐",
            videoUrl = videoUrl?.takeIf { it.isNotBlank() },
            summary = summary,
            detail = content?.takeIf { it.isNotBlank() } ?: summary,
            tags = tags,
            mediaColor = colorFor(id),
            favorited = isFavorite
        )
    }

    private fun colorFor(id: Long): Int {
        val palette = intArrayOf(
            0xFF4EA4FF.toInt(),
            0xFFFF7868.toInt(),
            0xFF3EC491.toInt(),
            0xFFF5B84A.toInt(),
            0xFFB284FF.toInt(),
            0xFF3ACAD6.toInt()
        )
        return palette[(id.hashCode().absoluteValue) % palette.size]
    }
}

object AdFallsApiClient {
    private const val DEFAULT_TIMEOUT_MS = 60_000
    private val baseUrl = BuildConfig.ADFALLS_API_BASE_URL.trimEnd('/')

    suspend fun login(username: String = "test", password: String = "123456"): BackendUserSession = withContext(Dispatchers.IO) {
        val data = requestJson(
            path = "/api/user/login",
            method = "POST",
            body = JSONObject()
                .put("username", username)
                .put("password", password)
        ).getJSONObject("data")
        BackendUserSession(
            userId = data.getLong("userId"),
            username = data.getString("username"),
            token = data.getString("token")
        )
    }

    suspend fun fetchFeed(
        channel: AdChannel,
        cursor: String?,
        size: Int,
        userId: Long?
    ): BackendPage<BackendAdDto> = withContext(Dispatchers.IO) {
        val data = requestJson(
            path = "/api/ads/feed",
            query = mapOf(
                "channel" to channel.toBackendValue(),
                "cursor" to cursor.orEmpty(),
                "size" to size.toString(),
                "userId" to userId?.toString()
            )
        ).getJSONObject("data")
        BackendPage(
            items = data.getJSONArray("list").mapObjects { it.toBackendAdDto() },
            nextCursor = data.optString("nextCursor", ""),
            hasMore = data.optBoolean("hasMore", false)
        )
    }

    suspend fun fetchAdDetail(adId: Long, userId: Long?): BackendAdDto = withContext(Dispatchers.IO) {
        val data = requestJson(
            path = "/api/ads/$adId",
            query = mapOf("userId" to userId?.toString())
        ).getJSONObject("data")
        data.toBackendAdDto()
    }

    suspend fun searchAds(keyword: String, cursor: String?, size: Int, userId: Long?): BackendPage<BackendAdDto> = withContext(Dispatchers.IO) {
        val data = requestJson(
            path = "/api/ads/search",
            query = mapOf(
                "keyword" to keyword,
                "cursor" to cursor.orEmpty(),
                "size" to size.toString(),
                "userId" to userId?.toString()
            )
        ).getJSONObject("data")
        BackendPage(
            items = data.getJSONArray("list").mapObjects { it.toBackendAdDto() },
            nextCursor = data.optString("nextCursor", ""),
            hasMore = data.optBoolean("hasMore", false)
        )
    }

    suspend fun chatSearch(userId: Long?, message: String, cursor: String?, size: Int): Pair<String, BackendPage<BackendAdDto>> = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("message", message)
            .put("cursor", cursor.orEmpty())
            .put("size", size)
        userId?.let { body.put("userId", it) }
        val data = requestJson(
            path = "/api/ai/chat-search",
            method = "POST",
            body = body
        ).getJSONObject("data")
        val page = BackendPage(
            items = data.getJSONArray("ads").mapObjects { it.toBackendAdDto() },
            nextCursor = data.optString("nextCursor", ""),
            hasMore = data.optBoolean("hasMore", false)
        )
        data.optString("reply", "已为你找到相关广告。") to page
    }

    suspend fun analyzeAd(userId: Long?, adId: Long, message: String): String = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("adId", adId)
            .put("message", message)
        userId?.let { body.put("userId", it) }
        requestJson(
            path = "/api/ai/ad-analysis",
            method = "POST",
            body = body
        ).getJSONObject("data").optString("reply", "已为你完成广告分析。")
    }

    suspend fun favorite(userId: Long, adId: Long) = withContext(Dispatchers.IO) {
        requestJson(path = "/api/user/$userId/favorites/$adId", method = "POST")
    }

    suspend fun unfavorite(userId: Long, adId: Long) = withContext(Dispatchers.IO) {
        requestJson(path = "/api/user/$userId/favorites/$adId", method = "DELETE")
    }

    suspend fun like(userId: Long, adId: Long) = withContext(Dispatchers.IO) {
        requestJson(path = "/api/user/$userId/likes/$adId", method = "POST")
    }

    suspend fun unlike(userId: Long, adId: Long) = withContext(Dispatchers.IO) {
        requestJson(path = "/api/user/$userId/likes/$adId", method = "DELETE")
    }

    suspend fun recordBehavior(userId: Long, adId: Long?, channel: AdChannel?, behaviorType: String) = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("userId", userId)
            .put("behaviorType", behaviorType)
        adId?.let { body.put("adId", it) }
        channel?.let { body.put("channel", it.toBackendValue()) }
        requestJson(path = "/api/behavior", method = "POST", body = body)
    }

    private fun requestJson(
        path: String,
        method: String = "GET",
        query: Map<String, String?> = emptyMap(),
        body: JSONObject? = null
    ): JSONObject {
        val url = URL("$baseUrl$path${query.toQueryString()}")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = DEFAULT_TIMEOUT_MS
            readTimeout = DEFAULT_TIMEOUT_MS
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            if (body != null) {
                doOutput = true
                outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
        }
        var responseCode = -1
        val responseText = try {
            responseCode = connection.responseCode
            val stream = if (responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream ?: connection.inputStream
            }
            BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
        } finally {
            connection.disconnect()
        }
        val json = JSONObject(responseText)
        val code = json.optInt("code", responseCode)
        if (code !in 200..299) {
            throw IllegalStateException(json.optString("message", "接口请求失败"))
        }
        return json
    }

    private fun Map<String, String?>.toQueryString(): String {
        val pairs = entries.mapNotNull { (key, value) ->
            value?.let {
                "${key.urlEncode()}=${it.urlEncode()}"
            }
        }
        return if (pairs.isEmpty()) "" else pairs.joinToString(prefix = "?", separator = "&")
    }

    private fun String.urlEncode(): String = URLEncoder.encode(this, "UTF-8")

    private fun AdChannel.toBackendValue(): String = when (this) {
        AdChannel.FEATURED -> "featured"
        AdChannel.COMMERCE -> "ecommerce"
        AdChannel.LOCAL -> "local"
    }

    private fun JSONObject.toBackendAdDto(): BackendAdDto {
        return BackendAdDto(
            id = getLong("id"),
            title = optString("title"),
            content = optString("content").takeIf { it.isNotBlank() },
            summary = optString("summary"),
            tags = optJSONArray("tags")?.toStringList().orEmpty(),
            coverUrl = optString("coverUrl").takeIf { it.isNotBlank() },
            imageUrls = optJSONArray("imageUrls")?.toStringList().orEmpty(),
            videoUrl = optString("videoUrl").takeIf { it.isNotBlank() && it != "null" },
            type = optString("type", "IMAGE_LARGE"),
            targetUrl = optString("targetUrl").takeIf { it.isNotBlank() },
            isFavorite = optBoolean("isFavorite", false)
        )
    }

    private fun JSONArray.toStringList(): List<String> {
        return List(length()) { index -> optString(index) }.filter { it.isNotBlank() }
    }

    private fun <T> JSONArray.mapObjects(transform: (JSONObject) -> T): List<T> {
        return List(length()) { index -> transform(getJSONObject(index)) }
    }
}
